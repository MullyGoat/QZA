package com.qza.chat;

import com.qza.util.IgnUtil;

import java.util.regex.Pattern;

public final class ChannelParser {
    public static final String EVERYTHING = "everything";
    public static final String ALL = "all";
    public static final String PARTY = "party";
    public static final String GUILD = "guild";
    public static final String COOP = "coop";

    private static final int MAX_LENGTH = 512;

    private static final String PARTY_FINDER = "Party Finder > ";

    private static final String RANK = "(?:\\[[^\\]]*] ?)?";

    private static final String NAME = "\\w{1,16}";

    private static final Pattern[] PARTY_EVENTS = {
            Pattern.compile(RANK + NAME + " joined the party\\."),
            Pattern.compile(RANK + NAME + " has left the party\\."),
            Pattern.compile(RANK + NAME + " has been removed from the party\\."),
            Pattern.compile(RANK + NAME
                    + " was removed from your party because they disconnected\\."),
            Pattern.compile(RANK + NAME + " has disbanded the party!"),
            Pattern.compile("The party leader " + RANK + NAME + " has rejoined\\."),
            Pattern.compile("The party leader, " + RANK + NAME + " has disconnected, they have "
                    + "5 minutes to rejoin before the party is disbanded\\."),
            Pattern.compile("You have joined " + RANK + NAME + "'s? party!"),
            Pattern.compile("You have been kicked from the party by " + RANK + NAME),
            Pattern.compile("You left the party\\."),
            Pattern.compile("The party was disbanded because all invites expired "
                    + "and the party was empty\\."),
            Pattern.compile("The party was disbanded because the party leader disconnected\\."),
    };

    private ChannelParser() {
    }

    public record Line(String channel, String speaker) {
    }

    public static Line parse(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH) {
            return null;
        }

        String message = IgnUtil.stripCodes(raw).trim();
        if (message.isEmpty()) {
            return null;
        }

        if (message.startsWith(PARTY_FINDER)) {
            return new Line(PARTY, null);
        }

        Line line = tagged(message, "Party > ", PARTY);
        if (line != null) {
            return line;
        }
        line = tagged(message, "Guild > ", GUILD);
        if (line != null) {
            return line;
        }
        line = tagged(message, "Co-op > ", COOP);
        if (line != null) {
            return line;
        }
        line = tagged(message, "Coop > ", COOP);
        if (line != null) {
            return line;
        }

        if (message.startsWith("From ") || message.startsWith("To ")) {
            return null;
        }

        String speaker = speakerOf(message);
        return speaker == null ? null : new Line(ALL, speaker);
    }

    public static boolean isPartyEvent(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH) {
            return false;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (message.isEmpty()) {
            return false;
        }
        for (Pattern pattern : PARTY_EVENTS) {
            if (pattern.matcher(message).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Line tagged(String message, String prefix, String channel) {
        if (!message.startsWith(prefix)) {
            return null;
        }
        String body = message.substring(prefix.length()).trim();
        return body.isEmpty() ? null : new Line(channel, speakerOf(body));
    }

    public static String speakerAnywhere(String message) {
        if (message == null) {
            return null;
        }
        String text = IgnUtil.stripCodes(message).trim();
        for (String prefix : new String[]{"Party > ", "Guild > ", "Co-op > ", "Coop > ", "From ", "To "}) {
            if (text.startsWith(prefix)) {
                text = text.substring(prefix.length());
                break;
            }
        }
        return speakerOf(text);
    }

    public static String speakerOf(String body) {
        int colon = body.indexOf(": ");
        return colon <= 0 ? null : speakerFrom(body.substring(0, colon));
    }

    public static String speakerFrom(String who) {
        String text = who.trim();

        while (text.endsWith("]")) {
            int open = text.lastIndexOf('[');
            if (open < 0) {
                return null;
            }
            text = text.substring(0, open).trim();
        }

        String name = IgnUtil.trailingName(text);
        if (name == null) {
            return null;
        }

        String lead = text.substring(0, IgnUtil.nameStart(text, name)).trim();
        if (lead.isEmpty()) {
            return name;
        }
        return lead.startsWith("[") && lead.endsWith("]") ? name : null;
    }
}
