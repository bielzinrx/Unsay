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

    /** C2S: only the server message id. */
    public static void writeDeleteC2S(FriendlyByteBuf buf, long messageId) {
        buf.writeLong(messageId);
    }

    public static long readDeleteC2S(FriendlyByteBuf buf) {
        return buf.readLong();
    }

    /**
     * S2C: id + identity hints so other clients can wipe the HUD even when they
     * never bound the server id (register race / late join).
     */
    public static void writeDeleteS2C(FriendlyByteBuf buf, long messageId, UUID sender, String plainText) {
        buf.writeLong(messageId);
        buf.writeBoolean(sender != null);
        if (sender != null) buf.writeUUID(sender);
        buf.writeUtf(plainText == null ? "" : plainText, 256);
    }

    public static DeleteS2CPayload readDeleteS2C(FriendlyByteBuf buf) {
        long id = buf.readLong();
        UUID sender = buf.readBoolean() ? buf.readUUID() : null;
        String plain = buf.readUtf(256);
        return new DeleteS2CPayload(id, sender, plain);
    }

    /** @deprecated use writeDeleteC2S / writeDeleteS2C */
    public static void writeDelete(FriendlyByteBuf buf, long messageId) {
        writeDeleteC2S(buf, messageId);
    }

    /** @deprecated use readDeleteC2S */
    public static long readDelete(FriendlyByteBuf buf) {
        return readDeleteC2S(buf);
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

    public record DeleteS2CPayload(long messageId, UUID sender, String plainText) {}

    public record EditPayload(long messageId, String newText) {}

    public record ReplyPayload(long targetId, String text, String targetName, String targetPreview) {}
}
