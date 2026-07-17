package com.ninuna.losttales.network.server;

import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonAimPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonBlockActionPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Guards common packet and authority classes against client-only linkage. */
public final class ThirdPersonDedicatedServerIsolationTest {

    @Test
    public void commonThirdPersonClassesContainNoClientOrLwjglReferences()
            throws Exception {
        Class<?>[] commonClasses = {
                ThirdPersonProjectileItemPolicy.class,
                LostTalesThirdPersonAimPacket.class,
                LostTalesThirdPersonBlockActionPacket.class,
                LostTalesThirdPersonEntityActionPacket.class,
                LostTalesThirdPersonAimService.class,
                LostTalesThirdPersonBlockActionService.class,
                LostTalesThirdPersonEntityActionService.class,
                LostTalesThirdPersonProjectileAimHandler.class
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
