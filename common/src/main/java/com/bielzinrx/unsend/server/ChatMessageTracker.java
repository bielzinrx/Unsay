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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatMessageTracker {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<Long, TrackedChatMessage> MESSAGES = new ConcurrentHashMap<>();
    private static final Map<Long, Long> DELETED_AT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, ActionBucket> RATE = new ConcurrentHashMap<>();

    private static final int MAX_TRACKED = 512;
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final long TOMBSTONE_TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_TEXT = 256;
    private static final int SNAPSHOT_MAX = 200;

    private static final int RATE_WINDOW_MS = 10_000;
    private static final int RATE_DELETE = 8;
    private static final int RATE_EDIT = 10;
    private static final int RATE_REPLY = 12;

    private ChatMessageTracker() {}

    public static void clear() {
        MESSAGES.clear();
        DELETED_AT_MS.clear();
        RATE.clear();
    }

    public static boolean isDeleted(long messageId) {
        return DELETED_AT_MS.containsKey(messageId);
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
            sendResult(requester, false, "unsend.error.already_gone");
            return false;
        }

        TrackedChatMessage msg = messageId > 0 ? MESSAGES.get(messageId) : null;
        if (msg == null && plainFallback != null && !plainFallback.isBlank()) {
            msg = findOwnedByPlain(requester, plainFallback);
        }
        if (msg == null || !canDelete(requester, msg)) {
            sendResult(requester, false, "unsend.error.not_found");
            return false;
        }

        UUID sender = msg.sender;
        String plain = msg.plainText == null ? "" : msg.plainText;
        long id = msg.id;
        MESSAGES.remove(id);
        DELETED_AT_MS.put(id, System.currentTimeMillis());

        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.get().sendDeleteBroadcast(player, id, sender, plain);
            }
        }
        Unsend.LOGGER.info("[Unsend] Message {} deleted by {}", id, requester.getGameProfile().getName());
        sendResult(requester, true, "unsend.result.deleted");
        return true;
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
        Platform.get().sendSnapshot(player, entries);
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

    private static void sendResult(ServerPlayer player, boolean ok, String key) {
        try {
            Platform.get().sendResult(player, ok, key);
        } catch (Throwable ignored) {
        }
    }

    private enum Action { DELETE, EDIT, REPLY }

    private static boolean allow(UUID player, Action action) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        ActionBucket b = RATE.computeIfAbsent(player, u -> new ActionBucket());
        b.prune(now);
        int limit = switch (action) {
            case DELETE -> RATE_DELETE;
            case EDIT -> RATE_EDIT;
            case REPLY -> RATE_REPLY;
        };
        List<Long> list = switch (action) {
            case DELETE -> b.deletes;
            case EDIT -> b.edits;
            case REPLY -> b.replies;
        };
        if (list.size() >= limit) return false;
        list.add(now);
        return true;
    }

    private static final class ActionBucket {
        final List<Long> deletes = new ArrayList<>();
        final List<Long> edits = new ArrayList<>();
        final List<Long> replies = new ArrayList<>();

        void prune(long now) {
            long cut = now - RATE_WINDOW_MS;
            deletes.removeIf(t -> t < cut);
            edits.removeIf(t -> t < cut);
            replies.removeIf(t -> t < cut);
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
        DELETED_AT_MS.entrySet().removeIf(e -> now - e.getValue() > TOMBSTONE_TTL_MS);
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
    }
}
