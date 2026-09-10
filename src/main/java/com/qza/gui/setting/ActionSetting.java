package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class ActionSetting extends Setting {

    private final Supplier<String> label;
    private final Runnable action;
    /** 0 = use the screen's default button width. Widen it for long labels. */
    public final int buttonWidth;

    public ActionSetting(String category, String section, String title, Component description,
                         Supplier<String> label, Runnable action) {
        this(category, section, title, description, label, action, 0);
    }

    public ActionSetting(String category, String section, String title, Component description,
                         Supplier<String> label, Runnable action, int buttonWidth) {
        super(category, section, title, description);
        this.label = label;
        this.action = action;
        this.buttonWidth = buttonWidth;
    }

    public ActionSetting(String category, String section, String title, Component description,
                         String label, Runnable action) {
        this(category, section, title, description, () -> label, action, 0);
    }

    public String buttonLabel() {
        return label.get();
    }

    public void run() {
        action.run();
    }
}
