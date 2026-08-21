package com.ninuna.losttales.chat;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.List;
import net.minecraft.util.ChatAllowedCharacters;

/**
 * Shared, deterministic validation for untrusted player conversation text.
 * The player-facing limit counts each share token as one character (it
 * renders as one icon), so sharing an item or marker never costs the sender
 * message room; the raw text is still bounded so the wire stays small.
 */
public final class ChatMessageValidator {
    /** Visible characters, with every share token counted once. */
    public static final int MAX_CHARACTERS = 100;
    /** Longest token: opener, name, ordinal suffix, and closer. */
    private static final int MAX_TOKEN_LENGTH =
            3 + ChatShareTokenParser.MAX_NAME_LENGTH + 4;
    /** Raw text bound: the visible limit plus the tokens' own characters. */
    public static final int MAX_RAW_CHARACTERS = MAX_CHARACTERS
            + ChatShareTokenParser.MAX_TOKENS * MAX_TOKEN_LENGTH;
    /** Worst case UTF-8 for {@link #MAX_RAW_CHARACTERS} BMP characters. */
    public static final int MAX_UTF8_BYTES = MAX_RAW_CHARACTERS * 3;

    private ChatMessageValidator() {}

    public static boolean isValid(String message) {
        if (message == null || message.length() == 0
                || message.length() > MAX_RAW_CHARACTERS
                || visibleLength(message) > MAX_CHARACTERS
                || !message.equals(message.trim())) {
            return false;
        }
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character == '\u00a7'
                    || !ChatAllowedCharacters.isAllowedCharacter(character)) {
                return false;
            }
        }
        return true;
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
