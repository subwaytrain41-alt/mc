package com.example.ecomod;

import com.example.ecomod.core.LevelCurve;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** config/ecomod/config.json, prices.json and quests.json. All are created with defaults on first run. */
public final class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class ItemGrant {
        public String item;
        public int count;

        public ItemGrant() {}

        public ItemGrant(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public static final class Milestone {
        public int level;
        public String title;
        public long money;
        public List<ItemGrant> items = new ArrayList<>();

        public Milestone() {}

        Milestone(int level, String title, long money, List<ItemGrant> items) {
            this.level = level;
            this.title = title;
            this.money = money;
            this.items = items;
        }
    }

    public long startingBalance = 100;
    public int sellPercent = 60;
    public long confirmPurchaseAbove = 100_000;
    public boolean includeModdedItems = false;

    public double levelBase = 50;
    public double levelExponent = 1.5;
    public int maxLevel = 200;

    public int xpPerHostileKill = 2;
    public int xpPerOreMined = 1;
    public int xpPerBossKill = 500;
    public int activityXpDailyCap = 2000;

    public int maxHomes = 3;
    public int homeCooldownSeconds = 3;

    public List<Milestone> milestones = defaultMilestones();

    private static Config instance = new Config();
    private static LevelCurve curve = new LevelCurve(50, 1.5, 200);

    public static Config get() { return instance; }

    public static LevelCurve curve() { return curve; }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("ecomod");
    }

    private static List<ItemGrant> armor(String material) {
        return List.of(new ItemGrant("minecraft:" + material + "_helmet", 1),
                new ItemGrant("minecraft:" + material + "_chestplate", 1),
                new ItemGrant("minecraft:" + material + "_leggings", 1),
                new ItemGrant("minecraft:" + material + "_boots", 1));
    }

    private static List<Milestone> defaultMilestones() {
        List<Milestone> m = new ArrayList<>();
        m.add(new Milestone(5, "Getting Started", 2_500, new ArrayList<>()));
        m.add(new Milestone(10, "Apprentice", 10_000, new ArrayList<>()));
        m.add(new Milestone(25, "Journeyman", 50_000, new ArrayList<>()));
        m.add(new Milestone(35, "Veteran", 100_000, new ArrayList<>()));
        m.add(new Milestone(50, "Diamond Standard", 0, new ArrayList<>(armor("diamond"))));
        m.add(new Milestone(60, "Expert", 250_000, new ArrayList<>()));
        m.add(new Milestone(75, "Master", 500_000, new ArrayList<>()));
        m.add(new Milestone(90, "Grandmaster", 1_000_000, new ArrayList<>()));
        m.add(new Milestone(100, "Netherite Legend", 0, new ArrayList<>(armor("netherite"))));
        return m;
    }

    public static void load() {
        try {
            Files.createDirectories(dir());
            Path p = dir().resolve("config.json");
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            }
            sanitize(instance);
            Files.writeString(p, GSON.toJson(instance)); // rewrites with any newly added fields
        } catch (IOException | RuntimeException e) {
            EconomyMod.LOGGER.error("Could not read config.json, using defaults", e);
            instance = new Config();
        }
        curve = new LevelCurve(instance.levelBase, instance.levelExponent, instance.maxLevel);
    }

    private static void sanitize(Config c) {
        c.sellPercent = Math.max(1, Math.min(99, c.sellPercent)); // selling must stay below buying
        c.startingBalance = Math.max(0, c.startingBalance);
        c.maxHomes = Math.max(1, c.maxHomes);
        c.maxLevel = Math.max(2, c.maxLevel);
        c.levelBase = Math.max(1, c.levelBase);
        c.levelExponent = Math.max(1.0, c.levelExponent);
        if (c.milestones == null) c.milestones = defaultMilestones();
        c.milestones.removeIf(m -> m == null || m.level < 2);
        for (Milestone m : c.milestones) {
            m.money = Math.max(0, m.money);
            if (m.items == null) m.items = new ArrayList<>();
        }
    }

    // ---- prices.json: { "minecraft:dirt": 1, ... } ----
    public static Map<String, Long> loadPrices() {
        Path p = dir().resolve("prices.json");
        try {
            if (Files.exists(p)) {
                Type t = new TypeToken<TreeMap<String, Long>>() {}.getType();
                Map<String, Long> m = GSON.fromJson(Files.readString(p), t);
                if (m != null) return m;
            }
        } catch (IOException | RuntimeException e) {
            EconomyMod.LOGGER.error("Could not read prices.json, regenerating", e);
        }
        return new TreeMap<>();
    }

    public static void savePrices(Map<String, Long> prices) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("prices.json"), GSON.toJson(new TreeMap<>(prices)));
        } catch (IOException e) {
            EconomyMod.LOGGER.error("Could not write prices.json", e);
        }
    }

    // ---- quests.json ----
    public static List<QuestDef> loadQuests() {
        Path p = dir().resolve("quests.json");
        List<QuestDef> quests = null;
        try {
            if (Files.exists(p)) {
                Type t = new TypeToken<List<QuestDef>>() {}.getType();
                quests = GSON.fromJson(Files.readString(p), t);
            }
        } catch (IOException | RuntimeException e) {
            EconomyMod.LOGGER.error("Could not read quests.json, using defaults", e);
        }
        if (quests == null || quests.isEmpty()) {
            quests = QuestDef.defaults();
            try {
                Files.createDirectories(dir());
                Files.writeString(p, GSON.toJson(quests));
            } catch (IOException e) {
                EconomyMod.LOGGER.error("Could not write quests.json", e);
            }
        }
        return quests;
    }
}
