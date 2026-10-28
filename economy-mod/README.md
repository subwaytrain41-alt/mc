# Complete Economy (Fabric, Minecraft Java 26.3)

**Status: source code only. It has NOT been compiled against Minecraft 26.3, and no `.jar` exists.**
Read "What was and was not verified" before relying on anything here.

## What was and was not verified

Verified (public sources, checked 27 Oct 2026):
- Minecraft Java 26.3 ("Wilderness Bound") is an official release, dated 15 September 2026; it requires Java 25.
- Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3 are published for 26.3 (other mods list these exact versions).
- Fabric, Forge, NeoForge (beta) and Quilt all list 26.3. Fabric was chosen because 26.3 support is listed as stable.

Tested here (pure Java, no Minecraft): `core/` - pricing rules, sell formula, money formatting, XP curve, home-name
validation. 384 checks pass (tool hierarchy for every material, dirt = $1, elytra = $50,000,000, netherite items inside
$1M-$2M, sell = 60% with $1 minimum, strictly increasing XP curve, name validation).

NOT verified (the build sandbox could not reach Mojang, Fabric or Maven Central, so nothing touching Minecraft's API was
compiled or run): everything outside `core/`. Specifically unverified: that the project compiles, that the jar loads, GUI
behaviour, quest tracking, level rewards in-game, home teleports, multiplayer separation, restart persistence.
Names I am least sure of in 26.x: `Identifier` (renamed from `ResourceLocation`), `ResourceKey#identifier()`,
`ServerPlayer#teleportTo(..., Set<Relative>, ...)`, `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`, and the
Loom plugin version in `gradle.properties` (`loom_version` is a placeholder - take the current one from fabricmc.net/develop).
Expect to fix a handful of compile errors on first build.

## Build

```
# Java 25 required
./gradlew build          # (generate the wrapper first with: gradle wrapper)
# jar: build/libs/ecomod-1.0.0.jar
```

Run the core tests without Gradle:

```
javac -d out src/main/java/com/example/ecomod/core/*.java src/test/java/com/example/ecomod/core/CoreTests.java
java -cp out com.example.ecomod.core.CoreTests
```

## Install

The GUIs are server-side chest menus, so **only the server needs the mod**; vanilla clients can join.
- Single player: install Fabric Loader 0.19.5+ for 26.3, put the jar and Fabric API (0.161.0+26.3) in `mods/`.
- Dedicated server: install the Fabric server for 26.3 (Java 25), put the jar and Fabric API in `mods/`, start once.

First start writes `config/ecomod/config.json`, `prices.json` (every item and its price) and `quests.json`.
Player data lives in `<world>/ecomod/players/<uuid>.json` (written on every claim/home change, on disconnect,
every 5 minutes and on shutdown).

## Commands

| Command | Who | Notes |
|---|---|---|
| `/balance`, `/bal` | everyone | |
| `/shop [search]` | everyone | left-click buy 1, right-click 16, shift-click full stack |
| `/sell` | everyone | deposit slots or "sell everything" (two-click confirm) |
| `/quests` | everyone | click a ready quest to claim |
| `/level` | everyone | chat summary + progression screen |
| `/rewards` | everyone | moves stored reward items into the inventory |
| `/homes`, `/sethome <name>`, `/home <name>`, `/delhome <name>` | everyone | 3 homes max (configurable) |
| `/eco balance\|give\|take\|set <player> [amount]` | gamemasters (op level 2) | online players only; logged |

## Design notes

- **Prices**: `PriceRules` gives rule-based defaults (dirt $1, netherite $1M-$2M, elytra $50M, hoe < shovel < axe < sword
  < pickaxe per material). Items the rules don't know get a rarity-based fallback, so new game items are never unpriced.
  Edit any price in `prices.json`. Selling pays `sellPercent`% (default 60, capped below 100), minimum $1.
- **Not in the shop**: items with no single default form or not obtainable in survival (spawn eggs, command blocks,
  bedrock, spawners, potions/tipped arrows/enchanted books/goat horns/suspicious stew, etc.). Potion and enchanted-book
  variants are therefore not individually listed - a known gap against "every obtainable item".
- **Quests** (25, in `quests.json`): progress comes from vanilla statistics and advancements, computed server-side, with a
  baseline taken on first join so old stats are not paid out. "Obtain X ingots" counts items smelted/crafted, not items
  bought or picked up, so the shop cannot be used to complete it. Two of the example quests were adapted to be reliably
  trackable: Potion Master became "Enchanter" (brewing has no vanilla statistic) and Treasure Hunter uses sherd/vault
  advancements instead of counting chest opens (which is farmable).
- **$10,000,000 quest** ("Ultimate Challenge"): one-time only, advancement-based (visit every biome, kill every hostile
  type, tame every cat variant, eat every food). It cannot be bought from the shop and cannot repeat.
- **Levels**: XP to next = round(50 * level^1.5); level 50 needs about 345k total XP, level 100 about 1.98M (tested).
  Milestones: levels 5, 10 ($10,000), 25 ($50,000), 35, 50 (diamond armor), 60, 75 ($500,000), 90, 100 (netherite armor).
  Claim flag is set before paying. Items that do not fit stay in a stored queue (`/rewards` or the level screen).
- **Safety**: all prices and totals are recomputed on the server; deposit slots are returned on close; icon slots cannot
  be taken or filled; enchanted/renamed/custom-data items cannot be sold; repeatable activity XP has a daily cap.

## Known limitations

- Everything under "NOT verified" above.
- `/eco` works on online players only.
- If the server crashes while the sell screen is open, items in the deposit slots are lost (they are returned on a normal close).
- Gameplay XP (hostile kills, ore mining) is a simple daily-capped trickle; tune it in `config.json`.
