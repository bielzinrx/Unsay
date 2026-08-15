# Unsay 0.1.6b Beta — Moderation & Synchronization Update

Unsay 0.1.6b makes bulk deletion safer for operators and substantially improves
multiplayer synchronization. This release currently targets Minecraft 1.20.1 on
Fabric and Forge.

## What's new since 0.1.5b

- Added operator selection filters: **Mine**, **Others**, **Player** and **All**.
- Operators now start in **Mine**, preventing `Ctrl + Shift + A` from selecting other
  players' messages by default.
- Added a second, explicit Delete confirmation when a selection contains messages from
  other players.
- Player-specific filters now identify senders by UUID rather than display name.
- Changing filters now removes hidden or out-of-scope messages from the selection.
- Added pagination for servers with more than eight known message authors.
- Improved duplicate-message matching so identical messages from different players do
  not remove the wrong occurrence.
- Added recent deletion tombstones to reconnect snapshots, preventing deleted messages
  from surviving as remote ghost rows.
- Improved remote HUD reconciliation after single and bulk deletion.
- Fixed quick delete after scrolling: newest/oldest shortcuts now always target the
  correct message owned by the local player, independent of hover position.
- Improved moderation audit logs with moderator, sender, message ID and sender UUID.
- Preserved typed chat input when entering or canceling selection mode.
- Removed stale Mixin configuration warnings on Forge.
- Refreshed the mod icon and in-chat action textures.

## Short changelog

> Added safe operator filters and moderation confirmation, fixed remote ghost messages
> and duplicate-message targeting, improved reconnect synchronization, and corrected
> newest/oldest quick deletion after scrolling.

## Validation

- 14 automated tests passed.
- Clean Common, Fabric and Forge builds passed on Java 17.
- Tested with two real clients on Fabric and Forge dedicated servers.
- Verified single, 2-message, 20-message and 50-message deletion batches.
- Verified delete, edit, reply, scroll, duplicate messages, reconnects and remote HUD
  synchronization.

See the [complete QA summary](https://github.com/bielzinrx/Unsay/blob/0.1.6b/docs/QA_RESULTS_0.1.6B.md)
for the test results and residual coverage notes.

## Compatibility

- Minecraft 1.20.1
- Fabric Loader with Fabric API 0.92.7 or newer
- Forge 47.x
- Java 17
- Unsay must be installed on the server and every participating client

The 1.19.2 branch remains on its previous release for now. Do not mix Unsay versions or
loaders between the server and clients.

> This remains a beta release. Back up important server data and include both client and
> server logs when reporting synchronization issues.
