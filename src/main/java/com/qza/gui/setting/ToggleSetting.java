package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ToggleSetting extends Setting {
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public ToggleSetting(String category, String section, String title, Component description,
                         BooleanSupplier getter, Consumer<Boolean> setter) {
        super(category, section, title, description);
        this.getter = getter;
        this.setter = setter;
    }

    public boolean value() {
        return getter.getAsBoolean();
    }

    public void toggle() {
        setter.accept(!getter.getAsBoolean());
    }
}
