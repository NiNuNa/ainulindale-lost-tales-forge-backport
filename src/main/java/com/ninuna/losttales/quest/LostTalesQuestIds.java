package com.ninuna.losttales.quest;

/**
 * The one bound on a quest id: 256 bytes of UTF-8. Every place a quest id
 * crosses the wire or is saved holds it to this, so a quest the server
 * knows can always be sent and kept.
 */
public final class LostTalesQuestIds {
    public static final int MAX_BYTES = 256;

    private LostTalesQuestIds() {}

    /** Whether the id is one a quest may have: not empty and within {@link #MAX_BYTES}. */
    public static boolean fits(String id) {
        return id != null && id.length() > 0 && utf8Bytes(id) <= MAX_BYTES;
    }

    /** How many bytes the text takes as UTF-8, counted without copying it. */
    public static int utf8Bytes(String text) {
        if (text == null) {
            return 0;
        }
        int bytes = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c)
                    && index + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }
}
