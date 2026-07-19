package com.bielzinrx.unsend.forge.platform;

import com.bielzinrx.unsend.forge.network.ForgeNetwork;
import com.bielzinrx.unsend.platform.IPlatformHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

public final class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendDeleteRequestToServer(long messageId) {
        ForgeNetwork.sendDeleteToServer(messageId);
    }

    @Override
    public void sendEditRequestToServer(long messageId, String newText) {
        ForgeNetwork.sendEditToServer(messageId, newText);
    }

    @Override
    public void sendReplyToServer(long targetId, String text, String targetName, String targetPreview) {
        ForgeNetwork.sendReplyToServer(targetId, text, targetName, targetPreview);
    }

    @Override
    public void sendRegisterMessage(ServerPlayer target, long messageId, UUID sender, String senderName, String plainText) {
        ForgeNetwork.sendRegister(target, messageId, sender, senderName, plainText);
    }

    @Override
    public void sendDeleteBroadcast(ServerPlayer target, long messageId, UUID sender, String plainText) {
        ForgeNetwork.sendDelete(target, messageId, sender, plainText);
    }

    @Override
    public void sendEditBroadcast(ServerPlayer target, long messageId, String newText) {
        ForgeNetwork.sendEdit(target, messageId, newText);
    }
}
