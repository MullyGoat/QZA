package com.qza.party;

import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;

import java.util.regex.Pattern;

public final class PartyFinderQueue {
    private static final Pattern QUEUED = Pattern.compile(
            "^Party Finder > Your party has been queued in the dungeon finder!$");
    private static final Pattern ENDED = Pattern.compile(
            "^Party Finder > (Your group has been de-listed!"
                    + "|Your group has been removed from the party finder.*"
                    + "|Your dungeon group is full!.*)$"
                    + "|^You left the party\\.$"
                    + "|^The party was disbanded.*$"
                    + "|^You have been kicked from the party.*$");

    private static volatile boolean queued;

    private PartyFinderQueue() {
    }

    public static void onChatMessage(String raw) {
        if (raw == null) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (QUEUED.matcher(message).matches()) {
            queued = true;
        } else if (ENDED.matcher(message).matches()) {
            queued = false;
        }
    }

    public static boolean queued() {
        if (queued && DungeonState.inDungeon()) {
            queued = false;
        }
        return queued;
    }

    public static void reset() {
        queued = false;
    }
}
