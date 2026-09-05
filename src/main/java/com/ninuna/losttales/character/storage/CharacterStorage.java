package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Resolves the single authoritative character store for the current server.
 */
public final class CharacterStorage {

    private CharacterStorage() {}

    public static CharacterWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException("Character storage is server-side only");
        }

        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterWorldData data = (CharacterWorldData) storage.loadData(
                CharacterWorldData.class,
                CharacterWorldData.DATA_NAME
        );
        if (data == null) {
            data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
            storage.setData(CharacterWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
