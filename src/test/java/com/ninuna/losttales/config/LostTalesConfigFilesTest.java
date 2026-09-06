package com.ninuna.losttales.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import java.io.File;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The client's files live in the client folder and the server's in the
 * server folder, and a server asking for its own files never creates
 * the client's folder.
 */
public final class LostTalesConfigFilesTest {

    private File configDirectory;

    @Before
    public void setUp() throws Exception {
        configDirectory = File.createTempFile("losttales-config", "");
        assertTrue(configDirectory.delete());
        assertTrue(configDirectory.mkdirs());
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, configDirectory.getParentFile());
    }

    @After
    public void tearDown() {
        delete(configDirectory);
    }

    @Test
    public void filesLiveInTheirSidesFolder() {
        File client = LostTalesConfigFiles.clientOptions(configDirectory);
        File server = LostTalesConfigFiles.serverOptions(configDirectory);
        assertEquals(new File(configDirectory, "losttales/client"), client.getParentFile());
        assertEquals(new File(configDirectory, "losttales/server"), server.getParentFile());
        assertEquals("client.cfg", client.getName());
        assertEquals("server.cfg", server.getName());
        assertTrue(client.getParentFile().isDirectory());
        assertTrue(server.getParentFile().isDirectory());
        assertFalse(client.exists());
        assertFalse(server.exists());
        assertEquals(new File(configDirectory, "losttales/client/chat/layout.txt"),
                LostTalesConfigFiles.clientFile(configDirectory, LostTalesConfigFiles.CHAT_LAYOUT));
        assertEquals("third-person.cfg", LostTalesConfigFiles.CAMERA_OPTIONS);
    }

    @Test
    public void aServerNeverCreatesTheClientFolder() {
        assertTrue(LostTalesConfigFiles.serverOptions(configDirectory)
                .getParentFile().isDirectory());
        assertTrue(LostTalesConfigFiles.rolesOptions(configDirectory)
                .getParentFile().isDirectory());
        assertEquals(new File(configDirectory, "losttales/server/channels.cfg"),
                LostTalesConfigFiles.channelsOptions(configDirectory));
        assertFalse(new File(configDirectory, "losttales/client").exists());
        assertEquals(new File(configDirectory, "losttales/lore_characters"),
                LostTalesConfigFiles.file(configDirectory, "lore_characters"));
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
