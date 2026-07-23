package com.ninuna.losttales.network.server;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        for (Class<?> type : commonClasses) {
            String constantPool = new String(
                    readClass(type), StandardCharsets.ISO_8859_1);
            assertFalse(type.getName(), constantPool.contains(
                    "net/minecraft/client/"));
            assertFalse(type.getName(), constantPool.contains(
                    "org/lwjgl/"));
        }
    }

    @Test
    public void generationHandlerUsesForgeWorldGeneratorPipeline() {
        assertTrue(IWorldGenerator.class.isAssignableFrom(
                LostTalesWaystoneGenerationHandler.class));
    }

    private static byte[] readClass(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/')
                + ".class";
        InputStream input = type.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Missing class resource " + resource);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
