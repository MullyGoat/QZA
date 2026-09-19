# QZA Discord relay

Pings a player on Discord when their dungeon party fills up. Anyone in the
server can use it — they link their game to their Discord account once, with a
code, and the relay messages whoever that code belonged to.

The bot token lives on this Worker and never goes near the jar or anybody's
config folder.

## For players

Three steps, no files to edit, no ids to copy:

1. In game, `/qza discord link` (or the **Link** button under
   **Notifications → Discord** in `/qza`). It prints a six character code.
2. In the QZA Discord, run `/link <code>`. Only you see the reply.
3. Turn on **Party Full Alert** in `/qza`, and check it with
   `/qza discord test`.

`/unlink` in Discord stops the alerts and deletes everything stored about you.
So does `/qza discord unlink` in game.

An alert is sent when your party **just hit 5/5** — it will not repeat while the
party stays full — and **either** of these is true:

- **you are the party leader**, so a group you are recruiting for always tells
  you, whether or not you are watching it,
- **the Minecraft window is not focused**, so a party filling while you are
  looking at something else reaches you whoever leads it.

That leaves exactly one case quiet: somebody else's party filling up while you
are sat watching it happen.

## What is stored

Two records per linked player, and nothing else:

```
token:<sha256 of their token>  ->  { discordId, linkedAt }
discord:<their discord id>     ->  { tokens: [hash, ...] }
```

Plus, for ten minutes while a code is live and then gone with it:

```
code:<CODE>  ->  { tokenHash, ign, replaces }
```

- **No Minecraft uuid**, no account name after the code expires, no party
  membership, no chat, no IP addresses, nothing about what anyone was doing.
- The in-game name is held only while the code is alive, so the person
  confirming the link can see which account they are attaching.
- **Tokens are stored as SHA-256 hashes, not as themselves.** A dump of the
  namespace hands over no working credentials.
- An alert carries only the party size. The relay does not log it.

A Discord id has to be stored, because it is the thing being messaged. `/unlink`
deletes it along with everything pointing at it.

`LinkTest.mjs` asserts all of this, including that the raw token and the in-game
name never appear in storage.
