package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudOwnLine;
import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.List;
import java.util.UUID;

public final class UnsendComposer {
    private static boolean editing;
    private static boolean replying;
    private static long editingId = -1L;
    private static long replyToId = -1L;
    private static String replyPreview = "";
    private static String replyAuthor = "";
    /** Cursor in {@link ChatHudEditor#listOwnHudLines()} (newest → oldest). */
    private static int historyIndex = -1;

    /** Fingerprint of the HUD line opened for edit (source of truth for identical bodies). */
    private static HudPin editPin = null;

    private UnsendComposer() {}

    public static void clear() {
        editing = false;
        replying = false;
        editingId = -1L;
        replyToId = -1L;
        replyPreview = "";
        replyAuthor = "";
        historyIndex = -1;
        editPin = null;
    }

    public static boolean isEditing() {
        return editing;
    }

    public static boolean isReplying() {
        return replying;
    }

    public static long getEditingId() {
        return editingId;
    }

    public static String getReplyLabel() {
        if (!replying) return "";
        String prev = replyPreview == null ? "" : replyPreview;
        if (prev.length() > 40) prev = prev.substring(0, 40) + "…";
        return "↳ " + replyAuthor + " · " + prev;
    }

    public static String getStatusLabel() {
        if (editing) {
            if (!isEditableTarget(editingId)) {
                abortEditQuiet(null);
                return "";
            }
            return Component.translatable("unsend.status.editing").getString();
        }
        if (replying) {
            if (!isReplyTargetAlive(replyToId)) {
                clear();
                return "";
            }
            return Component.translatable("unsend.status.replying", getReplyLabel()).getString();
        }
        return "";
    }

    public static void beginReply(ClientTrackedMessage target) {
        if (target == null || target.deleting) return;
        editing = false;
        editingId = -1L;
        historyIndex = -1;
        editPin = null;
        replying = true;
        // Keep exact server id when present — do not re-resolve by plain text
        if (target.id >= 0) {
            replyToId = target.id;
        } else {
            long resolved = resolveServerId(target.id);
            replyToId = resolved;
        }
        replyAuthor = target.senderName != null && !target.senderName.isEmpty()
            ? target.senderName
            : shortUuid(target.sender);
        // Full plain for server citation (not another twin)
        String t = target.plainText == null ? "" : target.plainText.replace('\n', ' ').strip();
        replyPreview = ClientMessageIndex.stripEditedBadge(t);
    }

    public static boolean beginEdit(ClientTrackedMessage target, ChatScreen screen) {
        return beginEdit(target, screen, null);
    }

    public static boolean beginEdit(ClientTrackedMessage target, ChatScreen screen, HudPin forcedPin) {
        if (target == null || target.deleting || !isEditableTarget(target.id)) {
            return false;
        }
        // Prefer the exact HUD pin from the clicked icon when present
        if (forcedPin != null && forcedPin.isValid()) {
            return beginEditWithPin(target, forcedPin, -1, screen);
        }
        List<HudOwnLine> hud = ChatHudEditor.listOwnHudLines();
        HudOwnLine match = findHudLineForTracked(hud, target);
        if (match != null) {
            return beginEditFromHud(match, indexOfHudLine(hud, match), screen);
        }
        return beginEditWithPin(target, ChatHudEditor.capturePin(target), -1, screen);
    }

    /** Open edit on a concrete HUD line (navigation / pencil). */
    private static boolean beginEditFromHud(HudOwnLine line, int hudIndex, ChatScreen screen) {
        if (line == null || line.tracked == null) return false;
        if (line.tracked.deleting || ClientMessageIndex.isTombstoned(line.tracked)) return false;

        List<HudOwnLine> hud = ChatHudEditor.listOwnHudLines();
        int rank = rankAmongSamePlainOnHud(hud, line);
        HudPin pin = new HudPin(
            rank,
            line.gui != null ? line.gui.addedTime() : line.tracked.addedTime,
            line.gui != null ? line.gui.headerSignature() : line.tracked.signature,
            line.fullLine,
            line.plain
        );
        int idx = hudIndex >= 0 ? hudIndex : indexOfHudLine(hud, line);
        return beginEditWithPin(line.tracked, pin, idx, screen);
    }

    private static boolean beginEditWithPin(ClientTrackedMessage target, HudPin pin,
                                           int hudIndex, ChatScreen screen) {
        if (target == null || target.deleting || !isEditableTarget(target.id)) {
            return false;
        }
        replying = false;
        replyToId = -1L;
        replyPreview = "";
        replyAuthor = "";
        editing = true;

        long stableId = resolveServerId(target.id);
        editingId = stableId >= 0 ? stableId : target.id;
        editPin = pin != null ? pin : ChatHudEditor.capturePin(target);

        if (editPin != null && editPin.isValid()) {
            ClientTrackedMessage live = ClientMessageIndex.get(editingId);
            if (live == null) live = target;
            if (editPin.plain != null) live.plainText = editPin.plain;
            if (editPin.addedTime != Integer.MIN_VALUE) live.addedTime = editPin.addedTime;
            if (editPin.signature != null) live.signature = editPin.signature;
        }

        try {
            if (hudIndex >= 0) {
                historyIndex = hudIndex;
            } else {
                List<HudOwnLine> hud = ChatHudEditor.listOwnHudLines();
                historyIndex = indexOfHudById(hud, editingId);
                if (historyIndex < 0) historyIndex = indexOfHudById(hud, target.id);
            }
            EditBox input = ChatScreenAccess.getInput(screen);
            if (input != null) {
                String fill = editPin != null && editPin.plain != null
                    ? editPin.plain
                    : (target.plainText == null ? "" : target.plainText);
                input.setValue(ClientMessageIndex.stripEditedBadge(fill));
                input.moveCursorToEnd();
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Provisional local id was upgraded to a server id — keep edit/reply mode alive. */
    public static void remapTrackedId(long fromId, long toId) {
        if (fromId == toId) return;
        if (editing && editingId == fromId) editingId = toId;
        if (replying && replyToId == fromId) replyToId = toId;
    }

    /** Navigate own messages for edit using the live HUD order (newest → oldest). */
    public static boolean tryNavigateOwnHistory(ChatScreen screen, int delta) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || delta == 0 || screen == null) return false;

            List<HudOwnLine> hud = ChatHudEditor.listOwnHudLines();
            if (hud.isEmpty()) return false;

            int cur;
            if (!editing) {
                if (delta < 0) return false;
                EditBox input = ChatScreenAccess.getInput(screen);
                if (input == null) return false;
                String val = input.getValue();
                if (val != null && !val.isEmpty()) return false;
                cur = -1;
            } else {
                cur = resolveHudCursor(hud);
            }

            int step = delta > 0 ? 1 : -1;
            int next = cur + step;

            while (next >= 0 && next < hud.size()) {
                HudOwnLine line = hud.get(next);
                if (line == null || line.tracked == null
                    || line.tracked.deleting || ClientMessageIndex.isTombstoned(line.tracked)) {
                    next += step;
                    continue;
                }
                if (!beginEditFromHud(line, next, screen)) {
                    next += step;
                    continue;
                }
                historyIndex = next;
                return true;
            }

            if (next < 0) {
                abortEditQuiet(screen);
                return true;
            }
            if (next >= hud.size()) {
                return editing;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void cancelWithEsc(ChatScreen screen) {
        boolean wasEdit = editing;
        clear();
        if (wasEdit) {
            tryClearInput(screen);
        }
    }

    public static void onMessageDeleted(long messageId) {
        long resolved = resolveServerId(messageId);
        ClientMessageIndex.tombstoneId(messageId);
        if (resolved != messageId) ClientMessageIndex.tombstoneId(resolved);

        if (editing && (editingId == messageId || editingId == resolved
            || resolveServerId(editingId) == messageId || resolveServerId(editingId) == resolved
            || ClientMessageIndex.isTombstoned(editingId))) {
            abortEditQuiet(null);
        }

        if (replying && (replyToId == messageId || replyToId == resolved
            || resolveServerId(replyToId) == messageId || resolveServerId(replyToId) == resolved
            || ClientMessageIndex.isTombstoned(replyToId))) {
            clear();
        }
    }

    public static boolean tryHandleSend(String message) {
        try {
            String text = message == null ? "" : message.strip();

            if (text.startsWith("/")) {
                clear();
                return false;
            }

            if (editing) {
                long localId = editingId;
                long id = resolveServerId(localId);
                HudPin pin = editPin;

                if (!isEditableTarget(localId) && !isEditableTarget(id)) {
                    abortEditQuiet(null);
                    return true;
                }

                if (text.isEmpty()) {
                    clear();
                    try {
                        ClientDelete.deleteFromEdit(localId, id);
                    } catch (Throwable ignored) {
                    }
                    tryClearInput(null);
                    return true;
                }

                ClientTrackedMessage tracked = ClientMessageIndex.get(id >= 0 ? id : localId);
                if (tracked == null || tracked.deleting) {
                    abortEditQuiet(null);
                    return true;
                }

                if (pin != null && pin.isValid()) {
                    if (pin.plain != null) tracked.plainText = pin.plain;
                    if (pin.addedTime != Integer.MIN_VALUE) tracked.addedTime = pin.addedTime;
                    if (pin.signature != null) tracked.signature = pin.signature;
                }

                long applyId = id >= 0 ? id : localId;
                clear();
                ClientMessageIndex.applyEdit(applyId, text, pin);
                if (id >= 0) {
                    Platform.get().sendEditRequestToServer(id, text);
                }
                return true;
            }

            if (replying) {
                if (text.isEmpty()) {
                    return false;
                }
                if (!isReplyTargetAlive(replyToId)) {
                    clear();
                    return false;
                }
                long target = resolveServerId(replyToId);
                String name = replyAuthor;
                String preview = replyPreview;
                clear();
                Platform.get().sendReplyToServer(target, text, name, preview);
                return true;
            }

            return false;
        } catch (Throwable t) {
            clear();
            return true;
        }
    }

    private static void tryClearInput(ChatScreen screen) {
        try {
            ChatScreen chat = screen;
            if (chat == null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.screen instanceof ChatScreen) {
                    chat = (ChatScreen) mc.screen;
                }
            }
            if (chat == null) return;
            EditBox input = ChatScreenAccess.getInput(chat);
            if (input != null) input.setValue("");
        } catch (Throwable ignored) {
        }
    }

    private static int resolveHudCursor(List<HudOwnLine> hud) {
        if (historyIndex >= 0 && historyIndex < hud.size()) {
            HudOwnLine at = hud.get(historyIndex);
            if (at != null && at.tracked != null
                && !at.tracked.deleting && !ClientMessageIndex.isTombstoned(at.tracked)) {
                if (at.tracked.id == editingId) return historyIndex;
                if (editPin != null && editPin.fullLine != null
                    && editPin.fullLine.equals(at.fullLine)
                    && editPin.addedTime == (at.gui != null ? at.gui.addedTime() : Integer.MIN_VALUE)) {
                    return historyIndex;
                }
            }
        }
        int byId = indexOfHudById(hud, editingId);
        if (byId >= 0) return byId;
        if (historyIndex >= 0 && historyIndex < hud.size()) return historyIndex;
        return 0;
    }

    private static int indexOfHudById(List<HudOwnLine> hud, long id) {
        for (int i = 0; i < hud.size(); i++) {
            HudOwnLine line = hud.get(i);
            if (line != null && line.tracked != null && line.tracked.id == id) return i;
        }
        long resolved = resolveServerId(id);
        if (resolved != id) {
            for (int i = 0; i < hud.size(); i++) {
                HudOwnLine line = hud.get(i);
                if (line != null && line.tracked != null && line.tracked.id == resolved) return i;
            }
        }
        return -1;
    }

    private static int indexOfHudLine(List<HudOwnLine> hud, HudOwnLine line) {
        if (line == null) return -1;
        for (int i = 0; i < hud.size(); i++) {
            HudOwnLine o = hud.get(i);
            if (o == null) continue;
            if (o.allIndex == line.allIndex) return i;
            if (o.tracked != null && line.tracked != null && o.tracked.id == line.tracked.id) return i;
        }
        return -1;
    }

    /** Newest-first rank among HUD own lines with the same plain as {@code line}. */
    private static int rankAmongSamePlainOnHud(List<HudOwnLine> hud, HudOwnLine line) {
        if (line == null || line.plain == null) return -1;
        int rank = 0;
        for (HudOwnLine o : hud) {
            if (o == null || o.plain == null) continue;
            if (o.allIndex == line.allIndex) return rank;
            if (line.plain.equals(o.plain)) rank++;
        }
        return rank;
    }

    private static HudOwnLine findHudLineForTracked(List<HudOwnLine> hud, ClientTrackedMessage target) {
        if (target == null || hud == null) return null;
        HudOwnLine best = null;
        int bestScore = -1;
        for (HudOwnLine line : hud) {
            if (line == null || line.tracked == null) continue;
            int score = 0;
            if (line.tracked.id == target.id) score += 1000;
            if (target.signature != null && line.gui != null
                && target.signature.equals(line.gui.headerSignature())) score += 500;
            if (line.gui != null && target.addedTime == line.gui.addedTime()
                && target.plainText != null && target.plainText.equals(line.plain)) score += 100;
            if (target.plainText != null && target.plainText.equals(line.plain)
                && line.tracked.id == target.id) score += 50;
            if (score > bestScore) {
                bestScore = score;
                best = line;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static boolean isEditableTarget(long id) {
        if (id == Long.MIN_VALUE) return false;
        if (ClientMessageIndex.isTombstoned(id)) return false;
        ClientTrackedMessage t = ClientMessageIndex.get(id);
        if (t != null) {
            return !t.deleting && !ClientMessageIndex.isTombstoned(t);
        }
        long resolved = resolveServerId(id);
        if (resolved != id) {
            if (ClientMessageIndex.isTombstoned(resolved)) return false;
            t = ClientMessageIndex.get(resolved);
            if (t != null && !t.deleting && !ClientMessageIndex.isTombstoned(t)) {
                remapTrackedId(id, resolved);
                return true;
            }
        }
        return false;
    }

    private static boolean isReplyTargetAlive(long id) {
        if (id == Long.MIN_VALUE) return false;
        if (ClientMessageIndex.isTombstoned(id)) return false;
        ClientTrackedMessage t = ClientMessageIndex.get(id);
        if (t != null) {
            return !t.deleting && !ClientMessageIndex.isTombstoned(t);
        }
        long resolved = resolveServerId(id);
        if (resolved != id) {
            if (ClientMessageIndex.isTombstoned(resolved)) return false;
            t = ClientMessageIndex.get(resolved);
            return t != null && !t.deleting && !ClientMessageIndex.isTombstoned(t);
        }
        return false;
    }

    private static void abortEditQuiet(ChatScreen screen) {
        clear();
        tryClearInput(screen);
    }

    private static long resolveServerId(long id) {
        if (id >= 0) return id;
        ClientTrackedMessage local = ClientMessageIndex.get(id);
        if (local == null) return id;
        for (ClientTrackedMessage m : ClientMessageIndex.all()) {
            if (m.id < 0 || m.deleting || !m.isOwnedBy(local.sender)) continue;
            if (local.signature != null && local.signature.equals(m.signature)) {
                return m.id;
            }
            if (local.plainText != null && local.plainText.equals(m.plainText)
                && m.addedTime == local.addedTime) {
                return m.id;
            }
        }
        return id;
    }

    private static String shortUuid(UUID id) {
        if (id == null) return "?";
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }
}
