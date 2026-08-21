package com.ninuna.losttales.chat;

/**
 * Player-typed formatting codes. Minecraft rejects the section sign in
 * chat input, so — like most servers — players write {@code &6gold} and the
 * client translates it for display. The wire format keeps the ampersand
 * form: it survives validation, copying, and unsupported clients unchanged.
 */
public final class ChatFormattingCodes {
    private static final char SECTION_SIGN = 167;

    private ChatFormattingCodes() {}

    /** Translates {@code &x} to a section-sign code for valid codes only. */
    public static String translateAmpersand(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder translated = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '&' && index + 1 < text.length()
                    && isFormattingCode(text.charAt(index + 1))) {
                translated.append(SECTION_SIGN).append(Character
                        .toLowerCase(text.charAt(index + 1)));
                index++;
                continue;
            }
            translated.append(character);
        }
        return translated.toString();
    }

    private static boolean isFormattingCode(char character) {
        char lower = Character.toLowerCase(character);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }
}
