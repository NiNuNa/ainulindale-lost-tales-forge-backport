package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.LostTalesItemMissiveLetter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Writes a missive board's notices: kill-or-gather tasks built from the
 * objective types and selectors the quest runtime already handles. A
 * notice is written as template ids and a target, never as sentences
 * ({@link MissiveWords}), so each player reads it in their own language.
 */
public final class LostTalesMissiveGenerator {
    /** Which generator wrote a letter, kept in its context; letters of another kind of words are not read. */
    private static final String GENERATOR_VERSION = "2";
    private static final long ONE_INGAME_DAY_TICKS = 24000L;

    /** Who writes the notices, as template ids ({@code missive.losttales.issuer.*}). */
    private static final String[] ISSUERS = new String[] {
            "local_watch", "road_warden", "village_reeve", "caravan_master",
            "quartermaster"
    };
    private static final String[] KILL_TITLES = new String[] {
            "trouble_on_the_road", "dangerous_work", "clear_the_paths"
    };
    private static final String[] KILL_FLAVORS = new String[] {
            "shapes_beyond_firelight", "roads_open", "steady_blade"
    };
    private static final String[] GATHER_TITLES = new String[] {
            "materials_wanted", "gatherers_pay", "stores_run_low"
    };
    private static final String[] GATHER_FLAVORS = new String[] {
            "small_stores", "plain_notice", "useful_materials"
    };

    private static final KillTemplate[] KILL_TEMPLATES = new KillTemplate[] {
            new KillTemplate("hostile", true, 4, 8, 12),
            new KillTemplate("Zombie", false, 3, 7, 10),
            new KillTemplate("Skeleton", false, 3, 6, 12),
            new KillTemplate("Spider", false, 3, 6, 12),
            new KillTemplate("Creeper", false, 2, 4, 18)
    };

    private static final GatherTemplate[] GATHER_TEMPLATES = new GatherTemplate[] {
            new GatherTemplate("minecraft:coal", 8, 18, 3),
            new GatherTemplate("minecraft:iron_ingot", 4, 10, 6),
            new GatherTemplate("minecraft:gold_ingot", 2, 6, 10),
            new GatherTemplate("minecraft:wheat", 8, 18, 3),
            new GatherTemplate("minecraft:leather", 3, 8, 6),
            new GatherTemplate("minecraft:string", 4, 10, 5),
            new GatherTemplate("minecraft:bone", 4, 10, 5),
            new GatherTemplate("minecraft:log", 8, 20, 2)
    };

    private LostTalesMissiveGenerator() {}

    /** A new notice for a board, sealed by the world. */
    public static ItemStack createRandomMissiveLetter(World world, String boardKey, long worldTime, int sequence, Random random) {
        LostTalesMissiveData missive = createRandomMissive(world, boardKey, worldTime, sequence, random);
        ItemStack letter = createMissiveLetter(missive);
        if (letter != null) {
            MissiveSeals.seal(world, letter);
        }
        return letter;
    }

    public static ItemStack createMissiveLetter(LostTalesMissiveData missive) {
        if (missive == null || !missive.isValid()) {
            return null;
        }

        Item item = ELostTalesItem.MISSIVE_LETTER.getItem();
        if (item instanceof LostTalesItemMissiveLetter) {
            return ((LostTalesItemMissiveLetter) item).createStack(missive);
        }

        ItemStack stack = new ItemStack(item);
        LostTalesMissiveNbt.writeToItemStack(stack, missive);
        return stack;
    }

    public static LostTalesMissiveData createRandomMissive(World world, String boardKey, long worldTime, int sequence, Random random) {
        Random safeRandom = random == null ? new Random() : random;
        int choice = safeRandom.nextInt(2);
        if (choice == 0) {
            return createKillMissive(world, boardKey, worldTime, sequence, safeRandom);
        }
        return createGatherMissive(world, boardKey, worldTime, sequence, safeRandom);
    }

    private static LostTalesMissiveData createKillMissive(World world, String boardKey, long worldTime, int sequence, Random random) {
        KillTemplate template = KILL_TEMPLATES[random.nextInt(KILL_TEMPLATES.length)];
        int count = randomBetween(random, template.minCount, template.maxCount);
        int xp = count * template.xpPerTarget + randomBetween(random, 8, 24);
        String issuer = randomIssuer(random);
        String questId = LostTalesMissiveData.createQuestId(boardKey, worldTime, sequence);

        Map<String, String> params = new LinkedHashMap<String, String>();
        if (template.groupSelector) {
            params.put("group", template.selector);
        } else {
            params.put("entity", template.selector);
        }
        params.put("count", String.valueOf(count));

        LostTalesMissiveObjectiveData objective = new LostTalesMissiveObjectiveData(
                "kill_" + normalizeId(template.selector),
                LostTalesMissiveObjectiveData.TYPE_KILL,
                false,
                params
        );

        return LostTalesMissiveData.builder(questId, LostTalesMissiveObjectiveData.TYPE_KILL)
                .titleId(randomChoice(random, KILL_TITLES))
                .descriptionId(LostTalesMissiveObjectiveData.TYPE_KILL)
                .issuerId(issuer)
                .flavorId(randomChoice(random, KILL_FLAVORS))
                .target(template.selector)
                .repeatable(true)
                .firstComeFirstServed(true)
                .generationWorldTime(worldTime)
                .timeLimitTicks(randomTimeLimitTicks(random))
                .context("generator", GENERATOR_VERSION)
                .context("board", safeBoardKey(boardKey))
                .context("dimension", String.valueOf(getDimensionId(world)))
                .objective(objective)
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(xp, rewardItemsForDifficulty(random, count)))
                .build();
    }

    private static LostTalesMissiveData createGatherMissive(World world, String boardKey, long worldTime, int sequence, Random random) {
        GatherTemplate template = GATHER_TEMPLATES[random.nextInt(GATHER_TEMPLATES.length)];
        int count = randomBetween(random, template.minCount, template.maxCount);
        int xp = count * template.xpPerItem + randomBetween(random, 6, 18);
        String issuer = randomIssuer(random);
        String questId = LostTalesMissiveData.createQuestId(boardKey, worldTime, sequence);

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("item", template.itemId);
        params.put("count", String.valueOf(count));

        LostTalesMissiveObjectiveData objective = new LostTalesMissiveObjectiveData(
                "gather_" + normalizeId(template.itemId),
                LostTalesMissiveObjectiveData.TYPE_GATHER,
                false,
                params
        );

        return LostTalesMissiveData.builder(questId, LostTalesMissiveObjectiveData.TYPE_GATHER)
                .titleId(randomChoice(random, GATHER_TITLES))
                .descriptionId(LostTalesMissiveObjectiveData.TYPE_GATHER)
                .issuerId(issuer)
                .flavorId(randomChoice(random, GATHER_FLAVORS))
                .target(template.itemId)
                .repeatable(true)
                .firstComeFirstServed(true)
                .generationWorldTime(worldTime)
                .timeLimitTicks(randomTimeLimitTicks(random))
                .context("generator", GENERATOR_VERSION)
                .context("board", safeBoardKey(boardKey))
                .context("dimension", String.valueOf(getDimensionId(world)))
                .objective(objective)
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(xp, rewardItemsForDifficulty(random, Math.max(1, count / 2))))
                .build();
    }

    private static long randomTimeLimitTicks(Random random) {
        if (random == null || !LostTalesConfig.enableTimedMissives) {
            return 0L;
        }

        int chance = Math.max(0, Math.min(100, LostTalesConfig.timedMissiveChancePercent));
        if (chance <= 0 || random.nextInt(100) >= chance) {
            return 0L;
        }

        int minDays = Math.max(1, LostTalesConfig.timedMissiveMinDays);
        int maxDays = Math.max(minDays, LostTalesConfig.timedMissiveMaxDays);
        return randomBetween(random, minDays, maxDays) * ONE_INGAME_DAY_TICKS;
    }

    private static String rewardItemsForDifficulty(Random random, int difficulty) {
        int emeralds = Math.max(1, Math.min(3, difficulty / 4));
        if (random.nextBoolean()) {
            return "minecraft:emerald*" + emeralds;
        }
        int gold = Math.max(1, Math.min(6, difficulty / 2));
        return "minecraft:gold_ingot*" + gold;
    }

    private static String randomIssuer(Random random) {
        return ISSUERS[random.nextInt(ISSUERS.length)];
    }

    private static String randomChoice(Random random, String[] choices) {
        return choices[random.nextInt(choices.length)];
    }

    private static int randomBetween(Random random, int min, int max) {
        if (max <= min) {
            return Math.max(1, min);
        }
        return min + random.nextInt(max - min + 1);
    }

    private static int getDimensionId(World world) {
        return world == null || world.provider == null ? 0 : world.provider.dimensionId;
    }

    private static String safeBoardKey(String boardKey) {
        return boardKey == null || boardKey.trim().length() == 0 ? "board" : boardKey.trim();
    }

    private static String normalizeId(String value) {
        String text = value == null ? "objective" : value.trim().toLowerCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.length() == 0 ? "objective" : builder.toString();
    }

    private static final class KillTemplate {
        private final String selector;
        private final boolean groupSelector;
        private final int minCount;
        private final int maxCount;
        private final int xpPerTarget;

        private KillTemplate(String selector, boolean groupSelector, int minCount, int maxCount, int xpPerTarget) {
            this.selector = selector;
            this.groupSelector = groupSelector;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.xpPerTarget = xpPerTarget;
        }
    }

    private static final class GatherTemplate {
        private final String itemId;
        private final int minCount;
        private final int maxCount;
        private final int xpPerItem;

        private GatherTemplate(String itemId, int minCount, int maxCount, int xpPerItem) {
            this.itemId = itemId;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.xpPerItem = xpPerItem;
        }
    }
}
