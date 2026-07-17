package com.bielzinrx.unsend.server;

import net.minecraft.server.level.ServerPlayer;

public final class UnsendServer {
    private UnsendServer() {}

    public static void onPlayerChat(ServerPlayer player, String rawText) {
        if (player == null || rawText == null || rawText.isBlank()) {
            return;
        }
        ChatMessageTracker.register(player, rawText);
    }

    public static void onDeleteRequest(ServerPlayer player, long messageId) {
        if (player == null) return;
        ChatMessageTracker.requestDelete(player, messageId);
    }

    public static void onEditRequest(ServerPlayer player, long messageId, String newText) {
        if (player == null) return;
        ChatMessageTracker.requestEdit(player, messageId, newText);
    }

    public static void onReplyRequest(ServerPlayer player, long targetId, String text,
                                      String targetName, String targetPreview) {
        if (player == null) return;
        ChatMessageTracker.requestReply(player, targetId, text, targetName, targetPreview);
    }
}
