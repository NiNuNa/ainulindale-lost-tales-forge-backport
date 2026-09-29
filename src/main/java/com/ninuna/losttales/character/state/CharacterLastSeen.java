package com.ninuna.losttales.character.state;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.UUID;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * The world's record of when each identity was last played or heard
 * ({@link CharacterLastSeenWorldData}), kept in dimension 0's storage.
 * Server only; a world whose storage cannot be had has seen nobody.
 */
public final class CharacterLastSeen {

    private CharacterLastSeen() {}

    /** The identity (a character, or the account playing as itself) is played or heard now. */
    public static void saw(World world, UUID identityId) {
        CharacterLastSeenWorldData data = dataOf(world);
        if (data != null) {
            data.saw(identityId, System.currentTimeMillis());
        }
    }

    /** Whether the identity was played or heard within the last {@code windowMillis}. */
    public static boolean seenWithin(World world, UUID identityId, long windowMillis) {
        CharacterLastSeenWorldData data = dataOf(world);
        return data != null && data.seenWithin(identityId, windowMillis,
                System.currentTimeMillis());
    }

    private static CharacterLastSeenWorldData dataOf(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage;
        try {
            WorldServer overworld = LostTalesDimensionHelper.overworld(world);
            storage = overworld.mapStorage;
        } catch (IllegalStateException noOverworld) {
            return null;
        }
        if (storage == null) {
            return null;
        }
        CharacterLastSeenWorldData data = (CharacterLastSeenWorldData) storage.loadData(
                CharacterLastSeenWorldData.class, CharacterLastSeenWorldData.DATA_NAME);
        if (data == null) {
            data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
            storage.setData(CharacterLastSeenWorldData.DATA_NAME, data);
        }
        return data;
    }
}
