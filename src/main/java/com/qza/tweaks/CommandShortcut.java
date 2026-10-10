package com.qza.tweaks;

public class CommandShortcut {
    public String shortcut = "";

    public String command = "";

    public boolean enabled = true;

    public CommandShortcut() {
    }

    public CommandShortcut(String shortcut, String command) {
        this.shortcut = shortcut;
        this.command = command;
    }
}
