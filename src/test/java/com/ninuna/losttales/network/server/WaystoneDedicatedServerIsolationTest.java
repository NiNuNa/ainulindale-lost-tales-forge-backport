package com.ninuna.losttales.network.server;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.block.LostTalesWaystoneLifecycleService;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneSettingsService;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import com.ninuna.losttales.world.waystone.LostTalesGlowstoneHouseWaystonePlacer;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneGenerationHandler;
import cpw.mods.fml.common.IWorldGenerator;
import java.io.IOException;
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
                LostTalesGlowstoneHouseWaystonePlacer.class,
                LostTalesWaystoneGenerationHandler.class,
                LostTalesWaystoneSettingsService.class,
                LostTalesLotrWaystoneTravelAdapter.class,
                LostTalesWaystoneSettingsRequestPacket.class,
                LostTalesWaystoneStatePacket.class,
                LostTalesWaystoneTravelRequestPacket.class
        };
        DedicatedServerIsolation.assertServerSafe(commonClasses);
    }
}
