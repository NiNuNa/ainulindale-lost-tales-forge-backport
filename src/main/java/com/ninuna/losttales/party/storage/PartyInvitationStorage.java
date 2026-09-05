package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/** Resolves pending invitations from dimension-zero MapStorage. */
public final class PartyInvitationStorage {

    private PartyInvitationStorage() {}

    public static PartyInvitationWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Party invitation storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        PartyInvitationWorldData data =
                (PartyInvitationWorldData) storage.loadData(
                        PartyInvitationWorldData.class,
                        PartyInvitationWorldData.DATA_NAME);
        if (data == null) {
            data = new PartyInvitationWorldData(
                    PartyInvitationWorldData.DATA_NAME);
            storage.setData(PartyInvitationWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }
}
