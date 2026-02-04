# Ayquza's Grief Utility Addon

A comprehensive Meteor Client addon designed for utility and automation, built to work seamlessly with the MLPI Scanner. Join our community at [discord.gg/mlp1](https://dsc.gg/mlpi).

**Credits:** Special thanks to [@rbmkblaze](https://github.com/rbmkblaze) for the login command implementation.

---

## Core Features

### Login Command
Quickly authenticate to cracked servers using the `.login` command (alias: `.l`).

**Usage:**
```
.login <username>
.l <username>
```

**Additional Commands:**
- `.login clear` - Remove all cracked accounts from the account manager
- `.login random` - Connect with a random account
- `.login remove` - Remove an account from the list
- `.login list` - Display all saved accounts

The command automatically reconnects you after switching accounts.



### Login Command Keybind
Bind a key to instantly login with the username currently in your clipboard. When triggered, executes `.login <clipboard>` automatically.

Includes an optional auto-reconnect feature (enabled by default).



### Fake Disconnect
Remain in the world after being disconnected from the server instead of being sent to the menu. Allows you to continue viewing the world, though server interaction is disabled.

The module displays the actual disconnect reason in chat. Useful for taking screenshots of builds or exploring the world state before leaving.



### Quick Join Buttons
Three Quick Join buttons added to:
- Multiplayer Menu
- Game Menu  
- Disconnect Screen

Copy a server IP to your clipboard and press Quick Join to connect instantly.



### Hotbar Stack Refill
Automatically refills hotbar stacks from your main inventory when they drop below a configurable threshold. 
For TrouserStreaks AutoMountain Module because I couldn't figure out how to do it with trouserstreaks! 


**Settings:**
- Threshold slider (1-64) - Refills stacks below this count
- Delay between refills (0-20 ticks)
- All hotbar slots mode or selected slot only



### AirSignPlace
Places a block where your crosshair is pointing at and optionally a sign on top.
(AirPlace from meteor with the addition to automatically place a sign on top)



### Quick Join Keybind
Bind a key to connect to the server IP in your clipboard. No need to navigate through menus.



### Clipboard Reconnect Button
Adds a reconnect button to the disconnect screen that uses your clipboard content as the username. Located below the standard Quick Join button.

Use case: Getting kicked for not being whitelisted? Copy a valid username, press Clipboard Reconnect, and rejoin immediately.



### Player Name Cycler
Automatically cycles through all online player names on the current server, logging in with each name and reconnecting.


**Features:**
- Optional stop when an OP account is detected
- Configurable hotkey to start/stop cycling
- Adjustable reconnect delay between account switches
- Automatically adds missing accounts to your account manager
- Debug mode for detailed logging

**Suggested by:** once_oh


### Custom Discord RPC
Configure custom Discord Rich Presence for the addon with your own application ID, images, and status text.

Includes in-game controls to update, restart, or test the RPC connection directly from Meteor settings.

---

## Additional kinda useless Utilities

- **Copy Server IP** - Quick copy current server address
- **Current Clipboard HUD** - Display clipboard contents on your HUD
- **Meteor Account Manager Keybind** - Bind a key to open the account manager
- **Disconnect Screenshot** *(largely obsolete due to Fake Disconnect module)*

---

## Installation

1. Download the latest release
2. Place the JAR file in your `.minecraft/mods` folder
3. Launch Minecraft with Meteor Client installed
4. Configure the addon modules in the Meteor Client menu

---

## Contributing

Found a bug or have a feature suggestion? Contact me on Discord: **@Ayquza**

---

## License & Credits

If you use or reference code from this project, please provide proper credit to both Ayquza and [@rbmkblaze](https://github.com/rbmkblaze).
