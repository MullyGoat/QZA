package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public abstract class Setting {
    public final String category;
    public final String section;
    public final String title;
    public final Component description;

    private final String searchIndex;
    private BooleanSupplier visible = () -> true;

    protected Setting(String category, String section, String title, Component description) {
        this.category = category;
        this.section = section;
        this.title = title;
        this.description = description;
        this.searchIndex = (category + " " + section + " " + title + " " + description.getString())
                .toLowerCase(Locale.ROOT);
    }

    public boolean matchesSearch(String query) {
        return searchIndex.contains(query.toLowerCase(Locale.ROOT));
    }

    public Setting visibleWhen(BooleanSupplier condition) {
        this.visible = condition;
        return this;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }
}
