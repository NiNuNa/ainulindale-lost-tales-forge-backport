package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneGenerationState;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.world.WorldServer;

/**
 * Resolves registered placers and persists durable failure state. A
 * marker whose waystone waits to be built is {@code NOT_ATTEMPTED}, saved
 * with its record: the generation handler builds it as its chunk loads.
 */
public final class LostTalesWaystonePlacementService {
    private LostTalesWaystonePlacementService() {}

    /**
     * The record marked to be built again, as {@code retry} marks it
     * (WAY-18): a generated waystone not yet standing, failed or never
     * tried, waits as {@code NOT_ATTEMPTED}, a revision newer where it
     * failed. Null for a record with no waystone, or one standing,
     * placed by a player or turned off.
     */
    public static LostTalesMapMarkerRecord markForRetry(
            LostTalesMapMarkerRecord record) {
        if (record == null || !record.hasWaystone() || record.isLinked()) {
            return null;
        }
        if (record.getGenerationState()
                == LostTalesWaystoneGenerationState.NOT_ATTEMPTED) {
            return record;
        }
        if (record.getGenerationState()
                != LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED) {
            return null;
        }
        return record.withGenerationState(
                LostTalesWaystoneGenerationState.NOT_ATTEMPTED);
    }

    public static LostTalesWaystonePlacementResult attempt(
            WorldServer world, LostTalesMapMarkerRecord record) {
        if (world == null || record == null
                || world.isRemote
                || !record.hasWaystone()
                || record.getDimensionId()
                        != world.provider.dimensionId) {
            return LostTalesWaystonePlacementResult.blocked(
                    "invalid_placement_context");
        }
        if (record.getGenerationState()
                == LostTalesWaystoneGenerationState.PLACED
                || record.getGenerationState()
                == LostTalesWaystoneGenerationState.DISABLED) {
            return LostTalesWaystonePlacementResult.blocked(
                    "generation_state_not_placeable");
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(world);
        LostTalesWaystoneStructurePlacer placer =
                LostTalesWaystoneStructureRegistry.get(
                        record.getWaystoneStructureType());
        if (placer == null) {
            LostTalesMapMarkerRecord failed =
                    record.withGenerationState(
                            LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED);
            data.saveRecord(failed);
            LostTalesLog.warning("Marker %s references unknown waystone structure %s",
                    record.getId(), record.getWaystoneStructureType());
            return LostTalesWaystonePlacementResult.blocked(
                    "unknown_structure_type");
        }
        LostTalesWaystonePlacementResult result =
                placer.place(world, data, record);
        if (result.getStatus()
                == LostTalesWaystonePlacementResult.Status.BLOCKED) {
            LostTalesMapMarkerRecord current =
                    data.getRecord(record.getId());
            if (current != null && current.getGenerationState()
                            != LostTalesWaystoneGenerationState.PLACED) {
                data.saveRecord(current.withGenerationState(
                        LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED));
            }
            LostTalesLog.warning("Waystone generation blocked for marker %s: %s",
                    record.getId(), result.getReason());
        }
        return result;
    }
}
