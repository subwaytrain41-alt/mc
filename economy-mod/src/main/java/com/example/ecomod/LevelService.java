package com.example.ecomod;

import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** XP, level-ups, milestone rewards and the safe item-delivery queue. */
public final class LevelService {
    private LevelService() {}

    /** Adds XP unconditionally (quests, admin). */
    public static void addXp(ServerPlayer sp, long amount, String reason) {
        if (amount <= 0) return;
        PlayerData d = DataStore.get(sp);
        d.xp += amount;
        recalc(sp, d);
    }

    /** Adds XP from repeatable gameplay activity, subject to the configurable daily cap. */
    public static void addActivityXp(ServerPlayer sp, long amount) {
        if (amount <= 0) return;
        PlayerData d = DataStore.get(sp);
        long today = System.currentTimeMillis() / 86_400_000L;
        if (d.activityDay != today) {
            d.activityDay = today;
            d.activityXpToday = 0;
        }
        long room = Config.get().activityXpDailyCap - d.activityXpToday;
        if (room <= 0) return;
        long grant = Math.min(room, amount);
        d.activityXpToday += grant;
        addXp(sp, grant, "activity");
    }

    public static void recalc(ServerPlayer sp, PlayerData d) {
        int target = Config.curve().levelForTotal(d.xp);
        boolean leveled = false;
        while (d.level < target) {
            d.level++;
            leveled = true;
            sp.sendSystemMessage(Component.literal("LEVEL UP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("You are now level " + d.level + ".").withStyle(ChatFormatting.YELLOW)));
            d.log("Reached level " + d.level);
        }
        if (leveled) {
            sp.displayClientMessage(Component.literal("Level " + d.level + "!").withStyle(ChatFormatting.GOLD), true);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        grantMilestones(sp, d);
    }

    /** Grants every reached, unclaimed milestone exactly once. */
    public static void grantMilestones(ServerPlayer sp, PlayerData d) {
        for (Config.Milestone m : Config.get().milestones) {
            if (d.level < m.level || d.claimedMilestones.contains(m.level)) continue;
            d.claimedMilestones.add(m.level); // mark first: a reward can never be paid twice
            if (m.money > 0) {
                Economy.deposit(sp, m.money);
            }
            if (!m.items.isEmpty()) {
                for (Config.ItemGrant g : m.items) d.pendingItems.add(new Config.ItemGrant(g.item, g.count));
            }
            String what = (m.money > 0 ? Money.format(m.money) : "") + (m.money > 0 && !m.items.isEmpty() ? " + " : "")
                    + (!m.items.isEmpty() ? "items" : "");
            d.log("Level " + m.level + " reward: " + what);
            sp.sendSystemMessage(Component.literal("Milestone reward (" + m.title + "): ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(what).withStyle(ChatFormatting.GREEN)));
        }
        deliverPending(sp);
    }

    /**
     * Moves queued reward items into the inventory. Whatever does not fit stays queued
     * (nothing is ever deleted or dropped); claim again after freeing space.
     */
    public static int deliverPending(ServerPlayer sp) {
        PlayerData d = DataStore.get(sp);
        if (d.pendingItems.isEmpty()) return 0;
        List<Config.ItemGrant> remaining = new ArrayList<>();
        int delivered = 0;
        for (Config.ItemGrant g : d.pendingItems) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(g.item));
            if (item.isEmpty()) {
                EconomyMod.LOGGER.warn("Unknown reward item {} dropped from queue for {}", g.item, sp.getName().getString());
                continue;
            }
            int left = g.count;
            while (left > 0) {
                int n = Math.min(left, item.get().getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item.get(), n);
                sp.getInventory().add(stack);
                int notAdded = stack.getCount();
                delivered += n - notAdded;
                left -= n - notAdded;
                if (notAdded > 0) break; // inventory full
            }
            if (left > 0) remaining.add(new Config.ItemGrant(g.item, left));
        }
        d.pendingItems = remaining;
        if (!remaining.isEmpty()) {
            sp.sendSystemMessage(Component.literal("Your inventory is full - " + pendingCount(d)
                    + " reward item(s) are stored. Free some space and open /level (or run /rewards) to claim them.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return delivered;
    }

    public static int pendingCount(PlayerData d) {
        int n = 0;
        for (Config.ItemGrant g : d.pendingItems) n += g.count;
        return n;
    }
}
