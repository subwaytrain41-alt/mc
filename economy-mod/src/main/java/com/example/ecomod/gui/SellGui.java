package com.example.ecomod.gui;

import com.example.ecomod.DataStore;
import com.example.ecomod.Economy;
import com.example.ecomod.Prices;
import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Sell screen. Put items into the top 45 slots and press "Sell deposited items", or use "Sell everything"
 * for the whole inventory (two-click confirmation). Everything is priced and paid server-side.
 */
public final class SellGui {
    private final GuiMenu menu;
    private long pendingAllTotal = -1;
    private long pendingAllAt;

    private SellGui(GuiMenu menu) {
        this.menu = menu;
    }

    public static void open(ServerPlayer player) {
        GuiMenu.open(player, Component.literal("Sell Items"), 6, m -> {
            SellGui g = new SellGui(m);
            m.deposit(0, 45, () -> g.render(player));
            g.render(player);
        });
    }

    private long depositedTotal(int[] ineligible) {
        long total = 0;
        for (int i = menu.depositStart(); i < menu.depositEnd(); i++) {
            ItemStack s = menu.container().getItem(i);
            if (s.isEmpty()) continue;
            long v = Prices.sellValue(s);
            if (v < 0) ineligible[0]++;
            else total = Math.min(Money.MAX_BALANCE, total + v);
        }
        return total;
    }

    private long inventoryTotal(ServerPlayer p) {
        long total = 0;
        for (int i = 0; i < 36; i++) { // main inventory + hotbar only; armor and offhand are never sold
            long v = Prices.sellValue(p.getInventory().getItem(i));
            if (v > 0) total = Math.min(Money.MAX_BALANCE, total + v);
        }
        return total;
    }

    private void render(ServerPlayer p) {
        int[] bad = {0};
        long total = depositedTotal(bad);
        long all = inventoryTotal(p);
        List<Component> info = new ArrayList<>();
        info.add(Ui.gray("Place items in the slots above."));
        info.add(Ui.gray("Items sell for " + com.example.ecomod.Config.get().sellPercent + "% of their shop price."));
        info.add(Ui.gray("Enchanted, renamed and custom items cannot be sold."));
        if (bad[0] > 0) info.add(Ui.text(bad[0] + " stack(s) can't be sold and will be returned.", ChatFormatting.RED));
        menu.set(45, Ui.icon(Items.PAPER, Ui.text("How it works", ChatFormatting.WHITE), info, false), null);
        menu.set(47, Ui.icon(Items.EMERALD, Ui.text("Sell deposited items", ChatFormatting.GREEN),
                List.of(Ui.text("You will receive: " + Money.format(total), ChatFormatting.GOLD)), total > 0), (pl, b, t) -> sellDeposited(pl));
        boolean armed = pendingAllTotal == all && all > 0 && System.currentTimeMillis() - pendingAllAt < 10_000;
        menu.set(48, Ui.icon(Items.GOLD_BLOCK, Ui.text(armed ? "CLICK AGAIN TO CONFIRM" : "Sell everything in inventory", armed ? ChatFormatting.RED : ChatFormatting.GREEN),
                List.of(Ui.text("Total: " + Money.format(all), ChatFormatting.GOLD),
                        Ui.gray("Sells every eligible item in your"), Ui.gray("main inventory and hotbar.")), armed), (pl, b, t) -> sellAll(pl));
        menu.set(49, Ui.icon(Items.GOLD_INGOT, Ui.text("Balance: " + Money.format(Economy.balance(p)), ChatFormatting.GOLD), null, false), null);
        menu.set(51, Ui.icon(Items.CHEST, Ui.text("Open shop", ChatFormatting.AQUA), null, false), (pl, b, t) -> ShopGui.open(pl, ""));
        menu.set(53, Ui.icon(Items.BARRIER, Ui.text("Close", ChatFormatting.RED), null, false), (pl, b, t) -> pl.closeContainer());
        menu.broadcastChanges();
    }

    private void sellDeposited(ServerPlayer p) {
        long total = 0;
        int stacks = 0;
        // Remove first, then pay: the same stack can never be paid twice.
        for (int i = menu.depositStart(); i < menu.depositEnd(); i++) {
            ItemStack s = menu.container().getItem(i);
            long v = Prices.sellValue(s);
            if (v <= 0) continue;
            menu.container().setItem(i, ItemStack.EMPTY);
            total = Math.min(Money.MAX_BALANCE, total + v);
            stacks++;
        }
        finish(p, total, stacks);
    }

    private void sellAll(ServerPlayer p) {
        long all = inventoryTotal(p);
        long now = System.currentTimeMillis();
        if (all <= 0) {
            p.sendSystemMessage(Component.literal("You have nothing sellable in your inventory.").withStyle(ChatFormatting.RED));
            return;
        }
        if (pendingAllTotal != all || now - pendingAllAt >= 10_000) {
            pendingAllTotal = all;
            pendingAllAt = now;
            p.sendSystemMessage(Component.literal("Selling everything would pay " + Money.format(all) + ". Click again to confirm.").withStyle(ChatFormatting.YELLOW));
            render(p);
            return;
        }
        pendingAllTotal = -1;
        long total = 0;
        int stacks = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            long v = Prices.sellValue(s);
            if (v <= 0) continue;
            p.getInventory().setItem(i, ItemStack.EMPTY);
            total = Math.min(Money.MAX_BALANCE, total + v);
            stacks++;
        }
        finish(p, total, stacks);
    }

    private void finish(ServerPlayer p, long total, int stacks) {
        if (total <= 0) {
            p.sendSystemMessage(Component.literal("Nothing to sell.").withStyle(ChatFormatting.RED));
            render(p);
            return;
        }
        Economy.deposit(p, total);
        DataStore.get(p).log("Sold " + stacks + " stack(s) for " + Money.format(total));
        p.sendSystemMessage(Component.literal("Sold " + stacks + " stack(s) for " + Money.format(total) + ". Balance: "
                + Money.format(Economy.balance(p))).withStyle(ChatFormatting.GREEN));
        render(p);
    }
}
