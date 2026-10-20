package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;

/**
 * How large a race's posed figure stands in a screen and where its feet
 * are, the one rule for every figure drawn with no entity: the Characters
 * page's and the creator's.
 */
public final class CharacterGuiPreviewLayout {

    private CharacterGuiPreviewLayout() {}

    /** The race's figure as a share of a human's; 1 for a race not known. */
    public static float scaleOf(String raceId) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        return definition == null ? 1.0F : definition.getGuiPreviewScale();
    }

    public static int scale(String raceId, int baseScale) {
        return Math.max(1, Math.round(baseScale * scaleOf(raceId)));
    }

    public static int baselineY(String raceId, int baseY) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        return baseY + (definition == null
                ? 0 : definition.getGuiPreviewVerticalOffset());
    }
}
