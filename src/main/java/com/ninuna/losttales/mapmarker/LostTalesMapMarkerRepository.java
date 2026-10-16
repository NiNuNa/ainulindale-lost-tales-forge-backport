package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
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
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        id, placerName(PlayableIdentityResolver
                                .resolve(owner).getCharacter()),
                        owner.getUniqueID(),
                        owner.dimension, x, y, z, linkToken);
        LostTalesMapMarkerWorldData data = get(owner.worldObj);
        if (data.getRecord(id) != null) {
            throw new IllegalStateException(
                    "generated duplicate marker id");
        }
        data.saveRecord(record);
        return record;
    }

    /**
     * The name a waystone is called after: the character that placed it,
     * so it never names an account. Empty when nobody is played; no record
     * takes that, and the placing fails.
     */
    static String placerName(RoleplayCharacter played) {
        String name = played == null ? null : played.getName();
        return name == null ? "" : name.trim();
    }
}
