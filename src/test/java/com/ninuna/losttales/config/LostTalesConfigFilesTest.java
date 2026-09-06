package com.ninuna.losttales.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Every file lives in the mod's folder, and an older build's file is moved there once. */
public final class LostTalesConfigFilesTest {

    private File configDirectory;

    @Before
    public void setUp() throws IOException {
        configDirectory = File.createTempFile("losttales-config", "");
        assertTrue(configDirectory.delete());
        assertTrue(configDirectory.mkdirs());
    }

    @After
    public void tearDown() {
        delete(configDirectory);
    }

    @Test
    public void filesLiveInTheModsFolder() {
        File file = LostTalesConfigFiles.file(configDirectory, LostTalesConfigFiles.MAIN_OPTIONS);
        assertEquals(new File(configDirectory, "losttales"), file.getParentFile());
        assertEquals("losttales.cfg", file.getName());
        assertTrue(file.getParentFile().isDirectory());
        assertFalse(file.exists());
        assertEquals("losttales-third-person.cfg", LostTalesConfigFiles.CAMERA_OPTIONS);
    }

    @Test
    public void anOlderBuildsFileIsMovedIntoTheFolderOnce() throws IOException {
        File legacy = new File(configDirectory, LostTalesConfigFiles.MAIN_OPTIONS);
        write(legacy, "old settings");
        File file = LostTalesConfigFiles.file(configDirectory, LostTalesConfigFiles.MAIN_OPTIONS);
        assertTrue(file.isFile());
        assertFalse(legacy.exists());
        assertEquals("old settings", read(file));
        // A file already in the folder is never overwritten by an old one.
        write(legacy, "stale copy");
        File again = LostTalesConfigFiles.file(configDirectory, LostTalesConfigFiles.MAIN_OPTIONS);
        assertEquals(file, again);
        assertEquals("old settings", read(again));
        assertTrue(legacy.isFile());
    }

    private static void write(File file, String text) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(text.getBytes(Charset.forName("UTF-8")));
        } finally {
            output.close();
        }
    }

    private static String read(File file) throws IOException {
        byte[] bytes = new byte[(int)file.length()];
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
        } finally {
            input.close();
        }
        return new String(bytes, Charset.forName("UTF-8"));
    }

    private static void delete(File file) {
        if (file == null) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }
}
