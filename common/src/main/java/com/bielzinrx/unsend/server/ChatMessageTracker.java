package com.bielzinrx.unsend.server;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatMessageTracker {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<Long, TrackedChatMessage> MESSAGES = new ConcurrentHashMap<>();

    private static final Map<Long, Long> DELETED_AT_MS = new ConcurrentHashMap<>();
    private static final int MAX_TRACKED = 512;
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final long TOMBSTONE_TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_TEXT = 256;

    private ChatMessageTracker() {}

    public static void clear() {
        MESSAGES.clear();
        DELETED_AT_MS.clear();
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
        if (isDeleted(messageId)) {
            return false;
        }
        TrackedChatMessage msg = MESSAGES.get(messageId);
        if (msg == null || !canDelete(requester, msg)) {
            return false;
        }

        MESSAGES.remove(messageId);
        DELETED_AT_MS.put(messageId, System.currentTimeMillis());
        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.get().sendDeleteBroadcast(player, messageId);
            }
        }
        Unsend.LOGGER.info("[Unsend] Message {} deleted by {}", messageId, requester.getGameProfile().getName());
        return true;
    }

    public static boolean requestEdit(ServerPlayer requester, long messageId, String newText) {

        if (isDeleted(messageId)) {
            Unsend.LOGGER.info("[Unsend] Rejected edit of deleted message {} by {}",
                messageId, requester != null ? requester.getGameProfile().getName() : "?");
            return false;
        }
        TrackedChatMessage msg = MESSAGES.get(messageId);
        if (msg == null || !canEdit(requester, msg)) {
            return false;
        }
        String text = sanitize(newText);

        if (text.isEmpty()) {
            return requestDelete(requester, messageId);
        }
        msg.plainText = text;
        msg.edited = true;

        MinecraftServer server = Unsend.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.get().sendEditBroadcast(player, messageId, text);
            }
        }
        Unsend.LOGGER.info("[Unsend] Message {} edited by {}", messageId, requester.getGameProfile().getName());
        return true;
    }

    public static boolean requestReply(ServerPlayer sender, long targetId, String replyText,
                                       String fallbackName, String fallbackPreview) {
        String text = sanitize(replyText);
        if (sender == null || text.isEmpty()) {
            return false;
        }

        TrackedChatMessage target = MESSAGES.get(targetId);
        if (target == null && fallbackPreview != null && !fallbackPreview.isBlank()) {
            target = findRecentByPlain(fallbackPreview);
        }

        String targetName;
        String previewText;
        if (target != null) {
            targetName = target.senderName == null || target.senderName.isEmpty() ? "?" : target.senderName;
            previewText = preview(target.plainText);
        } else if (isDeleted(targetId)) {
            targetName = (fallbackName != null && !fallbackName.isBlank()) ? fallbackName : "?";
            previewText = Component.translatable("unsend.message.deleted_placeholder").getString();
        } else {
            // Not in server map — client still saw the line; do NOT label as deleted
            targetName = (fallbackName != null && !fallbackName.isBlank()) ? fallbackName : "?";
            String fp = fallbackPreview == null ? "" : fallbackPreview.strip();
            previewText = fp.isEmpty() ? "…" : preview(fp);
        }

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

    private static TrackedChatMessage findRecentByPlain(String plain) {
        String needle = sanitize(plain);
        if (needle.isEmpty()) return null;
        TrackedChatMessage best = null;
        for (TrackedChatMessage m : MESSAGES.values()) {
            if (m.plainText == null) continue;
            if (!m.plainText.equals(needle)) continue;
            if (best == null || m.createdAtMs > best.createdAtMs) best = m;
        }
        return best;
    }

    private static void broadcastRegister(TrackedChatMessage msg) {
        MinecraftServer server = Unsend.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Platform.get().sendRegisterMessage(player, msg.id, msg.sender, msg.senderName, msg.plainText);
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
        if (text == null || text.isEmpty()) return "…";
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
