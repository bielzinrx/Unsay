package com.bielzinrx.unsend.fabric;

import com.bielzinrx.unsend.client.UnsendClient;
import com.bielzinrx.unsend.fabric.platform.FabricPlatformHelper;
import com.bielzinrx.unsend.network.PacketIds;
import com.bielzinrx.unsend.network.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public final class UnsendFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricPlatformHelper.setClientDeleteSender(messageId -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            Packets.writeDeleteC2S(buf, messageId);
            ClientPlayNetworking.send(PacketIds.DELETE_C2S, buf);
        });
        FabricPlatformHelper.setClientEditSender((messageId, text) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            Packets.writeEdit(buf, messageId, text);
            ClientPlayNetworking.send(PacketIds.EDIT_C2S, buf);
        });
        FabricPlatformHelper.setClientReplySender((targetId, text, targetName, targetPreview) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            Packets.writeReply(buf, targetId, text, targetName, targetPreview);
            ClientPlayNetworking.send(PacketIds.REPLY_C2S, buf);
        });

        ClientPlayNetworking.registerGlobalReceiver(PacketIds.REGISTER, (client, handler, buf, responseSender) -> {
            Packets.RegisterPayload payload = Packets.readRegister(buf);
            client.execute(() -> UnsendClient.handleRegister(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(PacketIds.DELETE_S2C, (client, handler, buf, responseSender) -> {
            Packets.DeleteS2CPayload payload = Packets.readDeleteS2C(buf);
            client.execute(() -> UnsendClient.handleDelete(payload.messageId(), payload.sender(), payload.plainText()));
        });

        ClientPlayNetworking.registerGlobalReceiver(PacketIds.EDIT_S2C, (client, handler, buf, responseSender) -> {
            Packets.EditPayload payload = Packets.readEdit(buf);
            client.execute(() -> UnsendClient.handleEdit(payload));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(UnsendClient::onDisconnect));
    }
}
