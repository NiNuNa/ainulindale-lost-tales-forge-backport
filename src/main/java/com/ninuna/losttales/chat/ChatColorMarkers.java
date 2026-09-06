package com.ninuna.losttales.chat;

/**
 * The wire form of an exact-colour mark on a chat component: a click
 * event value the client reads as "draw this run in this RGB" and never
 * as an action. The server puts one on a component when it knows a
 * colour vanilla's sixteen cannot say — an NPC's faction on a trader
 * notice — and the client's own lines carry the same mark for the same
 * reason; the encoding lives here so both sides agree on it. Free of
 * Minecraft imports.
 */
public final class ChatColorMarkers {
    /** What every colour mark starts with. */
    public static final String PREFIX = "losttales-chat-color:";

    private ChatColorMarkers() {}

    /** The mark for an RGB colour. */
    public static String value(int rgb) {
        String hex = Integer.toHexString(rgb & 0xFFFFFF);
        StringBuilder result = new StringBuilder(PREFIX.length() + 6).append(PREFIX);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }

    /** The RGB a mark names, or null for a value that is not a colour mark. */
    public static Integer decode(String value) {
        if (value == null || !value.startsWith(PREFIX)
                || value.length() != PREFIX.length() + 6) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(
                    value.substring(PREFIX.length()), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
