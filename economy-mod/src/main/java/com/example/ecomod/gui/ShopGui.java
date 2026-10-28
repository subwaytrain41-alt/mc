package com.example.ecomod.gui;

import com.example.ecomod.Config;
import com.example.ecomod.DataStore;
import com.example.ecomod.Economy;
import com.example.ecomod.LevelService;
import com.example.ecomod.Prices;
import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Paginated, searchable, sortable shop. 45 item slots + a control row. */
public final class ShopGui {
    private enum Sort {
        NAME("Name (A-Z)"), PRICE_LOW("Price (low to high)"), PRICE_HIGH("Price (high to low)"),
        CATEGORY("Category"), RARITY("Rarity");

        final String label;

        Sort(String label) { this.label = label; }
    }

    private static final int PAGE = 45;

    private final GuiMenu menu;
    private Prices.Category category = Prices.Category.ALL;
    private Sort sort = Sort.NAME;
    private String query;
    private int page;
    private List<Prices.Entry> view = new ArrayList<>();

    // purchase confirmation state
    private String pendingKey = "";
    private long pendingAt;

    private ShopGui(GuiMenu menu, String query) {
        this.menu = menu;
        this.query = query == null ? "" : query.trim();
    }

    public static void open(ServerPlayer player, String query) {
        GuiMenu.open(player, Component.literal("Item Shop"), 6, m -> {
            ShopGui g = new ShopGui(m, query);
            g.refilter();
            g.render(player);
        });
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).replace(' ', '_');
        List<Prices.Entry> out = new ArrayList<>();
        for (Prices.Entry e : Prices.all()) {
            if (category != Prices.Category.ALL && e.category() != category) continue;
            if (!q.isEmpty() && !e.path().contains(q)) continue;
            out.add(e);
        }
        Comparator<Prices.Entry> byName = Comparator.comparing(Prices.Entry::path);
        switch (sort) {
            case NAME -> out.sort(byName);
            case PRICE_LOW -> out.sort(Comparator.comparingLong(Prices.Entry::buy).thenComparing(byName));
            case PRICE_HIGH -> out.sort(Comparator.comparingLong(Prices.Entry::buy).reversed().thenComparing(byName));
            case CATEGORY -> out.sort(Comparator.comparing((Prices.Entry e) -> e.category().ordinal()).thenComparing(byName));
            case RARITY -> out.sort(Comparator.comparing((Prices.Entry e) -> e.tier().ordinal()).thenComparing(Prices.Entry::buy).thenComparing(byName));
        }
        view = out;
        int maxPage = Math.max(0, (view.size() - 1) / PAGE);
        page = Math.min(page, maxPage);
    }

    private void render(ServerPlayer p) {
        menu.clear();
        int start = page * PAGE;
        for (int i = 0; i < PAGE && start + i < view.size(); i++) {
            Prices.Entry e = view.get(start + i);
            ItemStack icon = e.item().getDefaultInstance();
            List<Component> lore = new ArrayList<>();
            lore.add(Ui.text("Buy:  " + Money.format(e.buy()), ChatFormatting.GREEN));
            lore.add(Ui.text("Sell: " + Money.format(e.sell()), ChatFormatting.GOLD));
            lore.add(Ui.gray(e.category().label + " - " + e.tier().name().toLowerCase(Locale.ROOT)));
            lore.add(Ui.gray(""));
            lore.add(Ui.gray("Left-click: buy 1"));
            lore.add(Ui.gray("Right-click: buy 16"));
            lore.add(Ui.gray("Shift-click: buy a full stack"));
            Ui.withLore(icon, lore);
            menu.set(i, icon, (pl, button, type) -> buy(pl, e, button, type));
        }
        int maxPage = Math.max(0, (view.size() - 1) / PAGE);
        menu.set(45, Ui.icon(Items.ARROW, Ui.text("Previous page", ChatFormatting.YELLOW), null, false), (pl, b, t) -> {
            if (page > 0) { page--; render(pl); }
        });
        menu.set(46, Ui.icon(Items.CHEST, Ui.text("Category: " + category.label, ChatFormatting.AQUA),
                List.of(Ui.gray("Left-click: next category"), Ui.gray("Right-click: previous category")), false), (pl, b, t) -> {
            Prices.Category[] all = Prices.Category.values();
            int idx = (category.ordinal() + (b == 1 ? all.length - 1 : 1)) % all.length;
            category = all[idx];
            page = 0;
            refilter();
            render(pl);
        });
        menu.set(47, Ui.icon(Items.HOPPER, Ui.text("Sort: " + sort.label, ChatFormatting.AQUA),
                List.of(Ui.gray("Click to change sorting")), false), (pl, b, t) -> {
            Sort[] all = Sort.values();
            sort = all[(sort.ordinal() + 1) % all.length];
            refilter();
            render(pl);
        });
        menu.set(48, Ui.icon(Items.COMPASS, Ui.text(query.isEmpty() ? "Search" : "Search: " + query, ChatFormatting.AQUA),
                List.of(Ui.gray("Type /shop <text> to search"), Ui.gray("Click to clear the search")), false), (pl, b, t) -> {
            query = "";
            page = 0;
            refilter();
            render(pl);
        });
        menu.set(49, Ui.icon(Items.GOLD_INGOT, Ui.text("Balance: " + Money.format(Economy.balance(p)), ChatFormatting.GOLD),
                List.of(Ui.gray(view.size() + " items shown")), false), null);
        menu.set(50, Ui.icon(Items.EMERALD, Ui.text("Open sell screen", ChatFormatting.GREEN), null, false),
                (pl, b, t) -> SellGui.open(pl));
        menu.set(51, Ui.icon(Items.EXPERIENCE_BOTTLE, Ui.text("Level & rewards", ChatFormatting.GREEN), null, false),
                (pl, b, t) -> LevelGui.open(pl));
        menu.set(52, Ui.icon(Items.PAPER, Ui.text("Page " + (page + 1) + " / " + (maxPage + 1), ChatFormatting.WHITE), null, false), null);
        menu.set(53, Ui.icon(Items.ARROW, Ui.text("Next page", ChatFormatting.YELLOW), null, false), (pl, b, t) -> {
            if (page < maxPage) { page++; render(pl); }
        });
        menu.broadcastChanges();
    }

    private void buy(ServerPlayer p, Prices.Entry e, int button, ClickType type) {
        int qty = type == ClickType.QUICK_MOVE ? e.item().getDefaultMaxStackSize() : button == 1 ? 16 : 1;
        qty = Math.max(1, Math.min(qty, e.item().getDefaultMaxStackSize()));

        // Server recomputes the cost from the registry; nothing the client sends is trusted.
        long total = Money.safeMul(e.buy(), qty);
        if (total <= 0 || total >= Money.MAX_BALANCE) return;

        if (!Economy.canAfford(p, total)) {
            p.sendSystemMessage(Component.literal("Not enough money: " + qty + "x item costs " + Money.format(total)
                    + ", you have " + Money.format(Economy.balance(p)) + ".").withStyle(ChatFormatting.RED));
            return;
        }
        String key = e.id() + "x" + qty;
        long now = System.currentTimeMillis();
        if (total >= Config.get().confirmPurchaseAbove && !(key.equals(pendingKey) && now - pendingAt < 10_000)) {
            pendingKey = key;
            pendingAt = now;
            p.sendSystemMessage(Component.literal("Click again within 10 seconds to confirm buying " + qty + "x "
                    + e.path().replace('_', ' ') + " for " + Money.format(total) + ".").withStyle(ChatFormatting.YELLOW));
            return;
        }
        pendingKey = "";

        if (!Economy.withdraw(p, total)) return;
        int left = qty;
        while (left > 0) {
            int n = Math.min(left, e.item().getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(e.item(), n);
            p.getInventory().add(stack);
            if (!stack.isEmpty()) p.drop(stack, false); // inventory full: item lands at the player's feet, never lost
            left -= n;
        }
        DataStore.get(p).log("Bought " + qty + "x " + e.path() + " for " + Money.format(total));
        p.sendSystemMessage(Component.literal("Bought " + qty + "x " + e.path().replace('_', ' ') + " for " + Money.format(total)
                + ". Balance: " + Money.format(Economy.balance(p))).withStyle(ChatFormatting.GREEN));
        render(p);
    }
}
