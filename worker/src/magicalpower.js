/**
 * Current magical power, worked out from what is actually in the accessory bag.
 *
 * Hypixel stores only accessory_bag_storage.highest_magical_power, which is the
 * best somebody has ever had. Sell a mythic accessory and that number stays put,
 * so it is no use for judging who is in front of you. The bag itself is the only
 * honest source, which means unpacking it and adding the accessories up the way
 * the game does.
 *
 * Null when the bag cannot be read at all, which is what an inventory API left
 * switched off looks like. The mod says "API Off" for that rather than printing
 * a total that is really just a guess.
 */

import { FAMILY } from './accessories.js';
import { decodeNbt } from './nbt.js';

const MAGICAL_POWER = {
    COMMON: 3,
    UNCOMMON: 5,
    RARE: 8,
    EPIC: 12,
    LEGENDARY: 16,
    MYTHIC: 22,
    SPECIAL: 3,
    'VERY SPECIAL': 5,
};

/**
 * The rarity named on an accessory's tooltip. "VERY SPECIAL" has to come first
 * or it reads as "SPECIAL". Deliberately not anchored: see rarityOf.
 */
const RARITY = /\b(VERY SPECIAL|SPECIAL|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\b/;

const FORMATTING = /§./g;

/** Counts twice over, which is the whole point of it. */
const HEGEMONY = 'HEGEMONY_ARTIFACT';

/** Not an accessory and not in the bag, so it is added on separately. */
const RIFT_PRISM = 11;

export async function magicalPower(member) {
    const packed = bagData(member);
    if (!packed) {
        // Hypixel leaves the whole inventory out when that API is switched
        // off. An inventory with no accessory bag in it is a real answer: the
        // bag is empty.
        return member && member.inventory ? 0 : null;
    }

    let bag;
    try {
        bag = await decodeNbt(packed);
    } catch (e) {
        return null;
    }

    const items = bag && Array.isArray(bag.i) ? bag.i : null;
    if (!items) {
        return null;
    }

    const contacts = contactCount(member);

    // Keyed by family so a Speed Talisman kept next to the Speed Artifact does
    // not pay twice, and nor does a second copy of the same accessory.
    const best = new Map();

    for (const item of items) {
        const id = itemId(item);
        if (!id) {
            continue;
        }

        const power = itemPower(id, item, contacts);
        if (power === null) {
            continue;
        }

        const family = FAMILY.get(id) || id;
        const seen = best.get(family);
        if (seen === undefined || seen < power) {
            best.set(family, power);
        }
    }

    let total = 0;
    for (const power of best.values()) {
        total += power;
    }
    if (hasRiftPrism(member)) {
        total += RIFT_PRISM;
    }
    return total;
}

/**
 * The bag as Hypixel stores it. Newer profiles keep it under inventory, older
 * ones at the top level, and a profile with the inventory API switched off has
 * neither.
 */
function bagData(member) {
    const inventory = member && member.inventory ? member.inventory : {};
    const bags = inventory.bag_contents || {};
    const bag = bags.talisman_bag || (member ? member.talisman_bag : null);
    return bag && typeof bag.data === 'string' && bag.data ? bag.data : null;
}

function itemPower(id, item, contacts) {
    const rarity = rarityOf(item);
    const power = rarity === null ? undefined : MAGICAL_POWER[rarity];
    if (power === undefined) {
        return null;
    }

    // The phone pays its rarity and then one more for every two contacts, on
    // top rather than instead.
    if (id.startsWith('ABICASE')) {
        return power + Math.floor(contacts / 2);
    }
    return id === HEGEMONY ? power * 2 : power;
}

/**
 * Read off the tooltip rather than looked up, because the tooltip is what a
 * recombobulator changes.
 *
 * Found by the word ACCESSORY rather than by where it sits, and matched
 * anywhere on that line rather than at the start of it. Both matter: soulbound
 * accessories print a line underneath the rarity, and Hypixel wraps a shiny
 * one in obfuscated text, which leaves a stray letter at each end once the
 * formatting codes come off - "a MYTHIC ACCESSORY a". Anchoring to the start
 * quietly skipped every shiny accessory in the bag.
 *
 * A rarity on a line that does not say ACCESSORY is kept only as a fallback,
 * in case Hypixel ever words one differently.
 */
function rarityOf(item) {
    const display = item && item.tag ? item.tag.display : null;
    const lore = display ? display.Lore : null;
    if (!Array.isArray(lore)) {
        return null;
    }

    let fallback = null;
    for (let i = lore.length - 1; i >= 0; i--) {
        const line = lore[i];
        if (typeof line !== 'string') {
            continue;
        }

        const text = line.replace(FORMATTING, '').trim();
        const match = RARITY.exec(text);
        if (!match) {
            continue;
        }
        // Covers ACCESSORY, HATCESSORY and "DUNGEON ACCESSORY".
        if (text.includes('CESSORY')) {
            return match[1];
        }
        if (fallback === null) {
            fallback = match[1];
        }
    }
    return fallback;
}

function itemId(item) {
    const extra = item && item.tag ? item.tag.ExtraAttributes : null;
    return extra && typeof extra.id === 'string' ? extra.id : null;
}

function contactCount(member) {
    const nether = member.nether_island_player_data || {};
    const phone = nether.abiphone || {};
    return Array.isArray(phone.active_contacts) ? phone.active_contacts.length : 0;
}

function hasRiftPrism(member) {
    const rift = member.rift || {};
    const access = rift.access || {};
    return Boolean(access.consumed_prism);
}
