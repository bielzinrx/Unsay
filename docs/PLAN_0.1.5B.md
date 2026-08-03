# Unsay 0.1.5b — Bulk & Controls Update

## Release objective

Turn deletion into a controlled workflow that supports fast sequential cleanup and multi-message moderation without weakening the server-authoritative safety introduced in 0.1.4b.

## Scope

### 1. Directional sequential deletion

- Preserve `Shift+Delete` and `Shift+Backspace` as newest-to-oldest deletion, matching the visual bottom-to-top direction of Minecraft chat.
- Add `Ctrl+Shift+Delete` and `Ctrl+Shift+Backspace` for oldest-to-newest deletion.
- Prefer the currently hovered message when a shortcut is pressed.
- Require an empty chat input when no message is hovered, preventing accidental deletion while typing.
- Keep keyboard auto-repeat blocked until the physical key is released.

### 2. Multi-selection UX

- `Ctrl+click` toggles a deletable message.
- `Ctrl+Shift+A` selects all currently visible deletable messages.
- Selected rows receive a clear highlight and selected-count status.
- `Esc` first cancels the selection instead of closing the chat.
- Selection is cleared when the chat closes, but an already submitted batch continues safely.

### 3. Confirmation and progress

- Batches larger than one message require confirmation by default.
- `Enter` or the configured delete-selection shortcut confirms; `Esc` cancels.
- Display requested/deleted/skipped totals.
- Display a progress bar while delete broadcasts are applied.
- Stagger deletion animations according to selection order.
- Use a three-second visual fallback so a missing HUD match cannot leave the client permanently locked.
- Use an eight-second request timeout while keeping the selection available for retry.
- Prevent keyboard auto-repeat from using the same `Delete` press to both open and accept confirmation.

### 4. Configurable controls

Create `config/unsay-client.json` with:

- newest-first shortcut alternatives;
- oldest-first shortcut alternatives;
- selection deletion shortcut;
- select-visible shortcut;
- mouse selection modifier;
- confirmation toggle;
- animation toggle;
- maximum batch size from 2 to 50.

The client checks the file modification time and reloads changes without restarting Minecraft.

### 5. Authoritative network protocol

Add two packets:

- `bulk_delete_c2s`: positive request ID plus an ordered list of tracked server message IDs;
- `bulk_result_s2c`: matching request ID plus requested, deleted, and skipped totals.

Security rules:

- reject invalid request IDs and malformed packet sizes above 50;
- deduplicate IDs while preserving order;
- ignore non-positive IDs;
- independently verify that each message still exists and is deletable by the requester;
- allow owners, plus operators for moderation;
- never accept client text as the deletion authority;
- rate-limit bulk requests separately to three requests per ten seconds;
- ignore late results whose request ID no longer matches the active client batch.

Forge network protocol changes from 5 to 6. The server and clients must update together.

## Files and architecture

- `UnsayClientConfig`: JSON config, hot reload, shortcut parser.
- `ClientBulkDelete`: selection, confirmation, request, result, progress, and timeout state.
- `UnsendHud`: controls, row highlight, status, confirmation, progress bar.
- `ClientDelete` / `DeleteAnimation`: ordered staggered visuals after authoritative broadcasts.
- `Packets` / loader network classes: bulk C2S and result S2C transport.
- `ChatMessageTracker`: validation, rate limiting, independent deletions, summary result.

## Out of scope for 0.1.5b

- Persistent or global pinned messages; reserved for 0.2.0b.
- A full in-game keybind configuration screen. 0.1.5b uses a hot-reloaded JSON file.
- Offline deletion of messages no longer present in the server tracker.
- Cross-server storage of chat history.

## Acceptance criteria

1. The four Fabric/Forge and 1.19.2/1.20.1 targets compile with JDK 17.
2. A normal player can batch-delete only their own tracked messages.
3. An operator can include other players' messages in a batch.
4. Unauthorized, stale, duplicate, and already deleted IDs are skipped without cancelling valid entries.
5. Holding a shortcut deletes only one message per physical key press.
6. The newest-first and oldest-first shortcuts choose opposite ends of owned history.
7. Typing text prevents non-hovered quick deletion and selection deletion.
8. Confirmation can be accepted or cancelled without closing chat.
9. Closing chat clears pending selection but does not corrupt a submitted batch.
10. Disconnect clears all client batch state.
11. English, Portuguese, and Spanish language JSON files contain all new keys.
12. Forge rejects 0.1.4b peers because the packet protocol changed.

## Manual test matrix

Run every core scenario on:

- Minecraft 1.19.2 Fabric;
- Minecraft 1.19.2 Forge;
- Minecraft 1.20.1 Fabric;
- Minecraft 1.20.1 Forge.

Test with two normal players and one operator:

- single newest-first delete;
- repeated key press/release sequence;
- oldest-first delete;
- hovered shortcut deletion;
- 2, 10, and 50-message batches;
- cancel and confirm paths;
- mixed owned/foreign/deleted/stale IDs;
- rate-limit rejection;
- player disconnect during a batch;
- chat closed immediately after submission;
- animations disabled;
- confirmation disabled;
- custom shortcut reload while the game is open;
- duplicate messages with identical text;
- edit/reply attempted while a batch is active.

## Release procedure

1. Run `./gradlew clean build` in both source trees.
2. Test all four generated JARs on a dedicated server and at least two clients.
3. Verify JAR metadata says 0.1.5b.
4. Generate SHA-256 checksums.
5. Publish the same four artifacts to GitHub, Modrinth, and CurseForge.
6. State clearly that client and server must update together.
7. Tag the 1.20.1 branch with `0.1.5b` after the final artifacts pass testing.
