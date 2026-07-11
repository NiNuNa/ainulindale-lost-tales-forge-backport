package com.ninuna.losttales.character.registry;

/** Common-side deterministic gameplay-profile fallbacks. */
public final class CharacterRaceGameplayRegistry {

    public static final CharacterRaceGameplayProfile DEFAULT =
            new CharacterRaceGameplayProfile(
                    "",
                    "net.minecraft.entity.player.EntityPlayer",
                    0.60F,
                    1.80F,
                    1.62F,
                    1.54F,
                    20.0D,
                    1.0D,
                    2.0D,
                    false
            );

    private CharacterRaceGameplayRegistry() {}

    public static CharacterRaceGameplayProfile getFallback(String raceId) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        return definition == null
                ? DEFAULT : definition.createFallbackGameplayProfile();
    }
}
