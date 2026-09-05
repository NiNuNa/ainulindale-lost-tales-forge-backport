package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmoticonConverter;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.Locale;

/**
 * The rules about the text in the chat's input field that need no
 * screen: what is a command, what is one of the chat's own whisper
 * verbs, where the message limit is, and what a typed shortcode or an
 * outgoing message becomes. Pure functions over strings, so the screen
 * asks them and tests pin them.
 */
final class ChatInputRules {
    /** The verbs the chat answers itself rather than sending as commands. */
    private static final String[] WHISPER_VERBS = {"/msg", "/tell", "/w"};

    private ChatInputRules() {}

    /** Whether the text is a command rather than a message. */
    static boolean isCommand(String text) {
        return text != null && text.trim().startsWith("/");
    }

    /**
     * Whether the text is a command the server will answer, rather than
     * a message or one of the chat's own whisper verbs, which never
     * reach the server as commands.
     */
    static boolean isServerCommand(String text) {
        return isCommand(text) && !isWhisperCommand(text);
    }

    /** {@code /msg}, {@code /tell} and {@code /w}, whatever follows. */
    static boolean isWhisperCommand(String text) {
        if (text == null) {
            return false;
        }
        String verb = text.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        for (String whisperVerb : WHISPER_VERBS) {
            if (whisperVerb.equals(verb)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The verb, the name and the rest of a whisper command, as far as
     * they are there: {@code /msg Name the text} gives three parts,
     * {@code /msg Name} two, {@code /msg} one.
     */
    static String[] whisperParts(String command) {
        return (command == null ? "" : command.trim()).split("\\s+", 3);
    }

    /** Whether a message holds as many visible characters as may be sent. */
    static boolean atMessageLimit(String text) {
        return ChatMessageValidator.visibleLength(text)
                >= ChatMessageValidator.MAX_CHARACTERS;
    }

    /**
     * A message that got past the limit anyway (a paste) cut back to it,
     * one character at a time from the end, so the field never holds
     * more than can be sent. A command is never cut: it keeps its own
     * limits.
     */
    static String trimToLimit(String text) {
        if (text == null) {
            return "";
        }
        if (isCommand(text)) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 0 && ChatMessageValidator.visibleLength(
                trimmed) > ChatMessageValidator.MAX_CHARACTERS) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Whether the colon just typed at {@code cursor} closed a canonical
     * emoji shortcode with nothing after it, so the field should get
     * the same trailing space every inserted token gets. Only the exact
     * typed colon triggers this; nothing rewrites text already standing.
     */
    static boolean shortcodeJustClosed(String text, int cursor) {
        if (text == null || cursor < 3 || cursor > text.length()
                || text.charAt(cursor - 1) != ':'
                || (cursor < text.length() && text.charAt(cursor) == ' ')) {
            return false;
        }
        int open = cursor - 2;
        while (open > 0 && isShortcodeNameCharacter(text.charAt(open))) {
            open--;
        }
        if (text.charAt(open) != ':' || open >= cursor - 2) {
            return false;
        }
        return ChatEmoji.fromName(text.substring(open + 1, cursor - 1)) != null;
    }

    /** The shortcode alphabet, exactly as the parser scans it. */
    static boolean isShortcodeNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    /**
     * The message as it goes out: whole-token emoticons become their
     * canonical shortcodes when the setting asks for it and the longer
     * text still fits the limit; otherwise exactly what was typed.
     */
    static String outgoingMessage(String message) {
        if (!LostTalesConfig.enableChatEmojis
                || !LostTalesConfig.convertChatEmoticons) {
            return message;
        }
        String converted = ChatEmoticonConverter.convert(message);
        return ChatMessageValidator.isValid(converted) ? converted : message;
    }
}
