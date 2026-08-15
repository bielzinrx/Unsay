# Unsay controls guide

Unsay adds message actions directly to Minecraft's existing chat screen. The mod must be installed on both the client and server so every edit or deletion can be validated and synchronized.

## Main actions

Open chat with `T`, hold `Shift`, and hover over a tracked message.

| Control | Result |
|---|---|
| Click the trash icon | Delete the hovered message for everyone |
| Click the pencil icon | Load your message into the input for editing |
| Click the reply arrow | Start a reply with the sender and a short preview |

Press `Enter` to submit an edit. Submitting an empty edit deletes the original message.

## Quick actions

Quick-delete and history shortcuts work when the chat input is empty.

| Shortcut | Result |
|---|---|
| `Shift + Delete` | Delete the newest available message |
| `Shift + Backspace` | Delete the newest available message |
| `Ctrl + Shift + Delete` | Delete the oldest available message |
| `Ctrl + Shift + Backspace` | Delete the oldest available message |
| `Shift + Up Arrow` | Browse recent messages for editing |

## Selection and bulk deletion

| Shortcut | Result |
|---|---|
| `Ctrl + click` | Select or deselect one message |
| `Ctrl + Shift + A` | Select deletable messages in the loaded chat history |
| `Delete` | Delete the current selection |
| `Esc` | Cancel the selection |

While selection mode is active, the input temporarily stops receiving keyboard input. Text already typed is preserved and returns after deletion or cancellation.

Players can act on their own messages. Operators can also moderate messages sent by other players, subject to server validation.

## Client configuration

Unsay creates `config/unsay-client.json` on the client. It can customize quick-delete shortcuts, selection controls, deletion animations, confirmation preferences and maximum selection size.

Changing client preferences never bypasses server permissions. The server remains authoritative for edits, deletions and moderation.

## Troubleshooting

- Confirm that the client and server use the same Unsay version and loader.
- Fabric installations also require the matching Fabric API.
- Message actions are only available for messages Unsay can track.
- Include both client and server logs when reporting synchronization problems.
