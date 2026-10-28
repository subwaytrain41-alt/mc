package com.example.ecomod.gui;

import com.example.ecomod.Config;
import com.example.ecomod.DataStore;
import com.example.ecomod.Economy;
import com.example.ecomod.PlayerData;
import com.example.ecomod.QuestDef;
import com.example.ecomod.Quests;
import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class QuestGui {
    private QuestGui() {}

    public static void open(ServerPlayer player) {
        GuiMenu.open(player, Component.literal("Quests"), 4, m -> render(m, player));
    }

    private static ChatFormatting color(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> ChatFormatting.GREEN;
            case "MEDIUM" -> ChatFormatting.YELLOW;
            case "HARD" -> ChatFormatting.GOLD;
            case "LEGENDARY" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.RED;
        };
    }

    private static void render(GuiMenu m, ServerPlayer p) {
        m.clear();
        PlayerData d = DataStore.get(p);
        List<QuestDef> quests = Quests.all();
        for (int i = 0; i < quests.size() && i < 27; i++) {
            QuestDef q = quests.get(i);
            Quests.State st = Quests.state(p, d, q);
            long prog = Quests.progress(p, d, q);
            List<Component> lore = new ArrayList<>();
            lore.add(Ui.text(q.difficulty, color(q.difficulty), ChatFormatting.BOLD));
            lore.add(Ui.gray(q.description));
            lore.add(Ui.text("[" + Ui.bar(Math.min(prog, q.amount), q.amount, 20) + "] " + Math.min(prog, q.amount) + "/" + q.amount, ChatFormatting.WHITE));
            lore.add(Ui.text("Reward: " + Money.format(q.money) + " + " + q.xp + " XP", ChatFormatting.GOLD));
            lore.add(Ui.gray(q.repeatable ? "Repeatable (cooldown " + Ui.duration(q.cooldownMinutes * 60_000L) + ")" : "One-time quest"));
            int times = d.completions.getOrDefault(q.id, 0);
            if (times > 0) lore.add(Ui.gray("Completed " + times + "x"));
            switch (st) {
                case READY -> lore.add(Ui.text("READY - click to claim!", ChatFormatting.GREEN, ChatFormatting.BOLD));
                case COOLDOWN -> lore.add(Ui.text("On cooldown: " + Ui.duration(d.cooldownUntil.getOrDefault(q.id, 0L) - System.currentTimeMillis()), ChatFormatting.RED));
                case DONE -> lore.add(Ui.text("Completed", ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
                default -> lore.add(Ui.gray("In progress"));
            }
            Item icon = BuiltInRegistries.ITEM.getOptional(Identifier.parse(q.icon)).orElse(Items.PAPER);
            m.set(i, Ui.icon(icon, Ui.text(q.name, color(q.difficulty)), lore, st == Quests.State.READY), (pl, b, t) -> {
                Quests.claim(pl, q);
                render(m, pl);
                m.broadcastChanges();
            });
        }
        m.set(27, Ui.icon(Items.EXPERIENCE_BOTTLE, Ui.text("Level & rewards", ChatFormatting.GREEN), null, false), (pl, b, t) -> LevelGui.open(pl));
        m.set(31, Ui.icon(Items.CHEST, Ui.text("Claim all ready quests", ChatFormatting.GREEN), null, false), (pl, b, t) -> {
            int n = Quests.claimAll(pl);
            if (n == 0) pl.sendSystemMessage(Component.literal("No quests are ready to claim.").withStyle(ChatFormatting.YELLOW));
            render(m, pl);
            m.broadcastChanges();
        });
        m.set(35, Ui.icon(Items.GOLD_INGOT, Ui.text("Balance: " + Money.format(Economy.balance(p)), ChatFormatting.GOLD), null, false), null);
        m.broadcastChanges();
    }
}
