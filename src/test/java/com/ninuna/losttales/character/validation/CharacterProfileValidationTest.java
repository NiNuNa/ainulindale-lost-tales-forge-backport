package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Creation and a later edit hold a character's profile and age to one
 * set of bounds: each About text, fact and glance to its length and to
 * printable characters, a glance to one of the chat's emoji and a title,
 * and the profile is judged as it is stored. Each About text has its own
 * length: the History 2,048 characters, Appearance and Personality 512.
 */
public final class CharacterProfileValidationTest {

    @Test
    public void aProfileInsideTheBoundsIsAccepted() {
        assertTrue(CharacterValidator.validateProfile(CharacterProfile.EMPTY,
                CharacterValidator.MIN_AGE).isValid());
        CharacterProfile full = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            full = full.withSection(section, repeat(section.getMaxLength()));
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            full = full.withFact(fact, repeat(CharacterProfile.MAX_FACT_LENGTH));
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>();
        for (int index = 0; index < CharacterProfile.MAX_GLANCES; index++) {
            glances.add(new CharacterProfile.Glance("smiley",
                    repeat(CharacterProfile.MAX_GLANCE_TITLE_LENGTH),
                    repeat(CharacterProfile.MAX_GLANCE_LINE_LENGTH)));
        }
        assertTrue(CharacterValidator.validateProfile(full.withGlances(glances),
                CharacterValidator.MAX_AGE).isValid());
    }

    @Test
    public void anAgeOutsideTheBoundsIsRefused() {
        assertEquals(CharacterErrorId.INVALID_AGE, CharacterValidator
                .validateProfile(CharacterProfile.EMPTY,
                        CharacterValidator.MIN_AGE - 1).getErrorId());
        assertEquals(CharacterErrorId.INVALID_AGE, CharacterValidator
                .validateProfile(CharacterProfile.EMPTY,
                        CharacterValidator.MAX_AGE + 1).getErrorId());
    }

    @Test
    public void aTextTooLongOrCarryingCodesIsRefused() {
        assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT, error(
                history(repeat(CharacterProfile.Section.HISTORY
                        .getMaxLength() + 1))));
        assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT,
                error(history("§cRed")));
        assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT,
                error(history("a\u0007bell")));
        assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT, error(
                CharacterProfile.EMPTY.withFact(CharacterProfile.Fact.HEIGHT,
                        repeat(CharacterProfile.MAX_FACT_LENGTH + 1))));
        assertEquals("a fact is one line", CharacterErrorId.INVALID_PROFILE_TEXT,
                error(CharacterProfile.EMPTY.withFact(
                        CharacterProfile.Fact.HOME, "Bree\nShire")));
        assertTrue("an About text keeps its paragraphs", CharacterValidator
                .validateProfile(history("One.\n\nTwo."), 20).isValid());
    }

    @Test
    public void eachAboutTextIsHeldToItsOwnLength() {
        assertEquals(2048, CharacterProfile.Section.HISTORY.getMaxLength());
        assertEquals(512, CharacterProfile.Section.APPEARANCE.getMaxLength());
        assertEquals(512, CharacterProfile.Section.PERSONALITY.getMaxLength());
        assertTrue(CharacterValidator.validateProfile(history(repeat(2048)),
                20).isValid());
        for (CharacterProfile.Section section : new CharacterProfile.Section[] {
                CharacterProfile.Section.APPEARANCE,
                CharacterProfile.Section.PERSONALITY}) {
            assertTrue(section.getId(), CharacterValidator.validateProfile(
                    CharacterProfile.EMPTY.withSection(section, repeat(512)),
                    20).isValid());
            assertEquals("a History's length is not " + section.getId() + "'s",
                    CharacterErrorId.INVALID_PROFILE_TEXT, error(
                            CharacterProfile.EMPTY.withSection(section,
                                    repeat(513))));
        }
        assertTrue("the length counts characters, not UTF-16 units",
                CharacterValidator.isValidSection(
                        CharacterProfile.Section.HISTORY, repeat("\uD83D\uDE00",
                                CharacterProfile.Section.HISTORY.getMaxLength())));
    }

    @Test
    public void aGlanceNeedsAChatEmojiATitleAndItsBounds() {
        assertEquals(CharacterErrorId.INVALID_GLANCE,
                error(glance("not_an_emoji", "Tired", "")));
        assertEquals(CharacterErrorId.INVALID_GLANCE,
                error(glance("smiley", "", "No title")));
        assertEquals(CharacterErrorId.INVALID_GLANCE, error(glance("smiley",
                "Tired", repeat(CharacterProfile.MAX_GLANCE_LINE_LENGTH + 1))));
        List<CharacterProfile.Glance> six =
                new ArrayList<CharacterProfile.Glance>();
        for (int index = 0; index <= CharacterProfile.MAX_GLANCES; index++) {
            six.add(new CharacterProfile.Glance("smiley", "Tired", ""));
        }
        assertEquals(CharacterErrorId.INVALID_GLANCE,
                error(CharacterProfile.EMPTY.withGlances(six)));
        assertTrue(CharacterValidator.validateProfile(
                glance("smiley", "Tired", ""), 20).isValid());
    }

    @Test
    public void aProfileIsStoredWithItsWhitespaceFolded() {
        CharacterProfile typed = CharacterProfile.EMPTY
                .withSection(CharacterProfile.Section.APPEARANCE,
                        "  Tall,\t grey.\r\n\n\n\nScarred.  ")
                .withFact(CharacterProfile.Fact.EYES, "  grey   green ")
                .withGlances(Collections.singletonList(
                        new CharacterProfile.Glance(" smiley ", " Tired  out ",
                                "  after\nthe road ")));
        CharacterProfile stored = CharacterValidator.normalizeProfile(typed);
        assertEquals("Tall, grey.\n\nScarred.",
                stored.section(CharacterProfile.Section.APPEARANCE));
        assertEquals("grey green", stored.fact(CharacterProfile.Fact.EYES));
        assertEquals("smiley", stored.glances().get(0).getEmoji());
        assertEquals("Tired out", stored.glances().get(0).getTitle());
        assertEquals("after the road", stored.glances().get(0).getLine());
        String padded = "   " + repeat(CharacterProfile.Section.HISTORY
                .getMaxLength()) + "\t\t";
        assertTrue("space that folds away does not count against the limit",
                CharacterValidator.validateProfile(CharacterValidator
                        .normalizeProfile(history(padded)), 20).isValid());
    }

    /**
     * The invisible marks that turn text around or take no room are
     * dropped as the profile is stored, as the chat drops them, and a
     * profile still holding one is refused.
     */
    @Test
    public void invisibleMarksAreDroppedAndRefused() {
        String[] marks = {"‮", "‪", "⁦", "⁩", "​",
                "‌", "‍", "‎", "‏", "⁠", "﻿",
                "؜"};
        for (String mark : marks) {
            String typed = "Ar" + mark + "agorn";
            CharacterProfile stored = CharacterValidator.normalizeProfile(
                    history(typed + "\n\nof" + mark + " Gondor")
                            .withFact(CharacterProfile.Fact.EYES, mark + "Grey")
                            .withGlances(Collections.singletonList(
                                    new CharacterProfile.Glance("smiley",
                                            "Pi" + mark + "pe", mark))));
            assertEquals("Aragorn\n\nof Gondor",
                    stored.section(CharacterProfile.Section.HISTORY));
            assertEquals("Grey", stored.fact(CharacterProfile.Fact.EYES));
            assertEquals("Pipe", stored.glances().get(0).getTitle());
            assertEquals("", stored.glances().get(0).getLine());
            assertEquals(CharacterErrorId.NONE, error(stored));
            assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT,
                    error(history(typed)));
            assertEquals(CharacterErrorId.INVALID_PROFILE_TEXT,
                    error(CharacterProfile.EMPTY.withFact(
                            CharacterProfile.Fact.EYES, typed)));
        }
    }

    private static CharacterErrorId error(CharacterProfile profile) {
        return CharacterValidator.validateProfile(profile, 20).getErrorId();
    }

    private static CharacterProfile history(String text) {
        return CharacterProfile.EMPTY.withSection(
                CharacterProfile.Section.HISTORY, text);
    }

    private static CharacterProfile glance(String emoji, String title,
                                           String line) {
        return CharacterProfile.EMPTY.withGlances(Collections.singletonList(
                new CharacterProfile.Glance(emoji, title, line)));
    }

    private static String repeat(int count) {
        return repeat("a", count);
    }

    private static String repeat(String unit, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(unit);
        }
        return text.toString();
    }
}
