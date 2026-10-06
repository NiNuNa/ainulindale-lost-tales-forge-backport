package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A shared quest's card: found by the quest the sender's client names,
 * whatever its token was typed as in the sender's language, and carried as
 * data each reader's game words in its own language, in English word for
 * word as the server once wrote it.
 */
public final class LostTalesQuestShareCardTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NIA = "losttales:tutorial/meet_nia";
    private static final String TITLE_KEY = "quest.losttales.tutorial.meet_nia.title";
    private static final String OBJECTIVE_KEY =
            "quest.losttales.tutorial.meet_nia.objective.gather_sticks";
    private static final List<String> GERMAN_KEYS =
            Arrays.asList(TITLE_KEY, OBJECTIVE_KEY);

    @BeforeClass
    public static void readInEnglish() {
        StringTranslate.inject(LostTalesQuestShareCardTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

    @After
    public void readInEnglishAgain() {
        StringBuilder english = new StringBuilder();
        for (String key : GERMAN_KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        inject(english.toString());
    }

    private static LostTalesQuestPlayerData runningNia() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(NIA, "10", 0, 0L);
        return data;
    }

    /**
     * The quest is the one the reference names. The sender typed its name
     * in German, which the server never reads: the card carries no words
     * of a bundled quest at all.
     */
    @Test
    public void theCardIsFoundByTheQuestsReferenceAndCarriesNoWords() {
        String typedInGerman = "[q:Ein Gespräch mit Nia]";
        assertTrue(typedInGerman.length() > 0);
        ChatShowcase showcase = LostTalesQuestShareResolver.resolve(
                runningNia(), NIA, 0);
        assertNotNull(showcase);
        ChatQuestCard card = showcase.getQuestCard();
        assertEquals(ChatQuestCard.Source.BUNDLED, card.getSource());
        assertEquals("", card.getTitle());
        assertEquals(LostTalesQuestCategory.TUTORIALS, card.getCategory());
        ChatQuestCard.Objective objective = card.getObjectives().get(0);
        assertEquals("gather_sticks", objective.getId());
        assertEquals("", objective.getText());
        assertEquals("minecraft:stick", objective.getTargets().get("item"));
        assertEquals(4, objective.getCount());
        assertEquals(0, objective.getProgress());
        assertEquals("20", card.getRewards().get("experience"));

        assertNull("a quest the sender does not run is never shared",
                LostTalesQuestShareResolver.resolve(
                        new LostTalesQuestPlayerData(), NIA, 0));
        assertNull(LostTalesQuestShareResolver.resolve(runningNia(),
                "losttales:tutorial/cheese_cache", 0));
    }

    /** A reader's game in English reads the card as the server once wrote it. */
    @Test
    public void inEnglishTheCardReadsAsItAlwaysDid() {
        ChatQuestCard card = LostTalesQuestShareResolver.resolve(
                runningNia(), NIA, 0).getQuestCard();
        assertEquals("A Conversation with Nia",
                LostTalesQuestCardWords.title(NIA, card));
        assertEquals("Bring Nia 4 sticks. (0/4)",
                LostTalesQuestCardWords.objectives(NIA, card));
        assertEquals(LostTalesQuestRewardText.summary(
                nia().getRewards()), LostTalesQuestCardWords.reward(card));
    }

    @Test
    public void aReadersGameWordsTheCardInItsOwnLanguage() {
        ChatQuestCard card = LostTalesQuestShareResolver.resolve(
                runningNia(), NIA, 0).getQuestCard();
        inject(TITLE_KEY + "=Ein Gespräch mit Nia\n"
                + OBJECTIVE_KEY + "=Bringe Nia 4 Stöcke.\n");
        assertEquals("Ein Gespräch mit Nia",
                LostTalesQuestCardWords.title(NIA, card));
        assertEquals("Bringe Nia 4 Stöcke. (0/4)",
                LostTalesQuestCardWords.objectives(NIA, card));
    }

    /** A server quest's words are its operator's, carried as written and shown as they came. */
    @Test
    public void aServerQuestsCardCarriesItsOperatorsWords() {
        LostTalesQuestDefinition quest = new LostTalesQuestDefinition(
                "losttales:server/road", "Die alte Straße", "", false, false,
                LostTalesQuestDefinition.START_MODE_ITEM, null,
                Collections.singletonMap("experience", "5"), null, null, null,
                null, null,
                Collections.singletonList(new LostTalesQuestStageDefinition("10",
                        Arrays.asList(
                                new LostTalesQuestObjectiveDefinition("walk",
                                        "goto", "Geh die Straße entlang.", false,
                                        Collections.<String, String>emptyMap()),
                                new LostTalesQuestObjectiveDefinition("wheat",
                                        "gather", "", false,
                                        Collections.singletonMap("count", "3"))))));
        ChatQuestCard card = LostTalesQuestShareResolver.card(quest,
                new com.ninuna.losttales.quest.progress.LostTalesQuestProgress(
                        "losttales:server/road", 0, "10",
                        Collections.singletonMap("wheat", Integer.valueOf(2)),
                        0L, 0L));
        assertEquals(ChatQuestCard.Source.SERVER, card.getSource());
        assertEquals("Die alte Straße",
                LostTalesQuestCardWords.title("losttales:server/road", card));
        assertEquals("Geh die Straße entlang. (0/1); Gather 3 items. (2/3)",
                LostTalesQuestCardWords.objectives("losttales:server/road", card));
        assertEquals("5 experience", LostTalesQuestCardWords.reward(card));
    }

    /** LOTR's welcome quest is worded by the mod's own lines; nothing it says travels. */
    @Test
    public void lotrsWelcomeQuestIsWordedByTheModsLines() {
        ChatQuestCard card = new ChatQuestCard(
                ChatQuestCard.Source.LOTR_WELCOME, "", "main_story",
                Collections.<ChatQuestCard.Objective>emptyList(),
                Collections.<String, String>emptyMap());
        assertEquals(LostTalesLangFile.english().get(
                "gui.losttales.quest.lotr.grey_wanderer"),
                LostTalesQuestCardWords.title("lotr:miniquest:x", card));
        assertEquals(LostTalesLangFile.english().get(
                "gui.losttales.quest.lotr.reward.tutorial"),
                LostTalesQuestCardWords.reward(card));
    }

    private static LostTalesQuestDefinition nia() {
        return LostTalesQuestRegistry.getQuest(NIA);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
