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
  "class": "berserk",
  "cataExp": 569809640,
  "secrets": 123456,
  "magicalPower": 1546,
  "runs": { "cata": { "0": 12, "7": 500 }, "master": { "7": 300 } },
  "pb":   { "cata": { "7": 512340 }, "master": { "7": 299000 } }
}
```

`pb` values are milliseconds, keyed by floor number. Failures come back as
`{ "ok": false, "error": "…" }`.

Deliberately raw: cata level, secret average and all formatting are worked out
in the mod, so changing how those read never needs a redeploy.

## Magical power

Hypixel records only `highest_magical_power`, the best a player has ever had.
Sell a mythic accessory and that number stays where it was, so it says nothing
about who is in front of you now. The Worker adds up the accessory bag instead:
unzip the NBT, read each accessory's rarity off its tooltip — which is what a
recombobulator changes — and pay out once per upgrade family, so a Speed
Talisman left sitting beside the Speed Artifact does not count twice. The three
accessories that pay more than their rarity are handled too: Hegemony counts
double, an Abicase adds one for every two abiphone contacts on top of its
rarity, and an imbued Rift Prism adds eleven.

Reading the rarity off the tooltip means coping with how Hypixel writes it. A
shiny accessory has its rarity line wrapped in obfuscated text, so once the
formatting codes come off the line reads `a MYTHIC ACCESSORY a`, and soulbound
ones print another line below it. The line is found by the word `ACCESSORY`
rather than by where it sits, and the rarity is matched anywhere on it.

The total is the one you see outside a dungeon. Dungeon accessories pay double
while you are inside one, which is a property of where you are standing rather
than of the profile, so it is not something the proxy can report.

`magicalPower` is `null` when the bag cannot be read at all, which is what an
inventory API left switched off looks like. The mod prints "API Off" for that
rather than a total that would really be a guess.

The upgrade families in `src/accessories.js` come from NotEnoughUpdates,
narrowed to the ids Hypixel's own item list calls an `ACCESSORY`. Families
Hypixel adds later are simply missing, which at worst counts a superseded
accessory twice.

## Protecting the key

The mod ships the Worker's URL, which anyone can read out of the jar, so this
endpoint has to assume strangers will find it. Three things keep that from
costing you your Hypixel key:

- **Caching.** An hour per player, ten minutes for a name that does not exist.
  A popular player is fetched once for everyone rather than once per user.
- **A per-IP limit.** 30 lookups a minute per caller, checked before the cache,
  so hammering an already-cached player still costs the caller their own
  allowance rather than this Worker's request quota.
- **An upstream budget.** At most 30 calls a minute are forwarded to Hypixel in
  total. Past that the Worker refuses instead of spending the key, so abuse
  degrades the abuser rather than taking stats down for everybody.

Both limits are per-minute counters in the edge cache, keyed by the minute so
they expire on their own. They are deliberately approximate: the cache is
per-location and concurrent requests can read the same value, so a burst may
slip a few through. That is fine for holding back sustained abuse, which is what
they are for. If the counter itself errors the request is allowed, because a
broken limiter must not take the service down.

Note what this is *not*: a secret shipped inside a mod is not secret, so none of
this proves the caller is really QZA. The only approach that does is verifying a
Minecraft session against Mojang, which is what Odin and NoammAddons do. These
limits bound what any caller can spend instead, which addresses the practical
problem without pretending to be unbreakable.

If Hypixel does throttle anyway, the Worker passes back a clear message rather
than failing silently.
