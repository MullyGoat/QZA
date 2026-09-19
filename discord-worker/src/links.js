/**
 * The link between a copy of the mod and a Discord account, and everything
 * stored about it.
 *
 * What is kept, in full:
 *
 *   token:<sha256 of the token>  ->  { discordId, linkedAt }
 *   discord:<discord user id>    ->  { tokens: [hash, ...] }
 *   code:<CODE>                  ->  { tokenHash, ign, replaces }   for 10 minutes
 *
 * That is the whole of it. No Minecraft uuid, no party membership, no chat, no
 * addresses, nothing about what anybody was doing when the alert fired. A
 * Discord id has to be stored because it is the thing being messaged; the in
 * game name is held only for the ten minutes a code is alive, so the person
 * confirming the link can see which account they are about to attach, and is
 * gone with the code.
 *
 * Tokens are stored as SHA-256 hashes rather than as themselves. The key is the
 * hash, so a lookup is still one read, but a dump of this namespace hands over
 * no working credentials.
 */

/** No O/0 or I/1, since these get read off a screen and typed by hand. */
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 6;

export const CODE_TTL_SECONDS = 600;

/** 32 random bytes. Generated here so the mod never invents its own. */
export function newToken() {
    return hex(crypto.getRandomValues(new Uint8Array(32)));
}

export function newCode() {
    const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
    let code = '';
    for (const byte of bytes) {
        code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
    }
    return code;
}

export async function hashToken(token) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
    return hex(new Uint8Array(digest));
}

function hex(bytes) {
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Codes are typed by people, so case and stray spaces should not matter. */
export function tidyCode(raw) {
    return String(raw || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
}

export async function putCode(kv, code, record) {
    await kv.put(`code:${code}`, JSON.stringify(record), {
        expirationTtl: CODE_TTL_SECONDS,
    });
}

/** Read and burn: a code works once, whether or not the link then succeeds. */
export async function takeCode(kv, code) {
    const key = `code:${code}`;
    const raw = await kv.get(key);
    if (!raw) {
        return null;
    }
    await kv.delete(key);
    try {
        return JSON.parse(raw);
    } catch (e) {
        return null;
    }
}

export async function bindingFor(kv, tokenHash) {
    const raw = await kv.get(`token:${tokenHash}`);
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch (e) {
        return null;
    }
}

/**
 * Attaches a token to a Discord account, dropping whatever the same copy of the
 * mod was using before so relinking does not leave the old one working.
 */
export async function bind(kv, tokenHash, discordId, replaces) {
    if (replaces && replaces !== tokenHash) {
        await unbind(kv, replaces);
    }

    await kv.put(`token:${tokenHash}`, JSON.stringify({
        discordId: discordId,
        linkedAt: new Date().toISOString(),
    }));

    const owned = await tokensOf(kv, discordId);
    if (!owned.includes(tokenHash)) {
        owned.push(tokenHash);
        await kv.put(`discord:${discordId}`, JSON.stringify({ tokens: owned }));
    }
}

/** Forgets one token, and the account's pointer to it. */
export async function unbind(kv, tokenHash) {
    const binding = await bindingFor(kv, tokenHash);
    await kv.delete(`token:${tokenHash}`);
    if (!binding) {
        return false;
    }

    const owned = (await tokensOf(kv, binding.discordId)).filter((t) => t !== tokenHash);
    if (owned.length === 0) {
        await kv.delete(`discord:${binding.discordId}`);
    } else {
        await kv.put(`discord:${binding.discordId}`, JSON.stringify({ tokens: owned }));
    }
    return true;
}

/**
 * Forgets everything about a Discord account. This is what /unlink runs, so
 * somebody who wants their data gone gets all of it gone in one go, including
 * links made from a copy of the mod they no longer have.
 */
export async function forget(kv, discordId) {
    const owned = await tokensOf(kv, discordId);
    for (const tokenHash of owned) {
        await kv.delete(`token:${tokenHash}`);
    }
    await kv.delete(`discord:${discordId}`);
    return owned.length;
}

async function tokensOf(kv, discordId) {
    const raw = await kv.get(`discord:${discordId}`);
    if (!raw) {
        return [];
    }
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed.tokens) ? parsed.tokens : [];
    } catch (e) {
        return [];
    }
}
