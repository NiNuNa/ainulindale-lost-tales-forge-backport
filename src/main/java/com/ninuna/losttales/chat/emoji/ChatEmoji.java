package com.ninuna.losttales.chat.emoji;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical emoji registry shared by the picker, the parser, and the chat
 * renderer. One constant per sprite on the bundled sheet; the shortcode is the
 * wire format, so it is protocol surface: never rename or reuse one without a
 * compatibility plan. Sprite cells are addressed explicitly so reordering
 * constants (for picker layout) can never silently remap artwork.
 *
 * <p>A constant may also carry the Unicode emoji it stands for — what the
 * Discord bridge posts outbound and maps back inbound — and <em>aliases</em>:
 * other names that resolve to it on input (completion, normalization, the
 * bridge) while everything internal — the wire, copies, the renderer — stays
 * on the canonical name. Most aliases are names Discord itself recognises,
 * verified against Discord's shortcode set; the one project alias,
 * {@code skeleton} for the skull, names the mob the sprite draws. The
 * sprites of the mod's own — {@code cutesy}, {@code discord},
 * {@code console}, {@code creeper} — have no Unicode form and cross the
 * bridge as their literal shortcode text.</p>
 *
 * <p>This class is intentionally free of Minecraft imports so it is loadable
 * on a dedicated server and testable without a game runtime.</p>
 */
public enum ChatEmoji {
    // Declaration order is the picker's browsing order and follows
    // Discord's: the smileys in Unicode's expression sequence — happy
    // through affectionate, silly, neutral, sleepy-sick, surprised,
    // sad, angry — then the monsters, the hands and body, the hearts,
    // the animals, the food, and the mod's own objects last. The sheet
    // cell each sprite lives in is pinned to its constant, so
    // reordering here can never remap artwork.
    GRINNING("grinning", 7, 0, "😀", "grinning_face"),
    SMILEY("smiley", 7, 1, "😃"),
    SMILE("smile", 6, 0, "😄"),
    LAUGHING("laughing", 4, 0, "😆", "satisfied"),
    SWEAT_SMILE("sweat_smile", 8, 0, "😅"),
    JOY("joy", 0, 4, "😂"),
    SLIGHT_SMILE("slight_smile", 0, 0, "🙂",
            "slightly_smiling_face"),
    UPSIDE_DOWN("upside_down", 0, 3, "🙃", "upside_down_face"),
    BLUSH("blush", 2, 3, "😊"),
    HEART_EYES("heart_eyes", 0, 2, "😍"),
    KISSING_CLOSED_EYES("kissing_closed_eyes", 3, 4, "😚"),
    KISSING_SMILING_EYES("kissing_smiling_eyes", 4, 4, "😙"),
    SMILING_FACE_WITH_TEAR("smiling_face_with_tear", 3, 1, "🥲"),
    YUM("yum", 4, 3, "😋"),
    ZANY_FACE("zany_face", 4, 2, "🤪"),
    STUCK_OUT_TONGUE_CLOSED_EYES("stuck_out_tongue_closed_eyes", 4, 1,
            "😝"),
    CUTESY("cutesy", 5, 1, ""),
    EXPRESSIONLESS("expressionless", 1, 0, "😑"),
    NO_MOUTH("no_mouth", 1, 2, "😶"),
    SMIRK("smirk", 5, 0, "😏", "smirking_face"),
    UNAMUSED("unamused", 1, 1, "😒"),
    ROLLING_EYES("rolling_eyes", 6, 1, "🙄",
            "face_with_rolling_eyes"),
    PENSIVE("pensive", 3, 3, "😔"),
    DROOLING_FACE("drooling_face", 2, 4, "🤤"),
    NAUSEATED_FACE("nauseated_face", 8, 2, "🤢"),
    DIZZY_FACE("dizzy_face", 7, 3, "😵"),
    /**
     * The plain frown U+1F641, whose Discord names are the aliases below;
     * Discord's own {@code :frowning:} is the open-mouthed U+1F626 and is
     * deliberately not mapped to this sprite.
     */
    FROWNING("frowning", 0, 1, "🙁",
            "slight_frown", "slightly_frowning_face"),
    OPEN_MOUTH("open_mouth", 1, 3, "😮"),
    ASTONISHED("astonished", 1, 4, "😲"),
    FLUSHED("flushed", 2, 0, "😳", "flushed_face"),
    PLEADING_FACE("pleading_face", 2, 1, "🥺"),
    FACE_HOLDING_BACK_TEARS("face_holding_back_tears", 7, 2,
            "🥹"),
    FEARFUL("fearful", 2, 2, "😨"),
    COLD_SWEAT("cold_sweat", 6, 4, "😰"),
    SOB("sob", 3, 0, "😭"),
    DISAPPOINTED("disappointed", 3, 2, "😞"),
    RAGE("rage", 8, 1, "😡", "pout"),
    ANGRY("angry", 8, 3, "😠"),
    SKULL("skull", 2, 5, "💀", "skeleton"),
    ZOMBIE("zombie", 3, 5, "🧟"),
    CREEPER("creeper", 1, 5, ""),
    INDEX_POINTING_AT_THE_VIEWER("index_pointing_at_the_viewer", 6, 3,
            "🫵"),
    EAR("ear", 0, 5, "👂"),
    BROKEN_HEART("broken_heart", 6, 2, "💔"),
    HEART("heart", 5, 2, "❤\uFE0F"),
    YELLOW_HEART("yellow_heart", 9, 3, "💛"),
    GREEN_HEART("green_heart", 9, 1, "💚"),
    BLUE_HEART("blue_heart", 9, 0, "💙"),
    PURPLE_HEART("purple_heart", 9, 2, "💜"),
    WHITE_HEART("white_heart", 9, 4, "🤍"),
    FOX("fox", 4, 5, "🦊", "fox_face"),
    CAT("cat", 7, 5, "🐱"),
    KANGAROO("kangaroo", 7, 4, "🦘"),
    CHICKEN("chicken", 6, 5, "🐔"),
    BEE("bee", 5, 5, "🐝", "honeybee"),
    CHEESE("cheese", 8, 4, "🧀", "cheese_wedge"),
    DISCORD("discord", 5, 3, ""),
    CONSOLE("console", 5, 4, "");

    /**
     * Square sprite edge in texels; also the on-screen size everywhere,
     * including inline chat, whose 11px line stride leaves room for it.
     */
    public static final int SPRITE_SIZE = 10;
    /** Cell-to-cell distance on the sheet: sprite plus a one-texel gutter. */
    public static final int SHEET_STRIDE = 11;
    public static final int SHEET_WIDTH = 109;
    public static final int SHEET_HEIGHT = 65;
    /** Domain-relative path of the sprite sheet inside the losttales assets. */
    public static final String TEXTURE_PATH = "textures/gui/emojis.png";
    /** The emoji variation selector (U+FE0F); optional after some emoji. */
    private static final char VARIATION_SELECTOR = '\uFE0F';

    private static final Map<String, ChatEmoji> BY_NAME =
            new HashMap<String, ChatEmoji>();
    /** Canonical names and aliases together; what input resolves by. */
    private static final Map<String, ChatEmoji> BY_INPUT_NAME =
            new HashMap<String, ChatEmoji>();
    /** Emoji with a Unicode form, longest form first, for inbound matching. */
    private static final List<ChatEmoji> BY_UNICODE;
    private static final int LONGEST_NAME;

    static {
        int longest = 0;
        List<ChatEmoji> withUnicode = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : values()) {
            if (BY_NAME.put(emoji.emojiName, emoji) != null
                    || BY_INPUT_NAME.put(emoji.emojiName, emoji) != null) {
                throw new IllegalStateException(
                        "duplicate chat emoji shortcode");
            }
            longest = Math.max(longest, emoji.emojiName.length());
            for (String alias : emoji.aliases) {
                if (BY_INPUT_NAME.put(alias, emoji) != null) {
                    throw new IllegalStateException(
                            "duplicate chat emoji alias: " + alias);
                }
                longest = Math.max(longest, alias.length());
            }
            if (emoji.unicode.length() > 0) {
                withUnicode.add(emoji);
            }
        }
        Collections.sort(withUnicode, new Comparator<ChatEmoji>() {
            @Override
            public int compare(ChatEmoji left, ChatEmoji right) {
                return right.unicode.length() - left.unicode.length();
            }
        });
        BY_UNICODE = Collections.unmodifiableList(withUnicode);
        LONGEST_NAME = longest;
    }

    private final String emojiName;
    private final int column;
    private final int row;
    private final String unicode;
    private final List<String> aliases;

    private ChatEmoji(String emojiName, int column, int row, String unicode,
                      String... aliases) {
        this.emojiName = emojiName;
        this.column = column;
        this.row = row;
        this.unicode = unicode == null ? "" : unicode;
        this.aliases = Collections.unmodifiableList(
                Arrays.asList(aliases));
    }

    /** Lowercase canonical identifier between the colons, e.g. {@code joy}. */
    public String getName() {
        return this.emojiName;
    }

    /** Canonical textual representation, e.g. {@code :joy:}. */
    public String getShortcode() {
        return ":" + this.emojiName + ":";
    }

    /** The Unicode emoji this sprite stands for; empty for the mod's own. */
    public String getUnicode() {
        return this.unicode;
    }

    /** Discord-recognised alternative names; input only, never written. */
    public List<String> getAliases() {
        return this.aliases;
    }

    /** Left texel of this sprite's cell on the sheet. */
    public int getTextureU() {
        return this.column * SHEET_STRIDE;
    }

    /** Top texel of this sprite's cell on the sheet. */
    public int getTextureV() {
        return this.row * SHEET_STRIDE;
    }

    /** Resolves a canonical identifier; aliases stay literal text here. */
    public static ChatEmoji fromName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /** Resolves a canonical identifier or an alias, for input paths. */
    public static ChatEmoji fromInputName(String name) {
        return name == null ? null : BY_INPUT_NAME.get(name);
    }

    /**
     * The emoji whose Unicode form starts at {@code index} of the text,
     * or null. Longest forms win, a form carrying a variation selector
     * also matches without it, and a stray selector directly after a
     * match is absorbed into its length.
     */
    public static UnicodeMatch matchUnicode(String text, int index) {
        if (text == null || index < 0 || index >= text.length()) {
            return null;
        }
        for (ChatEmoji emoji : BY_UNICODE) {
            int length = matchLength(text, index, emoji.unicode);
            if (length > 0) {
                if (index + length < text.length() && text.charAt(
                        index + length) == VARIATION_SELECTOR) {
                    length++;
                }
                return new UnicodeMatch(emoji, length);
            }
        }
        return null;
    }

    private static int matchLength(String text, int index, String unicode) {
        if (text.startsWith(unicode, index)) {
            return unicode.length();
        }
        int bare = unicode.length() - 1;
        if (bare > 0 && unicode.charAt(bare) == VARIATION_SELECTOR
                && text.regionMatches(index, unicode, 0, bare)) {
            return bare;
        }
        return 0;
    }

    /** One Unicode occurrence: which emoji, and how many chars it took. */
    public static final class UnicodeMatch {
        public final ChatEmoji emoji;
        public final int length;

        private UnicodeMatch(ChatEmoji emoji, int length) {
            this.emoji = emoji;
            this.length = length;
        }
    }

    /** Upper bound for shortcode-name scanning, aliases included. */
    public static int longestName() {
        return LONGEST_NAME;
    }
}
