package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.LostTalesItemMissiveLetter;
import com.ninuna.losttales.quest.LostTalesQuestParams;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Writes a missive board's notices from the templates the server loaded
 * ({@link MissiveTemplates}, QUEST-42): a kill or a gather built from the
 * objective types and selectors the quest runtime already handles. A
 * bundled template's notice is written as template ids and a target, never
 * as sentences ({@link MissiveWords}), so each player reads it in their
 * own language; a server's template's notice carries its title, issuer and
 * flavour as written.
 */
public final class LostTalesMissiveGenerator {
    private static final long ONE_INGAME_DAY_TICKS = 24000L;

    private LostTalesMissiveGenerator() {}

    /**
     * A new notice for a board, sealed by the world; null where the world
     * cannot seal (its key cannot be used, which {@link MissiveSeals} logs
     * once), where no template is loaded, or where the missive is not
     * valid. A board posts nothing it could not seal, so no notice stands
     * that nobody could accept.
     */
    public static ItemStack createRandomMissiveLetter(World world, String boardKey, long worldTime, int sequence, Random random) {
        if (MissiveSeals.keyOf(world) == null) {
            return null;
        }
        LostTalesMissiveData missive = createRandomMissive(world, boardKey, worldTime, sequence, random);
        if (missive == null || !missive.isValid()) {
            return null;
        }
        ItemStack letter = ((LostTalesItemMissiveLetter) ELostTalesItem.MISSIVE_LETTER
                .getItem()).createStack(missive);
        MissiveSeals.seal(world, letter);
        return letter;
    }

    /** A notice from a template the server loaded, picked by chance; null where there is none. */
    public static LostTalesMissiveData createRandomMissive(World world, String boardKey, long worldTime, int sequence, Random random) {
        Random safeRandom = random == null ? new Random() : random;
        MissiveTemplate template = MissiveTemplates.pick(safeRandom);
        return template == null ? null
                : createMissive(template, world, boardKey, worldTime, sequence, safeRandom);
    }

    /**
     * A notice from {@code template}: a count between its least and its
     * most, the experience it pays for each and a little more by chance,
     * LOTR coins, a title, an issuer and a flavour line of its words, and
     * maybe a time limit.
     */
    public static LostTalesMissiveData createMissive(MissiveTemplate template, World world,
                                                     String boardKey, long worldTime,
                                                     int sequence, Random random) {
        boolean kill = template.isKill();
        int count = randomBetween(random, template.getLeastCount(), template.getMostCount());
        int xp = count * template.getExperience()
                + (kill ? randomBetween(random, 8, 24) : randomBetween(random, 6, 18));
        String issuer = randomWord(random, template.getIssuers());
        String questId = LostTalesMissiveData.createQuestId(boardKey, worldTime, sequence);

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(template.getTargetParam(), template.getTarget());
        params.put("count", String.valueOf(count));
        if (!kill) {
            // A notice's gathering is handed over, so one stack pays one notice.
            params.put(LostTalesQuestParams.TAKE, "true");
        }

        LostTalesMissiveObjectiveData objective = new LostTalesMissiveObjectiveData(
                template.getKind() + "_" + normalizeId(template.getTarget()),
                template.getKind(),
                false,
                params
        );

        return LostTalesMissiveData.builder(questId, template.getKind())
                .written(template.isWritten())
                .titleId(randomWord(random, template.getTitles()))
                .descriptionId(template.getKind())
                .issuerId(issuer)
                .flavorId(randomWord(random, template.getFlavours()))
                .target(template.getTarget())
                .generationWorldTime(worldTime)
                .timeLimitTicks(randomTimeLimitTicks(random))
                .context("board", safeBoardKey(boardKey))
                .context("dimension", String.valueOf(getDimensionId(world)))
                .objective(objective)
                .rewardData(LostTalesMissiveRewardData.experienceAndCoins(xp,
                        coinsForDifficulty(random, kill ? count : Math.max(1, count / 2))))
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

    /**
     * A notice's coins: four for each foe or every second item asked for,
     * and a little more by chance, as LOTR's own mini-quests pay a few
     * dozen coins for a small task.
     */
    private static int coinsForDifficulty(Random random, int difficulty) {
        return Math.max(1, difficulty) * 4 + randomBetween(random, 5, 15);
    }

    /** One of the words, by chance; empty where there are none. */
    private static String randomWord(Random random, List<String> words) {
        return words.isEmpty() ? "" : words.get(random.nextInt(words.size()));
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
}
