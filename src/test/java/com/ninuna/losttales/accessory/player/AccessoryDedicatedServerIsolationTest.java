package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;

/** Guards authoritative accessory classes against physical-client linkage. */
public final class AccessoryDedicatedServerIsolationTest {

    @Test
    public void commonAccessoryClassesContainNoClientOrLwjglReferences()
            throws IOException {
        Class<?>[] commonClasses = {
                AccessoryEquipService.class,
                AccessoryEffectService.class,
                AccessoryInventory.class,
                AccessoryInventorySyncManager.class,
                AccessoryPlayerData.class
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
