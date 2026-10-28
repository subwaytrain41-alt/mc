package com.example.ecomod.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Small helpers for building GUI icons. */
public final class Ui {
    private Ui() {}

    public static Component text(String s, ChatFormatting... fmt) {
        return Component.literal(s).withStyle(fmt).withStyle(st -> st.withItalic(false));
    }

    public static Component gray(String s) {
        return text(s, ChatFormatting.GRAY);
    }

    public static ItemStack icon(Item item, Component name, List<Component> lore, boolean glint) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, name);
        if (lore != null && !lore.isEmpty()) s.set(DataComponents.LORE, new ItemLore(new ArrayList<>(lore)));
        if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    /** Adds lore lines to an existing stack (used for shop items so the vanilla name/localisation is kept). */
    public static ItemStack withLore(ItemStack s, List<Component> lore) {
        s.set(DataComponents.LORE, new ItemLore(new ArrayList<>(lore)));
        return s;
    }

    public static String bar(long cur, long max, int len) {
        double f = max <= 0 ? 1 : Math.min(1.0, (double) cur / max);
        int filled = (int) Math.round(f * len);
        return "#".repeat(filled) + "-".repeat(len - filled);
    }

    public static String duration(long millis) {
        long mins = Math.max(0, millis / 60_000);
        long d = mins / 1440, h = (mins % 1440) / 60, m = mins % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return Math.max(1, m) + "m";
    }
}
