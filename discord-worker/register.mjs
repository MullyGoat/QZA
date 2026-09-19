/**
 * Registers the two slash commands with Discord. Run once, and again only if
 * the commands below change.
 *
 * The bot token is read from the environment rather than a file, so it never
 * sits on disk where it could be committed by accident:
 *
 *   DISCORD_APP_ID=... DISCORD_BOT_TOKEN=... node register.mjs
 *
 * On Windows PowerShell:
 *
 *   $env:DISCORD_APP_ID="..."; $env:DISCORD_BOT_TOKEN="..."; node register.mjs
 *
 * Set DISCORD_GUILD_ID as well to register to one server, which takes effect
 * immediately. Without it the commands are global, which is what you want for
 * a bot in more than one server, but Discord can take up to an hour to show
 * them.
 */

const APP_ID = process.env.DISCORD_APP_ID;
const BOT_TOKEN = process.env.DISCORD_BOT_TOKEN;
const GUILD_ID = process.env.DISCORD_GUILD_ID;

if (!APP_ID || !BOT_TOKEN) {
    console.error('Set DISCORD_APP_ID and DISCORD_BOT_TOKEN in the environment first.');
    console.error('See the comment at the top of this file.');
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
    process.exit(1);
}

const registered = JSON.parse(body).map((c) => `/${c.name}`).join(', ');
console.log(`Registered ${registered} ${GUILD_ID ? `to guild ${GUILD_ID}` : 'globally'}.`);
if (!GUILD_ID) {
    console.log('Global commands can take up to an hour to appear in Discord.');
}
