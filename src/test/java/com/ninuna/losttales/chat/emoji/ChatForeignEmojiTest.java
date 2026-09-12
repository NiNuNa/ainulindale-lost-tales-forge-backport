package com.ninuna.losttales.chat.emoji;

import com.ninuna.losttales.chat.ChatReactionSummary;
import java.nio.charset.Charset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A foreign key is what Discord calls an emoji the registry lacks, one
 * key per emoji, never a registry name, strictly shaped and within the
 * wire's bound; its label is what a chip's card shows.
 */
public final class ChatForeignEmojiTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** U+1F984, which the registry does not carry. */
    private static final String UNICORN = "🦄";

    @Test
    public void noRegistryNameIsEverAForeignKey() {
        for (ChatEmoji emoji : ChatEmoji.values()) {
            String name = emoji.getName();
            assertFalse(name, ChatForeignEmoji.isForeign(name));
            assertTrue(name, ChatForeignEmoji.isReactionKey(name));
            for (int index = 0; index < name.length(); index++) {
                char character = name.charAt(index);
                assertTrue("registry names stay lowercase ASCII: " + name,
                        (character >= 'a' && character <= 'z')
                                || (character >= '0' && character <= '9')
                                || character == '_');
            }
        }
    }

    @Test
    public void aCustomEmojiIsItsNameAndId() {
        assertEquals("partyparrot:556",
                ChatForeignEmoji.customKey("partyparrot", "556"));
        assertEquals("Party_Parrot2:123456789012345678",
                ChatForeignEmoji.customKey("Party_Parrot2", "123456789012345678"));
        assertTrue(ChatForeignEmoji.isCustom("partyparrot:556"));
        assertTrue(ChatForeignEmoji.isForeign("partyparrot:556"));
        assertFalse(ChatForeignEmoji.isUnicode("partyparrot:556"));
        assertTrue(ChatForeignEmoji.isReactionKey("partyparrot:556"));
        assertEquals("the card names it as Discord does", ":Party_Parrot2:",
                ChatForeignEmoji.label("Party_Parrot2:123456789012345678"));
        assertEquals("the bot reacts with name:id", "partyparrot:556",
                ChatForeignEmoji.discordForm("partyparrot:556"));
    }

    @Test
    public void aCustomEmojiOutOfDiscordsShapeHasNoKey() {
        assertNull("one character", ChatForeignEmoji.customKey("p", "556"));
        assertNull("thirty-three characters",
                ChatForeignEmoji.customKey(repeat("p", 33), "556"));
        assertNotNull(ChatForeignEmoji.customKey(repeat("p", 32), "556"));
        assertNull(ChatForeignEmoji.customKey("party-parrot", "556"));
        assertNull(ChatForeignEmoji.customKey("party parrot", "556"));
        assertNull(ChatForeignEmoji.customKey("party:parrot", "556"));
        assertNull(ChatForeignEmoji.customKey("partyparrot", ""));
        assertNull(ChatForeignEmoji.customKey("partyparrot", "12a"));
        assertNull("a leading zero would be a second spelling of one id",
                ChatForeignEmoji.customKey("partyparrot", "0556"));
        assertNull(ChatForeignEmoji.customKey("partyparrot", repeat("1", 21)));
        assertNotNull(ChatForeignEmoji.customKey("partyparrot", repeat("1", 20)));
        assertNull(ChatForeignEmoji.customKey(null, "556"));
        assertNull(ChatForeignEmoji.customKey("partyparrot", null));
        assertFalse(ChatForeignEmoji.isCustom("partyparrot:556:1"));
        assertFalse(ChatForeignEmoji.isCustom(":556"));
        assertFalse(ChatForeignEmoji.isCustom("partyparrot:"));
        assertFalse(ChatForeignEmoji.isCustom("partyparrot"));
    }

    @Test
    public void aCustomEmojiNamedAsTheRegistrysIsTheRegistrys() {
        assertNull(ChatForeignEmoji.customKey("Cutesy", "555"));
        assertNull("an alias resolves too",
                ChatForeignEmoji.customKey("skeleton", "555"));
        assertFalse(ChatForeignEmoji.isForeign("cutesy:555"));
    }

    @Test
    public void aUnicodeEmojiTheRegistryLacksIsItself() {
        assertEquals(UNICORN, ChatForeignEmoji.unicodeKey(UNICORN));
        assertTrue(ChatForeignEmoji.isUnicode(UNICORN));
        assertFalse(ChatForeignEmoji.isCustom(UNICORN));
        assertEquals("named as Discord names it", ":unicorn:",
                ChatForeignEmoji.label(UNICORN));
        assertEquals(UNICORN, ChatForeignEmoji.discordForm(UNICORN));

        String family = "👨‍👩‍👧";
        assertEquals(family, ChatForeignEmoji.unicodeKey(family));
        assertEquals(":family_mwg:", ChatForeignEmoji.label(family));

        String keycap = "1️⃣";
        assertEquals(keycap, ChatForeignEmoji.unicodeKey(keycap));
        assertEquals("the variation selector is left out of the lookup",
                ":one:", ChatForeignEmoji.label(keycap));

        assertNotNull("a flag", ChatForeignEmoji.unicodeKey(
                "🇩🇪"));
        assertNotNull("a symbol in the basic plane",
                ChatForeignEmoji.unicodeKey("☕"));
        String heartOnFire = "❤️‍🔥";
        assertEquals("it opens with the registry's heart but is not it",
                heartOnFire, ChatForeignEmoji.unicodeKey(heartOnFire));
    }

    @Test
    public void aUnicodeEmojiTheRegistryCarriesIsTheRegistrys() {
        assertNull(ChatForeignEmoji.unicodeKey("😄"));
        assertNull("without its selector",
                ChatForeignEmoji.unicodeKey("❤"));
        assertNull(ChatForeignEmoji.unicodeKey("❤️️"));
        assertFalse(ChatForeignEmoji.isForeign("😄"));
        assertEquals(ChatEmoji.SMILE,
                ChatForeignEmoji.registryEmojiOf("😄️"));
        assertNull(ChatForeignEmoji.registryEmojiOf(UNICORN));
    }

    @Test
    public void anythingElseIsNoKey() {
        assertNull(ChatForeignEmoji.unicodeKey(""));
        assertNull(ChatForeignEmoji.unicodeKey(null));
        assertNull("words", ChatForeignEmoji.unicodeKey("abc"));
        assertNull("a digit with no keycap", ChatForeignEmoji.unicodeKey("1"));
        assertNull("a letter first", ChatForeignEmoji.unicodeKey("a" + UNICORN));
        assertNull("a space after", ChatForeignEmoji.unicodeKey(UNICORN + " "));
        assertNull("a joiner first", ChatForeignEmoji.unicodeKey(
                String.valueOf((char)0x200D) + UNICORN));
        assertNull("a bidirectional override", ChatForeignEmoji.unicodeKey(
                UNICORN + String.valueOf((char)0x202E)));
        assertNull("half a surrogate pair", ChatForeignEmoji.unicodeKey("\uD83E"));
        assertNull("a colon", ChatForeignEmoji.unicodeKey(UNICORN + ":"));
        assertNull("a letter of another script",
                ChatForeignEmoji.unicodeKey("中"));
        assertNotNull(ChatForeignEmoji.unicodeKey(repeat(UNICORN, 16)));
        assertNull(ChatForeignEmoji.unicodeKey(repeat(UNICORN, 17)));
        assertFalse(ChatForeignEmoji.isReactionKey("not_an_emoji"));
        assertFalse(ChatForeignEmoji.isReactionKey(""));
        assertFalse(ChatForeignEmoji.isReactionKey(null));
        assertEquals("", ChatForeignEmoji.label("not_an_emoji"));
        assertEquals("", ChatForeignEmoji.discordForm("not_an_emoji"));
    }

    /**
     * A key saved as foreign whose emoji the registry has come to carry
     * since is read back under the registry's name, so the registry
     * growing never makes a saved reaction unreadable.
     */
    @Test
    public void aSavedKeyTheRegistryNowCarriesIsTheRegistrysName() {
        // Keys this build takes as they are stay as they are.
        assertEquals(UNICORN, ChatForeignEmoji.restoredKey(UNICORN));
        assertEquals("partyparrot:556",
                ChatForeignEmoji.restoredKey("partyparrot:556"));
        assertEquals("smile", ChatForeignEmoji.restoredKey("smile"));
        // The registry's emoji by its Unicode, with Discord's selector or
        // without, and by a custom emoji named as one of its names.
        assertEquals("grinning", ChatForeignEmoji.restoredKey("😀"));
        assertEquals("grinning", ChatForeignEmoji.restoredKey("😀️"));
        assertEquals("an alias", "grinning",
                ChatForeignEmoji.restoredKey("grinning_face:123"));
        assertEquals("a name in any case", "cutesy",
                ChatForeignEmoji.restoredKey("Cutesy:555"));
        // Anything out of a key's shape is no key.
        assertNull(ChatForeignEmoji.restoredKey("grinning_face:0123"));
        assertNull(ChatForeignEmoji.restoredKey("grinning face:123"));
        assertNull(ChatForeignEmoji.restoredKey("not_an_emoji"));
        assertNull(ChatForeignEmoji.restoredKey("a" + UNICORN));
        assertNull(ChatForeignEmoji.restoredKey(""));
        assertNull(ChatForeignEmoji.restoredKey(null));
    }

    @Test
    public void aCustomIdIsASnowflakeSpelledOneWay() {
        assertTrue(ChatForeignEmoji.isCustomId("556"));
        assertFalse(ChatForeignEmoji.isCustomId("0556"));
        assertFalse(ChatForeignEmoji.isCustomId("55a"));
        assertFalse(ChatForeignEmoji.isCustomId(""));
        assertFalse(ChatForeignEmoji.isCustomId(null));
    }

    @Test
    public void everyKeyFitsTheWire() {
        String longestCustom = ChatForeignEmoji.customKey(repeat("p", 32),
                repeat("9", 20));
        assertNotNull(longestCustom);
        assertTrue(longestCustom.getBytes(UTF_8).length
                <= ChatReactionSummary.MAX_EMOJI_BYTES);
        String longestUnicode = ChatForeignEmoji.unicodeKey(repeat(UNICORN, 16));
        assertTrue(longestUnicode.getBytes(UTF_8).length
                <= ChatReactionSummary.MAX_EMOJI_BYTES);
    }

    @Test
    public void theRegistrysOwnKeysAreNamedAndPostedAsBefore() {
        assertEquals(":smile:", ChatForeignEmoji.label("smile"));
        assertEquals("😄", ChatForeignEmoji.discordForm("smile"));
        assertEquals("the mod's own sprites have no Discord form", "",
                ChatForeignEmoji.discordForm("cutesy"));
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < times; index++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
