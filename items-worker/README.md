# QZA shared custom items

Lets QZA users see each other's custom items, like a knife skin on someone's Hyperion.

Turn on **Shared Custom Items** under **Items** in `/qza`. While you're on SkyBlock, QZA uploads the look of
each of your custom items, and shows other QZA users' custom items when they hold them.

What is stored, per player:

- your Minecraft name and uuid
- for each custom item: its SkyBlock uuid and item id, its name, and its look (knife, style, skin, pattern,
  wear, material or lightsaber colours)

Nothing else is sent. To prove an upload is really from you, QZA signs it with the chat key Minecraft gets for
your account at login, the same key servers use to check signed chat. Mojang signs that key, so this worker checks
Mojang's signature and then yours, without asking Mojang anything. Your login token and private key never leave
your game. Turning off **Share Mine** (or the whole feature) uploads an empty list, which deletes your entry.

## Endpoints

- `GET /items` returns `{ ok, players: { "<uuid>": [items] } }`, cached for a minute.
- `PUT /items` with `{ uuid, name, key, expires, keySig, time, items, sig }` replaces your list. `items` is the
  list as JSON text, and `sig` signs `qza-items\n<uuid>\n<name>\n<time>\n<items>`. An empty list deletes it.
- `/live` is a WebSocket (one `LiveRoom` Durable Object). Send `{ type: "hello", uuid, name, key, expires, keySig,
  time, sig }` first, with `sig` over `qza-live\n<uuid>\n<name>\n<time>`. After `{ type: "ready" }`, each
  `{ type: "inspect", a, s }` you send (a few per 2 seconds at most) goes to everyone else signed in as
  `{ type: "inspect", u, a, s }`, so QZA users can play each other's inspect animations.

## Setup

```
wrangler kv namespace create ITEMS
```

Paste the id it prints into `wrangler.toml`, then `wrangler deploy`.
