# Unsay 0.1.5b Beta — Message Selection Update

This beta introduces multi-message selection and expands Unsay's deletion controls directly inside Minecraft chat.

## What's new since 0.1.4b

- Added multi-message selection with `Ctrl + click`
- Added loaded-history selection with `Ctrl + Shift + A`
- Added selected-message deletion with `Delete`
- Improved selection across scrolled chat history
- Improved handling of duplicate and identical messages
- Improved cleanup of stale chat entries after deletion
- Improved server-synchronized deletion reliability
- The chat input now hides during selection and returns afterward
- Removed technical retry messages while editing
- Simplified bulk-action text and player-facing feedback

## Controls

| Action | Default control |
|---|---|
| Select or deselect a message | `Ctrl + click` |
| Select loaded chat messages | `Ctrl + Shift + A` |
| Delete selected messages | `Delete` |
| Cancel selection | `Esc` |
| Delete newest available message | `Shift + Delete` or `Shift + Backspace` |
| Delete oldest available message | `Ctrl + Shift + Delete` or `Ctrl + Shift + Backspace` |
| Browse recent messages for editing | `Shift + ↑` |

## Compatibility

- Minecraft 1.19.2
- Minecraft 1.20.1
- Fabric
- Forge
- Java 17
- Client and server installation required

> This is a beta pre-release. Update the server and participating clients together.
