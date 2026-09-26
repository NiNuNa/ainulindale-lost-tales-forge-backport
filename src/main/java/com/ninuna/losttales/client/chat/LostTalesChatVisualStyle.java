package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.opengl.GL11;

/**
 * Lost Tales' ivory text and plum-black shadow treatment for chat. Every
 * chat element — text, emojis, head icons, item and marker icons — draws
 * its shadow as a flat {@link LostTalesUiInk#SHADOW} silhouette offset by one pixel at
 * two-thirds opacity, so the relationship between content and shadow is
 * identical at every GUI scale; inline glyphs share the box and baseline
 * rules of {@link ChatInlineIcons}.
 *
 * <p>Every one of those draws goes through {@link LostTalesUiInk#beginContent()} first,
 * which turns blending on. It has to: {@code Gui.drawRect} — which every
 * panel, strip and bar in the chat is built from — <em>disables</em>
 * blending when it is done, and text or a sprite drawn after one would
 * otherwise land opaque, shadow and all. Nothing here relies on the
 * state it is handed, so no call site has to remember that.</p>
 *
 * <p>Channel prefix components are skipped entirely while the chat
 * screen is open (the tabs already say which channel a line belongs
 * to), and layout markers advance the cursor without drawing.</p>
 */
public final class LostTalesChatVisualStyle {

    /**
     * The chat's one tone for what is said about a line rather than in
     * it: the Console's own colour. Timestamps, the Server's and the
     * Client's names, a day's rule, the reply chip, the typing line, the
     * edited mark, inline code and a logged command's words, and the
     * words in an empty place — a window's invitation, the empty
     * screen's line, the empty field's hint — all wear it. Asked for
     * rather than kept, so a server that recolours the Console recolours
     * every one of them with it.
     */
    public static int asideRgb() {
        return ClientChatChannelState.displayColor(ChatChannel.CLIENT_CONSOLE);
    }

    /** The line under the pointer, in the client's chosen palette colour; plum grey until it chooses. */
    static int selectedLineRgb() {
        return paletteRgb(LostTalesConfig.chatSelectedLineColor,
                LostTalesColors.PLUM_GRAY);
    }

    /** A line that @-mentions this player, in the client's chosen palette colour; coral until it chooses. */
    static int mentionLineRgb() {
        return paletteRgb(LostTalesConfig.chatMentionLineColor,
                LostTalesColors.CORAL);
    }

    /** The line a reply's quote jumped to, while it is lit, in the chosen colour. */
    static int replyHighlightRgb() {
        return paletteRgb(LostTalesConfig.chatReplyHighlightColor,
                LostTalesColors.APRICOT);
    }

    /**
     * A line that mentions this player, under the pointer: the palette
     * colour the client chose, or — automatic, the default — the mention
     * colour a shade lighter ({@link #automaticSelectedMentionRgb}).
     */
    static int selectedMentionLineRgb() {
        String chosen = LostTalesConfig.chatSelectedMentionColor;
        return LostTalesColors.isPaletteName(chosen)
                ? paletteRgb(chosen, LostTalesColors.CORAL)
                : automaticSelectedMentionRgb();
    }

    /**
     * The automatic selected mention: the mention colour one shade
     * lighter on its own ramp of the palette, and the selected line's
     * colour where the mention colour is already its ramp's lightest.
     */
    static int automaticSelectedMentionRgb() {
        return lighterShadeRgb(LostTalesConfig.chatMentionLineColor);
    }

    /**
     * The line a reply's quote jumped to, under the pointer while it is
     * still lit: its light one shade lighter, the selected line's colour
     * where there is none.
     */
    static int selectedReplyHighlightRgb() {
        return lighterShadeRgb(LostTalesConfig.chatReplyHighlightColor);
    }

    /** The palette colour a shade lighter than {@code name}, else the selected line's. */
    private static int lighterShadeRgb(String name) {
        String lighter = LostTalesColors.lighterStep(name);
        return lighter == null ? selectedLineRgb()
                : paletteRgb(lighter, LostTalesColors.MAUVE);
    }

    private static int paletteRgb(String name, int fallback) {
        return LostTalesColors.rgb(LostTalesColors.paletteColor(name, fallback));
    }

    private LostTalesChatVisualStyle() {}

    /**
     * The widest of the font's ten digits, its spacing column included:
     * what a run of digits that must keep its width whatever its value
     * is sized by — the timestamp column's clock and the input bar's
     * counter.
     */
    static int widestDigitWidth(FontRenderer font) {
        int widest = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            widest = Math.max(widest, font.getCharWidth(digit));
        }
        return widest;
    }

    /**
     * The size small text is drawn at — the input bar's counter, the
     * timestamps: one display pixel less per font pixel than the text
     * beside it, the next step down the screen can draw without pixels
     * of two sizes. Half at GUI scale 2, two thirds at 3, three quarters
     * at 4, five sixths at 6; full size at 1, which has no smaller step.
     * Asked with the display scale of this frame, so a GUI-scale change
     * picks the new step at once.
     */
    static float smallTextScale(int displayScaleFactor) {
        int factor = Math.max(1, displayScaleFactor);
        return Math.max(1, factor - 1) / (float)factor;
    }

    /**
     * How far below the full-size text's top small text starts, in GUI
     * pixels: its capitals centred on the full-size capitals by the
     * chat's one rule, the odd display pixel below, on whole display
     * pixels.
     */
    static float smallTextTopOffset(int displayScaleFactor) {
        int factor = Math.max(1, displayScaleFactor);
        int spare = LostTalesUiInk.CAP_HEIGHT
                * (factor - Math.max(1, factor - 1));
        return (spare / 2) / (float)factor;
    }

    /** The game's chat Scale setting: the scale the message stack is drawn at. */
    static float chatScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        float scale = minecraft == null || minecraft.ingameGUI == null
                ? 1.0F : minecraft.ingameGUI.getChatGUI().func_146244_h();
        return scale <= 0.0F ? 1.0F : scale;
    }

    /**
     * The chat's small text inside the message stack — the timestamps, a
     * reply's quote, the reaction chips and the dividers — in the
     * stack's own units, since its matrix already carries the chat
     * scale: {@link #smallPixels} display pixels per font pixel, always
     * smaller than the words and always on the display's grid. At the
     * default chat scale that is {@link #smallTextScale}.
     */
    static float stackSmallScale() {
        return stackRowScale(-1);
    }

    /**
     * How far below the words' top edge the stack's small text starts,
     * in the stack's units: its capitals centred on the words' by the
     * chat's one rule ({@link #smallDrop}).
     */
    static float stackSmallTopOffset() {
        int factor = LostTalesDisplayPixels.scaleFactor();
        float chat = chatScale();
        return smallDrop(chat, factor) / (float)factor / chat;
    }

    /**
     * Display pixels per font pixel of a row drawn {@code step} whole
     * display pixels from the words' own size, at {@code chatScale} on a
     * display of {@code displayScaleFactor} pixels per GUI pixel: that
     * many steps below or above the nearest whole number the words
     * reach, never under one. Step zero is the words' own size, whatever
     * the chat scale makes of it.
     *
     * <p>Only a whole number of display pixels per font pixel keeps the
     * font and the pixel art crisp, so this ladder is every size the
     * chat has: the small text is one step down and a message's speaker
     * one step up.</p>
     */
    static int rowPixels(float chatScale, int displayScaleFactor, int step) {
        float words = Math.max(0.0F, chatScale)
                * Math.max(1, displayScaleFactor);
        if (step > 0) {
            return (int)Math.floor(words + 0.001F) + step;
        }
        if (step < 0) {
            return Math.max(1, (int)Math.ceil(words - 0.001F) + step);
        }
        return Math.max(1, Math.round(words));
    }

    /**
     * The size a row drawn {@code step} steps from the words is, in the
     * stack's own units, since its matrix already carries the chat
     * scale. Step zero is the words' own size exactly.
     */
    static float stackRowScale(int step) {
        if (step == 0) {
            return 1.0F;
        }
        int factor = LostTalesDisplayPixels.scaleFactor();
        float chat = chatScale();
        float scale = rowPixels(chat, factor, step) / (float)factor / chat;
        // A display with no smaller size to offer keeps the words' own,
        // rather than handing a step down back as a step up.
        return step < 0 ? Math.min(1.0F, scale) : Math.max(1.0F, scale);
    }

    /**
     * The step a size option names. The words are read here, where the
     * option is read: {@code SMALLER} is one display pixel per font
     * pixel down the ladder, {@code LARGER} one up, and anything else —
     * {@code SAME} — the words' own size.
     */
    static int sizeStep(String option) {
        if (LostTalesConfig.CHAT_SIZE_SMALLER.equals(option)) {
            return -1;
        }
        return LostTalesConfig.CHAT_SIZE_LARGER.equals(option) ? 1 : 0;
    }

    /**
     * The step a message's own words take in this state. The open
     * window's words are the size every other row is measured against,
     * so they never step; the closed feed may take them down, and
     * everything a message is made of goes with them.
     */
    static int messageStep(boolean chatOpen) {
        return chatOpen ? 0
                : sizeStep(LostTalesConfig.chatFeedMessageSize);
    }

    /**
     * The size the row naming a message's speaker is drawn at: its own
     * step from the words <em>of that state</em>, so the feed moves the
     * speaker, the words and a quote together by moving the words.
     */
    static float speakerRowScale(boolean chatOpen) {
        return stackRowScale(messageStep(chatOpen) + sizeStep(chatOpen
                ? LostTalesConfig.chatSpeakerSize
                : LostTalesConfig.chatFeedSpeakerSize));
    }

    /** The size a message's own words are drawn at. */
    static float messageRowScale(boolean chatOpen) {
        return stackRowScale(messageStep(chatOpen));
    }

    /**
     * The size the row a reply opens with is drawn at: its own step from
     * the words of that state, the window's option or the feed's.
     */
    static float quoteRowScale(boolean chatOpen) {
        return stackRowScale(messageStep(chatOpen) + sizeStep(chatOpen
                ? LostTalesConfig.chatQuoteSize
                : LostTalesConfig.chatFeedQuoteSize));
    }

    /**
     * Display pixels per font pixel of small text beside words drawn at
     * {@code chatScale} on a display of {@code displayScaleFactor}
     * pixels per GUI pixel: the largest whole number below the words'
     * own, and never under one — one step down the ladder.
     */
    static int smallPixels(float chatScale, int displayScaleFactor) {
        return rowPixels(chatScale, displayScaleFactor, -1);
    }

    /**
     * Display pixels small text starts below the words' top edge: its
     * capitals centred on theirs, the odd display pixel below.
     */
    static int smallDrop(float chatScale, int displayScaleFactor) {
        float words = Math.max(0.0F, chatScale)
                * Math.max(1, displayScaleFactor);
        float spare = LostTalesUiInk.CAP_HEIGHT
                * (words - smallPixels(chatScale, displayScaleFactor));
        return Math.max(0, (int)Math.floor(spare / 2.0F + 0.001F));
    }

    /**
     * The size large text is drawn at: one display pixel more per font
     * pixel than the text beside it, the next step up the screen can
     * draw without pixels of two sizes. Double at GUI scale 1, three
     * halves at 2, four thirds at 3, five quarters at 4. Asked with the
     * display scale of this frame, so a GUI-scale change picks the new
     * step at once.
     */
    static float largeTextScale(int displayScaleFactor) {
        int factor = Math.max(1, displayScaleFactor);
        return (factor + 1) / (float)factor;
    }

    /**
     * How far from the words' top edge a row drawn at another size than
     * they are starts, in the stack's units ({@link #rowRise}), negative
     * being up the screen: every row of the stack stands on the line the
     * words stand on, so a speaker's row takes the room it gains above
     * them and a reply's quote gives its room back there.
     */
    static float stackRowTopOffset(float rowScale) {
        int factor = LostTalesDisplayPixels.scaleFactor();
        float chat = chatScale();
        float words = Math.max(0.001F, factor * chat);
        return -rowRise(chat, factor,
                Math.max(1, Math.round(rowScale * words))) / words;
    }

    /**
     * Display pixels per font pixel of large text beside words drawn at
     * {@code chatScale} on a display of {@code displayScaleFactor}
     * pixels per GUI pixel: the smallest whole number above the words'
     * own. Unlike the small size, every scale has one.
     */
    static int largePixels(float chatScale, int displayScaleFactor) {
        return rowPixels(chatScale, displayScaleFactor, 1);
    }

    /**
     * Display pixels a row drawn at {@code rowPixels} display pixels per
     * font pixel starts above the words' own top edge, negative for a
     * row drawn smaller than they are: the row is the words' row grown
     * or shrunk from the bottom, so the two share the line they stand
     * on.
     */
    static int rowRise(float chatScale, int displayScaleFactor,
                       int rowPixels) {
        float words = Math.max(0.0F, chatScale)
                * Math.max(1, displayScaleFactor);
        return Math.round(LostTalesChatOverlayRenderer.TEXT_OFFSET
                * (rowPixels - words));
    }

    /**
     * One row of a line, its shadow under it, at {@code x}, {@code y}. A
     * hovered message's body row moves as {@code motion} says: its chevron
     * and each of its words drawn where the motion has them; null draws
     * the row as it always stands. {@code chatLineId} is the message the
     * row is of, which its backdrops light with; zero for none.
     */
    static void drawFormatted(final FontRenderer font,
                              final IChatComponent line,
                              final ChatHeadMarker.Data metadata,
                              final int x, final int y, final int alpha,
                              final boolean chatOpen,
                              final ChatRowMotion motion,
                              final int chatLineId) {
        if (font == null || line == null || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        LostTalesUiInk.beginContent();
        final int shadow = LostTalesUiInk.shadowAlpha(alpha);
        LostTalesUiFlatLayers.Layers layers = new LostTalesUiFlatLayers.Layers() {
            @Override
            public void draw() {
                if (shadow > 0) {
                    drawComponentPass(font, line, metadata,
                            x + LostTalesUiInk.SHADOW_OFFSET, y + LostTalesUiInk.SHADOW_OFFSET, shadow, true,
                            chatOpen, motion, chatLineId);
                    // The line stands over its shadow, so a line fading
                    // does not show the shadow through its strokes.
                    LostTalesUiFlatLayers.nextLayer();
                }
                drawComponentPass(font, line, metadata, x, y, alpha, false,
                        chatOpen, motion, chatLineId);
            }
        };
        if (alpha >= 255 || LostTalesUiFlatLayers.isActive()) {
            layers.draw();
            return;
        }
        // A fading line fades as one picture with its shadow. The bounds
        // reach past the row's ink for the icons a line carries, which
        // stand a little taller than the capitals and may be wider than
        // the room their placeholders declare.
        LostTalesUiFlatLayers.draw(alpha, x - LINE_PICTURE_MARGIN,
                y - LINE_PICTURE_MARGIN,
                x + inkEnd(font, line, chatOpen) + 3 * LINE_PICTURE_MARGIN,
                y + font.FONT_HEIGHT + LINE_PICTURE_MARGIN, layers);
    }

    /** How far past a line's words its icons may stand, in the line's units. */
    private static final int LINE_PICTURE_MARGIN = 4;

    static void drawPlain(FontRenderer font, String text,
                          int x, int y, int alpha) {
        LostTalesUiInk.drawText(font, text, x, y, LostTalesUiInk.IVORY, alpha);
    }

    /**
     * A string carrying vanilla's section-sign codes, drawn in the
     * palette: each colour code becomes the palette's tone of that
     * colour ({@link #paletteRgb}), the decorations stay, and a reset
     * returns to ivory. What the hover cards for achievements and text
     * components draw with, so a tooltip reads in the same sixteen
     * colours as the line it hangs from.
     */
    static void drawLegacyFormatted(FontRenderer font, String text,
                                    int x, int y, int alpha) {
        if (font == null || text == null) {
            return;
        }
        int cursor = x;
        for (LegacyRun run : legacyRuns(text)) {
            LostTalesUiInk.drawText(font, run.text, cursor, y, run.rgb, alpha);
            cursor += font.getStringWidth(run.text);
        }
    }

    /**
     * The runs a section-sign-coded string is drawn as: the text of each
     * with its decoration codes ahead of it, and the palette colour it
     * is drawn in. A colour code starts a run and clears the
     * decorations, as vanilla does; a reset does both and returns to
     * ivory; a code with nothing after it is not a code.
     */
    static List<LegacyRun> legacyRuns(String text) {
        List<LegacyRun> runs = new ArrayList<LegacyRun>();
        if (text == null || text.length() == 0) {
            return runs;
        }
        int rgb = LostTalesUiInk.IVORY;
        StringBuilder styles = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '\u00a7' || index + 1 >= text.length()) {
                pending.append(character);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            EnumChatFormatting formatting = formattingOf(code);
            index++;
            if (formatting == null) {
                // FontRenderer treats an unknown code as white; the pair
                // is consumed and the run goes on.
                continue;
            }
            // Text before a code is drawn as it stood; the code shapes
            // what follows.
            if (pending.length() > 0) {
                runs.add(new LegacyRun(prefix(styles) + pending, rgb));
                pending.setLength(0);
            }
            if (formatting.isFancyStyling()) {
                styles.append('\u00a7').append(code);
                continue;
            }
            styles.setLength(0);
            rgb = formatting == EnumChatFormatting.RESET ? LostTalesUiInk.IVORY
                    : paletteRgb(formatting);
        }
        if (pending.length() > 0) {
            runs.add(new LegacyRun(prefix(styles) + pending, rgb));
        }
        return runs;
    }

    private static String prefix(StringBuilder styles) {
        return styles.length() == 0 ? "" : styles.toString();
    }

    private static EnumChatFormatting formattingOf(char code) {
        for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
            String written = formatting.toString();
            if (written.length() == 2 && written.charAt(1) == code) {
                return formatting;
            }
        }
        return null;
    }

    /** One run of a legacy-coded string: what is drawn, and in what colour. */
    static final class LegacyRun {
        /** The text with its decoration codes ahead of it, colours gone. */
        final String text;
        final int rgb;

        LegacyRun(String text, int rgb) {
            this.text = text;
            this.rgb = rgb & 0xFFFFFF;
        }
    }

    /**
     * The palette entry a vanilla colour code stands for, so text the
     * chat did not compose itself — an achievement from vanilla or from
     * LOTR, another mod's notice, a player's own {@code &}-codes — reads
     * in the same sixteen colours everything else does. The mapping
     * keeps each code's identity: green stays green, yellow yellow,
     * only in the palette's own tones. Vanilla's white is the chat's
     * ivory, which is what {@link #removeExplicitWhite} already assumes.
     */
    static int paletteRgb(EnumChatFormatting formatting) {
        if (formatting == null) {
            return LostTalesUiInk.IVORY;
        }
        switch (formatting) {
            case BLACK:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.PLUM_BLACK);
            case DARK_BLUE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.INDIGO);
            case DARK_GREEN:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SEA_GREEN);
            case DARK_AQUA:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.TEAL);
            case DARK_RED:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.RUST);
            case DARK_PURPLE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MULBERRY);
            case GOLD:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.APRICOT);
            case GRAY:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.ROSE_GRAY);
            case DARK_GRAY:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MAUVE);
            case BLUE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.STEEL_BLUE);
            case GREEN:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MEADOW_GREEN);
            case AQUA:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SEAFOAM);
            case RED:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SALMON);
            case LIGHT_PURPLE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.ORCHID);
            case YELLOW:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.HONEY);
            default:
                return LostTalesUiInk.IVORY;
        }
    }

    /**
     * Width of one component as the renderer advances past it: the text
     * measured with its style's formatting code, or, for an indent marker,
     * the inset recorded for the current chat state, and the padding of a
     * backdrop it opens or closes ({@link ChatRunBackdrops}). Every walk
     * over a line — drawing, head placement, hit testing, the hover card —
     * must advance by this, so they all ask here.
     */
    static int partWidth(FontRenderer font, IChatComponent part,
                         boolean chatOpen) {
        ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
        if (layout != null) {
            return layout.indent(chatOpen);
        }
        int pads = ChatRunBackdrops.pads(part);
        int declared = ChatInlineIcons.declaredWidth(part);
        if (declared >= 0) {
            return declared + pads;
        }
        return measure(font, part.getChatStyle().getFormattingCode(),
                part.getUnformattedTextForChat(), chatColoursEnabled()) + pads;
    }

    /**
     * The colour a backdropped run's words are drawn in, as the draw
     * picks it: a share's, a mention's, a link's, and an achievement's own
     * colour in the palette's tone; ivory for anything else.
     */
    static int runRgb(IChatComponent part) {
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        if (share != null) {
            return share.textColor;
        }
        ChatMentionMarker.Data mention = ChatMentionMarker.decode(part);
        if (mention != null) {
            return mention.color;
        }
        Integer link = ChatChannelLinkMarker.colorOf(part);
        if (link != null) {
            return link.intValue();
        }
        return part.getChatStyle().getColor() != null
                ? paletteRgb(part.getChatStyle().getColor()) : LostTalesUiInk.IVORY;
    }

    /**
     * Where a row's first drawn run starts, in its text space: past the
     * layout markers it opens with — a continuation row's indent — the
     * gaps — a quote's own indent in an open window — and the runs the
     * chat does not draw. A row drawn small shrinks from here, so it
     * keeps its place under the message.
     */
    static int contentStart(IChatComponent row, boolean chatOpen) {
        int cursor = 0;
        if (row == null) {
            return cursor;
        }
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
            if (layout != null) {
                cursor += layout.indent(chatOpen);
                continue;
            }
            int gap = ChatSpacerMarker.decode(part);
            if (gap >= 0) {
                cursor += gap;
                continue;
            }
            // An empty run with no slot of its own takes no room and
            // draws nothing: the row's root, most often.
            if (part.getUnformattedTextForChat().length() == 0
                    && ChatInlineIcons.declaredWidth(part) < 0) {
                continue;
            }
            return cursor;
        }
        return cursor;
    }

    /**
     * Where a row's ink ends, in its text space: past its last glyph,
     * icon or chip, leaving out trailing spaces and the spacing column
     * the font's advance carries after a run's last glyph. What the
     * closed feed stands a right-aligned or centred row by, so its drawn
     * pixels meet the edge rather than the room after them.
     */
    static int inkEnd(FontRenderer font, IChatComponent row,
                      boolean chatOpen) {
        int cursor = 0;
        int end = 0;
        if (font == null || row == null) {
            return end;
        }
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            int width = partWidth(font, part, chatOpen);
            if (drawsSlot(part)) {
                end = cursor + width;
            } else if (ChatLayoutMarker.decode(part) == null
                    && ChatSpacerMarker.decode(part) < 0) {
                String text = part.getUnformattedTextForChat();
                if (text.trim().length() > 0) {
                    end = cursor + width - trailingSpaceWidth(font, text) - 1;
                }
            }
            cursor += width;
        }
        return Math.max(0, end);
    }

    /**
     * Whether a run draws something across the whole slot it takes — a
     * head or the mark standing for one, an emoji, a shared item's or
     * marker's icon, a bubble, a reaction chip, the time behind a name —
     * rather than glyphs of its own size.
     */
    static boolean drawsSlot(IChatComponent part) {
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        return ChatHeadMarker.headOf(part) != null
                || ChatStampMarker.isMarker(part)
                || ChatReactionMarker.isAddButton(part)
                || ChatReplyMarker.isIconSlot(part)
                || ChatReactionMarker.isMarker(part)
                || ChatEmojiMarker.decode(part) != null
                || ChatChannelLinkMarker.isIconSlot(part)
                || (share != null && share.icon);
    }

    /** Whether the game's chat-links option is on: what gates a link, a command, a share. */
    static boolean chatLinksEnabled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.gameSettings == null
                || minecraft.gameSettings.chatLinks;
    }

    /**
     * Vanilla's "chat colours" option. Off, every formatting code — ours
     * and the sender's — is stripped before measuring and drawing, and the
     * whole line is plain ivory, exactly as vanilla renders colourless
     * chat. Measuring and drawing always agree because both ask here.
     */
    static boolean chatColoursEnabled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.gameSettings == null
                || minecraft.gameSettings.chatColours;
    }

    /** Every section-sign code removed, colour and decoration alike. */
    static String stripCodes(String text) {
        if (text == null || text.indexOf('§') < 0) {
            return text == null ? "" : text;
        }
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return stripped == null ? "" : stripped;
    }

    private static int measure(FontRenderer font, String formatting,
                               String text, boolean colours) {
        return font.getStringWidth(colours ? formatting + text
                : stripCodes(formatting + text));
    }

    private static String removeExplicitWhite(String text) {
        return text.replace("\u00a7f", "").replace("\u00a7F", "");
    }

    private static void drawComponentPass(
            FontRenderer font, IChatComponent line,
            ChatHeadMarker.Data metadata, int x, int y,
            int alpha, boolean shadowPass, boolean chatOpen,
            ChatRowMotion motion, int chatLineId) {
        int cursor = x;
        // A moving row's words, counted as the pass meets them, so each
        // is drawn at its own place in the row's stagger.
        int words = motion == null ? 0 : countWords(line, chatOpen);
        int word = 0;
        // Where the lit run the rule is under was drawn, so the rule
        // stays under its words while they move.
        float ruleShift = 0.0F;
        boolean afterHead = false;
        boolean identitySeen = false;
        boolean colours = chatColoursEnabled();
        // The hovered run is known by the row it is on and its place
        // there, never by identity: a row's iterator hands out copies.
        // Nothing on any other row answers to it.
        IChatComponent hovered = LostTalesChatPresentation.isHoveredLineRow(line)
                ? LostTalesChatPresentation.hoveredComponent() : null;
        // The sender is one thing under the pointer — the opening
        // bracket, the head, the name, the title and the closing
        // bracket — so resting on any of them underlines the whole
        // span, in the name's colour. Whether the pointer is on it is
        // the hover card's answer, measured against each part's own
        // pixels with the closing bracket's trailing gap left out, so
        // the rule, the card and the hand cursor agree to the pixel.
        boolean personHovered =
                LostTalesChatPresentation.isHoveredSenderRow(line);
        boolean identitySpan = false;
        // The rule under whatever is lit: one rectangle per unbroken
        // run of lit parts, in the colour the first of them is drawn
        // in, closed by the first part that is not lit and drawn once
        // its extent is known — so a name reads as one thing with its
        // head, brackets and title, and a link as one line. The rule
        // stands on the row the descenders' shadow takes and is drawn in
        // the content pass alone, with its own shadow under it.
        int ruleStart = -1;
        int ruleEnd = 0;
        int ruleColor = 0;
        int ruleTrailing = 0;
        int index = -1;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            index++;
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
            if (layout != null) {
                // Zero-width layout metadata; an indent marker insets a
                // continuation line under the message body.
                cursor += layout.indent(chatOpen);
                continue;
            }
            if (ChatStampMarker.isMarker(part)
                    || ChatReactionMarker.isAddButton(part)) {
                // The time behind a name and the button a reaction row
                // ends on are the renderer's to draw; here they are room,
                // and past the sender: the name's rule ends before them.
                if (ruleStart >= 0) {
                    drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor,
                            alpha, ruleShift);
                    ruleStart = -1;
                }
                identitySpan = false;
                cursor += ChatInlineIcons.declaredWidth(part);
                continue;
            }
            ChatReactionMarker.Data reaction = ChatReactionMarker.decode(part);
            if (reaction != null) {
                // A reaction chip is never underlined: it lights as a
                // whole under the pointer instead, on the same hit the
                // hand cursor and the click read. Its emoji and count
                // carry their own shadows, so the shadow pass leaves it.
                if (ruleStart >= 0) {
                    drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor,
                            alpha, ruleShift);
                    ruleStart = -1;
                }
                if (!shadowPass) {
                    drawReactionChip(font, reaction, cursor, y, alpha,
                            LostTalesChatPresentation.isHoveredRun(line, index),
                            colours);
                }
                cursor += reaction.width;
                continue;
            }
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            if (ChatSpoilerMarker.isRevealed(part)) {
                // A revealed spoiler is read in the clear: only the
                // style's own obfuscation goes — a sender's typed &k
                // lives in the text and stays whatever it was.
                formatting = formatting.replace("§k", "");
            }
            if (isWebLink(part)) {
                // A link another mod or Forge underlined for good is
                // drawn plain: the hover rule underlines it, as it does
                // every other run that answers to a click.
                formatting = formatting.replace("§n", "");
            }
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                afterHead = true;
            }
            boolean replyIdentity = ChatSenderSpan.isSenderName(part);
            // The span opens on the bracket, the name or the head —
            // an NPC's bracket carries no whisper, so its head opens
            // it — and closes after the run the closing bracket starts.
            if (!identitySpan && (replyIdentity || marker != null
                    || "<".equals(text))) {
                identitySpan = true;
            }
            boolean closesSpan = identitySpan && marker == null
                    && text.startsWith(">");
            boolean underlined = false;
            int underlineColor = LostTalesUiInk.IVORY;
            int width;
            // A run that wears a backdrop stands inside its padding, and
            // the pointer lights the backdrop and turns its words ivory
            // instead of underlining them.
            boolean backdropped = ChatRunBackdrops.kindOf(part)
                    != ChatRunBackdrops.Kind.NONE;
            float backdropLit = backdropped && !shadowPass
                    ? ChatRunBackdrops.litShare(chatLineId, part) : 0.0F;
            cursor += ChatRunBackdrops.padBefore(part);

            ChatEmoji emoji = ChatEmojiMarker.decode(part);
            ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
            // An icon in a moving row is a word of it: it travels on the
            // place its turn in the row gives it.
            float runShift = 0.0F;
            boolean icon = isIconRun(part, emoji, share);
            if (motion != null && icon) {
                runShift = motion.wordX(word++, words);
                GL11.glPushMatrix();
                GL11.glTranslatef(runShift, 0.0F, 0.0F);
            }
            if (emoji != null) {
                width = measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(), emoji,
                            ChatInlineIcons.boxLeft(cursor, width),
                            ChatInlineIcons.boxTop(y, width),
                            ChatInlineIcons.contentSize(width), alpha,
                            shadowPass);
                }
            } else if (share != null && share.icon) {
                // An item's slot declares its width; a marker's is its
                // two spaces.
                int declared = ChatInlineIcons.declaredWidth(part);
                width = declared >= 0 ? declared
                        : measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    drawShareIcon(share, cursor, y, width, alpha,
                            shadowPass);
                }
                // The icon is a piece of its share, before its name:
                // while a share a spoiler hides is lit, the rule runs on
                // under it, so the share keeps one underline.
                underlined = !backdropped && hovered != null
                        && ChatInteractions.answersClick(part,
                                chatLinksEnabled())
                        && sharesInteraction(part, line, index, hovered);
                underlineColor = share.textColor;
            } else if (ChatReplyMarker.isIconSlot(part)) {
                // The bubble a reply's quote opens with, in the quote's
                // own tone, centred in the row as a box of its own, on
                // the row the typing line's and a message link's bubble
                // take. A piece of the quote, it answers the quote's
                // click, but the rule starts after it: a line under a
                // bubble reads as a smudge.
                width = ChatReplyMarker.ICON_SLOT_WIDTH;
                Integer quoteColor = ChatReplyMarker.colorOf(part);
                // A forward opens with the forward arrow, standing where
                // the bubble's middle would.
                LostTalesUiSheet bubble = ChatReplyMarker.isForwardIcon(part)
                        ? LostTalesUiSheet.FORWARD : LostTalesUiSheet.SPEECH_BUBBLE;
                bubble.drawSilhouette(shadowPass ? LostTalesUiInk.SHADOW
                                : !colours || quoteColor == null ? LostTalesUiInk.IVORY
                                        : quoteColor.intValue(),
                        cursor + (LostTalesUiSheet.SPEECH_BUBBLE.getWidth()
                                - bubble.getWidth()) / 2,
                        y + WindowStyle.centredBoxTop(bubble.getHeight()),
                        alpha);
            } else if (ChatChannelLinkMarker.isIconSlot(part)) {
                // The bubble of a link to a message, in the link's own
                // colour, centred in the slot its two spaces reserve. It
                // is a piece of the link and answers the click like the
                // name before it, but the rule stops short of it: a line
                // under a bubble reads as a smudge.
                width = measure(font, formatting, text, colours);
                Integer linkColor = ChatChannelLinkMarker.colorOf(part);
                ChatInlineIcons.drawSheetSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                        ChatInlineIcons.boxLeft(cursor, width),
                        ChatInlineIcons.boxTop(y, width),
                        ChatInlineIcons.contentSize(width),
                        ChatRunBackdrops.wordsRgb(!colours || linkColor == null
                                ? LostTalesUiInk.IVORY : linkColor.intValue(), backdropLit),
                        alpha, shadowPass);
            } else {
                String rendered;
                int color;
                Integer prefixColor = ChatPrefixMarker.decode(part);
                Integer explicitColor = ChatColorMarker.decode(part);
                if (explicitColor == null) {
                    // The chevron a body opens with wears the sender's colour.
                    explicitColor = ChatBodyMarker.decode(part);
                }
                if (explicitColor == null) {
                    ChatMentionMarker.Data mention =
                            ChatMentionMarker.decode(part);
                    explicitColor = mention == null ? null
                            : Integer.valueOf(mention.color);
                }
                if (explicitColor == null) {
                    explicitColor = ChatTitleMarker.colorOf(part);
                }
                if (explicitColor == null) {
                    explicitColor = ChatReplyMarker.colorOf(part);
                }
                if (explicitColor == null) {
                    explicitColor = ChatChannelLinkMarker.colorOf(part);
                }
                boolean identityBracket = "<".equals(text)
                        || (identitySeen && text.startsWith(">"));
                // The colour the glyphs are drawn in; the rule under a
                // lit run takes the same, read off the inline codes
                // where the text carries its own.
                int glyphColor;
                if (!colours) {
                    rendered = stripCodes(formatting + text);
                    color = shadowPass ? LostTalesUiInk.SHADOW : LostTalesUiInk.IVORY;
                    glyphColor = LostTalesUiInk.IVORY;
                } else if (shadowPass) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = LostTalesUiInk.SHADOW;
                    glyphColor = LostTalesUiInk.SHADOW;
                } else if (share != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = share.textColor;
                    glyphColor = color;
                } else if (prefixColor != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = prefixColor.intValue();
                    glyphColor = color;
                } else if (explicitColor != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = explicitColor.intValue();
                    glyphColor = color;
                } else if (metadata != null && identityBracket) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.nameColor;
                    glyphColor = color;
                } else if (metadata != null && replyIdentity) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.nameColor;
                    glyphColor = color;
                } else if (metadata != null && afterHead
                        && !identitySeen && marker == null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.titleColor;
                    glyphColor = color;
                } else if (part.getChatStyle().getColor() != null) {
                    // A line from vanilla, LOTR or any other mod says what
                    // colour it wants in vanilla's sixteen; it is drawn in
                    // the palette's nearest, so an achievement is still
                    // green (or yellow, or purple) but in the chat's own
                    // greens and yellows rather than beside them.
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = paletteRgb(part.getChatStyle().getColor());
                    glyphColor = color;
                } else {
                    rendered = removeExplicitWhite(formatting + text);
                    color = LostTalesUiInk.IVORY;
                    glyphColor = lastInlineColor(rendered, LostTalesUiInk.IVORY);
                }
                if (backdropped && !shadowPass) {
                    color = ChatRunBackdrops.wordsRgb(color, backdropLit);
                }
                // Asked again for every run: an inline glyph between two of
                // them is drawn by code of its own, and whatever that leaves
                // behind must not decide what the next run's shadow looks
                // like.
                LostTalesUiInk.beginContent();
                if (motion == null) {
                    font.drawString(rendered, cursor, y, LostTalesUiInk.argb(color, alpha));
                } else if (ChatBodyMarker.isMarker(part)) {
                    // The chevron steps on its own, lit toward a lighter
                    // shade of the speaker's colour; its shadow stays
                    // the shadow.
                    runShift = motion.chevronX();
                    int lit = shadowPass ? color : LostTalesUiInk.blend(color,
                            lighterShadeOf(color), motion.brighten());
                    GL11.glPushMatrix();
                    GL11.glTranslatef(runShift, 0.0F, 0.0F);
                    font.drawString(rendered, cursor, y, LostTalesUiInk.argb(lit, alpha));
                    GL11.glPopMatrix();
                } else {
                    runShift = motion.wordX(word, words);
                    word = drawWords(font, rendered, cursor, y,
                            LostTalesUiInk.argb(color, alpha), motion, word, words);
                }
                int declared = ChatInlineIcons.declaredWidth(part);
                width = declared >= 0 ? declared
                        : measure(font, formatting, text, colours);
                if (backdropped) {
                    // Its backdrop lights instead.
                    underlined = false;
                } else if (personHovered && identitySpan) {
                    // Every part of the sender, the head's slot and the
                    // title and the spacers between included, under one
                    // rule in the name's colour.
                    underlined = true;
                    underlineColor = !colours ? LostTalesUiInk.IVORY
                            : marker != null ? marker.nameColor
                            : metadata != null ? metadata.nameColor
                            : glyphColor;
                } else if (marker == null) {
                    // A run that answers to a click is underlined while
                    // the pointer rests on it — or on a run acting with
                    // it, so a reply's quote and the pieces of one link
                    // light together — in its own colour, on the row the
                    // font's own underline takes, so it reads as usable
                    // before it is used. A run that only carries a card
                    // stays plain: the rule promises a click. The head
                    // slot a quote wears is a run of the quote and lights
                    // with it, so the rule runs under the quote whole.
                    underlined = hovered != null
                            && (rendered.trim().length() > 0
                                    || ChatReplyMarker.headOf(part) != null)
                            && ChatInteractions.answersClick(part,
                                    chatLinksEnabled())
                            && sharesInteraction(part, line, index, hovered);
                    underlineColor = glyphColor;
                }
            }

            if (motion != null && icon) {
                GL11.glPopMatrix();
            }

            // A spacer holds no ink, so it neither starts, lengthens nor
            // ends a rule: the rule runs on over one only to the next lit
            // run of its element, and ends where the last lit run did
            // when none follows — the gap before the time behind a name
            // stays bare.
            boolean spacer = ChatSpacerMarker.decode(part) >= 0;
            if (underlined && width > 0 && !shadowPass && !spacer) {
                if (ruleStart < 0) {
                    ruleStart = cursor;
                    ruleColor = underlineColor;
                    ruleShift = runShift;
                }
                ruleEnd = cursor + width;
                // The last run's trailing spaces belong to the gap
                // after it, not to what is lit.
                ruleTrailing = ChatInlineIcons.declaredWidth(part) >= 0 ? 0
                        : trailingSpaceWidth(font, text);
            } else if (ruleStart >= 0 && !spacer) {
                drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor,
                        alpha, ruleShift);
                ruleStart = -1;
            }
            cursor += width + ChatRunBackdrops.padAfter(part);
            identitySeen |= replyIdentity;
            if (closesSpan) {
                identitySpan = false;
            }
        }
        if (ruleStart >= 0) {
            drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor, alpha,
                    ruleShift);
        }
    }

    /** Whether a run is drawn as an icon rather than as glyphs. */
    private static boolean isIconRun(IChatComponent part, ChatEmoji emoji,
                                     ChatShowcaseMarker.Data share) {
        return emoji != null || share != null && share.icon
                || ChatReplyMarker.isIconSlot(part)
                || ChatChannelLinkMarker.isIconSlot(part);
    }

    /**
     * The words of a row in the order the pass meets them: every piece of
     * a run's text with ink in it, and every icon. The chevron and the
     * markers are none.
     */
    static int countWords(IChatComponent line, boolean chatOpen) {
        int count = 0;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)
                    || ChatLayoutMarker.decode(part) != null
                    || ChatStampMarker.isMarker(part)
                    || ChatReactionMarker.isAddButton(part)
                    || ChatReactionMarker.decode(part) != null) {
                continue;
            }
            count += wordsIn(part);
        }
        return count;
    }

    /**
     * How many of a moving row's words one run is: one for an icon, none
     * for the chevron, and one for each piece of its text with ink in it.
     */
    static int wordsIn(IChatComponent part) {
        if (isIconRun(part, ChatEmojiMarker.decode(part),
                ChatShowcaseMarker.decode(part))) {
            return 1;
        }
        if (ChatBodyMarker.isMarker(part)) {
            return 0;
        }
        String text = part.getUnformattedTextForChat();
        int count = 0;
        for (int start = 0; start < text.length();) {
            int end = pieceEnd(text, start);
            if (hasInk(text, start, end)) {
                count++;
            }
            start = end;
        }
        return count;
    }

    /**
     * A run's text drawn a word at a time, each where the row's motion
     * has it, every piece carrying the formatting in force where it
     * starts. Answers the index of the next word.
     */
    private static int drawWords(FontRenderer font, String rendered, int x,
                                 int y, int argb, ChatRowMotion motion,
                                 int word, int words) {
        int next = word;
        for (int start = 0; start < rendered.length();) {
            int end = pieceEnd(rendered, start);
            String before = rendered.substring(0, start);
            GL11.glPushMatrix();
            GL11.glTranslatef(motion.wordX(next, words), 0.0F, 0.0F);
            font.drawString(activeCodes(before)
                            + rendered.substring(start, end),
                    x + font.getStringWidth(before), y, argb);
            GL11.glPopMatrix();
            if (hasInk(rendered, start, end)) {
                next++;
            }
            start = end;
        }
        return next;
    }

    /**
     * Where the piece starting at {@code start} ends: past the spaces
     * before a word, the word with its formatting codes, and the spaces
     * after it, so the next piece begins on a word.
     */
    private static int pieceEnd(String text, int start) {
        int at = start;
        while (at < text.length() && text.charAt(at) == ' ') {
            at++;
        }
        while (at < text.length() && text.charAt(at) != ' ') {
            at += text.charAt(at) == '\u00a7' && at + 1 < text.length() ? 2 : 1;
        }
        while (at < text.length() && text.charAt(at) == ' ') {
            at++;
        }
        return at;
    }

    /** Whether text between the two places holds anything but spaces and codes. */
    private static boolean hasInk(String text, int start, int end) {
        for (int at = start; at < end; at++) {
            char glyph = text.charAt(at);
            if (glyph == '\u00a7') {
                at++;
            } else if (glyph != ' ') {
                return true;
            }
        }
        return false;
    }

    /**
     * The formatting codes in force at the end of {@code text}, as the
     * font reads them: a colour clears the styles before it, a reset
     * clears everything.
     */
    static String activeCodes(String text) {
        String colour = "";
        StringBuilder styles = new StringBuilder();
        for (int at = 0; at + 1 < text.length(); at++) {
            if (text.charAt(at) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(at + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                colour = "\u00a7" + code;
                styles.setLength(0);
            } else if ("klmno".indexOf(code) >= 0) {
                styles.append('\u00a7').append(code);
            } else if (code == 'r') {
                colour = "";
                styles.setLength(0);
            }
            at++;
        }
        return colour + styles;
    }

    /**
     * A shade lighter than {@code rgb}: the next step of its own ramp when
     * it is a palette colour, else partway toward ivory.
     */
    static int lighterShadeOf(int rgb) {
        int wanted = rgb & 0xFFFFFF;
        for (String name : LostTalesColors.paletteNames()) {
            if (LostTalesColors.rgb(LostTalesColors.paletteColor(name, 0))
                    == wanted) {
                String lighter = LostTalesColors.lighterStep(name);
                if (lighter != null) {
                    return LostTalesColors.rgb(
                            LostTalesColors.paletteColor(lighter, 0));
                }
                break;
            }
        }
        return LostTalesUiInk.blend(wanted, LostTalesUiInk.IVORY, LIGHTER_SHADE_SHARE);
    }

    /** How far toward ivory a colour off the palette is taken for its lighter shade. */
    private static final float LIGHTER_SHADE_SHARE = 0.45F;

    /**
     * One reaction chip: a framed button holding the emoji at its native
     * size and the count, the emoji's box the frame's inset in from every
     * edge and the count's capitals half a pixel above the box's middle,
     * the text's top at {@code y}. A chip the reader is in stands lit, the
     * way a chosen main-menu button does, its count in the accent; the
     * pointer lights any other on the controls' crossfade. Drawn half a
     * step in front of the hole the history leaves for it
     * ({@link LostTalesChatOverlayRenderer#CHIP_DEPTH}). An emoji the
     * registry lacks is drawn as {@link #drawUnknownEmoji}.
     */
    private static void drawReactionChip(FontRenderer font,
                                         ChatReactionMarker.Data chip,
                                         int x, int y, int alpha,
                                         boolean hovered, boolean colours) {
        int left = x;
        int top = y - ChatReactionMarker.TEXT_DROP;
        int accent = LostTalesColors.rgb(LostTalesColors.HONEY);
        float lit = chip.mine ? 1.0F
                : LostTalesChatPresentation.chipHoverFade(chip, hovered);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F,
                    LostTalesChatOverlayRenderer.CHIP_DEPTH);
            LostTalesUiFramedButton.drawSurface(left, top, chip.width,
                    ChatReactionMarker.HEIGHT, lit,
                    Math.round(alpha * WindowStyle.INSET_ALPHA / 255.0F));
            LostTalesUiInk.beginContent();
            int emojiTop = chipEmojiTop(top);
            if (chip.emoji != null) {
                ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(),
                        chip.emoji, left + ChatReactionMarker.PAD, emojiTop,
                        ChatInlineIcons.CONTENT_SIZE, alpha);
            } else {
                drawUnknownEmoji(font, left + ChatReactionMarker.PAD,
                        emojiTop, y, alpha);
            }
            LostTalesUiInk.beginContent();
            LostTalesUiInk.drawText(font, chip.countText(), left + ChatReactionMarker.PAD
                    + ChatReactionMarker.ICON + ChatReactionMarker.GAP, y,
                    chip.mine && colours ? accent : LostTalesUiInk.IVORY, alpha);
            LostTalesUiFramedButton.drawInk(left, top, chip.width,
                    ChatReactionMarker.HEIGHT, lit, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The button a reaction row ends on ({@link ChatReactionMarker#addButton}),
     * at {@code x} on a reaction row whose text stands at zero: a framed
     * button as tall as a chip, holding the input bar's emoji button —
     * what the toolbar's React holds — crossing to its lit artwork and
     * lit as far as {@code lit}.
     */
    static void drawReactionAddButton(int x, int alpha, float lit) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int top = -ChatReactionMarker.TEXT_DROP;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F,
                    LostTalesChatOverlayRenderer.CHIP_DEPTH);
            LostTalesUiFramedButton.drawSurface(x, top,
                    ChatReactionMarker.ADD_WIDTH, ChatReactionMarker.HEIGHT,
                    lit, Math.round(alpha * WindowStyle.INSET_ALPHA / 255.0F));
            LostTalesUiInk.beginContent();
            LostTalesUiSheet.drawPairWithShadow(LostTalesUiSheet.EMOJI,
                    LostTalesUiSheet.EMOJI_HOVER, lit,
                    x + ChatReactionMarker.PAD, chipEmojiTop(top), alpha);
            LostTalesUiFramedButton.drawInk(x, top,
                    ChatReactionMarker.ADD_WIDTH, ChatReactionMarker.HEIGHT,
                    lit, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Where a reaction row's add button stands in its text space — its
     * left edge and its width — found by the walk that draws the row, or
     * null for a row without one.
     */
    static int[] addButtonBox(FontRenderer font, IChatComponent row,
                              boolean chatOpen) {
        if (font == null || row == null) {
            return null;
        }
        int cursor = 0;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            if (ChatReactionMarker.isAddButton(part)) {
                return new int[] {cursor, ChatReactionMarker.ADD_WIDTH};
            }
            cursor += partWidth(font, part, chatOpen);
        }
        return null;
    }

    /**
     * Where a reaction chip's emoji starts, for a chip starting at
     * {@code chipTop}: the frame's inset below the chip's top edge, and
     * the same inset above its foot, so it stands in the chip's middle.
     */
    static int chipEmojiTop(int chipTop) {
        return chipTop
                + (ChatReactionMarker.HEIGHT - ChatReactionMarker.ICON) / 2;
    }

    /**
     * Where a reaction row's chips stand in its text space, two numbers
     * to a chip — its left edge and its width — found by the walk that
     * draws the row, so the holes cut for them fit the chips exactly.
     */
    static int[] chipBoxes(FontRenderer font, IChatComponent row,
                           boolean chatOpen) {
        if (font == null || row == null) {
            return new int[0];
        }
        int[] boxes = new int[8];
        int count = 0;
        int cursor = 0;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatReactionMarker.Data reaction = ChatReactionMarker.decode(part);
            if (reaction != null) {
                if (count + 2 > boxes.length) {
                    boxes = java.util.Arrays.copyOf(boxes, boxes.length * 2);
                }
                boxes[count++] = cursor;
                boxes[count++] = reaction.width;
                cursor += reaction.width;
                continue;
            }
            cursor += partWidth(font, part, chatOpen);
        }
        return java.util.Arrays.copyOf(boxes, count);
    }

    /** The glyph that stands in for an emoji the game has no sprite for. */
    private static final String UNKNOWN_EMOJI_GLYPH = "?";

    /**
     * An emoji the game has no sprite for, in the emoji's own cell from
     * {@code top}: a tile the size of a sprite with its corners cut, and
     * a question mark in ivory on it, standing on the row's text line at
     * {@code y}, whole pixels throughout. The tile keeps the chip's shape
     * and makes the mark read as an icon.
     */
    private static void drawUnknownEmoji(FontRenderer font, int x, int top,
                                         int y, int alpha) {
        int size = ChatReactionMarker.ICON;
        int tile = LostTalesUiInk.argb(LostTalesColors.rgb(LostTalesColors.PLUM_GRAY), alpha);
        Gui.drawRect(x + 1, top, x + size - 1, top + size, tile);
        Gui.drawRect(x, top + 1, x + 1, top + size - 1, tile);
        Gui.drawRect(x + size - 1, top + 1, x + size, top + size - 1, tile);
        if (font == null) {
            return;
        }
        // The advance ends in the font's one-pixel gap; the ink is the rest.
        int ink = Math.max(0, font.getStringWidth(UNKNOWN_EMOJI_GLYPH) - 1);
        LostTalesUiInk.drawText(font, UNKNOWN_EMOJI_GLYPH, x + (size - ink) / 2, y, LostTalesUiInk.IVORY,
                alpha);
    }

    /**
     * The row under a run's glyphs the font draws its own underline on,
     * counted from the text's top: the row the descenders' shadow falls
     * on, which in a message row leaves the row's last pixel for the
     * rule's own shadow.
     */
    static final int UNDERLINE_ROW = 8;

    /**
     * One rule from {@code start} to {@code end}, the last pixel left
     * to the glyph's own trailing gap so the rule ends with the glyphs,
     * over the chat's one shadow. The rule is drawn once its extent is
     * known, after the glyphs beside it, and its shadow with it: the row
     * the shadow takes is under every glyph and every glyph's shadow, so
     * nothing of the line lies there for it to be laid over.
     */
    private static void drawRule(int start, int end, int y, int color,
                                 int alpha, float shift) {
        if (shift == 0.0F) {
            drawRule(start, end, y, color, alpha);
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(shift, 0.0F, 0.0F);
        drawRule(start, end, y, color, alpha);
        GL11.glPopMatrix();
    }

    private static void drawRule(int start, int end, int y, int color,
                                 int alpha) {
        if (end - 1 <= start) {
            return;
        }
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            LostTalesUiInk.fillRect(start + LostTalesUiInk.SHADOW_OFFSET,
                    y + UNDERLINE_ROW + LostTalesUiInk.SHADOW_OFFSET,
                    end - 1 + LostTalesUiInk.SHADOW_OFFSET,
                    y + UNDERLINE_ROW + 1 + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        LostTalesUiInk.fillRect(start, y + UNDERLINE_ROW,
                end - 1, y + UNDERLINE_ROW + 1, LostTalesUiInk.argb(color, alpha));
    }

    /** Whether the run opens a web address on a click. */
    private static boolean isWebLink(IChatComponent part) {
        ClickEvent click = part.getChatStyle() == null ? null
                : part.getChatStyle().getChatClickEvent();
        return click != null && click.getAction() == ClickEvent.Action.OPEN_URL;
    }

    /** The width of the spaces a run ends with. */
    static int trailingSpaceWidth(FontRenderer font, String text) {
        int spaces = 0;
        for (int at = text.length() - 1; at >= 0 && text.charAt(at) == ' ';
             at--) {
            spaces++;
        }
        return spaces * font.getCharWidth(' ');
    }

    /**
     * The colour the last inline colour code of a run leaves its glyphs
     * in, in the palette's tone, or {@code fallback} when the run
     * carries none.
     */
    private static int lastInlineColor(String rendered, int fallback) {
        int color = fallback;
        for (int at = 0; at + 1 < rendered.length(); at++) {
            if (rendered.charAt(at) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(rendered.charAt(at + 1));
            for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
                if (formatting.isColor()
                        && formatting.getFormattingCode() == code) {
                    color = paletteRgb(formatting);
                    break;
                }
            }
            at++;
        }
        return color;
    }

    /**
     * Whether the run at {@code index} of {@code line} acts with the
     * hovered one, which is on the same row: it is the hovered run, or a
     * piece of the same element ({@link ChatInteractions#sameElement}) —
     * a reply quote, a link, a spoiler, the brackets, icon and name of a
     * share or an achievement — so the whole of it is underlined as
     * one. Nothing on another row ever acts with it.
     */
    private static boolean sharesInteraction(IChatComponent part,
                                             IChatComponent line, int index,
                                             IChatComponent hovered) {
        if (part == null || hovered == null) {
            return false;
        }
        return LostTalesChatPresentation.isHoveredRun(line, index)
                || ChatInteractions.sameElement(part, hovered);
    }

    private static void drawShareIcon(ChatShowcaseMarker.Data share,
                                      int cursor, int y, int slotWidth,
                                      int alpha, boolean shadowPass) {
        Minecraft minecraft = Minecraft.getMinecraft();
        float boxX = ChatInlineIcons.boxLeft(cursor, slotWidth);
        float boxY = ChatInlineIcons.boxTop(y, slotWidth);
        float size = ChatInlineIcons.contentSize(slotWidth);
        if (share.kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(share.showcaseId);
            if (stack != null) {
                LostTalesUiItemIcon.drawFitted(minecraft, stack, boxX, boxY, size,
                        alpha, shadowPass);
            }
            return;
        }
        if (share.kind == ChatShareKind.QUEST) {
            if (ClientChatShowcaseStore.getQuest(share.showcaseId) != null) {
                LostTalesUiSheet.QUEST.drawWithShadow(boxX, boxY, alpha);
            }
            return;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(share.showcaseId);
        if (marker != null) {
            ChatInlineIcons.drawMarker(minecraft, marker.iconName,
                    ChatInlineIcons.markerRgb(marker.colorName),
                    boxX, boxY, size, alpha, shadowPass);
        }
    }

    /** Removes colour/reset codes but keeps bold/italic decorations and width. */
    static String styleCodesOnly(String formatting) {
        if (formatting == null || formatting.length() == 0) {
            return "";
        }
        StringBuilder kept = new StringBuilder(formatting.length());
        for (int index = 0; index + 1 < formatting.length(); index++) {
            if (formatting.charAt(index) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(
                    formatting.charAt(index + 1));
            if (code >= 'k' && code <= 'o') {
                kept.append('\u00a7').append(code);
            }
            index++;
        }
        return kept.toString();
    }

    /**
     * Removes embedded legacy colours and resets while retaining decorative
     * formatting and visible text. LOTR title display names may contain their
     * own colour code; allowing it through would override the RGB supplied to
     * FontRenderer for the remainder of that component, including the custom
     * shadow pass.
     */
    static String removeColorCodes(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        StringBuilder kept = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\u00a7' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code >= 'k' && code <= 'o') {
                    kept.append(character).append(code);
                }
                // FontRenderer treats an unknown section-sign code as white,
                // so every non-decoration pair must be consumed as well.
                index++;
                continue;
            }
            kept.append(character);
        }
        return kept.toString();
    }
}
