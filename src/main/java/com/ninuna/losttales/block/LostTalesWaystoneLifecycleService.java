package com.ninuna.losttales.block;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import cpw.mods.fml.common.FMLLog;

/** Coordinates legitimate removal, paired-block cleanup, and relocation guards. */
public final class LostTalesWaystoneLifecycleService {
    private static final ThreadLocal<Integer> PRESERVE_DEPTH =
            new ThreadLocal<Integer>();

    private LostTalesWaystoneLifecycleService() {}

    public static void runPreservingMarker(Runnable action) {
        int previous = depth();
        PRESERVE_DEPTH.set(Integer.valueOf(previous + 1));
        try {
            action.run();
        } finally {
            if (previous <= 0) {
                PRESERVE_DEPTH.remove();
            } else {
                PRESERVE_DEPTH.set(Integer.valueOf(previous));
            }
        }
    }

    public static void onBlockRemoved(
            LostTalesTileEntityWaystone tileEntity, String reason) {
        if (depth() > 0 || tileEntity == null
                || tileEntity.getWorldObj() == null
                || tileEntity.getWorldObj().isRemote
                || !tileEntity.isLinked()) {
            return;
        }
        try {
            LostTalesMapMarkerWorldData data =
                    LostTalesMapMarkerStorage.get(
                            tileEntity.getWorldObj());
            LostTalesMapMarkerRecord record =
                    data.getRecord(tileEntity.getMarkerId());
            if (record == null || !record.isLinked()
                    || !tileEntity.getLinkToken().equals(
                            record.getLinkToken())
                    || record.getLinkedDimensionId()
                            != tileEntity.getWorldObj().provider.dimensionId
                    || record.getLinkedX() != tileEntity.xCoord
                    || record.getLinkedY() != tileEntity.yCoord
                    || record.getLinkedZ() != tileEntity.zCoord) {
                return;
            }
            data.removeRecord(record.getId());
            LostTalesMapMarkerSyncManager.syncAll();
        } catch (RuntimeException exception) {
            try {
                FMLLog.severe(
                        "[%s] Failed to delete marker for removed waystone %s: %s",
                        LostTalesMetaData.MOD_ID,
                        tileEntity.getMarkerId(),
                        exception.getMessage());
            } catch (RuntimeException ignored) {}
        }
    }

    private static int depth() {
        Integer value = PRESERVE_DEPTH.get();
        return value == null ? 0 : value.intValue();
    }
}
