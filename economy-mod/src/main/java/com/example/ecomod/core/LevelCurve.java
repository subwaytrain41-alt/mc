package com.example.ecomod.core;

/**
 * XP curve. XP needed to go from level L to L+1 is round(base * L^exponent).
 * Players start at level 1 with 0 total XP.
 * With defaults (50, 1.5): level 50 needs ~350k total XP, level 100 needs ~2.0M.
 */
public final class LevelCurve {
    private final double base;
    private final double exponent;
    private final int maxLevel;

    public LevelCurve(double base, double exponent, int maxLevel) {
        this.base = base;
        this.exponent = exponent;
        this.maxLevel = maxLevel;
    }

    public int maxLevel() { return maxLevel; }

    public long xpToNext(int level) {
        return Math.max(1, Math.round(base * Math.pow(Math.max(1, level), exponent)));
    }

    /** Total XP required to be at {@code level}. */
    public long totalForLevel(int level) {
        long sum = 0;
        for (int i = 1; i < level; i++) sum += xpToNext(i);
        return sum;
    }

    public int levelForTotal(long totalXp) {
        int level = 1;
        long needed = xpToNext(1);
        long acc = 0;
        while (level < maxLevel && totalXp >= acc + needed) {
            acc += needed;
            level++;
            needed = xpToNext(level);
        }
        return level;
    }
}
