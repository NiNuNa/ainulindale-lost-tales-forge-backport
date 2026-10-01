package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves pending invitations from dimension-zero MapStorage. */
public final class FellowshipInvitationStorage {

    private FellowshipInvitationStorage() {}

    public static FellowshipInvitationWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Fellowship invitation storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        FellowshipInvitationWorldData data =
                (FellowshipInvitationWorldData) storage.loadData(
                        FellowshipInvitationWorldData.class,
                        FellowshipInvitationWorldData.DATA_NAME);
        if (data == null) {
            data = new FellowshipInvitationWorldData(
                    FellowshipInvitationWorldData.DATA_NAME);
            storage.setData(FellowshipInvitationWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
