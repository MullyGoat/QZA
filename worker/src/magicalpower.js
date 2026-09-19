
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

const RARITY = /\b(VERY SPECIAL|SPECIAL|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\b/;

const FORMATTING = /§./g;

const HEGEMONY = 'HEGEMONY_ARTIFACT';

const RIFT_PRISM = 11;

export async function magicalPower(member) {
    const packed = bagData(member);
    if (!packed) {

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

    if (id.startsWith('ABICASE')) {
        return power + Math.floor(contacts / 2);
    }
    return id === HEGEMONY ? power * 2 : power;
}

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
