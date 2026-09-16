package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import cpw.mods.fml.common.FMLLog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * The profanity filter as this client shows it: the words in force
 * ({@link ChatProfanityCatalog}: the bundled list with the server's
 * words over it) with this installation's own words over both, read
 * once as the client starts from {@code config/losttales/client/chat/
 * profanity.txt}, one {@code word=replacement} per line; and the mode
 * the client chose ({@code client.chatProfanityFilter}).
 *
 * <p>Applied where a player's words are drawn — the message lines, a
 * reply's quote, the speech bubbles, NPC speech — and nowhere else: the
 * wire, the history, copies and the input field keep what was typed.</p>
 */
public final class ClientChatProfanity {
    static final String FILE_PATH = LostTalesConfigFiles.CHAT_PROFANITY;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static ChatProfanityWords local = ChatProfanityWords.NONE;
    /** The catalogue's list the merged one was built over. */
    private static ChatProfanityWords mergedOver;
    private static ChatProfanityWords merged;

    private ClientChatProfanity() {}

    /** Reads this installation's own words; none without a file. */
    public static synchronized void initialize(File configDirectory) {
        File file = configDirectory == null ? null
                : new File(configDirectory, FILE_PATH);
        List<String> warnings = new ArrayList<String>();
        local = ChatProfanityWords.parse(readLines(file),
                ChatProfanityWords.MAX_WORDS, warnings);
        for (String warning : warnings) {
            FMLLog.warning("[%s] %s: %s", LostTalesMetaData.MOD_ID,
                    FILE_PATH, warning);
        }
        merged = null;
    }

    /** A run of plain words as this client shows it. */
    public static String filter(String text) {
        return ChatProfanityFilter.filter(text, mode(), words());
    }

    /** A message as typed, its tokens, emojis and links left alone. */
    public static String filterMessage(String message) {
        return ChatProfanityFilter.filterMessage(message, mode(), words());
    }

    /** The mode the client chose; off for a value that names none. */
    static ChatProfanityMode mode() {
        return ChatProfanityMode.of(LostTalesConfig.chatProfanityFilter,
                ChatProfanityMode.OFF);
    }

    /** The words in force with this installation's own over them. */
    static synchronized ChatProfanityWords words() {
        ChatProfanityWords over = ChatProfanityCatalog.effective();
        if (merged == null || mergedOver != over) {
            mergedOver = over;
            merged = over.plus(local);
        }
        return merged;
    }

    private static String[] readLines(File file) {
        if (file == null || !file.isFile()) {
            return new String[0];
        }
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException exception) {
            FMLLog.warning("[%s] %s could not be read: %s",
                    LostTalesMetaData.MOD_ID, FILE_PATH, exception.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Nothing left to do with a reader that would not close.
                }
            }
        }
        return lines.toArray(new String[lines.size()]);
    }
}
