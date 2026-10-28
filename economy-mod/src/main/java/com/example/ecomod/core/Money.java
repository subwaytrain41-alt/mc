package com.example.ecomod.core;

import java.util.Locale;

/** Pure money helpers (no Minecraft dependencies, unit-testable). */
public final class Money {
    public static final long MAX_PRICE = 50_000_000L;
    /** Hard ceiling for any balance so arithmetic can never overflow. */
    public static final long MAX_BALANCE = 1_000_000_000_000_000L;

    private Money() {}

    public static String format(long amount) {
        String digits = String.format(Locale.ROOT, "%,d", Math.abs(amount));
        return (amount < 0 ? "-$" : "$") + digits;
    }

    /** Selling price of one item: buy * percent / 100, rounded half up, minimum $1. */
    public static long sellPrice(long buyPrice, int sellPercent) {
        if (buyPrice <= 0) return 1;
        long v = (buyPrice * sellPercent + 50) / 100;
        return Math.max(1, v);
    }

    public static long clampPrice(long p) {
        return Math.min(MAX_PRICE, Math.max(1, p));
    }

    /** Adds without overflow, capped at MAX_BALANCE. */
    public static long safeAdd(long balance, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        long r = balance + amount; // both bounded well below Long.MAX_VALUE
        return Math.min(MAX_BALANCE, r);
    }

    public static long safeMul(long unit, long count) {
        try {
            return Math.multiplyExact(unit, count);
        } catch (ArithmeticException e) {
            return MAX_BALANCE;
        }
    }
}
