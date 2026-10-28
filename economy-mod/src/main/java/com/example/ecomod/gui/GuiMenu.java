package com.example.ecomod.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side chest GUI. Works with an unmodified vanilla client: the server simply opens a chest-type menu
 * and decides what every click means. Icon slots can never be taken or have items placed in them; only the
 * optional "deposit" range behaves like a real container.
 */
public class GuiMenu extends ChestMenu {
    @FunctionalInterface
    public interface Click {
        void on(ServerPlayer player, int button, ClickType type);
    }

    private final int topSize;
    private final SimpleContainer top;
    private final Map<Integer, Click> clicks = new HashMap<>();
    private int depositStart = -1;
    private int depositEnd = -1;
    private Runnable afterDepositChange = () -> {};

    private GuiMenu(int rows, int syncId, Inventory inv, SimpleContainer top) {
        super(typeFor(rows), syncId, inv, top, rows);
        this.top = top;
        this.topSize = rows * 9;
    }

    private static MenuType<ChestMenu> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** Opens a new menu (or replaces the current one). The builder receives the menu to fill. */
    public static GuiMenu[] open(ServerPlayer player, Component title, int rows, java.util.function.Consumer<GuiMenu> builder) {
        GuiMenu[] holder = new GuiMenu[1];
        player.openMenu(new SimpleMenuProvider((syncId, inv, p) -> {
            GuiMenu m = new GuiMenu(rows, syncId, inv, new SimpleContainer(rows * 9));
            builder.accept(m);
            holder[0] = m;
            return m;
        }, title));
        return holder;
    }

    public Container container() { return top; }

    public int size() { return topSize; }

    public void clear() {
        for (int i = 0; i < topSize; i++) {
            if (!isDeposit(i)) top.setItem(i, ItemStack.EMPTY);
        }
        clicks.clear();
    }

    public void set(int slot, ItemStack stack, Click click) {
        top.setItem(slot, stack);
        if (click != null) clicks.put(slot, click);
        else clicks.remove(slot);
    }

    public void deposit(int startInclusive, int endExclusive, Runnable afterChange) {
        this.depositStart = startInclusive;
        this.depositEnd = endExclusive;
        this.afterDepositChange = afterChange;
    }

    public boolean isDeposit(int slot) {
        return depositStart >= 0 && slot >= depositStart && slot < depositEnd;
    }

    public int depositStart() { return depositStart; }

    public int depositEnd() { return depositEnd; }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (type == ClickType.QUICK_CRAFT || type == ClickType.CLONE) return; // no dragging, no creative clone

        if (slotId >= 0 && slotId < topSize) {
            if (isDeposit(slotId)) {
                super.clicked(slotId, button, type, player);
                afterDepositChange.run();
                return;
            }
            Click c = clicks.get(slotId);
            if (c != null) c.on(sp, button, type);
            return; // icon slots never reach vanilla handling
        }
        super.clicked(slotId, button, type, player);
        if (depositStart >= 0) afterDepositChange.run();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot.container == top) return isDeposit(slot.index);
        return super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (depositStart < 0) return ItemStack.EMPTY; // locked menu: shift-click does nothing
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < topSize) {
            if (!isDeposit(index)) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stack, topSize, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, depositStart, depositEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (depositStart >= 0 && player instanceof ServerPlayer sp) {
            // Anything left in the deposit area goes back to the player; nothing is ever lost.
            for (int i = depositStart; i < depositEnd; i++) {
                ItemStack s = top.removeItemNoUpdate(i);
                if (!s.isEmpty()) {
                    sp.getInventory().add(s);
                    if (!s.isEmpty()) sp.drop(s, false);
                }
            }
        }
    }
}
