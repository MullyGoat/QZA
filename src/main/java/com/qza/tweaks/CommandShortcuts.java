package com.qza.tweaks;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;

import java.util.List;
import java.util.Locale;

public final class CommandShortcuts {
    private CommandShortcuts() {
    }

    public static List<CommandShortcut> shortcuts() {
        List<CommandShortcut> shortcuts = ConfigManager.get().commandShortcuts;
        shortcuts.removeIf(shortcut -> shortcut == null);
        return shortcuts;
    }

    public static String rewrite(String command) {
        QZAConfig cfg = ConfigManager.get();
        if (command == null || !cfg.commandShortcutsEnabled || cfg.commandShortcuts == null) {
            return command;
        }
        String typed = command.trim();
        String lower = typed.toLowerCase(Locale.ROOT);
        String best = null;
        String target = null;
        for (CommandShortcut shortcut : cfg.commandShortcuts) {
            if (shortcut == null || !shortcut.enabled) {
                continue;
            }
            String key = strip(shortcut.shortcut).toLowerCase(Locale.ROOT);
            String replacement = strip(shortcut.command);
            if (key.isEmpty() || replacement.isEmpty() || (best != null && key.length() <= best.length())) {
                continue;
            }
            if (lower.equals(key) || lower.startsWith(key + " ")) {
                best = key;
                target = replacement;
            }
        }
        return best == null ? command : target + typed.substring(best.length());
    }

    public static String strip(String value) {
        String out = value == null ? "" : value.trim();
        while (out.startsWith("/")) {
            out = out.substring(1).trim();
        }
        return out;
    }
}
