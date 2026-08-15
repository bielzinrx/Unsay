package com.bielzinrx.unsend.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSelectionFilterTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void ordinaryPlayersRemainRestrictedToOwnMessagesInEveryMode() {
        for (ClientSelectionFilter.Mode mode : ClientSelectionFilter.Mode.values()) {
            assertTrue(ClientSelectionFilter.matchesScope(mode, OTHER, SELF, false, SELF));
            assertFalse(ClientSelectionFilter.matchesScope(mode, OTHER, SELF, false, OTHER));
        }
    }

    @Test
    void operatorMineAndOthersAreMutuallyExclusive() {
        assertTrue(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.MINE, null, SELF, true, SELF));
        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.MINE, null, SELF, true, OTHER));

        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.OTHERS, null, SELF, true, SELF));
        assertTrue(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.OTHERS, null, SELF, true, OTHER));
    }

    @Test
    void playerFilterUsesTheSelectedUuidOnly() {
        assertTrue(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.PLAYER, OTHER, SELF, true, OTHER));
        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.PLAYER, OTHER, SELF, true, THIRD));
        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.PLAYER, null, SELF, true, OTHER));
    }

    @Test
    void allStillRejectsUnknownSendersAndNullContext() {
        assertTrue(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.ALL, null, SELF, true, OTHER));
        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.ALL, null, SELF, true, null));
        assertFalse(ClientSelectionFilter.matchesScope(
            ClientSelectionFilter.Mode.ALL, null, null, true, OTHER));
    }

    @Test
    void missingModeFallsBackToMine() {
        assertTrue(ClientSelectionFilter.matchesScope(null, null, SELF, true, SELF));
        assertFalse(ClientSelectionFilter.matchesScope(null, null, SELF, true, OTHER));
    }
}
