# Unsay 0.1.6b — Moderation & Filters Update

## Scope

- Selection filter defaults to **Mine** for every player, including operators.
- Operator filters: **Mine**, **Others**, **Player: <name>**, **All**.
- `Ctrl + Shift + A` selects only messages matching the active filter.
- `Ctrl + click` also respects the active filter.
- Changing filter revalidates the current selection so hidden/out-of-scope messages cannot remain selected.
- Selecting messages from other players requires an explicit second Delete confirmation.
- Player-specific filtering uses the tracked sender UUID; names are display-only.
- Server logs moderation deletes with moderator, message id, sender name and sender UUID.
- Remote deletion now rebinds duplicate rows by stable server-id order and reconciles orphaned HUD rows after authoritative delete broadcasts.
- Recent delete tombstones are included in join snapshots so a reconnecting client can
  reconcile lines deleted while it was offline.
- Player filters are paginated instead of silently hiding senders after the first eight.

## Multiplayer acceptance test

1. Start two clients connected to the same server.
2. Send repeated/identical messages from both clients.
3. On the operator client, verify the default filter is **Mine**.
4. `Ctrl + Shift + A` must not select the other client's messages while the filter is **Mine**.
5. Test **Others**, a specific player, and **All**.
6. Delete each selection and verify both clients remove the exact same rows.
7. Close/reopen chat, scroll history, and reconnect the remote client: no deleted row may reappear or remain unselectable.
