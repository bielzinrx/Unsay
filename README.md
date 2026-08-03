# Unsay

### _Take back what you said._

[![Forge](https://img.shields.io/badge/Forge-1.19.2%20%7C%201.20.1-b07219?style=flat-square&logo=curseforge&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Fabric](https://img.shields.io/badge/Fabric-1.19.2%20%7C%201.20.1-5c7a4e?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Side](https://img.shields.io/badge/Side-Client%20%2B%20Server-1a6b8a?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Version](https://img.shields.io/badge/Version-0.1.5b%20Pre--release-f2a900?style=flat-square)](https://github.com/bielzinrx/Unsay/releases/tag/0.1.5b)
[![Status](https://img.shields.io/badge/Status-Beta-f2a900?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

<img width="1536" height="1024" alt="Unsay banner" src="https://github.com/user-attachments/assets/010edfec-d69a-4298-8b2e-8e7c44d3d50a" />

Unsay brings modern, native-feeling message controls directly into Minecraft chat.

Delete something you regret, correct a typo without sending another message, reply with context, or select several messages and remove them together.

No commands. No separate menus. Message actions happen inside the existing chat screen and are validated by the server before being synchronized to connected players.

> Unsay is currently in beta. Feedback and reproducible bug reports are welcome.

---

## Features

| Feature | Description |
|---|---|
| **Delete** | Remove one of your messages for everyone. |
| **Message selection** | Select individual messages or the loaded chat history and delete them together. |
| **Edit** | Correct a previously sent message without posting a replacement. |
| **Reply** | Quote a tracked message with its sender and a compact preview. |
| **Quick history** | Browse your recent messages with `Shift + ↑`. |
| **Moderation** | Operators can manage messages sent by other players. |
| **Native interface** | Controls are integrated directly into Minecraft chat. |
| **Server synchronization** | Edits and deletions are validated by the server before being applied. |
| **Loader support** | Available for Fabric and Forge on Minecraft 1.19.2 and 1.20.1. |

---

## Delete a message

1. Open chat with `T`.
2. Hold **Shift**.
3. Hover over one of your messages.
4. Click the trash icon.

The trash icon removes only the message being hovered.

With an empty chat input, you can quickly remove your latest available message using:

- `Shift + Delete`
- `Shift + Backspace`

<img width="840" height="68" alt="Deleting a message with Unsay" src="https://github.com/user-attachments/assets/f3fb5006-a1c4-43f3-b07e-0713e19a6426" />

---

## Select and delete multiple messages

Unsay includes a lightweight selection mode directly inside chat.

| Control | Action |
|---|---|
| `Ctrl + click` | Select or deselect an individual message |
| `Ctrl + Shift + A` | Select deletable messages from the loaded chat history |
| `Delete` | Delete the current selection |
| `Esc` | Cancel the selection |

While selection mode is active, the chat input temporarily disappears and stops receiving keyboard input. Any text already typed is preserved and returns after deletion or cancellation.

Selected messages are removed through server-validated actions and use Unsay's deletion animation.

Players can select their own messages. Operators can also select messages from other players for moderation.

---

## Edit a message

Start editing in either of these ways:

- Hold **Shift** and click the pencil icon beside your message.
- Leave the chat input empty and press `Shift + ↑` to browse your recent messages.

Press `Enter` to apply the edit for everyone.

Submitting an empty edit removes the message instead.

<img width="840" height="117" alt="Editing a message with Unsay" src="https://github.com/user-attachments/assets/c8915395-135c-4d24-a5c7-7fa771d64c92" />

---

## Reply to a message

Click the reply arrow beside a tracked message.

Unsay adds a compact quote containing the original sender and a short message preview before your response.

<img width="813" height="118" alt="Replying to a message with Unsay" src="https://github.com/user-attachments/assets/edeefd26-2802-44f5-b009-1bef48fed8d7" />

---

## Controls

| Action | Default control |
|---|---|
| Show message controls | Hold `Shift` |
| Delete hovered message | Click the trash icon |
| Delete newest available message | `Shift + Delete` or `Shift + Backspace` |
| Delete oldest available message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Select or deselect a message | `Ctrl + click` |
| Select loaded chat messages | `Ctrl + Shift + A` |
| Delete selected messages | `Delete` |
| Cancel selection | `Esc` |
| Browse messages for editing | `Shift + ↑` |

---

## Client configuration

Unsay creates the following client configuration file:

```text
config/unsay-client.json
```

It can be used to customize quick-delete shortcuts, selection controls, deletion animations, confirmation preferences, and the maximum selection size.

The configuration is client-side. Message permissions and deletion authority remain controlled by the server.

---

## Requirements

Unsay must be installed on both the **client and server**.

- **Fabric:** Fabric API is required
- **Forge:** no Fabric API required
- **Java:** Java 17 or newer
- **Minecraft:** 1.19.2 or 1.20.1

All participating clients and the server should use the same Unsay release.

Unsay is designed for multiplayer servers but also works in singleplayer.

---

## Installation

1. Install Fabric or Forge for your Minecraft version.
2. Install Fabric API when using Fabric.
3. Download the matching Unsay JAR.
4. Place the JAR in the `mods` folder on both the client and server.
5. Restart the game and server.

---

## Supported branches

| Branch | Minecraft version |
|---|---|
| [`1.20.1`](https://github.com/bielzinrx/Unsay/tree/1.20.1) | Minecraft 1.20.1 |
| [`1.19.2`](https://github.com/bielzinrx/Unsay/tree/1.19.2) | Minecraft 1.19.2 |

---

## Building from source

JDK 17 or newer is required.

```bash
./gradlew clean build
```

Build outputs:

```text
fabric/build/libs/Unsay-<minecraft>-Fabric-<version>.jar
forge/build/libs/Unsay-<minecraft>-Forge-<version>.jar
```

---

## Reporting bugs

When opening an issue, include:

- Unsay version
- Minecraft version
- Fabric or Forge
- Singleplayer or multiplayer
- Relevant client and server logs
- Clear reproduction steps

Reports involving repeated messages, scrolled chat history, message selection, custom chat formatting, edits, replies, or multiplayer synchronization are especially useful during beta development.

---

## Project information

- **Display name:** Unsay
- **Internal mod ID:** `unsend`
- **Required side:** Client and server
- **Required Java version:** Java 17
- **License:** MIT

The internal mod ID should not be changed without a migration plan.

---

_Unsay — take back what you said._
