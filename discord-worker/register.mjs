
import { createInterface } from 'node:readline/promises';

async function ask(question) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    try {
        return (await rl.question(question)).trim();
    } finally {
        rl.close();
    }
}

function fromEnv(name) {
    const raw = process.env[name];
    return raw === undefined ? null : raw.trim();
}

const APP_ID = fromEnv('DISCORD_APP_ID')
    || await ask('Application ID (General Information page): ');
const BOT_TOKEN = fromEnv('DISCORD_BOT_TOKEN')
    || await ask('Bot token (Bot tab, Reset Token): ');

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
                type: 3,
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
    {
        name: 'verifydiag',
        description: 'Check why verification is failing',
        default_member_permissions: '32',
        dm_permission: false,
    },
    {
        name: 'verifypanel',
        description: 'Post the verification button in this channel',
        default_member_permissions: '32',
        dm_permission: false,
        options: [
            {
                name: 'message',
                description: 'What the panel should say',
                type: 3,
                required: false,
                max_length: 400,
            },
        ],
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
