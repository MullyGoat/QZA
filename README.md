# QZA

Hypixel Skyblock QoL Mod featuring Shitter List, QZA's Universal Hypixel Chat, & more.

Built for **Minecraft 26.1.2** on **Fabric**. It will not load on any other version.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.0 or newer
- Fabric API
- Java 25

## Install

Grab the jar from the [latest release](https://github.com/MullyGoat/QZA/releases/latest)
and drop it into your `mods` folder alongside Fabric API.

## Commands

`/qza` opens the settings GUI. `/qzahelp` prints this list in game.

| Command | What it does |
| --- | --- |
| `/qza` | Open the settings GUI |
| `/qza chat` | Open QZA Chat |
| `/qza gui` | Open GUI edit mode to move and resize notifications |
| `/qza gui reset` | Restore the GUI layout to its default |
| `/qza music` | Show what is playing and how many tracks are loaded |
| `/qza music play` | Start the music now |
| `/qza music stop` | Stop the music |
| `/qza music folder` | Open the music folder |
| `/qza music reload` | Re-scan the music folder |
| `/qza reload` | Reload config and list from disk |
| `/qza help` | Print this list in game |

### Shitter list

| Command | What it does |
| --- | --- |
| `/shitter` | Show the shitter list commands |
| `/shitter add <ign> [reason]` | Add someone to the shitter list |
| `/shitter remove <ign>` | Take someone off the list |
| `/shitter list [page]` | Show the list, 8 per page |
| `/shitter clear` | Wipe the list |
| `/shitter help` | Show the shitter list commands |

`/shitterlist` works as an alias for every `/shitter` command.

## Files

Settings, the shitter list, your saved DMs and your music live in `config/qza/`, so
updating the mod never touches them.

## Music formats

Drop `.mp3`, `.ogg`, `.wav`, `.aiff` or `.au` files into `config/qza/music/` and they
play as-is. No converting, and nothing extra to install.

## Third-party code

QZA bundles two audio decoders as nested jars so nothing has to be installed separately:

- [JLayer](http://www.javazoom.net/javalayer/javalayer.html) by JavaZoom — mp3 decoding, LGPL
- [Concentus](https://github.com/lostromb/concentus) by Logan Stromberg — Opus decoding, BSD

Both ship as separate jars inside `META-INF/jars/`, so either can be swapped out.
