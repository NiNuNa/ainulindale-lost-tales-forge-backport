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
 * Small first-pass radiant missive generator.
 *
 * This generator deliberately uses objective types and selectors already handled
 * by the existing 1.7.10 quest runtime. More immersive LOTR-region/faction
 * rules can be layered on later without changing the board tile entity.
 */
public final class LostTalesMissiveGenerator {
    public static final int MIN_GENERATION_BATCH = 1;
    public static final int MAX_GENERATION_BATCH = 3;

    private static final String GENERATOR_VERSION = "1";
    private static final long ONE_INGAME_DAY_TICKS = 24000L;

    private static final String[] ISSUERS = new String[] {
            "The Local Watch",
            "A Road Warden",
            "A Village Reeve",
            "A Caravan Master",
            "The Quartermaster"
    };

    private static final KillTemplate[] KILL_TEMPLATES = new KillTemplate[] {
            new KillTemplate("hostile", "hostile creatures", true, 4, 8, 12),
            new KillTemplate("Zombie", "zombies", false, 3, 7, 10),
            new KillTemplate("Skeleton", "skeletons", false, 3, 6, 12),
            new KillTemplate("Spider", "spiders", false, 3, 6, 12),
            new KillTemplate("Creeper", "creepers", false, 2, 4, 18)
    };

    private static final GatherTemplate[] GATHER_TEMPLATES = new GatherTemplate[] {
            new GatherTemplate("minecraft:coal", "coal", 8, 18, 3),
            new GatherTemplate("minecraft:iron_ingot", "iron ingots", 4, 10, 6),
            new GatherTemplate("minecraft:gold_ingot", "gold ingots", 2, 6, 10),
            new GatherTemplate("minecraft:wheat", "wheat", 8, 18, 3),
            new GatherTemplate("minecraft:leather", "leather", 3, 8, 6),
            new GatherTemplate("minecraft:string", "string", 4, 10, 5),
            new GatherTemplate("minecraft:bone", "bones", 4, 10, 5),
            new GatherTemplate("minecraft:log", "logs", 8, 20, 2)
    };

    private LostTalesMissiveGenerator() {}

    public static ItemStack createRandomMissiveLetter(World world, String boardKey, long worldTime, int sequence, Random random) {
        LostTalesMissiveData missive = createRandomMissive(world, boardKey, worldTime, sequence, random);
        return createMissiveLetter(missive);
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
        int choice = safeRandom.nextInt(3);
        if (choice == 0) {
            return createKillMissive(world, boardKey, worldTime, sequence, safeRandom);
        }
        if (choice == 1) {
            return createGatherMissive(world, boardKey, worldTime, sequence, safeRandom, false);
        }
        return createDeliveryPreparationMissive(world, boardKey, worldTime, sequence, safeRandom);
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
                "kill_" + normalizeId(template.displayName),
                LostTalesMissiveObjectiveData.TYPE_KILL,
                "Defeat " + count + " " + template.displayName + ".",
                false,
                params
        );

        return LostTalesMissiveData.builder(questId, LostTalesMissiveObjectiveData.TYPE_KILL)
                .title(randomChoice(random,
                        "Missive: Trouble on the Road",
                        "Bounty: Dangerous Work",
                        "Missive: Clear the Paths"))
                .description("Travellers have reported " + template.displayName + " threatening the roads. Thin their numbers and claim the posted reward.")
                .issuer(issuer)
                .flavorText(randomChoice(random,
                        "Those who walk after dusk speak of shapes moving beyond the firelight.",
                        "The roads must remain open for honest folk and weary travellers.",
                        "A steady blade and a brave heart will be paid in coin and thanks."))
                .repeatable(true)
                .firstComeFirstServed(true)
                .generationWorldTime(worldTime)
                .timeLimitTicks(randomTimeLimitTicks(random))
                .context("generator", GENERATOR_VERSION)
                .context("board", safeBoardKey(boardKey))
                .context("dimension", String.valueOf(getDimensionId(world)))
                .context("target", template.displayName)
                .objective(objective)
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(xp, rewardItemsForDifficulty(random, count)))
                .build();
    }

    private static LostTalesMissiveData createGatherMissive(World world, String boardKey, long worldTime, int sequence, Random random, boolean deliveryStyle) {
        GatherTemplate template = GATHER_TEMPLATES[random.nextInt(GATHER_TEMPLATES.length)];
        int count = randomBetween(random, template.minCount, template.maxCount);
        int xp = count * template.xpPerItem + randomBetween(random, 6, 18);
        String issuer = randomIssuer(random);
        String questId = LostTalesMissiveData.createQuestId(boardKey, worldTime, sequence);

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("item", template.itemId);
        params.put("count", String.valueOf(count));

        LostTalesMissiveObjectiveData objective = new LostTalesMissiveObjectiveData(
                deliveryStyle ? "prepare_" + normalizeId(template.displayName) : "gather_" + normalizeId(template.displayName),
                LostTalesMissiveObjectiveData.TYPE_GATHER,
                (deliveryStyle ? "Gather and prepare " : "Gather ") + count + " " + template.displayName + ".",
                false,
                params
        );

        return LostTalesMissiveData.builder(questId, deliveryStyle ? LostTalesMissiveObjectiveData.TYPE_DELIVER : LostTalesMissiveObjectiveData.TYPE_GATHER)
                .title(deliveryStyle
                        ? randomChoice(random, "Missive: Supplies for the Road", "Delivery Notice: Goods Wanted", "Missive: A Parcel Prepared")
                        : randomChoice(random, "Missive: Materials Wanted", "Notice: Gatherer's Pay", "Missive: Stores Run Low"))
                .description(deliveryStyle
                        ? "A parcel of " + template.displayName + " must be gathered before it can be sent onward. Bring the goods and the issuer will see them dispatched."
                        : "The stores are running short of " + template.displayName + ". Bring what is asked and take the posted reward.")
                .issuer(issuer)
                .flavorText(deliveryStyle
                        ? randomChoice(random,
                                "A wax mark has been pressed beside the notice, but the destination is written only for the courier.",
                                "The roads are long, and even simple goods can decide whether a journey succeeds.",
                                "The issuer asks for steady hands before swift feet.")
                        : randomChoice(random,
                                "Every hall and camp depends on small stores gathered before they are missed.",
                                "The notice is plain, but the reward is marked clearly beneath it.",
                                "Useful materials are worth more than idle promises."))
                .repeatable(true)
                .firstComeFirstServed(true)
                .generationWorldTime(worldTime)
                .timeLimitTicks(randomTimeLimitTicks(random))
                .context("generator", GENERATOR_VERSION)
                .context("board", safeBoardKey(boardKey))
                .context("dimension", String.valueOf(getDimensionId(world)))
                .context("target", template.displayName)
                .context("deliveryPlaceholder", String.valueOf(deliveryStyle))
                .objective(objective)
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(xp, rewardItemsForDifficulty(random, Math.max(1, count / 2))))
                .build();
    }

    /**
     * Delivery objectives are not tracked by the existing quest runtime yet.
     * This first stage therefore creates delivery-flavoured gather quests so the
     * generated data remains completable once server-side acceptance is added.
     */
    private static LostTalesMissiveData createDeliveryPreparationMissive(World world, String boardKey, long worldTime, int sequence, Random random) {
        return createGatherMissive(world, boardKey, worldTime, sequence, random, true);
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

    private static String randomChoice(Random random, String a, String b, String c) {
        int choice = random.nextInt(3);
        return choice == 0 ? a : choice == 1 ? b : c;
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
        private final String displayName;
        private final boolean groupSelector;
        private final int minCount;
        private final int maxCount;
        private final int xpPerTarget;

        private KillTemplate(String selector, String displayName, boolean groupSelector, int minCount, int maxCount, int xpPerTarget) {
            this.selector = selector;
            this.displayName = displayName;
            this.groupSelector = groupSelector;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.xpPerTarget = xpPerTarget;
        }
    }

    private static final class GatherTemplate {
        private final String itemId;
        private final String displayName;
        private final int minCount;
        private final int maxCount;
        private final int xpPerItem;

        private GatherTemplate(String itemId, String displayName, int minCount, int maxCount, int xpPerItem) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.xpPerItem = xpPerItem;
        }
    }
}
