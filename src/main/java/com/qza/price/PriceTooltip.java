package com.qza.price;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.itemlist.ItemRepo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PriceTooltip {
    public static final String AVERAGE_OFF = "off";
    public static final String AVERAGE_ONE = "one_day";
    public static final String AVERAGE_THREE = "three_day";
    public static final String AVERAGE_BOTH = "both";
    public static final List<String> AVERAGES = List.of(AVERAGE_OFF, AVERAGE_ONE, AVERAGE_THREE, AVERAGE_BOTH);

    private static final int CACHE_SIZE = 64;
    private static final Map<ItemStack, Cached> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ItemStack, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private record Cached(int generation, ItemRepo.Constants constants, int count, String settings,
                          List<Component> lines) {
    }

    private PriceTooltip() {
    }

    public static String averageLabel(String mode) {
        return switch (mode) {
            case AVERAGE_ONE -> "1 Day";
            case AVERAGE_THREE -> "3 Day";
            case AVERAGE_BOTH -> "Both";
            default -> "Off";
        };
    }

    public static void append(ItemStack stack, List<Component> lines) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.itemPricesEnabled || stack == null || stack.isEmpty()) {
            return;
        }
        String key = PriceKey.of(stack);
        if (key == null) {
            return;
        }
        String average = cfg.itemPriceAverage == null ? AVERAGE_THREE : cfg.itemPriceAverage;
        boolean oneDay = average.equals(AVERAGE_ONE) || average.equals(AVERAGE_BOTH);
        boolean threeDay = average.equals(AVERAGE_THREE) || average.equals(AVERAGE_BOTH) || cfg.itemPriceValue;
        PriceData.ensure(oneDay, threeDay, cfg.itemPriceValue);
        if (cfg.itemPriceValue) {
            ItemRepo.ensureLoaded();
        }

        int count = Math.max(1, stack.getCount());
        String settings = cfg.itemPriceBazaar + "," + cfg.itemPriceLowestBin + "," + average + "," + cfg.itemPriceValue;
        synchronized (CACHE) {
            Cached cached = CACHE.get(stack);
            ItemRepo.Constants constants = ItemRepo.constants();
            if (cached == null || cached.generation() != PriceData.generation() || cached.constants() != constants
                    || cached.count() != count || !cached.settings().equals(settings)) {
                cached = new Cached(PriceData.generation(), constants, count, settings,
                        build(stack, key, count, cfg, average));
                CACHE.put(stack, cached);
            }
            lines.addAll(cached.lines());
        }
    }

    private static List<Component> build(ItemStack stack, String key, int count, QZAConfig cfg, String average) {
        List<Component> out = new ArrayList<>();
        PriceData.Bazaar bazaar = PriceData.bazaar(key);
        if (bazaar != null) {
            if (cfg.itemPriceBazaar) {
                out.add(line("Bazaar Buy", bazaar.buy(), count));
                out.add(line("Bazaar Sell", bazaar.sell(), count));
            }
            return out;
        }
        if (cfg.itemPriceLowestBin) {
            Double lowest = PriceData.lowestBin(key);
            if (lowest != null) {
                out.add(line("Lowest BIN" + petBracket(key), lowest, count));
            }
        }
        if (average.equals(AVERAGE_ONE) || average.equals(AVERAGE_BOTH)) {
            Double day = PriceData.average(key, 1);
            if (day != null) {
                out.add(line("1 Day Avg BIN", day, count));
            }
        }
        if (average.equals(AVERAGE_THREE) || average.equals(AVERAGE_BOTH)) {
            Double days = PriceData.average(key, 3);
            if (days != null) {
                out.add(line("3 Day Avg BIN", days, count));
            }
        }
        if (cfg.itemPriceValue && PriceData.bazaarLoaded()) {
            double value = ItemValue.estimate(stack);
            if (value > 0) {
                out.add(line("Est. Item Value", value / count, count));
            }
        }
        return out;
    }

    private static String petBracket(String key) {
        if (!key.startsWith("LVL_")) {
            return "";
        }
        int end = key.indexOf('_', "LVL_".length());
        return end < 0 ? "" : " (Lvl " + key.substring("LVL_".length(), end) + ")";
    }

    private static Component line(String label, double each, int count) {
        MutableComponent line = Component.literal(label + ": ").withStyle(ChatFormatting.LIGHT_PURPLE);
        if (each <= 0) {
            return line.append(Component.literal("No data").withStyle(ChatFormatting.RED));
        }
        line.append(Component.literal(coins(each * count) + " coins").withStyle(ChatFormatting.DARK_AQUA));
        if (count > 1) {
            line.append(Component.literal(" (" + coins(each) + " each)").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    static String coins(double value) {
        if (value >= 1000) {
            return String.format(Locale.ENGLISH, "%,.0f", value);
        }
        String text = String.format(Locale.ENGLISH, "%,.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
