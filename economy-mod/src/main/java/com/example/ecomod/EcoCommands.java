package com.example.ecomod;

import com.example.ecomod.core.Money;
import com.example.ecomod.gui.HomeGui;
import com.example.ecomod.gui.LevelGui;
import com.example.ecomod.gui.QuestGui;
import com.example.ecomod.gui.SellGui;
import com.example.ecomod.gui.ShopGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Player commands are open to everyone. /eco (balance administration) requires gamemaster permission,
 * the same level as /give and /gamemode.
 */
public final class EcoCommands {
    private EcoCommands() {}

    private static int run(CommandContext<CommandSourceStack> c, Consumer<ServerPlayer> action) throws CommandSyntaxException {
        action.accept(c.getSource().getPlayerOrException());
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var balance = Commands.literal("balance").executes(c -> run(c, p ->
                p.sendSystemMessage(Component.literal("Balance: " + Money.format(Economy.balance(p))).withStyle(ChatFormatting.GOLD))));
        d.register(balance);
        d.register(Commands.literal("bal").redirect(d.getRoot().getChild("balance")));

        d.register(Commands.literal("shop")
                .executes(c -> run(c, p -> ShopGui.open(p, "")))
                .then(Commands.argument("search", StringArgumentType.greedyString())
                        .executes(c -> run(c, p -> ShopGui.open(p, StringArgumentType.getString(c, "search"))))));
        d.register(Commands.literal("sell").executes(c -> run(c, SellGui::open)));
        d.register(Commands.literal("quests").executes(c -> run(c, QuestGui::open)));
        d.register(Commands.literal("level").executes(c -> run(c, p -> {
            for (String line : LevelGui.summary(DataStore.get(p))) {
                p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GREEN));
            }
            LevelGui.open(p);
        })));
        d.register(Commands.literal("rewards").executes(c -> run(c, p -> {
            PlayerData data = DataStore.get(p);
            if (data.pendingItems.isEmpty()) {
                p.sendSystemMessage(Component.literal("You have no stored rewards.").withStyle(ChatFormatting.YELLOW));
            } else {
                int n = LevelService.deliverPending(p);
                if (n > 0) p.sendSystemMessage(Component.literal("Received " + n + " stored reward item(s).").withStyle(ChatFormatting.GREEN));
            }
        })));

        // ---- homes ----
        d.register(Commands.literal("homes").executes(c -> run(c, p -> {
            Homes.list(p);
            HomeGui.open(p);
        })));
        d.register(Commands.literal("sethome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> run(c, p -> Homes.set(p, StringArgumentType.getString(c, "name"))))));
        d.register(Commands.literal("home")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer sp) {
                                return SharedSuggestionProvider.suggest(DataStore.get(sp).homes.stream().map(h -> h.name), b);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> run(c, p -> Homes.teleport(p, StringArgumentType.getString(c, "name"))))));
        d.register(Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer sp) {
                                return SharedSuggestionProvider.suggest(DataStore.get(sp).homes.stream().map(h -> h.name), b);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> run(c, p -> Homes.delete(p, StringArgumentType.getString(c, "name"))))));

        // ---- admin ----
        d.register(Commands.literal("eco")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("balance").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            c.getSource().sendSuccess(() -> Component.literal(t.getName().getString() + " has " + Money.format(Economy.balance(t))), false);
                            return 1;
                        })))
                .then(adjust("give"))
                .then(adjust("take"))
                .then(adjust("set")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> adjust(String mode) {
        return Commands.literal(mode).then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(0, Money.MAX_BALANCE))
                        .executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            long amount = LongArgumentType.getLong(c, "amount");
                            PlayerData data = DataStore.get(t);
                            switch (mode) {
                                case "give" -> Economy.deposit(t, amount);
                                case "take" -> Economy.set(data, data.balance - Math.min(amount, data.balance));
                                default -> Economy.set(data, amount);
                            }
                            EconomyMod.LOGGER.info("[eco] {} {} {} -> balance {}", c.getSource().getTextName(), mode,
                                    t.getName().getString() + " " + amount, data.balance);
                            DataStore.save(t.getUUID());
                            c.getSource().sendSuccess(() -> Component.literal(t.getName().getString() + " now has " + Money.format(data.balance)), true);
                            return 1;
                        })));
    }
}
