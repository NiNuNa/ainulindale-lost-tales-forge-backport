package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowFields;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

/**
 * The world's lore characters, in a sub-window of the Characters
 * tab's window: yours, each a press from being released; those free to
 * claim, into the empty slot picked in the roster or else the first empty
 * one; and those another player has. Each figure's description stands
 * under its name, cut to two lines, and the row's card holds the whole
 * text. The field at the top finds a figure by name. It is laid out and
 * drawn as a menu is: its field, headings, lit row and scroll edge.
 */
final class LoreCharactersWindow extends SubWindowContent {
    private static final int WIDTH = 280;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    private static final int DESCRIPTION_LINE = 10;
    private static final int DESCRIPTION_LINES = 2;
    private static final int ROW_GAP = 3;
    private static final int NOTE_LINE = 10;
    private static final int FIND_LIMIT = 32;
    /** The most the list stands open at before it scrolls. */
    private static final int MAX_LIST_HEIGHT = 220;
    /** The widest the row's card grows before it wraps. */
    private static final int CARD_WIDTH = 220;
    private static final String FIELD = "field";
    private static final String ROW_PREFIX = "lore:";
    /** How often the field keeps time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;
    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;

    /** One line of the list: a section's name, or a figure. */
    private static final class Line {
        final String header;
        final LoreCharacterSummary character;
        /** Why a press does nothing here; empty for a row that can be taken. */
        final String unavailable;

        Line(String header, LoreCharacterSummary character,
             String unavailable) {
            this.header = header;
            this.character = character;
            this.unavailable = unavailable == null ? "" : unavailable;
        }
    }

    private final GuiTextField find;
    /** Pixels the list is asked to be scrolled by; the drawn offset glides after it, as a menu's does. */
    private int scroll;
    private double renderedScroll;
    private long scrollNanos;
    private boolean fieldFocused;
    /** The figure under the pointer this frame; null for none. */
    private Line hovered;
    private long tickedNanos;

    LoreCharactersWindow() {
        this.find = WindowFields.make(LostTalesUiCaret.HEIGHT, FIND_LIMIT,
                false);
        this.find.setFocused(false);
    }

    /** Opens afresh: the list from its top. */
    void restart() {
        this.scroll = 0;
        this.renderedScroll = 0.0D;
    }

    /** A figure's name: a menu's row. */
    private static int nameLine() {
        return MenuWindow.rowHeight();
    }

    /* ---- The lines ---- */

    private String query() {
        return this.find.getText().trim();
    }

    /** The target line above the list: which slot a claim fills; empty while none. */
    private static String target(CharactersPage page) {
        int slot = page == null ? -1 : page.claimSlot();
        return slot < 0 ? "" : I18n.format("gui.losttales.lore.target_slot",
                I18n.format("gui.losttales.character.slot",
                        Integer.valueOf(slot + 1)));
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<Line>();
        LoreCharacterSnapshot lore = ClientLoreCharacterCache.getSnapshot();
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (lore == null || roster == null) {
            return lines;
        }
        CharactersPage page = CharactersPage.current();
        String busy = page != null && page.isPending()
                ? I18n.format("gui.losttales.character.working") : "";
        String frozen = lore.canMutate() ? "" : I18n.format(
                "gui.losttales.character.error.lore_character_ownership_storage_read_only");
        int slot = page == null ? -1 : page.claimSlot();
        PageSearch search = PageSearch.of(query());
        List<Line> yours = new ArrayList<Line>();
        List<Line> free = new ArrayList<Line>();
        List<Line> taken = new ArrayList<Line>();
        for (LoreCharacterSummary character : lore.getCharacters()) {
            if (!search.matches(character.getName())) {
                continue;
            }
            if (character.isOwnedByViewer()) {
                yours.add(new Line(null, character, firstOf(
                        character.isTransferInProgress() ? I18n.format(
                                "gui.losttales.lore.transfer_pending") : "",
                        character.getOwnedCharacterId() != null
                                && character.getOwnedCharacterId().equals(
                                        roster.getActiveCharacterId())
                                ? I18n.format("gui.losttales.character.error.lore_character_active")
                                : "", frozen, busy)));
            } else if (character.isAvailable()) {
                free.add(new Line(null, character, firstOf(
                        !character.isConfigured() ? I18n.format(
                                "gui.losttales.lore.not_configured") : "",
                        character.isTransferInProgress() ? I18n.format(
                                "gui.losttales.lore.transfer_pending") : "",
                        slot < 0 ? I18n.format("gui.losttales.lore.no_slot")
                                : "", frozen, busy)));
            } else {
                taken.add(new Line(null, character,
                        character.getOwnerName().length() == 0
                                ? I18n.format("gui.losttales.lore.claimed")
                                : I18n.format("gui.losttales.lore.owned_by",
                                        character.getOwnerName())));
            }
        }
        addSection(lines, "gui.losttales.lore.section.yours", yours);
        addSection(lines, "gui.losttales.lore.section.free", free);
        addSection(lines, "gui.losttales.lore.section.taken", taken);
        return lines;
    }

    private static void addSection(List<Line> lines, String key,
                                   List<Line> rows) {
        if (!rows.isEmpty()) {
            lines.add(new Line(I18n.format(key), null, ""));
            lines.addAll(rows);
        }
    }

    /** The first reason given; empty while none is. */
    private static String firstOf(String... reasons) {
        for (String reason : reasons) {
            if (reason != null && reason.length() > 0) {
                return reason;
            }
        }
        return "";
    }

    /** A figure's description as its row shows it: two lines at most, the second cut short. */
    static List<String> shortDescription(FontRenderer font, String description,
                                         int width) {
        List<String> shown = new ArrayList<String>(DESCRIPTION_LINES);
        if (description == null || description.trim().length() == 0) {
            return shown;
        }
        List<?> wrapped = font.listFormattedStringToWidth(description.trim(),
                Math.max(1, width));
        for (int index = 0; index < wrapped.size()
                && index < DESCRIPTION_LINES; index++) {
            String line = String.valueOf(wrapped.get(index));
            if (index == DESCRIPTION_LINES - 1
                    && wrapped.size() > DESCRIPTION_LINES) {
                String ellipsis = I18n.format("gui.losttales.lore.more");
                line = font.trimStringToWidth(line, Math.max(0, width
                        - font.getStringWidth(ellipsis))) + ellipsis;
            }
            shown.add(line);
        }
        return shown;
    }

    private static int lineHeight(FontRenderer font, Line line, int width) {
        if (line.header != null) {
            return MenuWindow.rowHeight();
        }
        return nameLine() + DESCRIPTION_LINE * shortDescription(font,
                ClientCharacterDisplayNames.loreDescription(line.character),
                width).size() + ROW_GAP;
    }

    private static int listHeight(FontRenderer font, List<Line> lines,
                                  int width) {
        int total = 0;
        for (Line line : lines) {
            total += lineHeight(font, line, width);
        }
        return total;
    }

    /* ---- Where things stand ---- */

    private int listTop(LostTalesUiHitBox box) {
        return (int)box.top + PADDING_Y + WindowLists.fieldHeight()
                + NOTE_LINE;
    }

    private int listBoxHeight(LostTalesUiHitBox box) {
        return Math.max(0, (int)(box.top + box.height) - PADDING_Y
                - listTop(box));
    }

    /** Whether a point is on the field, its whole row from the padding in. */
    private static boolean onField(LostTalesUiHitBox box, double x, double y) {
        return LostTalesUiHitBox.contains(x, y, box.left + PADDING_X,
                box.top + PADDING_Y, box.width - 2 * PADDING_X,
                WindowLists.fieldHeight());
    }

    /** The figure's row under a point, in the box's own space; null off every row. */
    private Line lineAt(FontRenderer font, LostTalesUiHitBox box, double x,
                        double y) {
        int width = (int)box.width - 2 * PADDING_X;
        if (Double.isNaN(x) || Double.isNaN(y)
                || !LostTalesUiHitBox.contains(x, y, box.left, listTop(box),
                        box.width, listBoxHeight(box))) {
            return null;
        }
        int top = listTop(box) - (int)Math.round(this.renderedScroll);
        for (Line line : lines()) {
            int height = lineHeight(font, line, width);
            if (y >= top && y < top + height) {
                return line.header == null ? line : null;
            }
            top += height;
        }
        return null;
    }

    /* ---- The window ---- */

    @Override
    public String stripTitle() {
        return null;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return LostTalesUiSheet.MEMBERS;
    }

    @Override
    public int naturalWidth() {
        return WIDTH;
    }

    @Override
    public int naturalHeight(int width) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int list = listHeight(font, lines(), width - 2 * PADDING_X);
        return PADDING_Y + WindowLists.fieldHeight() + NOTE_LINE
                + Math.max(NOTE_LINE, Math.min(MAX_LIST_HEIGHT, list))
                + PADDING_Y;
    }

    @Override
    public int minWidth() {
        return 180;
    }

    @Override
    public int minHeight() {
        return PADDING_Y + WindowLists.fieldHeight() + NOTE_LINE
                + 3 * nameLine() + PADDING_Y;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        keepTime();
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left + PADDING_X;
        int width = (int)box.width - 2 * PADDING_X;
        List<Line> lines = lines();
        int boxHeight = listBoxHeight(box);
        int maxScroll = Math.max(0, listHeight(font, lines, width) - boxHeight);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        glideScroll(maxScroll);
        this.hovered = lineAt(font, box, pointerX, pointerY);
        // The hovered row, cut to the list's band, before anything lands
        // on it, as a menu's is.
        if (this.hovered != null && this.hovered.unavailable.length() == 0) {
            int top = rowTop(font, box, this.hovered, width);
            WindowLists.drawLitRow(box.left, box.left + box.width, box.left,
                    Math.max(listTop(box), top), box.left + box.width,
                    Math.min(listTop(box) + boxHeight,
                            top + lineHeight(font, this.hovered, width)),
                    surfaceAlpha);
        }
        WindowLists.drawField(font, this.find, LostTalesUiSheet.SEARCH,
                I18n.format("gui.losttales.lore.find"), left,
                (int)box.top + PADDING_Y, left + width, left + width, alpha);
        LostTalesUiInk.beginContent();
        String note = ClientLoreCharacterCache.getSnapshot() == null
                || ClientCharacterRosterCache.getSnapshot() == null
                ? I18n.format("gui.losttales.lore.loading")
                : lines.isEmpty() ? I18n.format(query().length() > 0
                        ? "gui.losttales.lore.none_found"
                        : "gui.losttales.lore.none")
                : target(CharactersPage.current());
        LostTalesUiInk.drawText(font, font.trimStringToWidth(note, width),
                left, listTop(box) - NOTE_LINE, WindowStyle.asideRgb(), alpha);
        boolean clipped = SubWindowContent.beginClip(minecraft,
                clipX, clipY + (listTop(box) - box.top), box.width,
                boxHeight);
        try {
            int y = listTop(box) - (int)Math.round(this.renderedScroll);
            for (Line line : lines) {
                int height = lineHeight(font, line, width);
                if (y + height >= listTop(box)
                        && y < listTop(box) + boxHeight) {
                    drawLine(font, line, left, y, width, height, alpha);
                }
                y += height;
            }
        } finally {
            SubWindowContent.endClip(clipped);
        }
        WindowLists.drawScroll(box.left, listTop(box), box.left + box.width,
                box.top + box.height, listTop(box), listTop(box) + boxHeight,
                this.renderedScroll, maxScroll, alpha);
    }

    /** Where a line stands this frame, the drawn scroll taken in. */
    private int rowTop(FontRenderer font, LostTalesUiHitBox box, Line wanted,
                       int width) {
        int top = listTop(box) - (int)Math.round(this.renderedScroll);
        for (Line line : lines()) {
            if (line == wanted || line.character != null
                    && wanted.character != null && line.character.getId()
                            .equals(wanted.character.getId())) {
                return top;
            }
            top += lineHeight(font, line, width);
        }
        return top;
    }

    /** The drawn scroll glides after the asked one with the windows' shared scroll motion, as a menu's does. */
    private void glideScroll(int maxScroll) {
        long now = System.nanoTime();
        double elapsed = this.scrollNanos == 0L ? 0.0D
                : (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        this.renderedScroll = Math.max(0.0D, Math.min(maxScroll,
                this.renderedScroll));
        if (Math.abs(this.scroll - this.renderedScroll) <= SCROLL_SNAP_PIXELS) {
            this.renderedScroll = this.scroll;
            return;
        }
        this.renderedScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.renderedScroll, this.scroll, elapsed);
    }

    private void drawLine(FontRenderer font, Line line, int left, int y,
                          int width, int height, int alpha) {
        if (line.header != null) {
            WindowLists.drawHeading(font, line.header, left, left,
                    left + width, y, MenuWindow.rowHeight(), false, alpha);
            return;
        }
        boolean takeable = line.unavailable.length() == 0;
        LostTalesUiInk.beginContent();
        String race = ClientCharacterDisplayNames.race(
                line.character.getRaceId());
        int raceWidth = font.getStringWidth(race);
        int nameTop = y + LostTalesUiInk.centredStart(nameLine(),
                LostTalesUiInk.CAP_HEIGHT);
        LostTalesUiInk.drawText(font, font.trimStringToWidth(
                        line.character.getName(), Math.max(0,
                                width - raceWidth - 6)), left, nameTop,
                takeable ? LostTalesUiInk.IVORY : WindowStyle.asideRgb(),
                alpha);
        LostTalesUiInk.drawText(font, race, left + width - raceWidth, nameTop,
                WindowStyle.asideRgb(), alpha);
        int lineY = y + nameLine();
        for (String shown : shortDescription(font,
                ClientCharacterDisplayNames.loreDescription(line.character),
                width)) {
            LostTalesUiInk.drawText(font, shown, left, lineY,
                    WindowStyle.asideRgb(), alpha);
            lineY += DESCRIPTION_LINE;
        }
    }

    /**
     * The row's card: the figure's name and its whole description. Why a
     * greyed row cannot be taken is said when it is pressed.
     */
    @Override
    public String tipKey() {
        return this.hovered == null || this.hovered.character == null ? ""
                : this.hovered.character.getName();
    }

    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY,
                        int screenWidth, float share) {
        Line line = this.hovered;
        if (line == null || line.character == null) {
            return;
        }
        String description = ClientCharacterDisplayNames.loreDescription(
                line.character).trim();
        if (description.length() == 0) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<String> text = new ArrayList<String>();
        List<Integer> colours = new ArrayList<Integer>();
        text.add(ClientCharacterDisplayNames.loreCardName(line.character));
        colours.add(Integer.valueOf(LostTalesUiTheme.accentRgb()));
        for (Object wrapped : font.listFormattedStringToWidth(description,
                CARD_WIDTH)) {
            text.add(String.valueOf(wrapped));
            colours.add(Integer.valueOf(LostTalesUiInk.IVORY));
        }
        int widest = 0;
        for (String each : text) {
            widest = Math.max(widest, font.getStringWidth(each));
        }
        int width = widest + WindowStyle.POPUP_INSET * 2;
        int height = text.size() * DESCRIPTION_LINE - 2
                + WindowStyle.POPUP_INSET * 2 + LostTalesUiInk.SHADOW_OFFSET;
        int x = Math.max(2, Math.min(screenWidth - width - 2,
                tipX - width / 2));
        int y = Math.max(2, tipY - 3 - height);
        LostTalesTooltipSmoothing.begin(tipX, tipY);
        try {
            WindowStyle.drawPopup(x, y, x + width, y + height, share);
            LostTalesUiInk.beginContent();
            int lineY = y + WindowStyle.POPUP_INSET;
            for (int index = 0; index < text.size(); index++) {
                LostTalesUiInk.drawText(font, text.get(index),
                        x + WindowStyle.POPUP_INSET, lineY,
                        colours.get(index).intValue(),
                        Math.round(255.0F * share));
                lineY += DESCRIPTION_LINE;
            }
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }

    /** The field keeps time as a screen's ticks would: the caret blinks. */
    private void keepTime() {
        long now = System.nanoTime();
        if (now - this.tickedNanos >= TICK_NANOS) {
            this.tickedNanos = now;
            this.find.updateCursorCounter();
        }
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        if (onField(box, x, y)) {
            hover.part = FIELD;
            hover.acts = true;
            return hover;
        }
        Line line = lineAt(font, box, x, y);
        if (line == null) {
            return null;
        }
        hover.part = ROW_PREFIX + line.character.getId();
        hover.acts = line.unavailable.length() == 0;
        hover.greyedWhy = line.unavailable;
        return hover;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (FIELD.equals(hover.part)) {
            takeKeys();
            this.find.mouseClicked((int)Math.floor(x), (int)Math.floor(y),
                    button);
            return true;
        }
        if (button != 0 || hover.part == null
                || !hover.part.startsWith(ROW_PREFIX)) {
            return true;
        }
        String id = hover.part.substring(ROW_PREFIX.length());
        for (Line line : lines()) {
            if (line.character != null && line.character.getId().equals(id)) {
                if (line.unavailable.length() == 0) {
                    act(line.character);
                }
                break;
            }
        }
        return true;
    }

    /** Claiming is sent at once; releasing asks first. */
    private static void act(final LoreCharacterSummary character) {
        final CharactersPage page = CharactersPage.current();
        final CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        if (page == null || roster == null || page.isPending()) {
            return;
        }
        if (!character.isOwnedByViewer()) {
            int slot = page.claimSlot();
            if (slot >= 0) {
                page.track(ClientCharacterNetwork.claimLoreCharacter(
                        roster.getRevision(),
                        character.getOwnershipRevision(), slot,
                        character.getId()),
                        "gui.losttales.lore.processing");
            }
            return;
        }
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            screen.ask(WindowPages.tab(CharactersPage.PAGE_ID),
                    I18n.format("gui.losttales.lore.release_question",
                            character.getName()),
                    I18n.format("gui.losttales.lore.release_detail"),
                    I18n.format("gui.losttales.lore.release"),
                    new Runnable() {
                        @Override
                        public void run() {
                            page.track(ClientCharacterNetwork
                                    .releaseLoreCharacter(
                                            roster.getRevision(),
                                            character.getOwnershipRevision(),
                                            character.getId()),
                                    "gui.losttales.lore.processing");
                        }
                    });
        }
    }

    /** The wheel scrolls the list by whole rows, as a menu's does. */
    @Override
    public void scrollBy(int lines) {
        this.scroll = Math.max(0, this.scroll + WheelStep.pixels(
                WheelStep.menuRows(lines), MenuWindow.rowHeight()));
    }

    @Override
    public boolean holdsKeys() {
        return this.fieldFocused;
    }

    /** The field takes the keys as the window comes in front, so typing finds. */
    @Override
    public void takeKeys() {
        this.fieldFocused = true;
        this.find.setFocused(true);
    }

    @Override
    public void releaseKeys() {
        this.fieldFocused = false;
        this.find.setFocused(false);
    }

    @Override
    public void closed() {
        releaseKeys();
    }

    /** Typing finds a figure by name; the list starts again from its top. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.fieldFocused) {
            return false;
        }
        String before = this.find.getText();
        this.find.textboxKeyTyped(typedChar, keyCode);
        if (!before.equals(this.find.getText())) {
            this.scroll = 0;
        }
        return true;
    }
}
