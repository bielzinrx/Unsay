package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.network.Packets;

import java.util.UUID;

public final class UnsendClient {
    private UnsendClient() {}

    public static void handleRegister(long messageId, UUID sender, String senderName, String plainText) {
        ClientMessageIndex.onRegisterPacket(messageId, sender, senderName, plainText);
    }

    public static void handleRegister(Packets.RegisterPayload payload) {
        handleRegister(payload.messageId(), payload.sender(), payload.senderName(), payload.plainText());
    }

    public static void handleDelete(long messageId) {
        UnsendHud.onDeleteBroadcast(messageId);
    }

    public static void handleEdit(long messageId, String newText) {
        if (ClientMessageIndex.isTombstoned(messageId)) return;
        var tracked = ClientMessageIndex.get(messageId);
        if (tracked == null || tracked.deleting || ClientMessageIndex.isTombstoned(tracked)) {
            return;
        }
        ClientMessageIndex.applyEdit(messageId, newText);
    }

    public static void handleEdit(Packets.EditPayload payload) {
        handleEdit(payload.messageId(), payload.newText());
    }

    public static void onDisconnect() {
        ClientMessageIndex.clear();
        UnsendComposer.clear();
    }
}
