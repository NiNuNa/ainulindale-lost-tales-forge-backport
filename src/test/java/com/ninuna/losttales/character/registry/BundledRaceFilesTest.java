package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The six race files shipped in the jar (CHAR-38) load with no warning and
 * hold what the races were before they were files: their bodies, sizes,
 * values, body types, starting factions and skins. Their words are the lang
 * file's, never their own.
 */
public final class BundledRaceFilesTest {

    @Test
    public void theSixLoadWithNoWarningInTheirOrder() {
        DataFiles.Result<CharacterRaceDefinition> read = DataFiles.readBundled(
                CharacterRaceRegistry.CLASSPATH, CharacterRaceFiles.INDEX,
                CharacterRaceFiles.LIST_KEY, CharacterRaceFiles.kind(null));

        assertEquals(new ArrayList<String>(), read.problems);
        List<String> ids = new ArrayList<String>();
        for (CharacterRaceDefinition race : read.values) {
            ids.add(race.getId());
        }
        assertEquals(Arrays.asList(CharacterRaceRegistry.HUMAN, CharacterRaceRegistry.ELF,
                CharacterRaceRegistry.DWARF, CharacterRaceRegistry.HOBBIT,
                CharacterRaceRegistry.ORC, CharacterRaceRegistry.URUK), ids);
    }

    @Test
    public void eachStandsOnItsOwnBodyWithItsOldValues() {
        assertRace(CharacterRaceRegistry.HUMAN, CharacterBody.HUMAN, "lotr:bree_man",
                0.60F, 1.80F, 20, 1.0, 2, 1.0F, 1.0F, 0);
        assertRace(CharacterRaceRegistry.ELF, CharacterBody.ELF, "lotr:high_elf",
                0.60F, 1.80F, 20, 1.0, 2, 1.0F, 0.96F, 0);
        assertRace(CharacterRaceRegistry.DWARF, CharacterBody.DWARF, "lotr:dwarf",
                0.50F, 1.50F, 20, 0.9, 3, 0.8125F, 1.12F, -2);
        assertRace(CharacterRaceRegistry.HOBBIT, CharacterBody.HOBBIT, "lotr:hobbit",
                0.45F, 1.20F, 16, 1.0, 2, 0.75F, 1.34F, -5);
        assertRace(CharacterRaceRegistry.ORC, CharacterBody.ORC, "lotr:mordor_orc",
                0.50F, 1.55F, 20, 1.0, 3, 0.85F, 1.08F, -2);
        assertRace(CharacterRaceRegistry.URUK, CharacterBody.URUK, "lotr:uruk_hai",
                0.60F, 1.80F, 24, 1.05, 4, 1.0F, 1.05F, -2);
    }

    @Test
    public void theHobbitStartsOnlyInTheShireOrBreeAndTheOrcsHaveOneBody() {
        CharacterRaceDefinition hobbit = CharacterRaceRegistry.get(CharacterRaceRegistry.HOBBIT);
        assertTrue(hobbit.getAllowedFactionCategories().isEmpty());
        assertEquals(Arrays.asList("lotr:hobbit", "lotr:bree"),
                new ArrayList<String>(hobbit.getAllowedFactionIds()));
        for (String orc : Arrays.asList(CharacterRaceRegistry.ORC, CharacterRaceRegistry.URUK)) {
            CharacterRaceDefinition race = CharacterRaceRegistry.get(orc);
            assertEquals(Arrays.asList(CharacterBodyTypeRegistry.NEUTRAL),
                    new ArrayList<String>(race.getAllowedBodyTypeIds()));
            assertTrue(race.getAllowedFactionCategories().contains(CharacterFactionCategory.ORC));
        }
    }

    /**
     * Orcs and Uruks may start among the half-trolls, who are no race of
     * their own (CHAR-09); LOTR types the faction as men, so Humans may too.
     */
    @Test
    public void orcsAndUruksMayStartInTheHalfTrollFaction() {
        CharacterFactionDefinition halfTrolls = new CharacterFactionDefinition("lotr:half_troll",
                true, Collections.singleton(CharacterFactionCategory.HUMAN));
        for (String orc : Arrays.asList(CharacterRaceRegistry.ORC, CharacterRaceRegistry.URUK)) {
            CharacterRaceDefinition race = CharacterRaceRegistry.get(orc);
            assertEquals(Arrays.asList("lotr:half_troll"),
                    new ArrayList<String>(race.getAllowedFactionIds()));
            assertTrue(orc, race.isCompatibleWith(halfTrolls));
        }
        assertTrue(CharacterRaceRegistry.get(CharacterRaceRegistry.HUMAN)
                .isCompatibleWith(halfTrolls));
        for (String other : Arrays.asList(CharacterRaceRegistry.ELF,
                CharacterRaceRegistry.DWARF, CharacterRaceRegistry.HOBBIT)) {
            assertFalse(other, CharacterRaceRegistry.get(other).isCompatibleWith(halfTrolls));
        }
    }

    /** The skins the races offer, by count, as the registry registered them. */
    @Test
    public void theSkinsAreThoseTheRacesOffered() {
        assertSkins(CharacterRaceRegistry.HUMAN, CharacterBodyTypeRegistry.MASCULINE, 1 + 30 + 3 + 4
                + 4 + 10 + 3 + 5 + 5 + 5 + 5 + 4 + 5 + 5 + 6 + 4);
        assertSkins(CharacterRaceRegistry.HUMAN, CharacterBodyTypeRegistry.FEMININE, 1 + 9 + 2 + 4
                + 3 + 14 + 4 + 4 + 3 + 3 + 3 + 3 + 3 + 5 + 7 + 3);
        assertSkins(CharacterRaceRegistry.DWARF, CharacterBodyTypeRegistry.MASCULINE, 1 + 3 + 3 + 1);
        assertSkins(CharacterRaceRegistry.HOBBIT, CharacterBodyTypeRegistry.FEMININE, 1 + 13);
        assertSkins(CharacterRaceRegistry.ORC, CharacterBodyTypeRegistry.NEUTRAL, 1 + 8);
        assertSkins(CharacterRaceRegistry.URUK, CharacterBodyTypeRegistry.NEUTRAL, 1 + 3 + 3);
        assertEquals("losttales:textures/skins/dwarf/exiled_male/0.png",
                CharacterSkinRegistry.get("losttales:dwarf_exiled_masculine_0").getTextureLocation());
        assertEquals("lotr:mob/orc/urukHai/2.png",
                CharacterSkinRegistry.get("losttales:uruk_hai_2").getTextureLocation());
    }

    @Test
    public void everyBundledWordIsTheLangFiles() {
        Map<String, String> english = LostTalesLangFile.english();
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getBundled()) {
            assertEquals(race.getId() + " writes no words of its own", "", race.getName());
            String path = race.getId().substring(race.getId().indexOf(':') + 1);
            assertTrue(race.getId(), english.containsKey("gui.losttales.character.race." + path));
            for (CharacterRaceSkins skins : race.getSkins()) {
                assertEquals(skins.getGroup(), "", skins.getName());
                assertTrue(skins.getGroup(), english.containsKey(
                        "gui.losttales.character.skin_group." + skins.getGroup()));
            }
            assertFalse(race.isStandIn());
        }
    }

    private static void assertRace(String id, CharacterBody body, String valuesFrom,
                                   float width, float height, double health, double speed,
                                   double attack, float renderer, float preview, int offset) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(id);
        assertEquals(id, body, race.getBody());
        assertEquals(id, valuesFrom, race.getLotrRaceAssociation());
        assertEquals(id, width, race.getWidth(), 0.0F);
        assertEquals(id, height, race.getHeight(), 0.0F);
        assertEquals(id, height * 0.85F, race.getStandingEyeHeight(), 0.0F);
        assertEquals(id, health, race.getMaxHealth(), 0.0D);
        assertEquals(id, speed, race.getMovementSpeedMultiplier(), 0.0D);
        assertEquals(id, attack, race.getAttackDamage(), 0.0D);
        assertEquals(id, renderer, race.getRendererScale(), 0.0F);
        assertEquals(id, preview, race.getGuiPreviewScale(), 0.0F);
        assertEquals(id, offset, race.getGuiPreviewVerticalOffset());
    }

    private static void assertSkins(String raceId, String bodyTypeId, int count) {
        List<CharacterSkinDefinition> skins =
                CharacterSkinRegistry.getCompatibleSkins(raceId, bodyTypeId);
        assertEquals(raceId + " " + bodyTypeId, count, skins.size());
        assertTrue(skins.get(0).isAccountSkin());
    }
}
