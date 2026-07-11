package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Shared entity-data keys for the locally resolved roleplay race profile.
 *
 * These values are written independently on the logical server and client.
 * They are never accepted from a client as gameplay input; the server resolves
 * the active character from UUID-owned world storage.
 */
public final class CharacterRaceEntityData {

    private static final String TAG_RACE = "LostTalesActiveRace";
    private static final String TAG_STANDING_EYE_HEIGHT = "LostTalesEyeHeight";
    private static final String TAG_SNEAKING_EYE_HEIGHT = "LostTalesSneakingEyeHeight";
    private static final String TAG_WIDTH = "LostTalesWidth";
    private static final String TAG_HEIGHT = "LostTalesHeight";
    private static final String TAG_LOTR_DERIVED = "LostTalesLotrDerivedProfile";

    private CharacterRaceEntityData() {}

    public static void write(
            Entity entity,
            String raceId,
            CharacterRaceGameplayProfile profile) {
        if (entity == null || profile == null) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        data.setString(TAG_RACE, raceId == null ? "" : raceId);
        data.setFloat(TAG_STANDING_EYE_HEIGHT, profile.getStandingEyeHeight());
        data.setFloat(TAG_SNEAKING_EYE_HEIGHT, profile.getSneakingEyeHeight());
        data.setFloat(TAG_WIDTH, profile.getWidth());
        data.setFloat(TAG_HEIGHT, profile.getHeight());
        data.setBoolean(TAG_LOTR_DERIVED, profile.isLotrDerived());
    }

    public static String getRaceId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(TAG_RACE);
    }

    public static float getStandingEyeHeight(Entity entity, float fallback) {
        if (entity == null || getRaceId(entity).length() == 0) {
            return fallback;
        }
        float value = entity.getEntityData().getFloat(TAG_STANDING_EYE_HEIGHT);
        return value > 0.0F ? value : fallback;
    }

    public static float getSneakingEyeHeight(Entity entity, float fallback) {
        if (entity == null || getRaceId(entity).length() == 0) {
            return fallback;
        }
        float value = entity.getEntityData().getFloat(TAG_SNEAKING_EYE_HEIGHT);
        return value > 0.0F ? value : getStandingEyeHeight(entity, fallback);
    }

    /** Backwards-compatible name used by older callers. */
    public static float getEyeHeight(Entity entity, float fallback) {
        return getStandingEyeHeight(entity, fallback);
    }

    public static boolean isLotrDerived(Entity entity) {
        return entity != null
                && entity.getEntityData().getBoolean(TAG_LOTR_DERIVED);
    }
}
