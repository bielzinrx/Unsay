package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
        handleEdit(messageId, newText, null, null);
    }

    public static void handleEdit(long messageId, String newText, String oldPlain, UUID sender) {
        if (ClientMessageIndex.isTombstoned(messageId)) return;

        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        if (tracked == null && oldPlain != null && !oldPlain.isBlank()) {
            tracked = ClientMessageIndex.findBestForRemote(sender, oldPlain);
            if (tracked != null && tracked.id < 0) {
                ClientMessageIndex.promoteToServerId(tracked, messageId, sender, oldPlain);
                tracked = ClientMessageIndex.get(messageId);
            }
        }
        if (tracked == null || tracked.deleting || ClientMessageIndex.isTombstoned(tracked)) {
            if (oldPlain != null && !oldPlain.isBlank() && newText != null && !newText.isBlank()) {
                ClientMessageIndex.applyRemoteEditLoose(sender, oldPlain, newText, messageId);
            }
            return;
        }

        // Local optimistic apply already ran → S2C must NOT re-match among remaining
        // identical "aaa" twins (would also edit the line above).
        String cur = tracked.plainText == null ? "" : tracked.plainText;
        if (tracked.edited && newText != null && newText.equals(cur)) {
            return;
        }

        // Keep the signature we already bound (local apply) — stronger than re-rank
        var keepSig = tracked.signature;
        int keepTick = tracked.addedTime;
        String matchPlain = (oldPlain != null && !oldPlain.isBlank()) ? oldPlain : cur;
        if (oldPlain != null && !oldPlain.isBlank()) {
            tracked.plainText = oldPlain;
        }
        HudPin pin = ChatHudEditor.capturePin(tracked);
        if (keepSig != null || keepTick != Integer.MIN_VALUE) {
            pin = new HudPin(
                pin != null ? pin.rank : -1,
                keepTick != Integer.MIN_VALUE ? keepTick : (pin != null ? pin.addedTime : Integer.MIN_VALUE),
                keepSig != null ? keepSig : (pin != null ? pin.signature : null),
                pin != null ? pin.fullLine : null,
                matchPlain
            );
        }
        ClientMessageIndex.applyEdit(messageId, newText, pin);
    }

    public static void handleEdit(Packets.EditPayload payload) {
        handleEdit(payload.messageId(), payload.newText());
    }

    public static void handleSnapshot(Packets.SnapshotPayload payload) {
        if (payload == null || payload.entries() == null) return;
        ClientMessageIndex.applySnapshot(payload.entries());
    }

    public static void handleResult(boolean ok, String messageKey) {
        // Success toasts are noisy; only show failures / rate-limit
        if (ok) return;
        if (messageKey == null || messageKey.isBlank()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.displayClientMessage(Component.translatable(messageKey), true);
        } catch (Throwable ignored) {
        }
    }

    public static void onDisconnect() {
        ClientMessageIndex.clear();
        UnsendComposer.clear();
    }
}
