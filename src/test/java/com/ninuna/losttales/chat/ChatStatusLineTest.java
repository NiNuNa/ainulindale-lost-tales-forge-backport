package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * A status line is a few plain words: formatting codes, control
 * characters and extra spaces go, and it is cut to its length without
 * splitting a character.
 */
public final class ChatStatusLineTest {

    @Test
    public void aLineIsPlainWords() {
        assertEquals("", ChatStatusLine.clean(null));
        assertEquals("", ChatStatusLine.clean("   "));
        assertEquals("Out hunting orcs",
                ChatStatusLine.clean("  Out \t hunting\n§corcs  "));
        assertEquals("Brewing", ChatStatusLine.clean("Brewing"));
        // A code at the very end takes nothing with it.
        assertEquals("Tea", ChatStatusLine.clean("Tea§"));
    }

    @Test
    public void aLineIsCutWithoutSplittingACharacter() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < ChatStatusLine.MAX_CHARACTERS + 10;
                index++) {
            long_.append('a');
        }
        assertEquals(ChatStatusLine.MAX_CHARACTERS,
                ChatStatusLine.clean(long_.toString()).length());
        // A pair standing across the cut goes whole.
        StringBuilder pairs = new StringBuilder();
        for (int index = 0; index < ChatStatusLine.MAX_CHARACTERS - 1;
                index++) {
            pairs.append('b');
        }
        pairs.append("🍺");
        String cut = ChatStatusLine.clean(pairs.toString());
        assertEquals(ChatStatusLine.MAX_CHARACTERS - 1, cut.length());
        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
        // Every character fits the bytes a line may take.
        StringBuilder widest = new StringBuilder();
        for (int index = 0; index < ChatStatusLine.MAX_CHARACTERS; index++) {
            widest.append('☃');
        }
        assertEquals(ChatStatusLine.MAX_BYTES, ChatStatusLine.clean(
                widest.toString()).getBytes(java.nio.charset.Charset
                .forName("UTF-8")).length);
    }
}
