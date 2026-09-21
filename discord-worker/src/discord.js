
const DISCORD_API = 'https://discord.com/api/v10';

export const PING = 1;
export const APPLICATION_COMMAND = 2;
export const MESSAGE_COMPONENT = 3;

export const PONG = 1;
export const REPLY = 4;

export const EPHEMERAL = 64;

export const ACTION_ROW = 1;
export const BUTTON = 2;
export const BUTTON_SUCCESS = 3;

export async function verifySignature(publicKeyHex, signatureHex, timestamp, rawBody) {
    if (!publicKeyHex || !signatureHex || !timestamp) {
        return false;
    }

    let key;
    let signature;
    try {
        key = await crypto.subtle.importKey(
            'raw', fromHex(publicKeyHex), { name: 'Ed25519' }, false, ['verify']);
        signature = fromHex(signatureHex);
    } catch (e) {
        return false;
    }

    try {
        return await crypto.subtle.verify(
            'Ed25519', key, signature, new TextEncoder().encode(timestamp + rawBody));
    } catch (e) {
        return false;
    }
}

function fromHex(text) {
    if (typeof text !== 'string' || text.length % 2 !== 0 || /[^0-9a-fA-F]/.test(text)) {
        throw new Error('not hex');
    }
    const out = new Uint8Array(text.length / 2);
    for (let i = 0; i < out.length; i++) {
        out[i] = parseInt(text.substr(i * 2, 2), 16);
    }
    return out;
}

export function ephemeral(content) {
    return Response.json({
        type: REPLY,
        data: { content: content, flags: EPHEMERAL },
    });
}

export function invoker(interaction) {
    const user = (interaction.member && interaction.member.user) || interaction.user;
    return user && user.id ? user.id : null;
}

export function optionValue(interaction, name) {
    const options = (interaction.data && interaction.data.options) || [];
    const found = options.find((o) => o && o.name === name);
    return found ? found.value : null;
}

export async function openDm(env, userId) {
    const opened = await call(env, 'POST', '/users/@me/channels', { recipient_id: userId });
    if (opened.error) {
        return { error: opened.error };
    }
    if (!opened.body || !opened.body.id) {
        return { error: 'Discord did not return a DM channel' };
    }
    return { id: opened.body.id };
}

export async function sendMessage(env, channelId, payload) {
    return call(env, 'POST', `/channels/${encodeURIComponent(channelId)}/messages`, payload);
}

export async function addRole(env, guildId, userId, roleId) {
    const result = await call(env, 'PUT', `/guilds/${encodeURIComponent(guildId)}`
        + `/members/${encodeURIComponent(userId)}`
        + `/roles/${encodeURIComponent(roleId)}`);

    if (result.code === MISSING_PERMISSIONS) {
        return {
            code: result.code,
            error: `${result.error}. For a role it needs Manage Roles, and its own role `
                 + 'has to sit above the one it is handing out',
        };
    }
    return result;
}

export async function call(env, method, path, payload) {
    const headers = {
        'Authorization': `Bot ${env.DISCORD_BOT_TOKEN}`,
        'User-Agent': 'QZA-relay (https://github.com/MullyGoat/QZA, 1.0)',
    };
    const options = { method: method, headers: headers };
    if (payload !== undefined) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(payload);
    }

    let response;
    try {
        response = await fetch(`${DISCORD_API}${path}`, options);
    } catch (e) {
        return { error: 'could not reach Discord' };
    }

    if (response.ok) {
        try {
            return { body: await response.json() };
        } catch (e) {
            return { body: {} };
        }
    }

    const problem = await describe(response);
    return { error: problem.message, code: problem.code };
}

export const MISSING_PERMISSIONS = 50013;

async function describe(response) {
    let detail = '';
    let code = 0;
    try {
        const body = await response.json();
        code = body.code || 0;
        detail = body.message || '';
    } catch (e) {

    }

    return { code: code, message: explain(response, code, detail) };
}

function explain(response, code, detail) {
    if (code === 50007) {
        return 'they have DMs from server members switched off, or the bot does not '
             + 'share a server with them';
    }
    if (response.status === 401) {
        return 'the bot token was rejected (401)';
    }
    if (code === MISSING_PERMISSIONS) {
        return 'the bot is missing a permission';
    }
    if (response.status === 403) {
        return 'the bot is not allowed to do that (403)';
    }
    if (response.status === 404) {
        return 'that channel or user does not exist (404) - check the ids';
    }
    if (response.status === 429) {
        return 'Discord is rate limiting the bot (429)';
    }
    return `Discord returned ${response.status}${detail ? ` - ${detail}` : ''}`;
}
