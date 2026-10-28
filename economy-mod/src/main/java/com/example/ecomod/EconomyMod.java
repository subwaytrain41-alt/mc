package com.example.ecomod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EconomyMod implements ModInitializer {
    public static final String MOD_ID = "ecomod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private int tick;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EcoCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Config.load();
            DataStore.init(server);
            Prices.init(server);
            Quests.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DataStore.saveAll());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerData d = DataStore.get(p);
            if (!d.initialized) {
                d.initialized = true;
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Welcome! Use /shop, /sell, /quests, /level, /homes and /balance."));
            }
            Quests.ensureBaselines(p, d);
            LevelService.recalc(p, d); // picks up config changes (new milestones) and delivers stored items
            DataStore.save(p.getUUID());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.getPlayer();
            Quests.forget(p.getUUID());
            DataStore.unload(p.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 100 == 0) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) Quests.notifyReady(p);
            }
            if (tick % 6000 == 0) DataStore.saveAll(); // every 5 minutes
        });

        // Gameplay XP from hostile kills and bosses (capped per day, see config).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer p)) return;
            Config c = Config.get();
            if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
                LevelService.addXp(p, c.xpPerBossKill, "boss");
            } else if (entity instanceof Enemy) {
                LevelService.addActivityXp(p, c.xpPerHostileKill);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer p)) return;
            countCrop(p, state);
            if (isOre(state)) LevelService.addActivityXp(p, Config.get().xpPerOreMined);
        });
    }

    /** Only fully grown crops count; breaking seedlings never advances the farming quest. */
    private static void countCrop(ServerPlayer p, BlockState state) {
        boolean mature = false;
        if (state.getBlock() instanceof CropBlock crop) mature = crop.isMaxAge(state);
        else if (state.is(Blocks.NETHER_WART)) mature = state.getValue(NetherWartBlock.AGE) >= 3;
        else if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) mature = true;
        if (mature) DataStore.get(p).cropsHarvested++;
    }

    private static boolean isOre(BlockState s) {
        return s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.REDSTONE_ORES)
                || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES) || s.is(Blocks.ANCIENT_DEBRIS);
    }
}
