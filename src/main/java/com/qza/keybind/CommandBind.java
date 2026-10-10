package com.qza.keybind;

public class CommandBind {
    public static final String UNBOUND = "key.keyboard.unknown";

    public String name = "New Keybind";

    public String key = UNBOUND;

    public String command = "";

    public boolean enabled = true;

    public boolean inMenus = false;

    public String modifier = CommandKeybinds.MOD_ANY;

    public String islands = "";

    public boolean bound() {
        return key != null && !key.isBlank() && !UNBOUND.equals(key);
    }
}
