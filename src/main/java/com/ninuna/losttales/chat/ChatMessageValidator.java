package com.ninuna.losttales.chat;

import net.minecraft.util.ChatAllowedCharacters;

/** Shared, deterministic validation for untrusted player conversation text. */
public final class ChatMessageValidator {
    public static final int MAX_CHARACTERS = 100;
    public static final int MAX_UTF8_BYTES = 400;

    private ChatMessageValidator() {}

    public static boolean isValid(String message) {
        if (message == null || message.length() == 0
                || message.length() > MAX_CHARACTERS
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
}
