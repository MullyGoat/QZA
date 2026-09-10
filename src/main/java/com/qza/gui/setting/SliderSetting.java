package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public class SliderSetting extends Setting {
    private final DoubleSupplier getter;
    private final Consumer<Double> setter;
    public final double min;
    public final double max;
    public final double step;
    public final String suffix;

    public SliderSetting(String category, String section, String title, Component description,
                         double min, double max, double step, String suffix,
                         DoubleSupplier getter, Consumer<Double> setter) {
        super(category, section, title, description);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.getter = getter;
        this.setter = setter;
    }

    public double value() {
        return Math.max(min, Math.min(max, getter.getAsDouble()));
    }

    public void setFromFraction(double fraction) {
        double raw = min + (Math.max(0, Math.min(1, fraction)) * (max - min));
        double snapped = min + (Math.round((raw - min) / step) * step);
        setter.accept(Math.max(min, Math.min(max, snapped)));
    }

    public double fraction() {
        return max == min ? 0 : (value() - min) / (max - min);
    }

    public String label() {
        return labelFor(value());
    }

    public String labelFor(double v) {
        String number = (step >= 1 && v == Math.rint(v))
                ? String.valueOf((long) v)
                : String.format("%.2f", v);
        return number + suffix;
    }
}
