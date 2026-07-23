package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneGenerationState;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.world.WorldServer;

/** Resolves registered placers and persists durable failure state. */
public final class LostTalesWaystonePlacementService {
    private LostTalesWaystonePlacementService() {}

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
                            LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED,
                            "unknown_structure_type");
            data.saveRecord(failed);
            warn("Marker %s references unknown waystone structure %s",
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
                        LostTalesWaystoneGenerationState.FAILED_OR_BLOCKED,
                        result.getReason()));
            }
            warn("Waystone generation blocked for marker %s: %s",
                    record.getId(), result.getReason());
        }
        return result;
    }

    private static void warn(String format, Object... args) {
        Object[] values = new Object[
                (args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        try {
            FMLLog.warning("[%s] " + format, values);
        } catch (RuntimeException ignored) {}
    }
}
