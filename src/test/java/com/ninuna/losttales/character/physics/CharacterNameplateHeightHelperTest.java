package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

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

}
