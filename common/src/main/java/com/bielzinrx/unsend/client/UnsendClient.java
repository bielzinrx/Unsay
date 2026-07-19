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
        handleDelete(messageId, null, null);
    }

    public static void handleDelete(long messageId, UUID sender, String plainText) {
        UnsendHud.onDeleteBroadcast(messageId, sender, plainText);
    }

    public static void handleEdit(long messageId, String newText) {
        if (ClientMessageIndex.isTombstoned(messageId)) return;
        var tracked = ClientMessageIndex.get(messageId);
        if (tracked == null || tracked.deleting || ClientMessageIndex.isTombstoned(tracked)) {
            // Still try HUD wipe/edit by id alone is impossible without text; skip
            return;
        }
        // Capture pin BEFORE plainText changes so multi-identical lines stay correct
        var pin = ChatHudEditor.capturePin(tracked);
        ClientMessageIndex.applyEdit(messageId, newText, pin);
    }

    public static void handleEdit(Packets.EditPayload payload) {
        handleEdit(payload.messageId(), payload.newText());
    }

    public static void onDisconnect() {
        ClientMessageIndex.clear();
        UnsendComposer.clear();
    }
}
