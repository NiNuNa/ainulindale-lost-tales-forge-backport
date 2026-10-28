package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Finds the switch store in dimension 0's storage, and writes it to disk. */
public final class CharacterSwitchStorage {

    private CharacterSwitchStorage() {}

    public static CharacterSwitchWorldData get(World world) {
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        CharacterSwitchWorldData data = (CharacterSwitchWorldData) storage.loadData(
                CharacterSwitchWorldData.class,
                CharacterSwitchWorldData.DATA_NAME);
        if (data == null) {
            data = new CharacterSwitchWorldData(CharacterSwitchWorldData.DATA_NAME);
            storage.setData(CharacterSwitchWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    /**
     * Writes every changed store of dimension 0 to disk at once, rather
     * than at the next autosave: a switch writes after each of its steps.
     */
    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
