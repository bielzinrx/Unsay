# Unsay

### *Take back what you said.*

[![Fabric](https://img.shields.io/badge/Fabric-1.19.2%20%7C%201.20.1-dbd0b4?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Forge](https://img.shields.io/badge/Forge-1.19.2%20%7C%201.20.1-b07219?style=flat-square&logo=curseforge&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Environment](https://img.shields.io/badge/Environment-Client%20%2B%20Server-1a6b8a?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Status](https://img.shields.io/badge/Status-Beta-f2a900?style=flat-square)](https://github.com/bielzinrx/Unsay/releases)
[![License](https://img.shields.io/badge/License-MIT-3c8527?style=flat-square)](LICENSE)

<img width="1536" height="1024" alt="Unsay banner" src="https://github.com/user-attachments/assets/010edfec-d69a-4298-8b2e-8e7c44d3d50a">

**Unsay is a Minecraft mod that lets you delete, edit, reply to, and moderate chat messages directly from the existing chat screen.**

There are no commands or separate interfaces required for ordinary message actions. Changes are validated by the server and synchronized with every connected player using Unsay.

> Unsay is currently in beta and must be installed on both the server and every participating client.

![Synchronized message deletion](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978102/unsay-0.1.6b-synchronized-delete_epdvzr.gif)

---

## Message controls

Open chat and hold **Shift** over a tracked message to reveal its controls:

- **Trash:** delete your message for everyone.
- **Pencil:** edit your message without sending a replacement.
- **Reply arrow:** reply with the sender and a short message preview.

![Replying to a message](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978110/unsay-0.1.6b-reply_hshzfv.gif)

---

## Bulk selection

Unsay includes a selection mode integrated directly into Minecraft chat.

| Action | Default control |
|---|---|
| Select or deselect a message | `Ctrl + Click` |
| Select loaded messages | `Ctrl + Shift + A` |
| Delete the current selection | `Delete` |
| Cancel selection | `Esc` |
| Delete your newest message | `Shift + Delete` or `Shift + Backspace` |
| Delete your oldest message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Browse recent messages for editing | `Shift + Up Arrow` |

Text entered before opening selection mode is preserved and restored after deleting or canceling.

For complete behavior and configuration details, see the [controls guide](docs/CONTROLS.md).

---

## Moderation controls

Server operators can moderate messages without receiving unrestricted selection by default. The available filters are:

- **Mine:** only the operator's messages.
- **Others:** messages sent by other players.
- **Player:** messages from one specific player UUID.
- **All:** every message the operator is allowed to moderate.

Operators always begin with **Mine** selected. Deleting another player's message requires an additional confirmation, helping prevent accidental moderation.

![Operator filters and moderation confirmation](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978115/unsay-0.1.6b-moderation_emfzze.gif)

---

## Multiplayer synchronization

Message actions are validated by the server before being applied. Unsay handles:

- single and bulk deletion;
- message editing;
- replies with tracked context;
- identical messages from different players;
- new messages arriving during a selection;
- deletion after scrolling;
- reopening chat and reconnecting;
- synchronization between connected players.

This prevents clients from ending with different chat histories after the same action.

---

## Requirements

Unsay must be installed on the **server and every participating client**.

### Fabric

- Fabric Loader
- Fabric API
- Unsay Fabric build matching the Minecraft version

### Forge

- Forge
- Unsay Forge build matching the Minecraft version

### General

- Java 17 or newer
- The same Unsay release and loader on the server and clients

Do not mix Fabric and Forge installations.

---

## Supported versions

| Minecraft | Loader | Unsay release |
|---|---|---|
| 1.20.1 | Fabric and Forge | 0.1.6b |
| 1.19.2 | Fabric and Forge | Previous beta release |

The moderation and synchronization improvements in 0.1.6b currently apply to Minecraft 1.20.1.

---

## Installation

1. Install Fabric or Forge for your Minecraft version.
2. Install Fabric API when using the Fabric build.
3. Download the matching Unsay JAR.
4. Place the required files in the `mods` folder on the server and clients.
5. Restart the game and server.

Unsay also works in singleplayer through Minecraft's integrated server.

---

## Configuration

Client preferences are stored in:

```text
config/unsay-client.json
```

The file controls shortcuts, confirmations, selection limits, and deletion animations. Permissions and message authority always remain on the server.

---

## Privacy

Unsay does not include telemetry, advertisements, paid features, or connections to external services. Client preferences remain inside the Minecraft configuration directory.

---

## Support and links

- [GitHub releases](https://github.com/bielzinrx/Unsay/releases)
- [Complete controls guide](docs/CONTROLS.md)
- [Report a bug](https://github.com/bielzinrx/Unsay/issues)
- [Modrinth page](https://modrinth.com/mod/unsay)

When reporting a problem, include the Unsay version, Minecraft version, loader, reproduction steps, and both client and server logs when available.

<details>
<summary><strong>Building from source</strong></summary>

JDK 17 or newer is required.

```bash
./gradlew clean build
```

Builds are written to `fabric/build/libs/` and `forge/build/libs/`.

</details>

---

Unsay is licensed under the [MIT License](LICENSE). The internal mod ID is `unsend` and should not be changed without a migration plan.

*Unsay — take back what you said.*
