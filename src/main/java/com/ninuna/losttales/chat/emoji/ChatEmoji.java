package com.ninuna.losttales.chat.emoji;

import java.util.HashMap;
import java.util.Map;

/**
 * Canonical emote registry shared by the picker, the parser, and the chat
 * renderer. One constant per sprite on the bundled sheet; the shortcode is the
 * wire format, so it is protocol surface: never rename or reuse one without a
 * compatibility plan. Sprite cells are addressed explicitly so reordering
 * constants (for picker layout) can never silently remap artwork.
 *
 * <p>This class is intentionally free of Minecraft imports so it is loadable
 * on a dedicated server and testable without a game runtime.</p>
 */
public enum ChatEmoji {
    SMILE("smile", 0, 0),
    GRIN("grin", 1, 0),
    FLUSHED("flushed", 2, 0),
    SOB("sob", 3, 0),
    LAUGH("laugh", 4, 0),
    ANGRY("angry", 5, 0),
    FROWN("frown", 0, 1),
    CALM("calm", 1, 1),
    AWE("awe", 2, 1),
    CRY("cry", 3, 1),
    JOY("joy", 4, 1),
    SAD("sad", 5, 1),
    BLUSH("blush", 0, 2),
    STARE("stare", 1, 2),
    SWEAT("sweat", 2, 2),
    CONFUSED("confused", 3, 2),
    EYEROLL("eyeroll", 4, 2),
    UNAMUSED("unamused", 0, 3),
    GASP("gasp", 1, 3),
    SHY("shy", 2, 3),
    GRIMACE("grimace", 3, 3),
    SILLY("silly", 4, 3),
    PLEADING("pleading", 0, 4),
    SHOCKED("shocked", 1, 4),
    DROOL("drool", 2, 4),
    DIZZY("dizzy", 3, 4),
    SMUG("smug", 4, 4);

    /**
     * Square sprite edge in texels; also the on-screen size everywhere,
     * including inline chat, whose 11px line stride leaves room for it.
     */
    public static final int SPRITE_SIZE = 10;
    /** Cell-to-cell distance on the sheet: sprite plus a one-texel gutter. */
    public static final int SHEET_STRIDE = 11;
    public static final int SHEET_WIDTH = 65;
    public static final int SHEET_HEIGHT = 54;
    /** Domain-relative path of the sprite sheet inside the losttales assets. */
    public static final String TEXTURE_PATH = "textures/gui/emotes.png";

    private static final Map<String, ChatEmoji> BY_NAME =
            new HashMap<String, ChatEmoji>();
    private static final int LONGEST_NAME;

    static {
        int longest = 0;
        for (ChatEmoji emoji : values()) {
            BY_NAME.put(emoji.emojiName, emoji);
            longest = Math.max(longest, emoji.emojiName.length());
        }
        if (BY_NAME.size() != values().length) {
            throw new IllegalStateException("duplicate chat emoji shortcode");
        }
        LONGEST_NAME = longest;
    }

    private final String emojiName;
    private final int column;
    private final int row;

    private ChatEmoji(String emojiName, int column, int row) {
        this.emojiName = emojiName;
        this.column = column;
        this.row = row;
    }

    /** Lowercase identifier between the colons, e.g. {@code smile}. */
    public String getName() {
        return this.emojiName;
    }

    /** Canonical textual representation, e.g. {@code :smile:}. */
    public String getShortcode() {
        return ":" + this.emojiName + ":";
    }

    /** Left texel of this sprite's cell on the sheet. */
    public int getTextureU() {
        return this.column * SHEET_STRIDE;
    }

    /** Top texel of this sprite's cell on the sheet. */
    public int getTextureV() {
        return this.row * SHEET_STRIDE;
    }

    /** Resolves the identifier between colons; null for unknown names. */
    public static ChatEmoji fromName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /** Upper bound for shortcode-name scanning, so parsing stays linear. */
    public static int longestName() {
        return LONGEST_NAME;
    }
}
