package com.ninuna.losttales.mapmarker;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/** Main-thread mutation facade for authoritative marker records. */
public final class LostTalesMapMarkerRepository {
    private LostTalesMapMarkerRepository() {}

    public static LostTalesMapMarkerWorldData get(World world) {
        return LostTalesMapMarkerStorage.get(world);
    }

    public static LostTalesMapMarkerRecord createPlayerMarker(
            EntityPlayerMP owner, int x, int y, int z, UUID linkToken) {
        if (owner == null || owner.worldObj == null
                || owner.worldObj.isRemote) {
            throw new IllegalArgumentException(
                    "player marker requires a server player");
        }
        UUID markerUuid = UUID.randomUUID();
        String id = "losttales:player/"
                + markerUuid.toString().replace("-", "");
        String name = owner.getCommandSenderName() + "'s Waystone";
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        id, name, owner.getUniqueID(),
                        owner.dimension, x, y, z, linkToken);
        LostTalesMapMarkerWorldData data = get(owner.worldObj);
        if (data.getRecord(id) != null) {
            throw new IllegalStateException(
                    "generated duplicate marker id");
        }
        data.saveRecord(record);
        return record;
    }
}
