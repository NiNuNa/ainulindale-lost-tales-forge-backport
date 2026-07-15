package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CharacterNameplateHeightHelperTest {

    private static final float EPSILON = 0.0001F;

    @Test
    public void everyRaceLabelClearsBothPhysicalAndRenderedBody() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            float extra = CharacterNameplateHeightHelper.resolveExtraHeight(
                    race.getHeight(), race.getRendererScale());
            float anchor = race.getHeight() + extra;
            float visual = CharacterNameplateHeightHelper.VANILLA_MODEL_HEIGHT
                    * race.getRendererScale();
            assertTrue(race.getId(), anchor + EPSILON >= race.getHeight());
            assertTrue(race.getId(), anchor + EPSILON >= visual);
        }
    }

    @Test
    public void fullSizeUrukUsesNormalMultiplayerNameplateAnchor() {
        CharacterRaceDefinition uruk = CharacterRaceRegistry.get(
                CharacterRaceRegistry.URUK);
        CharacterRaceDefinition human = CharacterRaceRegistry.get(
                CharacterRaceRegistry.HUMAN);
        CharacterRaceDefinition elf = CharacterRaceRegistry.get(
                CharacterRaceRegistry.ELF);
        assertEquals(1.80F, uruk.getHeight(), EPSILON);
        assertEquals(0.0F,
                CharacterNameplateHeightHelper.resolveExtraHeight(
                        uruk.getHeight(), uruk.getRendererScale()), EPSILON);
        assertEquals(CharacterNameplateHeightHelper.resolveExtraHeight(
                        human.getHeight(), human.getRendererScale()),
                CharacterNameplateHeightHelper.resolveExtraHeight(
                        uruk.getHeight(), uruk.getRendererScale()), EPSILON);
        assertEquals(CharacterNameplateHeightHelper.resolveExtraHeight(
                        elf.getHeight(), elf.getRendererScale()),
                CharacterNameplateHeightHelper.resolveExtraHeight(
                        uruk.getHeight(), uruk.getRendererScale()), EPSILON);
    }
}
