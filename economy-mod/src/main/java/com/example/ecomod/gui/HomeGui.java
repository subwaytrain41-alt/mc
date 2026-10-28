package com.example.ecomod.gui;

import com.example.ecomod.Config;
import com.example.ecomod.DataStore;
import com.example.ecomod.Homes;
import com.example.ecomod.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;

public final class HomeGui {
    private String pendingDelete = "";
    private long pendingAt;

    private HomeGui() {}

    public static void open(ServerPlayer player) {
        GuiMenu.open(player, Component.literal("Homes"), 3, m -> new HomeGui().render(m, player));
    }

    private void render(GuiMenu m, ServerPlayer p) {
        m.clear();
        PlayerData d = DataStore.get(p);
        int max = Config.get().maxHomes;
        int[] slots = {11, 13, 15, 10, 12, 14, 16, 9, 17}; // first three are the classic centred layout
        for (int i = 0; i < max && i < slots.length; i++) {
            int slot = slots[i];
            if (i < d.homes.size()) {
                PlayerData.Home h = d.homes.get(i);
                m.set(slot, Ui.icon(Items.RED_BED, Ui.text(h.name, ChatFormatting.GREEN, ChatFormatting.BOLD),
                        List.of(Ui.gray(Homes.describe(h)), Ui.gray(""), Ui.gray("Left-click: teleport"),
                                Ui.gray("Right-click twice: delete")), false), (pl, b, t) -> {
                    if (b == 1) {
                        long now = System.currentTimeMillis();
                        if (h.name.equals(pendingDelete) && now - pendingAt < 8_000) {
                            Homes.delete(pl, h.name);
                            pendingDelete = "";
                            render(m, pl);
                            m.broadcastChanges();
                        } else {
                            pendingDelete = h.name;
                            pendingAt = now;
                            pl.sendSystemMessage(Component.literal("Right-click '" + h.name + "' again to delete it.").withStyle(ChatFormatting.YELLOW));
                        }
                    } else {
                        pl.closeContainer();
                        Homes.teleport(pl, h.name);
                    }
                });
            } else {
                m.set(slot, Ui.icon(Items.GRAY_STAINED_GLASS_PANE, Ui.text("Empty home slot", ChatFormatting.GRAY),
                        List.of(Ui.gray("Stand where you want it and run"), Ui.gray("/sethome <name>")), false), null);
            }
        }
        m.set(22, Ui.icon(Items.PAPER, Ui.text("Homes used: " + d.homes.size() + " / " + max, ChatFormatting.WHITE),
                List.of(Ui.gray((max - d.homes.size()) + " slot(s) remaining")), false), null);
        m.broadcastChanges();
    }
}
