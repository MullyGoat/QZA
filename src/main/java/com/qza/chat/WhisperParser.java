package com.qza.chat;

import com.qza.util.IgnUtil;

import java.util.Locale;
import java.util.Set;

public final class WhisperParser {
    private static final String FROM = "From ";
    private static final String TO = "To ";
    private static final int MAX_LENGTH = 512;

    private static final Set<String> RESERVED_NAMES = Set.of("stash");

    private WhisperParser() {
    }

    public record Whisper(String ign, boolean outgoing, String text) {
    }

    public static Whisper parse(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH) {
            return null;
        }

        String message = IgnUtil.stripCodes(raw);
        boolean outgoing;
        int start;

        if (message.startsWith(FROM)) {
            outgoing = false;
            start = FROM.length();
        } else if (message.startsWith(TO)) {
            outgoing = true;
            start = TO.length();
        } else {
            return null;
        }

        int colon = message.indexOf(':', start);
        if (colon < 0) {
            return null;
        }

        String who = message.substring(start, colon);
        if (who.indexOf(':') >= 0) {
            return null;
        }

        String ign = IgnUtil.trailingName(who);
        if (ign == null || !isRankOnly(who.substring(0, IgnUtil.nameStart(who, ign)))) {
            return null;
        }
        if (RESERVED_NAMES.contains(ign.toLowerCase(Locale.ROOT))) {
            return null;
        }

        String text = message.substring(colon + 1).trim();
        if (text.isEmpty()) {
            return null;
        }

        return new Whisper(ign, outgoing, text);
    }

    private static boolean isRankOnly(String prefix) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }
}
