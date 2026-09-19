# QZA

Hypixel Skyblock QoL Mod featuring Shitter List, QZA's Universal Hypixel Chat, & more.

Built for **Minecraft 26.1.2** on **Fabric**. It will not load on any other version.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.0 or newer
- Fabric API
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api)
- Java 25

## Install

Grab the jar from the [latest release](https://github.com/MullyGoat/QZA/releases/latest)
and drop it into your `mods` folder alongside Fabric API and the Hypixel Mod API.

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
| `/qza stats` | Check the stats source against your own profile |
| `/qza stats <ign>` | Show someone's cata, floor PB, class, secret average and MP |
| `/qza discord` | Show whether party full alerts are set up |
| `/qza discord test` | Send a test alert to Discord |
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

## Discord alerts

QZA can ping you on Discord when your party fills up, either because you are the
one recruiting or because you are tabbed out of the game, so you do not have to
sit watching it fill.

Three steps, with nothing to fill in by hand:

1. `/qza discord link` in game prints a six character code.
2. `/link <code>` in the QZA Discord. Only you see the reply.
3. Turn on **Party Full Alert** under **Notifications → Discord** in `/qza`, and
   check it with `/qza discord test`.

`/unlink` in Discord, or `/qza discord unlink` in game, stops the alerts and
deletes everything stored about you.

[discord-worker/README.md](discord-worker/README.md) covers what the relay is
and the short list of what it keeps.

## Files

Settings, the shitter list, your saved DMs and your music live in `config/qza/`, so
updating the mod never touches them.

## Music formats

Drop `.mp3`, `.ogg`, `.wav`, `.aiff` or `.au` files into `config/qza/music/` and they
play as-is. No converting, and nothing extra to install.

