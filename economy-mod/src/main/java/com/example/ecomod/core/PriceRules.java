package com.example.ecomod.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based default prices keyed by item id path (e.g. "iron_pickaxe").
 * Pure Java so it can be unit-tested without Minecraft.
 * Returns -1 when no rule applies; the game layer then falls back to a rarity-based price.
 * Everything here is only a default: admins override any price in prices.json.
 */
public final class PriceRules {
    public static final long DIRT = 1;
    public static final long ELYTRA = 50_000_000L;

    private static final Map<String, Long> EXACT = new HashMap<>();
    private static final Map<String, Long> COLORED = new HashMap<>();
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Map<String, Long> SUFFIX = new LinkedHashMap<>();

    // Tool material base = price of a hoe. Multipliers enforce hoe < shovel < axe < (spear) < sword < pickaxe.
    private static final Map<String, Long> TOOL_BASE = Map.of(
            "wooden", 20L, "stone", 40L, "copper", 100L, "iron", 300L, "golden", 600L, "diamond", 4_000L);
    private static final Map<String, Double> TOOL_MULT = Map.of(
            "hoe", 1.0, "shovel", 1.25, "axe", 1.6, "spear", 1.8, "sword", 2.0, "pickaxe", 2.5);
    private static final Map<String, Long> NETHERITE_TOOL = Map.of(
            "hoe", 1_200_000L, "shovel", 1_300_000L, "axe", 1_450_000L, "spear", 1_525_000L,
            "sword", 1_600_000L, "pickaxe", 1_750_000L);

    // Armor: price = piece weight * per-unit material value.
    private static final Map<String, Long> ARMOR_UNIT = Map.of(
            "leather", 8L, "chainmail", 70L, "copper", 40L, "iron", 120L, "golden", 300L, "diamond", 2_500L);
    private static final Map<String, Long> NETHERITE_ARMOR = Map.of(
            "helmet", 1_500_000L, "chestplate", 2_000_000L, "leggings", 1_900_000L, "boots", 1_400_000L);
    private static final Map<String, Integer> ARMOR_WEIGHT = Map.of(
            "helmet", 5, "chestplate", 8, "leggings", 7, "boots", 4);

    private static final Set<String> EXCLUDED = Set.of(
            "air", "barrier", "bedrock", "light", "structure_block", "structure_void", "jigsaw", "debug_stick",
            "knowledge_book", "command_block", "chain_command_block", "repeating_command_block",
            "command_block_minecart", "spawner", "trial_spawner", "vault", "end_portal_frame", "reinforced_deepslate",
            "budding_amethyst", "petrified_oak_slab", "chorus_plant", "farmland", "frogspawn", "potion",
            "splash_potion", "lingering_potion", "tipped_arrow", "enchanted_book", "suspicious_stew", "goat_horn",
            "filled_map", "written_book", "player_head", "firework_star", "ominous_bottle", "test_block",
            "test_instance_block", "end_portal", "nether_portal", "end_gateway");

    static {
        loadExact("""
                dirt=1
                coarse_dirt=2
                rooted_dirt=6
                podzol=5
                mycelium=25
                grass_block=3
                dirt_path=3
                mud=2
                packed_mud=6
                mud_bricks=8
                clay=8
                clay_ball=3
                sand=3
                red_sand=3
                gravel=3
                stone=4
                cobblestone=3
                mossy_cobblestone=8
                stone_bricks=8
                deepslate=5
                cobbled_deepslate=4
                deepslate_bricks=10
                deepslate_tiles=12
                andesite=4
                diorite=4
                granite=4
                tuff=4
                tuff_bricks=8
                calcite=10
                dripstone_block=10
                pointed_dripstone=6
                basalt=6
                blackstone=6
                netherrack=2
                soul_sand=8
                soul_soil=8
                nether_bricks=10
                red_nether_bricks=14
                nether_brick=3
                glowstone=40
                glowstone_dust=10
                magma_block=30
                end_stone=12
                end_stone_bricks=14
                purpur_block=20
                purpur_pillar=22
                obsidian=400
                crying_obsidian=1200
                ice=12
                packed_ice=40
                blue_ice=200
                snow=3
                snow_block=8
                snowball=2
                glass=6
                glass_pane=3
                tinted_glass=150
                bricks=24
                brick=5
                terracotta=8
                moss_block=12
                moss_carpet=6
                sculk=40
                sculk_vein=20
                sculk_sensor=2000
                sculk_catalyst=4000
                sculk_shrieker=3500
                coal=40
                charcoal=30
                coal_block=360
                raw_iron=100
                iron_ingot=120
                iron_nugget=14
                iron_block=1080
                raw_iron_block=900
                raw_copper=30
                copper_ingot=40
                copper_block=360
                raw_copper_block=270
                raw_gold=250
                gold_ingot=300
                gold_nugget=34
                gold_block=2700
                raw_gold_block=2250
                redstone=30
                redstone_block=270
                lapis_lazuli=60
                lapis_block=540
                quartz=80
                quartz_block=320
                quartz_bricks=340
                amethyst_shard=100
                amethyst_block=400
                diamond=2500
                diamond_block=22500
                emerald=1200
                emerald_block=10800
                coal_ore=45
                iron_ore=120
                copper_ore=40
                gold_ore=280
                redstone_ore=35
                lapis_ore=70
                diamond_ore=2600
                emerald_ore=1300
                nether_gold_ore=120
                nether_quartz_ore=90
                ancient_debris=300000
                netherite_scrap=1000000
                netherite_ingot=1100000
                netherite_block=2000000
                netherite_upgrade_smithing_template=1000000
                lodestone=1500000
                elytra=50000000
                dragon_egg=20000000
                dragon_head=2000000
                wither_skeleton_skull=250000
                zombie_head=40000
                skeleton_skull=40000
                creeper_head=45000
                piglin_head=60000
                nether_star=600000
                beacon=900000
                conduit=150000
                heart_of_the_sea=25000
                totem_of_undying=150000
                trident=200000
                mace=8000000
                heavy_core=400000
                breeze_rod=800
                wind_charge=120
                trial_key=5000
                ominous_trial_key=30000
                nautilus_shell=4000
                shulker_shell=2500
                shulker_box=3000
                sponge=1500
                wet_sponge=1500
                echo_shard=6000
                recovery_compass=55000
                disc_fragment_5=2500
                music_disc_pigstep=60000
                music_disc_otherside=40000
                music_disc_creator=45000
                saddle=2500
                name_tag=1000
                experience_bottle=3000
                enchanting_table=7000
                anvil=4000
                chipped_anvil=3000
                damaged_anvil=2000
                ender_chest=3400
                end_crystal=20000
                respawn_anchor=6000
                ender_pearl=150
                ender_eye=300
                blaze_rod=180
                blaze_powder=90
                ghast_tear=900
                slime_ball=40
                magma_cream=100
                phantom_membrane=250
                prismarine_shard=40
                prismarine_crystals=90
                turtle_scute=400
                armadillo_scute=700
                turtle_helmet=750
                wolf_armor=800
                bone=10
                bone_meal=4
                string=8
                gunpowder=25
                feather=6
                leather=20
                rabbit_hide=10
                rabbit_foot=500
                flint=3
                stick=1
                ink_sac=20
                glow_ink_sac=90
                dragon_breath=5000
                fire_charge=60
                apple=25
                golden_apple=8000
                enchanted_golden_apple=750000
                golden_carrot=400
                glistering_melon_slice=350
                bread=15
                wheat=6
                wheat_seeds=2
                beetroot_seeds=2
                melon_seeds=3
                pumpkin_seeds=3
                torchflower_seeds=40
                pitcher_pod=40
                carrot=4
                potato=4
                baked_potato=8
                poisonous_potato=6
                beetroot=5
                melon_slice=3
                melon=25
                pumpkin=20
                carved_pumpkin=24
                jack_o_lantern=30
                sugar_cane=5
                sugar=6
                cocoa_beans=10
                sweet_berries=4
                glow_berries=8
                kelp=4
                dried_kelp=5
                dried_kelp_block=45
                bamboo=3
                cactus=5
                beef=12
                cooked_beef=22
                porkchop=12
                cooked_porkchop=22
                chicken=8
                cooked_chicken=16
                mutton=10
                cooked_mutton=20
                rabbit=10
                cooked_rabbit=20
                cod=6
                cooked_cod=12
                salmon=8
                cooked_salmon=16
                tropical_fish=25
                pufferfish=40
                egg=5
                milk_bucket=60
                cake=120
                cookie=4
                pumpkin_pie=40
                mushroom_stew=25
                rabbit_stew=70
                beetroot_soup=30
                honey_bottle=60
                honeycomb=30
                honey_block=120
                honeycomb_block=120
                rotten_flesh=4
                spider_eye=12
                fermented_spider_eye=40
                chorus_fruit=20
                popped_chorus_fruit=25
                piston=120
                sticky_piston=180
                observer=200
                hopper=750
                dropper=80
                dispenser=100
                crafter=300
                repeater=60
                comparator=150
                redstone_torch=35
                redstone_lamp=150
                lever=8
                tripwire_hook=30
                tnt=150
                daylight_detector=250
                target=120
                lightning_rod=130
                note_block=40
                jukebox=3000
                rail=25
                powered_rail=150
                detector_rail=80
                activator_rail=70
                minecart=400
                chest_minecart=420
                hopper_minecart=1200
                tnt_minecart=600
                furnace_minecart=450
                lead=40
                carrot_on_a_stick=30
                warped_fungus_on_a_stick=40
                flint_and_steel=130
                shears=250
                fishing_rod=30
                bow=60
                crossbow=250
                arrow=3
                spectral_arrow=15
                shield=150
                bucket=400
                water_bucket=410
                lava_bucket=450
                powder_snow_bucket=420
                compass=600
                clock=1300
                spyglass=200
                brush=90
                book=40
                writable_book=120
                paper=5
                map=150
                bookshelf=120
                chiseled_bookshelf=130
                lectern=140
                bundle=90
                iron_door=700
                iron_trapdoor=500
                iron_bars=50
                chain=30
                lantern=60
                soul_lantern=70
                torch=2
                soul_torch=4
                campfire=20
                soul_campfire=30
                furnace=15
                blast_furnace=900
                smoker=60
                crafting_table=10
                chest=25
                trapped_chest=60
                barrel=20
                loom=25
                stonecutter=150
                grindstone=40
                smithing_table=80
                cartography_table=40
                fletching_table=30
                brewing_stand=250
                cauldron=840
                composter=20
                bell=3000
                scaffolding=15
                ladder=3
                painting=25
                item_frame=15
                glow_item_frame=100
                armor_stand=40
                flower_pot=8
                decorated_pot=40
                bowl=2
                glass_bottle=8
                end_rod=40
                heavy_weighted_pressure_plate=600
                light_weighted_pressure_plate=300
                stone_pressure_plate=8
                stone_button=3
                oak_planks=3
                cobweb=300
                slime_block=360
                sea_lantern=240
                prismarine=60
                prismarine_bricks=90
                dark_prismarine=100
                seagrass=3
                sea_pickle=20
                lily_pad=8
                vine=4
                glow_lichen=20
                hanging_roots=12
                big_dripleaf=30
                small_dripleaf=20
                spore_blossom=200
                azalea=20
                flowering_azalea=25
                mangrove_roots=12
                muddy_mangrove_roots=14
                chorus_flower=60
                shroomlight=80
                crimson_fungus=20
                warped_fungus=20
                crimson_roots=6
                warped_roots=6
                nether_sprouts=6
                weeping_vines=6
                twisting_vines=6
                nether_wart=20
                nether_wart_block=40
                warped_wart_block=40
                red_mushroom=10
                brown_mushroom=10
                red_mushroom_block=20
                brown_mushroom_block=20
                mushroom_stem=20
                fern=3
                short_grass=2
                tall_grass=3
                dead_bush=3
                sunflower=6
                lilac=6
                rose_bush=6
                peony=6
                dandelion=3
                poppy=3
                blue_orchid=6
                allium=6
                azure_bluet=6
                red_tulip=6
                orange_tulip=6
                white_tulip=6
                pink_tulip=6
                oxeye_daisy=6
                cornflower=6
                lily_of_the_valley=8
                wither_rose=2500
                torchflower=40
                pitcher_plant=40
                pink_petals=5
                wildflowers=5
                leaf_litter=3
                cactus_flower=20
                firefly_bush=40
                bush=8
                eyeblossom=50
                open_eyeblossom=50
                closed_eyeblossom=50
                pale_moss_block=15
                pale_moss_carpet=7
                pale_hanging_moss=10
                resin_clump=15
                resin_block=135
                resin_brick=20
                resin_bricks=80
                creaking_heart=15000
                firework_rocket=40
                lingering=0
                """);
        EXACT.remove("lingering");

        loadColored("""
                wool=12
                carpet=5
                bed=60
                banner=40
                candle=12
                concrete=8
                concrete_powder=8
                terracotta=8
                glazed_terracotta=14
                stained_glass=8
                stained_glass_pane=3
                shulker_box=3000
                dye=6
                bundle=90
                harness=150
                """);

        SUFFIX.put("_hanging_sign", 25L);
        SUFFIX.put("_sign", 12L);
        SUFFIX.put("_chest_boat", 28L);
        SUFFIX.put("_chest_raft", 28L);
        SUFFIX.put("_boat", 16L);
        SUFFIX.put("_raft", 16L);
        SUFFIX.put("_planks", 3L);
        SUFFIX.put("_log", 8L);
        SUFFIX.put("_wood", 8L);
        SUFFIX.put("_stem", 8L);
        SUFFIX.put("_hyphae", 8L);
        SUFFIX.put("_sapling", 12L);
        SUFFIX.put("_propagule", 12L);
        SUFFIX.put("_leaves", 3L);
        SUFFIX.put("_fence_gate", 8L);
        SUFFIX.put("_fence", 5L);
        SUFFIX.put("_trapdoor", 9L);
        SUFFIX.put("_door", 10L);
        SUFFIX.put("_pressure_plate", 5L);
        SUFFIX.put("_button", 3L);
        SUFFIX.put("_shelf", 14L);
        SUFFIX.put("_banner_pattern", 400L);
        SUFFIX.put("_smithing_template", 8000L);
        SUFFIX.put("_pottery_sherd", 600L);
        SUFFIX.put("_spawn_egg", -1L);
    }

    private PriceRules() {}

    private static void loadExact(String block) {
        for (String line : block.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] kv = line.split("=");
            EXACT.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
        }
    }

    private static void loadColored(String block) {
        for (String line : block.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] kv = line.split("=");
            COLORED.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
        }
    }

    /** Items that cannot be obtained in survival (or have no single default form) are not sold. */
    public static boolean excluded(String path) {
        return EXCLUDED.contains(path) || path.endsWith("_spawn_egg") || path.startsWith("infested_")
                || path.startsWith("test_") || path.endsWith("_command_block");
    }

    /** Default price for an item id path, or -1 if no rule applies. */
    public static long price(String path) {
        return price(path, 0);
    }

    private static long price(String path, int depth) {
        if (depth > 3) return -1;
        Long exact = EXACT.get(path);
        if (exact != null) return exact;

        long t = toolOrArmor(path);
        if (t > 0) return t;

        // Netherite catch-all stays inside the required $1M-$2M band.
        if (path.startsWith("netherite_")) return 1_500_000L;

        // Ores in deepslate cost slightly more than the stone variant.
        if (path.startsWith("deepslate_") && path.endsWith("_ore")) {
            long b = price(path.substring("deepslate_".length()), depth + 1);
            if (b > 0) return Math.max(2, Math.round(b * 1.1));
        }

        for (String c : COLORS) {
            if (path.startsWith(c + "_")) {
                Long r = COLORED.get(path.substring(c.length() + 1));
                if (r != null) return r;
            }
        }

        for (Map.Entry<String, Long> s : SUFFIX.entrySet()) {
            if (path.endsWith(s.getKey())) return s.getValue();
        }

        return derived(path, depth);
    }

    private static long derived(String path, int depth) {
        String[][] prefixes = {
                {"waxed_", "1.1"}, {"exposed_", "1.0"}, {"weathered_", "1.0"}, {"oxidized_", "1.0"},
                {"stripped_", "1.0"}, {"polished_", "1.5"}, {"smooth_", "1.25"}, {"chiseled_", "1.5"},
                {"cut_", "1.25"}, {"cracked_", "1.1"}, {"mossy_", "1.5"}};
        for (String[] p : prefixes) {
            if (path.startsWith(p[0])) {
                long b = price(path.substring(p[0].length()), depth + 1);
                if (b > 0) return Math.max(2, Math.round(b * Double.parseDouble(p[1])));
            }
        }
        Object[][] shapes = {{"_stairs", 1.5}, {"_slab", 0.5}, {"_wall", 1.0}};
        for (Object[] sh : shapes) {
            String suf = (String) sh[0];
            if (path.endsWith(suf)) {
                String base = path.substring(0, path.length() - suf.length());
                for (String cand : new String[]{base, base + "s", base + "_block", base + "_bricks", base + "_planks"}) {
                    long b = price(cand, depth + 1);
                    if (b > 0) return Math.max(2, Math.round(b * (double) sh[1]));
                }
                return 6;
            }
        }
        if (path.contains("copper")) {
            // Copper family (bulbs, grates, chests, lanterns, golem statues, ...)
            if (path.endsWith("_block") || path.contains("copper_grate") || path.contains("copper_bulb")) return 360;
            return 200;
        }
        return -1;
    }

    private static long toolOrArmor(String path) {
        int us = path.indexOf('_');
        if (us < 0) return -1;
        String material = path.substring(0, us);
        String kind = path.substring(us + 1);

        if (TOOL_MULT.containsKey(kind)) {
            if (material.equals("netherite")) return NETHERITE_TOOL.get(kind);
            Long base = TOOL_BASE.get(material);
            if (base != null) return Math.round(base * TOOL_MULT.get(kind));
        }
        if (ARMOR_WEIGHT.containsKey(kind)) {
            if (material.equals("netherite")) return NETHERITE_ARMOR.get(kind);
            Long unit = ARMOR_UNIT.get(material);
            if (unit != null) return unit * ARMOR_WEIGHT.get(kind);
        }
        if (kind.equals("horse_armor") || kind.equals("nautilus_armor")) {
            if (material.equals("netherite")) return 1_500_000L;
            Long unit = ARMOR_UNIT.get(material);
            if (unit != null) return unit * 12;
        }
        return -1;
    }
}
