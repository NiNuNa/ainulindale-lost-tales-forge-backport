package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatEmojiUsageStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void resetStore() {
        ChatEmojiUsageStore.initialize(null);
    }

    @Test
    public void startsEmptyAndToleratesAMissingDirectory() {
        ChatEmojiUsageStore.initialize(null);
        assertTrue(ChatEmojiUsageStore.getFavorites().isEmpty());
        assertTrue(ChatEmojiUsageStore.getFrequentlyUsed(6).isEmpty());
        // Mutations without a backing file must not throw.
        ChatEmojiUsageStore.toggleFavorite(ChatEmoji.JOY);
        ChatEmojiUsageStore.recordUse(ChatEmoji.JOY);
        assertTrue(ChatEmojiUsageStore.isFavorite(ChatEmoji.JOY));
    }

    @Test
    public void favoritesToggleAndPersistAcrossReloads() {
        File configDir = temporaryFolder.getRoot();
        ChatEmojiUsageStore.initialize(configDir);
        ChatEmojiUsageStore.toggleFavorite(ChatEmoji.SOB);
        ChatEmojiUsageStore.toggleFavorite(ChatEmoji.SMILE);
        assertTrue(ChatEmojiUsageStore.isFavorite(ChatEmoji.SOB));

        ChatEmojiUsageStore.initialize(configDir);
        List<ChatEmoji> favorites = ChatEmojiUsageStore.getFavorites();
        assertEquals(2, favorites.size());
        // Registry order keeps the grid stable regardless of toggle order.
        assertSame(ChatEmoji.SMILE, favorites.get(0));
        assertSame(ChatEmoji.SOB, favorites.get(1));

        ChatEmojiUsageStore.toggleFavorite(ChatEmoji.SOB);
        ChatEmojiUsageStore.initialize(configDir);
        assertFalse(ChatEmojiUsageStore.isFavorite(ChatEmoji.SOB));
        assertTrue(ChatEmojiUsageStore.isFavorite(ChatEmoji.SMILE));
    }

    @Test
    public void frequentlyUsedOrdersByCountThenRegistryOrder() {
        ChatEmojiUsageStore.initialize(temporaryFolder.getRoot());
        ChatEmojiUsageStore.recordUse(ChatEmoji.JOY);
        ChatEmojiUsageStore.recordUse(ChatEmoji.JOY);
        ChatEmojiUsageStore.recordUse(ChatEmoji.JOY);
        ChatEmojiUsageStore.recordUse(ChatEmoji.SMILE);
        ChatEmojiUsageStore.recordUse(ChatEmoji.SAD);

        List<ChatEmoji> frequent = ChatEmojiUsageStore.getFrequentlyUsed(6);
        assertEquals(3, frequent.size());
        assertSame(ChatEmoji.JOY, frequent.get(0));
        assertSame(ChatEmoji.SMILE, frequent.get(1));
        assertSame(ChatEmoji.SAD, frequent.get(2));

        assertEquals(1, ChatEmojiUsageStore.getFrequentlyUsed(1).size());
        assertTrue(ChatEmojiUsageStore.getFrequentlyUsed(0).isEmpty());

        ChatEmojiUsageStore.initialize(temporaryFolder.getRoot());
        assertEquals(3,
                ChatEmojiUsageStore.getFrequentlyUsed(6).size());
    }

    @Test
    public void malformedAndUnknownFileLinesAreIgnored() throws Exception {
        File configDir = temporaryFolder.getRoot();
        File file = new File(configDir, ChatEmojiUsageStore.FILE_PATH);
        assertTrue(file.getParentFile().mkdirs());
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), Charset.forName("UTF-8"));
        try {
            writer.write("favorite joy\n");
            writer.write("favorite does_not_exist\n");
            writer.write("count smile 4\n");
            writer.write("count smile not_a_number\n");
            writer.write("count does_not_exist 9\n");
            writer.write("count sad -3\n");
            writer.write("garbage line that means nothing\n");
            writer.write("\n");
        } finally {
            writer.close();
        }

        ChatEmojiUsageStore.initialize(configDir);
        assertTrue(ChatEmojiUsageStore.isFavorite(ChatEmoji.JOY));
        assertEquals(1, ChatEmojiUsageStore.getFavorites().size());
        List<ChatEmoji> frequent = ChatEmojiUsageStore.getFrequentlyUsed(6);
        assertEquals(1, frequent.size());
        assertSame(ChatEmoji.SMILE, frequent.get(0));
    }
}
