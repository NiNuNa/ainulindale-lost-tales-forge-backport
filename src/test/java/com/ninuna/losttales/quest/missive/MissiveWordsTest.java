package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestWords;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A missive keeps template ids and a target, never sentences: the
 * generator writes ids, the letter, its quest and its sync keep them, and
 * each game words them from the lang file, in English word for word as
 * the generator once wrote them.
 */
public final class MissiveWordsTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** The mod's missive lines, and only those, so the rest of the suite still reads keys. */
    @Before
    public void readInEnglish() {
        StringBuilder english = new StringBuilder();
        for (Map.Entry<String, String> line : LostTalesLangFile.english().entrySet()) {
            if (line.getKey().startsWith(MissiveWords.KEY_PREFIX)
                    || line.getKey().startsWith("gui.losttales.quest.objective.")) {
                english.append(line.getKey()).append('=')
                        .append(line.getValue()).append('\n');
            }
        }
        inject(english.toString());
    }

    @After
    public void readInEnglishAgain() {
        readInEnglish();
    }

    /** Every letter the generator writes is ids and arguments the lang file words. */
    @Test
    public void theGeneratorWritesIdsAndArguments() {
        for (int seed = 0; seed < 200; seed++) {
            LostTalesMissiveData missive = LostTalesMissiveGenerator
                    .createRandomMissive(null, "dim0_1_64_1", 1000L, seed,
                            new Random(seed));
            assertTrue(missive.isValid());
            assertTrue(MissiveWords.isTemplateId(missive.getTitleId()));
            assertTrue(EnglishWords.INSTANCE.has(
                    MissiveWords.titleKey(missive.getTitleId())));
            assertTrue(EnglishWords.INSTANCE.has(
                    MissiveWords.issuerKey(missive.getIssuerId())));
            assertTrue(EnglishWords.INSTANCE.has(
                    MissiveWords.flavorKey(missive.getFlavorId())));
            assertEquals(missive.getQuestType(), missive.getDescriptionId());
            assertTrue(EnglishWords.INSTANCE.has(
                    MissiveWords.descriptionKey(missive.getDescriptionId())));
            assertTrue(MissiveWords.isTarget(missive.getTarget()));
            assertTrue("its target has a noun line: " + missive.getTarget(),
                    EnglishWords.INSTANCE.has(
                            MissiveWords.targetKey(missive.getTarget())));
            assertEquals("2", missive.getGenerationContext().get("generator"));
            for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
                assertEquals("an objective's count is an argument",
                        String.valueOf(Integer.parseInt(
                                objective.getParams().get("count"))),
                        objective.getParams().get("count"));
            }
        }
    }

    /** In English a letter reads word for word as the generator once wrote it. */
    @Test
    public void inEnglishALetterReadsAsItAlwaysDid() {
        LostTalesMissiveData road = letter("kill", "trouble_on_the_road",
                "road_warden", "shapes_beyond_firelight", "Zombie");
        assertEquals("Missive: Trouble on the Road", MissiveWords.title(road));
        assertEquals("A Road Warden", MissiveWords.issuer(road));
        assertEquals("Travellers have reported zombies threatening the roads."
                + " Thin their numbers and claim the posted reward.",
                MissiveWords.description(road));
        assertEquals("Those who walk after dusk speak of shapes moving beyond"
                + " the firelight.", MissiveWords.flavor(road));
        LostTalesMissiveData iron = letter("gather", "gatherers_pay",
                "quartermaster", "useful_materials", "minecraft:iron_ingot");
        assertEquals("Notice: Gatherer's Pay", MissiveWords.title(iron));
        assertEquals("The stores are running short of iron ingots. Bring what"
                + " is asked and take the posted reward.",
                MissiveWords.description(iron));
        assertEquals("hostile creatures", MissiveWords.targetNoun("hostile"));
    }

    /**
     * A missive's objective reads word for word as the generator once wrote
     * it, on the letter, in its quest as the quest log keeps it, and on its
     * card: its kind's line with the count and the target's plural noun.
     */
    @Test
    public void aMissivesObjectivesReadWordForWordAsBefore() {
        assertEquals("Defeat 5 zombies.", EnglishWords.INSTANCE.format(
                MissiveWords.objectiveKey("kill"), "5", EnglishWords.INSTANCE
                        .format(MissiveWords.targetKey("Zombie"))));
        assertEquals("Gather 10 logs.", EnglishWords.INSTANCE.format(
                MissiveWords.objectiveKey("gather"), "10", EnglishWords.INSTANCE
                        .format(MissiveWords.targetKey("minecraft:log"))));

        LostTalesMissiveData zombies = counted("kill", "entity", "Zombie", 5);
        LostTalesMissiveData hostile = counted("kill", "group", "hostile", 6);
        LostTalesMissiveData logs = counted("gather", "item", "minecraft:log", 10);
        LostTalesMissiveData iron = counted("gather", "item",
                "minecraft:iron_ingot", 4);
        assertEquals("Defeat 5 zombies.", MissiveWords.objective(
                zombies.getObjectives().get(0)));
        assertEquals("Defeat 6 hostile creatures.", MissiveWords.objective(
                hostile.getObjectives().get(0)));
        assertEquals("Gather 10 logs.", MissiveWords.objective(
                logs.getObjectives().get(0)));
        assertEquals("Gather 4 iron ingots.", MissiveWords.objective(
                iron.getObjectives().get(0)));

        LostTalesQuestDefinition quest =
                LostTalesMissiveQuestFactory.createQuestDefinition(zombies);
        LostTalesQuestDefinition kept = com.ninuna.losttales.quest
                .LostTalesQuestDefinitionNbt.read(com.ninuna.losttales.quest
                        .LostTalesQuestDefinitionNbt.write(quest));
        assertEquals("Defeat 5 zombies.", com.ninuna.losttales.quest
                .LostTalesQuestObjectiveTextHelper.describe(
                        kept.getStages().get(0).getObjectives().get(0)));
        com.ninuna.losttales.chat.share.ChatQuestCard card =
                com.ninuna.losttales.quest.LostTalesQuestShareResolver.card(kept,
                        new com.ninuna.losttales.quest.progress.LostTalesQuestProgress(
                                kept.getId(), 0, "10",
                                Collections.singletonMap(kept.getStages().get(0)
                                        .getObjectives().get(0).getId(),
                                        Integer.valueOf(2)), 0L, 0L));
        assertEquals("Defeat 5 zombies. (2/5)", com.ninuna.losttales.quest
                .LostTalesQuestCardWords.objectives(kept.getId(), card));
    }

    @Test
    public void aLetterReadsInTheGamesLanguage() {
        inject(MissiveWords.titleKey("clear_the_paths") + "=Sendschreiben: Räumt die Wege\n"
                + MissiveWords.descriptionKey("kill")
                + "=Reisende melden %s an den Straßen.\n"
                + MissiveWords.targetKey("Skeleton") + "=Skelette\n");
        LostTalesMissiveData letter = letter("kill", "clear_the_paths",
                "road_warden", "roads_open", "Skeleton");
        assertEquals("Sendschreiben: Räumt die Wege", MissiveWords.title(letter));
        assertEquals("Reisende melden Skelette an den Straßen.",
                MissiveWords.description(letter));
    }

    /** A title id the game has no line for reads as a missive; a bad id is never a letter. */
    @Test
    public void anUnknownTitleReadsAsAMissiveAndASentenceIsNoId() {
        assertEquals(EnglishWords.INSTANCE.format("missive.losttales.title"),
                MissiveWords.title("unknown_title"));
        assertFalse(MissiveWords.isTemplateId("Missive: Trouble on the Road"));
        assertFalse(MissiveWords.isTemplateId(""));
        assertFalse(MissiveWords.isTarget("zombies and more"));
        assertFalse(letter("kill", "Missive: Clear", "road_warden",
                "roads_open", "Zombie").isValid());
    }

    /** A missive's quest keeps the letter's ids; its objectives carry no words. */
    @Test
    public void aMissivesQuestIsWordedByTheLettersIds() {
        LostTalesMissiveData letter = letter("kill", "dangerous_work",
                "village_reeve", "steady_blade", "Creeper");
        LostTalesQuestDefinition quest =
                LostTalesMissiveQuestFactory.createQuestDefinition(letter);
        assertNotNull(quest);
        assertEquals("", quest.getTitle());
        assertEquals("", quest.getDescription());
        assertTrue(quest.getJournalLog().isEmpty());
        assertEquals(MissiveWords.wordsOf(letter), quest.getWords());
        assertEquals("Bounty: Dangerous Work", LostTalesQuestWords.title(quest));
        assertEquals(MissiveWords.description(letter),
                LostTalesQuestWords.description(quest));
        assertEquals("A steady blade and a brave heart will be paid in coin"
                + " and thanks.", LostTalesQuestWords.journalLine(quest, 0));
        for (LostTalesQuestObjectiveDefinition objective
                : quest.getStages().get(0).getObjectives()) {
            assertEquals("", objective.getDescription());
        }
        IChatComponent title = LostTalesQuestWords.titleComponent(quest);
        assertTrue(title instanceof ChatComponentTranslation);
        assertEquals(MissiveWords.titleKey("dangerous_work"),
                ((ChatComponentTranslation)title).getKey());
    }

    /** A letter's item data keeps the ids, and a sentence in their place is not read. */
    @Test
    public void theLettersItemDataKeepsIdsAndRefusesSentences() {
        LostTalesMissiveData letter = letter("gather", "stores_run_low",
                "caravan_master", "small_stores", "minecraft:wheat");
        NBTTagCompound tag = LostTalesMissiveNbt.writeToNBT(letter);
        LostTalesMissiveData read = LostTalesMissiveNbt.readFromNBT(tag);
        assertNotNull(read);
        assertEquals("stores_run_low", read.getTitleId());
        assertEquals("caravan_master", read.getIssuerId());
        assertEquals("gather", read.getDescriptionId());
        assertEquals("small_stores", read.getFlavorId());
        assertEquals("minecraft:wheat", read.getTarget());
        assertFalse(tag.hasKey("Title"));

        NBTTagCompound sentence = LostTalesMissiveNbt.writeToNBT(letter);
        sentence.setString("TitleId", "Missive: Stores Run Low");
        assertNull(LostTalesMissiveNbt.readFromNBT(sentence));
        NBTTagCompound longTarget = LostTalesMissiveNbt.writeToNBT(letter);
        StringBuilder target = new StringBuilder();
        while (target.length() <= MissiveWords.MAX_TARGET_BYTES) {
            target.append("minecraft:");
        }
        longTarget.setString("Target", target.toString());
        assertNull(LostTalesMissiveNbt.readFromNBT(longTarget));
        NBTTagCompound old = LostTalesMissiveNbt.writeToNBT(letter);
        old.removeTag("TitleId");
        old.setString("Title", "Missive: Stores Run Low");
        assertNull("a letter written in words is not read",
                LostTalesMissiveNbt.readFromNBT(old));
    }

    /** The quest log keeps a missive's words as ids, and nothing else in their place. */
    @Test
    public void theQuestLogKeepsTheWordsAsIds() {
        LostTalesQuestDefinition quest = LostTalesMissiveQuestFactory
                .createQuestDefinition(letter("kill", "clear_the_paths",
                        "local_watch", "roads_open", "hostile"));
        NBTTagCompound tag = com.ninuna.losttales.quest.LostTalesQuestDefinitionNbt
                .write(quest);
        LostTalesQuestDefinition read = com.ninuna.losttales.quest
                .LostTalesQuestDefinitionNbt.read(tag);
        assertNotNull(read);
        assertEquals(quest.getWords(), read.getWords());
        tag.getTagList("Words", 10).getCompoundTagAt(0)
                .setString("Value", "Missive: Clear the Paths");
        assertNull(com.ninuna.losttales.quest.LostTalesQuestDefinitionNbt
                .read(tag));
    }

    private static LostTalesMissiveData counted(String type, String param,
                                                String target, int count) {
        Map<String, String> params = new java.util.LinkedHashMap<String, String>();
        params.put(param, target);
        params.put("count", String.valueOf(count));
        return LostTalesMissiveData.builder("losttales:missive/generated/b/1_0", type)
                .titleId("dangerous_work").descriptionId(type).target(target)
                .objective(new LostTalesMissiveObjectiveData(type + "_target",
                        type, false, params))
                .build();
    }

    private static LostTalesMissiveData letter(String type, String title,
            String issuer, String flavor, String target) {
        List<LostTalesMissiveObjectiveData> objectives =
                new ArrayList<LostTalesMissiveObjectiveData>();
        objectives.add(new LostTalesMissiveObjectiveData(type + "_target",
                type, false, Collections.singletonMap(
                        "kill".equals(type) ? "entity" : "item", target)));
        LostTalesMissiveData.Builder builder = LostTalesMissiveData.builder(
                "losttales:missive/generated/b/1_0", type)
                .titleId(title).issuerId(issuer).descriptionId(type)
                .flavorId(flavor).target(target);
        for (LostTalesMissiveObjectiveData objective : objectives) {
            builder.objective(objective);
        }
        return builder.build();
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
