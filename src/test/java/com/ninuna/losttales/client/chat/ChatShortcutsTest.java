package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageHelp;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.WindowKeys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every row of a conversation's keys, and of the keys every page shares,
 * reads words of the shipped language file, so none shows its bare key,
 * and no two shortcuts share one; the search finds a key by the name its
 * icon writes. A conversation's help first says who reads it and who you
 * speak as there. The file is read as it ships, not loaded into the
 * game's translator, which other tests read without it: there a row
 * shows each word as its key, and the key is what is looked for in the
 * file.
 */
public final class ChatShortcutsTest {
    private static final String MOD_KEY = "gui.losttales.";

    private static List<MenuWindow.Entry> rows() {
        List<PageKeys.Area> areas = new ArrayList<PageKeys.Area>(
                ChatShortcuts.areas());
        areas.addAll(PageKeys.windowAreas());
        return PageKeys.rows(areas, "");
    }

    @Test
    public void everyAreaShortcutAndWordHasItsWords() throws IOException {
        Set<String> written = languageKeys();
        int checked = 0;
        for (MenuWindow.Entry row : rows()) {
            checked += check(written, row.label);
            for (Object part : row.keys) {
                if (part instanceof String) {
                    checked += check(written, (String)part);
                }
            }
        }
        assertTrue("the rows show their words as keys", checked > 0);
    }

    @Test
    public void aKeyIsFoundByTheNameItsIconWrites() {
        assertEquals("f", WindowKeys.keyName(Keyboard.KEY_F));
        assertEquals("backspace", WindowKeys.keyName(Keyboard.KEY_BACK));
        assertEquals("pgup", WindowKeys.keyName(Keyboard.KEY_PRIOR));
        assertEquals("pgdn", WindowKeys.keyName(Keyboard.KEY_NEXT));
    }

    @Test
    public void noTwoShortcutsShareTheirWords() {
        Set<String> shortcuts = new HashSet<String>();
        for (MenuWindow.Entry row : rows()) {
            if (!row.group) {
                assertTrue(row.label, shortcuts.add(row.label));
            }
        }
        assertTrue(!shortcuts.isEmpty());
    }

    @Test
    public void aConversationsHelpSaysWhoReadsItAndWhoYouSpeakAs() {
        assertEquals("gui.losttales.help.chat.readers.everyone",
                help(ChatChannel.GLOBAL).guide.get(0));
        assertEquals("gui.losttales.help.chat.speaking.identity",
                help(ChatChannel.GLOBAL).guide.get(1));
        assertEquals("gui.losttales.help.chat.readers.proximity",
                help(ChatChannel.PROXIMITY).guide.get(0));
        assertEquals("gui.losttales.help.chat.speaking.played",
                help(ChatChannel.PROXIMITY).guide.get(1));
        assertEquals("gui.losttales.help.chat.speaking.account",
                help(ChatChannel.OOC).guide.get(1));
        // Nobody else reads the Console: there is nobody to
        // speak as.
        PageHelp console = help(ChatChannel.CLIENT_CONSOLE);
        assertEquals("gui.losttales.help.chat.readers.self",
                console.guide.get(0));
        assertEquals(1, console.guide.size());
        assertEquals(ChatShortcuts.areas().size(), console.areas.size());
    }

    private static PageHelp help(ChatChannel channel) {
        return ConversationPage.of(channel).help();
    }

    /** One when {@code shown} is a word's key and the file has it; a failure when it does not. */
    private static int check(Set<String> written, String shown) {
        if (shown == null || !shown.startsWith(MOD_KEY)) {
            return 0;
        }
        assertTrue(shown, written.contains(shown));
        return 1;
    }

    static Set<String> languageKeys() throws IOException {
        InputStream stream = ChatShortcutsTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        assertNotNull("en_US.lang missing", stream);
        Set<String> keys = new HashSet<String>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    keys.add(line.substring(0, equals));
                }
            }
        } finally {
            reader.close();
        }
        return keys;
    }
}
