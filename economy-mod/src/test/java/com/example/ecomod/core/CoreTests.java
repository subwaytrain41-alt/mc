package com.example.ecomod.core;

import java.util.List;

/** Dependency-free tests for the pure logic. Run: see README ("Running the core tests"). */
public final class CoreTests {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + msg);
        }
    }

    public static void main(String[] args) {
        List<String> mats = List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite");
        String[] order = {"hoe", "shovel", "axe", "sword", "pickaxe"};

        // 1. Tool hierarchy within every material: hoe < shovel < axe < sword < pickaxe.
        for (String m : mats) {
            for (int i = 0; i + 1 < order.length; i++) {
                long a = PriceRules.price(m + "_" + order[i]);
                long b = PriceRules.price(m + "_" + order[i + 1]);
                check(a > 0 && b > 0 && a < b, m + ": " + order[i] + " (" + a + ") < " + order[i + 1] + " (" + b + ")");
            }
        }
        // spear sits between axe and sword
        for (String m : mats) {
            long axe = PriceRules.price(m + "_axe"), spear = PriceRules.price(m + "_spear"), sword = PriceRules.price(m + "_sword");
            check(axe < spear && spear < sword, m + ": axe < spear < sword");
        }
        // 2. Material ordering for each tool type: wooden < stone < iron < golden < diamond < netherite.
        for (String t : order) {
            long prev = 0;
            for (String m : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite")) {
                long p = PriceRules.price(m + "_" + t);
                check(p > prev, t + ": " + m + " (" + p + ") > previous (" + prev + ")");
                prev = p;
            }
        }
        // 3. Fixed prices from the spec.
        check(PriceRules.price("dirt") == 1, "dirt is $1");
        check(PriceRules.price("elytra") == 50_000_000L, "elytra is $50,000,000");
        // 4. Netherite items in [1M, 2M].
        for (String p : List.of("netherite_ingot", "netherite_scrap", "netherite_block", "netherite_helmet",
                "netherite_chestplate", "netherite_leggings", "netherite_boots", "netherite_hoe", "netherite_shovel",
                "netherite_axe", "netherite_sword", "netherite_pickaxe", "netherite_upgrade_smithing_template",
                "netherite_horse_armor", "netherite_spear", "netherite_nautilus_armor")) {
            long v = PriceRules.price(p);
            check(v >= 1_000_000 && v <= 2_000_000, p + " in $1M..$2M (was " + v + ")");
        }
        // 5. Nothing priced by rules exceeds elytra or undercuts dirt; only dirt is $1.
        List<String> sample = List.of("stone", "oak_planks", "oak_stairs", "stone_slab", "cobblestone_wall",
                "diamond_pickaxe", "diamond_block", "ancient_debris", "beacon", "nether_star", "dragon_egg", "mace",
                "red_wool", "lime_concrete", "polished_andesite", "cut_copper_stairs", "waxed_copper_block",
                "deepslate_diamond_ore", "white_shulker_box", "oak_boat", "iron_helmet", "leather_boots",
                "golden_apple", "enchanted_golden_apple", "totem_of_undying", "trident", "lodestone");
        for (String s : sample) {
            long v = PriceRules.price(s);
            check(v > 0, s + " has a rule");
            check(v <= PriceRules.ELYTRA, s + " <= elytra");
            if (!s.equals("dirt")) check(v >= 2, s + " is above dirt");
            if (!s.equals("elytra")) check(v < PriceRules.ELYTRA, s + " below elytra");
        }
        // 6. Sell formula.
        check(Money.sellPrice(1, 60) == 1, "dirt sells for $1 minimum");
        check(Money.sellPrice(100, 60) == 60, "$100 -> $60");
        check(Money.sellPrice(10_000, 60) == 6_000, "$10,000 -> $6,000");
        check(Money.sellPrice(1_500_000, 60) == 900_000, "$1.5M -> $900,000");
        check(Money.sellPrice(50_000_000, 60) == 30_000_000, "elytra -> $30,000,000");
        for (long p : new long[]{2, 3, 5, 7, 9, 13, 99, 12345, 777777}) {
            check(Money.sellPrice(p, 60) < p, "sell < buy for " + p);
            check(Money.sellPrice(p, 60) >= 1, "sell >= 1 for " + p);
        }
        // 7. Formatting and arithmetic safety.
        check(Money.format(1000).equals("$1,000"), "format $1,000");
        check(Money.format(50_000_000).equals("$50,000,000"), "format elytra");
        check(Money.safeAdd(Money.MAX_BALANCE, 5) == Money.MAX_BALANCE, "balance capped");
        check(Money.safeMul(Long.MAX_VALUE, 2) == Money.MAX_BALANCE, "multiplication overflow capped");
        // 8. XP curve.
        LevelCurve c = new LevelCurve(50, 1.5, 200);
        long prev = -1;
        for (int l = 1; l <= 150; l++) {
            long n = c.xpToNext(l);
            check(n > prev, "xp requirement strictly increases at level " + l);
            prev = n;
        }
        check(c.levelForTotal(0) == 1, "0 xp -> level 1");
        check(c.levelForTotal(c.totalForLevel(50)) == 50, "round trip level 50");
        check(c.levelForTotal(c.totalForLevel(50) - 1) == 49, "one xp short of 50");
        check(c.levelForTotal(c.totalForLevel(100)) == 100, "round trip level 100");
        check(c.totalForLevel(100) > 4 * c.totalForLevel(50), "level 100 is far beyond level 50");
        System.out.printf("total XP to level 50 = %,d, level 100 = %,d%n", c.totalForLevel(50), c.totalForLevel(100));
        // 9. Home names.
        check(Names.validHomeName("base"), "base valid");
        check(Names.validHomeName("My_Home-2"), "My_Home-2 valid");
        check(!Names.validHomeName(""), "empty invalid");
        check(!Names.validHomeName("has space"), "space invalid");
        check(!Names.validHomeName("../etc"), "path chars invalid");
        check(!Names.validHomeName("a".repeat(17)), "17 chars invalid");
        check(!Names.validHomeName(null), "null invalid");

        // 10. Exclusions.
        check(PriceRules.excluded("bedrock"), "bedrock excluded");
        check(PriceRules.excluded("zombie_spawn_egg"), "spawn eggs excluded");
        check(PriceRules.excluded("command_block"), "command blocks excluded");
        check(!PriceRules.excluded("dirt"), "dirt not excluded");

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
