package com.ninuna.losttales.quest;

import com.ninuna.losttales.item.LostTalesItemQuestStarter;
import com.ninuna.losttales.quest.missive.MissiveSeal;
import com.ninuna.losttales.quest.missive.MissiveWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A starter item (QUEST-34, QUEST-39): its seal covers the quest the
 * starter names and the starter's own nonce, so a starter changed by hand
 * to name another quest, or made by hand, carries no seal that fits; only
 * the mod's own starter item names a quest; and its name is built in each
 * reader's language, a bundled quest's from its lang line, a server
 * quest's from the words its operator wrote, which the starter carries.
 */
public final class QuestStartersTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NOTE = "losttales:tutorial/starter_note";
    private static final List<String> GERMAN_KEYS = Arrays.asList(
            "item.quest_starter.named", LostTalesQuestWords.titleKey(NOTE));
    private static final Item STARTER = new LostTalesItemQuestStarter()
            .setUnlocalizedName("quest_starter");

    @BeforeClass
    public static void readInEnglish() {
        StringTranslate.inject(QuestStartersTest.class
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

    private static byte[] key(int seed) {
        byte[] key = new byte[MissiveSeal.LENGTH];
        Arrays.fill(key, (byte) seed);
        return key;
    }

    @Test
    public void aStartersSealCoversItsQuestAndNonce() {
        byte[] nonce = new byte[] { 1, 2, 3, 4 };
        byte[] seal = MissiveSeal.signBytes(key(7),
                QuestStarters.content("losttales:path/survivalist/first_sparks", nonce));
        assertNotNull(seal);
        assertTrue(MissiveSeal.verifiesBytes(key(7), QuestStarters.content(
                "losttales:path/survivalist/first_sparks", nonce), seal));
        assertFalse("another quest", MissiveSeal.verifiesBytes(key(7),
                QuestStarters.content("losttales:tutorial/starter_note", nonce), seal));
        assertFalse("another nonce", MissiveSeal.verifiesBytes(key(7),
                QuestStarters.content("losttales:path/survivalist/first_sparks",
                        new byte[] { 1, 2, 3, 5 }), seal));
        assertFalse("another world's key", MissiveSeal.verifiesBytes(key(8),
                QuestStarters.content("losttales:path/survivalist/first_sparks", nonce),
                seal));
    }

    /** Only the mod's starter names a quest; paper carrying the same data is paper. */
    @Test
    public void onlyTheStarterItemNamesAQuest() {
        ItemStack starter = starter(NOTE, null);
        assertTrue(QuestStarters.isStarter(starter));
        assertEquals(NOTE, QuestStarters.questIdOf(starter));

        ItemStack paper = new ItemStack(new Item());
        paper.setTagCompound((NBTTagCompound) starter.getTagCompound().copy());
        paper.getTagCompound().setByteArray(QuestStarters.TAG_SEAL, new byte[] { 1 });
        paper.getTagCompound().setByteArray(QuestStarters.TAG_NONCE, new byte[] { 2 });
        assertFalse(QuestStarters.isStarter(paper));
        assertEquals("", QuestStarters.questIdOf(paper));
        assertFalse("never sealed", QuestStarters.isGenuine(null, paper));

        assertEquals("a blank starter names nothing", "",
                QuestStarters.questIdOf(new ItemStack(STARTER)));
        StringBuilder pastBound = new StringBuilder("losttales:");
        while (LostTalesQuestIds.utf8Bytes(pastBound.toString()) <= LostTalesQuestIds.MAX_BYTES) {
            pastBound.append('a');
        }
        assertEquals("an id past the bound", "",
                QuestStarters.questIdOf(starter(pastBound.toString(), null)));
        assertEquals("", QuestStarters.questIdOf(null));
    }

    /** A server quest's starter carries its operator's words; a bundled quest's or a missive's none. */
    @Test
    public void onlyAServerQuestsStarterCarriesItsTitle() {
        assertEquals("The Old Road", QuestStarters.carriedTitle(
                quest("losttales:regional/old_road", " The Old Road ", false, null)));
        assertEquals("", QuestStarters.carriedTitle(quest(NOTE, "The Sealed Note", true, null)));
        assertEquals("", QuestStarters.carriedTitle(quest("missive/abc", "Wolves", false,
                Collections.singletonMap(MissiveWords.TITLE, "wolves"))));
        StringBuilder title = new StringBuilder();
        while (LostTalesQuestIds.utf8Bytes(title.toString()) <= QuestStarters.MAX_TITLE_BYTES) {
            title.append('é');
        }
        assertTrue(LostTalesQuestIds.utf8Bytes(QuestStarters.carriedTitle(
                quest("losttales:regional/long", title.toString(), false, null)))
                <= QuestStarters.MAX_TITLE_BYTES);
    }

    @Test
    public void aStarterShowsWhatItCarriesElseItsLineElseItsId() {
        assertEquals("The Old Road", QuestStarters.title("x", "The Old Road", "Line"));
        assertEquals("Line", QuestStarters.title("x", "", "Line"));
        assertEquals("x", QuestStarters.title("x", "", ""));
        assertEquals("x", QuestStarters.title("x", null, null));
    }

    /** A bundled quest's starter is named in the reader's language, from its quest's lang line. */
    @Test
    public void aBundledQuestsStarterIsNamedInTheReadersLanguage() {
        ItemStack starter = starter(NOTE, null);
        assertEquals("Quest Starter: The Sealed Note", starter.getDisplayName());
        inject("item.quest_starter.named=Questbeginn: %s\n"
                + LostTalesQuestWords.titleKey(NOTE) + "=Die versiegelte Notiz\n");
        assertEquals("Questbeginn: Die versiegelte Notiz", starter.getDisplayName());
    }

    /** A server quest's starter keeps its operator's words in every language. */
    @Test
    public void aServerQuestsStarterKeepsItsOperatorsWords() {
        ItemStack starter = starter("losttales:regional/old_road", "The Old Road");
        assertEquals("Quest Starter: The Old Road", starter.getDisplayName());
        inject("item.quest_starter.named=Questbeginn: %s\n");
        assertEquals("Questbeginn: The Old Road", starter.getDisplayName());
    }

    @Test
    public void aBlankStarterIsAQuestStarter() {
        assertEquals("Quest Starter", new ItemStack(STARTER).getDisplayName());
    }

    private static ItemStack starter(String questId, String title) {
        ItemStack stack = new ItemStack(STARTER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(QuestStartGate.TAG_QUEST_ID, questId);
        if (title != null) {
            tag.setString(QuestStarters.TAG_TITLE, title);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    private static LostTalesQuestDefinition quest(String id, String title, boolean bundled,
                                                  Map<String, String> words) {
        return new LostTalesQuestDefinition(id, title, "", false, false,
                LostTalesQuestDefinition.START_MODE_LOCKED, null, null, null, null,
                null, null, null, null, bundled, words, null);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
