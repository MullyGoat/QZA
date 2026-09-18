/**
 * QZA stats proxy.
 *
 * Holds the Hypixel API key server side so the mod never ships it. The mod
 * asks this Worker for a player's dungeon numbers; the Worker asks Mojang and
 * Hypixel, trims the response down to what the mod needs, and caches it.
 *
 * Deploy with:
 *   wrangler secret put HYPIXEL_API_KEY
 *   wrangler deploy
 *
 * The key is a Worker secret. It is never in this file, the repo, or the jar.
 *
 *   GET /stats?name=<ign>     or     GET /stats?uuid=<uuid>
 *
 * Responds with
 *   { ok: true, name, uuid, cataExp, secrets,
 *     runs:   { cata: {"0":n,...}, master: {"1":n,...} },
 *     pb:     { cata: {"1":ms,...}, master: {"7":ms,...} } }
 * or
 *   { ok: false, error: "<message>" }
 *
 * Raw numbers only. Cata level, secret average and formatting are worked out
 * in the mod, so changing how any of that reads never needs a redeploy.
 */

const NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
const UUID_PATTERN = /^[0-9a-fA-F]{32}$/;

// Dungeon records barely move, so an hour of caching costs nothing in
// freshness and cuts upstream calls roughly sixfold.
const CACHE_SECONDS = 3600;
const NOT_FOUND_CACHE_SECONDS = 600;

// What any single caller may ask for, and the hard ceiling on calls this
// Worker will forward to Hypixel. The second one is what actually protects the
// key: past the budget the Worker refuses rather than spending it, so abuse
// degrades the abuser instead of taking the key down for everybody.
const IP_LIMIT_PER_MINUTE = 30;
const UPSTREAM_LIMIT_PER_MINUTE = 30;

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);

        if (request.method !== 'GET') {
            return fail('Only GET is supported', 405);
        }
        if (url.pathname !== '/stats' && url.pathname !== '/') {
            return fail('Unknown path', 404);
        }
        if (!env.HYPIXEL_API_KEY) {
            return fail('Proxy is missing its Hypixel API key', 500);
        }

        // Checked before the cache, so hammering a cached player still costs
        // the caller their own allowance rather than this Worker's quota.
        const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
        if (!await allow(`ip:${ip}`, IP_LIMIT_PER_MINUTE)) {
            return retryLater('Too many stats lookups from your connection');
        }

        const rawName = (url.searchParams.get('name') || '').trim();
        const rawUuid = (url.searchParams.get('uuid') || '').replace(/-/g, '').trim();

        if (!rawName && !rawUuid) {
            return fail('Pass name or uuid', 400);
        }
        if (rawName && !NAME_PATTERN.test(rawName)) {
            return fail('That is not a valid Minecraft name', 400);
        }
        if (rawUuid && !UUID_PATTERN.test(rawUuid)) {
            return fail('That is not a valid uuid', 400);
        }

        // Cache on the normalised identity so ten people asking about the same
        // player in the same minute cost one Hypixel call, not ten.
        const key = rawUuid
            ? `uuid:${rawUuid.toLowerCase()}`
            : `name:${rawName.toLowerCase()}`;
        const cacheKey = new Request(`https://qza.invalid/stats?${key}`, { method: 'GET' });
        const cache = caches.default;

        const hit = await cache.match(cacheKey);
        if (hit) {
            return withHeader(hit, 'X-QZA-Cache', 'hit');
        }

        // A miss means real upstream calls, so it has to fit the budget.
        if (!await allow('upstream', UPSTREAM_LIMIT_PER_MINUTE)) {
            return retryLater('Stats are busy right now, try again in a minute');
        }

        let payload;
        let status = 200;
        try {
            payload = await lookup(rawName, rawUuid, env.HYPIXEL_API_KEY);
        } catch (e) {
            payload = { ok: false, error: e && e.message ? e.message : 'Lookup failed' };
            status = e && e.status ? e.status : 502;
        }

        const seconds = payload.ok ? CACHE_SECONDS : NOT_FOUND_CACHE_SECONDS;
        const response = json(payload, status, seconds);

        // Only cache answers, never transport failures.
        if (status === 200 || status === 404) {
            ctx.waitUntil(cache.put(cacheKey, response.clone()));
        }
        return withHeader(response, 'X-QZA-Cache', 'miss');
    },
};

async function lookup(name, uuid, apiKey) {
    let id = uuid;
    let resolved = name;

    if (!id) {
        const profile = await resolveName(name);
        id = profile.id.replace(/-/g, '');
        resolved = profile.name || name;
    }

    const [profiles, player] = await Promise.all([
        hypixel(`https://api.hypixel.net/v2/skyblock/profiles?uuid=${id}`, apiKey),
        hypixel(`https://api.hypixel.net/v2/player?uuid=${id}`, apiKey),
    ]);

    if (!resolved && player && player.player && player.player.displayname) {
        resolved = player.player.displayname;
    }

    const list = profiles && Array.isArray(profiles.profiles) ? profiles.profiles : null;
    if (!list || list.length === 0) {
        throw withStatus(new Error(`${resolved || id} has no SkyBlock profiles`), 404);
    }

    const chosen = list.find((p) => p && p.selected) || list[0];
    const member = chosen && chosen.members ? chosen.members[id] : null;
    if (!member) {
        throw withStatus(new Error(`${resolved} is not on their selected profile`), 404);
    }

    const dungeons = member.dungeons || {};
    const types = dungeons.dungeon_types || {};
    const cata = types.catacombs || {};
    const master = types.master_catacombs || {};

    if (!types.catacombs) {
        throw withStatus(new Error(`${resolved} has no Catacombs data (API may be off)`), 404);
    }

    const achievements = player && player.player ? (player.player.achievements || {}) : {};
    const secrets = numberOr(achievements.skyblock_treasure_hunter, numberOr(dungeons.secrets, 0));

    return {
        ok: true,
        name: resolved,
        uuid: id,
        cataExp: numberOr(cata.experience, 0),
        secrets: secrets,
        runs: {
            cata: intMap(cata.tier_completions),
            master: intMap(master.tier_completions),
        },
        pb: {
            cata: intMap(cata.fastest_time_s_plus),
            master: intMap(master.fastest_time_s_plus),
        },
    };
}

/**
 * Name to uuid. Mojang answers 403 to requests from datacenter ranges, which is
 * where this Worker runs, so several resolvers are tried in turn. They all
 * return the same { id, name } shape.
 *
 * A 404 or 204 is a real answer - that name does not exist - so it stops there
 * rather than asking the next one. Only a block or an outage moves on.
 */
async function resolveName(name) {
    const encoded = encodeURIComponent(name);
    const resolvers = [
        `https://api.mojang.com/users/profiles/minecraft/${encoded}`,
        `https://mowojang.matdoes.dev/${encoded}`,
        `https://api.minecraftservices.com/minecraft/profile/lookup/name/${encoded}`,
    ];

    let lastStatus = 0;
    for (const url of resolvers) {
        let response;
        try {
            response = await fetch(url, {
                headers: {
                    'Accept': 'application/json',
                    'User-Agent': 'qza-stats-proxy',
                },
            });
        } catch (e) {
            continue;
        }

        if (response.status === 404 || response.status === 204) {
            throw withStatus(new Error(`No Minecraft account named ${name}`), 404);
        }
        if (!response.ok) {
            lastStatus = response.status;
            continue;
        }

        let body;
        try {
            body = await response.json();
        } catch (e) {
            lastStatus = 502;
            continue;
        }

        if (body && body.id) {
            return body;
        }
        throw withStatus(new Error(`No Minecraft account named ${name}`), 404);
    }

    throw withStatus(new Error(
        `Could not look up ${name}` + (lastStatus ? ` (name service returned ${lastStatus})` : '')), 502);
}

async function hypixel(url, apiKey) {
    const response = await fetch(url, {
        headers: { 'API-Key': apiKey, 'Accept': 'application/json' },
    });

    if (response.status === 429) {
        throw withStatus(new Error('Hypixel rate limit reached, try again shortly'), 429);
    }
    if (response.status === 403) {
        throw withStatus(new Error('Proxy API key was rejected by Hypixel'), 502);
    }
    if (!response.ok) {
        throw withStatus(new Error(`Hypixel returned ${response.status}`), 502);
    }

    const body = await response.json();
    if (body && body.success === false) {
        throw withStatus(new Error(body.cause || 'Hypixel rejected the request'), 502);
    }
    return body;
}

/** Keeps only whole non-negative numbers, so odd upstream values cannot leak through. */
function intMap(source) {
    const out = {};
    if (!source || typeof source !== 'object') {
        return out;
    }
    for (const [floor, value] of Object.entries(source)) {
        const n = Number(value);
        if (Number.isFinite(n) && n >= 0) {
            out[String(floor)] = Math.round(n);
        }
    }
    return out;
}

function numberOr(value, fallback) {
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 ? n : fallback;
}

function withStatus(error, status) {
    error.status = status;
    return error;
}

function json(body, status, cacheSeconds) {
    return new Response(JSON.stringify(body), {
        status: status,
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': `public, max-age=${cacheSeconds}`,
        },
    });
}

function fail(message, status) {
    return json({ ok: false, error: message }, status, 60);
}

function retryLater(message) {
    const response = json({ ok: false, error: message }, 429, 0);
    response.headers.set('Retry-After', '60');
    response.headers.set('Cache-Control', 'no-store');
    return response;
}

/**
 * A per-minute counter kept in the edge cache. Buckets are keyed by the minute
 * so they expire on their own.
 *
 * Approximate on purpose: the cache is per location and concurrent requests can
 * read the same value, so a burst may slip a few past. That is fine for holding
 * back sustained abuse, which is what this is for. If the counter itself fails
 * the request is allowed, since a broken limiter must not take the service down.
 */
async function allow(bucket, limit) {
    const minute = Math.floor(Date.now() / 60000);
    const key = new Request(`https://qza.invalid/rl/${encodeURIComponent(bucket)}/${minute}`);
    const cache = caches.default;

    try {
        const hit = await cache.match(key);
        const count = hit ? Number(await hit.text()) || 0 : 0;
        if (count >= limit) {
            return false;
        }
        await cache.put(key, new Response(String(count + 1), {
            headers: { 'Cache-Control': 'public, max-age=120' },
        }));
        return true;
    } catch (e) {
        return true;
    }
}

function withHeader(response, name, value) {
    const copy = new Response(response.body, response);
    copy.headers.set(name, value);
    return copy;
}
