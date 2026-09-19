/**
 * QZA Discord relay.
 *
 * Pings a player on Discord when their dungeon party fills up. Anyone in the
 * server can use it: they link their game to their Discord account once, with a
 * code, and the relay messages whoever that code belonged to.
 *
 * Kept apart from the stats proxy on purpose. That one is public and answers
 * anonymous requests all day; this one holds a bot token that can message
 * people. Separate Workers mean separate secret stores, so a mistake in one
 * cannot reach the other.
 *
 * Deploy with:
 *   wrangler kv namespace create LINKS      (once, then paste the id below)
 *   wrangler secret put DISCORD_BOT_TOKEN
 *   wrangler secret put DISCORD_PUBLIC_KEY
 *   wrangler secret put DISCORD_CHANNEL_ID  (optional, for the channel ping)
 *   wrangler secret put DISCORD_OWNER_ID    (optional, see CHANNEL_SCOPE)
 *   wrangler deploy
 *
 * Nothing identifying is in wrangler.toml, so the repo stays clean of ids.
 *
 *   POST /link/start          { ign, replaces }      -> { ok, code, token }
 *   POST /alert               Bearer <token>         -> { ok }
 *   POST /unlink              Bearer <token>         -> { ok }
 *   POST /interactions        signed by Discord      -> /link and /unlink
 *
 * See links.js for the complete list of what is stored, which is short.
 */

import {
    CODE_TTL_SECONDS, bind, bindingFor, forget, hashToken, newCode, newToken,
    putCode, takeCode, tidyCode, unbind,
} from './links.js';
import {
    APPLICATION_COMMAND, PING, PONG, ephemeral, invoker, openDm, optionValue,
    sendMessage, verifySignature,
} from './discord.js';

/** Blurple for a real alert, grey for a test, so the two never look alike. */
const COLOUR_ALERT = 0x5865f2;
const COLOUR_TEST = 0x99aab5;

// Per token, so one noisy player cannot drown out anybody else, and overall so
// the relay cannot be used to hammer Discord.
const ALERT_LIMIT_PER_MINUTE = 6;
const ALERT_LIMIT_TOTAL_PER_MINUTE = 120;
const LINK_LIMIT_PER_MINUTE = 10;

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);

        if (request.method !== 'POST') {
            return fail('Only POST is supported', 405);
        }
        if (!env.DISCORD_BOT_TOKEN) {
            return fail('Relay is missing DISCORD_BOT_TOKEN '
                + '(wrangler secret put DISCORD_BOT_TOKEN)', 500);
        }
        if (!env.LINKS) {
            return fail('Relay is missing its LINKS KV namespace '
                + '(wrangler kv namespace create LINKS, then add it to wrangler.toml)', 500);
        }

        switch (url.pathname) {
            case '/interactions':
                return interactions(request, env);
            case '/link/start':
                return linkStart(request, env);
            case '/alert':
                return alert(request, env);
            case '/unlink':
                return unlink(request, env);
            default:
                return fail('Unknown path', 404);
        }
    },
};

// ---- the game asking for a code --------------------------------------------

/**
 * Hands out a code and the token it will belong to. Deliberately open: the
 * token is worthless until somebody proves ownership of a Discord account by
 * running /link with the code, and the code dies in ten minutes.
 */
async function linkStart(request, env) {
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    if (!await allow(`link:${ip}`, LINK_LIMIT_PER_MINUTE)) {
        return fail('Too many link attempts from your connection, wait a minute', 429);
    }

    let body;
    try {
        body = await request.json();
    } catch (e) {
        body = {};
    }

    const token = newToken();
    const code = newCode();

    await putCode(env.LINKS, code, {
        tokenHash: await hashToken(token),
        // Shown once when confirming the link so the person can see which
        // account they are attaching, then thrown away with the code.
        ign: typeof body.ign === 'string' ? body.ign.slice(0, 16) : '',
        // Whatever this copy of the mod was using before, so relinking does not
        // leave the previous token working.
        replaces: typeof body.replaces === 'string' && body.replaces
            ? await hashToken(body.replaces) : null,
    });

    return json({ ok: true, code: code, token: token, expiresIn: CODE_TTL_SECONDS }, 200);
}

// ---- Discord running a slash command ---------------------------------------

async function interactions(request, env) {
    if (!env.DISCORD_PUBLIC_KEY) {
        return fail('Relay is missing DISCORD_PUBLIC_KEY '
            + '(wrangler secret put DISCORD_PUBLIC_KEY)', 500);
    }

    // Read once, as text. The signature covers these exact bytes, so parsing
    // first and re-serialising would not verify.
    const raw = await request.text();
    const ok = await verifySignature(
        env.DISCORD_PUBLIC_KEY,
        request.headers.get('X-Signature-Ed25519'),
        request.headers.get('X-Signature-Timestamp'),
        raw);

    if (!ok) {
        // Discord requires exactly this for an unverified request, and checks
        // for it when you save the endpoint URL.
        return new Response('invalid request signature', { status: 401 });
    }

    let interaction;
    try {
        interaction = JSON.parse(raw);
    } catch (e) {
        return fail('Body was not JSON', 400);
    }

    if (interaction.type === PING) {
        return Response.json({ type: PONG });
    }
    if (interaction.type !== APPLICATION_COMMAND) {
        return ephemeral('That is not something this bot knows how to do.');
    }

    const who = invoker(interaction);
    if (!who) {
        return ephemeral('Could not tell who ran that.');
    }

    const name = interaction.data && interaction.data.name;
    if (name === 'link') {
        return runLink(env, interaction, who);
    }
    if (name === 'unlink') {
        return runUnlink(env, who);
    }
    return ephemeral('Unknown command.');
}

async function runLink(env, interaction, who) {
    const code = tidyCode(optionValue(interaction, 'code'));
    if (!code) {
        return ephemeral('Give me the code from `/qza discord link` in game.');
    }

    const record = await takeCode(env.LINKS, code);
    if (!record) {
        return ephemeral('That code is wrong or has expired. Codes last '
            + `${Math.round(CODE_TTL_SECONDS / 60)} minutes - run \`/qza discord link\` `
            + 'in game for a fresh one.');
    }

    await bind(env.LINKS, record.tokenHash, who, record.replaces);

    const account = record.ign ? ` **${escapeMarkdown(record.ign)}**` : '';
    return ephemeral(`Linked${account} to your Discord. You will get a DM when your `
        + 'party fills up.\n\nRun `/unlink` any time to delete everything stored about you.');
}

async function runUnlink(env, who) {
    const removed = await forget(env.LINKS, who);
    if (removed === 0) {
        return ephemeral('You were not linked, so there was nothing stored about you.');
    }
    return ephemeral(`Unlinked. Everything stored about you is deleted${
        removed > 1 ? ` (${removed} linked accounts)` : ''}. Run \`/qza discord link\` `
        + 'in game to set it up again.');
}

/** Stops an in game name from bolding or linking the confirmation message. */
function escapeMarkdown(text) {
    return text.replace(/[\\`*_~|>[\]()#-]/g, '\\$&');
}

// ---- the game sending an alert ---------------------------------------------

async function alert(request, env) {
    const token = bearer(request);
    if (!token) {
        return fail('No alert token. Run /qza discord link in game.', 401);
    }

    const tokenHash = await hashToken(token);

    if (!await allow(`alert:${tokenHash.slice(0, 16)}`, ALERT_LIMIT_PER_MINUTE)) {
        return fail('Too many alerts in the last minute', 429);
    }
    if (!await allow('alert:all', ALERT_LIMIT_TOTAL_PER_MINUTE)) {
        return fail('The relay is busy, try again in a minute', 429);
    }

    const binding = await bindingFor(env.LINKS, tokenHash);
    if (!binding) {
        return fail('This game is not linked to a Discord account. '
            + 'Run /qza discord link in game.', 403);
    }

    let body;
    try {
        body = await request.json();
    } catch (e) {
        return fail('Body was not JSON', 400);
    }

    const wantsDm = body.dm !== false;
    const wantsChannel = body.channel !== false && channelAllowed(env, binding.discordId);
    if (!wantsDm && !wantsChannel) {
        return fail('Nothing to send: both the DM and the channel ping are off', 400);
    }

    const message = compose(body, binding.discordId);
    const problems = [];

    // One after another rather than together, so hitting a Discord rate limit
    // on the first does not also burn the second.
    if (wantsDm) {
        const to = await openDm(env, binding.discordId);
        if (to.error) {
            problems.push(`DM: ${to.error}`);
        } else {
            const sent = await sendMessage(env, to.id, message.dm);
            if (sent.error) {
                problems.push(`DM: ${sent.error}`);
            }
        }
    }

    if (wantsChannel) {
        const sent = await sendMessage(env, env.DISCORD_CHANNEL_ID, message.channel);
        if (sent.error) {
            problems.push(`Channel: ${sent.error}`);
        }
    }

    if (problems.length > 0) {
        return json({ ok: false, error: problems.join('; ') }, 502);
    }
    return json({ ok: true }, 200);
}

/**
 * Whether this person's alerts may also go to the shared channel.
 *
 * "everyone" is the default: the channel becomes a feed everybody can see, and
 * because each message mentions only the one person whose party filled, nobody
 * else is notified by it. "owner" narrows it to the relay's own account, "off"
 * turns it off entirely.
 */
function channelAllowed(env, discordId) {
    if (!env.DISCORD_CHANNEL_ID) {
        return false;
    }
    const scope = (env.CHANNEL_SCOPE || 'everyone').toLowerCase();
    if (scope === 'off') {
        return false;
    }
    if (scope === 'everyone') {
        return true;
    }
    return !!env.DISCORD_OWNER_ID && discordId === env.DISCORD_OWNER_ID;
}

async function unlink(request, env) {
    const token = bearer(request);
    if (!token) {
        return fail('No alert token to unlink', 401);
    }
    const removed = await unbind(env.LINKS, await hashToken(token));
    return json({ ok: true, removed: removed }, 200);
}

function bearer(request) {
    const header = request.headers.get('Authorization') || '';
    return header.startsWith('Bearer ') ? header.slice(7).trim() : '';
}

/**
 * The two wordings. The channel one carries the mention, because that is what
 * makes it a ping; the DM does not need one, since the message itself is
 * already the notification.
 */
function compose(body, userId) {
    const test = body.event === 'test';
    const size = Number.isFinite(Number(body.size)) ? Math.round(Number(body.size)) : 5;

    const embed = {
        title: test ? 'QZA test alert' : 'Party is full',
        description: test
            ? 'If you can read this, the relay works. A real alert looks like this one '
              + 'but in blurple, and only arrives when your party fills up.'
            : `Your party just hit ${clampSize(size)}/5.`,
        color: test ? COLOUR_TEST : COLOUR_ALERT,
        timestamp: new Date().toISOString(),
    };

    // parse: [] switches off @everyone, @here and role pings outright, so the
    // only thing this bot can ever mention is the one account it is sending to.
    return {
        dm: { embeds: [embed], allowed_mentions: { parse: [] } },
        channel: {
            content: `<@${userId}>`,
            embeds: [embed],
            allowed_mentions: { parse: [], users: [userId] },
        },
    };
}

function clampSize(size) {
    return Math.max(0, Math.min(99, size));
}

/**
 * A per-minute counter kept in the edge cache, keyed by the minute so buckets
 * expire on their own. Approximate, since the cache is per location and racing
 * requests can read the same value, which is fine for holding back abuse. A
 * broken limiter allows the request rather than taking the relay down.
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

function json(body, status) {
    return new Response(JSON.stringify(body), {
        status: status,
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'no-store',
        },
    });
}

function fail(message, status) {
    return json({ ok: false, error: message }, status);
}
