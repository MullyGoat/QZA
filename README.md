# QZA

Hypixel Skyblock QoL Mod featuring Shitter List & more.

Built for **Minecraft 26.1.2** on **Fabric**. It will not load on any other version.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.0 or newer
- Fabric API
- Java 25

## Install

Drop `qza-1.0.0.jar` into your `mods` folder alongside Fabric API.

## Commands

`/qza` opens the settings GUI. `/qzahelp` prints this list in game.

| Command | What it does |
| --- | --- |
| `/qza` | Open the settings GUI |
| `/shitter add <ign> [reason]` | Add someone to the shitter list |
| `/shitter remove <ign>` | Take someone off the list |
| `/shitter list [page]` | Show the list, 8 per page |
| `/shitter clear` | Wipe the list |
| `/qza music play` | Start the music now |
| `/qza music stop` | Stop the music |
| `/qza music folder` | Open the music folder |
| `/qza music reload` | Re-scan the music folder |
| `/qza reload` | Reload config and list from disk |

`/shitterlist` works as an alias for every `/shitter` command.

## Files

Settings, the shitter list and your music live in `config/qza/`, so updating the mod
never touches them.
