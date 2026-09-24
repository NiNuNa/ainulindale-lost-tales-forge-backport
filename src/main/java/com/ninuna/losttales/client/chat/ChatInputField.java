package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * The chat's text fields — the input, and the pickers' search — drawn
 * the way the rest of the chat is drawn.
 *
 * <p>Vanilla's {@code GuiTextField} draws its text with
 * {@code drawStringWithShadow}, whose shadow is a quarter of the text's
 * own colour at full opacity — a different shadow from every other glyph
 * in the chat, right beside them on the same bar. Everything else about
 * the field is vanilla's: this replaces the drawing alone, using the
 * shared {@link LostTalesChatVisualStyle} treatment for the text, the
 * caret and the selection.</p>
 *
 * <p>A mention being typed is drawn in the colour it will have once it
 * is sent — a role's own, a player's role, or the shared mention honey —
 * so the bar reads as the line it is about to become. The rest of the
 * text stays ivory.</p>
 *
 * <p>The chat's markup is previewed as it is typed: the text between a
 * pair of marks wears the marks' style — bold, italic, underlined,
 * struck — code is the chat's inline code, spoiler text a subdued
 * colour, and the marker characters themselves stay on the bar, dimmed,
 * at their own width, so nothing the caret can stand on is hidden from
 * it. The same scan that lays out the sent line
 * ({@link ChatMarkdown#layout}) decides which characters are marks, so
 * the preview and the line agree. A command is inline code as it is
 * typed, whole, as the chat shows a command everywhere; only the words
 * a whisper verb sends are previewed, as the whisper they become
 * ({@link ChatInputStyles#layout}). A bold glyph is a pixel wider than
 * its plain self, and the display model below measures it so, which
 * keeps the caret, the selection and the scroll on the glyphs actually
 * drawn.</p>
 *
 * <p>A share token whose item or marker the client can already resolve
 * — {@code [i:Stone Sword]}, {@code [m:Northgate]} — is shown as the
 * preview it will be in chat: the bracket, the icon, the real name, in
 * the rarity's or marker's colour. A complete emoji shortcode —
 * {@code :smile:} — is shown the same way, as the sprite it will be.
 * The raw text is untouched — it is what goes on the wire — but while
 * it is edited a resolved token behaves as one character: the caret can
 * stand on either side of it and never inside, Left and Right step
 * across it whole, Backspace behind it and Delete before it remove all
 * of it, a click lands on its nearer edge, and a selection takes it
 * whole or not at all. All of that is one rule —
 * {@link #snapOutsideTokens} — applied where every caret and selection
 * movement already converges ({@link #setCursorPosition},
 * {@link #setSelectionPos}), so no key needs handling of its own; an
 * incomplete or unresolvable token is plain text and edits as such.</p>
 *
 * <p>A search field shows what is typed as it is ({@link #plainText}):
 * no mention colours, no previews and no markup.</p>
 *
 * <p>The caret is the mod's one caret ({@link LostTalesUiCaret}), lit
 * from the last key or caret move, and only while the field holds the
 * keys.</p>
 *
 * <p>How far vanilla has scrolled the text has no accessor, so it is
 * read reflectively by both its names, verified to be the int it is.
 * Without it the field falls back to vanilla's own drawing rather than
 * showing the wrong text.</p>
 */
final class ChatInputField extends GuiTextField {
    private static final Field LINE_SCROLL_OFFSET =
            resolve("lineScrollOffset", "field_146225_q");
    private static boolean fallbackLogged;

    private final FontRenderer font;
    private final int fieldHeight;
    /** The raw text the previews below were resolved for. */
    private String previewedText;
    private List<TokenPreview> previews = Collections.emptyList();
    /** The raw text the styles below were laid out for. */
    private String styledText;
    /** The text {@link #links} was found in. */
    private String linkedText;
    private Links links = Links.NONE;
    /** Every character's style, or null for text with nothing to style. */
    private int[] styles;
    /** Whether what is typed is shown as it is, as a search field's is. */
    private boolean plainText;
    /** When the field last took a key or moved its caret: the caret's blink starts there. */
    private long caretNanos = System.nanoTime();

    ChatInputField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.font = font;
        this.fieldHeight = height;
    }

    /**
     * Shows what is typed as it is — no mention colours, no previews, no
     * markup — as a search field's text, which is looked up rather than
     * sent, should be.
     */
    ChatInputField plainText() {
        this.plainText = true;
        return this;
    }

    /** Whether the field can be drawn in the chat's own style. */
    static boolean isStyled() {
        return LINE_SCROLL_OFFSET != null;
    }

    /** Whether the caret shows this frame: the field holds the keys and the blink is lit. */
    private boolean caretLit() {
        return isFocused() && LostTalesUiCaret.isLit(this.caretNanos,
                System.nanoTime());
    }

    @Override
    public void drawTextBox() {
        if (!getVisible()) {
            return;
        }
        if (!isStyled()) {
            logFallbackOnce();
            super.drawTextBox();
            return;
        }
        int scrollOffset;
        try {
            scrollOffset = LINE_SCROLL_OFFSET.getInt(this);
        } catch (IllegalAccessException unreadable) {
            logFallbackOnce();
            super.drawTextBox();
            return;
        }
        String text = getText();
        if (scrollOffset < 0 || scrollOffset > text.length()) {
            // Vanilla keeps this in range; anything else is not ours to
            // draw from.
            super.drawTextBox();
            return;
        }
        List<TokenPreview> resolved = previewsFor(text);
        if (!resolved.isEmpty() || stylesFor(text) != null
                || linksFor(text).backdrops) {
            // Vanilla scrolls by raw character widths, but a token is
            // drawn as one narrow element — ":creeper:" measures nine
            // characters and draws ten pixels — and a bold glyph a pixel
            // wider than its plain self, so vanilla scrolls too early or
            // too late and can rest inside a shortcode. The offset is
            // recomputed from the drawn widths every frame and written
            // back, so the field scrolls by what is actually on it and
            // vanilla continues from the corrected position.
            int corrected = correctedScrollOffset(text, resolved,
                    scrollOffset);
            if (corrected != scrollOffset) {
                scrollOffset = corrected;
                try {
                    LINE_SCROLL_OFFSET.setInt(this, corrected);
                } catch (IllegalAccessException ignored) {
                    // The drawn frame still uses the corrected value.
                }
            }
            drawWithPreviews(text, resolved, scrollOffset);
            return;
        }
        String visible = this.font.trimStringToWidth(
                text.substring(scrollOffset), getWidth());
        int caret = getCursorPosition() - scrollOffset;
        int selection = Math.min(getSelectionEnd() - scrollOffset,
                visible.length());
        boolean caretInside = caret >= 0 && caret <= visible.length();
        boolean caretVisible = caretLit() && caretInside;
        int left = this.xPosition;
        int top = this.yPosition;
        // The whole visible run is coloured at once, so a mention split
        // by the caret keeps one colour across the break.
        int[] colors = colorsOf(text, scrollOffset,
                scrollOffset + visible.length());
        int headEnd = caretInside ? caret : visible.length();
        int cursorX = left + this.font.getStringWidth(
                visible.substring(0, headEnd));
        // The caret stands on the boundary between the two runs; the
        // runs themselves are never shifted for it, so the text stays
        // still as the caret walks through it.
        int caretX = cursorX;
        if (!caretInside) {
            caretX = caret > 0 ? left + getWidth() : left;
        }
        if (caretVisible) {
            drawCaretShadow(caretX, top);
        }
        if (visible.length() > 0) {
            drawRuns(visible, colors, 0, headEnd, left, top);
        }
        if (visible.length() > 0 && caretInside && caret < visible.length()) {
            drawRuns(visible, colors, caret, visible.length(), cursorX, top);
        }
        if (caretVisible) {
            drawCaretBar(caretX, top);
        }
        if (selection != caret && caretInside) {
            int selectionX = left + this.font.getStringWidth(
                    visible.substring(0, Math.max(0, selection)));
            drawSelection(caretX, selectionBandTop(top), selectionX - 1,
                    selectionBandBottom(top));
        }
    }

    /**
     * Height of the band the caret and the selection wash stand on: the
     * caret's ten rows, the typing well's middle ten, a clear row short
     * of the well at both ends. It is the well's own middle, not the
     * capitals': a bar as tall as an emoji, centred on them as an emoji's
     * box is, would touch the well's top.
     */
    private static final int CONTENT_HEIGHT = LostTalesUiCaret.HEIGHT;

    /** Top of that band for text drawn at {@code textTop} in the well. */
    static int caretTop(int textTop) {
        return textTop - LostTalesChatOverlayRenderer.ROW_TEXT_TOP
                + (LostTalesChatOverlayRenderer.LINE_HEIGHT
                        - CONTENT_HEIGHT) / 2;
    }

    /**
     * The wash spans the caret's band, so it sits centred in the well on
     * what is selected instead of hanging low on the glyphs alone.
     */
    private static int selectionBandTop(int textTop) {
        return caretTop(textTop);
    }

    private static int selectionBandBottom(int textTop) {
        return caretTop(textTop) + CONTENT_HEIGHT;
    }

    /**
     * The caret where no glyph stands after it: at the end of what is
     * typed, or in an empty field. A caret inside the text lays its
     * shadow before the text and its bar after it instead
     * ({@link #drawCaretShadow}, {@link #drawCaretBar}).
     */
    static void drawCaret(int x, int textTop) {
        drawCaret(x, textTop, 0xFF);
    }

    /** As above at {@code alpha} (0-255): a field in a window fading in or out. */
    static void drawCaret(int x, int textTop, int alpha) {
        LostTalesUiCaret.draw(x, caretTop(textTop), CONTENT_HEIGHT, alpha);
    }

    /**
     * The caret: a one-pixel ivory bar a clear row short of the well at
     * both ends, wherever it stands. After the last character vanilla
     * draws an underscore instead, which hangs past the field's end and
     * out of the well; the bar keeps to the field.
     */
    private static void drawCaretBar(int x, int textTop) {
        LostTalesUiCaret.drawBar(x, caretTop(textTop), CONTENT_HEIGHT, 0xFF);
    }

    /**
     * The caret's shadow, as every word beside it casts one. It lands on
     * the column of the glyph the caret stands before, so it goes down
     * before the text, which draws over it.
     */
    private static void drawCaretShadow(int x, int textTop) {
        LostTalesUiCaret.drawShadow(x, caretTop(textTop), CONTENT_HEIGHT,
                0xFF);
    }

    /**
     * The colour of every character of {@code [from, to)} of the text:
     * ivory, except where an {@code @name} reaches somebody, which wears
     * that somebody's colour, and where a {@code #channel} names a
     * channel, which wears the channel's.
     */
    private int[] colorsOf(String text, int from, int to) {
        Links found = linksFor(text);
        int[] colors = new int[Math.max(0, to - from)];
        for (int index = 0; index < colors.length; index++) {
            int color = found.colorAt(from + index);
            colors[index] = color >= 0 ? color : LostTalesChatVisualStyle.IVORY;
        }
        return colors;
    }

    /**
     * The links of the text as the sent line will show them: every
     * {@code @name} that reaches somebody, and every {@code #channel} or
     * {@code #channel/id} that names a channel — each character's colour,
     * and the padding of the backdrop each wears ({@link ChatRunBackdrops})
     * at either end of it. Nothing inside code is a link, a command's
     * words are its arguments rather than a line, and a link a spoiler
     * hides wears no backdrop. Found again only when the text changes.
     */
    private static final class Links {
        static final Links NONE = new Links(new int[0], new int[0],
                new int[0], new boolean[0], false);

        /** Each character's colour; -1 outside a link. */
        final int[] colors;
        /** The padding before each character: a backdrop's, where one opens. */
        final int[] before;
        /** The padding after each character: a backdrop's, where one closes. */
        final int[] after;
        /** Whether a character is of a player's mention, whose backdrop is the ping's own. */
        final boolean[] players;
        /** Whether any link wears a backdrop. */
        final boolean backdrops;

        Links(int[] colors, int[] before, int[] after, boolean[] players,
              boolean backdrops) {
            this.colors = colors;
            this.before = before;
            this.after = after;
            this.players = players;
            this.backdrops = backdrops;
        }

        int colorAt(int index) {
            return index >= 0 && index < this.colors.length ? this.colors[index] : -1;
        }

        int beforeAt(int index) {
            return index >= 0 && index < this.before.length ? this.before[index] : 0;
        }

        int afterAt(int index) {
            return index >= 0 && index < this.after.length ? this.after[index] : 0;
        }
    }

    private Links linksFor(String text) {
        if (this.plainText || text.length() == 0) {
            return Links.NONE;
        }
        if (text.equals(this.linkedText)) {
            return this.links;
        }
        this.linkedText = text;
        this.links = findLinks(text, stylesFor(text));
        return this.links;
    }

    private static Links findLinks(String text, int[] styles) {
        int[] colors = new int[text.length()];
        java.util.Arrays.fill(colors, -1);
        int[] before = new int[text.length()];
        int[] after = new int[text.length()];
        boolean[] players = new boolean[text.length()];
        boolean backdrops = false;
        int cursor = 0;
        while (cursor < text.length()) {
            int at = text.indexOf('@', cursor);
            if (at < 0) {
                break;
            }
            int end = at + 1;
            while (end < text.length() && ChatMentionColors
                    .isMentionCharacter(text.charAt(end))) {
                end++;
            }
            boolean opensWord = at == 0 || !ChatMentionColors
                    .isMentionCharacter(text.charAt(at - 1));
            String name = text.substring(at + 1, end);
            int color = opensWord && end > at + 1
                    ? ChatMentionColors.colorOf(name) : -1;
            int style = ChatInputStyles.styleAt(styles, at);
            if (color >= 0 && (style & ChatMarkdown.Span.CODE) == 0) {
                boolean player = ChatMentionColors.roleFor(name) == null;
                for (int index = at; index < end; index++) {
                    colors[index] = color;
                    players[index] = player;
                }
                if ((style & ChatMarkdown.Span.SPOILER) == 0) {
                    before[at] = ChatRunBackdrops.PAD;
                    after[end - 1] = ChatRunBackdrops.PAD;
                    backdrops = true;
                }
            }
            cursor = Math.max(end, at + 1);
        }
        // A channel's name after a # opening a word, and a message's id
        // after it, read exactly as the sent line reads them.
        cursor = ChatInputRules.isCommand(text) ? text.length() : 0;
        while (cursor < text.length()) {
            int hash = text.indexOf('#', cursor);
            if (hash < 0) {
                break;
            }
            boolean opensWord = hash == 0 || Character.isWhitespace(text.charAt(hash - 1));
            ChatChannelSuggester.Link link = opensWord
                    ? ChatChannelSuggester.linkAt(text, hash) : null;
            if (link == null) {
                cursor = hash + 1;
                continue;
            }
            int linkEnd = link.end;
            int style = ChatInputStyles.styleAt(styles, hash);
            if ((style & ChatMarkdown.Span.CODE) == 0) {
                int color = ClientChatChannelState.displayColor(link.channel,
                        link.scope);
                for (int index = hash; index < linkEnd; index++) {
                    colors[index] = color;
                }
                if ((style & ChatMarkdown.Span.SPOILER) == 0) {
                    before[hash] = ChatRunBackdrops.PAD;
                    after[linkEnd - 1] = ChatRunBackdrops.PAD;
                    backdrops = true;
                }
            }
            cursor = linkEnd;
        }
        return new Links(colors, before, after, players, backdrops);
    }

    /**
     * A backdrop from {@code left} to {@code right} over the well, the
     * field's text top at {@code top}, fading with the field's words.
     */
    private static void drawBackdrop(int left, int right, int top, int rgb) {
        ChatRunBackdrops.fill(left, top + ChatRunBackdrops.TOP, right,
                top + ChatRunBackdrops.BOTTOM, rgb, ChatInputBar.faded(255));
    }

    /**
     * Draws {@code [from, to)} of the visible text as runs of one
     * colour, and answers where the text ends.
     */
    private int drawRuns(String visible, int[] colors, int from, int to,
                         int x, int y) {
        int cursor = x;
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && colors[end] == colors[start]) {
                end++;
            }
            String run = visible.substring(start, end);
            LostTalesChatVisualStyle.drawColored(this.font, run, cursor, y,
                    colors[start], ChatInputBar.faded(255));
            cursor += this.font.getStringWidth(run);
            start = end;
        }
        return cursor;
    }

    /** The selection band's wash: the palette's steel blue, translucent. */
    private static final int SELECTION_ARGB = LostTalesChatVisualStyle.argb(
            LostTalesColors.rgb(LostTalesColors.STEEL_BLUE), 0x66);

    /**
     * The selection band: a translucent wash laid over the text — and
     * over an emoji or token preview — rather than vanilla's colour
     * inversion, which turned sprites into their negatives.
     */
    private void drawSelection(int left, int top, int right, int bottom) {
        int fromX = Math.min(left, right);
        int toX = Math.max(left, right);
        int fromY = Math.min(top, bottom);
        int toY = Math.max(top, bottom);
        toX = Math.min(toX, this.xPosition + getWidth());
        fromX = Math.min(fromX, this.xPosition + getWidth());
        Gui.drawRect(fromX, fromY, toX, toY, SELECTION_ARGB);
    }

    /**
     * One resolved token: its raw span and its chat preview — a share
     * token's bracketed icon and name, or an emoji shortcode's sprite.
     */
    private static final class TokenPreview {
        final int start;
        final int end;
        final ChatShareKind kind;
        final ItemStack stack;
        final String markerIcon;
        final String name;
        /** The emoji this span previews as; null for a share token. */
        final ChatEmoji emoji;
        /** The brackets' and name's colour (white reads as ivory). */
        final int rgb;
        /** The marker artwork's exact colour; white stays untinted. */
        final int iconRgb;
        /** Display width: the icon slot and the name, with the backdrop's padding. */
        final int width;

        TokenPreview(int start, int end, ChatShareKind kind,
                     ItemStack stack, String markerIcon, String name,
                     int rgb, int iconRgb, int width) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.stack = stack;
            this.markerIcon = markerIcon;
            this.name = name;
            this.emoji = null;
            this.rgb = rgb;
            this.iconRgb = iconRgb;
            this.width = width;
        }

        TokenPreview(int start, int end, ChatEmoji emoji) {
            this.start = start;
            this.end = end;
            this.kind = null;
            this.stack = null;
            this.markerIcon = "";
            this.name = "";
            this.emoji = emoji;
            this.rgb = 0;
            this.iconRgb = 0;
            this.width = ChatInlineIcons.SLOT_WIDTH;
        }
    }

    /**
     * The previews for the given raw text, rebuilt only when it changes.
     * Only tokens the client can resolve right now — the same match the
     * send will make — become previews; the rest stay literal text.
     */
    private List<TokenPreview> previewsFor(String text) {
        if (this.plainText) {
            return Collections.emptyList();
        }
        if (text.equals(this.previewedText)) {
            return this.previews;
        }
        this.previewedText = text;
        this.previews = buildPreviews(text);
        return this.previews;
    }

    private List<TokenPreview> buildPreviews(String text) {
        List<TokenPreview> result = buildSharePreviews(text);
        // A command never previews: what is typed is what runs, and a
        // completed shortcode in one is an argument, not an emoji.
        if (LostTalesConfig.enableChatEmojis && !text.startsWith("/")
                && text.indexOf(':') >= 0) {
            result = mergeEmojiPreviews(text, result);
        }
        return outsideCode(result, stylesFor(text));
    }

    /**
     * The previews not inside code: quoted text and a command are shown
     * as typed in the sent line, so a token inside either is literal
     * here too.
     */
    private static List<TokenPreview> outsideCode(List<TokenPreview> previews,
                                                  int[] styles) {
        if (styles == null || previews.isEmpty()) {
            return previews;
        }
        List<TokenPreview> kept = new ArrayList<TokenPreview>(previews.size());
        for (int index = 0; index < previews.size(); index++) {
            TokenPreview preview = previews.get(index);
            if (!ChatInputStyles.isCode(styles, preview.start)) {
                kept.add(preview);
            }
        }
        return kept;
    }

    private List<TokenPreview> buildSharePreviews(String text) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || text.indexOf('[') < 0) {
            return Collections.emptyList();
        }
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(text);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatShareCandidates.ItemEntry> items =
                ChatShareCandidates.items(minecraft.thePlayer);
        List<ChatShareCandidates.MarkerEntry> markers =
                ChatShareCandidates.markers();
        List<ChatShareCandidates.QuestEntry> quests =
                ChatShareCandidates.quests();
        List<TokenPreview> result = new ArrayList<TokenPreview>();
        int index = 0;
        for (ChatShareTokenParser.Token token : tokens) {
            if (index++ >= ChatShareTokenParser.MAX_TOKENS) {
                break;
            }
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (entry.matchesToken(token)) {
                        result.add(itemPreview(token, entry.stack));
                        break;
                    }
                }
            } else if (token.kind == ChatShareKind.QUEST) {
                for (ChatShareCandidates.QuestEntry entry : quests) {
                    if (entry.matchesToken(token)) {
                        result.add(preview(token, null, "", entry.name,
                                LostTalesChatPresentation.QUEST_RGB,
                                LostTalesChatPresentation.QUEST_RGB));
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        result.add(preview(token, null,
                                entry.marker.getIconName(),
                                ChatShareTokenParser.plainName(
                                        entry.marker.getName()),
                                ChatInlineIcons.markerTextRgb(
                                        entry.marker.getColorName()),
                                ChatInlineIcons.markerRgb(
                                        entry.marker.getColorName())));
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Adds a preview for every complete {@code :name:} of a registered
     * emoji, exactly the spans the sent message will draw as sprites —
     * an alias or an unfinished name stays the literal text it is —
     * merged in span order with the share previews, whose tokens a
     * shortcode can never overlap ({@code :} is not a share-name
     * character, so a share token's span never parses as an emoji).
     */
    private static List<TokenPreview> mergeEmojiPreviews(
            String text, List<TokenPreview> shares) {
        List<TokenPreview> merged = new ArrayList<TokenPreview>(shares);
        int index = 0;
        int length = text.length();
        while (index < length) {
            if (text.charAt(index) != ':') {
                index++;
                continue;
            }
            int nameEnd = scanEmojiName(text, index + 1);
            ChatEmoji emoji = nameEnd > index + 1 && nameEnd < length
                    && text.charAt(nameEnd) == ':'
                    ? ChatEmoji.fromName(text.substring(index + 1, nameEnd))
                    : null;
            if (emoji == null || overlapsAny(shares, index, nameEnd + 1)) {
                index++;
                continue;
            }
            merged.add(new TokenPreview(index, nameEnd + 1, emoji));
            index = nameEnd + 1;
        }
        if (merged.size() == shares.size()) {
            return shares;
        }
        Collections.sort(merged, new Comparator<TokenPreview>() {
            @Override
            public int compare(TokenPreview left, TokenPreview right) {
                return left.start - right.start;
            }
        });
        return merged;
    }

    /** The parser's name scan: lowercase, digits, underscores, bounded. */
    private static int scanEmojiName(String text, int start) {
        int limit = Math.min(text.length(),
                start + ChatEmoji.longestName());
        int index = start;
        while (index < limit && isEmojiNameCharacter(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isEmojiNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    private static boolean overlapsAny(List<TokenPreview> previews,
                                       int start, int end) {
        for (int index = 0; index < previews.size(); index++) {
            TokenPreview preview = previews.get(index);
            if (start < preview.end && end > preview.start) {
                return true;
            }
        }
        return false;
    }

    private TokenPreview itemPreview(ChatShareTokenParser.Token token,
                                     ItemStack stack) {
        EnumRarity rarity = stack.getRarity();
        EnumChatFormatting formatting = rarity == null
                || rarity.rarityColor == null
                ? EnumChatFormatting.WHITE : rarity.rarityColor;
        int rgb = LostTalesChatPresentation.rarityRgb(formatting);
        return preview(token, stack, "",
                ChatShareTokenParser.plainName(stack.getDisplayName()),
                rgb, rgb);
    }

    private TokenPreview preview(ChatShareTokenParser.Token token,
                                 ItemStack stack, String markerIcon,
                                 String name, int rgb, int iconRgb) {
        // Icon and name on the backdrop that frames them, its padding
        // either side, as the sent line has it.
        int width = ChatRunBackdrops.PAD + slotWidth(token.kind)
                + this.font.getStringWidth(" " + name) + ChatRunBackdrops.PAD;
        return new TokenPreview(token.start, token.end, token.kind, stack,
                markerIcon, name, rgb, iconRgb, width);
    }

    /** The slot a shared thing's icon takes, as the message lines give it. */
    private static int slotWidth(ChatShareKind kind) {
        return kind == ChatShareKind.ITEM ? ChatInlineIcons.itemSlotWidth()
                : ChatInlineIcons.SLOT_WIDTH;
    }

    /**
     * The field with previews in it. Same structure as the plain path:
     * the visible span is cut to the box, drawn, and the caret and
     * selection are placed on it — every position through the one
     * display model, so nothing drawn and nothing hit can disagree.
     */
    private void drawWithPreviews(String text, List<TokenPreview> resolved,
                                  int scrollOffset) {
        int visibleEnd = scrollOffset + fittingRawCount(text, resolved,
                scrollOffset, getWidth());
        int left = this.xPosition;
        int top = this.yPosition;
        int caret = getCursorPosition();
        boolean caretInside = caret >= scrollOffset && caret <= visibleEnd;
        boolean caretVisible = caretLit() && caretInside;
        int caretX = left + displayedX(text, resolved, scrollOffset,
                Math.max(scrollOffset, Math.min(caret, visibleEnd)));
        drawLinkBackdrops(text, resolved, scrollOffset, visibleEnd, left, top);
        if (caretVisible) {
            drawCaretShadow(caretX, top);
        }
        int[] colors = colorsOf(text, scrollOffset, visibleEnd);
        int x = left;
        int cursor = scrollOffset;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            if (preview.end > visibleEnd) {
                break;
            }
            x = drawPlainRuns(text, colors, scrollOffset, cursor,
                    preview.start, x, top);
            x = drawPreview(preview, x, top);
            cursor = preview.end;
        }
        drawPlainRuns(text, colors, scrollOffset, cursor, visibleEnd,
                x, top);

        if (caretVisible) {
            drawCaretBar(caretX, top);
        }
        int selection = getSelectionEnd();
        if (selection != caret && caretInside) {
            int clamped = Math.max(scrollOffset,
                    Math.min(selection, visibleEnd));
            int selectionX = left + displayedX(text, resolved, scrollOffset,
                    clamped);
            drawSelection(caretX, selectionBandTop(top), selectionX - 1,
                    selectionBandBottom(top));
        }
    }

    /**
     * The backdrop of every link of {@code [from, to)} that wears one, on
     * the well, where the display model has it: from the padding before
     * its {@code @} or {@code #} to a clear pixel short of the one after
     * it. A player's mention stands on the ping's slate blue, anything
     * else on the darkest shade of its own colour.
     */
    private void drawLinkBackdrops(String text, List<TokenPreview> resolved,
                                   int from, int to, int left, int top) {
        Links found = linksFor(text);
        if (!found.backdrops) {
            return;
        }
        for (int start = from; start < to; start++) {
            // A link begins here, or began before the field scrolled
            // past its start and still wears its backdrop from the edge.
            boolean opens = found.beforeAt(start) > 0;
            if (!opens && (start > from || !wearsBackdrop(found, start))) {
                continue;
            }
            int end = start;
            while (end < text.length() && found.afterAt(end) == 0) {
                end++;
            }
            end = end + 1;
            // One the field's end cuts runs on to that end.
            int right = end <= to ? displayedX(text, resolved, from, end) - 1
                    : displayedX(text, resolved, from, to);
            int rgb = found.players[start] ? ChatRunBackdrops.PLAYER_RGB
                    : LostTalesColors.darkestShade(found.colors[start],
                            LostTalesChatVisualStyle.SURFACE_RGB);
            drawBackdrop(left + displayedX(text, resolved, from, start),
                    left + right, top, rgb);
            start = end - 1;
        }
    }

    /** Whether the character is of a link that wears a backdrop, wherever in it. */
    private static boolean wearsBackdrop(Links found, int index) {
        if (found.colorAt(index) < 0) {
            return false;
        }
        int start = index;
        while (start > 0 && found.colorAt(start - 1) >= 0) {
            start--;
        }
        return found.beforeAt(start) > 0;
    }

    /**
     * Runs of one colour and one markup style over the raw range, like
     * the plain path's: the style's decoration codes ahead of each run,
     * marks and code and spoiler text in their subdued colours over
     * whatever the link pass chose, and a link inside its backdrop's
     * padding.
     */
    private int drawPlainRuns(String text, int[] colors, int colorsBase,
                              int from, int to, int x, int y) {
        int[] styles = stylesFor(text);
        int cursor = x;
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && colors[end - colorsBase]
                    == colors[start - colorsBase]
                    && ChatInputStyles.styleAt(styles, end)
                            == ChatInputStyles.styleAt(styles, start)) {
                end++;
            }
            int style = ChatInputStyles.styleAt(styles, start);
            String run = ChatInputStyles.prefixOf(style)
                    + text.substring(start, end);
            LostTalesChatVisualStyle.drawColored(this.font, run,
                    cursor + linksFor(text).beforeAt(start), y,
                    ChatInputStyles.colorOf(style, colors[start - colorsBase]),
                    ChatInputBar.faded(255));
            cursor += rawWidth(text, start, end);
            start = end;
        }
        return cursor;
    }

    /**
     * The styles of the raw text as the field shows them
     * ({@link ChatInputStyles#layout}), laid out only when it changes;
     * null for text with nothing to style, the common case.
     */
    private int[] stylesFor(String text) {
        if (this.plainText) {
            return null;
        }
        if (text.equals(this.styledText)) {
            return this.styles;
        }
        this.styledText = text;
        this.styles = ChatInputStyles.layout(text);
        return this.styles;
    }

    /**
     * The drawn width of one raw character: its glyph, a pixel more in
     * bold, and a link backdrop's padding where one opens or closes.
     */
    private int charWidth(String text, int index) {
        int width = this.font.getCharWidth(text.charAt(index));
        Links found = linksFor(text);
        return (width > 0 && ChatInputStyles.isBold(stylesFor(text), index)
                ? width + 1 : width) + found.beforeAt(index) + found.afterAt(index);
    }

    /** The drawn width of the raw range {@code [from, to)}. */
    private int rawWidth(String text, int from, int to) {
        int width = 0;
        for (int index = from; index < to; index++) {
            width += charWidth(text, index);
        }
        return width;
    }

    /** The token as chat will show it: icon and name on their backdrop. */
    private int drawPreview(TokenPreview preview, int x, int y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (preview.emoji != null) {
            // The shortcode as the sprite it will be in chat, in the
            // same slot and box the message lines give it.
            ChatInlineIcons.drawEmoji(minecraft, preview.emoji,
                    ChatInlineIcons.boxLeft(x, ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.boxTop(y, ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.contentSize(ChatInlineIcons.SLOT_WIDTH),
                    ChatInputBar.faded(255));
            return x + preview.width;
        }
        int start = x;
        drawBackdrop(start, start + preview.width - 1, y,
                LostTalesColors.darkestShade(preview.rgb,
                        LostTalesChatVisualStyle.SURFACE_RGB));
        x += ChatRunBackdrops.PAD;
        int slot = slotWidth(preview.kind);
        float boxX = ChatInlineIcons.boxLeft(x, slot);
        float boxY = ChatInlineIcons.boxTop(y, slot);
        float size = ChatInlineIcons.contentSize(slot);
        if (preview.kind == ChatShareKind.ITEM) {
            ChatInlineIcons.drawItem(minecraft, preview.stack, boxX, boxY,
                    size, ChatInputBar.faded(255));
        } else if (preview.kind == ChatShareKind.QUEST) {
            LostTalesUiSheet.QUEST.drawWithShadow(boxX, boxY, ChatInputBar.faded(255));
        } else {
            ChatInlineIcons.drawMarker(minecraft, preview.markerIcon,
                    preview.iconRgb, boxX, boxY, size,
                    ChatInputBar.faded(255));
        }
        x += slot;
        String tail = " " + preview.name;
        LostTalesChatVisualStyle.drawColored(this.font, tail, x, y,
                preview.rgb, ChatInputBar.faded(255));
        return start + preview.width;
    }

    /**
     * Raw characters from {@code from} that fit {@code room} display
     * pixels: plain characters one by one, a previewed token as one
     * piece — a token the room cannot take whole ends the visible span
     * before it.
     */
    private int fittingRawCount(String text, List<TokenPreview> resolved,
                                int from, int room) {
        int x = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            while (cursor < preview.start) {
                int width = charWidth(text, cursor);
                if (x + width > room) {
                    return cursor - from;
                }
                x += width;
                cursor++;
            }
            if (x + preview.width > room) {
                return cursor - from;
            }
            x += preview.width;
            cursor = preview.end;
        }
        while (cursor < text.length()) {
            int width = charWidth(text, cursor);
            if (x + width > room) {
                return cursor - from;
            }
            x += width;
            cursor++;
        }
        return cursor - from;
    }

    /**
     * The display x of a raw index, measured from {@code from}: inside a
     * previewed token the position is proportional, so a selection band
     * over one still reads as a span.
     */
    private int displayedX(String text, List<TokenPreview> resolved,
                           int from, int index) {
        int x = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            if (preview.start >= index) {
                break;
            }
            x += rawWidth(text, cursor, preview.start);
            if (preview.end <= index) {
                x += preview.width;
                cursor = preview.end;
                continue;
            }
            return x + preview.width * (index - preview.start)
                    / (preview.end - preview.start);
        }
        return x + rawWidth(text, cursor, index);
    }

    /**
     * The raw index a click at {@code x} lands the caret on: between two
     * plain characters as vanilla puts it, and on the nearer edge of a
     * previewed token, which edits as one piece.
     */
    private int rawIndexAtX(String text, List<TokenPreview> resolved,
                            int from, int x) {
        int cx = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            while (cursor < preview.start) {
                int width = charWidth(text, cursor);
                if (cx + width > x) {
                    return cursor;
                }
                cx += width;
                cursor++;
            }
            if (cx + preview.width > x) {
                return (x - cx) * 2 > preview.width
                        ? preview.end : preview.start;
            }
            cx += preview.width;
            cursor = preview.end;
        }
        while (cursor < text.length()) {
            int width = charWidth(text, cursor);
            if (cx + width > x) {
                return cursor;
            }
            cx += width;
            cursor++;
        }
        return text.length();
    }

    /** The scroll offset moved off any token it came to rest inside. */
    private static int snapScrollOutsideTokens(List<TokenPreview> previews,
                                               int offset) {
        for (int index = 0; index < previews.size(); index++) {
            TokenPreview preview = previews.get(index);
            if (offset > preview.start && offset < preview.end) {
                return preview.start;
            }
        }
        return offset;
    }

    /**
     * The scroll offset the drawn field actually needs: zero while the
     * whole text fits the box as drawn, otherwise the least offset —
     * always on a token boundary — that keeps the caret visible and
     * leaves no empty tail while text remains scrolled out on the left.
     * Vanilla's own value is only the starting point; the field's
     * previews make drawn and raw widths disagree, so the display model
     * decides for itself.
     */
    private int correctedScrollOffset(String text,
                                      List<TokenPreview> resolved,
                                      int scrollOffset) {
        int offset = snapScrollOutsideTokens(resolved, Math.max(0,
                Math.min(scrollOffset, text.length())));
        int room = getWidth();
        if (displayedX(text, resolved, 0, text.length()) <= room) {
            return 0;
        }
        int caret = Math.max(0,
                Math.min(getCursorPosition(), text.length()));
        if (caret < offset) {
            offset = snapScrollOutsideTokens(resolved, caret);
        }
        while (offset < text.length() && caret > offset
                + fittingRawCount(text, resolved, offset, room)) {
            offset = stepOverTokens(resolved, offset + 1, true);
        }
        // Fill the box from the right: while everything from one step
        // earlier to the end still fits as drawn, show it.
        while (offset > 0) {
            int previous = stepOverTokens(resolved, offset - 1, false);
            if (displayedX(text, resolved, previous, text.length())
                    <= room) {
                offset = previous;
            } else {
                break;
            }
        }
        return offset;
    }

    /** A position moved off any token, forward or back along the step. */
    private static int stepOverTokens(List<TokenPreview> previews,
                                      int position, boolean forward) {
        for (int index = 0; index < previews.size(); index++) {
            TokenPreview preview = previews.get(index);
            if (position > preview.start && position < preview.end) {
                return forward ? preview.end : preview.start;
            }
        }
        return position;
    }

    /**
     * The nearest position outside every resolved token: a position
     * strictly inside one moves to the edge the motion came from — past
     * the whole token for a step that entered it — and, when there is no
     * motion to read a direction from, to the nearer edge, the same rule
     * a click uses. Positions already on a boundary stay exactly where
     * they are.
     */
    private int snapOutsideTokens(int position, int from) {
        List<TokenPreview> resolved = previewsFor(getText());
        for (int index = 0; index < resolved.size(); index++) {
            TokenPreview preview = resolved.get(index);
            if (position > preview.start && position < preview.end) {
                if (position > from) {
                    return preview.end;
                }
                if (position < from) {
                    return preview.start;
                }
                return (position - preview.start) * 2
                        > preview.end - preview.start
                        ? preview.end : preview.start;
            }
        }
        return position;
    }

    @Override
    public void setCursorPosition(int position) {
        super.setCursorPosition(
                snapOutsideTokens(position, getCursorPosition()));
        this.caretNanos = System.nanoTime();
    }

    @Override
    public void setSelectionPos(int position) {
        super.setSelectionPos(
                snapOutsideTokens(position, getSelectionEnd()));
        this.caretNanos = System.nanoTime();
    }

    /** Taking the keys lights the caret at once. */
    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) {
            this.caretNanos = System.nanoTime();
        }
        super.setFocused(focused);
    }

    /**
     * Backspace directly after a resolved token, and Delete directly
     * before one, remove the whole token; with a selection present the
     * selection is deleted exactly as vanilla deletes it, already
     * token-whole because its ends can only rest on boundaries.
     */
    @Override
    public void deleteFromCursor(int amount) {
        int adjusted = amount;
        if (getCursorPosition() == getSelectionEnd()) {
            int cursor = getCursorPosition();
            List<TokenPreview> resolved = previewsFor(getText());
            for (int index = 0; index < resolved.size(); index++) {
                TokenPreview preview = resolved.get(index);
                if (adjusted < 0 && cursor == preview.end) {
                    adjusted = preview.start - preview.end;
                    break;
                }
                if (adjusted > 0 && cursor == preview.start) {
                    adjusted = preview.end - preview.start;
                    break;
                }
            }
        }
        super.deleteFromCursor(adjusted);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        String text = getText();
        List<TokenPreview> resolved = previewsFor(text);
        boolean inside = LostTalesUiHitBox.contains(mouseX, mouseY, this.xPosition,
                this.yPosition, getWidth(), this.fieldHeight);
        if ((resolved.isEmpty() && !linksFor(text).backdrops)
                || !isStyled() || !inside || button != 0 || !isFocused()) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        int scrollOffset;
        try {
            scrollOffset = LINE_SCROLL_OFFSET.getInt(this);
        } catch (IllegalAccessException unreadable) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        setCursorPosition(rawIndexAtX(text, resolved, scrollOffset,
                mouseX - this.xPosition));
    }

    private static void logFallbackOnce() {
        if (fallbackLogged) {
            return;
        }
        fallbackLogged = true;
        FMLLog.warning("[%s] The chat input field keeps vanilla's drawing; "
                + "its text shadow will not match the rest of the chat",
                LostTalesMetaData.MOD_ID);
    }

    /** One of vanilla's own int fields, by either of its names. */
    private static Field resolve(String... names) {
        for (String name : names) {
            try {
                Field field = GuiTextField.class.getDeclaredField(name);
                if (field.getType() != int.class
                        || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                continue;
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }
}
