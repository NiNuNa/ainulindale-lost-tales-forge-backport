package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRShields;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Selected shield, alcohol tolerance, and last-death marker for one character. */
public final class LotrCharacterDetailsStateAdapter {

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000006");

    private static final String TAG_SHIELD = "Shield";
    private static final String TAG_ALCOHOL = "Alcohol";
    private static final String TAG_DEATH_X = "DeathX";
    private static final String TAG_DEATH_Y = "DeathY";
    private static final String TAG_DEATH_Z = "DeathZ";
    private static final String TAG_DEATH_DIMENSION = "DeathDim";

    private static final int MAX_SHIELD_NAME_LENGTH = 128;
    private static final int MAX_ABSOLUTE_WORLD_COORDINATE = 30000000;

    private static final Set<String> REQUIRED_KEYS = setOf(TAG_ALCOHOL);
    private static final Set<String> CHARACTER_KEYS = setOf(
            TAG_SHIELD,
            TAG_ALCOHOL,
            TAG_DEATH_X,
            TAG_DEATH_Y,
            TAG_DEATH_Z,
            TAG_DEATH_DIMENSION);

    public NBTTagCompound capture(EntityPlayerMP player) {
        try {
            NBTTagCompound details = extract(save(requireLiveData(player)));
            validate(details);
            return details;
        } catch (LinkageError error) {
            throw incompatible("Unable to capture LOTR character details", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to capture LOTR character details", exception);
        }
    }

    public NBTTagCompound createDefault() {
        try {
            NBTTagCompound details = extract(save(
                    new LOTRPlayerData(DETACHED_PLAYER_ID)));
            validate(details);
            return details;
        } catch (LinkageError error) {
            throw incompatible("Unable to create default LOTR character details", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to create default LOTR character details", exception);
        }
    }

    /** Validates the explicit v36.15 shape and LOTR's public load/save round trip. */
    public void validate(NBTTagCompound details) {
        validateShape(details);
        try {
            NBTTagCompound full = save(
                    new LOTRPlayerData(DETACHED_PLAYER_ID));
            overlay(full, details);
            LOTRPlayerData detached = new LOTRPlayerData(DETACHED_PLAYER_ID);
            detached.load(full);
            NBTTagCompound canonical = extract(save(detached));
            if (!canonical.equals(details)) {
                throw new IllegalArgumentException(
                        "LOTR character details contain non-canonical values");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (LinkageError error) {
            throw incompatible("LOTR character-details API is incompatible", error);
        } catch (RuntimeException exception) {
            throw incompatible("LOTR character details could not be validated", exception);
        }
    }

    /** Replaces only the allowlisted character fields and persists the LOTR cache. */
    public void apply(EntityPlayerMP player, NBTTagCompound details) {
        validate(details);
        LOTRPlayerData data = requireLiveData(player);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR character details cannot be applied during fast travel");
        }
        try {
            NBTTagCompound normalized = (NBTTagCompound)details.copy();
            if (normalized.hasKey(TAG_SHIELD, Constants.NBT.TAG_STRING)) {
                LOTRShields shield = LOTRShields.shieldForName(
                        normalized.getString(TAG_SHIELD));
                // Target progression has already applied. Never retain a shield
                // which this character cannot legitimately wear.
                if (shield == null || !shield.canPlayerWear(player)) {
                    normalized.removeTag(TAG_SHIELD);
                }
            }
            NBTTagCompound full = save(data);
            overlay(full, normalized);
            data.load(full);
            data.markDirty();
            LOTRLevelData.saveData(player.getUniqueID());
        } catch (LinkageError error) {
            throw incompatible("Unable to apply LOTR character details", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to apply LOTR character details", exception);
        }
    }

    public void synchronize(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        try {
            LOTRLevelData.sendShieldToAllPlayersInWorld(
                    player, player.worldObj);
        } catch (LinkageError error) {
            throw incompatible("Unable to synchronize LOTR shield", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to synchronize LOTR shield", exception);
        }
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR character details require a connected server player");
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data == null) {
            throw new IllegalStateException("LOTR player data is unavailable");
        }
        return data;
    }

    private static NBTTagCompound save(LOTRPlayerData data) {
        NBTTagCompound full = new NBTTagCompound();
        data.save(full);
        return full;
    }

    private static NBTTagCompound extract(NBTTagCompound full) {
        NBTTagCompound details = new NBTTagCompound();
        for (String key : CHARACTER_KEYS) {
            if (full.hasKey(key)) {
                details.setTag(key, full.getTag(key).copy());
            }
        }
        return details;
    }

    private static void overlay(NBTTagCompound full,
                                NBTTagCompound details) {
        for (String key : CHARACTER_KEYS) {
            full.removeTag(key);
        }
        for (Object keyObject : details.func_150296_c()) {
            String key = (String)keyObject;
            full.setTag(key, details.getTag(key).copy());
        }
    }

    private static void validateShape(NBTTagCompound details) {
        if (details == null) {
            throw new IllegalArgumentException(
                    "LOTR character details are missing");
        }
        Set<?> keys = details.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)
                    || !CHARACTER_KEYS.contains((String)keyObject)) {
                throw new IllegalArgumentException(
                        "LOTR character details contain an unsupported field");
            }
        }
        for (String required : REQUIRED_KEYS) {
            if (!keys.contains(required)) {
                throw new IllegalArgumentException(
                        "LOTR character details are missing " + required);
            }
        }
        requireInteger(details, TAG_ALCOHOL);
        int alcohol = details.getInteger(TAG_ALCOHOL);
        if (alcohol < 0) {
            throw new IllegalArgumentException(
                    "LOTR alcohol tolerance is outside supported bounds");
        }

        if (details.hasKey(TAG_SHIELD)) {
            if (!details.hasKey(TAG_SHIELD, Constants.NBT.TAG_STRING)) {
                throw new IllegalArgumentException(
                        "LOTR shield must be a string identifier");
            }
            String name = details.getString(TAG_SHIELD);
            LOTRShields shield = name.length() == 0
                    || name.length() > MAX_SHIELD_NAME_LENGTH
                    ? null : LOTRShields.shieldForName(name);
            if (shield == null || !shield.name().equals(name)) {
                throw new IllegalArgumentException(
                        "LOTR shield identifier is unknown or non-canonical");
            }
        }

        boolean deathX = details.hasKey(TAG_DEATH_X);
        boolean deathY = details.hasKey(TAG_DEATH_Y);
        boolean deathZ = details.hasKey(TAG_DEATH_Z);
        boolean deathDimension = details.hasKey(TAG_DEATH_DIMENSION);
        if (deathX || deathY || deathZ || deathDimension) {
            if (!(deathX && deathY && deathZ && deathDimension)) {
                throw new IllegalArgumentException(
                        "LOTR last-death marker is incomplete");
            }
            int x = requireInteger(details, TAG_DEATH_X);
            int y = requireInteger(details, TAG_DEATH_Y);
            int z = requireInteger(details, TAG_DEATH_Z);
            requireInteger(details, TAG_DEATH_DIMENSION);
            if (Math.abs((long)x) > MAX_ABSOLUTE_WORLD_COORDINATE
                    || Math.abs((long)z) > MAX_ABSOLUTE_WORLD_COORDINATE
                    || y < -4096 || y > 4096) {
                throw new IllegalArgumentException(
                        "LOTR last-death marker coordinates are outside supported bounds");
            }
        }
    }

    private static int requireInteger(NBTTagCompound compound, String key) {
        if (!compound.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return compound.getInteger(key);
    }

    private static Set<String> setOf(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
    }

    private static IllegalStateException incompatible(
            String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }
}
