package com.qza.itemlist;

import com.qza.util.IgnUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ItemSearch {
    private static final String[] NO_WORDS = new String[0];

    private ItemSearch() {
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String stripped = IgnUtil.stripCodes(text);
        StringBuilder out = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '#' || c == ' ') {
                out.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                out.append((char) (c + 32));
            }
        }
        return out.toString().trim();
    }

    static String[] words(String text) {
        String cleaned = clean(text);
        if (cleaned.isEmpty()) {
            return NO_WORDS;
        }
        List<String> out = new ArrayList<>();
        for (String word : cleaned.split(" +")) {
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out.toArray(NO_WORDS);
    }

    static List<RepoItem> filter(List<RepoItem> items, String query) {
        String text = query == null ? "" : query.trim();
        List<RepoItem> out = new ArrayList<>();
        if (text.isEmpty()) {
            for (RepoItem item : items) {
                if (!item.mob) {
                    out.add(item);
                }
            }
            return out;
        }

        List<Part> parts = parse(text);
        for (RepoItem item : items) {
            if (!item.mob && matches(item, parts)) {
                out.add(item);
            }
        }
        return out;
    }

    private static boolean matches(RepoItem item, List<Part> parts) {
        boolean result = false;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            boolean hit = part.matches(item);
            if (i == 0) {
                result = hit;
            } else if (part.and) {
                result = result && hit;
            } else {
                result = result || hit;
            }
        }
        return result;
    }

    private static List<Part> parse(String text) {
        List<Part> parts = new ArrayList<>();
        boolean and = false;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '|';
            if (c == '|' || c == '&') {
                Part part = Part.of(text.substring(start, i), and);
                if (part != null) {
                    parts.add(part);
                }
                and = c == '&';
                start = i + 1;
            }
        }
        return parts;
    }

    private static boolean sequence(String[] words, String[] query) {
        if (query.length == 0) {
            return true;
        }
        for (int i = 0; i + query.length <= words.length; i++) {
            boolean all = true;
            for (int j = 0; j < query.length; j++) {
                if (!words[i + j].startsWith(query[j])) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private record Part(boolean and, boolean negate, String mode, String raw, String[] words, String[] letters) {

        static Part of(String piece, boolean and) {
            String text = piece.trim();
            boolean negate = text.startsWith("!");
            if (negate) {
                text = text.substring(1).trim();
            }
            String mode = "";
            String lower = text.toLowerCase(Locale.ROOT);
            for (String prefix : new String[]{"id:", "title:", "desc:"}) {
                if (lower.startsWith(prefix)) {
                    mode = prefix;
                    text = text.substring(prefix.length()).trim();
                    break;
                }
            }
            if (text.isEmpty() && mode.isEmpty() && !negate) {
                return null;
            }
            String[] words = ItemSearch.words(text);
            String[] letters = NO_WORDS;
            if (mode.isEmpty() && words.length == 1 && words[0].length() > 1) {
                letters = new String[words[0].length()];
                for (int i = 0; i < letters.length; i++) {
                    letters[i] = String.valueOf(words[0].charAt(i));
                }
            }
            return new Part(and, negate, mode, text.toUpperCase(Locale.ROOT), words, letters);
        }

        boolean matches(RepoItem item) {
            return negate != test(item);
        }

        private boolean test(RepoItem item) {
            return switch (mode) {
                case "id:" -> item.id.startsWith(raw);
                case "title:" -> sequence(item.titleWords, words);
                case "desc:" -> sequence(item.loreWords, words);
                default -> sequence(item.titleWords, words) || sequence(item.loreWords, words)
                        || (letters.length > 0 && sequence(item.titleWords, letters));
            };
        }
    }
}
