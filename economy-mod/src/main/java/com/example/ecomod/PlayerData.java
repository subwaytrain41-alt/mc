package com.example.ecomod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything persisted for one player. Serialized as JSON, one file per player UUID. */
public final class PlayerData {
    public static final class Home {
        public String name;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
    }

    public long balance;
    public int level = 1;
    public long xp;

    public Map<String, Long> baselines = new HashMap<>();
    public Map<String, Integer> completions = new HashMap<>();
    public Map<String, Long> cooldownUntil = new HashMap<>();
    public Set<String> claimedOnce = new HashSet<>();
    public long cropsHarvested;

    public Set<Integer> claimedMilestones = new HashSet<>();
    public List<Config.ItemGrant> pendingItems = new ArrayList<>();
    public List<String> history = new ArrayList<>();

    public long activityXpToday;
    public long activityDay;

    public List<Home> homes = new ArrayList<>();

    public boolean initialized;

    public void log(String line) {
        history.add(line);
        while (history.size() > 30) history.remove(0);
    }
}
