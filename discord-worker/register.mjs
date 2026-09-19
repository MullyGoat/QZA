/**
 * Registers the two slash commands with Discord. Run once, and again only if
 * the commands below change.
 *
 *   node register.mjs
 *
 * It asks for what it needs. Answering the prompts keeps the bot token out of
 * your shell history, and out of any file where it could be committed by
 * accident.
 *
 * DISCORD_APP_ID, DISCORD_BOT_TOKEN and DISCORD_GUILD_ID are read from the
 * environment when they are set, for running this from a script. They are
 * trimmed, because the obvious way to set them on Windows
 *
 *   set DISCORD_BOT_TOKEN=abc && node register.mjs
 *
 * puts the space before the && inside the value, and a token with a trailing
 * space comes back from Discord as a bare 401 that looks like a wrong token.
 *
 * The server id registers the commands to one server, which takes effect
 * immediately. Leave it blank for global commands, which is what you want for
 * a bot in more than one server, but Discord can take up to an hour to show
 * them.
 */

import { createInterface } from 'node:readline/promises';

async function ask(question) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    try {
        return (await rl.question(question)).trim();
    } finally {
        rl.close();
    }
}

/** Null when the variable is not set at all, so an empty one still counts. */
function fromEnv(name) {
    const raw = process.env[name];
    return raw === undefined ? null : raw.trim();
}

const APP_ID = fromEnv('DISCORD_APP_ID')
    || await ask('Application ID (General Information page): ');
const BOT_TOKEN = fromEnv('DISCORD_BOT_TOKEN')
    || await ask('Bot token (Bot tab, Reset Token): ');
// ?? rather than ||, because an empty server id is an answer - register
// globally - and should not send it back round to the prompt.
const GUILD_ID = fromEnv('DISCORD_GUILD_ID')
    ?? await ask('Server ID, or blank to register globally: ');

if (!APP_ID || !BOT_TOKEN) {
    console.error('An application id and a bot token are both needed.');
    process.exit(1);
}
if (!/^\d+$/.test(APP_ID)) {
    console.error(`"${APP_ID}" is not an application id. That is the number on the`);
    console.error('General Information page, not the public key and not the token.');
    process.exit(1);
}
if (GUILD_ID && !/^\d+$/.test(GUILD_ID)) {
    console.error(`"${GUILD_ID}" is not a server id. Right click the server and`);
    console.error('"Copy Server ID" with Developer Mode switched on.');
    process.exit(1);
}

const COMMANDS = [
    {
        name: 'link',
        description: 'Link your Minecraft game to this Discord account for party alerts',
        options: [
            {
                name: 'code',
                description: 'The code from /qza discord link in game',
                type: 3, // STRING
                required: true,
                min_length: 4,
                max_length: 12,
            },
        ],
    },
    {
        name: 'unlink',
        description: 'Stop the alerts and delete everything stored about you',
    },
];

const path = GUILD_ID
    ? `/applications/${APP_ID}/guilds/${GUILD_ID}/commands`
    : `/applications/${APP_ID}/commands`;

const response = await fetch(`https://discord.com/api/v10${path}`, {
    method: 'PUT',
    headers: {
        'Authorization': `Bot ${BOT_TOKEN}`,
        'Content-Type': 'application/json',
    },
    body: JSON.stringify(COMMANDS),
});

const body = await response.text();

if (!response.ok) {
    console.error(`Discord returned ${response.status}`);
    console.error(body);
    if (response.status === 401) {
        console.error('\nThat means the bot token was not accepted. It is the one from the');
        console.error('Bot tab, not the public key and not the application id, and resetting');
        console.error('it on that page invalidates whatever was there before.');
    }
    if (response.status === 403) {
        console.error('\nThat usually means the bot is in the server without the');
        console.error('applications.commands scope. Re-invite it with that scope added.');
    }
    process.exit(1);
}

const registered = JSON.parse(body).map((c) => `/${c.name}`).join(', ');
console.log(`Registered ${registered} ${GUILD_ID ? `to guild ${GUILD_ID}` : 'globally'}.`);
if (!GUILD_ID) {
    console.log('Global commands can take up to an hour to appear in Discord.');
}
