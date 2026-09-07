package com.ninuna.losttales;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.Assert.assertFalse;

/**
 * Proves a class carries nothing a dedicated server cannot load.
 *
 * <p>A dedicated server has no {@code net.minecraft.client} classes and no
 * LWJGL. A common class that names one loads fine in an integrated server
 * and dies on a real one, which is the kind of fault that only shows up
 * once the mod is on somebody's server — so it is read straight out of
 * the compiled constant pool rather than by loading the class, which
 * would need the very classes that are missing.</p>
 *
 * <p>Each family keeps its own list of what has to stay loadable; the
 * reading is the same for all of them and lives here. Four families kept
 * a copy each, and one of them had already stopped checking for LWJGL.</p>
 */
public final class DedicatedServerIsolation {

    /** What no server-loadable class may name. */
    private static final String[] CLIENT_ONLY = {
            "net/minecraft/client/",
            "org/lwjgl/"
    };

    /** The constant pool is read as bytes, so any encoding that maps 1:1. */
    private static final Charset CLASS_FILE_TEXT = Charset.forName("ISO-8859-1");

    private DedicatedServerIsolation() {}

    /** Fails naming the first class that would not load on a server. */
    public static void assertServerSafe(Class<?>... types) throws IOException {
        for (Class<?> type : types) {
            String constantPool = new String(readClass(type), CLASS_FILE_TEXT);
            for (String forbidden : CLIENT_ONLY) {
                assertFalse(type.getName() + " names " + forbidden,
                        constantPool.contains(forbidden));
            }
        }
    }

    private static byte[] readClass(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
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
