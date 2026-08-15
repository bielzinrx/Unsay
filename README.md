# Unsay

### Take back what you said.

[![Forge](https://img.shields.io/badge/Forge-1.19.2%20%7C%201.20.1-b07219?style=flat-square&logo=curseforge&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Fabric](https://img.shields.io/badge/Fabric-1.19.2%20%7C%201.20.1-5c7a4e?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Side](https://img.shields.io/badge/Side-Client%20%2B%20Server-1a6b8a?style=flat-square)](https://github.com/bielzinrx/Unsay)
[![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk&logoColor=white)](https://github.com/bielzinrx/Unsay)
[![Version](https://img.shields.io/badge/Version-0.1.6b%20Beta-f2a900?style=flat-square)](https://github.com/bielzinrx/Unsay/releases/tag/0.1.6b)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

<img width="1536" height="1024" alt="Unsay banner" src="https://github.com/user-attachments/assets/010edfec-d69a-4298-8b2e-8e7c44d3d50a" />

**Unsay is a simple Minecraft mod that lets you delete, edit, and reply to chat messages directly from the chat screen.**

No commands and no separate menus. Actions are validated by the server and synchronized with connected players.

> Unsay is currently in beta. It must be installed on both the client and server.

---

## Main controls

Open chat and hold **Shift** over a tracked message to reveal its actions:

| Icon | Action |
|---|---|
| Trash | Delete your message for everyone |
| Pencil | Edit your message in place |
| Reply arrow | Reply with the sender and a short preview |

<img width="840" height="68" alt="Deleting a message with Unsay" src="https://github.com/user-attachments/assets/f3fb5006-a1c4-43f3-b07e-0713e19a6426" />

<details>
<summary><strong>Advanced shortcuts and bulk actions</strong></summary>

| Action | Default control |
|---|---|
| Delete newest available message | `Shift + Delete` or `Shift + Backspace` |
| Delete oldest available message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Select or deselect a message | `Ctrl + click` |
| Select loaded chat messages | `Ctrl + Shift + A` |
| Delete selected messages | `Delete` |
| Cancel a selection | `Esc` |
| Browse recent messages for editing | `Shift + Up Arrow` |

Selection mode supports server-validated bulk deletion. Operators can also moderate messages from other players.

</details>

For behavior, configuration and every shortcut, see the [complete controls guide](docs/CONTROLS.md).

---

## What Unsay adds

- Delete a sent message for everyone.
- Correct a typo without posting a replacement message.
- Reply to a tracked message with context.
- Select and delete multiple messages together.
- Browse recent messages for quick editing.
- Moderate messages as a server operator.
- Keep edits and deletions synchronized through the server.

<p>
  <img width="49%" alt="Editing a message with Unsay" src="https://github.com/user-attachments/assets/c8915395-135c-4d24-a5c7-7fa771d64c92" />
  <img width="49%" alt="Replying to a message with Unsay" src="https://github.com/user-attachments/assets/edeefd26-2802-44f5-b009-1bef48fed8d7" />
</p>

---

## Installation

### Fabric

1. Install Fabric Loader for the same Minecraft version as the Unsay build.
2. Install the matching Fabric API.
3. Place the Unsay and Fabric API JARs in the `mods` folder on the **client and server**.

### Forge

1. Install Forge for the same Minecraft version as the Unsay build.
2. Place the Unsay JAR in the `mods` folder on the **client and server**.

Use the same Unsay release and loader on every participating client and on the server. Java 17 or newer is required.

| Supported branch | Minecraft |
|---|---|
| [`1.20.1`](https://github.com/bielzinrx/Unsay/tree/1.20.1) | 1.20.1 |
| [`1.19.2`](https://github.com/bielzinrx/Unsay/tree/1.19.2) | 1.19.2 |

---

## Configuration

Client preferences are stored in:

```text
config/unsay-client.json
```

The file controls shortcuts, confirmations, selection size and deletion animations. Permissions and message authority always remain on the server.

---

## Reporting bugs

[Open an issue](https://github.com/bielzinrx/Unsay/issues) with the Unsay version, Minecraft version, loader, client/server logs and clear reproduction steps.

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
