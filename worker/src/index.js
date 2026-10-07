

const NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
const UUID_PATTERN = /^[0-9a-fA-F]{32}$/;

const SOOPY = 'https://soopy.dev/api/v2/';

const CATA_FLOORS = { e: 0, f1: 1, f2: 2, f3: 3, f4: 4, f5: 5, f6: 6, f7: 7 };

const MASTER_FLOORS = { m1: 1, m2: 2, m3: 3, m4: 4, m5: 5, m6: 6, m7: 7 };

const CACHE_SECONDS = 3600;
const NOT_FOUND_CACHE_SECONDS = 600;

const IP_LIMIT_PER_MINUTE = 30;
const UPSTREAM_LIMIT_PER_MINUTE = 15;

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);

        if (request.method !== 'GET') {
            return fail('Only GET is supported', 405);
        }
        if (url.pathname !== '/stats' && url.pathname !== '/') {
            return fail('Unknown path', 404);
        }

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

        const key = rawUuid
            ? `uuid:${rawUuid.toLowerCase()}`
            : `name:${rawName.toLowerCase()}`;
        const cacheKey = new Request(`https://qza.invalid/stats?${key}`, { method: 'GET' });
        const cache = caches.default;

        const hit = await cache.match(cacheKey);
        if (hit) {
            return withHeader(hit, 'X-QZA-Cache', 'hit');
        }

        if (!await allow('upstream', UPSTREAM_LIMIT_PER_MINUTE)) {
            return retryLater('Stats are busy right now, try again in a minute');
        }

        let payload;
        let status = 200;
        try {
            payload = await lookup(rawName, rawUuid);
        } catch (e) {
            payload = { ok: false, error: e && e.message ? e.message : 'Lookup failed' };
            status = e && e.status ? e.status : 502;
        }

        const seconds = payload.ok ? CACHE_SECONDS : NOT_FOUND_CACHE_SECONDS;
        const response = json(payload, status, seconds);

        if (status === 200 || status === 404) {
            ctx.waitUntil(cache.put(cacheKey, response.clone()));
        }
        return withHeader(response, 'X-QZA-Cache', 'miss');
    },
};

async function lookup(name, uuid) {
    let id = uuid;
    let resolved = name;

    if (!id) {
        const profile = await resolveName(name);
        id = profile.id;
        resolved = profile.name || name;
    }

    const [skyblock, player] = await Promise.all([
        soopy(`${SOOPY}player_skyblock/${id}`, resolved || id),
        soopy(`${SOOPY}player/${id}`, resolved || id),
    ]);

    const profiles = (skyblock.data && skyblock.data.profiles) || {};
    const chosen = pickProfile(profiles, id);
    if (!chosen) {
        throw withStatus(new Error(`${resolved || id} has no SkyBlock profiles`), 404);
    }

    const member = chosen.members ? chosen.members[id] : null;
    const dungeons = (member && member.dungeons) || null;
    const floors = dungeons ? dungeons.floorStats : null;
    if (!floors) {
        throw withStatus(new Error(`${resolved} has no Catacombs data`), 404);
    }

    const current = player.data && typeof player.data.username === 'string'
        ? player.data.username : '';
    if (current) {
        resolved = current;
    } else if (!resolved) {
        resolved = id;
    }

    return {
        ok: true,
        name: resolved,
        uuid: id,

        class: typeof dungeons.selected_class === 'string' ? dungeons.selected_class : '',
        cataExp: numberOr(dungeons.catacombs_xp, 0),
        secrets: secretsOf(player),
        magicalPower: powerOf(member),
        runs: {
            cata: completions(floors, CATA_FLOORS),
            master: completions(floors, MASTER_FLOORS),
        },
        pb: {
            cata: bests(floors, CATA_FLOORS),
            master: bests(floors, MASTER_FLOORS),
        },
    };
}

function pickProfile(profiles, id) {
    let chosen = null;
    let bestExp = -1;

    for (const key of Object.keys(profiles)) {
        const profile = profiles[key];
        const member = profile && profile.members ? profile.members[id] : null;
        if (!member) {
            continue;
        }
        if (profile.current || profile.selected) {
            return profile;
        }
        const exp = member.dungeons ? numberOr(member.dungeons.catacombs_xp, 0) : 0;
        if (exp > bestExp) {
            bestExp = exp;
            chosen = profile;
        }
    }
    return chosen;
}

function secretsOf(player) {
    const stats = player && player.data ? player.data.stats : null;
    const achievements = stats && stats.achievements ? stats.achievements.skyblock : null;
    return achievements ? numberOr(achievements.dungeon_secrets, 0) : 0;
}

function powerOf(member) {
    const reforge = member ? member.accessory_reforge : null;
    if (!reforge) {
        return null;
    }
    const power = Number(reforge.highest_magical_power);
    return Number.isFinite(power) && power >= 0 ? Math.round(power) : null;
}

function completions(floors, map) {
    const out = {};
    let total = 0;

    for (const key of Object.keys(map)) {
        const entry = floors[key];
        const runs = entry ? Number(entry.completions) : 0;
        if (Number.isFinite(runs) && runs > 0) {
            out[String(map[key])] = Math.round(runs);
            total += Math.round(runs);
        }
    }
    if (total > 0) {
        out.total = total;
    }
    return out;
}

function bests(floors, map) {
    const out = {};
    let best = 0;

    for (const key of Object.keys(map)) {
        const entry = floors[key];
        const time = entry && entry.fastest_time_s_plus
            ? Number(entry.fastest_time_s_plus.raw) : 0;
        if (Number.isFinite(time) && time > 0) {
            out[String(map[key])] = Math.round(time);
            if (best === 0 || time < best) {
                best = Math.round(time);
            }
        }
    }
    if (best > 0) {
        out.best = best;
    }
    return out;
}

const MISSING = 'missing';

const NAME_RESOLVERS = [
    {
        url: (encoded) => `https://playerdb.co/api/player/minecraft/${encoded}`,
        read: (status, body) => {
            if (status === 400 || status === 404) {
                return MISSING;
            }
            const player = body && body.data ? body.data.player : null;
            return player && player.id ? { id: player.id, name: player.username } : null;
        },
    },
    {
        url: (encoded) => `https://api.minetools.eu/uuid/${encoded}`,
        read: (status, body) => {
            if (status !== 200 || !body) {
                return null;
            }
            return body.id ? { id: body.id, name: body.name } : MISSING;
        },
    },
    {
        url: (encoded) => `https://api.mojang.com/users/profiles/minecraft/${encoded}`,
        read: (status, body) => {
            if (status === 404 || status === 204) {
                return MISSING;
            }
            return body && body.id ? { id: body.id, name: body.name } : null;
        },
    },
    {
        url: (encoded) =>
            `https://api.minecraftservices.com/minecraft/profile/lookup/name/${encoded}`,
        read: (status, body) => {
            if (status === 404 || status === 204) {
                return MISSING;
            }
            return body && body.id ? { id: body.id, name: body.name } : null;
        },
    },
];

async function resolveName(name) {
    const encoded = encodeURIComponent(name);
    let lastStatus = 0;

    for (const resolver of NAME_RESOLVERS) {
        let response;
        try {
            response = await fetch(resolver.url(encoded), {
                headers: {
                    'Accept': 'application/json',
                    'User-Agent': 'qza-stats-proxy',
                },
            });
        } catch (e) {
            continue;
        }

        let body = null;
        try {
            body = await response.json();
        } catch (e) {
            body = null;
        }

        const found = resolver.read(response.status, body);
        if (found === MISSING) {
            throw withStatus(new Error(`No Minecraft account named ${name}`), 404);
        }
        if (found && found.id) {
            return { id: found.id.replace(/-/g, ''), name: found.name || name };
        }
        lastStatus = response.status;
    }

    throw withStatus(new Error(
        `Could not look up ${name}` + (lastStatus ? ` (name service returned ${lastStatus})` : '')), 502);
}

async function soopy(url, who) {
    const response = await fetch(url, {
        headers: { 'Accept': 'application/json', 'User-Agent': 'qza-stats-proxy' },
    });

    let body = null;
    try {
        body = await response.json();
    } catch (e) {
        body = null;
    }

    if (response.status === 429) {
        throw withStatus(new Error('Stats source is busy right now, try again shortly'), 429);
    }
    if (!body) {
        throw withStatus(new Error(`Stats source returned ${response.status}`), 502);
    }
    if (body.success !== true) {
        const why = body.error && body.error.description
            ? body.error.description
            : (body.error && body.error.name ? body.error.name : null);
        if (response.status === 404 || (why && /not found|invalid/i.test(why))) {
            throw withStatus(new Error(`No SkyBlock data for ${who}`), 404);
        }
        throw withStatus(new Error(why || 'Stats source rejected the request'), 502);
    }
    return body;
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
