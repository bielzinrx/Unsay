# Unsay

**Take back what you said.**
---

<img width="1536" height="1024" alt="image" src="https://github.com/user-attachments/assets/010edfec-d69a-4298-8b2e-8e7c44d3d50a" />

---
Unsay lets you delete, edit, and reply to Minecraft chat messages. When you unsend or edit your message, every player sees the change.
---
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
<img width="840" height="68" alt="image" src="https://github.com/user-attachments/assets/f3fb5006-a1c4-43f3-b07e-0713e19a6426" />
---
## Edit

- **Shift** + pencil, or  
- Empty input + **Shift+↑** to browse your own lines  

Send to update for everyone. Empty field = unsend.
<img width="840" height="117" alt="image" src="https://github.com/user-attachments/assets/c8915395-135c-4d24-a5c7-7fa771d64c92" />
---
## Reply

Click the reply arrow to quote a message in global chat.
<img width="813" height="118" alt="image" src="https://github.com/user-attachments/assets/edeefd26-2802-44f5-b009-1bef48fed8d7" />
---
## Requirements

- Client **and** server need the mod
- **Fabric** (Fabric API) or **Forge**
- Minecraft version: this branch (`1.20.1`)

## Branches

| Branch | Minecraft |
|--------|-----------|
| `1.20.1` | 1.20.1 |
| `1.19.2` | 1.19.2 |

## Build

```bash
./gradlew build
```

Outputs:

- `fabric/build/libs/Unsay-1.20.1-Fabric-*.jar`
- `forge/build/libs/Unsay-1.20.1-Forge-*.jar`

Requires JDK 17+.

## Mod id

Display name **Unsay**. Internal mod id is `unsend` (do not change without a migration plan).

## License

MIT
