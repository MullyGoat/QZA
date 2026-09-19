/**
 * Everything that talks to Discord: verifying that an interaction really came
 * from them, and the handful of REST calls the relay makes.
 */

const DISCORD_API = 'https://discord.com/api/v10';

/** Interaction types, from Discord's docs. */
export const PING = 1;
export const APPLICATION_COMMAND = 2;

/** Response types. */
export const PONG = 1;
export const REPLY = 4;

/** Only the person who ran the command sees the answer. */
export const EPHEMERAL = 64;

/**
 * Whether this request was really signed by Discord for this application.
 *
 * Discord signs the timestamp followed by the raw body with the application's
 * Ed25519 key, so the body has to be verified exactly as it arrived rather than
 * re-serialised from a parsed object. Getting this wrong is not a small bug: an
 * unverified endpoint lets anybody forge an interaction and link their Discord
 * account to somebody else's alerts.
 */
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

/** An answer only the person who ran the command can see. */
export function ephemeral(content) {
    return Response.json({
        type: REPLY,
        data: { content: content, flags: EPHEMERAL },
    });
}

/**
 * Whoever ran the command. In a server this is under `member`, in a DM under
 * `user`, and the relay accepts both.
 */
export function invoker(interaction) {
    const user = (interaction.member && interaction.member.user) || interaction.user;
    return user && user.id ? user.id : null;
}

export function optionValue(interaction, name) {
    const options = (interaction.data && interaction.data.options) || [];
    const found = options.find((o) => o && o.name === name);
    return found ? found.value : null;
}

/** The bot's DM channel with one user, opened fresh each time it is needed. */
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

/** One place for every Discord call, so every failure reads the same way. */
export async function call(env, method, path, payload) {
    let response;
    try {
        response = await fetch(`${DISCORD_API}${path}`, {
            method: method,
            headers: {
                'Authorization': `Bot ${env.DISCORD_BOT_TOKEN}`,
                'Content-Type': 'application/json',
                'User-Agent': 'QZA-relay (https://github.com/MullyGoat/QZA, 1.0)',
            },
            body: JSON.stringify(payload),
        });
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

    return { error: await describe(response) };
}

/**
 * Discord's own words where they help, and a plainer explanation where the
 * status code is the more useful part. 50007 is the common one and never means
 * the setup is wrong.
 */
async function describe(response) {
    let detail = '';
    let code = 0;
    try {
        const body = await response.json();
        code = body.code || 0;
        detail = body.message || '';
    } catch (e) {
        // Leaves the status code to speak for itself.
    }

    if (code === 50007) {
        return 'they have DMs from server members switched off, or the bot does not '
             + 'share a server with them';
    }
    if (response.status === 401) {
        return 'the bot token was rejected (401)';
    }
    if (response.status === 403) {
        return 'the bot is not allowed to post there (403) - check it can see the channel';
    }
    if (response.status === 404) {
        return 'that channel or user does not exist (404) - check the ids';
    }
    if (response.status === 429) {
        return 'Discord is rate limiting the bot (429)';
    }
    return `Discord returned ${response.status}${detail ? ` - ${detail}` : ''}`;
}
