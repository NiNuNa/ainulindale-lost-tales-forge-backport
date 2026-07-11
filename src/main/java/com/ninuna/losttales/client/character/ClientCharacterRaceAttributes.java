package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import net.minecraft.world.World;

import java.util.Locale;

/** Client-only formatting helpers for LOTR-derived race gameplay profiles. */
public final class ClientCharacterRaceAttributes {

    private ClientCharacterRaceAttributes() {}

    public static CharacterRaceGameplayProfile resolve(World world, String raceId) {
        if (world == null || raceId == null || raceId.length() == 0) {
            return CharacterRaceGameplayRegistry.DEFAULT;
        }
        return LotrRaceProfileAdapter.getInstance().resolve(world, raceId);
    }

    public static String formatHitbox(CharacterRaceGameplayProfile profile) {
        CharacterRaceGameplayProfile safe = safe(profile);
        return format(safe.getWidth(), 2) + " x " + format(safe.getHeight(), 2);
    }

    public static String formatEyeHeight(CharacterRaceGameplayProfile profile) {
        return format(safe(profile).getEyeHeight(), 2);
    }

    public static String formatHealth(CharacterRaceGameplayProfile profile) {
        CharacterRaceGameplayProfile safe = safe(profile);
        return format(safe.getMaxHealth(), 1)
                + " (" + format(safe.getMaxHealth() / 2.0D, 1) + " hearts)";
    }

    public static String formatMovementSpeed(CharacterRaceGameplayProfile profile) {
        return format(safe(profile).getMovementSpeedMultiplier() * 100.0D, 0) + "%";
    }

    public static String formatAttackDamage(CharacterRaceGameplayProfile profile) {
        CharacterRaceGameplayProfile safe = safe(profile);
        return format(safe.getAttackDamage(), 1)
                + " (" + format(safe.getAttackDamage() / 2.0D, 1) + " hearts)";
    }

    private static CharacterRaceGameplayProfile safe(CharacterRaceGameplayProfile profile) {
        return profile == null ? CharacterRaceGameplayRegistry.DEFAULT : profile;
    }

    private static String format(double value, int decimals) {
        String pattern = decimals <= 0 ? "%.0f" : decimals == 1 ? "%.1f" : "%.2f";
        return String.format(Locale.ROOT, pattern, Double.valueOf(value));
    }
}
