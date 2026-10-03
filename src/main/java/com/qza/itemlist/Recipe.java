package com.qza.itemlist;

import java.util.ArrayList;
import java.util.List;

public sealed interface Recipe {

    Type type();

    List<Ingredient> inputs();

    List<Ingredient> outputs();

    enum Type {
        CRAFTING("Crafting", "minecraft:crafting_table"),
        FORGE("Forge", "minecraft:anvil"),
        TRADE("Trade", "minecraft:emerald"),
        DROPS("Mob Drops", "minecraft:diamond_sword"),
        NPC_SHOP("NPC Shop", "minecraft:gold_nugget"),
        KAT("Kat Upgrade", "minecraft:bone");

        public final String label;
        public final String icon;

        Type(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    record Crafting(List<Ingredient> grid, Ingredient output, String text) implements Recipe {
        public Type type() {
            return Type.CRAFTING;
        }

        public List<Ingredient> inputs() {
            return present(grid);
        }

        public List<Ingredient> outputs() {
            return List.of(output);
        }
    }

    record Forge(List<Ingredient> inputs, Ingredient output, long seconds) implements Recipe {
        public Type type() {
            return Type.FORGE;
        }

        public List<Ingredient> outputs() {
            return List.of(output);
        }
    }

    record Shop(String npc, List<Ingredient> cost, Ingredient output) implements Recipe {
        public Type type() {
            return Type.NPC_SHOP;
        }

        public List<Ingredient> inputs() {
            return cost;
        }

        public List<Ingredient> outputs() {
            return List.of(output);
        }
    }

    record Trade(Ingredient cost, Ingredient output) implements Recipe {
        public Type type() {
            return Type.TRADE;
        }

        public List<Ingredient> inputs() {
            return List.of(cost);
        }

        public List<Ingredient> outputs() {
            return List.of(output);
        }
    }

    record Drops(String mob, String name, int level, long coins, long xp,
                 List<Drop> drops, List<String> extra) implements Recipe {
        public Type type() {
            return Type.DROPS;
        }

        public List<Ingredient> inputs() {
            return List.of();
        }

        public List<Ingredient> outputs() {
            List<Ingredient> out = new ArrayList<>(drops.size());
            for (Drop drop : drops) {
                out.add(drop.item());
            }
            return out;
        }
    }

    record Drop(Ingredient item, String chance, List<String> extra) {
    }

    record Kat(Ingredient input, Ingredient output, List<Ingredient> items, long coins, long seconds)
            implements Recipe {
        public Type type() {
            return Type.KAT;
        }

        public List<Ingredient> inputs() {
            List<Ingredient> in = new ArrayList<>(items.size() + 1);
            in.add(input);
            in.addAll(items);
            return in;
        }

        public List<Ingredient> outputs() {
            return List.of(output);
        }
    }

    private static List<Ingredient> present(List<Ingredient> slots) {
        List<Ingredient> out = new ArrayList<>(slots.size());
        for (Ingredient slot : slots) {
            if (slot != null) {
                out.add(slot);
            }
        }
        return out;
    }
}
