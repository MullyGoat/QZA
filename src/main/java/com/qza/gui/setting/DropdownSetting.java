package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A row whose control is a closed button that opens a scrollable list of
 * choices. Values are stored raw (e.g. a file name); {@link #display} turns one
 * into what the user sees.
 */
public class DropdownSetting extends Setting {

    private final Supplier<List<String>> options;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final Function<String, String> display;
    private final Runnable onOpen;

    /** Shown when there is nothing to choose from. */
    public final String emptyLabel;
    public final int width;

    public DropdownSetting(String category, String section, String title, Component description,
                           Supplier<List<String>> options,
                           Supplier<String> getter,
                           Consumer<String> setter,
                           Function<String, String> display,
                           Runnable onOpen,
                           String emptyLabel,
                           int width) {
        super(category, section, title, description);
        this.options = options;
        this.getter = getter;
        this.setter = setter;
        this.display = display;
        this.onOpen = onOpen;
        this.emptyLabel = emptyLabel;
        this.width = width;
    }

    public List<String> options() {
        List<String> list = options.get();
        return list == null ? List.of() : list;
    }

    public String value() {
        return getter.get();
    }

    public void select(String option) {
        setter.accept(option);
    }

    public String display(String raw) {
        return display == null ? raw : display.apply(raw);
    }

    /** Called just before the list opens, so it can be refreshed from disk. */
    public void notifyOpen() {
        if (onOpen != null) {
            onOpen.run();
        }
    }

    /** Label for the closed button. */
    public String currentLabel() {
        if (options().isEmpty()) {
            return emptyLabel;
        }
        String current = value();
        if (current == null || current.isBlank() || !options().contains(current)) {
            return "(none)";
        }
        return display(current);
    }
}
