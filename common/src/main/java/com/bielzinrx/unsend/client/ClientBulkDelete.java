package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Selection state and reliable sequential deletion using the proven single-delete channel. */
public final class ClientBulkDelete {
    public record Visual(HudPin pin, float x, float y, float delay) {}

    private static final Map<Long, Selected> SELECTED = new LinkedHashMap<>();
    private static final Map<Long, Selected> RUNNING = new LinkedHashMap<>();
    private static final Deque<Target> QUEUE = new ArrayDeque<>();

    private static boolean confirming;
    private static boolean running;
    private static Target current;
    private static long currentSentAtMs;
    private static int currentAttempts;
    private static int requested;
    private static int applied;
    private static long completeAtMs;
    private static long timeoutAtMs;

    private ClientBulkDelete() {}

    public static synchronized boolean toggle(ClientTrackedMessage tracked, HudPin pin, float x, float y) {
        if (!selectable(tracked)) return false;
        if (SELECTED.remove(tracked.id) != null) return true;
        if (SELECTED.size() >= UnsayClientConfig.get().bulkLimit()) {
            toast("unsend.error.bulk_limit", UnsayClientConfig.get().bulkLimit());
            return true;
        }
        SELECTED.put(tracked.id, new Selected(tracked.id, pin, x, y, SELECTED.size()));
        return true;
    }

    public static synchronized void select(ClientTrackedMessage tracked, HudPin pin, float x, float y) {
        if (!selectable(tracked) || SELECTED.containsKey(tracked.id)) return;
        if (SELECTED.size() >= UnsayClientConfig.get().bulkLimit()) return;
        SELECTED.put(tracked.id, new Selected(tracked.id, pin, x, y, SELECTED.size()));
    }

    public static synchronized boolean isSelected(long id) {
        return SELECTED.containsKey(id);
    }

    public static synchronized int selectedCount() {
        return SELECTED.size();
    }

    public static synchronized boolean hasSelection() {
        return !SELECTED.isEmpty();
    }

    public static synchronized boolean isSelectionMode() {
        expireTransientState();
        return !SELECTED.isEmpty() || confirming || running;
    }

    public static synchronized boolean isConfirming() {
        return confirming;
    }

    public static synchronized boolean isRunning() {
        expireTransientState();
        return running;
    }

    public static synchronized boolean isBusy() {
        return confirming || isRunning();
    }

    /** Selection itself is the confirmation: one Delete press starts immediately. */
    public static synchronized boolean requestSelectedDelete() {
        if (SELECTED.isEmpty() || running) return false;
        confirming = false;
        return start();
    }

    public static synchronized void confirm() {
        if (!confirming) return;
        confirming = false;
        start();
    }

    public static synchronized void cancelConfirmation() {
        confirming = false;
    }

    public static synchronized void clearSelection() {
        if (running) return;
        confirming = false;
        SELECTED.clear();
    }

    public static synchronized void onChatClosed() {
        if (running) return;
        confirming = false;
        SELECTED.clear();
    }

    public static synchronized void clearAll() {
        SELECTED.clear();
        RUNNING.clear();
        QUEUE.clear();
        confirming = false;
        running = false;
        current = null;
        currentSentAtMs = 0L;
        currentAttempts = 0;
        requested = 0;
        applied = 0;
        completeAtMs = 0L;
        timeoutAtMs = 0L;
    }

    public static synchronized Visual visualFor(long messageId) {
        return visualFor(messageId, null, null);
    }

    /** Returns the exact selected row only when the incoming delete matches the outstanding request. */
    public static synchronized Visual visualFor(long messageId, UUID sender, String plainText) {
        Target target = matchingCurrent(messageId, sender, plainText);
        if (target != null) {
            Selected s = target.selected;
            return new Visual(s.pin, s.x, s.y, 0f);
        }
        Selected s = RUNNING.get(messageId);
        return s == null ? null : new Visual(s.pin, s.x, s.y, 0f);
    }

    public static synchronized void onDeleteApplied(long messageId) {
        onDeleteApplied(messageId, null, null);
    }

    /** Advances the queue only after the server's authoritative delete broadcast arrives. */
    public static synchronized void onDeleteApplied(long messageId, UUID sender, String plainText) {
        Target target = matchingCurrent(messageId, sender, plainText);
        if (target == null) {
            Selected removed = RUNNING.remove(messageId);
            if (removed != null) {
                SELECTED.remove(removed.id);
                QUEUE.removeIf(queued -> queued.selected.id == removed.id);
                ClientMessageIndex.tombstoneId(removed.id);
                ClientMessageIndex.remove(removed.id);
                applied++;

                if (current != null && current.selected.id == removed.id) {
                    current = null;
                    currentSentAtMs = 0L;
                    currentAttempts = 0;
                    sendNext();
                }
            }
            return;
        }

        SELECTED.remove(target.selected.id);
        RUNNING.remove(target.selected.id);
        // A provisional selected id may have been deleted through a real positive server id.
        // Remove both index identities so the old local tracker cannot become a ghost row later.
        ClientMessageIndex.tombstoneId(target.selected.id);
        ClientMessageIndex.remove(target.selected.id);
        applied++;
        current = null;
        currentSentAtMs = 0L;
        currentAttempts = 0;
        sendNext();
    }

    /** Handles an individual delete failure while a selected-delete queue is active. */
    public static synchronized boolean onIndividualResult(boolean ok, String messageKey) {
        if (!running || current == null) return false;
        if (ok) return false; // Success is completed by DELETE_S2C, which carries the exact row.

        if ("unsend.error.not_found".equals(messageKey)
            || "unsend.error.already_gone".equals(messageKey)) {
            // The server is already clean. Reconcile this exact selected row locally and
            // advance the queue as if the authoritative delete broadcast had arrived.
            Minecraft mc = Minecraft.getInstance();
            UUID self = mc != null && mc.player != null ? mc.player.getUUID() : null;
            ClientDelete.applyRemoteDelete(current.selected.id, self, current.plain);
            return true;
        }

        if ("unsend.error.rate_limit".equals(messageKey)) {
            // Keep the exact row and retry after the server's ten-second action window.
            // The pause is silent and cannot turn the remaining selection into ghost rows.
            currentAttempts = 0;
            currentSentAtMs = System.currentTimeMillis() + 8_100L;
            return true;
        }

        if ("unsend.error.pending".equals(messageKey)
            || "unsend.error.not_ready".equals(messageKey)) {
            // A message registration/action can finish just after the request. Retry quietly.
            currentAttempts = 0;
            currentSentAtMs = System.currentTimeMillis() - 1_200L;
            return true;
        }

        // A real rejection should never trap the chat in selection mode.
        abortAndRestoreInput();
        return true;
    }

    /** Legacy bulk-result compatibility. New clients no longer depend on this packet. */
    public static synchronized void onBulkResult(long requestId, int total,
                                                 int deletedCount, int skippedCount) {
        if (!running) return;
        if (deletedCount <= 0 && skippedCount > 0) {
            abortAndRestoreInput();
        }
    }

    public static synchronized Component status() {
        expireTransientState();
        if (!SELECTED.isEmpty() && !running) {
            return Component.translatable("unsend.select.hint", SELECTED.size());
        }
        // The disappearing rows are the feedback. No technical progress text while deleting.
        return Component.empty();
    }

    public static synchronized float progress() {
        return requested <= 0 ? 0f : Math.min(1f, applied / (float) requested);
    }

    private static boolean selectable(ClientTrackedMessage tracked) {
        return tracked != null && tracked.id != 0 && !tracked.deleting && !tracked.pendingEdit
            && !ClientMessageIndex.isTombstoned(tracked) && !running;
    }

    /** Keeps a selected GUI row selected when its provisional id is replaced by the server id. */
    public static synchronized void remapSelectedId(long oldId, long newId) {
        if (oldId == newId || newId <= 0L) return;
        remapMap(SELECTED, oldId, newId);
        remapMap(RUNNING, oldId, newId);
        if (current != null && current.selected.id == oldId) {
            Selected value = current.selected;
            Selected remapped = new Selected(newId, value.pin, value.x, value.y, value.order);
            current = new Target(remapped, newId, current.plain, current.fingerprint,
                current.occurrence, current.fallback);
        }
        if (!QUEUE.isEmpty()) {
            List<Target> rebuilt = new ArrayList<>(QUEUE.size());
            while (!QUEUE.isEmpty()) {
                Target t = QUEUE.removeFirst();
                if (t.selected.id == oldId) {
                    Selected value = t.selected;
                    Selected remapped = new Selected(newId, value.pin, value.x, value.y, value.order);
                    rebuilt.add(new Target(remapped, newId, t.plain, t.fingerprint,
                        t.occurrence, t.fallback));
                } else {
                    rebuilt.add(t);
                }
            }
            QUEUE.addAll(rebuilt);
        }
    }

    private static void remapMap(Map<Long, Selected> map, long oldId, long newId) {
        if (!map.containsKey(oldId)) return;
        Map<Long, Selected> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<Long, Selected> entry : map.entrySet()) {
            Selected value = entry.getValue();
            if (entry.getKey() == oldId) {
                rebuilt.put(newId, new Selected(newId, value.pin, value.x, value.y, value.order));
            } else {
                rebuilt.put(entry.getKey(), value);
            }
        }
        map.clear();
        map.putAll(rebuilt);
    }

    /** Called from the chat render loop. Retries one lost request and never leaves the UI stuck. */
    public static synchronized void tick() {
        if (!running || current == null || currentSentAtMs <= 0L) return;
        long now = System.currentTimeMillis();
        if (now - currentSentAtMs >= 2200L && currentAttempts < 2) {
            currentAttempts++;
            currentSentAtMs = now;
            sendCurrent();
            return;
        }
        if (now - currentSentAtMs >= 5200L) {
            timeoutAtMs = now;
            abortAndRestoreInput();
        }
    }

    private static boolean start() {
        if (SELECTED.isEmpty()) return false;

        // A registration packet can replace a provisional id between selection and Delete.
        // Normalize every selection from its exact GUI row before constructing the queue.
        List<long[]> remaps = new ArrayList<>();
        for (Selected selected : new ArrayList<>(SELECTED.values())) {
            if (selected.pin == null || selected.pin.guiRef == null) continue;
            ClientTrackedMessage exact = ClientMessageIndex.findByGuiReference(selected.pin.guiRef);
            if (exact != null && exact.id != selected.id && exact.id != 0L) {
                remaps.add(new long[] { selected.id, exact.id });
            }
        }
        for (long[] remap : remaps) remapSelectedId(remap[0], remap[1]);

        List<Target> targets = new ArrayList<>();
        RUNNING.clear();
        for (Selected selected : SELECTED.values()) {
            ClientTrackedMessage tracked = ClientMessageIndex.get(selected.id);
            String plain = tracked != null && tracked.plainText != null
                ? tracked.plainText
                : selected.pin != null ? selected.pin.plain : "";
            int occurrence = tracked != null
                ? ClientMessageIndex.occurrenceFromNewest(tracked)
                : 0;
            long fingerprint = Packets.fingerprintPlain(plain);
            String fallback = selected.id > 0L
                ? plain
                : Packets.encodeDeleteFallback(plain, occurrence);
            targets.add(new Target(selected, selected.id, plain, fingerprint, occurrence, fallback));
            RUNNING.put(selected.id, selected);
        }
        if (targets.isEmpty()) return false;

        // For provisional duplicate rows, delete oldest selected occurrences first. Removing an
        // older occurrence does not change the rank of newer rows, so every selected row stays exact.
        targets.sort(Comparator
            .comparingInt((Target t) -> t.id <= 0L ? 0 : 1)
            .thenComparing((Target a, Target b) -> Integer.compare(b.occurrence, a.occurrence))
            .thenComparingInt(t -> t.selected.order));

        QUEUE.clear();
        QUEUE.addAll(targets);
        requested = targets.size();
        applied = 0;
        running = true;
        completeAtMs = 0L;
        timeoutAtMs = 0L;
        current = null;
        sendNext();
        return true;
    }

    private static void sendNext() {
        if (!running) return;
        current = QUEUE.pollFirst();
        if (current == null) {
            finish();
            return;
        }
        currentAttempts = 1;
        currentSentAtMs = System.currentTimeMillis();
        sendCurrent();
    }

    private static void sendCurrent() {
        if (current == null) return;
        try {
            Platform.get().sendDeleteRequestToServer(current.id, current.fallback);
        } catch (Throwable failure) {
            abortAndRestoreInput();
        }
    }

    private static Target matchingCurrent(long messageId, UUID sender, String plainText) {
        if (!running || current == null) return null;

        // Local reconciliation uses the selected provisional id itself. Accept that exact id
        // before requiring sender/text data, otherwise a not-found response would remove one row
        // but leave the queue permanently waiting for a broadcast that will never arrive.
        if (current.selected.id == messageId || current.id == messageId) return current;
        if (current.id > 0L) return null;

        Minecraft mc = Minecraft.getInstance();
        UUID self = mc != null && mc.player != null ? mc.player.getUUID() : null;
        if (sender != null && self != null && !self.equals(sender)) return null;
        if (plainText == null || plainText.isBlank()) return null;
        return Packets.fingerprintPlain(plainText) == current.fingerprint ? current : null;
    }

    private static void finish() {
        SELECTED.clear();
        RUNNING.clear();
        QUEUE.clear();
        running = false;
        current = null;
        currentSentAtMs = 0L;
        currentAttempts = 0;
        completeAtMs = System.currentTimeMillis();
    }

    private static void abortAndRestoreInput() {
        SELECTED.clear();
        RUNNING.clear();
        QUEUE.clear();
        confirming = false;
        running = false;
        current = null;
        currentSentAtMs = 0L;
        currentAttempts = 0;
        completeAtMs = System.currentTimeMillis();
    }

    private static void expireTransientState() {
        long now = System.currentTimeMillis();
        if (completeAtMs > 0L && now - completeAtMs >= 400L) {
            completeAtMs = 0L;
            requested = 0;
            applied = 0;
        }
        if (timeoutAtMs > 0L && now - timeoutAtMs >= 1200L) {
            timeoutAtMs = 0L;
        }
    }

    private static void toast(String key, Object... args) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable(key, args), true);
        } catch (Throwable ignored) {
        }
    }

    private record Selected(long id, HudPin pin, float x, float y, int order) {}

    private record Target(Selected selected, long id, String plain, long fingerprint,
                          int occurrence, String fallback) {}
}
