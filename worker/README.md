# QZA stats proxy

A small Cloudflare Worker that holds the Hypixel API key so QZA does not have to.

QZA asks this Worker for a player's dungeon numbers. The Worker asks Mojang and
Hypixel, trims the answer down, and caches it. The mod only ever knows the
Worker's URL, which is not a secret, so the key is never in the jar.

## Why it works this way

A key shipped inside a mod is not secret. Anyone can pull strings out of a jar in
seconds, so a bundled key would be public immediately, would breach the terms it
was issued under, and would be shared by every user against one rate limit.
Keeping it server side is what Odin and NoammAddons do too.

## One-time setup

1. Get a key from [developer.hypixel.net](https://developer.hypixel.net) (sign in
   with your Minecraft account, create a personal-use app).

2. Install Wrangler and sign in to Cloudflare (free tier is plenty):

   ```bash
   npm install -g wrangler
   wrangler login
   ```

3. From this `worker/` directory, store the key as a secret. It is typed into
   this prompt and uploaded straight to Cloudflare — it is not written to disk
   and does not belong in any file here:

   ```bash
   wrangler secret put HYPIXEL_API_KEY
   ```

4. Deploy:

   ```bash
   wrangler deploy
   ```

   Wrangler prints a URL like `https://qza-stats.<your-subdomain>.workers.dev`.

5. Give that URL to QZA. Either set `statsProxyUrl` in
   `config/qza/config.json`, or have it baked in as the default so your users
   need no setup at all.

## Checking it

```bash
curl "https://qza-stats.<your-subdomain>.workers.dev/stats?name=Refraction"
```

Expect `{"ok":true,...}`. A repeat call within ten minutes returns
`X-QZA-Cache: hit` and does not touch Hypixel.

## What it returns

```json
{
  "ok": true,
  "name": "Steve",
  "uuid": "…",
  "cataExp": 569809640,
  "secrets": 123456,
  "runs": { "cata": { "0": 12, "7": 500 }, "master": { "7": 300 } },
  "pb":   { "cata": { "7": 512340 }, "master": { "7": 299000 } }
}
```

`pb` values are milliseconds, keyed by floor number. Failures come back as
`{ "ok": false, "error": "…" }`.

Deliberately raw: cata level, secret average and all formatting are worked out
in the mod, so changing how those read never needs a redeploy.

## Rate limiting

Answers are cached for ten minutes per player, misses for two. Popular players
get fetched once for everyone rather than once per user, which keeps this far
under the Hypixel limit. If Hypixel does throttle, the Worker passes back a
clear message rather than failing silently.
