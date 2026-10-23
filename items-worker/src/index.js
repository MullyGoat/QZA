const NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
const PLAYER_UUID_PATTERN = /^[0-9a-f]{32}$/;
const BASE64_PATTERN = /^[A-Za-z0-9+/]{16,2048}={0,2}$/;
const WORD_PATTERN = /^[a-z0-9_]{0,24}$/;
const ITEM_UUID_PATTERN = /^[0-9a-fA-F-]{8,40}$/;
const ITEM_ID_PATTERN = /^[A-Za-z0-9_:;.-]{0,64}$/;

const MAX_ITEMS = 40;
const MAX_ITEM_NAME = 48;
const MAX_ITEMS_TEXT = 24000;
const LIST_CACHE_SECONDS = 60;
const WRITE_GAP_MS = 15000;
const CLOCK_SLACK_MS = 300000;
const IP_WRITES_PER_MINUTE = 6;
const IP_READS_PER_MINUTE = 30;
const SIGNED_PREFIX = 'qza-items\n';
const LIVE_PREFIX = 'qza-live\n';
const ANIM_PATTERN = /^[A-Za-z]{1,24}$/;
const LIVE_BURST = 4;
const LIVE_WINDOW_MS = 2000;

// playerCertificateKeys from https://api.minecraftservices.com/publickeys (Mojang refuses requests from Cloudflare)
const MOJANG_CERTIFICATE_KEYS = [
    'MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==',
    'MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==',
];

let certificateKeys = null;

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);
        const ip = request.headers.get('CF-Connecting-IP') || 'unknown';

        if (url.pathname === '/items' && request.method === 'GET') {
            if (!await allow(`read:${ip}`, IP_READS_PER_MINUTE)) {
                return fail('Too many lookups from your connection', 429);
            }
            return list(env, ctx);
        }
        if (url.pathname === '/items' && request.method === 'PUT') {
            if (!await allow(`write:${ip}`, IP_WRITES_PER_MINUTE)) {
                return fail('Too many uploads from your connection', 429);
            }
            return upload(request, env);
        }
        if (url.pathname === '/live') {
            if (request.headers.get('Upgrade') !== 'websocket') {
                return fail('Connect with a WebSocket', 426);
            }
            if (!await allow(`live:${ip}`, IP_WRITES_PER_MINUTE)) {
                return fail('Too many connections from your connection', 429);
            }
            return env.LIVE.get(env.LIVE.idFromName('everyone')).fetch(request);
        }
        return fail('Unknown path', 404);
    },
};

async function upload(request, env) {
    let body;
    try {
        body = await request.json();
    } catch (e) {
        return fail('Body must be JSON', 400);
    }
    const uuid = typeof body.uuid === 'string' ? body.uuid.toLowerCase() : '';
    const name = typeof body.name === 'string' ? body.name : '';
    if (!PLAYER_UUID_PATTERN.test(uuid) || !NAME_PATTERN.test(name)) {
        return fail('Missing uuid or name. Update QZA', 400);
    }
    const signed = [body.key, body.keySig, body.sig];
    if (!signed.every(v => typeof v === 'string' && BASE64_PATTERN.test(v))
            || !Number.isSafeInteger(body.expires) || !Number.isSafeInteger(body.time)) {
        return fail('Missing chat key or signature. Update QZA', 400);
    }
    if (typeof body.items !== 'string' || body.items.length > MAX_ITEMS_TEXT) {
        return fail('items must be a JSON list as text', 400);
    }
    const now = Date.now();
    if (body.expires <= now) {
        return fail('Your Minecraft chat key has expired. Rejoin and try again', 403);
    }
    if (Math.abs(now - body.time) > CLOCK_SLACK_MS) {
        return fail('Your computer clock is too far off', 400);
    }
    let list;
    try {
        list = JSON.parse(body.items);
    } catch (e) {
        return fail('items must be a JSON list as text', 400);
    }
    if (!Array.isArray(list) || list.length > MAX_ITEMS) {
        return fail(`items must be a list of at most ${MAX_ITEMS}`, 400);
    }
    const items = [];
    for (const raw of list) {
        const item = clean(raw);
        if (!item) {
            return fail('An item has a field QZA does not understand', 400);
        }
        items.push(item);
    }

    const problem = await signatureProblem(env, uuid, body,
        `${SIGNED_PREFIX}${uuid}\n${name}\n${body.time}\n${body.items}`);
    if (problem) {
        return fail(problem, 403);
    }

    const key = `p:${uuid}`;
    const old = await env.ITEMS.get(key, 'json');
    if (old && now - (old.at || 0) < WRITE_GAP_MS) {
        return fail('Wait a few seconds before uploading again', 429);
    }
    if (old && Number.isSafeInteger(old.t) && body.time <= old.t) {
        return fail('That upload is older than the one already saved', 409);
    }
    if (items.length === 0) {
        await env.ITEMS.delete(key);
        return json({ ok: true, removed: true }, 200, 0);
    }
    await env.ITEMS.put(key, JSON.stringify({ name: name, at: now, t: body.time, items: items }));
    return json({ ok: true, count: items.length }, 200, 0);
}

async function signatureProblem(env, uuid, body, text) {
    try {
        const playerKey = base64(body.key);
        if (!await mojangSigned(env, certificate(uuid, body.expires, playerKey), base64(body.keySig))) {
            return 'Mojang did not sign that chat key';
        }
        const message = new TextEncoder().encode(text);
        if (!await verify(playerKey, 'SHA-256', base64(body.sig), message)) {
            return 'The upload was not signed with that chat key';
        }
        return null;
    } catch (e) {
        return 'That chat key could not be read';
    }
}

function certificate(uuid, expires, playerKey) {
    const bytes = new Uint8Array(24 + playerKey.length);
    const view = new DataView(bytes.buffer);
    view.setBigUint64(0, BigInt(`0x${uuid.slice(0, 16)}`));
    view.setBigUint64(8, BigInt(`0x${uuid.slice(16)}`));
    view.setBigInt64(16, BigInt(expires));
    bytes.set(playerKey, 24);
    return bytes;
}

async function mojangSigned(env, payload, signature) {
    if (!certificateKeys) {
        const keys = env.MOJANG_KEYS ? JSON.parse(env.MOJANG_KEYS) : MOJANG_CERTIFICATE_KEYS;
        certificateKeys = await Promise.all(keys.map(k => importKey(base64(k), 'SHA-1')));
    }
    for (const key of certificateKeys) {
        if (await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, signature, payload)) {
            return true;
        }
    }
    return false;
}

async function verify(spki, hash, signature, message) {
    return crypto.subtle.verify('RSASSA-PKCS1-v1_5', await importKey(spki, hash), signature, message);
}

function importKey(spki, hash) {
    return crypto.subtle.importKey('spki', spki, { name: 'RSASSA-PKCS1-v1_5', hash: hash }, false, ['verify']);
}

function base64(text) {
    const raw = atob(text);
    const bytes = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) {
        bytes[i] = raw.charCodeAt(i);
    }
    return bytes;
}

function clean(raw) {
    if (!raw || typeof raw !== 'object') {
        return null;
    }
    const words = ['t', 's', 'v', 'c', 'f', 'w'];
    const item = {};
    for (const field of words) {
        const value = raw[field] === undefined ? '' : raw[field];
        if (typeof value !== 'string' || !WORD_PATTERN.test(value)) {
            return null;
        }
        item[field] = value;
    }
    if (!item.t) {
        return null;
    }
    const seed = raw.p === undefined ? 0 : raw.p;
    if (!Number.isInteger(seed) || seed < 0 || seed > 999) {
        return null;
    }
    item.p = seed;
    const size = raw.z === undefined ? 100 : raw.z;
    if (!Number.isInteger(size) || size < 50 || size > 200) {
        return null;
    }
    item.z = size;
    const uuid = raw.u === undefined ? '' : raw.u;
    const id = raw.i === undefined ? '' : raw.i;
    const itemName = raw.n === undefined ? '' : raw.n;
    if (typeof uuid !== 'string' || (uuid && !ITEM_UUID_PATTERN.test(uuid))) {
        return null;
    }
    if (typeof id !== 'string' || !ITEM_ID_PATTERN.test(id)) {
        return null;
    }
    if (typeof itemName !== 'string') {
        return null;
    }
    item.u = uuid.toLowerCase();
    item.i = id;
    item.n = itemName.replace(/[\u0000-\u001f\u007f§]/g, '').slice(0, MAX_ITEM_NAME);
    return item;
}

async function list(env, ctx) {
    const cache = caches.default;
    const cacheKey = new Request('https://qza.invalid/items/all', { method: 'GET' });
    const hit = await cache.match(cacheKey);
    if (hit) {
        return hit;
    }
    const players = {};
    let cursor;
    do {
        const page = await env.ITEMS.list(cursor ? { prefix: 'p:', cursor: cursor } : { prefix: 'p:' });
        const values = await Promise.all(page.keys.map(k => env.ITEMS.get(k.name, 'json')));
        page.keys.forEach((k, i) => {
            const value = values[i];
            if (value && Array.isArray(value.items)) {
                players[k.name.slice(2)] = value.items;
            }
        });
        cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor);
    const response = json({ ok: true, players: players }, 200, LIST_CACHE_SECONDS);
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
}

function json(body, status, cacheSeconds) {
    return new Response(JSON.stringify(body), {
        status: status,
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': cacheSeconds > 0 ? `public, max-age=${cacheSeconds}` : 'no-store',
        },
    });
}

function fail(message, status) {
    return json({ ok: false, error: message }, status, 0);
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

async function helloProblem(env, body) {
    const uuid = typeof body.uuid === 'string' ? body.uuid.toLowerCase() : '';
    const name = typeof body.name === 'string' ? body.name : '';
    if (!PLAYER_UUID_PATTERN.test(uuid) || !NAME_PATTERN.test(name)) {
        return 'Missing uuid or name. Update QZA';
    }
    const signed = [body.key, body.keySig, body.sig];
    if (!signed.every(v => typeof v === 'string' && BASE64_PATTERN.test(v))
            || !Number.isSafeInteger(body.expires) || !Number.isSafeInteger(body.time)) {
        return 'Missing chat key or signature. Update QZA';
    }
    const now = Date.now();
    if (body.expires <= now) {
        return 'Your Minecraft chat key has expired. Rejoin and try again';
    }
    if (Math.abs(now - body.time) > CLOCK_SLACK_MS) {
        return 'Your computer clock is too far off';
    }
    return signatureProblem(env, uuid, body, `${LIVE_PREFIX}${uuid}\n${name}\n${body.time}`);
}

export class LiveRoom {
    constructor(state, env) {
        this.state = state;
        this.env = env;
    }

    async fetch(request) {
        const pair = new WebSocketPair();
        const [client, server] = Object.values(pair);
        this.state.acceptWebSocket(server);
        server.serializeAttachment({ uuid: null, window: 0, count: 0 });
        return new Response(null, { status: 101, webSocket: client });
    }

    async webSocketMessage(ws, message) {
        let body;
        try {
            body = JSON.parse(typeof message === 'string' ? message : new TextDecoder().decode(message));
        } catch (e) {
            return;
        }
        const meta = ws.deserializeAttachment() || {};
        if (!meta.uuid) {
            if (!body || body.type !== 'hello') {
                ws.close(1008, 'Say hello first');
                return;
            }
            const problem = await helloProblem(this.env, body);
            if (problem) {
                ws.send(JSON.stringify({ type: 'error', error: problem }));
                ws.close(1008, 'Not signed in');
                return;
            }
            meta.uuid = body.uuid.toLowerCase();
            ws.serializeAttachment(meta);
            ws.send(JSON.stringify({ type: 'ready' }));
            return;
        }
        if (!body || body.type !== 'inspect' || typeof body.a !== 'string' || !ANIM_PATTERN.test(body.a)) {
            return;
        }
        const speed = typeof body.s === 'number' && body.s >= 0.5 && body.s <= 2 ? body.s : 1;
        const now = Date.now();
        if (now - meta.window > LIVE_WINDOW_MS) {
            meta.window = now;
            meta.count = 0;
        }
        meta.count++;
        ws.serializeAttachment(meta);
        if (meta.count > LIVE_BURST) {
            return;
        }
        const out = JSON.stringify({ type: 'inspect', u: meta.uuid, a: body.a, s: speed });
        for (const other of this.state.getWebSockets()) {
            if (other === ws) {
                continue;
            }
            const them = other.deserializeAttachment();
            if (!them || !them.uuid) {
                continue;
            }
            try {
                other.send(out);
            } catch (e) {
                continue;
            }
        }
    }

    async webSocketClose(ws, code, reason) {
        try {
            ws.close(code, reason);
        } catch (e) {
            return;
        }
    }
}
