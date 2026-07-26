# Unsay

**Take back what you said.**

<img width="1536" height="1024" alt="Unsay banner" src="https://github.com/user-attachments/assets/010edfec-d69a-4298-8b2e-8e7c44d3d50a" />

Unsay adds native-feeling message controls to Minecraft chat.

Delete, edit, and reply to chat messages directly from the chat screen. Changes are synchronized through the server, so when you edit or remove one of your messages, every connected player sees the result.

> Unsay is currently in beta. Bug reports and feedback are welcome.

---

## Features

| Feature | Description |
|---|---|
| **Delete** | Remove your messages for everyone. Operators can also moderate messages from other players. |
| **Edit** | Correct a previously sent message without posting a second one. |
| **Reply** | Quote a tracked message and reply to it in global chat. |
| **Quick history** | Browse your recent messages with `Shift + ↑` while the input is empty. |
| **Clean interface** | Action buttons only appear while holding `Shift`. |
| **Multiplayer synchronization** | Edits and deletions are validated by the server before being applied. |

---

## Delete a message

1. Open chat with `T`.
2. Hold **Shift**.
3. Hover over one of your messages.
4. Click the trash icon.

The message is removed for every player.

Operators can also remove messages sent by other players.

### Keyboard shortcuts

With an empty chat input:

- `Shift + Delete`
- `Shift + Backspace`

These shortcuts remove your most recent available message.

<img width="840" height="68" alt="Deleting a message with Unsay" src="https://github.com/user-attachments/assets/f3fb5006-a1c4-43f3-b07e-0713e19a6426" />

---

## Edit a message

You can start editing in either of these ways:

- Hold **Shift** and click the pencil icon beside your message.
- Leave the chat input empty and press `Shift + ↑` to browse your recent messages.

Press `Enter` to apply the edit for everyone.

Submitting an empty edit removes the message instead.

<img width="840" height="117" alt="Editing a message with Unsay" src="https://github.com/user-attachments/assets/c8915395-135c-4d24-a5c7-7fa771d64c92" />

---

## Reply to a message

Click the reply arrow beside a tracked message.

Unsay adds a compact quote containing the original sender and message preview, then sends your response to global chat.

<img width="813" height="118" alt="Replying to a message with Unsay" src="https://github.com/user-attachments/assets/edeefd26-2802-44f5-b009-1bef48fed8d7" />

---

## Requirements

Unsay must be installed on both the **client and server**.

Supported loaders:

- Fabric with Fabric API
- Forge

Supported Minecraft versions are maintained in separate branches:

| Branch | Minecraft version |
|---|---|
| `1.20.1` | Minecraft 1.20.1 |
| `1.19.2` | Minecraft 1.19.2 |

Unsay is designed for multiplayer servers but also works in singleplayer.

---

## Installation

1. Install Fabric or Forge for your Minecraft version.
2. Install Fabric API when using the Fabric version.
3. Download the matching Unsay JAR.
4. Place the JAR in the `mods` folder on both the client and server.
5. Restart the game and server.

All players who need to see synchronized edits, deletions, and replies must have the mod installed.

---

## Building from source

Requires **JDK 17 or newer**.

```bash
./gradlew build
```

Build outputs:

```text
fabric/build/libs/Unsay-<minecraft>-Fabric-<version>.jar
forge/build/libs/Unsay-<minecraft>-Forge-<version>.jar
```

---

## Reporting bugs

When reporting a problem, include:

- Unsay version
- Minecraft version
- Fabric or Forge
- Singleplayer or multiplayer
- Relevant client and server logs
- Steps required to reproduce the issue

Reports involving duplicate messages, custom chat formatting, edited replies, or multiplayer synchronization are especially useful during beta development.

---

## Project information

- **Display name:** Unsay
- **Internal mod ID:** `unsend`
- **License:** MIT

The internal mod ID should not be changed without a migration plan.

---

*Unsay — take back what you said.*
