package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

/**
 * The world's lore characters, in a sub-window of the Characters
 * tab's window: yours, each a press from being released; those free to
 * claim, into the empty slot picked in the roster or else the first empty
 * one; and those another player has. Each figure's description stands
 * under its name, cut to two lines, and the row's card holds the whole
 * text. The field at the top finds a figure by name.
 */
final class LoreCharactersWindow extends SubWindowContent {
    private static final int WIDTH = 280;
    private static final int PADDING = 6;
    private static final int NAME_LINE = 11;
    private static final int DESCRIPTION_LINE = 10;
    private static final int DESCRIPTION_LINES = 2;
    private static final int ROW_GAP = 3;
    private static final int HEADER_HEIGHT = 15;
    private static final int NOTE_LINE = 10;
    /** The most the list stands open at before it scrolls. */
    private static final int MAX_LIST_HEIGHT = 220;
    /** The widest the row's card grows before it wraps. */
    private static final int CARD_WIDTH = 220;
    private static final String FIELD = "field";
    private static final String ROW_PREFIX = "lore:";
    /** How often the field keeps time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;

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

    private final CreatorTextControl find;
    private int scroll;
    private boolean fieldFocused;
    /** The figure under the pointer this frame; null for none. */
    private Line hovered;
    private long tickedNanos;

    LoreCharactersWindow() {
        Minecraft minecraft = Minecraft.getMinecraft();
        this.find = new CreatorTextControl(new CreatorContext(minecraft,
                minecraft.fontRenderer, null), I18n.format(
                        "gui.losttales.lore.find"), "", 32, false);
    }

    /** Opens afresh: the field empty, the list from its top. */
    void restart() {
        this.scroll = 0;
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
            return HEADER_HEIGHT;
        }
        return NAME_LINE + DESCRIPTION_LINE * shortDescription(font,
                line.character.getDescription(), width).size() + ROW_GAP;
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
        return (int)box.top + PADDING + this.find.height() + NOTE_LINE;
    }

    private int listBoxHeight(LostTalesUiHitBox box) {
        return Math.max(0, (int)(box.top + box.height) - PADDING
                - listTop(box));
    }

    /** The figure's row under a point, in the box's own space; null off every row. */
    private Line lineAt(FontRenderer font, LostTalesUiHitBox box, double x,
                        double y) {
        int width = (int)box.width - 2 * PADDING;
        if (Double.isNaN(x) || Double.isNaN(y)
                || !LostTalesUiHitBox.contains(x, y, box.left + PADDING,
                        listTop(box), width, listBoxHeight(box))) {
            return null;
        }
        int top = listTop(box) - this.scroll;
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
        int list = listHeight(font, lines(), width - 2 * PADDING);
        return PADDING + this.find.height() + NOTE_LINE
                + Math.max(NOTE_LINE, Math.min(MAX_LIST_HEIGHT, list))
                + PADDING;
    }

    @Override
    public int minWidth() {
        return 180;
    }

    @Override
    public int minHeight() {
        return PADDING + this.find.height() + NOTE_LINE + 3 * NAME_LINE
                + PADDING;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        keepTime();
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left + PADDING;
        int width = (int)box.width - 2 * PADDING;
        this.find.place(left, (int)box.top + PADDING, width);
        this.find.draw(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2);
        LostTalesUiInk.beginContent();
        List<Line> lines = lines();
        String note = ClientLoreCharacterCache.getSnapshot() == null
                || ClientCharacterRosterCache.getSnapshot() == null
                ? I18n.format("gui.losttales.lore.loading")
                : lines.isEmpty() ? I18n.format(query().length() > 0
                        ? "gui.losttales.lore.none_found"
                        : "gui.losttales.lore.none")
                : target(CharactersPage.current());
        LostTalesUiInk.drawText(font, font.trimStringToWidth(note, width),
                left, listTop(box) - NOTE_LINE, WindowStyle.asideRgb(), alpha);
        int boxHeight = listBoxHeight(box);
        this.scroll = Math.max(0, Math.min(this.scroll,
                listHeight(font, lines, width) - boxHeight));
        this.hovered = lineAt(font, box, pointerX, pointerY);
        boolean clipped = SubWindowContent.beginClip(minecraft,
                clipX + PADDING, clipY + (listTop(box) - box.top), width,
                boxHeight);
        try {
            int y = listTop(box) - this.scroll;
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
    }

    private void drawLine(FontRenderer font, Line line, int left, int y,
                          int width, int height, int alpha) {
        if (line.header != null) {
            String name = LostTalesSkyrimUiStyle.uppercase(line.header);
            int textTop = y + LostTalesUiInk.centredStart(HEADER_HEIGHT, 7);
            LostTalesUiInk.drawText(font, name, left, textTop,
                    LostTalesColors.rgb(LostTalesColors.TEXT), alpha);
            int ruleLeft = left + font.getStringWidth(name) + 5;
            if (ruleLeft < left + width) {
                Gui.drawRect(ruleLeft, textTop + 3, left + width, textTop + 4,
                        LostTalesColors.BORDER_DIM);
            }
            return;
        }
        boolean takeable = line.unavailable.length() == 0;
        if (line == this.hovered && takeable) {
            Gui.drawRect(left - 2, y, left + width, y + height - 1,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72));
        }
        LostTalesUiInk.beginContent();
        String race = ClientCharacterDisplayNames.race(
                line.character.getRaceId());
        int raceWidth = font.getStringWidth(race);
        LostTalesUiInk.drawText(font, font.trimStringToWidth(
                        line.character.getName(), Math.max(0,
                                width - raceWidth - 6)), left, y + 1,
                takeable ? LostTalesUiInk.IVORY : WindowStyle.asideRgb(),
                alpha);
        LostTalesUiInk.drawText(font, race, left + width - raceWidth, y + 1,
                WindowStyle.asideRgb(), alpha);
        int lineY = y + NAME_LINE;
        for (String shown : shortDescription(font,
                line.character.getDescription(), width)) {
            LostTalesUiInk.drawText(font, shown, left, lineY,
                    WindowStyle.asideRgb(), alpha);
            lineY += DESCRIPTION_LINE;
        }
    }

    /**
     * The row's card: the figure's name, its whole description, and why a
     * press does nothing where it does not.
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
        String description = line.character.getDescription().trim();
        if (description.length() == 0 && line.unavailable.length() == 0) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<String> text = new ArrayList<String>();
        List<Integer> colours = new ArrayList<Integer>();
        text.add(line.character.getName());
        colours.add(Integer.valueOf(LostTalesColors.rgb(LostTalesColors.HONEY)));
        for (Object wrapped : font.listFormattedStringToWidth(description,
                CARD_WIDTH)) {
            text.add(String.valueOf(wrapped));
            colours.add(Integer.valueOf(LostTalesUiInk.IVORY));
        }
        if (line.unavailable.length() > 0) {
            for (Object wrapped : font.listFormattedStringToWidth(
                    line.unavailable, CARD_WIDTH)) {
                text.add(String.valueOf(wrapped));
                colours.add(Integer.valueOf(WindowStyle.asideRgb()));
            }
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
            this.find.tick();
        }
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        if (this.find.contains((int)Math.floor(x), (int)Math.floor(y))) {
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

    /** The wheel scrolls the list by lines of text. */
    @Override
    public void scrollBy(int lines) {
        this.scroll = Math.max(0, this.scroll + lines * NAME_LINE);
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
        boolean taken = this.find.keyTyped(typedChar, keyCode);
        if (!before.equals(this.find.getText())) {
            this.scroll = 0;
        }
        return taken;
    }
}
