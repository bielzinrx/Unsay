package com.bielzinrx.unsend.server;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatMessageTracker {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<Long, TrackedChatMessage> MESSAGES = new ConcurrentHashMap<>();
    private static final Map<Long, DeletedChatMessage> DELETED = new ConcurrentHashMap<>();
    private static final Map<UUID, ActionBucket> RATE = new ConcurrentHashMap<>();

    private static final int MAX_TRACKED = 512;
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final long TOMBSTONE_TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_TEXT = 256;
    private static final int SNAPSHOT_MAX = 200;
    private static final int MAX_TOMBSTONES = 512;

    private static final int RATE_WINDOW_MS = 10_000;
    private static final int RATE_DELETE = 64;
    private static final int RATE_EDIT = 10;
    private static final int RATE_REPLY = 12;
    private static final int RATE_BULK = 3;
    private static final int BULK_MAX = 50;

    private ChatMessageTracker() {}

    public static void clear() {
        MESSAGES.clear();
        DELETED.clear();
        RATE.clear();
    }

    public static boolean isDeleted(long messageId) {
        return DELETED.containsKey(messageId);
    }

    public static TrackedChatMessage register(ServerPlayer sender, String plainText) {
        return register(sender, plainText, 0L);
    }

    public static TrackedChatMessage register(ServerPlayer sender, String plainText, long replyToId) {
        prune();
        String name = sender.getGameProfile().getName();
        String text = sanitize(plainText);
        long id = NEXT_ID.getAndIncrement();
        TrackedChatMessage msg = new TrackedChatMessage(
            id, sender.getUUID(), name, text, System.currentTimeMillis(), replyToId);
        MESSAGES.put(id, msg);
        broadcastRegister(msg);
        return msg;
    }

    public static TrackedChatMessage get(long id) {
        return MESSAGES.get(id);
    }

    public static boolean canDelete(ServerPlayer requester, TrackedChatMessage msg) {
        if (requester == null || msg == null) return false;
        return msg.sender.equals(requester.getUUID()) || requester.hasPermissions(2);
    }

    public static boolean canEdit(ServerPlayer requester, TrackedChatMessage msg) {
        return requester != null && msg != null && msg.sender.equals(requester.getUUID());
    }

    public static boolean requestDelete(ServerPlayer requester, long messageId) {
        return requestDelete(requester, messageId, null);
    }

    public static boolean requestDelete(ServerPlayer requester, long messageId, String plainFallback) {
        if (requester == null) return false;
        prune();
        if (!allow(requester.getUUID(), Action.DELETE)) {
            sendResult(requester, false, "unsend.error.rate_limit");
            return false;
        }
        if (isDeleted(messageId)) {
            // The server is already clean, but an older client HUD may still show the row.
            // Re-send the authoritative delete only to this player and treat it as success.
            sendDeleteCleanup(requester, messageId);
            sendResult(requester, true, "unsend.result.deleted");
            return true;
        }

        TrackedChatMessage msg = messageId > 0 ? MESSAGES.get(messageId) : null;
        if (msg == null && plainFallback != null && !plainFallback.isBlank()) {
            Packets.DeleteFallback fallback = Packets.decodeDeleteFallback(plainFallback);
            msg = fallback.encoded()
                ? findOwnedByFingerprint(requester, fallback.fingerprint(), fallback.occurrence())
                : findOwnedByPlain(requester, fallback.plainText());
        }
        if (msg == null && messageId > 0L) {
            // A row may outlive the server tracker after an interrupted older deletion.
            // Treat the server as authoritative: remove that stale row only for the requester.
            sendDeleteCleanup(requester, messageId);
            sendResult(requester, true, "unsend.result.deleted");
            return true;
        }
        if (msg == null || !canDelete(requester, msg)) {
            sendResult(requester, false, "unsend.error.not_found");
            return false;
        }

        deleteTracked(requester, msg);
        sendResult(requester, true, "unsend.result.deleted");
        return true;
    }

    public static boolean requestBulkDelete(ServerPlayer requester, long requestId, List<Long> messageIds) {
        if (requester == null) return false;
        prune();
        if (!allow(requester.getUUID(), Action.BULK)) {
            sendResult(requester, false, "unsend.error.rate_limit");
            sendBulkResult(requester, requestId, 0, 0, 0);
            return false;
        }

        Set<Long> unique = new LinkedHashSet<>();
        if (messageIds != null) {
            for (Long id : messageIds) {
                if (id != null && id > 0 && unique.size() < BULK_MAX) unique.add(id);
            }
        }
        int requested = unique.size();
        int deleted = 0;
        int skipped = 0;
        for (Long id : unique) {
            TrackedChatMessage msg = MESSAGES.get(id);
            if (isDeleted(id) || msg == null) {
                // Reconcile stale local rows from an interrupted/older deletion. This affects
                // only the requester and does not mutate server chat state.
                sendDeleteCleanup(requester, id);
                deleted++;
                continue;
            }
            if (!canDelete(requester, msg)) {
                skipped++;
                continue;
            }
            deleteTracked(requester, msg);
            deleted++;
        }
        sendBulkResult(requester, requestId, requested, deleted, skipped);
        Unsend.LOGGER.info("[Unsend] Bulk delete by {}: {}/{} removed, {} skipped",
            requester.getGameProfile().getName(), deleted, requested, skipped);
        return deleted > 0;
    }

    private static void sendDeleteCleanup(ServerPlayer target, long id) {
        try {
            DeletedChatMessage deleted = DELETED.get(id);
            Platform.get().sendDeleteBroadcast(target, id,
                deleted == null ? null : deleted.sender,
                deleted == null ? "" : deleted.plainText);
        } catch (Throwable ignored) {
        }
    }

    private static void deleteTracked(ServerPlayer requester, TrackedChatMessage msg) {
        UUID sender = msg.sender;
        String plain = msg.plainText == null ? "" : msg.plainText;
        long id = msg.id;
        MESSAGES.remove(id);
        DELETED.put(id, new DeletedChatMessage(id, sender, msg.senderName, plain,
            System.currentTimeMillis()));

        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.get().sendDeleteBroadcast(player, id, sender, plain);
            }
        }
        if (!requester.getUUID().equals(sender)) {
            Unsend.LOGGER.info("[Unsend] Moderator {} deleted message {} from {} ({})",
                requester.getGameProfile().getName(), id, msg.senderName, sender);
        } else {
            Unsend.LOGGER.info("[Unsend] Message {} deleted by {}", id,
                requester.getGameProfile().getName());
        }
    }

    public static boolean requestEdit(ServerPlayer requester, long messageId, String newText) {
        if (requester == null) return false;
        prune();
        String text = sanitize(newText);
        if (text.isEmpty()) {
            return requestDelete(requester, messageId, null);
        }
        if (!allow(requester.getUUID(), Action.EDIT)) {
            sendResult(requester, false, "unsend.error.rate_limit");
            return false;
        }
        if (isDeleted(messageId)) {
            sendResult(requester, false, "unsend.error.already_gone");
            return false;
        }
        TrackedChatMessage msg = MESSAGES.get(messageId);
        if (msg == null || !canEdit(requester, msg)) {
            sendResult(requester, false, "unsend.error.not_found");
            return false;
        }
        String oldPlain = msg.plainText == null ? "" : msg.plainText;
        UUID sender = msg.sender;
        msg.plainText = text;
        msg.edited = true;

        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.get().sendEditBroadcast(player, messageId, text, oldPlain, sender);
            }
        }
        Unsend.LOGGER.info("[Unsend] Message {} edited by {}", messageId, requester.getGameProfile().getName());
        sendResult(requester, true, "unsend.result.edited");
        return true;
    }

    public static boolean requestReply(ServerPlayer sender, long targetId, String replyText,
                                       String fallbackName, String fallbackPreview) {
        if (sender == null) return false;
        prune();
        if (!allow(sender.getUUID(), Action.REPLY)) {
            sendResult(sender, false, "unsend.error.rate_limit");
            return false;
        }
        String text = sanitize(replyText);
        if (text.isEmpty()) {
            sendResult(sender, false, "unsend.error.empty");
            return false;
        }

        TrackedChatMessage target = targetId > 0 ? MESSAGES.get(targetId) : null;

        if (target == null) {
            sendResult(sender, false, "unsend.error.not_found");
            return false;
        }
        String targetName = target.senderName == null || target.senderName.isEmpty() ? "?" : target.senderName;
        String previewText = preview(target.plainText);

        MutableComponent citation = Component.literal("  ↳ " + targetName + " · " + previewText)
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        MutableComponent body = Component.literal("<" + sender.getGameProfile().getName() + "> " + text);
        Component full = Component.empty().append(citation).append(Component.literal("\n")).append(body);

        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(full, false);
        }

        long replyParent = target != null ? target.id : Math.max(0L, targetId);
        register(sender, text, replyParent);
        return true;
    }

    public static void sendSnapshotTo(ServerPlayer player) {
        if (player == null) return;
        prune();
        List<TrackedChatMessage> ordered = new ArrayList<>(MESSAGES.values());
        ordered.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
        int n = Math.min(ordered.size(), SNAPSHOT_MAX);
        List<Packets.SnapshotEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            TrackedChatMessage m = ordered.get(i);
            entries.add(new Packets.SnapshotEntry(
                m.id, m.sender, m.senderName, m.plainText, m.edited));
        }
        List<DeletedChatMessage> deletedOrdered = new ArrayList<>(DELETED.values());
        deletedOrdered.sort(Comparator.comparingLong(
            (DeletedChatMessage message) -> message.deletedAtMs).reversed());
        int deletedCount = Math.min(deletedOrdered.size(), SNAPSHOT_MAX);
        List<Packets.DeletedSnapshotEntry> deletedEntries = new ArrayList<>(deletedCount);
        for (int i = 0; i < deletedCount; i++) {
            DeletedChatMessage message = deletedOrdered.get(i);
            deletedEntries.add(new Packets.DeletedSnapshotEntry(message.id, message.sender,
                message.senderName, message.plainText));
        }
        Platform.get().sendSnapshot(player, entries, deletedEntries);
    }

    private static TrackedChatMessage findOwnedByFingerprint(ServerPlayer requester,
                                                               long fingerprint,
                                                               int occurrence) {
        if (requester == null) return null;
        UUID self = requester.getUUID();
        List<TrackedChatMessage> matches = new ArrayList<>();
        for (TrackedChatMessage message : MESSAGES.values()) {
            // Encoded fallbacks are emitted only for the requester's provisional own rows.
            // Widening this lookup for operators could delete another player's identical text.
            if (!isOwnFallbackCandidate(self, message.sender)) continue;
            if (Packets.fingerprintPlain(message.plainText) != fingerprint) continue;
            matches.add(message);
        }
        matches.sort(Comparator
            .comparingLong((TrackedChatMessage message) -> message.createdAtMs).reversed()
            .thenComparing(Comparator.comparingLong((TrackedChatMessage message) -> message.id).reversed()));
        return occurrence >= 0 && occurrence < matches.size() ? matches.get(occurrence) : null;
    }

    static boolean isOwnFallbackCandidate(UUID requester, UUID sender) {
        return requester != null && requester.equals(sender);
    }

    private static TrackedChatMessage findOwnedByPlain(ServerPlayer requester, String plain) {
        String needle = sanitize(plain);
        if (needle.isEmpty() || requester == null) return null;
        UUID self = requester.getUUID();
        TrackedChatMessage best = null;
        int hits = 0;
        for (TrackedChatMessage m : MESSAGES.values()) {
            if (m.plainText == null || !m.plainText.equals(needle)) continue;
            if (!self.equals(m.sender) && !requester.hasPermissions(2)) continue;
            if (!self.equals(m.sender)) continue;
            hits++;
            if (best == null || m.createdAtMs > best.createdAtMs) best = m;
        }
        return hits == 1 ? best : null;
    }

    private static void broadcastRegister(TrackedChatMessage msg) {
        MinecraftServer server = Unsend.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Platform.get().sendRegisterMessage(player, msg.id, msg.sender, msg.senderName, msg.plainText);
        }
    }

    private static void sendBulkResult(ServerPlayer player, long requestId,
                                       int requested, int deleted, int skipped) {
        try {
            Platform.get().sendBulkResult(player, requestId, requested, deleted, skipped);
        } catch (Throwable ignored) {
        }
    }

    private static void sendResult(ServerPlayer player, boolean ok, String key) {
        try {
            Platform.get().sendResult(player, ok, key);
        } catch (Throwable ignored) {
        }
    }

    private enum Action { DELETE, EDIT, REPLY, BULK }

    private static boolean allow(UUID player, Action action) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        ActionBucket b = RATE.computeIfAbsent(player, u -> new ActionBucket());
        b.prune(now);
        int limit = switch (action) {
            case DELETE -> RATE_DELETE;
            case EDIT -> RATE_EDIT;
            case REPLY -> RATE_REPLY;
            case BULK -> RATE_BULK;
        };
        List<Long> list = switch (action) {
            case DELETE -> b.deletes;
            case EDIT -> b.edits;
            case REPLY -> b.replies;
            case BULK -> b.bulk;
        };
        if (list.size() >= limit) return false;
        list.add(now);
        return true;
    }

    private static final class ActionBucket {
        final List<Long> deletes = new ArrayList<>();
        final List<Long> edits = new ArrayList<>();
        final List<Long> replies = new ArrayList<>();
        final List<Long> bulk = new ArrayList<>();

        void prune(long now) {
            long cut = now - RATE_WINDOW_MS;
            deletes.removeIf(t -> t < cut);
            edits.removeIf(t -> t < cut);
            replies.removeIf(t -> t < cut);
            bulk.removeIf(t -> t < cut);
        }
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        String t = text.strip();
        if (t.length() > MAX_TEXT) {
            t = t.substring(0, MAX_TEXT);
        }
        return t;
    }

    private static String preview(String text) {
        if (text == null || text.isEmpty() ) return "…";
        String t = text.replace('\n', ' ').strip();
        if (t.length() > 48) {
            return t.substring(0, 48) + "…";
        }
        return t;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        MESSAGES.entrySet().removeIf(e -> now - e.getValue().createdAtMs > MAX_AGE_MS);
        DELETED.entrySet().removeIf(e -> now - e.getValue().deletedAtMs > TOMBSTONE_TTL_MS);
        while (MESSAGES.size() > MAX_TRACKED) {
            Long oldest = null;
            long oldestTs = Long.MAX_VALUE;
            for (Map.Entry<Long, TrackedChatMessage> e : MESSAGES.entrySet()) {
                if (e.getValue().createdAtMs < oldestTs) {
                    oldestTs = e.getValue().createdAtMs;
                    oldest = e.getKey();
                }
            }
            if (oldest == null) break;
            MESSAGES.remove(oldest);
        }
        while (DELETED.size() > MAX_TOMBSTONES) {
            Long oldest = null;
            long oldestTs = Long.MAX_VALUE;
            for (Map.Entry<Long, DeletedChatMessage> e : DELETED.entrySet()) {
                if (e.getValue().deletedAtMs < oldestTs) {
                    oldestTs = e.getValue().deletedAtMs;
                    oldest = e.getKey();
                }
            }
            if (oldest == null) break;
            DELETED.remove(oldest);
        }
    }

    private record DeletedChatMessage(long id, UUID sender, String senderName, String plainText,
                                      long deletedAtMs) {}
}
