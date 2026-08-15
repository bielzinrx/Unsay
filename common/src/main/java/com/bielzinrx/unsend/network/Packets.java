package com.bielzinrx.unsend.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Packets {
    private static final String DELETE_FALLBACK_PREFIX = "\u001fU1:";

    private Packets() {}

    /** Compact metadata for provisional rows; stays well below FriendlyByteBuf's 256-char cap. */
    public static String encodeDeleteFallback(String plainText, int occurrence) {
        return DELETE_FALLBACK_PREFIX + Math.max(0, occurrence) + ":"
            + Long.toUnsignedString(fingerprintPlain(plainText), 16);
    }

    public static DeleteFallback decodeDeleteFallback(String raw) {
        String value = raw == null ? "" : raw;
        if (value.startsWith(DELETE_FALLBACK_PREFIX)) {
            int split = value.indexOf(':', DELETE_FALLBACK_PREFIX.length());
            if (split > DELETE_FALLBACK_PREFIX.length() && split + 1 < value.length()) {
                try {
                    int occurrence = Integer.parseInt(
                        value.substring(DELETE_FALLBACK_PREFIX.length(), split));
                    long fingerprint = Long.parseUnsignedLong(value.substring(split + 1), 16);
                    return new DeleteFallback("", fingerprint, Math.max(0, occurrence), true);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return new DeleteFallback(value, fingerprintPlain(value), 0, false);
    }

    /** Deterministic 64-bit FNV-1a over the normalized message body. */
    public static long fingerprintPlain(String plainText) {
        String value = plainText == null ? "" : plainText.strip();
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            hash ^= c & 0xff;
            hash *= 0x100000001b3L;
            hash ^= (c >>> 8) & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

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

    public static void writeBulkDeleteC2S(FriendlyByteBuf buf, long requestId, List<Long> messageIds) {
        buf.writeLong(requestId);
        int n = messageIds == null ? 0 : Math.min(50, messageIds.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Long id = messageIds.get(i);
            buf.writeLong(id == null ? -1L : id);
        }
    }

    public static BulkDeleteC2SPayload readBulkDeleteC2S(FriendlyByteBuf buf) {
        long requestId = buf.readLong();
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Invalid bulk-delete request ID: " + requestId);
        }
        int declared = buf.readVarInt();
        if (declared < 0 || declared > 50) {
            throw new IllegalArgumentException("Invalid bulk-delete size: " + declared);
        }
        List<Long> ids = new ArrayList<>(declared);
        for (int i = 0; i < declared; i++) ids.add(buf.readLong());
        return new BulkDeleteC2SPayload(requestId, ids);
    }

    public static void writeBulkResultS2C(FriendlyByteBuf buf, long requestId,
                                          int requested, int deleted, int skipped) {
        buf.writeLong(requestId);
        buf.writeVarInt(Math.max(0, requested));
        buf.writeVarInt(Math.max(0, deleted));
        buf.writeVarInt(Math.max(0, skipped));
    }

    public static BulkResultS2CPayload readBulkResultS2C(FriendlyByteBuf buf) {
        return new BulkResultS2CPayload(
            buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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

    public static void writeSnapshot(FriendlyByteBuf buf, List<SnapshotEntry> entries,
                                     List<DeletedSnapshotEntry> deletedEntries) {
        int n = entries == null ? 0 : Math.min(entries.size(), 200);
        buf.writeVarInt(n);
        if (entries != null) {
            for (int i = 0; i < n; i++) {
                SnapshotEntry e = entries.get(i);
                buf.writeLong(e.messageId());
                buf.writeUUID(e.sender());
                buf.writeUtf(e.senderName() == null ? "" : e.senderName(), 64);
                buf.writeUtf(e.plainText() == null ? "" : e.plainText(), 256);
                buf.writeBoolean(e.edited());
            }
        }

        int deletedCount = deletedEntries == null ? 0 : Math.min(deletedEntries.size(), 200);
        buf.writeVarInt(deletedCount);
        if (deletedEntries != null) {
            for (int i = 0; i < deletedCount; i++) {
                DeletedSnapshotEntry e = deletedEntries.get(i);
                buf.writeLong(e.messageId());
                buf.writeUUID(e.sender());
                buf.writeUtf(e.senderName() == null ? "" : e.senderName(), 64);
                buf.writeUtf(e.plainText() == null ? "" : e.plainText(), 256);
            }
        }
    }

    public static void writeSnapshot(FriendlyByteBuf buf, List<SnapshotEntry> entries) {
        writeSnapshot(buf, entries, List.of());
    }

    public static SnapshotPayload readSnapshot(FriendlyByteBuf buf) {
        int n = checkedSnapshotCount(buf.readVarInt(), "active");
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
        // Fabric has no channel-version handshake here. Accept the old snapshot shape so a
        // 0.1.6b client fails gracefully when it briefly meets a 0.1.5b server during rollout.
        if (!buf.isReadable()) return new SnapshotPayload(list, List.of());
        int deletedCount = checkedSnapshotCount(buf.readVarInt(), "deleted");
        List<DeletedSnapshotEntry> deleted = new ArrayList<>(deletedCount);
        for (int i = 0; i < deletedCount; i++) {
            deleted.add(new DeletedSnapshotEntry(
                buf.readLong(),
                buf.readUUID(),
                buf.readUtf(64),
                buf.readUtf(256)
            ));
        }
        return new SnapshotPayload(list, deleted);
    }

    private static int checkedSnapshotCount(int count, String group) {
        if (count < 0 || count > 200) {
            throw new IllegalArgumentException("Invalid " + group + " snapshot size: " + count);
        }
        return count;
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

    public record DeleteFallback(String plainText, long fingerprint,
                                 int occurrence, boolean encoded) {}

    public record DeleteC2SPayload(long messageId, String plainFallback) {}

    public record DeleteS2CPayload(long messageId, UUID sender, String plainText) {}

    public record BulkDeleteC2SPayload(long requestId, List<Long> messageIds) {}

    public record BulkResultS2CPayload(long requestId, int requested, int deleted, int skipped) {}

    public record EditC2SPayload(long messageId, String newText) {}

    public record EditS2CPayload(long messageId, String newText, String oldPlain, UUID sender) {}

    public record EditPayload(long messageId, String newText) {}

    public record ReplyPayload(long targetId, String text, String targetName, String targetPreview) {}

    public record SnapshotEntry(long messageId, UUID sender, String senderName, String plainText, boolean edited) {}

    public record DeletedSnapshotEntry(long messageId, UUID sender, String senderName,
                                       String plainText) {}

    public record SnapshotPayload(List<SnapshotEntry> entries,
                                  List<DeletedSnapshotEntry> deletedEntries) {}

    public record ResultPayload(boolean ok, String messageKey) {}
}
