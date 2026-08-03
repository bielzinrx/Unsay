package com.bielzinrx.unsend.server;

import java.util.UUID;

public final class TrackedChatMessage {
    public final long id;
    public final UUID sender;
    public final String senderName;
    public String plainText;
    public final long createdAtMs;
    public final long replyToId;
    public boolean edited;

    public TrackedChatMessage(long id, UUID sender, String senderName, String plainText,
                              long createdAtMs, long replyToId) {
        this.id = id;
        this.sender = sender;
        this.senderName = senderName == null ? "" : senderName;
        this.plainText = plainText == null ? "" : plainText;
        this.createdAtMs = createdAtMs;
        this.replyToId = replyToId;
    }
}
