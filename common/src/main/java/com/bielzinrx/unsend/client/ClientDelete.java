package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class ClientDelete {
    private static HudPin pendingPin;
    private static float pendingOriginX = Float.NaN;
    private static float pendingOriginY = Float.NaN;
    private static long pendingLocalId = Long.MIN_VALUE;

    private ClientDelete() {}

    public static void deleteById(long messageId) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        if (tracked != null) requestDelete(tracked, Float.NaN, Float.NaN, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked) {
        requestDelete(tracked, Float.NaN, Float.NaN, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked, float fromX, float fromY) {
        requestDelete(tracked, fromX, fromY, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked, float fromX, float fromY,
                                     HudPin pin) {
        requestDelete(tracked, fromX, fromY, pin);
    }

    public static void applyRemoteDelete(long messageId) {
        applyRemoteDelete(messageId, null, null);
    }

    public static void applyRemoteDelete(long messageId, UUID sender, String plainText) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        boolean pendingMatch = tracked != null && (tracked.deleting || pendingLocalId == tracked.id);
        if (tracked == null && plainText != null && !plainText.isBlank()) {
            tracked = ClientMessageIndex.findBestForRemote(sender, plainText);
            pendingMatch = tracked != null && (tracked.deleting || pendingLocalId == tracked.id);
            if (tracked != null && tracked.id < 0 && messageId >= 0) {
                ClientMessageIndex.promoteToServerId(tracked, messageId, sender, plainText);
                tracked = ClientMessageIndex.get(messageId);
            }
        }
        pendingMatch = pendingMatch
            || ClientActionState.matches(ClientActionState.Type.DELETE, messageId)
            || pendingLocalId == messageId;

        if (pendingMatch) ClientActionState.confirmDelete();

        if (tracked != null) {
            HudPin pin = pendingMatch ? pendingPin : ChatHudEditor.capturePin(tracked);
            float fromX = pendingMatch ? pendingOriginX : Float.NaN;
            float fromY = pendingMatch ? pendingOriginY : Float.NaN;
            finalizeDelete(tracked, fromX, fromY, pin);
            if (pendingMatch) clearPendingVisuals();
            return;
        }

        if (plainText != null && !plainText.isBlank()) {
            ChatHudRemover.removeBySenderAndPlain(sender, plainText);
        }
        ClientMessageIndex.tombstoneId(messageId);
        if (pendingMatch) clearPendingVisuals();
    }

    public static void onResult(boolean ok) {
        if (!ok) clearPendingVisuals();
    }

    public static void deleteFromEdit(long localId, long resolvedId) {
        ClientTrackedMessage tracked = resolvedId >= 0 ? ClientMessageIndex.get(resolvedId) : null;
        if (tracked == null) tracked = ClientMessageIndex.get(localId);
        if (tracked != null) requestDelete(tracked, Float.NaN, Float.NaN, ChatHudEditor.capturePin(tracked));
    }

    private static void requestDelete(ClientTrackedMessage tracked, float originX, float originY,
                                      HudPin forcedPin) {
        if (tracked == null || tracked.deleting || tracked.pendingEdit) return;
        tracked = resolveServer(tracked);
        if (tracked == null || tracked.deleting || tracked.pendingEdit) return;
        if (!ClientActionState.beginDelete(tracked)) return;

        pendingPin = forcedPin != null && forcedPin.isValid()
            ? forcedPin : ChatHudEditor.capturePin(tracked);
        pendingOriginX = originX;
        pendingOriginY = originY;
        pendingLocalId = tracked.id;

        String plain = tracked.plainText == null ? "" : tracked.plainText;
        try {
            Platform.get().sendDeleteRequestToServer(tracked.id, plain);
        } catch (Throwable failure) {
            ClientActionState.onResult(false);
            clearPendingVisuals();
        }
    }

    private static void finalizeDelete(ClientTrackedMessage tracked, float originX, float originY,
                                       HudPin pin) {
        if (tracked == null) return;
        tracked.deleting = true;
        long id = tracked.id;
        UnsendComposer.onMessageDeleted(id);

        String animText = tracked.displayContent != null
            ? tracked.displayContent.getString() : tracked.plainText;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float trashX = sw - 36f;
        float trashY = sh - 40f;
        float fromX = Float.isFinite(originX) ? originX : Math.min(sw * 0.35f, 120f);
        float fromY = Float.isFinite(originY) ? originY : (sh - 72f);

        ChatHudRemover.removeTracked(tracked, pin);
        ClientMessageIndex.tombstone(tracked);
        DeleteAnimation.start(animText, fromX, fromY, trashX, trashY, null);
    }

    private static ClientTrackedMessage resolveServer(ClientTrackedMessage tracked) {
        if (tracked == null || tracked.id >= 0) return tracked;
        ClientTrackedMessage bySig = null;
        ClientTrackedMessage byTick = null;
        for (ClientTrackedMessage candidate : ClientMessageIndex.all()) {
            if (candidate.id < 0 || candidate.deleting || candidate.pendingEdit
                || ClientMessageIndex.isTombstoned(candidate)) continue;
            if (!candidate.isOwnedBy(tracked.sender)) continue;
            if (tracked.signature != null && tracked.signature.equals(candidate.signature)) {
                bySig = candidate;
                break;
            }
            if (tracked.plainText != null && tracked.plainText.equals(candidate.plainText)
                && candidate.addedTime == tracked.addedTime) {
                byTick = candidate;
            }
        }
        if (bySig != null) return bySig;
        if (byTick != null) return byTick;
        return tracked;
    }

    private static void clearPendingVisuals() {
        pendingPin = null;
        pendingOriginX = Float.NaN;
        pendingOriginY = Float.NaN;
        pendingLocalId = Long.MIN_VALUE;
    }
}
