package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;

/** Shared client-side preview layout derived from common race metadata. */
public final class CharacterGuiPreviewLayout {

    private CharacterGuiPreviewLayout() {}

    public static int scale(String raceId, int baseScale) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        float multiplier = definition == null ? 1.0F : definition.getGuiPreviewScale();
        return Math.max(1, Math.round(baseScale * multiplier));
    }

    public static int baselineY(String raceId, int baseY) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        return baseY + (definition == null
                ? 0 : definition.getGuiPreviewVerticalOffset());
    }
}
