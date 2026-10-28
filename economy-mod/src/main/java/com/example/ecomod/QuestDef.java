package com.example.ecomod;

import java.util.ArrayList;
import java.util.List;

/**
 * One quest. Progress is derived from vanilla statistics / advancements (server-side, hard to fake):
 *  KILL  - targets are entity ids, or "@monster" for every hostile mob
 *  CRAFT - targets are item ids; counts vanilla "crafted" stat (this includes items taken from furnaces)
 *  MINE  - targets are block ids or "#tag:id" block tags; counts blocks broken
 *  STAT  - targets[0] is a custom stat id (e.g. minecraft:fish_caught)
 *  ADV   - every advancement in targets must be completed (one-time quests only)
 *  CROPS - mature crops harvested (tracked by this mod; immature crops never count)
 */
public final class QuestDef {
    public String id;
    public String name;
    public String description;
    public String difficulty; // EASY, MEDIUM, HARD, LEGENDARY, EXTREME
    public String type;
    public List<String> targets = new ArrayList<>();
    public long amount = 1;
    public long money;
    public long xp;
    public boolean repeatable;
    public long cooldownMinutes;
    public String icon = "minecraft:paper";

    public QuestDef() {}

    private static QuestDef q(String id, String name, String desc, String diff, String type, long amount, long money,
                              long xp, boolean repeatable, long cooldownMin, String icon, String... targets) {
        QuestDef d = new QuestDef();
        d.id = id;
        d.name = name;
        d.description = desc;
        d.difficulty = diff;
        d.type = type;
        d.amount = amount;
        d.money = money;
        d.xp = xp;
        d.repeatable = repeatable;
        d.cooldownMinutes = cooldownMin;
        d.icon = icon;
        d.targets = new ArrayList<>(List.of(targets));
        return d;
    }

    public static List<QuestDef> defaults() {
        List<QuestDef> l = new ArrayList<>();
        l.add(q("zombie_hunter", "Zombie Hunter", "Kill 10 zombies.", "EASY", "KILL", 10, 500, 50, true, 60, "minecraft:rotten_flesh", "minecraft:zombie"));
        l.add(q("skeleton_slayer", "Skeleton Slayer", "Kill 15 skeletons.", "EASY", "KILL", 15, 750, 75, true, 60, "minecraft:bone", "minecraft:skeleton"));
        l.add(q("stone_collector", "Stone Collector", "Mine 256 stone blocks.", "EASY", "MINE", 256, 750, 60, true, 60, "minecraft:cobblestone", "minecraft:stone"));
        l.add(q("lumberjack", "Lumberjack", "Chop 128 logs.", "EASY", "MINE", 128, 1_500, 100, true, 60, "minecraft:iron_axe", "#minecraft:logs"));
        l.add(q("iron_miner", "Iron Miner", "Smelt 32 iron ingots.", "EASY", "CRAFT", 32, 2_000, 120, true, 120, "minecraft:iron_ingot", "minecraft:iron_ingot"));
        l.add(q("farmers_work", "Farmer's Work", "Harvest 128 fully grown crops.", "EASY", "CROPS", 128, 3_000, 150, true, 120, "minecraft:wheat"));
        l.add(q("fishing_day", "Fishing Day", "Catch 25 fish.", "MEDIUM", "STAT", 25, 4_000, 200, true, 180, "minecraft:fishing_rod", "minecraft:fish_caught"));
        l.add(q("creeper_control", "Creeper Control", "Kill 20 creepers.", "MEDIUM", "KILL", 20, 5_000, 250, true, 180, "minecraft:gunpowder", "minecraft:creeper"));
        l.add(q("master_chef", "Master Chef", "Cook 64 meals in a furnace, smoker or campfire.", "MEDIUM", "CRAFT", 64, 6_000, 300, true, 180, "minecraft:cooked_beef",
                "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton", "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:baked_potato"));
        l.add(q("monster_slayer", "Monster Slayer", "Kill 50 hostile mobs.", "MEDIUM", "KILL", 50, 10_000, 400, true, 240, "minecraft:iron_sword", "@monster"));
        l.add(q("gold_rush", "Gold Rush", "Smelt 64 gold ingots.", "MEDIUM", "CRAFT", 64, 15_000, 500, true, 360, "minecraft:gold_ingot", "minecraft:gold_ingot"));
        l.add(q("ender_hunter", "Ender Hunter", "Kill 30 endermen.", "HARD", "KILL", 30, 35_000, 1_000, true, 1440, "minecraft:ender_pearl", "minecraft:enderman"));
        l.add(q("blaze_hunter", "Blaze Hunter", "Kill 25 blazes.", "HARD", "KILL", 25, 40_000, 1_200, true, 1440, "minecraft:blaze_rod", "minecraft:blaze"));
        l.add(q("diamond_digger", "Diamond Digger", "Mine 16 diamond ores.", "HARD", "MINE", 16, 25_000, 800, true, 720, "minecraft:diamond", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
        l.add(q("enchanter", "Enchanter", "Enchant 10 items at an enchanting table.", "HARD", "STAT", 10, 30_000, 900, true, 720, "minecraft:enchanting_table", "minecraft:enchant_item"));
        l.add(q("treasure_hunter", "Treasure Hunter", "Brush up a pottery sherd from suspicious sand and open a vault with a trial key.", "HARD", "ADV", 2, 50_000, 1_500, false, 0, "minecraft:brush",
                "minecraft:adventure/salvage_sherd", "minecraft:adventure/under_lock_and_key"));
        l.add(q("nether_explorer", "Nether Explorer", "Enter the Nether and find a Nether Fortress.", "HARD", "ADV", 2, 75_000, 2_000, false, 0, "minecraft:netherrack",
                "minecraft:story/enter_the_nether", "minecraft:nether/find_fortress"));
        l.add(q("end_explorer", "End Explorer", "Enter the End.", "HARD", "ADV", 1, 100_000, 3_000, false, 0, "minecraft:end_stone", "minecraft:story/enter_the_end"));
        l.add(q("ancient_treasure", "Ancient Treasure", "Mine 16 ancient debris.", "LEGENDARY", "MINE", 16, 150_000, 3_500, true, 4320, "minecraft:ancient_debris", "minecraft:ancient_debris"));
        l.add(q("raid_defender", "Raid Defender", "Win a village raid (Hero of the Village).", "LEGENDARY", "ADV", 1, 200_000, 5_000, false, 0, "minecraft:white_banner", "minecraft:adventure/hero_of_the_village"));
        l.add(q("dragon_slayer", "Dragon Slayer", "Defeat the Ender Dragon.", "LEGENDARY", "ADV", 1, 300_000, 8_000, false, 0, "minecraft:dragon_head", "minecraft:end/kill_dragon"));
        l.add(q("wither_slayer", "Wither Slayer", "Defeat the Wither.", "LEGENDARY", "KILL", 1, 500_000, 12_000, false, 0, "minecraft:wither_skeleton_skull", "minecraft:wither"));
        l.add(q("legendary_collector", "Legendary Collector", "Obtain a totem of undying, craft a full-power beacon and wear a full set of netherite armor.", "LEGENDARY", "ADV", 3, 1_000_000, 20_000, false, 0, "minecraft:beacon",
                "minecraft:adventure/totem_of_undying", "minecraft:nether/create_full_beacon", "minecraft:nether/netherite_armor"));
        l.add(q("millionaires_challenge", "Millionaire's Challenge", "Slay the dragon, win a raid, summon the Wither and brew every potion effect.", "LEGENDARY", "ADV", 4, 2_000_000, 30_000, false, 0, "minecraft:emerald_block",
                "minecraft:end/kill_dragon", "minecraft:adventure/hero_of_the_village", "minecraft:nether/summon_wither", "minecraft:nether/all_potions"));
        l.add(q("ultimate_challenge", "Ultimate Challenge", "Visit every biome, kill every hostile mob type, tame every cat variant and eat every food.", "EXTREME", "ADV", 4, 10_000_000, 100_000, false, 0, "minecraft:nether_star",
                "minecraft:adventure/adventuring_time", "minecraft:adventure/kill_all_mobs", "minecraft:husbandry/complete_catalogue", "minecraft:husbandry/balanced_diet"));
        return l;
    }
}
