
import {
    CODE_TTL_SECONDS, bind, bindingFor, forget, hashToken, newCode, newToken,
    putCode, takeCode, tidyCode, unbind,
} from './links.js';
import {
    ACTION_ROW, APPLICATION_COMMAND, BUTTON, BUTTON_SUCCESS, MESSAGE_COMPONENT,
    PING, PONG, REPLY, addRole, call, ephemeral, invoker, openDm, optionValue,
    sendMessage, verifySignature,
} from './discord.js';

const COLOUR_ALERT = 0x5865f2;
const COLOUR_TEST = 0x99aab5;

const VERIFY_BUTTON = 'qza-verify';
const DEFAULT_VERIFY_TEXT = 'Press the button below to verify and get access '
    + 'to the rest of the server.';

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

        ign: typeof body.ign === 'string' ? body.ign.slice(0, 16) : '',

        replaces: typeof body.replaces === 'string' && body.replaces
            ? await hashToken(body.replaces) : null,
    });

    return json({ ok: true, code: code, token: token, expiresIn: CODE_TTL_SECONDS }, 200);
}

async function interactions(request, env) {
    if (!env.DISCORD_PUBLIC_KEY) {
        return fail('Relay is missing DISCORD_PUBLIC_KEY '
            + '(wrangler secret put DISCORD_PUBLIC_KEY)', 500);
    }

    const raw = await request.text();
    const ok = await verifySignature(
        env.DISCORD_PUBLIC_KEY,
        request.headers.get('X-Signature-Ed25519'),
        request.headers.get('X-Signature-Timestamp'),
        raw);

    if (!ok) {

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

    const who = invoker(interaction);
    if (!who) {
        return ephemeral('Could not tell who ran that.');
    }

    if (interaction.type === MESSAGE_COMPONENT) {
        const button = interaction.data && interaction.data.custom_id;
        if (button === VERIFY_BUTTON) {
            return runVerify(env, interaction, who);
        }
        return ephemeral('That button is not one this bot knows.');
    }

    if (interaction.type !== APPLICATION_COMMAND) {
        return ephemeral('That is not something this bot knows how to do.');
    }

    const name = interaction.data && interaction.data.name;
    if (name === 'link') {
        return runLink(env, interaction, who);
    }
    if (name === 'unlink') {
        return runUnlink(env, who);
    }
    if (name === 'verifypanel') {
        return runVerifyPanel(env, interaction);
    }
    if (name === 'verifydiag') {
        return runVerifyDiag(env, interaction);
    }
    return ephemeral('Unknown command.');
}

const PERMISSION_ADMINISTRATOR = 1n << 3n;
const PERMISSION_MANAGE_ROLES = 1n << 28n;

async function runVerifyDiag(env, interaction) {
    const guildId = interaction.guild_id;
    if (!guildId) {
        return ephemeral('Run that inside the server.');
    }

    const roleId = env.DISCORD_VERIFY_ROLE_ID;
    if (!roleId) {
        return ephemeral('No DISCORD_VERIFY_ROLE_ID is set on the relay.');
    }

    const [roles, self] = await Promise.all([
        call(env, 'GET', `/guilds/${encodeURIComponent(guildId)}/roles`),
        call(env, 'GET', `/users/@me/guilds/${encodeURIComponent(guildId)}/member`),
    ]);

    if (roles.error) {
        return ephemeral(`Could not read the roles: ${roles.error}`);
    }
    if (self.error) {
        return ephemeral(`Could not read my own membership: ${self.error}`);
    }

    const all = Array.isArray(roles.body) ? roles.body : [];
    const mine = (self.body && self.body.roles) || [];
    const held = all.filter((r) => mine.includes(r.id));

    let top = null;
    for (const role of held) {
        if (top === null || role.position > top.position) {
            top = role;
        }
    }

    const target = all.find((r) => r.id === roleId);

    let granted = 0n;
    try {
        granted = BigInt(interaction.app_permissions || '0');
    } catch (e) {
        granted = 0n;
    }
    const canManage = (granted & PERMISSION_ADMINISTRATOR) !== 0n
        || (granted & PERMISSION_MANAGE_ROLES) !== 0n;

    const lines = [];
    lines.push(`Target role id: \`${roleId}\``);
    lines.push(target
        ? `Target role: **${target.name}** at position ${target.position}`
              + `${target.managed ? ' (managed by an integration)' : ''}`
        : 'Target role: **not found in this server**');
    lines.push(top
        ? `My highest role: **${top.name}** at position ${top.position}`
        : 'My highest role: none but @everyone');
    lines.push(`Manage Roles here: ${canManage ? 'yes' : 'no'}`);
    lines.push('');

    if (!target) {
        lines.push('That role id does not exist in this server. Copy it again with '
            + 'Developer Mode on, right clicking the role in Server Settings then Roles.');
    } else if (target.managed) {
        lines.push(`**${target.name}** belongs to an integration, so nobody can hand it `
            + 'out, including you. Make a plain role and use that instead.');
    } else if (!canManage) {
        lines.push('I do not have Manage Roles in this channel. A channel permission '
            + 'override can take it away even when the server role grants it, so check '
            + 'the channel settings as well as the role.');
    } else if (top === null || top.position <= target.position) {
        lines.push(`My highest role is not above **${target.name}**. Drag my role above `
            + 'it in Server Settings then Roles. Equal positions do not count, it has to '
            + 'be strictly higher.');
    } else {
        lines.push('This all looks right, so the button should work. If it still does '
            + 'not, press it again and tell me what it says now.');
    }

    return ephemeral(lines.join('\n'));
}

function runVerifyPanel(env, interaction) {
    if (!env.DISCORD_VERIFY_ROLE_ID) {
        return ephemeral('Verification is not set up yet. Run '
            + '`wrangler secret put DISCORD_VERIFY_ROLE_ID` on the relay first.');
    }

    const text = optionValue(interaction, 'message') || DEFAULT_VERIFY_TEXT;

    return Response.json({
        type: REPLY,
        data: {
            embeds: [{
                title: 'Verification',
                description: text,
                color: COLOUR_ALERT,
            }],
            components: [{
                type: ACTION_ROW,
                components: [{
                    type: BUTTON,
                    style: BUTTON_SUCCESS,
                    label: 'Verify',
                    custom_id: VERIFY_BUTTON,
                }],
            }],
            allowed_mentions: { parse: [] },
        },
    });
}

async function runVerify(env, interaction, who) {
    const roleId = env.DISCORD_VERIFY_ROLE_ID;
    if (!roleId) {
        return ephemeral('Verification is not set up on the relay.');
    }
    if (!interaction.guild_id) {
        return ephemeral('That only works inside the server.');
    }

    const held = (interaction.member && interaction.member.roles) || [];
    if (held.includes(roleId)) {
        return ephemeral('You are already verified.');
    }

    const granted = await addRole(env, interaction.guild_id, who, roleId);
    if (granted.error) {
        return ephemeral(`Could not give you the role: ${granted.error}`);
    }
    return ephemeral('Verified. Welcome in.');
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

function escapeMarkdown(text) {
    return text.replace(/[\\`*_~|>[\]()#-]/g, '\\$&');
}

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
