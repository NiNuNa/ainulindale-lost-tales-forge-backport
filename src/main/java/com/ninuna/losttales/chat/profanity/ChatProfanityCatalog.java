package com.ninuna.losttales.chat.profanity;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * The profanity words in force on this side: the list bundled with the
 * mod, and the words the server adds in {@code chat.profanityWords}.
 * The server installs its words as its config loads and sends them to
 * every client with its chat access; a client installs what it was sent
 * and drops it on disconnect, as it drops every other server-owned
 * catalogue. Both sides read the same {@link #effective} list, so a
 * name the server refuses is a word a client would have filtered.
 */
public final class ChatProfanityCatalog {
    static final String RESOURCE = "assets/losttales/chat/profanity.txt";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** Room enough for the bundled list, which is the mod's own. */
    private static final int BUNDLED_LIMIT = 1024;

    private static ChatProfanityWords bundled;
    private static ChatProfanityWords serverWords = ChatProfanityWords.NONE;
    private static ChatProfanityWords effective;

    private ChatProfanityCatalog() {}

    /** The list bundled with the mod, read once. */
    public static synchronized ChatProfanityWords bundled() {
        if (bundled == null) {
            List<String> warnings = new ArrayList<String>();
            bundled = ChatProfanityWords.parse(readResource(RESOURCE),
                    BUNDLED_LIMIT, warnings);
            for (String warning : warnings) {
                FMLLog.warning("[%s] Bundled profanity list: %s",
                        LostTalesMetaData.MOD_ID, warning);
            }
        }
        return bundled;
    }

    /** The words the server adds; none until a config or a server says. */
    public static synchronized ChatProfanityWords serverWords() {
        return serverWords;
    }

    /** Puts the server's words in force beside the bundled list. */
    public static synchronized void installServerWords(ChatProfanityWords words) {
        serverWords = words == null ? ChatProfanityWords.NONE : words;
        effective = null;
    }

    /** The bundled list without any server's words: what stands before either says. */
    public static synchronized void resetToBundled() {
        installServerWords(ChatProfanityWords.NONE);
    }

    /** The bundled list with the server's words over it. */
    public static synchronized ChatProfanityWords effective() {
        if (effective == null) {
            effective = bundled().plus(serverWords);
        }
        return effective;
    }

    /** The lines of a resource on the class path; none when it cannot be read. */
    static String[] readResource(String resource) {
        InputStream input = ChatProfanityCatalog.class.getClassLoader()
                .getResourceAsStream(resource);
        if (input == null) {
            FMLLog.warning("[%s] The bundled profanity list %s is missing",
                    LostTalesMetaData.MOD_ID, resource);
            return new String[0];
        }
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException exception) {
            FMLLog.warning("[%s] The bundled profanity list %s could not be "
                    + "read: %s", LostTalesMetaData.MOD_ID, resource,
                    exception.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                } else {
                    input.close();
                }
            } catch (IOException ignored) {
                // Nothing left to do with a stream that would not close.
            }
        }
        return lines.toArray(new String[lines.size()]);
    }
}
