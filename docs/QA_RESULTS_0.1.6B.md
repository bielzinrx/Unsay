# Unsay 0.1.6b QA results

Validation date: 2026-08-15

## Environments

- Minecraft 1.20.1 and Java 17
- Fabric API 0.92.7, with two clients in LAN and dedicated-server sessions
- Forge 47.4.0, with two clients on a dedicated server
- One operator and one ordinary player for permission-boundary tests

## Results

- 14 of 14 automated tests passed.
- Clean Common, Fabric and Forge builds passed.
- **Mine**, **Others**, **Player** and **All** selected the expected UUID scopes.
- Ordinary players could not open or use moderation filters.
- Foreign-message selections required explicit confirmation; own-only selections did
  not.
- Identical messages from different authors retained the correct occurrence.
- New messages arriving with a filter menu or selection open did not shift deletion
  targets.
- Selection after scrolling included the complete loaded set, including boundary rows.
- Batches of 1, 2, 20 and the configured maximum of 50 were confirmed completely by
  the server and removed on both clients.
- Typed input survived selection cancellation.
- Reply and edit targeted the expected rows and propagated to both clients.
- Deleted rows did not return after reopening chat or reconnecting.
- Fabric and Forge both propagated delete and edit actions without remote ghost rows.

## Regression found and fixed during QA

Quick deletion previously preferred the hovered row before the documented newest or
oldest own message. After scrolling, an operator could therefore delete an unrelated
foreign row. Quick deletion is now independent of the cursor and viewport:

- `Shift + Delete` / `Shift + Backspace`: newest own message
- `Ctrl + Shift + Delete` / `Ctrl + Shift + Backspace`: oldest own message

The corrected build was restarted without hot swap and passed again while the operator
kept the cursor over another player's message.

## Residual coverage

- Pagination with more than eight authors was not exercised with nine real accounts;
  the code path remains covered by implementation review.
- A real username change was not performed; player-specific filtering is UUID-based and
  covered by automated tests.
- Minecraft 1.19.2 was not changed or tested in this release cycle.
