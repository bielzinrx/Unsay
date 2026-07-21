package com.bielzinrx.unsend.fabric.platform;

import com.bielzinrx.unsend.network.PacketIds;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.platform.IPlatformHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class FabricPlatformHelper implements IPlatformHelper {
    @FunctionalInterface
    public interface DeleteSender {
        void send(long messageId, String plainFallback);
    }

    @FunctionalInterface
    public interface ReplySender {
        void send(long targetId, String text, String targetName, String targetPreview);
    }

    private static DeleteSender clientDeleteSender = (id, plain) -> {};
    private static BiConsumer<Long, String> clientEditSender = (id, text) -> {};
    private static ReplySender clientReplySender = (id, text, name, preview) -> {};

    public static void setClientDeleteSender(DeleteSender sender) {
        clientDeleteSender = sender != null ? sender : (id, plain) -> {};
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
    public void sendDeleteRequestToServer(long messageId, String plainFallback) {
        clientDeleteSender.send(messageId, plainFallback);
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
    public void sendDeleteBroadcast(ServerPlayer target, long messageId, UUID sender, String plainText) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeDeleteS2C(buf, messageId, sender, plainText);
        ServerPlayNetworking.send(target, PacketIds.DELETE_S2C, buf);
    }

    @Override
    public void sendEditBroadcast(ServerPlayer target, long messageId, String newText, String oldPlain, UUID sender) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeEditS2C(buf, messageId, newText, oldPlain, sender);
        ServerPlayNetworking.send(target, PacketIds.EDIT_S2C, buf);
    }

    @Override
    public void sendSnapshot(ServerPlayer target, List<Packets.SnapshotEntry> entries) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeSnapshot(buf, entries);
        ServerPlayNetworking.send(target, PacketIds.SNAPSHOT_S2C, buf);
    }

    @Override
    public void sendResult(ServerPlayer target, boolean ok, String messageKey) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Packets.writeResult(buf, ok, messageKey);
        ServerPlayNetworking.send(target, PacketIds.RESULT_S2C, buf);
    }
}
