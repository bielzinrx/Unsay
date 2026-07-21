package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.platform.Platform;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Client-side unsend: wipe exactly one chat line, optionally notify server. */
public final class ClientDelete {
    private ClientDelete() {}

    public static void deleteById(long messageId) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        if (tracked == null) {
            ChatHudRemover.removeMessage(messageId);
            ClientMessageIndex.tombstoneId(messageId);
            return;
        }
        deleteTracked(tracked, true, Float.NaN, Float.NaN, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked) {
        deleteTracked(tracked, true, Float.NaN, Float.NaN, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked, float fromX, float fromY) {
        deleteTracked(tracked, true, fromX, fromY, null);
    }

    public static void deleteTracked(ClientTrackedMessage tracked, float fromX, float fromY,
                                     HudPin pin) {
        deleteTracked(tracked, true, fromX, fromY, pin);
    }

    public static void applyRemoteDelete(long messageId) {
        applyRemoteDelete(messageId, null, null);
    }

    /**
     * Another player (or the server) deleted a message. Always try to wipe the HUD —
     * even when this client never bound the server id (register race).
     */
    public static void applyRemoteDelete(long messageId, UUID sender, String plainText) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        if (tracked == null && plainText != null && !plainText.isBlank()) {
            tracked = ClientMessageIndex.findBestForRemote(sender, plainText);
        }
        if (tracked != null) {
            deleteTracked(tracked, false, Float.NaN, Float.NaN, ChatHudEditor.capturePin(tracked));
            return;
        }
        // No index row — still strip one matching HUD line so multiplayer unsend is visible
        if (plainText != null && !plainText.isBlank()) {
            ChatHudRemover.removeBySenderAndPlain(sender, plainText);
        }
        ClientMessageIndex.tombstoneId(messageId);
    }

    public static void deleteFromEdit(long localId, long resolvedId) {
        if (resolvedId != localId && ClientMessageIndex.get(resolvedId) != null) {
            deleteById(resolvedId);
            ClientTrackedMessage local = ClientMessageIndex.get(localId);
            if (local != null) {
                local.deleting = true;
                ClientMessageIndex.tombstone(local);
            }
            return;
        }
        if (ClientMessageIndex.get(localId) != null) {
            deleteById(localId);
            return;
        }
        ChatHudRemover.removeMessage(localId);
        ClientMessageIndex.tombstoneId(localId);
        if (resolvedId != localId) {
            ClientMessageIndex.tombstoneId(resolvedId);
            ClientMessageIndex.remove(resolvedId);
        }
    }

    private static void deleteTracked(ClientTrackedMessage tracked, boolean notifyServer,
                                      float originX, float originY, HudPin forcedPin) {
        if (tracked == null) return;
        tracked = resolveServer(tracked);

        HudPin pin = forcedPin != null && forcedPin.isValid()
            ? forcedPin
            : ChatHudEditor.capturePin(tracked);

        if (tracked.deleting || ClientMessageIndex.isTombstoned(tracked)) {
            ChatHudRemover.removeTracked(tracked, pin);
            ClientMessageIndex.tombstone(tracked);
            return;
        }

        tracked.deleting = true;
        long id = tracked.id;
        String plainSnap = tracked.plainText == null ? "" : tracked.plainText;
        UnsendComposer.onMessageDeleted(id);

        String animText = tracked.displayContent != null
            ? tracked.displayContent.getString()
            : tracked.plainText;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float trashX = sw - 36f;
        float trashY = sh - 40f;
        float fromX = Float.isFinite(originX) ? originX : Math.min(sw * 0.35f, 120f);
        float fromY = Float.isFinite(originY) ? originY : (sh - 72f);

        ClientTrackedMessage snap = tracked;

        ChatHudRemover.removeTracked(snap, pin);
        ClientMessageIndex.tombstone(snap);

        DeleteAnimation.start(animText, fromX, fromY, trashX, trashY, null);

        if (notifyServer) {
            long sendId = id;
            if (sendId < 0) {
                long late = resolveServer(snap).id;
                if (late >= 0) sendId = late;
            }
            // Always send plain fallback so server can resolve provisional / race cases
            Platform.get().sendDeleteRequestToServer(sendId, plainSnap);
        }
    }

    /** Provisional → server id for the SAME chat line only. */
    private static ClientTrackedMessage resolveServer(ClientTrackedMessage tracked) {
        if (tracked == null) return null;
        if (tracked.id >= 0) return tracked;
        ClientTrackedMessage bySig = null;
        ClientTrackedMessage byTick = null;
        for (ClientTrackedMessage m : ClientMessageIndex.all()) {
            if (m.id < 0 || m.deleting || ClientMessageIndex.isTombstoned(m)) continue;
            if (!m.isOwnedBy(tracked.sender)) continue;
            if (tracked.signature != null && tracked.signature.equals(m.signature)) {
                bySig = m;
                break;
            }
            if (tracked.plainText != null && tracked.plainText.equals(m.plainText)
                && m.addedTime == tracked.addedTime) {
                byTick = m;
            }
        }
        if (bySig != null) return bySig;
        if (byTick != null) return byTick;
        return tracked;
    }
}
