package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Names are compared by how they look (CHAR-11): a name uses the letters
 * of one script, and look-alike letters count as the same.
 */
public final class CharacterNameLookTest {

    private static final UUID OWNER =
            UUID.fromString("71000000-0000-0000-0000-000000000031");

    @Test
    public void lookAlikeLettersMakeNoNewName() {
        // A Cyrillic е and а, a zero for an o, rn for an m.
        assertTrue(CharacterNames.same("Server", "Sеrver"));
        assertTrue(CharacterNames.same("Аldric", "Aldric"));
        assertTrue(CharacterNames.same("Bob", "B0b"));
        assertTrue(CharacterNames.same("Amir", "Arnir"));
        assertTrue(CharacterNames.isVoice("Sеrvеr"));
        // A letter that only looks like a sign stays a letter.
        assertFalse(CharacterNames.same("ðorin", "Orin"));
        assertFalse(CharacterNames.same("Aldric", "Aldrid"));
    }

    @Test
    public void aNameUsesTheLettersOfOneScript() {
        assertTrue(CharacterNames.inOneScript("Aldric of Bree-land 2"));
        assertTrue(CharacterNames.inOneScript("Féanor"));
        assertTrue(CharacterNames.inOneScript("Алдрик"));
        assertFalse(CharacterNames.inOneScript("Sеrver"));
        assertFalse(CharacterNames.inOneScript("Ald山"));
        // The scripts a language writes together count as one.
        assertTrue(CharacterNames.inOneScript("山田たろう"));
        assertTrue(CharacterNames.inOneScript("金민준"));
        assertFalse(CharacterNames.inOneScript("た민"));
    }

    @Test
    public void theValidatorRefusesTwoScriptsAndALookAlikeName() {
        assertEquals(CharacterErrorId.INVALID_NAME_SCRIPT,
                validate(new CharacterRoster(OWNER), "Sеrver").getErrorId());
        assertFalse(CharacterValidator.isWellFormedName("Aldрic"));
        CharacterRoster roster = new CharacterRoster(OWNER, 3, null, 0L);
        assertTrue(roster.addCharacter(RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0).name("Ace").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0").age(30)
                .startingFaction("lotr:bree").createdAt(1L).build()));
        // All Cyrillic, so one script, and it reads as Ace.
        assertEquals(CharacterErrorId.DUPLICATE_NAME,
                validate(roster, "Асе").getErrorId());
        assertTrue(validate(roster, "Aldric").isValid());
    }

    @Test
    public void theBundledListReadsAndALineOutOfShapeIsSkipped() throws IOException {
        assertEquals("e", NameLookAlikes.of(0x0435));
        assertEquals("rn", NameLookAlikes.of('m'));
        assertNull(NameLookAlikes.of('a'));
        Map<Integer, String> read = NameLookAlikes.read(new BufferedReader(new StringReader(
                "# a comment\n0430 0061\nnot hex\n02BC\n110000 0061\n")));
        assertEquals(2, read.size());
        assertEquals("a", read.get(Integer.valueOf(0x0430)));
        assertEquals("", read.get(Integer.valueOf(0x02BC)));
    }

    private static CharacterAppearanceValidationResult validate(CharacterRoster roster,
                                                                String name) {
        return CharacterValidator.validateAppearance(roster, null,
                name, CharacterRaceRegistry.HUMAN,
                CharacterGenderRegistry.MALE,
                CharacterSkinRegistry.getDefaultSkinId(CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, OWNER),
                CharacterBodyTypeRegistry.WIDE,
                CharacterChestTypeRegistry.NONE, "", 25);
    }
}
