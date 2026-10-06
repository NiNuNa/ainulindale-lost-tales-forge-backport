package com.ninuna.losttales.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A bundled quest is worded by the lang lines its id names, in each
 * player's language; the quest files hold none of its words; a quest an
 * operator wrote keeps its words in every language; and a server's answer
 * names a bundled quest by a translation each reader's game words.
 */
public final class LostTalesQuestWordsTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NIA = "losttales:tutorial/meet_nia";
    private static final String PREFIX = "quest.losttales.tutorial.meet_nia";
    private static final List<String> GERMAN_KEYS = Arrays.asList(
            PREFIX + ".title", PREFIX + ".objective.gather_sticks",
            PREFIX + ".journal.10", PREFIX + ".dialogue.offer");

    @BeforeClass
    public static void readInEnglish() {
        StringTranslate.inject(LostTalesQuestWordsTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

    /** The mod's English lines back for every key a test wrote another language under. */
    @After
    public void readInEnglishAgain() {
        StringBuilder english = new StringBuilder();
        for (String key : GERMAN_KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        inject(english.toString());
    }

    @Test
    public void aQuestsIdNamesItsKeys() {
        assertEquals(PREFIX + ".title", LostTalesQuestWords.titleKey(NIA));
        assertEquals(PREFIX + ".description",
                LostTalesQuestWords.descriptionKey(NIA));
        assertEquals(PREFIX + ".objective.gather_sticks",
                LostTalesQuestWords.objectiveKey(NIA, "gather_sticks"));
        assertEquals(PREFIX + ".journal.20",
                LostTalesQuestWords.journalKey(NIA, "20"));
        assertEquals(PREFIX + ".dialogue.handIn",
                LostTalesQuestWords.dialogueKey(NIA, "handIn"));
        assertEquals("quest.losttales.path.survivalist.first_sparks.title",
                LostTalesQuestWords.titleKey(
                        "losttales:path/survivalist/first_sparks"));
        assertTrue(LostTalesQuestWords.isKeySafe(NIA));
        assertFalse(LostTalesQuestWords.isKeySafe("losttales:Some Quest"));
        assertFalse(LostTalesQuestWords.isKeySafe("losttales:x=y"));
    }

    /**
     * Every word a bundled quest is read by is a line of the English lang
     * file: its title, its description, each objective, each journal line
     * and each line of its conversation.
     */
    @Test
    public void everyBundledQuestFieldHasItsLangLine() {
        for (LostTalesQuestDefinition quest : bundled()) {
            String id = quest.getId();
            assertTrue(id, quest.isBundled());
            assertEquals(id, EnglishWords.INSTANCE.format(
                    LostTalesQuestWords.titleKey(id)), quest.getTitle());
            assertEquals(id, EnglishWords.INSTANCE.format(
                    LostTalesQuestWords.descriptionKey(id)), quest.getDescription());
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                    String key = LostTalesQuestWords.objectiveKey(id, objective.getId());
                    assertEquals(key, objective.getDescriptionKey());
                    assertEquals(key, EnglishWords.INSTANCE.format(key),
                            objective.getDescription());
                }
            }
            for (Map.Entry<String, String> line : quest.getJournalLog().entrySet()) {
                assertEquals(EnglishWords.INSTANCE.format(
                        LostTalesQuestWords.journalKey(id, line.getKey())),
                        line.getValue());
            }
            for (Map.Entry<String, String> line : quest.getDialogue().entrySet()) {
                assertEquals(EnglishWords.INSTANCE.format(
                        LostTalesQuestWords.dialogueKey(id, line.getKey())),
                        line.getValue());
            }
        }
    }

    /**
     * The objectives whose words a count and a plural carry read word for
     * word as their files once wrote them, each a line a translator words
     * with its own count and plural.
     */
    @Test
    public void theObjectivesReadWordForWordAsBefore() {
        String[][] lines = {
                {"losttales:tutorial/starter_note", "craft_torches", "Craft 4 torches."},
                {"losttales:path/survivalist/first_sparks", "gather_sticks", "Gather 8 sticks."},
                {"losttales:path/survivalist/first_sparks", "craft_torches", "Craft 8 torches."},
                {"losttales:path/survivalist/stone_and_shelter", "gather_cobblestone", "Gather 16 cobblestone."},
                {"losttales:path/survivalist/stone_and_shelter", "craft_furnace", "Craft a furnace."},
                {"losttales:path/survivalist/food_for_the_road", "gather_wheat", "Gather 6 wheat."},
                {"losttales:path/survivalist/tools_of_the_trail", "craft_stone_axe", "Craft a stone axe."},
                {"losttales:path/survivalist/tools_of_the_trail", "craft_stone_pickaxe", "Craft a stone pickaxe."},
                {"losttales:path/survivalist/tools_of_the_trail", "craft_stone_sword", "Craft a stone sword."},
                {"losttales:path/survivalist/tools_of_the_trail", "gather_coal", "Gather 8 coal."},
                {"losttales:path/survivalist/night_watch", "craft_bow", "Craft a bow."},
                {"losttales:path/survivalist/night_watch", "gather_arrows", "Gather 8 arrows."}};
        for (String[] line : lines) {
            assertEquals(line[2], EnglishWords.INSTANCE.format(
                    LostTalesQuestWords.objectiveKey(line[0], line[1])));
            assertEquals(line[2], LostTalesQuestObjectiveTextHelper.describe(
                    objective(line[0], line[1])));
        }
    }

    private static LostTalesQuestObjectiveDefinition objective(String questId,
                                                               String objectiveId) {
        for (LostTalesQuestDefinition quest : bundled()) {
            if (!quest.getId().equals(questId)) {
                continue;
            }
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                    if (objective.getId().equals(objectiveId)) {
                        return objective;
                    }
                }
            }
        }
        throw new AssertionError(questId + " has " + objectiveId);
    }

    /** The quest files leave every word to the lang file. */
    @Test
    public void theBundledFilesHoldNoWords() {
        for (String file : LostTalesQuestDefinitionJsonParser.parseQuestIndex(
                open(BundledQuestFiles.INDEX_FILE))) {
            JsonObject quest = new JsonParser().parse(open(file)).getAsJsonObject();
            for (String field : new String[] {"title", "description",
                    "journalLog", "dialogue"}) {
                assertFalse(file + " writes " + field, quest.has(field));
            }
            for (JsonElement stage : quest.getAsJsonArray("stages")) {
                for (JsonElement objective : stage.getAsJsonObject()
                        .getAsJsonArray("objectives")) {
                    assertFalse(file + " words an objective",
                            objective.getAsJsonObject().has("description"));
                }
            }
        }
    }

    @Test
    public void aBundledQuestReadsInTheGamesLanguage() {
        inject(PREFIX + ".title=Ein Gespräch mit Nia\n"
                + PREFIX + ".objective.gather_sticks=Bringe Nia 4 Stöcke.\n"
                + PREFIX + ".journal.10=Nia bat mich um ein paar Stöcke.\n"
                + PREFIX + ".dialogue.offer=Der Sturm hat meinen Dachbalken gebrochen.\n");
        LostTalesQuestDefinition quest = nia();
        assertEquals("Ein Gespräch mit Nia", LostTalesQuestWords.title(quest));
        assertEquals("Bringe Nia 4 Stöcke.", LostTalesQuestObjectiveTextHelper
                .describe(quest.getStages().get(0).getObjectives().get(0)));
        assertEquals("Nia bat mich um ein paar Stöcke.",
                LostTalesQuestWords.journalLine(quest, 0));
        assertEquals("Der Sturm hat meinen Dachbalken gebrochen.",
                LostTalesQuestDialogue.of(quest).line(LostTalesQuestDialogue.OFFER));
        assertTrue("the offer still makes it a conversation",
                LostTalesQuestDialogue.of(quest).isOffered());
    }

    /** In English the lines read word for word as the files once wrote them. */
    @Test
    public void inEnglishABundledQuestReadsAsItAlwaysDid() {
        LostTalesQuestDefinition quest = nia();
        assertEquals("A Conversation with Nia", LostTalesQuestWords.title(quest));
        assertEquals("Bring Nia 4 sticks.", LostTalesQuestObjectiveTextHelper
                .describe(quest.getStages().get(0).getObjectives().get(0)));
        assertEquals("I gathered the sticks Nia requested and should hand them over.",
                LostTalesQuestWords.journalLine(quest, 1));
        assertEquals("Take care, Nia.", LostTalesQuestDialogue.of(quest)
                .line(LostTalesQuestDialogue.LEAVE));
    }

    /** What an operator wrote is theirs: no key is ever made from their quest's id. */
    @Test
    public void anOperatorsQuestKeepsItsWords() {
        String id = "losttales:server/road";
        inject(LostTalesQuestWords.titleKey(id) + "=Übersetzt\n");
        LostTalesQuestDefinition quest = new LostTalesQuestDefinition(id,
                "The Old Road", "Walk it.", false, false,
                LostTalesQuestDefinition.START_MODE_ITEM, null, null, null,
                null, Collections.singletonMap("10", "I walked."), null, null,
                Collections.singletonList(new LostTalesQuestStageDefinition("10",
                        Collections.<LostTalesQuestObjectiveDefinition>emptyList())));
        assertFalse(quest.isBundled());
        assertEquals("The Old Road", LostTalesQuestWords.title(quest));
        assertEquals("Walk it.", LostTalesQuestWords.description(quest));
        assertEquals("I walked.", LostTalesQuestWords.journalLine(quest, 0));
        IChatComponent component = LostTalesQuestWords.titleComponent(quest);
        assertTrue(component instanceof ChatComponentText);
        assertEquals("The Old Road", EnglishWords.INSTANCE.read(component));
    }

    /** A server's answer names a bundled quest by its line, which each reader's game words. */
    @Test
    public void aServersAnswerNamesABundledQuestByItsLine() {
        IChatComponent component = LostTalesQuestWords.titleComponent(nia());
        assertTrue(component instanceof ChatComponentTranslation);
        assertEquals(PREFIX + ".title",
                ((ChatComponentTranslation)component).getKey());
        assertEquals("A Conversation with Nia",
                EnglishWords.INSTANCE.read(component));
    }

    private static LostTalesQuestDefinition nia() {
        for (LostTalesQuestDefinition quest : bundled()) {
            if (NIA.equals(quest.getId())) {
                return quest;
            }
        }
        throw new AssertionError("Nia's quest is bundled");
    }

    private static List<LostTalesQuestDefinition> bundled() {
        BundledQuestFiles.Result read = BundledQuestFiles.read(
                new BundledQuestFiles.Source() {
                    @Override
                    public Reader open(String path) {
                        return LostTalesQuestWordsTest.open(path);
                    }
                });
        assertTrue(read.problems.toString(), read.problems.isEmpty());
        return read.quests;
    }

    private static Reader open(String path) {
        InputStream in = LostTalesQuestWordsTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesQuestDefinitionJsonParser
                        .normalizeQuestFile(path));
        assertNotNull(path, in);
        return new InputStreamReader(in, UTF_8);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
