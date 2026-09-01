package com.ninuna.losttales.client.chat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.UUID;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClientChatIgnoresTest {

    private static final UUID ACCOUNT_A =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACCOUNT_B =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void cleanUp() {
        // Leaves the store pointing at nothing for the rest of the suite.
        ClientChatIgnores.initialize(null);
    }

    @Test
    public void ignoresPersistAcrossAReload() throws Exception {
        File configDir = temporaryFolder.newFolder();
        ClientChatIgnores.initialize(configDir);
        assertTrue(ClientChatIgnores.ignore(ACCOUNT_A, "Aldric"));
        assertTrue(ClientChatIgnores.isIgnored(ACCOUNT_A));
        assertTrue(ClientChatIgnores.isIgnoredName("aldric"));
        assertFalse(ClientChatIgnores.isIgnored(ACCOUNT_B));

        ClientChatIgnores.initialize(configDir);
        assertTrue(ClientChatIgnores.isIgnored(ACCOUNT_A));
        assertTrue(ClientChatIgnores.isIgnoredName("ALDRIC"));
        assertEquals(1, ClientChatIgnores.count());

        assertTrue(ClientChatIgnores.unignore(ACCOUNT_A));
        assertFalse(ClientChatIgnores.unignore(ACCOUNT_A));
        ClientChatIgnores.initialize(configDir);
        assertFalse(ClientChatIgnores.isIgnored(ACCOUNT_A));
        assertEquals(0, ClientChatIgnores.count());
    }

    @Test
    public void learnedNamesLastTheSessionAndAccountNamesLastForever()
            throws Exception {
        ClientChatIgnores.initialize(temporaryFolder.newFolder());
        assertTrue(ClientChatIgnores.ignore(ACCOUNT_A, "Aldric"));
        // A name is only learned for accounts actually ignored.
        ClientChatIgnores.rememberName(ACCOUNT_B, "Beren");
        assertFalse(ClientChatIgnores.isIgnoredName("Beren"));
        ClientChatIgnores.rememberName(ACCOUNT_A, "Thorin of the Hills");
        assertTrue(ClientChatIgnores.isIgnoredName("thorin of the hills"));

        ClientChatIgnores.clearSessionNames();
        assertFalse(ClientChatIgnores.isIgnoredName("Thorin of the Hills"));
        assertTrue(ClientChatIgnores.isIgnoredName("Aldric"));

        // Unignoring forgets the account's names with it.
        ClientChatIgnores.rememberName(ACCOUNT_A, "Thorin of the Hills");
        assertTrue(ClientChatIgnores.unignore(ACCOUNT_A));
        assertFalse(ClientChatIgnores.isIgnoredName("Aldric"));
        assertFalse(ClientChatIgnores.isIgnoredName("Thorin of the Hills"));
    }

    @Test
    public void malformedLinesAreDroppedAndTheCapHolds() throws Exception {
        File configDir = temporaryFolder.newFolder();
        File file = new File(configDir, ClientChatIgnores.FILE_PATH);
        assertTrue(file.getParentFile().mkdirs());
        Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                Charset.forName("UTF-8"));
        try {
            writer.write("ignore " + ACCOUNT_A + " Aldric\n");
            writer.write("ignore not-a-uuid Broken\n");
            writer.write("something else entirely\n");
            writer.write("ignore " + ACCOUNT_B + "\n");
        } finally {
            writer.close();
        }
        ClientChatIgnores.initialize(configDir);
        assertEquals(2, ClientChatIgnores.count());
        assertTrue(ClientChatIgnores.isIgnored(ACCOUNT_A));
        assertTrue(ClientChatIgnores.isIgnored(ACCOUNT_B));

        for (int index = ClientChatIgnores.count();
                index < ClientChatIgnores.MAX_IGNORES; index++) {
            assertTrue(ClientChatIgnores.ignore(new UUID(1L, index + 1L),
                    "Player" + index));
        }
        assertFalse(ClientChatIgnores.ignore(new UUID(2L, 1L), "OneTooMany"));
        // Re-ignoring an already ignored account is never a capacity question.
        assertTrue(ClientChatIgnores.ignore(ACCOUNT_A, "Aldric"));
    }
}
