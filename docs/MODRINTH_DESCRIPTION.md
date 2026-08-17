# Unsay

### *Take back what you said.*

[![Fabric](https://img.shields.io/badge/Fabric-Supported-dbd0b4?style=flat-square)](https://modrinth.com/mod/unsay/versions)
[![Forge](https://img.shields.io/badge/Forge-Supported-b07219?style=flat-square&logo=curseforge&logoColor=white)](https://modrinth.com/mod/unsay/versions)
![Environment](https://img.shields.io/badge/Environment-Client%20%2B%20Server-1a6b8a?style=flat-square)
![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk&logoColor=white)

![Unsay banner](https://cdn.modrinth.com/data/cached_images/a63d73bda86538c85b1478d1add35ef30fe372ae.png)

**Unsay adds native-feeling controls for deleting, editing, replying to, and moderating Minecraft chat messages.**

Message actions happen directly inside the existing chat screen. There are no commands or separate menus required for ordinary use. Every change is validated by the server and synchronized with connected players, keeping conversations consistent across clients.

> Unsay must be installed on the server and every participating client.

![Synchronized message deletion](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978102/unsay-0.1.6b-synchronized-delete_epdvzr.gif)

---

## Message controls

Open chat and hold **Shift** over a tracked message to reveal its available actions:

- **Delete:** remove your message for everyone.
- **Edit:** correct a sent message without posting a replacement.
- **Reply:** include the sender and a short preview in your response.

![Replying to a message](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978110/unsay-0.1.6b-reply_hshzfv.gif)

---

## Bulk selection

Unsay includes a lightweight selection mode integrated directly into Minecraft chat.

| Action | Default control |
|---|---|
| Show message controls | `Shift` |
| Select or deselect a message | `Ctrl + Click` |
| Select loaded messages | `Ctrl + Shift + A` |
| Delete selected messages | `Delete` |
| Cancel selection | `Esc` |
| Delete your newest message | `Shift + Delete` or `Shift + Backspace` |
| Delete your oldest message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Browse recent messages for editing | `Shift + Up Arrow` |

Text entered before selection is preserved and restored after deleting or canceling. Shortcuts, confirmations, selection limits, and deletion animations can be customized through the client configuration.

---

## Moderation

Server operators receive additional controls without starting with unrestricted access. Available filters include:

- **Mine:** only the operator's messages.
- **Others:** messages sent by other players.
- **Player:** messages from one specific player UUID.
- **All:** every message the operator is permitted to moderate.

Operators begin with **Mine** selected. Deleting another player's message requires an additional confirmation, helping prevent accidental moderation.

![Operator filters and moderation confirmation](https://res.cloudinary.com/diexbbgwe/image/upload/v1786978115/unsay-0.1.6b-moderation_emfzze.gif)

---

## Multiplayer synchronization

The server validates every edit and deletion before applying it. Unsay keeps message identity stable while handling:

- single and bulk deletion;
- message editing and contextual replies;
- identical messages from different players;
- scrolling and loaded chat history;
- new messages arriving during selection;
- reopening chat and reconnecting;
- synchronization between multiple clients.

---

## Installation

### Fabric

1. Install Fabric Loader for your Minecraft version.
2. Install the matching Fabric API.
3. Place the Unsay and Fabric API JARs in the `mods` folder on the server and clients.

### Forge

1. Install Forge for your Minecraft version.
2. Place the Unsay JAR in the `mods` folder on the server and clients.

Use the same Minecraft version, Unsay release, and loader across the server and participating clients. Do not mix Fabric and Forge files. Java 17 or newer is required.

Choose a compatible build from the [Versions page](https://modrinth.com/mod/unsay/versions).

---

## Privacy

Unsay does not include telemetry, advertisements, paid features, or external-service connections. Client preferences remain in the Minecraft configuration directory, while permissions and message authority remain controlled by the server.

---

## Support and links

- [Source code](https://github.com/bielzinrx/Unsay)
- [Complete controls guide](https://github.com/bielzinrx/Unsay/blob/1.20.1/docs/CONTROLS.md)
- [Report a bug](https://github.com/bielzinrx/Unsay/issues)
- [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/unsay)

When reporting a problem, include the Unsay version, Minecraft version, loader, reproduction steps, and client/server logs when available.

---

*Unsay — take back what you said.*
