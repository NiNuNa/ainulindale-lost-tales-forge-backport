package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import net.minecraft.world.WorldServer;

public interface LostTalesWaystoneStructurePlacer {
    String getId();

    LostTalesWaystonePlacementResult place(
            WorldServer world,
            LostTalesMapMarkerWorldData data,
            LostTalesMapMarkerRecord record);
}
