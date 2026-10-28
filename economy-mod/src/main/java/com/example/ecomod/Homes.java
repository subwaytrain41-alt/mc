package com.example.ecomod;

import com.example.ecomod.core.Names;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Homes {
    private static final Map<UUID, Long> LAST_TELEPORT = new HashMap<>();

    private Homes() {}

    private static void err(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
    }

    private static PlayerData.Home find(PlayerData d, String name) {
        for (PlayerData.Home h : d.homes) if (h.name.equalsIgnoreCase(name)) return h;
        return null;
    }

    public static boolean set(ServerPlayer p, String name) {
        PlayerData d = DataStore.get(p);
        if (!Names.validHomeName(name)) {
            err(p, "Invalid home name. Use 1-16 letters, digits, '_' or '-'.");
            return false;
        }
        if (find(d, name) != null) {
            err(p, "You already have a home called '" + name + "'. Delete it first with /delhome " + name + ".");
            return false;
        }
        int max = Config.get().maxHomes;
        if (d.homes.size() >= max) {
            err(p, "You already have " + max + " homes. Delete one with /delhome <name> first.");
            return false;
        }
        PlayerData.Home h = new PlayerData.Home();
        h.name = name;
        h.dimension = p.level().dimension().identifier().toString();
        h.x = p.getX();
        h.y = p.getY();
        h.z = p.getZ();
        h.yaw = p.getYRot();
        h.pitch = p.getXRot();
        d.homes.add(h);
        DataStore.save(p.getUUID());
        p.sendSystemMessage(Component.literal("Home '" + name + "' set (" + d.homes.size() + "/" + max + ").").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean delete(ServerPlayer p, String name) {
        PlayerData d = DataStore.get(p);
        PlayerData.Home h = find(d, name);
        if (h == null) {
            err(p, "You have no home called '" + name + "'. Use /homes to list them.");
            return false;
        }
        d.homes.remove(h);
        DataStore.save(p.getUUID());
        p.sendSystemMessage(Component.literal("Home '" + h.name + "' deleted.").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public static void list(ServerPlayer p) {
        PlayerData d = DataStore.get(p);
        int max = Config.get().maxHomes;
        if (d.homes.isEmpty()) {
            p.sendSystemMessage(Component.literal("You have no homes (0/" + max + "). Use /sethome <name>.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        p.sendSystemMessage(Component.literal("Your homes (" + d.homes.size() + "/" + max + "):").withStyle(ChatFormatting.GOLD));
        for (PlayerData.Home h : d.homes) {
            p.sendSystemMessage(Component.literal(" - " + h.name + ": " + describe(h)).withStyle(ChatFormatting.GRAY));
        }
    }

    public static String describe(PlayerData.Home h) {
        return String.format("%s  x=%d y=%d z=%d", h.dimension, (int) Math.floor(h.x), (int) Math.floor(h.y), (int) Math.floor(h.z));
    }

    public static boolean teleport(ServerPlayer p, String name) {
        PlayerData d = DataStore.get(p);
        PlayerData.Home h = find(d, name);
        if (h == null) {
            err(p, "You have no home called '" + name + "'. Use /homes to list them.");
            return false;
        }
        long now = System.currentTimeMillis();
        long wait = Config.get().homeCooldownSeconds * 1000L - (now - LAST_TELEPORT.getOrDefault(p.getUUID(), 0L));
        if (wait > 0) {
            err(p, "Please wait " + ((wait + 999) / 1000) + "s before teleporting again.");
            return false;
        }

        ServerLevel target;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(h.dimension));
            target = p.level().getServer().getLevel(key);
        } catch (RuntimeException e) {
            target = null;
        }
        if (target == null) {
            err(p, "Home '" + h.name + "' is in a dimension that is not available (" + h.dimension + ").");
            return false;
        }

        double y = findSafeY(target, h.x, h.y, h.z);
        if (Double.isNaN(y)) {
            err(p, "Home '" + h.name + "' is no longer safe (blocked or over lava/void). Set it again with /sethome.");
            return false;
        }
        boolean ok;
        try {
            ok = p.teleportTo(target, h.x, y, h.z, Set.<Relative>of(), h.yaw, h.pitch, true);
        } catch (RuntimeException e) {
            EconomyMod.LOGGER.error("Home teleport failed for {}", p.getName().getString(), e);
            ok = false;
        }
        if (!ok) {
            err(p, "Teleport failed. Try again in a moment.");
            return false;
        }
        LAST_TELEPORT.put(p.getUUID(), now);
        p.sendSystemMessage(Component.literal("Teleported to home '" + h.name + "'.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Looks a few blocks up/down from the saved spot for a non-solid, non-lava, supported 2-block-high space. */
    private static double findSafeY(ServerLevel level, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        int[] offsets = {0, 1, 2, 3, -1, -2, -3};
        int minY = level.getMinY(), maxY = level.getMaxY();
        for (int off : offsets) {
            int yy = by + off;
            if (yy <= minY + 1 || yy >= maxY - 2) continue;
            if (isSafe(level, new BlockPos(bx, yy, bz))) return off == 0 ? y : yy;
        }
        return Double.NaN;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        BlockState below = level.getBlockState(feet.below());
        if (!at.getCollisionShape(level, feet).isEmpty()) return false;
        if (!head.getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (at.getFluidState().is(FluidTags.LAVA) || head.getFluidState().is(FluidTags.LAVA)) return false;
        if (at.is(BlockTags.FIRE) || head.is(BlockTags.FIRE) || at.is(Blocks.CACTUS)) return false;
        if (below.getFluidState().is(FluidTags.LAVA) || below.is(BlockTags.FIRE) || below.is(Blocks.MAGMA_BLOCK)
                || below.is(Blocks.CACTUS) || below.is(Blocks.SWEET_BERRY_BUSH)) return false;
        boolean supported = below.isFaceSturdy(level, feet.below(), Direction.UP) || below.getFluidState().is(FluidTags.WATER);
        return supported;
    }
}
