package com.ninuna.losttales.character.state;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.UUID;

/** Resolves one isolated character player-state file per Minecraft account. */
public final class CharacterPlayerStateStorage {

    private CharacterPlayerStateStorage() {}

    public static CharacterPlayerStateWorldData get(World world, UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        String dataName = CharacterPlayerStateWorldData.dataName(ownerId);
        CharacterPlayerStateWorldData data =
                (CharacterPlayerStateWorldData) storage.loadData(
                        CharacterPlayerStateWorldData.class, dataName);
        if (data == null) {
            data = new CharacterPlayerStateWorldData(dataName);
            storage.setData(dataName, data);
            data.markDirty();
        }
        return data;
    }

    /**
     * The account's store as it stands, for a read; null where none was
     * ever written. Unlike {@link #get}, nothing is made.
     */
    public static CharacterPlayerStateWorldData find(World world, UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        return (CharacterPlayerStateWorldData) overworld.mapStorage.loadData(
                CharacterPlayerStateWorldData.class,
                CharacterPlayerStateWorldData.dataName(ownerId));
    }

    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
