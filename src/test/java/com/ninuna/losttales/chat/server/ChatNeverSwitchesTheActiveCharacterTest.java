package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The identity the chat speaks and reads as is the chat's own choice and
 * changes nothing about the character being played: picking another
 * identity in a tab is a presentation choice, not a character switch.
 * Nothing in the chat may reach the switch, so this reads the compiled
 * classes of both chat packages and refuses any mention of the types
 * that perform one — the same bytecode check that keeps client code out
 * of the common chat classes.
 *
 * <p>Reading the roster is expected and allowed; it is how a tab knows
 * which identities the player has. Only writing to it is refused.</p>
 */
public final class ChatNeverSwitchesTheActiveCharacterTest {

    private static final Charset CLASS_FILE_TEXT = Charset.forName("ISO-8859-1");

    /** The types that change which character is played, by internal name. */
    private static final List<String> SWITCHES = Arrays.asList(
            "com/ninuna/losttales/character/switching/CharacterSwitchCoordinator",
            "com/ninuna/losttales/character/server/CharacterService",
            "com/ninuna/losttales/network/packet/character/CharacterSelectRequestPacket",
            "com/ninuna/losttales/client/character/ClientCharacterNetwork");

    /** Fewer classes than this means the walk found the wrong place. */
    private static final int FEWEST_EXPECTED = 20;

    @Test
    public void noChatClassCanSwitchTheActiveCharacter() throws Exception {
        int scanned = 0;
        scanned += scanDirectory(packageDirectory(ChatChannel.class));
        File client = new File(packageDirectory(ChatChannel.class)
                .getParentFile(), "client/chat");
        if (client.isDirectory()) {
            scanned += scanDirectory(client);
        }
        if (scanned < FEWEST_EXPECTED) {
            throw new IOException("Only " + scanned + " chat classes were read; the "
                    + "compiled classes were not where this test looked, so it proved "
                    + "nothing");
        }
    }

    /** The directory holding a class's own package, from its class resource. */
    private static File packageDirectory(Class<?> type)
            throws IOException, URISyntaxException {
        URL url = type.getResource(type.getSimpleName() + ".class");
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IOException("The compiled chat classes are not on disk; this "
                    + "test reads class files and cannot run against a jar");
        }
        return new File(url.toURI()).getParentFile();
    }

    /** Reads every class file at or below the directory; answers how many. */
    private static int scanDirectory(File directory) throws IOException {
        int scanned = 0;
        List<File> pending = new ArrayList<File>();
        pending.add(directory);
        while (!pending.isEmpty()) {
            File current = pending.remove(pending.size() - 1);
            File[] entries = current.listFiles();
            if (entries == null) {
                continue;
            }
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    pending.add(entry);
                } else if (entry.getName().endsWith(".class")) {
                    assertNoSwitchIn(entry);
                    scanned++;
                }
            }
        }
        return scanned;
    }

    private static void assertNoSwitchIn(File classFile) throws IOException {
        String constants = read(classFile);
        for (String switching : SWITCHES) {
            if (constants.contains(switching)) {
                throw new AssertionError(classFile.getName() + " names " + switching
                        + "; choosing a chat identity must never switch the character "
                        + "being played");
            }
        }
    }

    private static String read(File file) throws IOException {
        InputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), CLASS_FILE_TEXT);
        } finally {
            input.close();
        }
    }
}
