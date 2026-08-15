# Unsay

### Take back what you said.

![Fabric](https://img.shields.io/badge/Fabric-Supported-dbd0b4?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-Supported-b07219?style=flat-square)
![Environment](https://img.shields.io/badge/Environment-Client%20%2B%20Server-1a6b8a?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-3c8527?style=flat-square)

**Unsay adds native-feeling controls for deleting, editing, replying to, and moderating
Minecraft chat messages.**

Message actions happen directly inside the existing chat screen. Changes are validated
by the server and synchronized with connected players, keeping conversations consistent
across clients.

![Synchronized message deletion](https://raw.githubusercontent.com/bielzinrx/Unsay/1.20.1/docs/media/unsay-0.1.6b-synchronized-delete.gif)

---

## Message Controls

Open the chat and hold **Shift** over a tracked message to reveal its available actions.

- **Delete:** remove one of your messages for everyone.
- **Edit:** correct a sent message without posting a replacement.
- **Reply:** quote the sender and a short preview before writing your response.

The controls are integrated directly into Minecraft chat. No separate menu or command
is required for ordinary message actions.

![Replying to a message](https://raw.githubusercontent.com/bielzinrx/Unsay/1.20.1/docs/media/unsay-0.1.6b-reply.gif)

---

## Bulk Selection

Unsay includes a lightweight selection mode for managing multiple messages.

- Select or deselect individual messages.
- Select messages from the loaded chat history.
- Delete the current selection together.
- Cancel without losing text already entered in the chat input.
- Continue receiving new messages without shifting existing deletion targets.

Selection is limited by server permissions. Ordinary players can only manage their own
messages.

---

## Moderation

Server operators receive additional controls for moderating chat safely.

Available filters include:

- **Mine:** messages sent by the operator.
- **Others:** messages sent by other players.
- **Player:** messages from one specific player.
- **All:** every message the operator is allowed to moderate.

Operators begin with **Mine** selected. Deleting messages from other players requires
an additional confirmation, helping prevent accidental moderation.

Player-specific filters use UUIDs instead of display names.

![Operator filters and moderation confirmation](https://raw.githubusercontent.com/bielzinrx/Unsay/1.20.1/docs/media/unsay-0.1.6b-moderation.gif)

---

## Multiplayer Synchronization

Every edit and deletion is validated by the server before being applied.

Unsay is designed to handle:

- single and bulk deletion;
- message editing;
- contextual replies;
- identical messages from different players;
- selection after scrolling;
- new messages arriving during selection;
- reopening the chat;
- reconnecting to the server;
- synchronization between multiple clients.

This prevents the clients from ending with different chat histories after the same
action.

---

## Controls

| Action | Default control |
|---|---|
| Show message controls | Hold `Shift` |
| Select or deselect a message | `Ctrl + Click` |
| Select loaded messages | `Ctrl + Shift + A` |
| Delete selected messages | `Delete` |
| Cancel selection | `Esc` |
| Delete your newest message | `Shift + Delete` or `Shift + Backspace` |
| Delete your oldest message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Browse recent messages for editing | `Shift + Up Arrow` |

Shortcuts, confirmation preferences, selection limits, and deletion animations can be
customized through the client configuration file.

---

## Requirements

Unsay must be installed on the **server and every participating client**.

### Fabric

- Install Fabric Loader.
- Install Fabric API.
- Download the Unsay Fabric file matching your Minecraft version.

### Forge

- Install Forge.
- Download the Unsay Forge file matching your Minecraft version.

The server and clients must use compatible Minecraft versions, Unsay releases, and mod
loaders. Do not mix Fabric and Forge files.

Check the **Files** section for all currently supported versions.

---

## Installation

1. Download the file matching your Minecraft version and loader.
2. Install Fabric API when using Fabric.
3. Place the required JARs inside the `mods` folder.
4. Install Unsay on the server and participating clients.
5. Restart the game and server.

Unsay also works in singleplayer through Minecraft's integrated server.

---

## Privacy

Unsay does not include telemetry, advertisements, paid features, or connections to
external services.

Client preferences remain inside the Minecraft configuration directory. Message
authority and moderation permissions remain controlled by the server.

---

## Support and Links

- [Source Code](https://github.com/bielzinrx/Unsay)
- [Downloads and Releases](https://github.com/bielzinrx/Unsay/releases)
- [Complete Controls Guide](https://github.com/bielzinrx/Unsay/blob/1.20.1/docs/CONTROLS.md)
- [Report a Bug](https://github.com/bielzinrx/Unsay/issues)
- [Modrinth Page](https://modrinth.com/mod/unsay)

When reporting a problem, include the Unsay version, Minecraft version, loader,
reproduction steps, and client/server logs when available.

---

*Unsay — take back what you said.*
