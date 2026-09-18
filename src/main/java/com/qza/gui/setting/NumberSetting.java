package com.qza.gui.setting;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One or more small boxes you type whole numbers into.
 *
 * A box whose value is over its maximum turns red and nothing is written, so
 * the config never takes a value the feature cannot use. Two boxes with a
 * separator make a minutes and seconds pair.
 */
public class NumberSetting extends Setting {
    public static final int BOX_W = 28;
    public static final int BOX_H = 16;

    /** A single box: the unit shown beside it, and the largest value it accepts. */
    public record Field(String unit, int max, int digits) {
    }

    public final List<Field> fields;
    public final String separator;

    private final Supplier<int[]> read;
    private final Consumer<int[]> write;

    public NumberSetting(String category, String section, String title, Component description,
                         List<Field> fields, String separator,
                         Supplier<int[]> read, Consumer<int[]> write) {
        super(category, section, title, description);
        this.fields = List.copyOf(fields);
        this.separator = separator == null ? "" : separator;
        this.read = read;
        this.write = write;
    }

    public int[] values() {
        int[] current = read.get();
        int[] safe = new int[fields.size()];
        for (int i = 0; i < safe.length; i++) {
            safe[i] = i < current.length ? Math.max(0, current[i]) : 0;
        }
        return safe;
    }

    public Field field(int index) {
        return fields.get(index);
    }

    /** Empty reads as zero, anything unparseable or over the max as invalid. */
    public static int parse(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean validFor(int index, String text) {
        int value = parse(text);
        return value >= 0 && value <= field(index).max();
    }

    /** Writes only when every box is inside its range. */
    public boolean commit(int index, String text) {
        if (!validFor(index, text)) {
            return false;
        }
        int[] next = values();
        next[index] = parse(text);
        for (int i = 0; i < next.length; i++) {
            if (next[i] > field(i).max()) {
                return false;
            }
        }
        write.accept(next);
        return true;
    }
}
