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

    public static void flush(World world) {
        LostTalesDimensionHelper.flushOverworldStorage(world);
    }
}
