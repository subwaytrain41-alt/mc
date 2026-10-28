package com.example.ecomod;

import com.example.ecomod.core.Money;
import net.minecraft.server.level.ServerPlayer;

/** The single place balances change. Every operation is validated and runs on the server thread. */
public final class Economy {
    private Economy() {}

    public static long balance(ServerPlayer p) {
        return DataStore.get(p).balance;
    }

    public static boolean canAfford(ServerPlayer p, long amount) {
        return amount >= 0 && DataStore.get(p).balance >= amount;
    }

    /** Removes money. Returns false (and changes nothing) for invalid amounts or insufficient funds. */
    public static boolean withdraw(ServerPlayer p, long amount) {
        if (amount < 0) return false;
        PlayerData d = DataStore.get(p);
        if (d.balance < amount) return false;
        d.balance -= amount;
        return true;
    }

    public static void deposit(ServerPlayer p, long amount) {
        if (amount <= 0) return;
        PlayerData d = DataStore.get(p);
        d.balance = Money.safeAdd(d.balance, amount);
    }

    public static void set(PlayerData d, long amount) {
        d.balance = Math.max(0, Math.min(Money.MAX_BALANCE, amount));
    }
}
