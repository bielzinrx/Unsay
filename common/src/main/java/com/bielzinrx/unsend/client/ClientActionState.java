package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;

public final class ClientActionState {
    public enum Type { DELETE, EDIT }

    private static Type pendingType;
    private static long pendingMessageId = Long.MIN_VALUE;

    private ClientActionState() {}

    public static synchronized boolean beginDelete(ClientTrackedMessage tracked) {
        if (tracked == null || pendingType != null) return false;
        pendingType = Type.DELETE;
        pendingMessageId = tracked.id;
        tracked.deleting = true;
        return true;
    }

    public static synchronized boolean beginEdit(ClientTrackedMessage tracked) {
        if (tracked == null || pendingType != null) return false;
        pendingType = Type.EDIT;
        pendingMessageId = tracked.id;
        tracked.pendingEdit = true;
        return true;
    }

    public static synchronized boolean isBusy() {
        return pendingType != null;
    }

    public static synchronized Type pendingType() {
        return pendingType;
    }

    public static synchronized long pendingMessageId() {
        return pendingMessageId;
    }

    public static synchronized boolean matches(Type type, long messageId) {
        return pendingType == type && pendingMessageId == messageId;
    }

    public static synchronized void confirmDelete() {
        if (pendingType == Type.DELETE) {
            clearFlags();
            clearState();
        }
    }

    public static synchronized void confirmEdit() {
        if (pendingType == Type.EDIT) {
            clearFlags();
            clearState();
        }
    }

    public static synchronized void onResult(boolean ok) {
        if (!ok) {
            clearFlags();
            clearState();
        }
    }

    public static synchronized void clear() {
        clearFlags();
        clearState();
    }

    private static void clearFlags() {
        for (ClientTrackedMessage tracked : ClientMessageIndex.all()) {
            if (tracked == null) continue;
            tracked.deleting = false;
            tracked.pendingEdit = false;
        }
    }

    private static void clearState() {
        pendingType = null;
        pendingMessageId = Long.MIN_VALUE;
    }
}
