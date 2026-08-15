package com.bielzinrx.unsend.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageTrackerTest {
    private static final UUID REQUESTER =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER =
        UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void provisionalFallbackNeverCrossesAuthorBoundary() {
        assertTrue(ChatMessageTracker.isOwnFallbackCandidate(REQUESTER, REQUESTER));
        assertFalse(ChatMessageTracker.isOwnFallbackCandidate(REQUESTER, OTHER));
        assertFalse(ChatMessageTracker.isOwnFallbackCandidate(null, REQUESTER));
        assertFalse(ChatMessageTracker.isOwnFallbackCandidate(REQUESTER, null));
    }
}
