package com.bielzinrx.unsend.fabric;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.fabric.platform.FabricPlatformHelper;
import com.bielzinrx.unsend.network.PacketIds;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.platform.Platform;
import com.bielzinrx.unsend.server.UnsendServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class UnsendFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.bootstrap(new FabricPlatformHelper());
        Unsend.init();

        ServerLifecycleEvents.SERVER_STARTING.register(Unsend::setServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Unsend.onServerStop());

        ServerPlayNetworking.registerGlobalReceiver(PacketIds.DELETE_C2S, (server, player, handler, buf, responseSender) -> {
            long messageId = Packets.readDeleteC2S(buf);
            server.execute(() -> UnsendServer.onDeleteRequest(player, messageId));
        });

        ServerPlayNetworking.registerGlobalReceiver(PacketIds.EDIT_C2S, (server, player, handler, buf, responseSender) -> {
            Packets.EditPayload payload = Packets.readEdit(buf);
            server.execute(() -> UnsendServer.onEditRequest(player, payload.messageId(), payload.newText()));
        });

        ServerPlayNetworking.registerGlobalReceiver(PacketIds.REPLY_C2S, (server, player, handler, buf, responseSender) -> {
            Packets.ReplyPayload payload = Packets.readReply(buf);
            server.execute(() -> UnsendServer.onReplyRequest(
                player, payload.targetId(), payload.text(), payload.targetName(), payload.targetPreview()));
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = null;
            if (message.signedContent() != null) {
                raw = message.signedContent().plain();
            }
            if (raw == null || raw.isBlank()) {
                raw = message.serverContent() != null ? message.serverContent().getString() : "";
            }
            String finalRaw = raw;
            sender.server.execute(() -> UnsendServer.onPlayerChat(sender, finalRaw));
        });
    }
}
