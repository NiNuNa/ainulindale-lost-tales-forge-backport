package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * A small vertical list of actions opened from a control or the pointer —
 * the channel settings behind a tab's cog, the closed channels and
 * open-able conversations behind the {@code +}, a message's or a
 * person's actions. One instance serves the whole chat screen: opening
 * it for another purpose replaces the previous list. Entries are plain
 * ids the screen interprets; the menu only lays them out, draws them,
 * hit tests them and registers its rectangle so nothing under it reacts.
 * A list may carry <em>header</em> rows — a section label over a
 * hairline, never clickable — and a list taller than
 * {@link #MAX_VISIBLE_ROWS} shows that many rows and scrolls by the
 * wheel, a honey hairline on an edge saying more lies past it. The list
 * opens <em>inwards</em> ({@link Anchor}): from the tab strip down into
 * its window, from the input bar up into it, from the pointer toward the
 * window's middle — so a window at the screen's edge, or filling the
 * screen, still shows its menus at full size.
 *
 * <p>A list may also be <em>searchable</em>: a field above its rows that
 * takes what is typed while it is open. The menu holds the text and
 * draws the field; which rows a filter leaves is the screen's business,
 * which hands the menu a new list whenever the text changes.</p>
 */
final class ChatPopupMenu {
    static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /** Rows shown at most; a longer list scrolls behind them. */
    static final int MAX_VISIBLE_ROWS = 12;
    /** Longest thing a search field takes; far past any tab's name. */
    private static final int MAX_FILTER_LENGTH = 48;
    /** Seam between a shortcut's key icons and the + joining them. */
    private static final int KEY_GAP = 2;
    private static final int[] NO_KEYS = new int[0];
    /**
     * Room the search field's magnifier takes before what is typed: the
     * glyph and the gap an icon keeps from its label in these lists.
     */


    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;
    /** Width of the colour chip before a palette entry's name. */
    private static final int CHIP_WIDTH = 7;
    /** The hairline that says the list continues past an edge. */
    private static final int MORE_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    static final class Entry {
        final String id;
        final String label;
        /** A section label over a hairline; never hovered, never clicked. */
        final boolean header;
        /** A display row that cannot be selected. */
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

        Entry(String id, String label) {
            this(id, label, false, false, false, -1, null);
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

        /** A display row that cannot be selected. */
        static Entry passive(String label) {
            return new Entry("", label, false, true, false, -1, null);
        }

        Entry withHead(java.util.UUID owner, String skinId) {
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

    }

    /**
     * Where a menu opens: the box it hangs from — a control's footprint,
     * or the pointer's point — and which way it grows from it. A menu
     * opens toward the middle of the window it belongs to: below a
     * control in the window's upper half, above one in its lower half,
     * lining up with the box's left edge in the window's left half and
     * its right edge in the right half. Only when that side of the screen
     * has less room than the other does it turn round.
     */
    static final class Anchor {
        /** Clear space between a menu and the box it hangs from. */
        static final int GAP = 2;
        final int left;
        final int top;
        final int right;
        final int bottom;
        /** Whether the menu opens below the box rather than above it. */
        final boolean below;
        /** Whether the menu's right edge lines up with the box's, growing leftward. */
        final boolean fromRight;

        Anchor(int left, int top, int right, int bottom, boolean below,
               boolean fromRight) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
            this.below = below;
            this.fromRight = fromRight;
        }

        /**
         * A menu opening from a box toward the middle of the window
         * spanning {@code windowLeft}..{@code windowRight} and
         * {@code windowTop}..{@code windowBottom}.
         */
        static Anchor inward(int left, int top, int right, int bottom,
                             double windowLeft, double windowTop,
                             double windowRight, double windowBottom) {
            double middleX = (windowLeft + windowRight) / 2.0D;
            double middleY = (windowTop + windowBottom) / 2.0D;
            return new Anchor(left, top, right, bottom,
                    (top + bottom) / 2.0D < middleY,
                    (left + right) / 2.0D > middleX);
        }

        /** A menu opening from a box toward the middle of a window's frame. */
        static Anchor inward(int left, int top, int right, int bottom,
                             ChatWindowFrame frame, int screenWidth,
                             int screenHeight) {
            if (frame == null || !frame.drawn) {
                return inward(left, top, right, bottom, 0.0D, 0.0D,
                        screenWidth, screenHeight);
            }
            double windowLeft = frame.drawnLeft();
            return inward(left, top, right, bottom, windowLeft,
                    frame.boxTop + frame.motionY,
                    windowLeft + (frame.boxRight - frame.boxLeft),
                    frame.boxBottom + frame.motionY);
        }
    }

    /** The head a row wears: an account's, or a character skin's. */
    static final class ChatHeadOwner {
        final java.util.UUID owner;
        final String skinId;

        ChatHeadOwner(java.util.UUID owner, String skinId) {
            this.owner = owner;
            this.skinId = skinId == null ? "" : skinId;
        }
    }

    private String kind = "";
    private ChatTab channel;
    private List<Entry> entries = Collections.emptyList();
    /** Where the open list hangs from; a re-open in place keeps it. */
    private Anchor anchor;
    private int x;
    private int y;
    private int width;
    /** Width of the colour column: a bar, or a chip when a row is a colour. */
    private int swatchWidth = SWATCH_WIDTH;
    private int height;
    /** Left edge of the labels inside the menu, past any swatch column. */
    private int labelX = PADDING_X;
    /** First row asked for — the wheel's target; rows above it lie past
     *  the top edge. */
    private double scrollRows;
    /**
     * The row offset the list is drawn at, easing toward
     * {@link #scrollRows} with the chat's shared scroll motion so a
     * wheel turn glides the rows instead of jumping them. Hit testing
     * reads this too, so it always answers for what is on screen.
     */
    private double renderedScrollRows;
    private long scrollNanos;
    /** Rows the menu has room for; the rest scroll. */
    private int visibleRows;
    /** What has been typed into a searchable list, or null for one that
     *  is not searchable. */
    private StringBuilder filter;
    /** Room the search field takes above the rows; 0 without one. */
    private int fieldHeight;
    /** What the empty field reads while nothing has been typed. */
    private String filterPrompt = "";
    /** The icon the field opens with: the magnifier for a search. */
    private LostTalesUiSheet fieldIcon = LostTalesUiSheet.SEARCH;
    /** The keys of the shortcut shown beside it, or empty for none. */
    private int[] filterHint = NO_KEYS;
    private long filterNanos;
    /**
     * How far each lighting row has crossed to its lit look, by the row's
     * id — a sprite to its lit artwork, a tab's name to the tab's colour
     * — so a list handed over again as it is typed into carries on from
     * what is on screen; and when the crossfades last stepped.
     */
    private final Map<String, Float> spriteFades = new HashMap<String, Float>();
    private final Map<String, Float> labelFades = new HashMap<String, Float>();
    private long spriteNanos;

    boolean isOpen() {
        return !this.entries.isEmpty();
    }

    /** Whether the open list takes what is typed. */
    boolean isSearchable() {
        return this.filter != null;
    }

    /** What has been typed into it; empty when nothing has. */
    String filter() {
        return this.filter == null ? "" : this.filter.toString();
    }

    /**
     * Offers a press to the field and answers whether it changed what
     * the field holds: a typed character while there is room, Backspace
     * (with Ctrl, back to the start of the word) and Ctrl+V.
     */
    boolean handleKeyTyped(LostTalesKeyPress press) {
        if (this.filter == null) {
            return false;
        }
        if (press.is(Keyboard.KEY_BACK)) {
            if (this.filter.length() == 0) {
                return false;
            }
            this.filter.setLength(press.command ? wordStart(this.filter)
                    : this.filter.length() - 1);
            this.filterNanos = System.nanoTime();
            return true;
        }
        String typed = press.types ? String.valueOf(press.character)
                : press.isCommand(Keyboard.KEY_V)
                        ? GuiScreen.getClipboardString() : "";
        String kept = ChatAllowedCharacters.filerAllowedCharacters(typed);
        int room = MAX_FILTER_LENGTH - this.filter.length();
        if (kept.length() == 0 || room <= 0) {
            return false;
        }
        this.filter.append(kept.length() > room ? kept.substring(0, room)
                : kept);
        this.filterNanos = System.nanoTime();
        return true;
    }

    /**
     * The Ctrl presses a field keeps from the screen behind it, so none
     * of them edits the chat bar the list covers: Backspace and Delete,
     * and the clipboard's and selection's letters. Ctrl+Shift+A is the
     * chat's own and passes.
     */
    static boolean isFieldCommand(LostTalesKeyPress press) {
        return press.is(Keyboard.KEY_BACK) || press.is(Keyboard.KEY_DELETE)
                || press.is(Keyboard.KEY_C) || press.is(Keyboard.KEY_V)
                || press.is(Keyboard.KEY_X)
                || press.is(Keyboard.KEY_A) && !press.shift;
    }

    /** Where the last word of {@code text} starts, the spaces after it counted in. */
    static int wordStart(CharSequence text) {
        int at = text.length();
        while (at > 0 && text.charAt(at - 1) == ' ') {
            at--;
        }
        while (at > 0 && text.charAt(at - 1) != ' ') {
            at--;
        }
        return at;
    }

    /**
     * Puts {@code text} in the open list's field, as far as the field
     * holds, as if it had been typed: what a field for editing something
     * opens with.
     */
    void setFilter(String text) {
        if (this.filter == null) {
            return;
        }
        this.filter.setLength(0);
        String kept = text == null ? "" : text;
        this.filter.append(kept.length() > MAX_FILTER_LENGTH
                ? kept.substring(0, MAX_FILTER_LENGTH) : kept);
        this.filterNanos = System.nanoTime();
    }

    /** Where the open list hangs from, for a list opened in its place. */
    Anchor anchor() {
        return this.anchor;
    }

    /** The open list's rows, top first. */
    List<Entry> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    String kind() {
        return this.kind;
    }

    ChatTab channel() {
        return this.channel;
    }

    /**
     * Opens the list from its {@code anchor}, shifted to stay inside the
     * screen. The scroll survives a re-open in place
     * ({@link #replaceEntries}), clamped to the new list.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, Anchor anchor, int screenWidth,
              int screenHeight) {
        open(kind, channel, entries, font, anchor, screenWidth, screenHeight,
                null, null);
    }

    /**
     * As above, with a search field above the rows: it reads
     * {@code searchPrompt} while it is empty and shows
     * {@code searchHint} — the keys of the shortcut that opens it,
     * drawn as the mod's own key icons — at its right end. What has
     * been typed survives a {@link #replaceEntries}, which is how the
     * screen narrows the list as it is typed into, and is dropped when
     * the menu next opens somewhere else.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, Anchor anchor, int screenWidth,
              int screenHeight, String searchPrompt, int[] searchHint) {
        open(kind, channel, entries, font, anchor, screenWidth, screenHeight,
                searchPrompt, searchHint, LostTalesUiSheet.SEARCH);
    }

    /**
     * As above, the field opening with {@code fieldIcon} in place of the
     * magnifier: a field that is typed into to say something rather than
     * to find it.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, Anchor anchor, int screenWidth,
              int screenHeight, String searchPrompt, int[] searchHint,
              LostTalesUiSheet fieldIcon) {
        if (entries == null || entries.isEmpty() || font == null
                || anchor == null) {
            close();
            return;
        }
        this.fieldIcon = fieldIcon == null ? LostTalesUiSheet.SEARCH
                : fieldIcon;
        this.anchor = anchor;
        boolean samePlace = !this.kind.equals("") && this.kind.equals(kind);
        if (searchPrompt == null) {
            this.filter = null;
            this.filterPrompt = "";
            this.filterHint = NO_KEYS;
        } else {
            if (this.filter == null || !samePlace) {
                this.filter = new StringBuilder();
                this.filterNanos = System.nanoTime();
            }
            this.filterPrompt = searchPrompt;
            this.filterHint = searchHint == null ? NO_KEYS : searchHint;
        }
        // The field is as tall as the key icons it carries, so they are
        // drawn at their own size rather than shrunk into a text row.
        this.fieldHeight = this.filter == null ? 0
                : Math.max(ROW_HEIGHT,
                        LostTalesInputIconRenderer.BASE_ICON_HEIGHT) + 1;
        double keptScroll = samePlace ? this.scrollRows : 0.0D;
        this.kind = kind == null ? "" : kind;
        this.channel = channel;
        this.entries = new ArrayList<Entry>(entries);
        int widest = 0;
        boolean swatches = false;
        boolean chips = false;
        boolean icons = false;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, entry.emojis
                    ? ChatInlineText.width(font, entry.label, "")
                    : font.getStringWidth(entry.label));
            swatches |= entry.color >= 0;
            chips |= entry.color >= 0 && entry.chip;
            icons |= entry.icon != null || entry.head != null
                    || entry.sprite != null;
        }
        // One swatch column and one icon column for the whole list, so
        // the names line up; headers hang left of them with the padding.
        this.swatchWidth = chips ? CHIP_WIDTH : SWATCH_WIDTH;
        this.labelX = PADDING_X + (swatches ? this.swatchWidth + SWATCH_GAP : 0)
                + (icons ? ChatChannelIcons.SLOT + ChatChannelIcons.GAP : 0);
        if (this.filter != null) {
            // The field's prompt and the shortcut beside it are content
            // too: a list narrower than they are would cut them off.
            widest = Math.max(widest, fieldIconRun()
                    + LostTalesUiCaret.WIDTH + 1
                    + font.getStringWidth(this.filterPrompt)
                    + SWATCH_GAP + hintWidth(Minecraft.getMinecraft()));
        }
        this.width = Math.max(MIN_WIDTH,
                this.labelX + widest + PADDING_X);
        // As many rows as the cap and the room on the anchor's side
        // allow; the other side only when it holds more of them.
        int wanted = Math.min(this.entries.size(), MAX_VISIBLE_ROWS);
        int roomBelow = rowsIn(screenHeight - anchor.bottom - Anchor.GAP);
        int roomAbove = rowsIn(anchor.top - Anchor.GAP);
        boolean below = anchor.below
                ? roomBelow >= wanted || roomBelow >= roomAbove
                : !(roomAbove >= wanted || roomAbove >= roomBelow);
        this.visibleRows = Math.max(1, Math.min(wanted,
                below ? roomBelow : roomAbove));
        this.height = PADDING_Y * 2 + this.fieldHeight
                + ROW_HEIGHT * this.visibleRows;
        this.x = Math.max(0, Math.min(screenWidth - this.width,
                anchor.fromRight ? anchor.right - this.width : anchor.left));
        this.y = Math.max(0, Math.min(screenHeight - this.height, below
                ? anchor.bottom + Anchor.GAP
                : anchor.top - Anchor.GAP - this.height));
        this.scrollRows = clampScroll(keptScroll);
        if (!samePlace) {
            // A fresh opening starts where it is asked; only wheel turns
            // on the open list glide.
            this.renderedScrollRows = this.scrollRows;
            this.spriteFades.clear();
            this.labelFades.clear();
        }
    }

    /** The field's icon and the gap after it. */
    private int fieldIconRun() {
        return this.fieldIcon.getWidth() + ChatChannelIcons.GAP;
    }

    /** How many rows a list has room for in {@code pixels} of height. */
    private int rowsIn(int pixels) {
        return Math.max(0, (pixels - PADDING_Y * 2 - this.fieldHeight)
                / ROW_HEIGHT);
    }

    /**
     * Swaps the entries of the open menu in place — after something
     * changed what a row should say, or who the rows are — keeping the
     * anchor and the scroll so the list does not jump under the pointer.
     */
    void replaceEntries(List<Entry> entries, FontRenderer font,
                        int screenWidth, int screenHeight) {
        open(this.kind, this.channel, entries, font, this.anchor,
                screenWidth, screenHeight,
                this.filter == null ? null : this.filterPrompt,
                this.filterHint);
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

    void close() {
        this.entries = Collections.emptyList();
        this.kind = "";
        this.channel = null;
        this.anchor = null;
        this.scrollRows = 0.0D;
        this.renderedScrollRows = 0.0D;
        this.visibleRows = 0;
        this.filter = null;
        this.filterPrompt = "";
        this.filterHint = NO_KEYS;
        this.spriteFades.clear();
        this.labelFades.clear();
        this.spriteNanos = 0L;
        this.fieldHeight = 0;
    }

    /**
     * The search field above the rows: the magnifier it opens with; what
     * has been typed, or while it is empty the prompt in the chat's aside
     * tone and italics, as the input bar's hint is; the shortcut that
     * opens the list at the right end; and the chat's caret blinking
     * after the text so it is plain the field is taking keys. A hairline
     * under it parts it from the rows.
     */
    private void drawSearchField(FontRenderer font) {
        if (this.filter == null) {
            return;
        }
        int top = this.y + PADDING_Y;
        int textY = top + LostTalesUiInk.centredStart(this.fieldHeight - 1,
                LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT);
        String typed = this.filter.toString();
        int quiet = LostTalesColors.rgb(LostTalesColors.SAND);
        // The icon stands on the capitals of what is typed beside it,
        // as every icon in a chat row does.
        this.fieldIcon.drawWithShadow(this.x + PADDING_X,
                textY + LostTalesChatOverlayRenderer.centredBoxTop(
                        this.fieldIcon.getHeight()), 255);
        int textX = this.x + PADDING_X + fieldIconRun();
        if (typed.length() == 0) {
            // A pixel clear of the caret waiting at the field's start.
            LostTalesChatVisualStyle.drawColored(font,
                    "§o" + this.filterPrompt, textX
                            + LostTalesUiCaret.WIDTH + 1, textY,
                    LostTalesChatVisualStyle.asideRgb(), 255);
        } else {
            LostTalesChatVisualStyle.drawPlain(font, typed, textX, textY,
                    255);
        }
        if (this.filterHint.length > 0 && typed.length() == 0) {
            // The shortcut as the keys themselves, in the mod's own key
            // icons, which press and spring back as the keys are held.
            Minecraft minecraft = Minecraft.getMinecraft();
            int keyX = this.x + this.width - PADDING_X
                    - hintWidth(minecraft);
            int keyY = top + (this.fieldHeight - 1
                    - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
            String joiner = keyJoiner();
            int joinerY = keyY + LostTalesUiInk.centredStart(
                    LostTalesInputIconRenderer.BASE_ICON_HEIGHT,
                    LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT);
            LostTalesChatVisualStyle.beginContent();
            for (int index = 0; index < this.filterHint.length; index++) {
                if (index > 0) {
                    keyX += KEY_GAP;
                    LostTalesChatVisualStyle.drawColored(font, joiner, keyX,
                            joinerY, quiet, 255);
                    keyX += font.getStringWidth(joiner) + KEY_GAP;
                    LostTalesChatVisualStyle.beginContent();
                }
                keyX += LostTalesInputIconRenderer.drawInput(minecraft,
                        LostTalesInputBinding.Type.KEYBOARD,
                        this.filterHint[index], keyX, keyY, 1.0F);
            }
        }
        if (LostTalesUiCaret.isLit(this.filterNanos, System.nanoTime())) {
            ChatInputField.drawCaret(textX + font.getStringWidth(typed),
                    textY);
        }
        Gui.drawRect(this.x + PADDING_X, top + this.fieldHeight - 1,
                this.x + this.width - PADDING_X, top + this.fieldHeight,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        0xA0));
    }

    boolean contains(double mouseX, double mouseY) {
        return isOpen() && LostTalesUiHitBox.contains(mouseX, mouseY, this.x, this.y,
                this.width, this.height);
    }

    /** Moves the list's target by whole rows; beyond either end it stays
     *  put. The drawn rows glide after the target. */
    void scrollBy(double rows) {
        this.scrollRows = clampScroll(this.scrollRows + rows);
    }

    private double clampScroll(double rows) {
        return Math.max(0.0D, Math.min(
                (double)(this.entries.size() - this.visibleRows), rows));
    }

    /**
     * Advances the drawn offset toward its target, once per drawn
     * frame; with chat animations off it simply arrives.
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

    /** The clickable entry under the point, resolved against the drawn
     *  offset so a gliding list answers for what is on screen; headers,
     *  display rows and the padding bands are nobody's. */
    Entry entryAt(double mouseX, double mouseY) {
        Entry entry = rowAt(mouseX, mouseY);
        return entry == null || entry.header || entry.passive
                || entry.unavailable.length() > 0 ? null : entry;
    }

    /** Why the row under the point cannot be taken, or empty for none. */
    String unavailableAt(double mouseX, double mouseY) {
        Entry entry = rowAt(mouseX, mouseY);
        return entry == null ? "" : entry.unavailable;
    }

    /** The entry drawn under the point, whatever kind it is, or null. */
    private Entry rowAt(double mouseX, double mouseY) {
        int index = rowIndexAt(mouseX, mouseY);
        return index < 0 ? null : this.entries.get(index);
    }

    /** The index of the entry drawn under the point, or -1 for none. */
    private int rowIndexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)
                || mouseY < this.y + PADDING_Y + this.fieldHeight
                || mouseY >= this.y + this.height - PADDING_Y) {
            return -1;
        }
        int index = (int)Math.floor(
                (mouseY - this.y - PADDING_Y - this.fieldHeight)
                / (double)ROW_HEIGHT + this.renderedScrollRows);
        return index >= 0 && index < this.entries.size() ? index : -1;
    }

    /**
     * Claims the menu's rectangle for this frame. Called before anything
     * under the menu asks who owns the pointer; the menu itself is drawn
     * last so it paints above everything.
     */
    void registerRegion(ChatPointerRegions regions) {
        if (isOpen()) {
            regions.addScreen(this.x, this.y, this.x + this.width,
                    this.y + this.height);
        }
    }

    /**
     * Draws the menu. {@code mouseX}/{@code mouseY} is the pointer while
     * the menu has it, else {@link ChatHover#AWAY}: the rows light by the
     * same hit test a press and the pointer's pose ask.
     */
    void draw(FontRenderer font, ChatPointerRegions regions,
              double mouseX, double mouseY) {
        if (!isOpen() || font == null) {
            return;
        }
        advanceScrollEasing();
        long now = System.nanoTime();
        double elapsed = this.spriteNanos == 0L ? 0.0D
                : (now - this.spriteNanos) / 1.0E9D;
        this.spriteNanos = now;
        Entry hovered = entryAt(mouseX, mouseY);
        // Rows are laid out from the drawn offset — whole rows pick where
        // the list starts, the fraction slides it — and clipped to the
        // menu's interior so the glide never paints over its frame; one
        // extra row fills the gap the slide opens.
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int rowY = this.y + PADDING_Y + this.fieldHeight - (int)Math.round(
                (this.renderedScrollRows - firstRow) * ROW_HEIGHT);
        // The popups' one surface, the hovered row lit in it rather than
        // over it and cut to the band the rows glide in.
        int rowsTop = this.y + PADDING_Y + this.fieldHeight;
        int rowsBottom = this.y + this.height - PADDING_Y;
        int litTop = hovered == null ? rowsTop : rowY
                + (this.entries.indexOf(hovered) - firstRow) * ROW_HEIGHT;
        LostTalesChatVisualStyle.drawPopup(this.x, this.y,
                this.x + this.width, this.y + this.height, 1.0F, this.x,
                Math.max(rowsTop, litTop), this.x + this.width,
                hovered == null ? rowsTop
                        : Math.min(rowsBottom, litTop + ROW_HEIGHT));
        int outline = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                LostTalesChatVisualStyle.POPUP_ALPHA);
        drawSearchField(font);
        int last = Math.min(this.entries.size(),
                firstRow + this.visibleRows + 1);
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(),
                this.y + PADDING_Y + this.fieldHeight - 1.0D,
                this.y + this.height - PADDING_Y + 1.0D, false);
        try {
        for (int index = Math.max(0, firstRow); index < last; index++) {
            Entry entry = this.entries.get(index);
            if (entry.header) {
                // The section's name over a hairline, in the sand the
                // timestamps wear, so it reads as a label, not a row.
                LostTalesChatVisualStyle.drawColored(font, entry.label,
                        this.x + PADDING_X, rowY + 1,
                        LostTalesColors.rgb(LostTalesColors.SAND), 255);
                Gui.drawRect(this.x + PADDING_X, rowY + ROW_HEIGHT - 1,
                        this.x + this.width - PADDING_X, rowY + ROW_HEIGHT,
                        outline);
                rowY += ROW_HEIGHT;
                continue;
            }
            if (entry.color >= 0) {
                // The channel's colour as a one-pixel upright bar the
                // height of the row's text; a palette row's as a chip
                // the width of the column.
                Gui.drawRect(this.x + PADDING_X, rowY + 1,
                        this.x + PADDING_X
                                + (entry.chip ? this.swatchWidth : SWATCH_WIDTH),
                        rowY + ROW_HEIGHT - 1,
                        LostTalesChatVisualStyle.argb(entry.color, 0xFF));
            }
            // An icon or a head stands beside the label as it does in a
            // message row: centred on the label's capitals by the chat's
            // one rule, on whole pixels, since both are pixel art. A row
            // naming a tab wears the tab's own icon, its unread mark in
            // its corner, as the tab does.
            int labelTop = rowY + 2;
            int iconX = this.x + this.labelX - ChatChannelIcons.SLOT
                    - ChatChannelIcons.GAP;
            if (entry.icon != null) {
                ChatChannelIcons.draw(Minecraft.getMinecraft(), entry.icon,
                        iconX, labelTop + ChatInlineIcons.CONTENT_TOP_OFFSET,
                        255, ChatIconMark.of(entry.icon));
            } else if (entry.head != null) {
                // A head drawn as the tabs draw theirs: eight pixels,
                // centred across the icon's box.
                float headX = iconX + 1.0F;
                float headY = labelTop
                        + LostTalesChatOverlayRenderer.HEAD_TOP_OFFSET;
                if (entry.head.skinId.length() == 0) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            Minecraft.getMinecraft(), entry.head.owner,
                            headX, headY, 8.0F, 1.0F, 1.0F);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            Minecraft.getMinecraft(), entry.head.owner,
                            entry.head.skinId, headX, headY, 8.0F,
                            1.0F, 1.0F);
                }
            } else if (entry.sprite != null) {
                // Centred in the icon column and on the label's capitals,
                // the odd pixel left and up.
                int spriteX = iconX + LostTalesUiInk.centredStart(
                        ChatChannelIcons.SIZE, entry.sprite.getWidth());
                int spriteY = labelTop + Math.floorDiv(
                        LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT
                                - entry.sprite.getHeight(), 2);
                if (entry.litSprite == null) {
                    entry.sprite.drawWithShadow(spriteX, spriteY, 255);
                } else {
                    LostTalesUiSheet.drawPairWithShadow(entry.sprite,
                            entry.litSprite,
                            spriteFade(entry, entry == hovered, elapsed),
                            spriteX, spriteY, 255);
                }
            }
            // Text at full opacity always; a muted channel is italic, the
            // hovered row is told by its highlight, and a row naming a
            // tab takes the tab's colour as it lights, as the tab's own
            // name does.
            int labelRgb = entry.unavailable.length() > 0
                    ? LostTalesChatVisualStyle.asideRgb()
                    : entry.labelColor >= 0 ? entry.labelColor
                    : LostTalesChatVisualStyle.IVORY;
            if (entry.icon != null) {
                labelRgb = LostTalesChatVisualStyle.blend(labelRgb,
                        ClientChatChannelState.displayColor(entry.icon),
                        labelFade(entry, entry == hovered, elapsed));
            }
            if (entry.emojis) {
                ChatInlineText.draw(Minecraft.getMinecraft(), font,
                        entry.label, entry.dim ? "§o" : "",
                        this.x + this.labelX, rowY + 2, labelRgb, 255);
            } else {
                LostTalesChatVisualStyle.drawColored(font,
                        entry.dim ? "§o" + entry.label : entry.label,
                        this.x + this.labelX, rowY + 2, labelRgb, 255);
            }
            rowY += ROW_HEIGHT;
        }
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        // A hairline on an edge the list continues past.
        if (this.scrollRows > 0.0D) {
            Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1,
                    this.y + 2,
                    LostTalesChatVisualStyle.argb(MORE_RGB, 0xFF));
        }
        if (last < this.entries.size()) {
            Gui.drawRect(this.x + 1, this.y + this.height - 2,
                    this.x + this.width - 1, this.y + this.height - 1,
                    LostTalesChatVisualStyle.argb(MORE_RGB, 0xFF));
        }
        registerRegion(regions);
    }
}
