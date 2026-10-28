package com.example.ecomod;

import com.example.ecomod.core.Money;
import com.example.ecomod.core.PriceRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Central price registry. Shop, sell screen and every GUI read from here, so prices can never disagree.
 * Items are discovered from the live item registry at startup; anything without a rule gets a
 * rarity-based fallback price, so items added by future game updates are never missing.
 */
public final class Prices {
    public enum Category {
        ALL("All items"), BLOCKS("Blocks"), BUILDING("Building"), ORES("Ores & minerals"), TOOLS("Tools"),
        WEAPONS("Weapons"), ARMOR("Armor"), FOOD("Food"), FARMING("Farming"), POTIONS("Brewing"),
        REDSTONE("Redstone"), TRANSPORT("Transportation"), RARE("Rare items");

        public final String label;

        Category(String label) { this.label = label; }
    }

    public enum Tier {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY;

        static Tier of(long price) {
            if (price < 100) return COMMON;
            if (price < 5_000) return UNCOMMON;
            if (price < 100_000) return RARE;
            if (price < 5_000_000) return EPIC;
            return LEGENDARY;
        }
    }

    public record Entry(Identifier id, Item item, String path, long buy, long sell, Category category, Tier tier) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<Item, Entry> BY_ITEM = new HashMap<>();

    private Prices() {}

    public static List<Entry> all() { return ENTRIES; }

    public static Entry get(Item item) { return BY_ITEM.get(item); }

    public static void init(MinecraftServer server) {
        ENTRIES.clear();
        BY_ITEM.clear();
        Map<String, Long> saved = Config.loadPrices();
        Map<String, Long> out = new TreeMap<>();
        int sellPct = Config.get().sellPercent;
        var features = server.overworld().enabledFeatures();

        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            boolean vanilla = id.getNamespace().equals("minecraft");
            if (item == Items.AIR || (!vanilla && !Config.get().includeModdedItems)) continue;
            if (vanilla && PriceRules.excluded(path)) continue;
            if (!item.isEnabled(features)) continue;

            long price;
            Long override = saved.get(id.toString());
            if (override != null) {
                price = Money.clampPrice(override);
            } else {
                long rule = vanilla ? PriceRules.price(path) : -1;
                price = rule > 0 ? rule : fallback(item);
                // Dirt is the one and only $1 item; everything else starts at $2.
                price = path.equals("dirt") && vanilla ? PriceRules.DIRT : Math.max(2, price);
                price = Money.clampPrice(price);
            }
            if (vanilla && path.equals("elytra") && override == null) price = PriceRules.ELYTRA;

            Entry e = new Entry(id, item, path, price, Money.sellPrice(price, sellPct), categorize(item, path, price), Tier.of(price));
            ENTRIES.add(e);
            BY_ITEM.put(item, e);
            out.put(id.toString(), price);
        }
        ENTRIES.sort((a, b) -> a.path().compareTo(b.path()));
        Config.savePrices(out);
        EconomyMod.LOGGER.info("Price registry ready: {} items", ENTRIES.size());
    }

    private static long fallback(Item item) {
        try {
            return switch (item.getDefaultInstance().getRarity().ordinal()) {
                case 0 -> 40;
                case 1 -> 800;
                case 2 -> 15_000;
                default -> 150_000;
            };
        } catch (RuntimeException e) {
            return 40;
        }
    }

    private static boolean has(String path, String... keys) {
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    private static Category categorize(Item item, String p, long price) {
        if (price >= 100_000) return Category.RARE;
        if (p.endsWith("_pickaxe") || p.endsWith("_axe") || p.endsWith("_shovel") || p.endsWith("_hoe")
                || has(p, "shears", "fishing_rod", "flint_and_steel", "bucket", "compass", "clock", "brush", "spyglass"))
            return Category.TOOLS;
        if (p.endsWith("_sword") || p.endsWith("_spear") || has(p, "bow", "arrow", "trident", "mace", "shield", "wind_charge"))
            return Category.WEAPONS;
        if (p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots")
                || has(p, "horse_armor", "wolf_armor", "nautilus_armor"))
            return Category.ARMOR;
        ItemStack s = item.getDefaultInstance();
        if (s.has(DataComponents.FOOD)) return Category.FOOD;
        if (has(p, "minecart", "rail", "boat", "raft", "saddle", "elytra", "carrot_on", "fungus_on"))
            return Category.TRANSPORT;
        if (has(p, "redstone", "piston", "repeater", "comparator", "observer", "hopper", "dropper", "dispenser", "lever",
                "tripwire", "tnt", "daylight", "target", "lightning_rod", "note_block", "pressure_plate", "button"))
            return Category.REDSTONE;
        if (has(p, "seeds", "sapling", "wheat", "carrot", "potato", "beetroot", "melon", "pumpkin", "sugar_cane", "bone_meal",
                "cocoa", "kelp", "bamboo", "berries", "nether_wart", "mushroom", "flower", "tulip", "orchid", "pod"))
            return Category.FARMING;
        if (has(p, "brewing", "blaze_powder", "glass_bottle", "ghast_tear", "fermented", "cauldron", "dragon_breath", "magma_cream", "phantom_membrane"))
            return Category.POTIONS;
        if (p.endsWith("_ore") || has(p, "ingot", "raw_", "diamond", "emerald", "lapis", "quartz", "amethyst", "coal", "netherite", "ancient_debris", "nugget") || p.equals("redstone") || p.equals("copper_block"))
            return Category.ORES;
        if (has(p, "planks", "stairs", "slab", "wall", "bricks", "fence", "door", "glass", "concrete", "terracotta", "wool", "carpet", "polished", "smooth", "cut_", "chiseled", "log", "wood"))
            return Category.BUILDING;
        return Category.BLOCKS;
    }

    /** Value of a concrete stack for selling, or -1 if it cannot be sold. */
    public static long sellValue(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        Entry e = BY_ITEM.get(stack.getItem());
        if (e == null) return -1;
        // Only plain (and merely damaged) items are accepted; enchanted/renamed/custom items are rejected
        // so the flat price table cannot be used to launder valuable or fake items.
        if (!isPlain(stack)) return -1;
        long unit = e.sell();
        if (stack.isDamaged() && stack.getMaxDamage() > 0) {
            long remaining = stack.getMaxDamage() - stack.getDamageValue();
            unit = Math.max(1, unit * Math.max(0, remaining) / stack.getMaxDamage());
        }
        return Money.safeMul(unit, stack.getCount());
    }

    private static boolean isPlain(ItemStack stack) {
        if (stack.isEnchanted()) return false;
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.CUSTOM_DATA)) return false;
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        if (stack.isDamaged()) return true;
        return stack.getComponentsPatch().isEmpty();
    }
}
