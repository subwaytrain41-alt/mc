package com.example.ecomod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player persistence under <world>/ecomod/players/<uuid>.json. Server-thread only. */
public final class DataStore {
    private static final Map<UUID, PlayerData> LOADED = new HashMap<>();
    private static Path dir;

    private DataStore() {}

    public static void init(MinecraftServer server) {
        dir = server.getWorldPath(LevelResource.ROOT).resolve("ecomod").resolve("players");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            EconomyMod.LOGGER.error("Could not create data directory {}", dir, e);
        }
        LOADED.clear();
    }

    public static PlayerData get(ServerPlayer p) {
        return get(p.getUUID());
    }

    public static PlayerData get(UUID id) {
        return LOADED.computeIfAbsent(id, DataStore::read);
    }

    private static PlayerData read(UUID id) {
        Path f = dir.resolve(id + ".json");
        try {
            if (Files.exists(f)) {
                PlayerData d = Config.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PlayerData.class);
                if (d != null) return normalize(d);
            }
        } catch (IOException | RuntimeException e) {
            // Keep the unreadable file for manual recovery rather than silently overwriting it.
            EconomyMod.LOGGER.error("Corrupt player data {}, moving it aside", f, e);
            try {
                Files.move(f, dir.resolve(id + ".corrupt-" + System.currentTimeMillis() + ".json"));
            } catch (IOException ignored) {
                // nothing more we can do
            }
        }
        PlayerData d = new PlayerData();
        d.balance = Config.get().startingBalance;
        return d;
    }

    private static PlayerData normalize(PlayerData d) {
        if (d.baselines == null) d.baselines = new HashMap<>();
        if (d.completions == null) d.completions = new HashMap<>();
        if (d.cooldownUntil == null) d.cooldownUntil = new HashMap<>();
        if (d.claimedOnce == null) d.claimedOnce = new java.util.HashSet<>();
        if (d.claimedMilestones == null) d.claimedMilestones = new java.util.HashSet<>();
        if (d.pendingItems == null) d.pendingItems = new java.util.ArrayList<>();
        if (d.history == null) d.history = new java.util.ArrayList<>();
        if (d.homes == null) d.homes = new java.util.ArrayList<>();
        if (d.balance < 0) d.balance = 0;
        if (d.level < 1) d.level = 1;
        return d;
    }

    public static void save(UUID id) {
        PlayerData d = LOADED.get(id);
        if (d == null || dir == null) return;
        Path tmp = dir.resolve(id + ".json.tmp");
        Path target = dir.resolve(id + ".json");
        try {
            Files.writeString(tmp, Config.GSON.toJson(d), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            EconomyMod.LOGGER.error("Could not save data for {}", id, e);
        }
    }

    public static void saveAll() {
        for (UUID id : LOADED.keySet()) save(id);
    }

    public static void unload(UUID id) {
        save(id);
        LOADED.remove(id);
    }
}
