# Unsay

**Take back what you said.**

Delete, edit, and reply to Minecraft chat messages. Unsend and edits apply for every player on the server.

<p align="center">
  <img src="docs/media/store/header/unsend-header-1920x400.png" alt="Unsay" width="720"/>
</p>

<p align="center">
  <img src="docs/media/store/icon/unsend-icon-256.png" alt="Icon" width="96"/>
</p>

## Features

| | |
|--|--|
| **Delete** | Shift + trash — removed for everyone (ops can moderate) |
| **Edit** | Shift + pencil, or Shift+↑ on empty input |
| **Reply** | Quote any tracked message in global chat |

## Delete

1. Open chat (`T`)
2. Hold **Shift** over your message
3. Click trash — removed for everyone

**Also:** `Shift+Delete` / `Shift+Backspace`

![Hold Shift — delete for everyone](docs/media/store/gallery/01-delete.jpg)

## Edit

- **Shift** + pencil, or  
- Empty input + **Shift+↑** to browse your own lines  

Send to update for everyone. Empty field = unsend.

![Edit your message](docs/media/store/gallery/02-edit.jpg)

## Reply

Click the reply arrow to quote a message in global chat.

![Reply with a quote](docs/media/store/gallery/03-reply.jpg)

## Requirements

- Client **and** server need the mod
- **Fabric** (Fabric API) or **Forge**
- Minecraft version: see the release / jar name (e.g. 1.20.1, 1.19.2)

## Build

```bash
./gradlew build
```

Outputs:

- `fabric/build/libs/Unsay-1.19.2-Fabric-*.jar`
- `forge/build/libs/Unsay-1.19.2-Forge-*.jar`

Requires JDK 17+.

## Mod id

Display name **Unsay**. Internal mod id is `unsend` (do not change without a migration plan).

## License

MIT
