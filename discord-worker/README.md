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

## Setup

This is the server owner's job, once. Players do not do any of it.

### 1. Make the Discord application

1. <https://discord.com/developers/applications> → **New Application** → call it
   `QZA` → **Create**.
2. On **General Information**, copy the **Application ID** and the
   **Public Key**. You need both.
3. **Bot** tab → **Reset Token** → copy it.

> **The bot token is a password.** Do not paste it into a chat, a file in this
> repo, or the mod's config. It goes into `wrangler secret put` and the
> `register.mjs` environment, nowhere else. If it leaks, hit **Reset Token**
> again and the old one dies immediately.

No Privileged Gateway Intents are needed. Leave them all off.

### 2. Invite the bot

Open this with your Application ID in place of `YOUR_APP_ID`:

```
https://discord.com/api/oauth2/authorize?client_id=YOUR_APP_ID&scope=bot%20applications.commands&permissions=19456
```

That grants View Channel, Send Messages and Embed Links, and registers slash
commands. The bot has to share a server with someone to DM them, so everyone who
wants alerts needs to be in this server.

### 3. Make the KV namespace

```
wrangler kv namespace create LINKS
```

Paste the id it prints into the `[[kv_namespaces]]` block in `wrangler.toml`.
The id is not a secret.

### 4. Set the secrets

```
wrangler secret put DISCORD_BOT_TOKEN
wrangler secret put DISCORD_PUBLIC_KEY
```

Optional, for the channel ping:

```
wrangler secret put DISCORD_CHANNEL_ID
wrangler secret put DISCORD_OWNER_ID
```

Discord ids are not secret, but they belong to real people, so they are set this
way rather than committed to the repo.

`CHANNEL_SCOPE` in `wrangler.toml` decides who gets a channel ping on top of
their DM:

| Value | Who |
| --- | --- |
| `everyone` (default) | anyone who has linked |
| `owner` | only `DISCORD_OWNER_ID` |
| `off` | nobody, DMs only |

Each channel message mentions only the one person whose party filled, so the
whole server sees it and nobody else gets a notification from it. `@everyone`,
`@here` and role pings are rejected by the relay outright, so the bot cannot be
made to notify a room.

### 5. Deploy

```
wrangler deploy
```

It prints the Worker URL, for example `https://qza-discord.qza.workers.dev`.

> On Windows, if PowerShell refuses to run `wrangler.ps1`, go through cmd:
> `cmd /c "cd /d C:\path\to\discord-worker && wrangler deploy"`

### 6. Point Discord at it

Back on the Discord application, **General Information** →
**Interactions Endpoint URL** → set it to your Worker URL with `/interactions`
on the end:

```
https://qza-discord.qza.workers.dev/interactions
```

Discord sends a signed test request when you save. If it refuses to save, the
public key is wrong — that is the whole purpose of the check.

### 7. Register the slash commands

```
node register.mjs
```

It asks for the application id, the bot token and optionally a server id.
Answering the prompts keeps the token out of your shell history and out of any
file. Giving a server id registers to that one server and takes effect
immediately; leaving it blank registers globally, which Discord can take up to
an hour to show.

`DISCORD_APP_ID`, `DISCORD_BOT_TOKEN` and `DISCORD_GUILD_ID` are used instead
when they are set, for scripting.

### 8. Point the mod at it

Only needed if you are not using the relay the mod ships with. Set
`discordAlertUrl` in `config/qza/config.json` to your Worker URL, then
`/qza reload`.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Discord will not save the interactions URL | `DISCORD_PUBLIC_KEY` is wrong or unset. It is on General Information, not the Bot tab, and it is not the Application ID. |
| `/link` says the code expired | Codes last ten minutes and work once. Get a fresh one with `/qza discord link`. |
| `This game is not linked` | The token was cleared, or `/unlink` was run. Link again. |
| `DM: they have DMs from server members switched off` | Discord → **Settings → Content & Social → Social permissions** → allow DMs from server members. |
| `Channel: the bot is not allowed to post there (403)` | The bot cannot see that channel. Fix its permissions, or re-invite with the URL in step 2. |
| `the bot token was rejected (401)` | Reset the token on the Bot tab and `wrangler secret put DISCORD_BOT_TOKEN` again. |
| `Relay is missing its LINKS KV namespace` | Step 3, and make sure the id landed in `wrangler.toml`. |
| Slash commands do not appear | Step 7. Global commands take up to an hour; use `DISCORD_GUILD_ID` for an instant one. |

Watch it live with `wrangler tail`.

## Limits

Six alerts a minute per linked player, 120 a minute across the whole relay, and
ten link attempts a minute per IP. The mod adds its own floor of one alert a
minute and will not re-announce a party that is already full.
