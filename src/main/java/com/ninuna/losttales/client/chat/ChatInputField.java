package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
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
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/**
 * The chat's one text field — the input bar, and every field of a small
 * window — drawn the way the rest of the chat is drawn.
 *
 * <p>Vanilla's {@code GuiTextField} draws its text with
 * {@code drawStringWithShadow}, whose shadow is a quarter of the text's
 * own colour at full opacity — a different shadow from every other glyph
 * in the chat, right beside them on the same bar. Everything else about
 * the field is vanilla's: this replaces the drawing alone, using the
 * shared {@link LostTalesChatVisualStyle} treatment for the text, the
 * caret and the selection.</p>
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
 * <p>Everything that can be inserted is shown as the sent line will
 * show it: a complete emoji shortcode ({@code :smile:}) as its sprite; a
 * share whose item, marker or quest the client can resolve
 * ({@code [i:Stone Sword]}) as its icon and real name on its backdrop; a
 * mention of a name the {@code @} list offers as the ping it will be,
 * seafoam on slate for a person and a role in its own colour; and a
 * channel link ({@code #global}, {@code #ooc/1234}) by the channel's
 * shown name in its colour, with the speech bubble of a link to one
 * message. The raw text is untouched — it is what goes on the wire — but
 * while it is edited each of them behaves as one character: the caret can
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
 * no previews and no markup. A status line shows its emoji and nothing
 * else ({@link #emojiOnly}), as a status line is drawn once set.</p>
 *
 * <p>The caret is the mod's one caret ({@link LostTalesUiCaret}), lit
 * from the last key or caret move, and only while the field holds the
 * keys.</p>
 *
 * <p>The chat's own message field grows in rows ({@link #rows}): its
 * words wrap at its width, Shift+Return starts a paragraph on a row of
 * its own ({@link #insertParagraph}), and past its last row it scrolls
 * a row at a time. Up and Down move between the rows, Home and End go
 * to a row's ends, and a click lands on the row under it. Every other
 * field keeps one row that scrolls sideways.</p>
 *
 * <p>How far vanilla has scrolled the text has no accessor, so it is
 * read reflectively by both its names, verified to be the int it is.
 * Without it the field falls back to vanilla's own drawing rather than
 * showing the wrong text.</p>
 */
public final class ChatInputField extends GuiTextField {
    private static final Field LINE_SCROLL_OFFSET =
            resolve("lineScrollOffset", "field_146225_q");
    private static boolean fallbackLogged;
    /** How tall one of the field's rows is: a message row's height. */
    static final int ROW_HEIGHT = LostTalesChatOverlayRenderer.LINE_HEIGHT;

    private final FontRenderer font;
    private final int fieldHeight;
    /** The raw text the previews below were resolved for. */
    private String previewedText;
    /** The names the previews' mentions were found among. */
    private List<ChatMentionCandidate> previewedNames;
    private List<TokenPreview> previews = Collections.emptyList();
    /** The raw text the styles below were laid out for. */
    private String styledText;
    /** Every character's style, or null for text with nothing to style. */
    private int[] styles;
    /** Whether what is typed is shown as it is, as a search field's is. */
    private boolean plainText;
    /** Whether only emoji are shown as they will be, as a status line's are. */
    private boolean emojiOnly;
    /** Where the names a mention may name come from; null for none. */
    private MentionSource mentions;
    /** When the field last took a key or moved its caret: the caret's blink starts there. */
    private long caretNanos = System.nanoTime();
    /** The most rows the field grows to; one keeps a single row that scrolls sideways. */
    private int maxRows = 1;
    /** The rows the field has room for where it stands now, at most {@link #maxRows}. */
    private int rowRoom = 1;
    /** The first row shown while more rows are typed than shown. */
    private int topRow;
    /** Where each row starts, as last laid out, and what it was laid out for. */
    private int[] rowStarts = {0};
    private String rowsText;
    private int rowsWidth = -1;
    private List<TokenPreview> rowsPreviews;
    private int[] rowsStyles;

    public ChatInputField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.font = font;
        this.fieldHeight = height;
    }

    /**
     * Shows what is typed as it is — no mention colours, no previews, no
     * markup — as a search field's text, which is looked up rather than
     * sent, should be.
     */
    public ChatInputField plainText() {
        this.plainText = true;
        return this;
    }

    /**
     * Shows a complete emoji shortcode as its sprite and nothing else as
     * anything but itself, as a status line is drawn once it is set.
     */
    ChatInputField emojiOnly() {
        this.emojiOnly = true;
        return this;
    }

    /** Where the names the {@code @} list offers come from. */
    interface MentionSource {
        /** The names now: the same list, unchanged, until they change. */
        List<ChatMentionCandidate> candidates();
    }

    /** Lets mentions of the names {@code source} offers show as pings. */
    void mentionsFrom(MentionSource source) {
        this.mentions = source;
    }

    /** Where the field's pings find their names; null for none. */
    MentionSource mentionSource() {
        return this.mentions;
    }

    /**
     * Lets the field grow to {@code rows} rows as its words wrap, with a
     * paragraph break starting a row of its own: the chat's message
     * field. Kept to one row where the field cannot be drawn its own way.
     */
    ChatInputField rows(int rows) {
        this.maxRows = isStyled() ? Math.max(1, rows) : 1;
        return this;
    }

    /** Whether the field lays its words out in rows. */
    boolean hasRows() {
        return this.maxRows > 1;
    }

    /** How many rows the window leaves the field room for, at most its own most. */
    void roomForRows(int rows) {
        this.rowRoom = Math.max(1, Math.min(this.maxRows, rows));
    }

    /** How many rows the field shows: as many as its words take, as many as it has room for. */
    int shownRows() {
        return hasRows() ? Math.min(this.rowRoom, layOutRows().length) : 1;
    }

    /**
     * Whether what is typed may hold paragraphs: a message or a whisper,
     * in a field of rows. A command is one line, and an action one
     * sentence.
     */
    boolean takesParagraphs() {
        String text = getText();
        return hasRows() && (!ChatInputRules.isCommand(text)
                || ChatInputRules.isWhisperCommand(text));
    }

    /**
     * Starts a new paragraph at the caret, in place of what is selected
     * (Shift+Return). Past a message's last paragraph a space goes in
     * instead, and a field at its length takes nothing.
     */
    void insertParagraph() {
        if (!takesParagraphs()) {
            return;
        }
        String text = getText();
        int breaks = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == ChatMessageValidator.PARAGRAPH_BREAK) {
                breaks++;
            }
        }
        replaceSelection(breaks + 1 < ChatMessageValidator.MAX_PARAGRAPHS
                ? String.valueOf(ChatMessageValidator.PARAGRAPH_BREAK) : " ");
    }

    private void replaceSelection(String inserted) {
        String text = getText();
        int from = Math.min(getCursorPosition(), getSelectionEnd());
        int to = Math.max(getCursorPosition(), getSelectionEnd());
        if (text.length() - (to - from) + inserted.length()
                > getMaxStringLength()) {
            return;
        }
        setText(text.substring(0, from) + inserted + text.substring(to));
        setCursorPosition(from + inserted.length());
    }

    /**
     * Pasted lines keep their breaks as paragraphs where what is typed
     * takes them, as many as a message holds, and are joined by spaces
     * in a command; in any other field they run together, as vanilla's
     * field has them.
     */
    @Override
    public void writeText(String typed) {
        if (!hasRows() || typed == null
                || (typed.indexOf('\n') < 0 && typed.indexOf('\r') < 0)) {
            super.writeText(typed);
            return;
        }
        String[] lines = typed.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0 && takesParagraphs()) {
                insertParagraph();
            } else if (index > 0) {
                super.writeText(" ");
            }
            super.writeText(lines[index]);
        }
    }

    /**
     * Moves the caret a row up or down, as near the place it stands
     * across as that row allows; with {@code select} the selection's
     * moving end goes instead, as Shift and the arrows move it. False
     * when there is no row that way, so the key can walk what was sent.
     */
    boolean moveRow(int direction, boolean select) {
        if (!hasRows()) {
            return false;
        }
        String text = getText();
        int[] starts = layOutRows();
        int moving = getSelectionEnd();
        int row = ChatInputRows.rowOf(starts, moving);
        int target = row + direction;
        if (target < 0 || target >= starts.length) {
            return false;
        }
        int across = displayedX(text, previewsFor(text), starts[row], moving);
        int index = indexInRow(starts, target, across);
        if (select) {
            setSelectionPos(index);
        } else {
            setCursorPosition(index);
        }
        return true;
    }

    /**
     * Home and End go to the ends of the row the caret is on in a field
     * of rows, Shift selecting as it does elsewhere; every other key is
     * vanilla's.
     */
    @Override
    public boolean textboxKeyTyped(char character, int keyCode) {
        if (hasRows() && isFocused()
                && (keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END)) {
            int[] starts = layOutRows();
            int row = ChatInputRows.rowOf(starts, getSelectionEnd());
            int target = keyCode == Keyboard.KEY_HOME ? starts[row]
                    : ChatInputRows.caretEnd(getText(), starts, row);
            if (GuiScreen.isShiftKeyDown()) {
                setSelectionPos(target);
            } else {
                setCursorPosition(target);
            }
            return true;
        }
        return super.textboxKeyTyped(character, keyCode);
    }

    /**
     * Where each row of the words starts at the field's width, laid out
     * again only when the words, the width or their previews change.
     */
    private int[] layOutRows() {
        final String text = getText();
        final List<TokenPreview> resolved = previewsFor(text);
        int[] styles = stylesFor(text);
        int width = getWidth();
        if (text.equals(this.rowsText) && width == this.rowsWidth
                && resolved == this.rowsPreviews && styles == this.rowsStyles) {
            return this.rowStarts;
        }
        this.rowStarts = ChatInputRows.starts(text, new ChatInputRows.Measure() {
            @Override
            public int charWidth(int index) {
                return ChatInputField.this.charWidth(text, index);
            }

            @Override
            public int tokenEnd(int index) {
                TokenPreview preview = previewAt(resolved, index);
                return preview == null ? -1 : preview.end;
            }

            @Override
            public int tokenWidth(int index) {
                TokenPreview preview = previewAt(resolved, index);
                return preview == null ? 0 : preview.width;
            }
        }, width);
        this.rowsText = text;
        this.rowsWidth = width;
        this.rowsPreviews = resolved;
        this.rowsStyles = styles;
        return this.rowStarts;
    }

    /** The preview a token starting at {@code index} is shown as, or null. */
    private static TokenPreview previewAt(List<TokenPreview> previews,
                                          int index) {
        for (int at = 0; at < previews.size(); at++) {
            if (previews.get(at).start == index) {
                return previews.get(at);
            }
        }
        return null;
    }

    /** The raw index {@code across} display pixels into a row, never past where the caret may stand on it. */
    private int indexInRow(int[] starts, int row, int across) {
        String text = getText();
        int index = rawIndexAtX(text, previewsFor(text), starts[row],
                Math.max(0, across));
        return Math.min(index, ChatInputRows.caretEnd(text, starts, row));
    }

    /** Top of a shown row's text: the top row at the field's own y, each next a row lower. */
    private int rowTextTop(int row) {
        return this.yPosition + (row - this.topRow) * ROW_HEIGHT;
    }

    /**
     * The field in rows: each shown row drawn as the one row of a single
     * field is, from its start, the tokens' backdrops first, then the
     * caret's shadow, the words, the caret, and the selection over each
     * row it takes. The row the selection's moving end stands on is kept
     * in view.
     */
    private void drawRows() {
        String text = getText();
        List<TokenPreview> resolved = previewsFor(text);
        int[] starts = layOutRows();
        int shown = Math.min(this.rowRoom, starts.length);
        int caret = getCursorPosition();
        int selection = getSelectionEnd();
        int moving = ChatInputRows.rowOf(starts, selection);
        if (moving < this.topRow) {
            this.topRow = moving;
        } else if (moving >= this.topRow + shown) {
            this.topRow = moving - shown + 1;
        }
        this.topRow = Math.max(0, Math.min(this.topRow, starts.length - shown));
        int left = this.xPosition;
        int last = this.topRow + shown;
        int caretRow = ChatInputRows.rowOf(starts, caret);
        boolean caretShown = caretLit() && caretRow >= this.topRow
                && caretRow < last;
        int caretX = left + displayedX(text, resolved, starts[caretRow], caret);
        for (int row = this.topRow; row < last; row++) {
            int from = starts[row];
            int to = ChatInputRows.drawnEnd(text, starts, row);
            for (TokenPreview preview : resolved) {
                if (preview.start >= from && preview.end <= to
                        && preview.kind != TokenKind.EMOJI) {
                    int x = left + displayedX(text, resolved, from,
                            preview.start);
                    drawBackdrop(x, x + preview.width - 1, rowTextTop(row),
                            preview.backdropRgb);
                }
            }
        }
        if (caretShown) {
            drawCaretShadow(caretX, rowTextTop(caretRow));
        }
        for (int row = this.topRow; row < last; row++) {
            drawSpan(text, resolved, starts[row],
                    ChatInputRows.drawnEnd(text, starts, row), left,
                    rowTextTop(row));
        }
        if (caretShown) {
            drawCaretBar(caretX, rowTextTop(caretRow));
        }
        if (selection == caret) {
            return;
        }
        int low = Math.min(caret, selection);
        int high = Math.max(caret, selection);
        for (int row = this.topRow; row < last; row++) {
            int from = starts[row];
            int start = Math.max(low, from);
            int end = Math.min(high, ChatInputRows.drawnEnd(text, starts, row));
            if (start < end) {
                int top = rowTextTop(row);
                drawSelection(left + displayedX(text, resolved, from, start),
                        selectionBandTop(top),
                        left + displayedX(text, resolved, from, end) - 1,
                        selectionBandBottom(top));
            }
        }
    }

    /** A click on one of the shown rows: the caret lands where it points on it. */
    private boolean clickRows(int mouseX, int mouseY, int button) {
        int[] starts = layOutRows();
        int shown = Math.min(this.rowRoom, starts.length);
        int top = this.yPosition - WindowStyle.ROW_TEXT_TOP;
        if (button != 0 || !isFocused() || !LostTalesUiHitBox.contains(
                mouseX, mouseY, this.xPosition, top, getWidth(),
                shown * ROW_HEIGHT)) {
            return false;
        }
        int row = Math.min(starts.length - 1,
                this.topRow + (mouseY - top) / ROW_HEIGHT);
        setCursorPosition(indexInRow(starts, row, mouseX - this.xPosition));
        return true;
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
        if (hasRows()) {
            drawRows();
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
        if (!resolved.isEmpty() || stylesFor(text) != null) {
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
        if (headEnd > 0) {
            drawRun(visible.substring(0, headEnd), left, top);
        }
        if (caretInside && caret < visible.length()) {
            drawRun(visible.substring(caret), cursorX, top);
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
        return textTop - WindowStyle.ROW_TEXT_TOP
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
     * A backdrop from {@code left} to {@code right} over the well, the
     * field's text top at {@code top}, fading with the field's words.
     */
    private static void drawBackdrop(int left, int right, int top, int rgb) {
        ChatRunBackdrops.fill(left, top + ChatRunBackdrops.TOP, right,
                top + ChatRunBackdrops.BOTTOM, rgb, ChatInputBar.faded(255));
    }

    /** A run of the field's text as it is typed, in ivory. */
    private void drawRun(String run, int x, int y) {
        LostTalesUiInk.drawText(this.font, run, x, y,
                LostTalesUiInk.IVORY, ChatInputBar.faded(255));
    }

    /** The selection band's wash: the palette's steel blue, translucent. */
    private static final int SELECTION_ARGB = LostTalesUiInk.argb(
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

    /** What a resolved token is; each is drawn as its sent line draws it. */
    private enum TokenKind { EMOJI, SHARE, MENTION, CHANNEL }

    /**
     * One resolved token: its raw span and how it is shown — an emoji's
     * sprite; a share's icon and name; a mention's or a channel link's
     * words, and a message link's bubble after them — each but an emoji
     * on its backdrop.
     */
    private static final class TokenPreview {
        final int start;
        final int end;
        final TokenKind kind;
        /** What a share shares; null for any other token. */
        final ChatShareKind shareKind;
        final ItemStack stack;
        final String markerIcon;
        /** The emoji an emoji's span shows; null for any other token. */
        final ChatEmoji emoji;
        /** A share's name after its icon, or a mention or link as shown. */
        final String label;
        /** The words' colour (white reads as ivory). */
        final int rgb;
        /** The marker artwork's exact colour; white stays untinted. */
        final int iconRgb;
        /** The backdrop's colour. */
        final int backdropRgb;
        /** Whether a link to one message shows its bubble after the words. */
        final boolean bubble;
        /** Display width, the backdrop's padding included. */
        final int width;

        TokenPreview(int start, int end, TokenKind kind,
                     ChatShareKind shareKind, ItemStack stack,
                     String markerIcon, ChatEmoji emoji, String label,
                     int rgb, int iconRgb, int backdropRgb, boolean bubble,
                     int width) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.shareKind = shareKind;
            this.stack = stack;
            this.markerIcon = markerIcon;
            this.emoji = emoji;
            this.label = label;
            this.rgb = rgb;
            this.iconRgb = iconRgb;
            this.backdropRgb = backdropRgb;
            this.bubble = bubble;
            this.width = width;
        }

        static TokenPreview emoji(int start, int end, ChatEmoji emoji) {
            return new TokenPreview(start, end, TokenKind.EMOJI, null, null,
                    "", emoji, "", 0, 0, 0, false, ChatInlineIcons.SLOT_WIDTH);
        }
    }

    private static final Comparator<TokenPreview> BY_START =
            new Comparator<TokenPreview>() {
                @Override
                public int compare(TokenPreview left, TokenPreview right) {
                    return left.start - right.start;
                }
            };

    /**
     * The previews for the given raw text, rebuilt only when it changes
     * or the names a mention may name do. Only tokens the client can
     * resolve right now — the same match the send will make — become
     * previews; the rest stay literal text.
     */
    private List<TokenPreview> previewsFor(String text) {
        if (this.plainText) {
            return Collections.emptyList();
        }
        List<ChatMentionCandidate> names = this.mentions == null
                || this.emojiOnly ? Collections.<ChatMentionCandidate>emptyList()
                : this.mentions.candidates();
        if (text.equals(this.previewedText) && names == this.previewedNames) {
            return this.previews;
        }
        this.previewedText = text;
        this.previewedNames = names;
        this.previews = buildPreviews(text, names);
        return this.previews;
    }

    /**
     * Every token of the text the send will turn into something, in span
     * order, none overlapping another: shares first, whose brackets may
     * hold any name, then emoji, channel links and mentions. Nothing
     * inside code is a token, and a command is code but for the words a
     * whisper sends.
     */
    private List<TokenPreview> buildPreviews(String text,
                                             List<ChatMentionCandidate> names) {
        List<TokenPreview> found = new ArrayList<TokenPreview>();
        if (!this.emojiOnly) {
            found.addAll(buildSharePreviews(text));
        }
        if (LostTalesConfig.enableChatEmojis && text.indexOf(':') >= 0) {
            addEmojiPreviews(text, found);
        }
        if (!this.emojiOnly) {
            addChannelPreviews(text, found);
            if (LostTalesConfig.enableChatPings && text.indexOf('@') >= 0) {
                addMentionPreviews(text, names, found);
            }
        }
        Collections.sort(found, BY_START);
        return outsideCode(found, stylesFor(text));
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
                        result.add(sharePreview(token, null, "", entry.name,
                                LostTalesChatPresentation.QUEST_RGB,
                                LostTalesChatPresentation.QUEST_RGB));
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        result.add(sharePreview(token, null,
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
     * emoji, exactly the spans the sent message will draw as sprites: an
     * alias or an unfinished name stays the literal text it is.
     */
    private static void addEmojiPreviews(String text,
                                         List<TokenPreview> found) {
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
            if (emoji == null || overlapsAny(found, index, nameEnd + 1)) {
                index++;
                continue;
            }
            found.add(TokenPreview.emoji(index, nameEnd + 1, emoji));
            index = nameEnd + 1;
        }
    }

    /**
     * Adds a preview for every channel link after a {@code #} opening a
     * word, read as the sent line reads it: the channel's shown name in
     * its colour on the darkest shade of it, and for a link to one of its
     * messages the arrow and the bubble after the name.
     */
    private void addChannelPreviews(String text, List<TokenPreview> found) {
        int cursor = 0;
        while (cursor < text.length()) {
            int hash = text.indexOf('#', cursor);
            if (hash < 0) {
                return;
            }
            boolean opensWord = hash == 0
                    || Character.isWhitespace(text.charAt(hash - 1));
            ChatChannelSuggester.Link link = opensWord
                    ? ChatChannelSuggester.linkAt(text, hash) : null;
            if (link == null || overlapsAny(found, hash, link.end)) {
                cursor = hash + 1;
                continue;
            }
            boolean toMessage = ChatMessageIds.isServerId(link.messageId);
            String label = "#" + ClientChatChannelState.displayName(
                    link.channel, link.scope)
                    + (toMessage ? ChatChannelLinkMarker.MESSAGE_SEPARATOR : "");
            int rgb = ClientChatChannelState.displayColor(link.channel,
                    link.scope);
            found.add(new TokenPreview(hash, link.end, TokenKind.CHANNEL,
                    null, null, "", null, label, rgb, rgb,
                    LostTalesColors.darkestShade(rgb,
                            LostTalesUiTheme.primaryRgb()),
                    toMessage, ChatRunBackdrops.PAD
                            + this.font.getStringWidth(label)
                            + (toMessage ? ChatInlineIcons.SLOT_WIDTH : 0)
                            + ChatRunBackdrops.PAD));
            cursor = link.end;
        }
    }

    /**
     * Adds a preview for every mention of a name the {@code @} list
     * offers ({@link ChatInputMentions}), as typed: a person's in seafoam
     * on the ping's slate blue, a role's in its own colour on the darkest
     * shade of it.
     */
    private void addMentionPreviews(String text,
                                    List<ChatMentionCandidate> names,
                                    List<TokenPreview> found) {
        for (ChatInputMentions.Found mention
                : ChatInputMentions.find(text, names)) {
            if (overlapsAny(found, mention.start, mention.end)) {
                continue;
            }
            String label = text.substring(mention.start, mention.end);
            boolean role = mention.candidate.isRole();
            int rgb = role ? mention.candidate.getRoleColor()
                    : ChatMentionColors.PLAYER_RGB;
            found.add(new TokenPreview(mention.start, mention.end,
                    TokenKind.MENTION, null, null, "", null, label, rgb, rgb,
                    role ? LostTalesColors.darkestShade(rgb,
                            LostTalesUiTheme.primaryRgb())
                            : ChatRunBackdrops.PLAYER_RGB,
                    false, ChatRunBackdrops.PAD
                            + this.font.getStringWidth(label)
                            + ChatRunBackdrops.PAD));
        }
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
        return sharePreview(token, stack, "",
                ChatShareTokenParser.plainName(stack.getDisplayName()),
                rgb, rgb);
    }

    private TokenPreview sharePreview(ChatShareTokenParser.Token token,
                                      ItemStack stack, String markerIcon,
                                      String name, int rgb, int iconRgb) {
        // Icon and name on the backdrop that frames them, its padding
        // either side, as the sent line has it.
        int width = ChatRunBackdrops.PAD + slotWidth(token.kind)
                + this.font.getStringWidth(" " + name) + ChatRunBackdrops.PAD;
        return new TokenPreview(token.start, token.end, TokenKind.SHARE,
                token.kind, stack, markerIcon, null, name, rgb, iconRgb,
                LostTalesColors.darkestShade(rgb,
                        LostTalesUiTheme.primaryRgb()), false, width);
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
     * display model, so nothing drawn and nothing hit can disagree. The
     * tokens' backdrops go down first, then the caret's shadow, then the
     * words and icons over both.
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
        for (TokenPreview preview : resolved) {
            if (preview.start >= scrollOffset && preview.end <= visibleEnd
                    && preview.kind != TokenKind.EMOJI) {
                int x = left + displayedX(text, resolved, scrollOffset,
                        preview.start);
                drawBackdrop(x, x + preview.width - 1, top,
                        preview.backdropRgb);
            }
        }
        if (caretVisible) {
            drawCaretShadow(caretX, top);
        }
        drawSpan(text, resolved, scrollOffset, visibleEnd, left, top);

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
     * The raw range {@code [from, to)} from {@code left}: its plain runs
     * and its previews in turn, a token the range cannot hold whole
     * ending it.
     */
    private void drawSpan(String text, List<TokenPreview> resolved,
                          int from, int to, int left, int top) {
        int x = left;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            if (preview.end > to) {
                break;
            }
            x = drawPlainRuns(text, cursor, preview.start, x, top);
            x = drawPreview(preview, x, top);
            cursor = preview.end;
        }
        drawPlainRuns(text, cursor, to, x, top);
    }

    /**
     * Runs of one markup style over the raw range, like the plain path's:
     * the style's decoration codes ahead of each run, and marks, code and
     * spoiler text in their subdued colours.
     */
    private int drawPlainRuns(String text, int from, int to, int x, int y) {
        int[] styles = stylesFor(text);
        int cursor = x;
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && ChatInputStyles.styleAt(styles, end)
                    == ChatInputStyles.styleAt(styles, start)) {
                end++;
            }
            int style = ChatInputStyles.styleAt(styles, start);
            String run = ChatInputStyles.prefixOf(style)
                    + text.substring(start, end);
            LostTalesUiInk.drawText(this.font, run, cursor, y,
                    ChatInputStyles.colorOf(style), ChatInputBar.faded(255));
            cursor += rawWidth(text, start, end);
            start = end;
        }
        return cursor;
    }

    /**
     * The styles of the raw text as the field shows them
     * ({@link ChatInputStyles#layout}), laid out only when it changes;
     * null for text with nothing to style, the common case, and for a
     * field that shows no markup.
     */
    private int[] stylesFor(String text) {
        if (this.plainText || this.emojiOnly) {
            return null;
        }
        if (text.equals(this.styledText)) {
            return this.styles;
        }
        this.styledText = text;
        this.styles = ChatInputStyles.layout(text);
        return this.styles;
    }

    /** The drawn width of one raw character: its glyph, a pixel more in bold. */
    private int charWidth(String text, int index) {
        int width = this.font.getCharWidth(text.charAt(index));
        return width > 0 && ChatInputStyles.isBold(stylesFor(text), index)
                ? width + 1 : width;
    }

    /** The drawn width of the raw range {@code [from, to)}. */
    private int rawWidth(String text, int from, int to) {
        int width = 0;
        for (int index = from; index < to; index++) {
            width += charWidth(text, index);
        }
        return width;
    }

    /**
     * The token as chat will show it, over the backdrop already laid for
     * it; answers where it ends.
     */
    private int drawPreview(TokenPreview preview, int x, int y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int alpha = ChatInputBar.faded(255);
        if (preview.kind == TokenKind.EMOJI) {
            // The shortcode as the sprite it will be in chat, in the
            // same slot and box the message lines give it.
            ChatInlineIcons.drawEmoji(minecraft, preview.emoji,
                    ChatInlineIcons.boxLeft(x, ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.boxTop(y, ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.contentSize(ChatInlineIcons.SLOT_WIDTH),
                    alpha);
            return x + preview.width;
        }
        int cursor = x + ChatRunBackdrops.PAD;
        if (preview.kind == TokenKind.SHARE) {
            int slot = slotWidth(preview.shareKind);
            float boxX = ChatInlineIcons.boxLeft(cursor, slot);
            float boxY = ChatInlineIcons.boxTop(y, slot);
            float size = ChatInlineIcons.contentSize(slot);
            if (preview.shareKind == ChatShareKind.ITEM) {
                LostTalesUiItemIcon.drawFitted(minecraft, preview.stack, boxX, boxY,
                        size, alpha);
            } else if (preview.shareKind == ChatShareKind.QUEST) {
                LostTalesUiSheet.QUEST.drawWithShadow(boxX, boxY, alpha);
            } else {
                ChatInlineIcons.drawMarker(minecraft, preview.markerIcon,
                        preview.iconRgb, boxX, boxY, size, alpha);
            }
            LostTalesUiInk.drawText(this.font,
                    " " + preview.label, cursor + slot, y, preview.rgb, alpha);
            return x + preview.width;
        }
        LostTalesUiInk.drawText(this.font, preview.label, cursor,
                y, preview.rgb, alpha);
        if (preview.bubble) {
            int slotX = cursor + this.font.getStringWidth(preview.label);
            int slot = ChatInlineIcons.SLOT_WIDTH;
            ChatInlineIcons.drawSheetSprite(LostTalesUiSheet.SPEECH_BUBBLE,
                    ChatInlineIcons.boxLeft(slotX, slot),
                    ChatInlineIcons.boxTop(y, slot),
                    ChatInlineIcons.contentSize(slot), preview.rgb, alpha);
        }
        return x + preview.width;
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
        if (hasRows()) {
            if (!clickRows(mouseX, mouseY, button)) {
                super.mouseClicked(mouseX, mouseY, button);
            }
            return;
        }
        String text = getText();
        List<TokenPreview> resolved = previewsFor(text);
        boolean inside = LostTalesUiHitBox.contains(mouseX, mouseY, this.xPosition,
                this.yPosition, getWidth(), this.fieldHeight);
        if (resolved.isEmpty() || !isStyled() || !inside || button != 0
                || !isFocused()) {
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
