package com.ninuna.losttales.chat;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.List;
import net.minecraft.util.ChatAllowedCharacters;

/**
 * Shared, deterministic validation for untrusted player conversation text.
 * The player-facing limit counts each share token as one character (it
 * renders as one icon), so sharing an item or marker never costs the sender
 * message room; the raw text is still bounded so the wire stays small.
 *
 * <p>A message holds up to {@link #MAX_PARAGRAPHS} paragraphs, a line
 * break between each two. A kept message is always in one form: every
 * paragraph trimmed and none empty ({@link #paragraphs}). The chat lays a
 * paragraph out under the one before it, so a break can never pass for a
 * line of its own; wherever a message is shown or logged as one line, its
 * breaks are spaces ({@link #oneLine}) or written out ({@link #logged}).</p>
 */
public final class ChatMessageValidator {
    /**
     * Visible characters, with every share token counted once: room for
     * a role-play turn. The chat folds a long message.
     */
    public static final int MAX_CHARACTERS = 1024;
    /** Longest token: opener, name, ordinal suffix, and closer. */
    private static final int MAX_TOKEN_LENGTH =
            3 + ChatShareTokenParser.MAX_NAME_LENGTH + 4;
    /** Raw text bound: the visible limit plus the tokens' own characters. */
    public static final int MAX_RAW_CHARACTERS = MAX_CHARACTERS
            + ChatShareTokenParser.MAX_TOKENS * MAX_TOKEN_LENGTH;
    /** Worst case UTF-8 for {@link #MAX_RAW_CHARACTERS} BMP characters. */
    public static final int MAX_UTF8_BYTES = MAX_RAW_CHARACTERS * 3;
    /** The most paragraphs a message holds. */
    public static final int MAX_PARAGRAPHS = 8;
    /** What stands between two paragraphs. */
    public static final char PARAGRAPH_BREAK = '\n';

    private ChatMessageValidator() {}

    public static boolean isValid(String message) {
        if (message == null || message.length() == 0
                || message.length() > MAX_RAW_CHARACTERS
                || visibleLength(message) > MAX_CHARACTERS
                || !message.equals(message.trim())) {
            return false;
        }
        int breaks = 0;
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character == PARAGRAPH_BREAK) {
                // Trimmed, the message neither starts nor ends on one, and
                // every paragraph either side of it is trimmed and has words.
                char before = message.charAt(index - 1);
                char after = message.charAt(index + 1);
                if (++breaks >= MAX_PARAGRAPHS || before == ' '
                        || after == ' ' || after == PARAGRAPH_BREAK) {
                    return false;
                }
                continue;
            }
            if (character == '\u00a7' || isHiddenBreak(character)
                    || isDirectionMark(character)
                    || !ChatAllowedCharacters.isAllowedCharacter(character)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A character some logs, editors and Discord take for a line break,
     * though it is not one here: the next line (U+0085) and the line and
     * paragraph separators (U+2028, U+2029).
     */
    static boolean isHiddenBreak(char character) {
        return character == '\u0085' || character == '\u2028'
                || character == '\u2029';
    }

    /**
     * An invisible mark that turns the text after it around (the embeddings,
     * overrides and isolates, U+202A-U+202E and U+2066-U+2069): it could
     * make a line read as something other than what was typed.
     */
    static boolean isDirectionMark(char character) {
        return character >= '\u202a' && character <= '\u202e'
                || character >= '\u2066' && character <= '\u2069';
    }

    /**
     * Text in the form a message keeps its paragraphs: every line break a
     * paragraph break (the hidden ones too), direction marks dropped, each
     * paragraph trimmed and empty ones dropped, and the paragraphs past the
     * last a message holds joined to it by spaces.
     */
    public static String paragraphs(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder plain = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\r' || isHiddenBreak(character)) {
                plain.append('\n');
            } else if (!isDirectionMark(character)) {
                plain.append(character);
            }
        }
        String[] lines = plain.toString().split("\n");
        StringBuilder kept = new StringBuilder(text.length());
        int count = 0;
        for (String line : lines) {
            String words = line.trim();
            if (words.length() == 0) {
                continue;
            }
            if (count > 0) {
                kept.append(count < MAX_PARAGRAPHS ? PARAGRAPH_BREAK : ' ');
            }
            kept.append(words);
            count++;
        }
        return kept.toString();
    }

    /** The message on one line: each paragraph break a space. */
    public static String oneLine(String message) {
        return message == null ? ""
                : message.replace(PARAGRAPH_BREAK, ' ');
    }

    /**
     * The message as a log line writes it, so no message can start a line
     * of its own in a log: a backslash doubled first, then each line break
     * written out as {@code \n} or {@code \r}, and every other character a
     * log may take for a break as its {@code \}{@code u} code. The game's
     * chat log goes through here too, patched in by the coremod.
     */
    public static String logged(String message) {
        if (message == null) {
            return "";
        }
        StringBuilder written = new StringBuilder(message.length() + 8);
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character == '\\') {
                written.append("\\\\");
            } else if (character == '\n') {
                written.append("\\n");
            } else if (character == '\r') {
                written.append("\\r");
            } else if (isHiddenBreak(character)) {
                written.append(String.format(java.util.Locale.ROOT,
                        "\\u%04x", Integer.valueOf(character)));
            } else {
                written.append(character);
            }
        }
        return written.toString();
    }

    /**
     * A component's words as a message may carry them: formatting codes
     * and characters the chat refuses dropped, line breaks made spaces,
     * cut to a message's length; empty when nothing is left.
     */
    public static String cleaned(String raw) {
        String text = raw == null ? "" : raw;
        StringBuilder kept = new StringBuilder(text.length());
        boolean skipCode = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (skipCode) {
                skipCode = false;
            } else if (character == '\u00a7') {
                skipCode = true;
            } else if (character == '\n' || character == '\r'
                    || isHiddenBreak(character)) {
                kept.append(' ');
            } else if (!isDirectionMark(character)
                    && ChatAllowedCharacters.isAllowedCharacter(character)) {
                kept.append(character);
            }
        }
        String words = kept.toString().trim();
        while (words.length() > 0 && !isValid(words)) {
            words = words.substring(0, words.length() - 1).trim();
        }
        return words;
    }


    /** Length as the player perceives it: share tokens count as one. */
    public static int visibleLength(String message) {
        if (message == null) {
            return 0;
        }
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        int length = message.length();
        for (int index = 0; index < tokens.size(); index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            length -= (token.end - token.start) - 1;
        }
        return length;
    }
}
