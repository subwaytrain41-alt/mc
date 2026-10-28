package com.example.ecomod;

import com.example.ecomod.core.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Quest tracking and reward claiming. All progress is computed on the server from vanilla stats/advancements. */
public final class Quests {
    private static List<QuestDef> quests = new ArrayList<>();
    private static final Set<String> NOTIFIED = new HashSet<>(); // "uuid:questId", transient

    private Quests() {}

    public static List<QuestDef> all() { return quests; }

    public static void load() {
        quests = Config.loadQuests();
        // Safety net: no quest may be both repeatable and advancement-based, and ids must be unique.
        Set<String> seen = new HashSet<>();
        quests.removeIf(q -> q.id == null || !seen.add(q.id));
        for (QuestDef q : quests) {
            if ("ADV".equals(q.type)) q.repeatable = false;
            if (q.amount < 1) q.amount = 1;
            if (q.money < 0) q.money = 0;
            if (q.xp < 0) q.xp = 0;
        }
        EconomyMod.LOGGER.info("Loaded {} quests", quests.size());
    }

    public static void ensureBaselines(ServerPlayer sp, PlayerData d) {
        for (QuestDef q : quests) {
            if (!"ADV".equals(q.type) && !d.baselines.containsKey(q.id)) {
                d.baselines.put(q.id, current(sp, d, q));
            }
        }
    }

    public static long progress(ServerPlayer sp, PlayerData d, QuestDef q) {
        long cur = current(sp, d, q);
        if ("ADV".equals(q.type)) return Math.min(cur, q.amount);
        return Math.max(0, cur - d.baselines.getOrDefault(q.id, 0L));
    }

    private static long current(ServerPlayer sp, PlayerData d, QuestDef q) {
        long total = 0;
        switch (q.type) {
            case "KILL" -> {
                for (String t : q.targets) {
                    if (t.equals("@monster")) {
                        for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
                            if (et.getCategory() == MobCategory.MONSTER) total += sp.getStats().getValue(Stats.ENTITY_KILLED.get(et));
                        }
                    } else {
                        Optional<EntityType<?>> et = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(t));
                        if (et.isPresent()) total += sp.getStats().getValue(Stats.ENTITY_KILLED.get(et.get()));
                    }
                }
            }
            case "CRAFT" -> {
                for (String t : q.targets) {
                    Optional<Item> it = BuiltInRegistries.ITEM.getOptional(Identifier.parse(t));
                    if (it.isPresent()) total += sp.getStats().getValue(Stats.ITEM_CRAFTED.get(it.get()));
                }
            }
            case "MINE" -> {
                for (String t : q.targets) {
                    if (t.startsWith("#")) {
                        TagKey<Block> tag = TagKey.create(Registries.BLOCK, Identifier.parse(t.substring(1)));
                        for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                            total += sp.getStats().getValue(Stats.BLOCK_MINED.get(h.value()));
                        }
                    } else {
                        Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(t));
                        if (b.isPresent()) total += sp.getStats().getValue(Stats.BLOCK_MINED.get(b.get()));
                    }
                }
            }
            case "STAT" -> {
                if (!q.targets.isEmpty()) total = sp.getStats().getValue(Stats.CUSTOM.get(Identifier.parse(q.targets.get(0))));
            }
            case "CROPS" -> total = d.cropsHarvested;
            case "ADV" -> {
                for (String t : q.targets) {
                    AdvancementHolder h = sp.level().getServer().getAdvancements().get(Identifier.parse(t));
                    if (h != null && sp.getAdvancements().getOrStartProgress(h).isDone()) total++;
                }
            }
            default -> {
            }
        }
        return total;
    }

    public enum State { IN_PROGRESS, READY, COOLDOWN, DONE }

    public static State state(ServerPlayer sp, PlayerData d, QuestDef q) {
        if (!q.repeatable && d.claimedOnce.contains(q.id)) return State.DONE;
        long until = d.cooldownUntil.getOrDefault(q.id, 0L);
        if (q.repeatable && until > System.currentTimeMillis()) return State.COOLDOWN;
        return progress(sp, d, q) >= q.amount ? State.READY : State.IN_PROGRESS;
    }

    /** Claims one quest. State is re-validated here, so a stale GUI can never double-pay. */
    public static boolean claim(ServerPlayer sp, QuestDef q) {
        PlayerData d = DataStore.get(sp);
        if (state(sp, d, q) != State.READY) {
            sp.sendSystemMessage(Component.literal("That quest is not ready to claim.").withStyle(ChatFormatting.RED));
            return false;
        }
        // Flip all state BEFORE paying out.
        if (q.repeatable) {
            d.baselines.put(q.id, current(sp, d, q));
            d.cooldownUntil.put(q.id, System.currentTimeMillis() + q.cooldownMinutes * 60_000L);
        } else {
            d.claimedOnce.add(q.id);
        }
        d.completions.merge(q.id, 1, Integer::sum);
        NOTIFIED.remove(sp.getUUID() + ":" + q.id);
        Economy.deposit(sp, q.money);
        d.log("Quest " + q.name + ": " + Money.format(q.money) + " + " + q.xp + " XP");
        sp.sendSystemMessage(Component.literal("Quest complete: " + q.name + "! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("+" + Money.format(q.money) + " and +" + q.xp + " XP").withStyle(ChatFormatting.YELLOW)));
        LevelService.addXp(sp, q.xp, "quest");
        DataStore.save(sp.getUUID());
        return true;
    }

    public static int claimAll(ServerPlayer sp) {
        PlayerData d = DataStore.get(sp);
        int n = 0;
        for (QuestDef q : quests) {
            if (state(sp, d, q) == State.READY && claim(sp, q)) n++;
        }
        return n;
    }

    /** Called periodically: tells the player once when a quest becomes claimable. */
    public static void notifyReady(ServerPlayer sp) {
        PlayerData d = DataStore.get(sp);
        for (QuestDef q : quests) {
            if (state(sp, d, q) == State.READY && NOTIFIED.add(sp.getUUID() + ":" + q.id)) {
                sp.sendSystemMessage(Component.literal("Quest ready: " + q.name + " - open /quests to claim your reward.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    public static void forget(UUID id) {
        NOTIFIED.removeIf(s -> s.startsWith(id.toString()));
    }
}
