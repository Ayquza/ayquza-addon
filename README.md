# Ayquza's Grief Utility Addon
A Meteor Client addon for utility and automation. Built to work with the MLPI Scanner.
Join the community: [discord.gg/mlpi](https://dsc.gg/mlpi) — Credits: [@rbmkblaze](https://github.com/rbmkblaze) for the login command.

---

## Account & Authentication

| Module | Description |
|---|---|
| **Login Command** | `.login <user>` / `.l <user>` — authenticate on cracked servers. Subcommands: `clear`, `random`, `remove`, `list`. Auto-reconnects after switching. |
| **Login Command Keybind** | Instantly executes `.login <clipboard>`. Optional auto-reconnect. |
| **Clipboard Reconnect Button** | Adds a button to the disconnect screen that reconnects using the clipboard as username. |
| **Account Manager Keybind** | Opens the Meteor account manager via a configurable keybind. |
| **Player Name Cycler** | Cycles through all online player names, logging in with each. Optionally stops on OP detection. Configurable delay and hotkey. |
| **Clear Cracked Accounts** | Periodically removes cracked accounts on a configurable interval (1–60 min). Optional debug logging. |

---

## Server Navigation

| Module | Description |
|---|---|
| **Quick Join** | Adds Quick Join buttons to the Multiplayer Menu, Game Menu, and Disconnect Screen. Connects to the clipboard IP instantly. |
| **Quick Join Keybind** | Binds a key to connect to the clipboard IP without navigating menus. |
| **Server Search** | Adds real-time name and MOTD search fields to the Multiplayer screen. |
| **Clear Current Server** | Keybind to remove the connected server or the selected server in the list from your saved list. |

---

## Griefing

| Module | Description |
|---|---|
| **Crash Command** | Crashes servers via stack overflow. *(from 0x06's Griefing Utils)* |
| **Cracked Kick** | `.ckick` / `.cracked-kick` — kicks a player on cracked servers. *(from 0x06's Griefing Utils)* |

---

## Utilities

| Module | Description |
|---|---|
| **Fake Disconnect** | Stay in-world after a disconnect. Shows the disconnect reason in chat. |
| **Hotbar Stack Refill** | Refills hotbar stacks from inventory below a configurable threshold. Configurable delay and slot selection. |
| **AirSignPlace** | Places a block at crosshair with an optional sign on top. |
| **Custom Discord RPC** | Custom Rich Presence with configurable app ID, images, and status. In-game controls to update or restart. |
| **Copy Server IP** | Copies the current server address to clipboard. |
| **Current Clipboard HUD** | Displays clipboard contents as a HUD element. |

---

## Installation

1. Download the latest release
2. Place the JAR in `.minecraft/mods`
3. Launch Minecraft with Meteor Client

---

## License & Credits
If you use code from this project, credit both **Ayquza** and [@rbmkblaze](https://github.com/rbmkblaze).
