# Unsay 0.1.6b — two-instance test matrix

Use two clean instances connected to the same server. Test Fabric 1.20.1 first before porting the update to the 1.19.2 branch.

## Roles

- Instance A: operator
- Instance B: normal player

## Filter safety

1. Both players send at least 10 messages, including repeated identical messages.
2. Open A's chat and hold Ctrl. Filter must initially show **Mine**.
3. Press Ctrl+Shift+A on A. Only A's messages may be selected.
4. Switch to **Others**. Existing selection must be revalidated; A's messages must leave the selection.
5. Ctrl+Shift+A. Only B's messages may be selected.
6. Switch to **Player: <B>**. Only B's UUID may match.
7. Switch to **All**. Both players' messages may be selected.
8. Close and reopen chat. Operator must return to **Mine**.

## Moderation confirmation

1. Under **Mine**, select A's own messages and press Delete: deletion starts immediately.
2. Under **Others**, select B's messages and press Delete: a human-readable confirmation must appear.
3. Release Delete, press it again: deletion starts.
4. Esc at the confirmation stage must cancel confirmation without deleting the selection.

## Remote ghost regression

1. A and B both send identical messages such as `a` several times.
2. A deletes only A's messages using **Mine**.
3. Verify every removed row disappears on A and B.
4. On B, close/reopen chat and scroll through loaded history.
5. No deleted line may remain visible but unselectable/uninteractive.
6. Repeat with B's messages deleted by A under **Others**.
7. Disconnect/reconnect B. Deleted rows must remain absent.
8. Disconnect B, delete a recent message while B is offline, reconnect B, and verify
   the tombstone snapshot removes any retained row without touching active duplicates.

## Edge cases

- New message arrives while the filter menu is open.
- New message arrives while a selection exists.
- Identical messages sent in the same second/tick window.
- Scroll before Ctrl+Shift+A.
- Switch filters after scrolling.
- Delete 1, 2, 20 and maximum configured messages.
- Try filter controls as a non-operator: only own messages remain selectable.
- Navigate the player-filter pages with more than eight known senders.
