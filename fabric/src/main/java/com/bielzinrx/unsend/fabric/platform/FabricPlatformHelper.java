package com.bielzinrx.unsend.fabric.platform;

import com.bielzinrx.unsend.network.PacketIds;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.platform.IPlatformHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

public final class FabricPlatformHelper implements IPlatformHelper {
    private static LongConsumer clientDeleteSender = id -> {};
    private static BiConsumer<Long, String> clientEditSender = (id, text) -> {};

    @FunctionalInterface
    public interface ReplySender {
        void send(long targetId, String text, String targetName, String targetPreview);
    }

    private static ReplySender clientReplySender = (id, text, name, preview) -> {};

    public static void setClientDeleteSender(LongConsumer sender) {
        clientDeleteSender = sender != null ? sender : id -> {};
    }

    public static void setClientEditSender(BiConsumer<Long, String> sender) {
        clientEditSender = sender != null ? sender : (id, text) -> {};
    }

    public static void setClientReplySender(ReplySender sender) {
        clientReplySender = sender != null ? sender : (id, text, name, preview) -> {};
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void sendDeleteRequestToServer(long messageId) {
        clientDeleteSender.accept(messageId);
    }

    @Override
    public void sendEditRequestToServer(long messageId, String newText) {
        clientEditSender.accept(messageId, newText);
    }

    @Override
    public void sendReplyToServer(long targetId, String text, String targetName, String targetPreview) {
        clientReplySender.send(targetId, text, targetName, targetPreview);
    }

    @Override
    public void sendRegisterMessage(ServerPlayer target, long messageId, UUID sender, String senderName, String plainText) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeRegister(buf, messageId, sender, senderName, plainText);
        ServerPlayNetworking.send(target, PacketIds.REGISTER, buf);
    }

    @Override
    public void sendDeleteBroadcast(ServerPlayer target, long messageId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeDelete(buf, messageId);
        ServerPlayNetworking.send(target, PacketIds.DELETE_S2C, buf);
    }

    @Override
    public void sendEditBroadcast(ServerPlayer target, long messageId, String newText) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeEdit(buf, messageId, newText);
        ServerPlayNetworking.send(target, PacketIds.EDIT_S2C, buf);
    }
}
