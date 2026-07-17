package com.bielzinrx.unsend.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public final class Packets {
    private Packets() {}

    public static void writeRegister(FriendlyByteBuf buf, long messageId, UUID sender, String senderName, String plainText) {
        buf.writeLong(messageId);
        buf.writeUUID(sender);
        buf.writeUtf(senderName == null ? "" : senderName, 64);
        buf.writeUtf(plainText == null ? "" : plainText, 256);
    }

    public static RegisterPayload readRegister(FriendlyByteBuf buf) {
        return new RegisterPayload(buf.readLong(), buf.readUUID(), buf.readUtf(64), buf.readUtf(256));
    }

    public static void writeDelete(FriendlyByteBuf buf, long messageId) {
        buf.writeLong(messageId);
    }

    public static long readDelete(FriendlyByteBuf buf) {
        return buf.readLong();
    }

    public static void writeEdit(FriendlyByteBuf buf, long messageId, String newText) {
        buf.writeLong(messageId);
        buf.writeUtf(newText == null ? "" : newText, 256);
    }

    public static EditPayload readEdit(FriendlyByteBuf buf) {
        return new EditPayload(buf.readLong(), buf.readUtf(256));
    }

    public static void writeReply(FriendlyByteBuf buf, long targetId, String text,
                                  String targetName, String targetPreview) {
        buf.writeLong(targetId);
        buf.writeUtf(text == null ? "" : text, 256);
        buf.writeUtf(targetName == null ? "" : targetName, 64);
        buf.writeUtf(targetPreview == null ? "" : targetPreview, 128);
    }

    public static ReplyPayload readReply(FriendlyByteBuf buf) {
        return new ReplyPayload(buf.readLong(), buf.readUtf(256), buf.readUtf(64), buf.readUtf(128));
    }

    public record RegisterPayload(long messageId, UUID sender, String senderName, String plainText) {}

    public record EditPayload(long messageId, String newText) {}

    public record ReplyPayload(long targetId, String text, String targetName, String targetPreview) {}
}
