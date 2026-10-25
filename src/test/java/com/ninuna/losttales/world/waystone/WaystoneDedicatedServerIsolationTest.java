package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.block.LostTalesWaystoneLifecycleService;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneSettingsService;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneStateReason;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneVisits;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import org.junit.Test;

public final class WaystoneDedicatedServerIsolationTest {

    @Test
    public void commonWaystoneClassesContainNoClientOrLwjglReferences()
            throws Exception {
        Class<?>[] commonClasses = {
                LostTalesWaystoneLifecycleService.class,
                LostTalesBlockWaystone.class,
                LostTalesTileEntityWaystone.class,
                LostTalesMapMarkerHeightResolver.class,
                LostTalesWaystoneHousePlacer.class,
                LostTalesWaystoneStructureRegistry.class,
                LostTalesWaystoneGenerationHandler.class,
                LostTalesWaystoneSettingsService.class,
                LostTalesWaystoneStateReason.class,
                LostTalesWaystoneVisits.class,
                LostTalesLotrWaystoneTravelAdapter.class,
                LostTalesWaystoneSettingsRequestPacket.class,
                LostTalesWaystoneStatePacket.class,
                LostTalesWaystoneTravelRequestPacket.class
        };
        DedicatedServerIsolation.assertServerSafe(commonClasses);
    }
}
