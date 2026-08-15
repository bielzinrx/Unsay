package com.bielzinrx.unsend.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketsTest {
    @Test
    void fingerprintHasAStableEmptyValueAndNormalizesOuterWhitespace() {
        assertEquals(0xcbf29ce484222325L, Packets.fingerprintPlain(""));
        assertEquals(Packets.fingerprintPlain(null), Packets.fingerprintPlain("   "));
        assertEquals(Packets.fingerprintPlain("message"), Packets.fingerprintPlain("  message  "));
    }

    @Test
    void fingerprintDistinguishesContentAndUnicode() {
        assertNotEquals(Packets.fingerprintPlain("message"), Packets.fingerprintPlain("Message"));
        assertNotEquals(Packets.fingerprintPlain("acao"), Packets.fingerprintPlain("ação"));
    }

    @Test
    void encodedDeleteFallbackRoundTripsFingerprintAndOccurrence() {
        String encoded = Packets.encodeDeleteFallback(" repeated message ", 7);
        Packets.DeleteFallback decoded = Packets.decodeDeleteFallback(encoded);

        assertTrue(decoded.encoded());
        assertEquals("", decoded.plainText());
        assertEquals(7, decoded.occurrence());
        assertEquals(Packets.fingerprintPlain("repeated message"), decoded.fingerprint());
    }

    @Test
    void negativeOccurrenceIsClampedToZero() {
        Packets.DeleteFallback decoded = Packets.decodeDeleteFallback(
            Packets.encodeDeleteFallback("message", -4));

        assertTrue(decoded.encoded());
        assertEquals(0, decoded.occurrence());
    }

    @Test
    void malformedMetadataRemainsAPlainLegacyFallback() {
        String malformed = "\u001fU1:not-a-number:not-a-hash";
        Packets.DeleteFallback decoded = Packets.decodeDeleteFallback(malformed);

        assertFalse(decoded.encoded());
        assertEquals(malformed, decoded.plainText());
        assertEquals(Packets.fingerprintPlain(malformed), decoded.fingerprint());
    }

    @Test
    void snapshotRoundTripsActiveMessagesAndRecentTombstones() {
        UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<Packets.SnapshotEntry> active = List.of(
            new Packets.SnapshotEntry(12L, sender, "TestPlayer", "still here", true));
        List<Packets.DeletedSnapshotEntry> deleted = List.of(
            new Packets.DeletedSnapshotEntry(11L, sender, "TestPlayer", "already gone"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        Packets.writeSnapshot(buffer, active, deleted);
        Packets.SnapshotPayload decoded = Packets.readSnapshot(buffer);

        assertEquals(active, decoded.entries());
        assertEquals(deleted, decoded.deletedEntries());
    }

    @Test
    void snapshotRejectsCountsAboveTheProtocolLimit() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(201);

        assertThrows(IllegalArgumentException.class, () -> Packets.readSnapshot(buffer));
    }

    @Test
    void snapshotReaderAcceptsTheLegacyActiveOnlyShape() {
        UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000001");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeLong(9L);
        buffer.writeUUID(sender);
        buffer.writeUtf("TestPlayer", 64);
        buffer.writeUtf("legacy", 256);
        buffer.writeBoolean(false);

        Packets.SnapshotPayload decoded = Packets.readSnapshot(buffer);

        assertEquals(1, decoded.entries().size());
        assertEquals("legacy", decoded.entries().get(0).plainText());
        assertTrue(decoded.deletedEntries().isEmpty());
    }
}
