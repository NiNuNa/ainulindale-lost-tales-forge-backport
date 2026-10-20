package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.util.LostTalesDimensionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Resolves and seeds the single authoritative marker store. The store
 * seeds itself from the marker files once the server's own were read as
 * it started, and again after each read of them (MOD-17), so a marker in
 * a server's file takes a bundled one's place before the bundled one is
 * ever seeded.
 */
public final class LostTalesMapMarkerStorage {
    private LostTalesMapMarkerStorage() {}

    public static LostTalesMapMarkerWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Map marker storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        LostTalesMapMarkerWorldData data =
                (LostTalesMapMarkerWorldData)storage.loadData(
                        LostTalesMapMarkerWorldData.class,
                        LostTalesMapMarkerWorldData.DATA_NAME);
        if (data == null) {
            data = new LostTalesMapMarkerWorldData(
                    LostTalesMapMarkerWorldData.DATA_NAME);
            storage.setData(LostTalesMapMarkerWorldData.DATA_NAME, data);
            data.markDirty();
        }
        if (LostTalesMapMarkerCatalog.isServerRead()) {
            int revision = LostTalesMapMarkerCatalog.revision();
            if (data.getSeededRevision() != revision
                    && !data.isReadOnlyForNewerVersion()) {
                data.seedDefinitions(LostTalesMapMarkerCatalog.getMarkers());
                data.setSeededRevision(revision);
            }
        }
        return data;
    }
}
