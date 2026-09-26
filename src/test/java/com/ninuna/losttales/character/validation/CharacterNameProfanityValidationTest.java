package com.ninuna.losttales.character.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

/**
 * A name holding a word the chat filters is refused, by the bundled list
 * and by the words the server adds; the reason is its own error. A
 * description is held to the same words, and a lore character's name is
 * nobody else's.
 */
public final class CharacterNameProfanityValidationTest {

    private static final UUID OWNER =
            UUID.fromString("72000000-0000-0000-0000-000000000018");

    @After
    public void tearDown() {
        ChatProfanityCatalog.resetToBundled();
    }

    @Test
    public void aBundledWordInANameIsRefused() {
        assertEquals(CharacterErrorId.INVALID_NAME_PROFANE,
                validate("Fucking Aldric").getErrorId());
        assertEquals("endings and case count too",
                CharacterErrorId.INVALID_NAME_PROFANE,
                validate("SHITTY Bob").getErrorId());
    }

    @Test
    public void aServersOwnWordIsRefusedOnceInstalled() {
        assertTrue(validate("Grumbold").isValid());
        ChatProfanityCatalog.installServerWords(ChatProfanityWords.parse(
                new String[] {"grumbold=grumpy"}, ChatProfanityWords.MAX_WORDS, null));
        assertEquals(CharacterErrorId.INVALID_NAME_PROFANE,
                validate("Grumbold").getErrorId());
    }

    @Test
    public void anOrdinaryNameStandsWholeWordsOnly() {
        assertTrue(validate("Aldric of Bree").isValid());
        assertTrue("assassin holds no listed word", validate("Assassin").isValid());
        assertTrue(validate("Scunthorpe").isValid());
    }

    @Test
    public void aListedWordInAProfileIsRefused() {
        assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT_PROFANE,
                CharacterValidator.validateProfile(history("A shitty ranger."),
                        30).getErrorId());
        assertTrue(CharacterValidator.validateProfile(
                history("A ranger of the North."), 30).isValid());
        assertEquals("a glance is held to the same words",
                CharacterValidator.validateProfile(CharacterProfile.EMPTY
                        .withGlances(Collections.singletonList(
                                new CharacterProfile.Glance("smiley",
                                        "Shitty", ""))), 30).getErrorId(),
                CharacterErrorId.INVALID_PROFILE_TEXT_PROFANE);
    }

    private static CharacterProfile history(String text) {
        return CharacterProfile.EMPTY.withSection(
                CharacterProfile.Section.HISTORY, text);
    }

    @Test
    public void aLoreCharactersNameIsReservedHoweverItIsWritten() {
        assertEquals(CharacterErrorId.NAME_RESERVED,
                validate("Glorfindel").getErrorId());
        assertEquals(CharacterErrorId.NAME_RESERVED,
                validate("eomer").getErrorId());
        assertTrue(validate("Glorfin").isValid());
    }

    private static CharacterAppearanceValidationResult validate(String name) {
        return CharacterValidator.validateAppearance(
                new CharacterRoster(OWNER), null, null,
                name, CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE,
                CharacterSkinRegistry.getDefaultSkinId(CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, OWNER),
                CharacterBodyTypeRegistry.WIDE,
                CharacterChestTypeRegistry.NONE, "", 25);
    }
}
