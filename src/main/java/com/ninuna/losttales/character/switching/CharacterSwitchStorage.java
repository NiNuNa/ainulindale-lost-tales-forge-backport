package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves and synchronously flushes the dedicated switch-state store. */
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
     * Switches are rare and security-sensitive, so phase boundaries force all
     * dirty overworld map data to disk instead of waiting for the next autosave.
     */
    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
