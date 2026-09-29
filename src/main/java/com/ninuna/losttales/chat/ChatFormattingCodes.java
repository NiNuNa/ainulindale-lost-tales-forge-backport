package com.ninuna.losttales.chat;

/**
 * Vanilla's section-sign formatting codes, as text the <em>server</em>
 * built may carry them: a team colour on a name, a coloured item name
 * in a death message. Players never get to write them — Minecraft
 * kicks a client that sends a section sign, {@code ChatMessageValidator}
 * refuses one, and an ampersand in a message is an ampersand: the
 * markup ({@code ChatMarkdown}) is the one styling a player's own words
 * carry, which is what keeps every line readable and every colour the
 * palette's.
 */
public final class ChatFormattingCodes {
    private static final char SECTION_SIGN = 167;

    private ChatFormattingCodes() {}

    /**
     * Removes every section sign, with the code letter after it, from text
     * the server built (a team colour on a name, a coloured item name in a
     * death message), leaving the words. A lone section sign goes too, so no
     * run of them can put a code back together. Server-safe, unlike
     * {@code EnumChatFormatting.getTextWithoutFormattingCodes}, which is
     * client-only in 1.7.10.
     */
    public static String stripSectionCodes(String text) {
        if (text == null) {
            return "";
        }
        if (text.indexOf(SECTION_SIGN) < 0) {
            return text;
        }
        StringBuilder stripped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == SECTION_SIGN) {
                if (index + 1 < text.length() && isFormattingCode(text.charAt(index + 1))) {
                    index++;
                }
                continue;
            }
            stripped.append(character);
        }
        return stripped.toString();
    }

    /** Whether the text holds a section sign or a control character, which no player's words may. */
    public static boolean hasCodeOrControl(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == SECTION_SIGN || character < 32 || character == 127) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFormattingCode(char character) {
        char lower = Character.toLowerCase(character);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }
}
