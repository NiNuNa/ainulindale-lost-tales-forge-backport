package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatInputRulesTest {

    @Test
    public void commandsAreTheSlashLinesAndWhisperVerbsAreNotServerCommands() {
        assertTrue(ChatInputRules.isCommand("/losttales hud"));
        assertTrue(ChatInputRules.isCommand("  /x"));
        assertFalse(ChatInputRules.isCommand("hello /x"));
        assertFalse(ChatInputRules.isCommand(""));
        assertFalse(ChatInputRules.isCommand(null));

        assertTrue(ChatInputRules.isWhisperCommand("/msg Bilbo hello"));
        assertTrue(ChatInputRules.isWhisperCommand("/TELL Bilbo"));
        assertTrue(ChatInputRules.isWhisperCommand("  /w"));
        assertFalse(ChatInputRules.isWhisperCommand("/whisper Bilbo"));
        assertFalse(ChatInputRules.isWhisperCommand("/msgs"));
        assertFalse(ChatInputRules.isWhisperCommand(null));

        assertTrue(ChatInputRules.isServerCommand("/losttales hud"));
        assertFalse(ChatInputRules.isServerCommand("/msg Bilbo hi"));
        assertFalse(ChatInputRules.isServerCommand("a message"));
    }

    @Test
    public void whisperPartsSplitTheVerbTheNameAndTheRest() {
        assertArrayEquals(new String[] {"/msg", "Bilbo", "hello  there"},
                ChatInputRules.whisperParts(" /msg  Bilbo hello  there "));
        assertArrayEquals(new String[] {"/msg", "Bilbo"},
                ChatInputRules.whisperParts("/msg Bilbo"));
        assertArrayEquals(new String[] {"/msg"},
                ChatInputRules.whisperParts("/msg"));
        assertArrayEquals(new String[] {""},
                ChatInputRules.whisperParts(null));
    }

    @Test
    public void theLimitIsAWallForMessagesAndNothingForCommands() {
        String full = repeat('x', ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(ChatInputRules.atMessageLimit(full));
        assertFalse(ChatInputRules.atMessageLimit(full.substring(1)));
        // A share token is one visible character however long it is.
        String withToken = repeat('x', ChatMessageValidator.MAX_CHARACTERS - 1)
                + "[i:Sword of Westernesse]";
        assertTrue(ChatInputRules.atMessageLimit(withToken));

        assertEquals(full, ChatInputRules.trimToLimit(full + "yyy"));
        assertEquals(withToken, ChatInputRules.trimToLimit(withToken + "y"));
        String command = "/" + repeat('x', ChatMessageValidator.MAX_CHARACTERS + 10);
        assertEquals(command, ChatInputRules.trimToLimit(command));
        assertEquals("", ChatInputRules.trimToLimit(null));
    }

    @Test
    public void aTypedColonClosingAKnownShortcodeAsksForASpace() {
        assertTrue(ChatInputRules.shortcodeJustClosed(":smile:", 7));
        assertTrue(ChatInputRules.shortcodeJustClosed("hi :smile:", 10));
        // An unknown name, a colon with a space after it, or a colon
        // that is not the closing one asks for nothing.
        assertFalse(ChatInputRules.shortcodeJustClosed(":nosuchemoji:", 13));
        assertFalse(ChatInputRules.shortcodeJustClosed(":smile: ", 7));
        assertFalse(ChatInputRules.shortcodeJustClosed("::", 2));
        assertFalse(ChatInputRules.shortcodeJustClosed(":smile", 6));
        assertFalse(ChatInputRules.shortcodeJustClosed("smile:", 6));
        assertFalse(ChatInputRules.shortcodeJustClosed(null, 3));
        assertFalse(ChatInputRules.shortcodeJustClosed(":smile:", 9));
    }

    @Test
    public void outgoingMessagesConvertEmoticonsOnlyWhenAskedAndWhenTheyFit() {
        boolean emojis = LostTalesConfig.enableChatEmojis;
        boolean convert = LostTalesConfig.convertChatEmoticons;
        try {
            LostTalesConfig.enableChatEmojis = true;
            LostTalesConfig.convertChatEmoticons = true;
            assertEquals(":slight_smile:", ChatInputRules.outgoingMessage(":)"));
            // Converting would push the message past the limit, so it
            // goes out as typed.
            String nearLimit = repeat('x',
                    ChatMessageValidator.MAX_CHARACTERS - 3) + " :)";
            assertEquals(nearLimit, ChatInputRules.outgoingMessage(nearLimit));
            LostTalesConfig.convertChatEmoticons = false;
            assertEquals(":)", ChatInputRules.outgoingMessage(":)"));
            LostTalesConfig.convertChatEmoticons = true;
            LostTalesConfig.enableChatEmojis = false;
            assertEquals(":)", ChatInputRules.outgoingMessage(":)"));
        } finally {
            LostTalesConfig.enableChatEmojis = emojis;
            LostTalesConfig.convertChatEmoticons = convert;
        }
    }

    private static String repeat(char character, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            text.append(character);
        }
        return text.toString();
    }
}
