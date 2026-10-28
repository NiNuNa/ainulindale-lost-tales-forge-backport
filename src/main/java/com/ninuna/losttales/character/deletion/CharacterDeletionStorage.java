package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Finds the deleted-character store in dimension 0's storage, and writes it to disk. */
public final class CharacterDeletionStorage {

    private CharacterDeletionStorage() {}

    public static CharacterDeletionWorldData get(World world) {
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterDeletionWorldData data =
                (CharacterDeletionWorldData) storage.loadData(
                        CharacterDeletionWorldData.class,
                        CharacterDeletionWorldData.DATA_NAME);
        if (data == null) {
            data = new CharacterDeletionWorldData(
                    CharacterDeletionWorldData.DATA_NAME);
            storage.setData(CharacterDeletionWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
