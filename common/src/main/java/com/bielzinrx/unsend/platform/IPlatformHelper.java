package com.bielzinrx.unsend.platform;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public interface IPlatformHelper {
    boolean isModLoaded(String modId);

    void sendDeleteRequestToServer(long messageId);

    void sendEditRequestToServer(long messageId, String newText);

    void sendReplyToServer(long targetId, String text, String targetName, String targetPreview);

    void sendRegisterMessage(ServerPlayer target, long messageId, UUID sender, String senderName, String plainText);

    void sendDeleteBroadcast(ServerPlayer target, long messageId, UUID sender, String plainText);

    void sendEditBroadcast(ServerPlayer target, long messageId, String newText);
}
