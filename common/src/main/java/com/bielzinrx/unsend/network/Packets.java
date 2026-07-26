package com.bielzinrx.unsend.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
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

    public static void writeDeleteC2S(FriendlyByteBuf buf, long messageId, String plainFallback) {
        buf.writeLong(messageId);
        String plain = plainFallback == null ? "" : plainFallback;
        buf.writeBoolean(!plain.isEmpty());
        if (!plain.isEmpty()) buf.writeUtf(plain, 256);
    }

    public static DeleteC2SPayload readDeleteC2S(FriendlyByteBuf buf) {
        long id = buf.readLong();
        String plain = buf.readBoolean() ? buf.readUtf(256) : "";
        return new DeleteC2SPayload(id, plain);
    }

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

    public static void writeEditC2S(FriendlyByteBuf buf, long messageId, String newText) {
        buf.writeLong(messageId);
        buf.writeUtf(newText == null ? "" : newText, 256);
    }

    public static EditC2SPayload readEditC2S(FriendlyByteBuf buf) {
        return new EditC2SPayload(buf.readLong(), buf.readUtf(256));
    }

    public static void writeEditS2C(FriendlyByteBuf buf, long messageId, String newText,
                                    String oldPlain, UUID sender) {
        buf.writeLong(messageId);
        buf.writeUtf(newText == null ? "" : newText, 256);
        buf.writeUtf(oldPlain == null ? "" : oldPlain, 256);
        buf.writeBoolean(sender != null);
        if (sender != null) buf.writeUUID(sender);
    }

    public static EditS2CPayload readEditS2C(FriendlyByteBuf buf) {
        long id = buf.readLong();
        String neu = buf.readUtf(256);
        String old = buf.readUtf(256);
        UUID sender = buf.readBoolean() ? buf.readUUID() : null;
        return new EditS2CPayload(id, neu, old, sender);
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

    public static void writeSnapshot(FriendlyByteBuf buf, List<SnapshotEntry> entries) {
        int n = entries == null ? 0 : Math.min(entries.size(), 200);
        buf.writeVarInt(n);
        if (entries == null) return;
        for (int i = 0; i < n; i++) {
            SnapshotEntry e = entries.get(i);
            buf.writeLong(e.messageId());
            buf.writeUUID(e.sender());
            buf.writeUtf(e.senderName() == null ? "" : e.senderName(), 64);
            buf.writeUtf(e.plainText() == null ? "" : e.plainText(), 256);
            buf.writeBoolean(e.edited());
        }
    }

    public static SnapshotPayload readSnapshot(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<SnapshotEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new SnapshotEntry(
                buf.readLong(),
                buf.readUUID(),
                buf.readUtf(64),
                buf.readUtf(256),
                buf.readBoolean()
            ));
        }
        return new SnapshotPayload(list);
    }

    public static void writeResult(FriendlyByteBuf buf, boolean ok, String messageKey) {
        buf.writeBoolean(ok);
        buf.writeUtf(messageKey == null ? "" : messageKey, 128);
    }

    public static ResultPayload readResult(FriendlyByteBuf buf) {
        return new ResultPayload(buf.readBoolean(), buf.readUtf(128));
    }

    public static void writeDeleteC2S(FriendlyByteBuf buf, long messageId) {
        writeDeleteC2S(buf, messageId, "");
    }

    public static long readDeleteC2SIdOnly(FriendlyByteBuf buf) {
        return readDeleteC2S(buf).messageId();
    }

    public static void writeEdit(FriendlyByteBuf buf, long messageId, String newText) {
        writeEditC2S(buf, messageId, newText);
    }

    public static EditPayload readEdit(FriendlyByteBuf buf) {
        EditC2SPayload p = readEditC2S(buf);
        return new EditPayload(p.messageId(), p.newText());
    }

    public record RegisterPayload(long messageId, UUID sender, String senderName, String plainText) {}

    public record DeleteC2SPayload(long messageId, String plainFallback) {}

    public record DeleteS2CPayload(long messageId, UUID sender, String plainText) {}

    public record EditC2SPayload(long messageId, String newText) {}

    public record EditS2CPayload(long messageId, String newText, String oldPlain, UUID sender) {}

    public record EditPayload(long messageId, String newText) {}

    public record ReplyPayload(long targetId, String text, String targetName, String targetPreview) {}

    public record SnapshotEntry(long messageId, UUID sender, String senderName, String plainText, boolean edited) {}

    public record SnapshotPayload(List<SnapshotEntry> entries) {}

    public record ResultPayload(boolean ok, String messageKey) {}
}
