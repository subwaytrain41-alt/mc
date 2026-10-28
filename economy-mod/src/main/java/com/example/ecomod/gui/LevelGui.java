package com.example.ecomod.gui;

import com.example.ecomod.Config;
import com.example.ecomod.DataStore;
import com.example.ecomod.Economy;
import com.example.ecomod.LevelService;
import com.example.ecomod.PlayerData;
import com.example.ecomod.core.LevelCurve;
import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class LevelGui {
    private LevelGui() {}

    public static void open(ServerPlayer player) {
        GuiMenu.open(player, Component.literal("Level & Rewards"), 4, m -> render(m, player));
    }

    /** Also used by /level to print a summary in chat. */
    public static List<String> summary(PlayerData d) {
        LevelCurve c = Config.curve();
        long into = d.xp - c.totalForLevel(d.level);
        long need = c.xpToNext(d.level);
        List<String> out = new ArrayList<>();
        out.add("Level " + d.level + " | Total XP: " + String.format("%,d", d.xp));
        if (d.level >= c.maxLevel()) out.add("Maximum level reached.");
        else out.add("Progress: [" + Ui.bar(into, need, 20) + "] " + String.format("%,d / %,d", into, need) + " XP to level " + (d.level + 1));
        return out;
    }

    private static void render(GuiMenu m, ServerPlayer p) {
        m.clear();
        PlayerData d = DataStore.get(p);
        LevelCurve c = Config.curve();
        long into = d.xp - c.totalForLevel(d.level);
        long need = c.xpToNext(d.level);
        boolean max = d.level >= c.maxLevel();

        m.set(4, Ui.icon(Items.EXPERIENCE_BOTTLE, Ui.text("Level " + d.level, ChatFormatting.GREEN, ChatFormatting.BOLD),
                List.of(Ui.text("Total XP: " + String.format("%,d", d.xp), ChatFormatting.WHITE),
                        max ? Ui.gray("Maximum level reached") : Ui.gray("XP to next level: " + String.format("%,d", Math.max(0, need - into)))), true), null);

        int filled = max ? 9 : (int) Math.floor(9.0 * Math.min(1.0, (double) into / need));
        for (int i = 0; i < 9; i++) {
            Item pane = i < filled ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE;
            m.set(9 + i, Ui.icon(pane, Ui.text(max ? "MAX" : String.format("%,d / %,d XP", into, need), ChatFormatting.WHITE), null, false), null);
        }

        List<Config.Milestone> ms = new ArrayList<>(Config.get().milestones);
        ms.sort((a, b) -> Integer.compare(a.level, b.level));
        int nextLevel = Integer.MAX_VALUE;
        for (Config.Milestone ms1 : ms) if (!d.claimedMilestones.contains(ms1.level) && ms1.level < nextLevel) nextLevel = ms1.level;
        for (int i = 0; i < ms.size() && i < 9; i++) {
            Config.Milestone x = ms.get(i);
            boolean claimed = d.claimedMilestones.contains(x.level);
            List<Component> lore = new ArrayList<>();
            lore.add(Ui.gray(x.title));
            if (x.money > 0) lore.add(Ui.text("Reward: " + Money.format(x.money), ChatFormatting.GOLD));
            for (Config.ItemGrant g : x.items) lore.add(Ui.text("Reward: " + g.count + "x " + g.item.replace("minecraft:", "").replace('_', ' '), ChatFormatting.GOLD));
            lore.add(claimed ? Ui.text("CLAIMED", ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Ui.text(x.level == nextLevel ? "NEXT MILESTONE" : "Locked", x.level == nextLevel ? ChatFormatting.YELLOW : ChatFormatting.RED));
            Item icon = !x.items.isEmpty() ? (x.items.get(0).item.contains("netherite") ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE)
                    : x.money >= 500_000 ? Items.EMERALD_BLOCK : x.money >= 50_000 ? Items.GOLD_BLOCK : Items.GOLD_INGOT;
            m.set(18 + i, Ui.icon(icon, Ui.text("Level " + x.level, claimed ? ChatFormatting.GREEN : ChatFormatting.YELLOW), lore, claimed), null);
        }

        m.set(27, Ui.icon(Items.GOLD_INGOT, Ui.text("Balance: " + Money.format(Economy.balance(p)), ChatFormatting.GOLD), null, false), null);
        int pending = LevelService.pendingCount(d);
        List<Component> pl = new ArrayList<>();
        pl.add(Ui.gray(pending > 0 ? pending + " item(s) are waiting for inventory space." : "No stored rewards."));
        pl.add(Ui.gray("Click to move stored rewards into your inventory."));
        m.set(31, Ui.icon(pending > 0 ? Items.ENDER_CHEST : Items.CHEST, Ui.text("Stored rewards", ChatFormatting.AQUA), pl, pending > 0), (pp, b, t) -> {
            int n = LevelService.deliverPending(pp);
            if (n > 0) pp.sendSystemMessage(Component.literal("Received " + n + " stored reward item(s).").withStyle(ChatFormatting.GREEN));
            render(m, pp);
            m.broadcastChanges();
        });
        List<Component> hist = new ArrayList<>();
        List<String> h = d.history;
        for (int i = Math.max(0, h.size() - 10); i < h.size(); i++) hist.add(Ui.gray(h.get(i)));
        if (hist.isEmpty()) hist.add(Ui.gray("Nothing yet."));
        m.set(35, Ui.icon(Items.BOOK, Ui.text("Recent activity", ChatFormatting.WHITE), hist, false), null);
        m.broadcastChanges();
    }
}
