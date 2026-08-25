package com.ninuna.losttales.chat.emoji;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class ChatEmoticonConverterTest {

    @Test
    public void wholeTokensConvertToCanonicalShortcodes() {
        assertEquals("hello :slight_smile: there",
                ChatEmoticonConverter.convert("hello :) there"));
        assertEquals(":smile:", ChatEmoticonConverter.convert(":D"));
        assertEquals(":frowning: :heart: :broken_heart:",
                ChatEmoticonConverter.convert(":( <3 </3"));
        assertEquals("a :stuck_out_tongue_closed_eyes: b",
                ChatEmoticonConverter.convert("a :P b"));
        // The whitespace between tokens is kept exactly as typed.
        assertEquals("one  :slight_smile:\ttwo",
                ChatEmoticonConverter.convert("one  :)\ttwo"));
    }

    @Test
    public void onlyWholeTokensAndTheRightCaseConvert() {
        assertEquals("hi:) there", ChatEmoticonConverter.convert("hi:) there"));
        assertEquals("(:)", ChatEmoticonConverter.convert("(:)"));
        assertEquals(":),", ChatEmoticonConverter.convert(":),"));
        assertEquals(":d", ChatEmoticonConverter.convert(":d"));
        assertEquals("http://x.com/:)", ChatEmoticonConverter.convert(
                "http://x.com/:)"));
    }

    @Test
    public void commandsAndShareTokensAreNeverTouched() {
        assertEquals("/msg Bob :)", ChatEmoticonConverter.convert(
                "/msg Bob :)"));
        // An emoticon inside a shared name must stay, or the token no
        // longer matches the item it names; one outside still converts.
        assertEquals("look [i:Cool :) Sword] :slight_smile:",
                ChatEmoticonConverter.convert(
                        "look [i:Cool :) Sword] :)"));
    }

    @Test
    public void unmatchedInputComesBackUntouchedAndSafe() {
        String plain = "no emoticons in here";
        assertSame(plain, ChatEmoticonConverter.convert(plain));
        assertEquals("", ChatEmoticonConverter.convert(null));
        assertEquals("", ChatEmoticonConverter.convert(""));
    }

    @Test
    public void everyMappedEmoticonResolvesDeterministically() {
        assertSame(ChatEmoji.SLIGHT_SMILE,
                ChatEmoticonConverter.emojiFor(":)"));
        assertSame(ChatEmoji.SLIGHT_SMILE,
                ChatEmoticonConverter.emojiFor(":-)"));
        assertSame(ChatEmoji.CUTESY, ChatEmoticonConverter.emojiFor(":3"));
        assertSame(ChatEmoji.LAUGHING, ChatEmoticonConverter.emojiFor("xD"));
        assertSame(ChatEmoji.FEARFUL, ChatEmoticonConverter.emojiFor("D:"));
        assertSame(ChatEmoji.SOB, ChatEmoticonConverter.emojiFor(":'("));
        assertNull(ChatEmoticonConverter.emojiFor(";)"));
        assertNull(ChatEmoticonConverter.emojiFor(":/"));
        assertNull(ChatEmoticonConverter.emojiFor(null));
    }
}
