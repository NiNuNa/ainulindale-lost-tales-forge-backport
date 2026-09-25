package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * A menu in a small window of its own: the rows a control or the pointer
 * opens — a tab's settings behind the tool strip's cog, the closed
 * channels and conversations behind the {@code +}, a message's or a
 * person's actions.
 * Each kind of menu has one window. The menu lays its rows out in the
 * window's content box, draws them, hit tests them and scrolls them;
 * what the rows are, what they are about and what they do is
 * {@link ChatScreenMenus}' business, which hands the menu new rows
 * whenever what they say has changed. A list may carry <em>header</em>
 * rows — a section label over a hairline, never taken — and a list longer
 * than its window scrolls by the wheel, a honey hairline on an edge
 * saying more lies past it.
 *
 * <p>A menu may hold a field above its rows, which takes what is typed
 * while its window is in front and until a row is taken: a search that
 * narrows the rows, or a note or a line to be kept. The menu holds the
 * text and draws the field; which rows a filter leaves is the screen's
 * menus' business.</p>
 */
final class ChatMenu extends ChatSmallWindowContent {
    static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /**
     * A row tall enough for the mod's key icons at their own size, as the
     * settings' rows are, since some of them show a shortcut's keys.
     */
    static final int TALL_ROW_HEIGHT =
            LostTalesInputIconRenderer.BASE_ICON_HEIGHT + 2;
    /** Rows a window opens with at most; a longer list scrolls behind them. */
    static final int MAX_VISIBLE_ROWS = 12;
    /** Longest thing a field takes unless told otherwise; far past any tab's name. */
    static final int MAX_FILTER_LENGTH = 48;
    /** Seam between a shortcut's key icons and the + joining them. */
    private static final int KEY_GAP = 2;
    private static final int[] NO_KEYS = new int[0];
    private static final Object[] NO_PARTS = new Object[0];
    /** Clear room between a row's label and its value at the least. */
    private static final int VALUE_GAP = 8;
    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;
    /** Width of the colour chip before a palette entry's name. */
    private static final int CHIP_WIDTH = 7;
    /** The hairline that says the list continues past an edge. */
    private static final int MORE_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    /** The hairline under a section's name: nearly opaque, a firmer line than the field's. */
    private static final int HEADER_RULE_ALPHA = 0xE0;

    static final class Entry {
        final String id;
        final String label;
        /** A section label over a hairline; never hovered, never taken. */
        final boolean header;
        /** A display row that cannot be taken. */
        final boolean passive;
        /** Drawn italic: a muted channel, like its tab. */
        final boolean dim;
        /** The channel's colour, shown as a small bar before the name; -1 for none. */
        final int color;
        /**
         * Whether the colour is the row's subject rather than its
         * channel's mark: a palette entry shows it as a chip wide
         * enough to read as a colour, not as a hairline.
         */
        boolean chip;
        /** The tab whose icon stands before the name, or null for none. */
        final ChatTab icon;
        /** Player whose head stands before the name, or null for none. */
        ChatHeadOwner head;
        /** A sheet sprite before the name, or null. */
        LostTalesUiSheet sprite;
        /**
         * The sprite's lit artwork, or null for none: the sprite crosses
         * to it while the pointer is on the row, and rests on it while
         * the row is {@link #chosen}.
         */
        LostTalesUiSheet litSprite;
        /** Whether the row is the one chosen, which keeps its sprite lit. */
        boolean chosen;
        /** Whether the label's emoji shortcodes are drawn as their sprites. */
        boolean emojis;
        /**
         * The colour the label is drawn in; -1 for the menu's ivory. An
         * operator's action wears the Operator channel's crimson, so a
         * row that reaches beyond this player's own words is told apart
         * at a glance.
         */
        int labelColor = -1;
        /**
         * Why the row's action cannot be taken here, or empty for a row
         * that can: such a row stands in its place, muted, answers no
         * click, and says why under the pointer, so every menu of its kind
         * keeps the same rows.
         */
        String unavailable = "";
        /** What the row's setting reads now, at its right end; empty for none. */
        String value = "";
        /** A colour chip before the value, a colour setting's; -1 for none. */
        int valueChip = -1;
        /**
         * A shortcut's keys at the row's right end: a key code for each
         * key, drawn in the mod's key icons, and words between and after
         * them; empty for none.
         */
        Object[] keys = NO_PARTS;
        /**
         * A group's name over the rows after it — a channel's, its icon
         * before it, or a part of the chat's — in its own colour, at the
         * padding as a header is; never taken.
         */
        boolean group;

        Entry(String id, String label) {
            this(id, label, false, false, false, -1, null);
        }

        /** The same entry showing what its setting reads now. */
        Entry withValue(String value) {
            this.value = value == null ? "" : value;
            return this;
        }

        /** The same entry with a colour chip before its value. */
        Entry withValueChip(int rgb) {
            this.valueChip = rgb;
            return this;
        }

        /** The same entry showing a shortcut's keys; see {@link #keys}. */
        Entry withKeys(Object... keys) {
            this.keys = keys == null ? NO_PARTS : keys;
            return this;
        }

        /** The same entry, muted and closed for {@code reason}. */
        Entry unavailable(String reason) {
            this.unavailable = reason == null ? "" : reason;
            return this;
        }

        /** The same entry with its label in the given colour. */
        Entry withLabelColor(int color) {
            this.labelColor = color;
            return this;
        }

        /** The same entry showing its colour as a chip; a palette row. */
        Entry asChip() {
            this.chip = true;
            return this;
        }

        /** The same entry drawing its label's emojis, as a status line's row does. */
        Entry withEmojis() {
            this.emojis = true;
            return this;
        }

        Entry(String id, String label, boolean dim, int color, ChatTab icon) {
            this(id, label, false, false, dim, color, icon);
        }

        private Entry(String id, String label, boolean header,
                      boolean passive, boolean dim, int color, ChatTab icon) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.header = header;
            this.passive = passive;
            this.dim = dim;
            this.color = color;
            this.icon = icon;
        }

        /** A section label: {@code Channels}, {@code Direct Messages}. */
        static Entry header(String label) {
            return new Entry("", label, true, false, false, -1, null);
        }

        /** A display row that cannot be taken. */
        static Entry passive(String label) {
            return new Entry("", label, false, true, false, -1, null);
        }

        /** A group's name in {@code rgb}, a channel's {@code icon} before it or none. */
        static Entry group(String label, ChatTab icon, int rgb) {
            Entry entry = new Entry("", label, false, true, false, -1, icon);
            entry.group = true;
            entry.labelColor = rgb;
            return entry;
        }

        Entry withHead(UUID owner, String skinId) {
            this.head = new ChatHeadOwner(owner, skinId);
            return this;
        }

        Entry withSprite(LostTalesUiSheet sprite) {
            this.sprite = sprite;
            return this;
        }

        /**
         * The same entry with a sprite that lights: under the pointer,
         * and for as long as the row is {@code chosen}.
         */
        Entry withSprite(LostTalesUiSheet sprite, LostTalesUiSheet litSprite,
                         boolean chosen) {
            this.sprite = sprite;
            this.litSprite = litSprite;
            this.chosen = chosen;
            return this;
        }

        /** Whether a press on the row does something. */
        boolean isTakeable() {
            return !this.header && !this.passive
                    && this.unavailable.length() == 0;
        }
    }

    /**
     * Where a window opens from a control or the pointer: the box it
     * hangs from — a control's footprint, or the pointer's point — and
     * which way it grows from it. It opens toward the middle of the
     * window the control belongs to: below a control in the window's
     * upper half, above one in its lower half, lining up with the box's
     * left edge in the window's left half and its right edge in the right
     * half. Only when that side of the screen has less room than the
     * other does it turn round.
     */
    static final class Anchor {
        /** Clear space between a window's frame and the box it hangs from. */
        static final int GAP = 2;
        /** From the box it hangs from to the window's own box, its frame between them. */
        static final int REACH = GAP + LostTalesUiWindowFrame.WIDTH;
        final int left;
        final int top;
        final int right;
        final int bottom;
        /** Whether it opens below the box rather than above it. */
        final boolean below;
        /** Whether its right edge lines up with the box's, growing leftward. */
        final boolean fromRight;
        /** The chat window the box belongs to, whose room the menu opens in; null for the bare screen. */
        final String windowId;

        Anchor(int left, int top, int right, int bottom, boolean below,
               boolean fromRight, String windowId) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
            this.below = below;
            this.fromRight = fromRight;
            this.windowId = windowId;
        }

        /**
         * Opening from a box toward the middle of the space spanning
         * {@code spaceLeft}..{@code spaceRight} and
         * {@code spaceTop}..{@code spaceBottom}, in chat window
         * {@code windowId}.
         */
        static Anchor inward(int left, int top, int right, int bottom,
                             double spaceLeft, double spaceTop,
                             double spaceRight, double spaceBottom,
                             String windowId) {
            double middleX = (spaceLeft + spaceRight) / 2.0D;
            double middleY = (spaceTop + spaceBottom) / 2.0D;
            return new Anchor(left, top, right, bottom,
                    (top + bottom) / 2.0D < middleY,
                    (left + right) / 2.0D > middleX, windowId);
        }

        /**
         * Opening from a box toward the middle of a chat window, in that
         * window; with none drawn, toward the middle of the bare screen.
         */
        static Anchor inward(int left, int top, int right, int bottom,
                             ChatWindowFrame frame, int screenWidth,
                             int screenHeight) {
            if (frame == null || !frame.drawn) {
                return inward(left, top, right, bottom, 0.0D, 0.0D,
                        screenWidth, screenHeight, null);
            }
            double windowLeft = frame.drawnLeft();
            return inward(left, top, right, bottom, windowLeft,
                    frame.boxTop + frame.motionY,
                    windowLeft + (frame.boxRight - frame.boxLeft),
                    frame.boxBottom + frame.motionY, frame.windowId);
        }
    }

    /** The head a row wears: an account's, or a character skin's. */
    static final class ChatHeadOwner {
        final UUID owner;
        final String skinId;

        ChatHeadOwner(UUID owner, String skinId) {
            this.owner = owner;
            this.skinId = skinId == null ? "" : skinId;
        }
    }

    /** Where the field and the rows stand in a content box, in whole pixels. */
    private static final class Layout {
        final int left;
        final int top;
        final int width;
        final int height;
        final int rowsTop;
        final int rowsBottom;
        final int rowHeight;

        Layout(LostTalesUiHitBox box, int fieldHeight, int rowHeight) {
            this.left = (int)Math.floor(box.left);
            this.top = (int)Math.floor(box.top);
            this.width = (int)Math.floor(box.width);
            this.height = (int)Math.floor(box.height);
            this.rowsTop = this.top + PADDING_Y + fieldHeight;
            this.rowsBottom = Math.max(this.rowsTop,
                    this.top + this.height - PADDING_Y);
            this.rowHeight = rowHeight;
        }

        /** Rows the box shows, the last of them perhaps in part. */
        double shownRows() {
            return (this.rowsBottom - this.rowsTop) / (double)this.rowHeight;
        }
    }

    final ChatSmallWindowKind kind;
    /**
     * What the menu is about, {@link ChatScreenMenus}' own: the message,
     * the person, the tab or the window it was opened for; null for a
     * menu about nothing in particular.
     */
    Object about;
    private String title = "";
    private LostTalesUiSheet icon;
    private List<Entry> entries = Collections.emptyList();
    /** How tall a row is: a menu's own, or {@link #TALL_ROW_HEIGHT}. */
    private int rowHeight = ROW_HEIGHT;
    /** Width of the colour column: a bar, or a chip when a row is a colour. */
    private int swatchWidth = SWATCH_WIDTH;
    /** Left edge of the labels inside the box, past any swatch column. */
    private int labelX = PADDING_X;
    /** First row asked for — the wheel's target; rows above it lie past the top edge. */
    private double scrollRows;
    /**
     * The row offset the list is drawn at, easing toward
     * {@link #scrollRows} with the chat's shared scroll motion so a
     * wheel turn glides the rows instead of jumping them. Hit testing
     * reads this too, so it always answers for what is on screen.
     */
    private double renderedScrollRows;
    private long scrollNanos;
    /**
     * The field above the rows — the chat's one text field, caret,
     * selection and clipboard and all — or null for a menu without one.
     * It holds the keys from its window coming in front until a row is
     * taken.
     */
    private ChatInputField field;
    /** What the empty field reads while nothing has been typed. */
    private String filterPrompt = "";
    /** The icon the field opens with: the magnifier for a search. */
    private LostTalesUiSheet fieldIcon = LostTalesUiSheet.SEARCH;
    /** The keys of the shortcut shown beside it, or empty for none. */
    private int[] filterHint = NO_KEYS;
    /** Whether the field shows its emoji, and offers the emoji list: a status line's does. */
    private boolean fieldShowsEmoji;
    /** The emoji list the field opens while a {@code :name} is typed at its caret. */
    private final ChatEmojiSuggestionBox emojiList = new ChatEmojiSuggestionBox();
    /** Where the field's row stood as it was last drawn: the emoji list hangs above it. */
    private int fieldRowLeft;
    private int fieldRowTop;
    /**
     * How far each lighting row has crossed to its lit look, by the row's
     * id — a sprite to its lit artwork, a tab's name to the tab's colour
     * — so a list handed over again carries on from what is on screen;
     * and when the crossfades last stepped.
     */
    private final Map<String, Float> spriteFades = new HashMap<String, Float>();
    private final Map<String, Float> labelFades = new HashMap<String, Float>();
    private long spriteNanos;

    ChatMenu(ChatSmallWindowKind kind) {
        this.kind = kind;
    }

    /* ---- What the screen's menus hand it ---- */

    /** The name and the glyph on the window's strip. */
    void setTitle(String title, LostTalesUiSheet icon) {
        this.title = title == null ? "" : title;
        this.icon = icon;
    }

    /** How tall every row is: a menu's own, or taller to hold key icons. */
    void setRowHeight(int height) {
        this.rowHeight = Math.max(ROW_HEIGHT, height);
    }

    /**
     * Hands the menu its rows in place of the ones it had, the scroll and
     * each row's light carried on, so a list handed over again as it is
     * typed into, or as what it says changes, does not jump.
     */
    void setRows(List<Entry> rows) {
        this.entries = rows == null ? Collections.<Entry>emptyList()
                : new ArrayList<Entry>(rows);
        boolean swatches = false;
        boolean chips = false;
        boolean icons = false;
        for (Entry entry : this.entries) {
            if (entry.group) {
                continue;
            }
            swatches |= entry.color >= 0;
            chips |= entry.color >= 0 && entry.chip;
            icons |= entry.icon != null || entry.head != null
                    || entry.sprite != null;
        }
        // One swatch column and one icon column for the whole list, so
        // the names line up; headers and groups hang left of them with
        // the padding.
        this.swatchWidth = chips ? CHIP_WIDTH : SWATCH_WIDTH;
        this.labelX = PADDING_X + (swatches ? this.swatchWidth + SWATCH_GAP : 0)
                + (icons ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP : 0);
    }

    /** The rows, top first. */
    List<Entry> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    /** Starts the list over at its top with no row lit: the menu opened, or turned to something else. */
    void restart() {
        this.scrollRows = 0.0D;
        this.renderedScrollRows = 0.0D;
        this.spriteFades.clear();
        this.labelFades.clear();
        this.spriteNanos = 0L;
    }

    /**
     * Gives the menu a field above its rows with nothing typed in it: it
     * reads {@code prompt} while it is empty, opens with {@code icon},
     * shows {@code hint} — the keys of the shortcut that opens the menu,
     * drawn as the mod's own key icons — at its right end while it is
     * empty, and holds {@code limit} characters. A field that
     * {@code showsEmoji} draws a complete shortcode as its sprite, as a
     * status line is drawn once set; any other shows what is typed as it
     * is.
     */
    void openField(String prompt, int[] hint, LostTalesUiSheet icon,
                   int limit, boolean showsEmoji) {
        ChatInputField made = new ChatInputField(
                Minecraft.getMinecraft().fontRenderer, 0, 0, 1,
                LostTalesUiCaret.HEIGHT);
        made.setEnableBackgroundDrawing(false);
        made.setMaxStringLength(Math.max(1, limit));
        made.setFocused(false);
        this.field = showsEmoji ? made.emojiOnly() : made.plainText();
        this.fieldShowsEmoji = showsEmoji;
        this.filterPrompt = prompt == null ? "" : prompt;
        this.filterHint = hint == null ? NO_KEYS : hint;
        this.fieldIcon = icon == null ? LostTalesUiSheet.SEARCH : icon;
    }

    /** Takes the field away: the rows alone. */
    void closeField() {
        this.field = null;
        this.fieldShowsEmoji = false;
        this.filterPrompt = "";
        this.filterHint = NO_KEYS;
    }

    boolean hasField() {
        return this.field != null;
    }

    /** What has been typed into the field; empty when nothing has, or there is none. */
    String filter() {
        return this.field == null ? "" : this.field.getText();
    }

    /**
     * Puts {@code text} in the field, as far as it holds, the caret after
     * it: what a field for editing something opens with.
     */
    void setFilter(String text) {
        if (this.field == null) {
            return;
        }
        this.field.setText(text == null ? "" : text);
        this.field.setCursorPositionEnd();
    }

    /**
     * Offers a press to the field, as the input bar's field takes one —
     * typing, the caret's keys, selecting, the clipboard — and answers
     * whether it changed what the field holds.
     */
    boolean edit(LostTalesKeyPress press) {
        if (this.field == null) {
            return false;
        }
        String before = this.field.getText();
        this.field.textboxKeyTyped(press.character, press.key);
        refreshEmojiList();
        return !before.equals(this.field.getText());
    }

    /** Brings the emoji list up to date with the field: out while a {@code :name} stands at the caret. */
    private void refreshEmojiList() {
        if (this.field != null && this.fieldShowsEmoji
                && LostTalesConfig.enableChatEmojis) {
            this.emojiList.update(this.field.getText(),
                    this.field.getCursorPosition());
        } else {
            this.emojiList.update("", 0);
        }
    }

    /**
     * Offers a press to the field's emoji list while it is out, as the
     * bar's list takes one: Up and Down walk it, Tab and Enter take the
     * emoji. Answers whether the list took the press.
     */
    boolean serveList(LostTalesKeyPress press) {
        refreshEmojiList();
        if (!this.emojiList.isActive()) {
            return false;
        }
        if (press.is(Keyboard.KEY_UP) || press.is(Keyboard.KEY_DOWN)) {
            this.emojiList.moveSelection(press.is(Keyboard.KEY_UP) ? -1 : 1);
            return true;
        }
        if (press.is(Keyboard.KEY_TAB) || press.is(Keyboard.KEY_RETURN)
                || press.is(Keyboard.KEY_NUMPADENTER)) {
            takeEmoji(this.emojiList.getSelected());
            return true;
        }
        return false;
    }

    /** Takes the emoji on a row of the list, as a press on it does. */
    void takeListRow(int row) {
        takeEmoji(this.emojiList.at(row));
    }

    /**
     * The emoji in place of the {@code :name} typed at the caret, a space
     * after it, as the bar takes one from its list.
     */
    private void takeEmoji(ChatEmoji emoji) {
        ChatEmojiSuggester.Query query = this.emojiList.getQuery();
        if (emoji == null || query == null || this.field == null) {
            return;
        }
        String text = this.field.getText();
        int start = Math.max(0, Math.min(query.colonIndex, text.length()));
        int cursor = Math.max(start, Math.min(this.field.getCursorPosition(),
                text.length()));
        String replacement = emoji.getShortcode() + " ";
        this.field.setText(text.substring(0, start) + replacement
                + text.substring(cursor));
        this.field.setCursorPosition(Math.min(this.field.getText().length(),
                start + replacement.length()));
        refreshEmojiList();
    }

    @Override
    boolean dismissPopup() {
        refreshEmojiList();
        if (!this.emojiList.isActive()) {
            return false;
        }
        this.emojiList.dismiss();
        return true;
    }

    /** The emoji list, over everything, a clear pixel above the field's row. */
    @Override
    void drawPopups(Minecraft minecraft, ChatPointerRegions regions,
                    double pointerX, double pointerY) {
        refreshEmojiList();
        if (this.emojiList.isActive()) {
            this.emojiList.draw(minecraft, minecraft.fontRenderer, regions,
                    listAnchor(), this.fieldRowLeft, pointerX, pointerY);
        }
    }

    @Override
    ChatHover popupHoverAt(double x, double y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (!this.emojiList.isActive() || !this.emojiList.contains(font, x, y,
                listAnchor(), this.fieldRowLeft)) {
            return null;
        }
        ChatHover hover = new ChatHover(ChatHover.Kind.FIELD_SUGGESTION);
        hover.fieldSuggestion = this.emojiList.rowAt(font, x, y, listAnchor(),
                this.fieldRowLeft);
        return hover;
    }

    private int listAnchor() {
        return ChatEmojiSuggestionBox.anchorEndingAt(this.fieldRowTop - 1);
    }

    /**
     * A press on the field puts the caret where it landed; answers
     * whether the press was on it.
     */
    boolean pressField(double x, double y) {
        if (this.field == null || !this.field.isFocused()
                || !LostTalesUiHitBox.contains(x, y, this.field.xPosition,
                        this.field.yPosition - 2, this.field.width,
                        LostTalesChatOverlayRenderer.LINE_HEIGHT)) {
            return false;
        }
        this.field.mouseClicked((int)Math.floor(x), this.field.yPosition, 0);
        return true;
    }

    /**
     * The Ctrl presses a field keeps from the screen behind it, so none
     * of them edits the chat bar: Backspace and Delete, and the
     * clipboard's and selection's letters. Ctrl+Shift+A is the chat's own
     * and passes.
     */
    static boolean isFieldCommand(LostTalesKeyPress press) {
        return press.is(Keyboard.KEY_BACK) || press.is(Keyboard.KEY_DELETE)
                || press.is(Keyboard.KEY_C) || press.is(Keyboard.KEY_V)
                || press.is(Keyboard.KEY_X)
                || press.is(Keyboard.KEY_A) && !press.shift;
    }

    /* ---- The window ---- */

    /** The name the menu was given, else its kind's. */
    @Override
    String stripTitle() {
        return this.title.length() == 0 ? null : this.title;
    }

    @Override
    LostTalesUiSheet stripIcon() {
        return this.icon;
    }

    /**
     * Wide enough for every row whole — its label beside its swatch and
     * icon, and its value — the field's prompt and shortcut, and the
     * strip's name.
     */
    @Override
    int naturalWidth() {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int widest = MIN_WIDTH;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, rowWidth(minecraft, font, entry));
        }
        if (this.field != null) {
            // The field's prompt and the shortcut beside it are content
            // too: a list narrower than they are would cut them off.
            widest = Math.max(widest, PADDING_X + fieldIconRun()
                    + LostTalesUiCaret.WIDTH + 1
                    + font.getStringWidth(this.filterPrompt)
                    + SWATCH_GAP + hintWidth(minecraft) + PADDING_X);
        }
        String name = stripTitle();
        return Math.max(widest, ChatChannelTabBar.loneTabWidth(font,
                name != null ? name : this.kind.title(), this.icon != null));
    }

    /** The width a row takes whole: from the window's edge to its value's end. */
    private int rowWidth(Minecraft minecraft, FontRenderer font, Entry entry) {
        int label = entry.emojis ? ChatInlineText.width(font, entry.label, "")
                : font.getStringWidth(entry.label);
        if (entry.header) {
            return PADDING_X + label + PADDING_X;
        }
        if (entry.group) {
            return PADDING_X + (entry.icon != null
                    ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP : 0)
                    + label + PADDING_X;
        }
        int value = valueWidth(minecraft, font, entry);
        return this.labelX + label + (value > 0 ? VALUE_GAP + value : 0)
                + PADDING_X;
    }

    /** Every row up to {@link #MAX_VISIBLE_ROWS}, the field above them. */
    @Override
    int naturalHeight(int width) {
        return PADDING_Y * 2 + fieldHeight() + this.rowHeight * Math.max(1,
                Math.min(this.entries.size(), MAX_VISIBLE_ROWS));
    }

    @Override
    int minWidth() {
        return MIN_WIDTH;
    }

    /** One row under the field. */
    @Override
    int minHeight() {
        return PADDING_Y * 2 + fieldHeight() + this.rowHeight;
    }

    /**
     * The content box the menu's window first opens round in
     * {@code room}, hung from {@code anchor}: toward the middle of the
     * window the control belongs to, as many rows as the room on that
     * side holds up to {@link #MAX_VISIBLE_ROWS}, and on the other side
     * only where that holds more of them.
     */
    LostTalesUiHitBox firstContentBox(Anchor anchor, LostTalesUiHitBox room) {
        return firstContentBox(anchor, naturalWidth(), room);
    }

    /** As above for a window {@code width} wide. */
    LostTalesUiHitBox firstContentBox(Anchor anchor, int width,
                                      LostTalesUiHitBox room) {
        int roomLeft = (int)Math.ceil(room.left);
        int roomTop = (int)Math.ceil(room.top);
        int roomRight = (int)Math.floor(room.left + room.width);
        int roomBottom = (int)Math.floor(room.top + room.height);
        int frame = ChatSmallWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int roomBelow = rowsIn(roomBottom - anchor.bottom - Anchor.REACH
                - frame);
        int roomAbove = rowsIn(anchor.top - Anchor.REACH - roomTop - frame);
        boolean below = anchor.below
                ? roomBelow >= wanted || roomBelow >= roomAbove
                : !(roomAbove >= wanted || roomAbove >= roomBelow);
        int rows = Math.max(1, Math.min(wanted,
                below ? roomBelow : roomAbove));
        int height = frame + rows * this.rowHeight;
        int left = Math.max(roomLeft, Math.min(roomRight - width,
                anchor.fromRight ? anchor.right - width : anchor.left));
        int top = Math.max(roomTop, Math.min(roomBottom - height,
                below ? anchor.bottom + Anchor.REACH
                        : anchor.top - Anchor.REACH - height));
        return new LostTalesUiHitBox(left,
                top + ChatSmallWindow.STRIP_HEIGHT, width,
                height - ChatSmallWindow.STRIP_HEIGHT);
    }

    /**
     * The content box the menu's window first opens round in
     * {@code room} beside {@code sibling}, the small window whose row
     * opened it: to its right with the gap windows keep, or to its left
     * where the right has no room, its top on the other's, as many rows
     * as the room below holds up to {@link #MAX_VISIBLE_ROWS}.
     */
    LostTalesUiHitBox firstContentBoxBeside(LostTalesUiHitBox sibling,
                                            LostTalesUiHitBox room) {
        int width = naturalWidth();
        double roomRight = room.left + room.width;
        double roomBottom = room.top + room.height;
        int gap = ChatWindowPlacement.WINDOW_GAP;
        int frame = ChatSmallWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        double right = sibling.left + sibling.width + gap;
        double left = right + width <= roomRight ? right
                : sibling.left - gap - width;
        left = Math.max(room.left, Math.min(roomRight - width, left));
        double top = Math.max(room.top, sibling.top);
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(roomBottom - top) - frame)));
        int height = frame + rows * this.rowHeight;
        top = Math.max(room.top, Math.min(roomBottom - height, top));
        return new LostTalesUiHitBox(left,
                top + ChatSmallWindow.STRIP_HEIGHT, width,
                height - ChatSmallWindow.STRIP_HEIGHT);
    }

    /**
     * The content box the menu's window first opens round in the middle
     * of {@code room}, as many rows as the room holds up to
     * {@link #MAX_VISIBLE_ROWS}: what a keyboard shortcut opens where
     * no control stands to hang it from.
     */
    LostTalesUiHitBox firstContentBoxCentred(LostTalesUiHitBox room) {
        int width = naturalWidth();
        int frame = ChatSmallWindow.STRIP_HEIGHT + PADDING_Y * 2
                + fieldHeight();
        int wanted = Math.max(1, Math.min(this.entries.size(),
                MAX_VISIBLE_ROWS));
        int rows = Math.max(1, Math.min(wanted,
                rowsIn((int)Math.floor(room.height) - frame)));
        int height = frame + rows * this.rowHeight;
        double left = room.left + Math.max(0, LostTalesUiInk.centredStart(
                (int)Math.floor(room.width), width));
        double top = room.top + Math.max(0, LostTalesUiInk.centredStart(
                (int)Math.floor(room.height), height));
        return new LostTalesUiHitBox(left,
                top + ChatSmallWindow.STRIP_HEIGHT, width,
                height - ChatSmallWindow.STRIP_HEIGHT);
    }

    /** Whole rows in {@code pixels}. */
    private int rowsIn(int pixels) {
        return Math.max(0, pixels / this.rowHeight);
    }

    /**
     * Room the field takes above the rows; none without one. As tall as
     * the key icons it carries, so they are drawn at their own size
     * rather than shrunk into a text row.
     */
    private int fieldHeight() {
        return this.field == null ? 0
                : Math.max(ROW_HEIGHT,
                        LostTalesInputIconRenderer.BASE_ICON_HEIGHT) + 1;
    }

    /** The field's icon and the gap after it. */
    private int fieldIconRun() {
        return this.fieldIcon.getWidth() + ChatChannelIcons.GAP;
    }

    @Override
    ChatHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        Layout at = layOut(box);
        clampScroll(at);
        int index = rowIndexAt(at, x, y);
        Entry row = index < 0 ? null : this.entries.get(index);
        boolean takes = row != null && row.isTakeable();
        ChatHover hover = new ChatHover(takes ? ChatHover.Kind.MENU_ENTRY
                : ChatHover.Kind.MENU);
        hover.menuEntry = takes ? row : null;
        hover.menuTip = row == null ? "" : row.unavailable;
        return hover;
    }

    /**
     * Moves the list's target by whole rows; beyond either end it stays
     * put. The drawn rows glide after the target.
     */
    @Override
    void scrollBy(int lines) {
        this.scrollRows += ChatWheelStep.menuRows(lines);
    }

    @Override
    boolean holdsKeys() {
        return this.field != null && this.field.isFocused();
    }

    /** The field takes the keys as its window comes in front, the caret lit at once. */
    @Override
    void takeKeys() {
        if (this.field != null) {
            this.field.setFocused(true);
        }
    }

    @Override
    void releaseKeys() {
        if (this.field != null) {
            this.field.setFocused(false);
        }
    }

    @Override
    void closed() {
        releaseKeys();
    }

    /** The menu itself comes back with the chat: what it is about, its field and its place in the list. */
    @Override
    Object sessionState() {
        return this;
    }

    /* ---- Drawing ---- */

    /**
     * The rows under the field, the hovered one lit in the window's
     * surface, clipped to the band they glide in.
     */
    @Override
    void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
              double clipY, double pointerX, double pointerY, int alpha,
              int surfaceAlpha) {
        FontRenderer font = minecraft.fontRenderer;
        Layout at = layOut(box);
        clampScroll(at);
        advanceScrollEasing();
        long now = System.nanoTime();
        double elapsed = this.spriteNanos == 0L ? 0.0D
                : (now - this.spriteNanos) / 1.0E9D;
        this.spriteNanos = now;
        int hoveredIndex = rowIndexAt(at, pointerX, pointerY);
        if (hoveredIndex >= 0
                && !this.entries.get(hoveredIndex).isTakeable()) {
            hoveredIndex = -1;
        }
        // Rows are laid out from the drawn offset — whole rows pick where
        // the list starts, the fraction slides it — and clipped to the
        // band they glide in; one extra row fills the gap the slide opens.
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int firstY = firstRowY(at, firstRow);
        // The hovered row is the window's surface recoloured, cut to the
        // band, before anything lands on it.
        if (hoveredIndex >= 0) {
            int litTop = firstY + (hoveredIndex - firstRow) * this.rowHeight;
            LostTalesChatOverlayRenderer.recolourSurface(at.left,
                    Math.max(at.rowsTop, litTop), at.left + at.width,
                    Math.min(at.rowsBottom, litTop + this.rowHeight),
                    surfaceAlpha, LostTalesChatVisualStyle.SURFACE_RGB,
                    LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB);
        }
        drawField(minecraft, font, at, alpha);
        int last = Math.min(this.entries.size(),
                firstRow + (int)Math.ceil(at.shownRows()) + 1);
        boolean clipped = beginClip(minecraft, clipX + (at.left - box.left),
                clipY + (at.rowsTop - box.top), at.width,
                at.rowsBottom - at.rowsTop);
        try {
            int rowY = firstY;
            for (int index = Math.max(0, firstRow); index < last; index++) {
                drawRow(minecraft, font, at, this.entries.get(index), rowY,
                        index == hoveredIndex, elapsed, alpha);
                rowY += this.rowHeight;
            }
        } finally {
            endClip(clipped);
        }
        // A hairline on an edge the list continues past.
        int more = LostTalesChatVisualStyle.argb(MORE_RGB, alpha);
        if (this.scrollRows > 0.0D) {
            Gui.drawRect(at.left + 1, at.top + 1, at.left + at.width - 1,
                    at.top + 2, more);
        }
        if (this.scrollRows < maxScroll(at) - 0.01D) {
            Gui.drawRect(at.left + 1, at.top + at.height - 2,
                    at.left + at.width - 1, at.top + at.height - 1, more);
        }
    }

    /**
     * One row at {@code rowY}, its words on the row's middle. Text at the
     * window's opacity always; a muted channel is italic, the hovered row
     * is told by its light, and a row naming a tab takes the tab's colour
     * as it lights, as the tab's own name does. A row's value stands at
     * its right end, and its label gives way before it.
     */
    private void drawRow(Minecraft minecraft, FontRenderer font, Layout at,
                         Entry entry, int rowY, boolean hovered,
                         double elapsed, int alpha) {
        int labelTop = rowY + LostTalesUiInk.centredStart(this.rowHeight,
                LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT);
        if (entry.header) {
            // The section's name over a hairline, in the sand the
            // timestamps wear, so it reads as a label, not a row.
            LostTalesChatVisualStyle.drawColored(font, trimmed(font,
                            entry.label, at.width - PADDING_X * 2),
                    at.left + PADDING_X, rowY + this.rowHeight - 10,
                    LostTalesColors.rgb(LostTalesColors.SAND), alpha);
            Gui.drawRect(at.left + PADDING_X, rowY + this.rowHeight - 1,
                    at.left + at.width - PADDING_X, rowY + this.rowHeight,
                    LostTalesChatVisualStyle.argb(
                            LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                            Math.round(HEADER_RULE_ALPHA * alpha / 255.0F)));
            return;
        }
        if (entry.group) {
            drawGroup(minecraft, font, at, entry, labelTop, alpha);
            return;
        }
        if (entry.color >= 0) {
            // The channel's colour as a one-pixel upright bar the height
            // of the row's text; a palette row's as a chip the width of
            // the column.
            Gui.drawRect(at.left + PADDING_X, rowY + 1,
                    at.left + PADDING_X
                            + (entry.chip ? this.swatchWidth : SWATCH_WIDTH),
                    rowY + this.rowHeight - 1,
                    LostTalesChatVisualStyle.argb(entry.color, alpha));
        }
        drawRowIcon(minecraft, at, entry, labelTop, hovered, elapsed, alpha);
        int labelRgb = entry.unavailable.length() > 0
                ? LostTalesChatVisualStyle.asideRgb()
                : entry.labelColor >= 0 ? entry.labelColor
                : LostTalesChatVisualStyle.IVORY;
        if (entry.icon != null) {
            labelRgb = LostTalesChatVisualStyle.blend(labelRgb,
                    ClientChatChannelState.displayColor(entry.icon),
                    labelFade(entry, hovered, elapsed));
        }
        int labelLeft = at.left + this.labelX;
        int right = at.left + at.width - PADDING_X;
        int value = valueWidth(minecraft, font, entry);
        if (value > 0) {
            drawValue(minecraft, font, entry, right - value, rowY, labelTop,
                    alpha);
            right -= value + VALUE_GAP;
        }
        if (entry.emojis) {
            // Emoji cannot be cut by the letter; the clip cuts a line
            // longer than the window at its edge.
            ChatInlineText.draw(minecraft, font, entry.label,
                    entry.dim ? "§o" : "", labelLeft, labelTop, labelRgb,
                    alpha);
        } else {
            String label = trimmed(font, entry.label, right - labelLeft);
            LostTalesChatVisualStyle.drawColored(font,
                    entry.dim ? "§o" + label : label, labelLeft, labelTop,
                    labelRgb, alpha);
        }
    }

    /**
     * A group's name at the padding, as a header's is, a channel's icon
     * before it, in the group's own colour — sand for a part of the chat.
     */
    private void drawGroup(Minecraft minecraft, FontRenderer font, Layout at,
                           Entry entry, int labelTop, int alpha) {
        int x = at.left + PADDING_X;
        if (entry.icon != null) {
            ChatChannelIcons.draw(minecraft, entry.icon, x,
                    labelTop + ChatInlineIcons.CONTENT_TOP_OFFSET, alpha,
                    ChatIconMark.of(entry.icon));
            x += ChatChannelIcons.SLOT + ChatChannelIcons.GAP;
        }
        LostTalesChatVisualStyle.drawColored(font, trimmed(font, entry.label,
                        at.left + at.width - PADDING_X - x), x, labelTop,
                entry.labelColor >= 0 ? entry.labelColor
                        : LostTalesColors.rgb(LostTalesColors.SAND), alpha);
    }

    /** How wide a row's value is: its chip, its words and its keys. */
    private static int valueWidth(Minecraft minecraft, FontRenderer font,
                                  Entry entry) {
        int width = 0;
        if (entry.valueChip >= 0) {
            width += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        width += font.getStringWidth(entry.value);
        for (Object part : entry.keys) {
            width += part instanceof Integer
                    ? LostTalesInputIconRenderer.measureInput(minecraft,
                            LostTalesInputBinding.Type.KEYBOARD,
                            ((Integer)part).intValue(), 1.0F)
                    : KEY_GAP + font.getStringWidth(String.valueOf(part))
                            + KEY_GAP;
        }
        return width;
    }

    /**
     * A row's value from {@code x}: a colour's chip, a square on the
     * words' capitals, then the words in the chat's aside tone, then a
     * shortcut's keys in the mod's own key icons on the row's middle,
     * words between them a seam's width clear.
     */
    private void drawValue(Minecraft minecraft, FontRenderer font,
                           Entry entry, int x, int rowY, int labelTop,
                           int alpha) {
        int aside = LostTalesChatVisualStyle.asideRgb();
        if (entry.valueChip >= 0) {
            Gui.drawRect(x, labelTop, x + CHIP_WIDTH,
                    labelTop + LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT,
                    LostTalesChatVisualStyle.argb(entry.valueChip, alpha));
            x += CHIP_WIDTH + (entry.value.length() > 0 ? SWATCH_GAP : 0);
        }
        if (entry.value.length() > 0) {
            LostTalesChatVisualStyle.drawColored(font, entry.value, x,
                    labelTop, aside, alpha);
            x += font.getStringWidth(entry.value);
        }
        int keyY = rowY + LostTalesUiInk.centredStart(this.rowHeight,
                LostTalesInputIconRenderer.BASE_ICON_HEIGHT);
        for (Object part : entry.keys) {
            if (part instanceof Integer) {
                LostTalesChatVisualStyle.beginContent();
                x += LostTalesInputIconRenderer.drawInput(minecraft,
                        LostTalesInputBinding.Type.KEYBOARD,
                        ((Integer)part).intValue(), x, keyY, 1.0F,
                        alpha / 255.0F);
            } else {
                String word = String.valueOf(part);
                x += KEY_GAP;
                LostTalesChatVisualStyle.drawColored(font, word, x, labelTop,
                        aside, alpha);
                x += font.getStringWidth(word) + KEY_GAP;
            }
        }
    }

    /**
     * An icon or a head beside the label as it stands in a message row:
     * centred on the label's capitals by the chat's one rule, on whole
     * pixels, since both are pixel art. A row naming a tab wears the
     * tab's own icon, its unread mark in its corner, as the tab does.
     */
    private void drawRowIcon(Minecraft minecraft, Layout at, Entry entry,
                             int labelTop, boolean hovered, double elapsed,
                             int alpha) {
        int iconX = at.left + this.labelX - ChatChannelIcons.SLOT
                - ChatChannelIcons.GAP;
        if (entry.icon != null) {
            ChatChannelIcons.draw(minecraft, entry.icon, iconX,
                    labelTop + ChatInlineIcons.CONTENT_TOP_OFFSET, alpha,
                    ChatIconMark.of(entry.icon));
        } else if (entry.head != null) {
            // A head drawn as the tabs draw theirs: eight pixels, centred
            // across the icon's box, fading as one picture.
            final Minecraft game = minecraft;
            final ChatHeadOwner head = entry.head;
            final float headX = iconX + 1.0F;
            final float headY = labelTop
                    + LostTalesChatOverlayRenderer.HEAD_TOP_OFFSET;
            final float opacity = alpha / 255.0F;
            LostTalesUiFlatLayers.draw(alpha, headX - 1.0F, headY - 1.0F,
                    headX + 10.0F, headY + 10.0F,
                    new LostTalesUiFlatLayers.Layers() {
                        @Override
                        public void draw() {
                            if (head.skinId.length() == 0) {
                                LostTalesCharacterHeadIconRenderer
                                        .drawAccountHead(game, head.owner,
                                                headX, headY, 8.0F, 1.0F,
                                                opacity);
                            } else {
                                LostTalesCharacterHeadIconRenderer
                                        .drawSnapshotHead(game, head.owner,
                                                head.skinId, headX, headY,
                                                8.0F, 1.0F, opacity);
                            }
                        }
                    });
        } else if (entry.sprite != null) {
            // Centred in the icon column and on the label's capitals, the
            // odd pixel left and up.
            int spriteX = iconX + LostTalesUiInk.centredStart(
                    ChatChannelIcons.SIZE, entry.sprite.getWidth());
            int spriteY = labelTop + Math.floorDiv(
                    LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT
                            - entry.sprite.getHeight(), 2);
            LostTalesChatVisualStyle.beginContent();
            if (entry.litSprite == null) {
                entry.sprite.drawWithShadow(spriteX, spriteY, alpha);
            } else {
                LostTalesUiSheet.drawPairWithShadow(entry.sprite,
                        entry.litSprite, spriteFade(entry, hovered, elapsed),
                        spriteX, spriteY, alpha);
            }
        }
    }

    /**
     * The field above the rows: the icon it opens with; what has been
     * typed, or while it is empty the prompt in the chat's aside tone and
     * italics, as the input bar's hint is; the shortcut that opens the
     * menu at the right end while it is empty and there is room; and the
     * chat's caret blinking after the text while the field holds the
     * keys. A hairline under it parts it from the rows.
     */
    private void drawField(Minecraft minecraft, FontRenderer font, Layout at,
                           int alpha) {
        if (this.field == null) {
            return;
        }
        int top = at.top + PADDING_Y;
        int height = fieldHeight();
        int right = at.left + at.width - PADDING_X;
        this.fieldRowLeft = at.left + PADDING_X;
        this.fieldRowTop = top;
        int textY = top + LostTalesUiInk.centredStart(height - 1,
                LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT);
        // The icon stands on the capitals of what is typed beside it, as
        // every icon in a chat row does.
        LostTalesChatVisualStyle.beginContent();
        this.fieldIcon.drawWithShadow(at.left + PADDING_X,
                textY + LostTalesChatOverlayRenderer.centredBoxTop(
                        this.fieldIcon.getHeight()), alpha);
        int textX = at.left + PADDING_X + fieldIconRun();
        boolean empty = this.field.getText().length() == 0;
        int hintWidth = empty ? hintWidth(minecraft) : 0;
        int promptX = textX + LostTalesUiCaret.WIDTH + 1;
        boolean hinted = hintWidth > 0
                && right - hintWidth - SWATCH_GAP > promptX;
        if (empty) {
            // A pixel clear of the caret waiting at the field's start.
            String prompt = trimmed(font, this.filterPrompt, right - promptX
                    - (hinted ? hintWidth + SWATCH_GAP : 0));
            LostTalesChatVisualStyle.drawColored(font, "§o" + prompt,
                    promptX, textY, LostTalesChatVisualStyle.asideRgb(),
                    alpha);
        }
        if (hinted) {
            drawHint(minecraft, font, right - hintWidth, top, height, alpha);
        }
        // The field itself, the input bar's own, at the window's fade: it
        // scrolls to its caret as the bar's does.
        this.field.xPosition = textX;
        this.field.yPosition = textY;
        this.field.width = Math.max(1, right - textX - LostTalesUiCaret.WIDTH);
        ChatInputBar.beginFade(alpha / 255.0F);
        try {
            this.field.drawTextBox();
        } finally {
            ChatInputBar.endFade();
        }
        Gui.drawRect(at.left + PADDING_X, top + height - 1, right,
                top + height, LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 0xA0)));
    }

    /**
     * The shortcut as the keys themselves, in the mod's own key icons,
     * which press and spring back as the keys are held, a {@code +}
     * between each pair.
     */
    private void drawHint(Minecraft minecraft, FontRenderer font, int keyX,
                          int top, int height, int alpha) {
        int keyY = top + (height - 1
                - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
        String joiner = keyJoiner();
        int joinerY = keyY + LostTalesUiInk.centredStart(
                LostTalesInputIconRenderer.BASE_ICON_HEIGHT,
                LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT);
        int quiet = LostTalesColors.rgb(LostTalesColors.SAND);
        LostTalesChatVisualStyle.beginContent();
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                keyX += KEY_GAP;
                LostTalesChatVisualStyle.drawColored(font, joiner, keyX,
                        joinerY, quiet, alpha);
                keyX += font.getStringWidth(joiner) + KEY_GAP;
                LostTalesChatVisualStyle.beginContent();
            }
            keyX += LostTalesInputIconRenderer.drawInput(minecraft,
                    LostTalesInputBinding.Type.KEYBOARD,
                    this.filterHint[index], keyX, keyY, 1.0F, alpha / 255.0F);
        }
    }

    /**
     * Room the shortcut takes: its key icons at their own size, with a
     * {@code +} between each pair of them.
     */
    private int hintWidth(Minecraft minecraft) {
        int width = 0;
        for (int index = 0; index < this.filterHint.length; index++) {
            if (index > 0) {
                width += KEY_GAP + minecraft.fontRenderer.getStringWidth(
                        keyJoiner()) + KEY_GAP;
            }
            width += LostTalesInputIconRenderer.measureInput(minecraft,
                    LostTalesInputBinding.Type.KEYBOARD,
                    this.filterHint[index], 1.0F);
        }
        return width;
    }

    /**
     * What stands between two keys of a shortcut, saying they are held
     * together rather than pressed in turn. The same glyph the quick
     * loot hints join their keys with, from the one place it is named.
     */
    private static String keyJoiner() {
        return StatCollector.translateToLocal("quickLootHud.losttales.plus");
    }

    /** {@code text} cut to {@code width}, whole when it fits. */
    private static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }

    /* ---- Scrolling and hit testing ---- */

    /** The furthest the list scrolls in {@code at}: its last row whole at the bottom. */
    private double maxScroll(Layout at) {
        return Math.max(0.0D, this.entries.size() - at.shownRows());
    }

    /** Keeps both the target and the drawn offset within the list as the box shows it. */
    private void clampScroll(Layout at) {
        double max = maxScroll(at);
        this.scrollRows = Math.max(0.0D, Math.min(max, this.scrollRows));
        this.renderedScrollRows = Math.max(0.0D,
                Math.min(max, this.renderedScrollRows));
    }

    /**
     * Advances the drawn offset toward its target, once per drawn frame;
     * with chat animations off it simply arrives.
     */
    private void advanceScrollEasing() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (Math.abs(this.scrollRows - this.renderedScrollRows) <= 0.01D) {
            this.renderedScrollRows = this.scrollRows;
            return;
        }
        this.renderedScrollRows = Motions.followTravel(MotionIds.CHAT_SCROLL,
                this.renderedScrollRows, this.scrollRows, elapsed);
    }

    /** The field and the rows in a content box, at this menu's row height. */
    private Layout layOut(LostTalesUiHitBox box) {
        return new Layout(box, fieldHeight(), this.rowHeight);
    }

    /** Where the row {@code firstRow} is drawn: the fraction of the drawn offset slides it up. */
    private int firstRowY(Layout at, int firstRow) {
        return at.rowsTop - (int)Math.round(
                (this.renderedScrollRows - firstRow) * this.rowHeight);
    }

    /**
     * The index of the row drawn under the point, whatever kind it is, or
     * -1 for none: resolved against the drawn offset, as the rows are
     * drawn, so a gliding list answers for what is on screen.
     */
    private int rowIndexAt(Layout at, double x, double y) {
        if (!(x >= at.left && x < at.left + at.width && y >= at.rowsTop
                && y < at.rowsBottom)) {
            return -1;
        }
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int index = firstRow + (int)Math.floor(
                (y - firstRowY(at, firstRow)) / (double)this.rowHeight);
        return index >= 0 && index < this.entries.size() ? index : -1;
    }

    /**
     * One step of a lighting sprite's crossfade, toward its lit artwork
     * while its row is hovered or chosen. A row drawn for the first time
     * starts where it is headed, so a menu opening on the chosen row
     * shows its sprite lit instead of lighting it up.
     */
    private float spriteFade(Entry entry, boolean hovered, double elapsed) {
        return stepFade(this.spriteFades, entry, hovered || entry.chosen,
                elapsed);
    }

    /**
     * One step of the crossfade a row naming a tab takes its name to the
     * tab's colour on, while the row is hovered: the tab's own name does
     * the same under the pointer.
     */
    private float labelFade(Entry entry, boolean hovered, double elapsed) {
        return stepFade(this.labelFades, entry, hovered, elapsed);
    }

    /** One step of a row's crossfade in {@code fades}, a new row starting where it is headed. */
    private static float stepFade(Map<String, Float> fades, Entry entry,
                                  boolean lit, double elapsed) {
        Float kept = fades.get(entry.id);
        float fade = kept == null ? (lit ? 1.0F : 0.0F)
                : LostTalesChatVisualStyle.hoverFade(kept.floatValue(), lit,
                        elapsed);
        fades.put(entry.id, Float.valueOf(fade));
        return fade;
    }
}
