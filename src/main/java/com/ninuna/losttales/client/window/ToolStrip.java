package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiFading;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

/**
 * A window's tool strip under its tab row: the controls that read the
 * tab in front rather than pick one, each offered by that tab
 * ({@link WindowPage#panel} and on). At its left, under the tab search,
 * the panel button and nothing else: over a conversation the timestamp
 * area's person, which drives the area out of the window and back in;
 * over a page the page's own panel, the journal's quest list.
 * Everything else stands at the right. From the right end: the help
 * button, a question mark, which opens the page's help; the search, a
 * well a third of the strip wide naming what it searches — {@code
 * Search Global}, {@code Search active quests} — with its magnifier at
 * the well's right end; the member list's button, two people, which a
 * page has none of; the full window button, which lets the tab in front
 * fill its window ({@link ContentView}); the split view button, which
 * opens the pages that can stand beside it, or the split's own rows; the
 * cog, which opens the settings of the page's kind (Chat Settings for
 * every conversation); a hairline; and the page's options, each a button
 * of its own ({@link PageOption}), a hairline between two groups: Mark
 * as Read, Notification Settings, the journal's filters, the map's kinds
 * of marker. Options the strip has no room for are left to the tab's
 * options, from the end of the list. The panel buttons rest lit while
 * their panels are out, the cog, the split and the question mark while
 * what they open is, and an option while it is on. A cog with no
 * settings, a split with no page to stand beside, an option that cannot
 * be taken and a well with nothing to search stay where they are,
 * greyed, and their tips say why; a split the padlock holds says
 * nothing, as the lit padlock says it.
 * While a search stands in a well, the count stands inside the well
 * before its end, and the magnifier has crossed over to the cross that
 * clears it: over a conversation the match stood on of how many, with
 * the chevrons walking them; over a page how many entries it found.
 *
 * <p>Every window's strip shows its well; the search itself is one at a
 * time ({@link WindowSearch}), and its one
 * field moves to the window it is open on. Everything stands on the
 * strip's centre row, laid out from the strip's edges as the tabs are,
 * and each control answers the pointer on the box it is drawn in: a
 * glyph on its ink with {@link #SLACK} clear pixels round it, the field
 * on its well. Each window keeps its controls' motions in its own
 * {@link State}, on its frame.</p>
 */
public final class ToolStrip {
    /** What a point on a strip lands on. */
    public enum Part {
        /** The panel button at the left end: the timestamp area's, or a page's own panel's. */
        PANEL,
        /** One of the options of the tab in front, a button of its own ({@link #optionAt}). */
        OPTION,
        /** The cog: the settings of the kind of the tab in front. */
        SETTINGS,
        /** The split view button: the pages that can stand beside the tab in front, or its split's rows. */
        SPLIT,
        /** The full window button: the tab in front filling its window. */
        VIEW,
        MEMBERS_TOGGLE,
        /** The question mark at the strip's right end: the page's help. */
        HELP,
        FIELD,
        /** The well's magnifier, or the cross it becomes while a search stands. */
        ICON,
        PREVIOUS,
        NEXT
    }

    /** A panel button: its glyph, its lit artwork, and its tips. */
    public static final class Panel {
        final LostTalesUiSheet glyph;
        final LostTalesUiSheet litGlyph;
        final String showKey;
        final String hideKey;

        public Panel(LostTalesUiSheet glyph, LostTalesUiSheet litGlyph,
                     String showKey, String hideKey) {
            this.glyph = glyph;
            this.litGlyph = litGlyph;
            this.showKey = showKey;
            this.hideKey = hideKey;
        }
    }

    /** What the well counts while words stand in it. */
    enum Count {
        /** Nothing typed: no count. */
        NONE,
        /** A conversation's search: the match stood on, of how many, and the chevrons. */
        WALK,
        /** A page's search: how many entries the words found. */
        FOUND
    }

    /** Clear space between the search's own controls. */
    static final int GAP = 3;
    /** The well: one message row, a clear row above and below the capitals' seven. */
    static final int WELL_HEIGHT = 12;
    /** The well's field: its text row, the well's clear rows and caret aside. */
    public static final int FIELD_HEIGHT = WELL_HEIGHT - 3;
    /** Narrower than this and a window's strip keeps no well. */
    static final int MIN_WELL_WIDTH = 44;
    /** The least the field keeps beside the count and the chevrons, else they wait. */
    private static final int MIN_WALK_FIELD = 12;
    /** Clear pixels inside the well before its text and after its icon. */
    private static final int WELL_INSET = 2;
    /**
     * Clear space between the strip's end glyphs and what stands next to
     * them, ink to edge, as between the tab row's end controls.
     */
    private static final int END_GAP = 5;
    /**
     * Clear pixels the strip's right-hand glyph keeps from the window's
     * edge, as the tab search keeps them on the left.
     */
    private static final int EDGE_MARGIN = 3;
    /** Clear pixels round a glyph that answer with it. */
    private static final int SLACK = 2;
    private static final int MAX_QUERY = 64;
    /** The least clear space the options keep from the panel button. */
    private static final int PANEL_CLEARANCE = END_GAP * 2;
    /** The hairline between two groups of buttons: the tab row's own. */
    private static final int DIVIDER_WIDTH = WindowStyle.DIVIDER_WIDTH;
    private static final int DIVIDER_HEIGHT = TabRow.END_CONTROL_SIZE;
    /** A hairline with a button's gap either side of it. */
    private static final int DIVIDER_ROOM = END_GAP + DIVIDER_WIDTH + END_GAP;
    private static final int HELP_WIDTH = LostTalesUiSheet.QUESTION.getWidth();
    private static final int HELP_HEIGHT = LostTalesUiSheet.QUESTION.getHeight();
    private static final int MEMBERS_WIDTH = LostTalesUiSheet.MEMBERS.getWidth();
    private static final int MEMBERS_HEIGHT =
            LostTalesUiSheet.MEMBERS.getHeight();
    private static final int COG_WIDTH = LostTalesUiSheet.COG.getWidth();
    private static final int COG_HEIGHT = LostTalesUiSheet.COG.getHeight();
    private static final int VIEW_WIDTH = LostTalesUiSheet.FULLSCREEN.getWidth();
    private static final int VIEW_HEIGHT =
            LostTalesUiSheet.FULLSCREEN.getHeight();
    private static final int SPLIT_WIDTH = LostTalesUiSheet.SPLIT.getWidth();
    private static final int SPLIT_HEIGHT = LostTalesUiSheet.SPLIT.getHeight();

    /** Where one window's strip stands this frame, in its row's space. */
    static final class Layout {
        int wellLeft;
        int wellTop;
        int wellRight;
        int wellBottom;
        int textTop;
        int fieldX;
        int fieldWidth;
        int iconSlotLeft;
        int panelX;
        /** The panel button's glyph size; a width of 0 for a strip with none. */
        int panelWidth;
        int panelHeight;
        /** The page's options the strip has room for, and where each stands. */
        PageOption[] options = new PageOption[0];
        int[] optionX = new int[0];
        /** The hairlines: between two groups of options, and after the last before the cog. */
        int[] dividerX = new int[0];
        int settingsX;
        int splitX;
        int viewX;
        int membersX;
        int helpX;
        /** Whether the strip has a member list button: a page's has none. */
        boolean hasMembers;
        /** Whether the count stands in the well; the query and the room decide. */
        boolean counting;
        /** Whether the chevrons stand beside the count: a conversation's search. */
        boolean walking;
        int countRight;
        int previousX;
        int nextX;
        /** Whether the strip keeps a well at all: a narrow window's does not. */
        boolean hasWell;
    }

    /** One window's strip: where its controls stand, and their motions. */
    static final class State {
        Layout layout;
        final LostTalesUiButtonMotion panelMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        /** Each option button's motion, by the option's id. */
        final Map<String, LostTalesUiButtonMotion> optionMotions =
                new HashMap<String, LostTalesUiButtonMotion>();
        final LostTalesUiButtonMotion helpMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        /** The cog only rises: a quarter turn leaves it as it was. */
        final LostTalesUiButtonMotion settingsMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        final LostTalesUiButtonMotion membersMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        final LostTalesUiButtonMotion splitMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        final LostTalesUiButtonMotion viewMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        /** The magnifier turns on its handle; the cross it becomes answers like a switch. */
        final LostTalesUiButtonMotion iconMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.TURN);
        final LostTalesUiButtonMotion previousMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        final LostTalesUiButtonMotion nextMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        /** The magnifier crossing over to the cross as a search stands in the well. */
        final MotionTransition clearing =
                new MotionTransition(MotionIds.WINDOW_SEARCH_CLEAR);
    }

    private GuiTextField field;

    /**
     * Gives the strip its one field, which the screen makes: the chat's
     * typing field, drawn as plain words, while the chat is installed.
     */
    public void bind(GuiTextField made) {
        if (this.field == null && made != null) {
            this.field = made;
            this.field.setMaxStringLength(MAX_QUERY);
            this.field.setEnableBackgroundDrawing(false);
            this.field.setTextColor(LostTalesUiInk.IVORY);
        }
    }

    public boolean isFocused() {
        return WindowSearch.isOpen() && this.field != null && this.field.isFocused();
    }

    public void focus(boolean on) {
        if (this.field != null) {
            this.field.setFocused(on);
        }
    }

    public String text() {
        return this.field == null ? "" : this.field.getText();
    }

    public void setText(String text) {
        if (this.field != null) {
            this.field.setText(text == null ? "" : text);
            this.field.setCursorPositionEnd();
        }
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return this.field != null && this.field.textboxKeyTyped(typedChar, keyCode);
    }

    /**
     * Lays a window's strip out for its row before the row is drawn, and
     * tells the row where the well is cut out of the strip's surface.
     * The search's field moves to the window its search is open on.
     */
    public void prepare(FontRenderer font, WindowFrame frame,
                 TabRow.Row row) {
        if (font == null || frame == null || row == null) {
            return;
        }
        WindowPage front = row.selected;
        if (front == null) {
            // A window with no tab in front keeps nothing of a strip.
            frame.toolStrip.layout = null;
            frame.tabBar.setToolStripHole(null);
            return;
        }
        boolean typed = WindowSearch.isOpenOn(frame.windowId)
                && WindowSearch.query().length() > 0;
        Panel panel = front.panel();
        Layout laid = layOut(TabRow.toolStripLeft(row),
                (int)Math.floor(frame.tabBar.toolStripRight(font, row)),
                row.rowBottom, TabRow.searchButtonLeft(row),
                TabRow.searchButtonSize(),
                panel == null ? 0 : panel.glyph.getWidth(),
                panel == null ? 0 : panel.glyph.getHeight(),
                front.hasMemberList(),
                !typed ? Count.NONE : front.walksSearch() ? Count.WALK
                        : Count.FOUND,
                font.getStringWidth(front.searchCount()));
        layOptions(laid, front.options(), panel == null
                ? TabRow.searchButtonLeft(row) + TabRow.searchButtonSize()
                : laid.panelX + laid.panelWidth);
        frame.toolStrip.layout = laid;
        frame.tabBar.setToolStripHole(laid.hasWell
                ? new LostTalesUiHitBox(laid.wellLeft, laid.wellTop,
                        laid.wellRight - laid.wellLeft, WELL_HEIGHT)
                : null);
        if (this.field != null && laid.hasWell
                && WindowSearch.isOpenOn(frame.windowId)) {
            this.field.xPosition = laid.fieldX;
            this.field.yPosition = laid.textTop;
            this.field.width = laid.fieldWidth;
        }
    }

    /**
     * Where everything on a strip stands: the panel button, a glyph
     * {@code panelWidth} by {@code panelHeight} (none for a width of 0),
     * centred under the tab search; the help button against the strip's
     * right end, {@link #EDGE_MARGIN} in; the well before it, a third of
     * the strip wide; before the well the member list's button where the
     * strip has one, the full window button, the split view button and
     * the cog. A
     * strip whose third is too narrow for a well, or leaves the buttons no
     * room, keeps none, and the buttons stand before the help button. A
     * {@code count} stands its text, {@code countWidth} wide, inside the
     * well before its icon, with the chevrons before the icon for a
     * {@link Count#WALK}, where the field leaves them room. Row space.
     */
    static Layout layOut(int stripLeft, int stripRight, int stripTop,
                         int searchButtonLeft, int searchButtonSize,
                         int panelWidth, int panelHeight, boolean members,
                         Count count, int countWidth) {
        Layout laid = new Layout();
        laid.wellTop = stripTop
                + (WindowPlacement.TOOL_STRIP_HEIGHT - 1 - WELL_HEIGHT) / 2;
        laid.wellBottom = laid.wellTop + WELL_HEIGHT;
        laid.textTop = laid.wellTop + 2;
        laid.panelWidth = panelWidth;
        laid.panelHeight = panelHeight;
        laid.panelX = searchButtonLeft
                + Math.floorDiv(searchButtonSize - panelWidth, 2);
        laid.hasMembers = members;
        laid.helpX = stripRight - EDGE_MARGIN - HELP_WIDTH;
        laid.wellRight = laid.helpX - END_GAP;
        laid.wellLeft = laid.wellRight
                - Math.floorDiv(stripRight - stripLeft, 3);
        int buttons = COG_WIDTH + END_GAP + SPLIT_WIDTH + END_GAP
                + VIEW_WIDTH + END_GAP
                + (members ? MEMBERS_WIDTH + END_GAP : 0);
        int floor = panelWidth > 0 ? laid.panelX + panelWidth + END_GAP
                : searchButtonLeft + searchButtonSize + END_GAP;
        laid.hasWell = laid.wellRight - laid.wellLeft >= MIN_WELL_WIDTH
                && laid.wellLeft - buttons >= floor;
        int buttonsRight = laid.hasWell ? laid.wellLeft - END_GAP
                : laid.helpX - END_GAP;
        laid.membersX = buttonsRight - MEMBERS_WIDTH;
        laid.viewX = (members ? laid.membersX - END_GAP : buttonsRight)
                - VIEW_WIDTH;
        laid.splitX = laid.viewX - END_GAP - SPLIT_WIDTH;
        laid.settingsX = laid.splitX - END_GAP - COG_WIDTH;
        laid.iconSlotLeft = laid.wellRight - WELL_INSET
                - LostTalesUiSheet.SEARCH.getWidth();
        laid.fieldX = laid.wellLeft + WELL_INSET;
        laid.nextX = laid.iconSlotLeft - GAP
                - LostTalesUiSheet.CHEVRON_1.getWidth();
        laid.previousX = laid.nextX - GAP
                - LostTalesUiSheet.CHEVRON_5.getWidth();
        laid.countRight = (count == Count.WALK ? laid.previousX
                : laid.iconSlotLeft) - GAP;
        int countedFieldRight = laid.countRight - countWidth - GAP;
        laid.counting = count != Count.NONE && laid.hasWell
                && countedFieldRight - LostTalesUiCaret.WIDTH - laid.fieldX
                        >= MIN_WALK_FIELD;
        laid.walking = laid.counting && count == Count.WALK;
        int fieldRight = laid.counting ? countedFieldRight
                : laid.iconSlotLeft - GAP;
        laid.fieldWidth = fieldRight - LostTalesUiCaret.WIDTH - laid.fieldX;
        return laid;
    }

    /**
     * Lays the page's options out against the cog, in their order, a
     * hairline between two groups and one between the last option and
     * the cog: as many as stand whole right of {@code after}, the panel
     * button's right edge. They are counted from the start of the list,
     * so those left out are the last ones; they stay in the tab's options.
     */
    static void layOptions(Layout laid, List<PageOption> options, int after) {
        int room = laid.settingsX - DIVIDER_ROOM - (after + PANEL_CLEARANCE);
        int shown = 0;
        int width = 0;
        for (PageOption option : options) {
            int step = option.glyph.width() + (shown == 0 ? 0
                    : sameGroup(options.get(shown - 1), option) ? END_GAP
                    : DIVIDER_ROOM);
            if (width + step > room) {
                break;
            }
            width += step;
            shown++;
        }
        laid.options = new PageOption[shown];
        laid.optionX = new int[shown];
        List<Integer> dividers = new ArrayList<Integer>();
        int x = laid.settingsX - DIVIDER_ROOM - width;
        for (int index = 0; index < shown; index++) {
            PageOption option = options.get(index);
            if (index > 0) {
                if (sameGroup(options.get(index - 1), option)) {
                    x += END_GAP;
                } else {
                    dividers.add(Integer.valueOf(x + END_GAP));
                    x += DIVIDER_ROOM;
                }
            }
            laid.options[index] = option;
            laid.optionX[index] = x;
            x += option.glyph.width();
        }
        if (shown > 0) {
            dividers.add(Integer.valueOf(x + END_GAP));
        }
        laid.dividerX = new int[dividers.size()];
        for (int index = 0; index < dividers.size(); index++) {
            laid.dividerX[index] = dividers.get(index).intValue();
        }
    }

    private static boolean sameGroup(PageOption one, PageOption other) {
        return one.group().equals(other.group());
    }

    /**
     * Draws a window's strip as laid out by {@link #prepare}, inside the
     * row's own matrix; {@code under} is the part the pointer is on, if
     * it is on this strip, {@code underOption} the option button it is
     * on, if any, and {@code out} which of what the tab in front opens is
     * out, so its button rests lit.
     */
    public void draw(FontRenderer font, WindowFrame frame, Window window,
              TabRow.Row row, float alphaScale, Part under,
              PageOption underOption, Out out) {
        State state = frame == null ? null : frame.toolStrip;
        Layout laid = state == null ? null : state.layout;
        WindowPage front = row == null ? null : row.selected;
        if (laid == null || font == null || window == null || front == null) {
            return;
        }
        long now = System.nanoTime();
        int ink = Math.round(255.0F * alphaScale);
        // Each panel's button rests lit while its panel is out, as a
        // messenger's member-list button does, and lifts under the
        // pointer either way.
        Panel panel = front.panel();
        if (laid.panelWidth > 0 && panel != null) {
            state.panelMotion.advance(now,
                    front.isPanelOut(window) || under == Part.PANEL,
                    under == Part.PANEL,
                    under == Part.PANEL && Mouse.isButtonDown(0));
            LostTalesUiButton.drawGlyph(panel.glyph, panel.litGlyph,
                    state.panelMotion, laid.panelX,
                    glyphTop(laid, laid.panelHeight), ink);
        }
        drawOptions(state, laid, underOption, out == null ? "" : out.pick,
                now, ink);
        int divider = Math.round(WindowStyle.DIVIDER_ALPHA * ink / 255.0F);
        for (int x : laid.dividerX) {
            WindowStyle.drawDivider(x, glyphTop(laid, DIVIDER_HEIGHT),
                    DIVIDER_HEIGHT, divider);
        }
        drawButton(state.settingsMotion, front.settingsPlace() != null,
                out != null && out.settings, under, Part.SETTINGS,
                LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER,
                laid.settingsX, glyphTop(laid, COG_HEIGHT), now, ink);
        // The split view button rests lit while its page shares the
        // window, as a panel's button does while its panel is out; the
        // padlock holds a split, so a locked window's stands greyed.
        drawButton(state.splitMotion, !window.isLocked()
                        && TabMenus.canSplit(front),
                (out != null && out.split) || window.splitOf(front) != null,
                under, Part.SPLIT, LostTalesUiSheet.SPLIT,
                LostTalesUiSheet.SPLIT_LIT, laid.splitX,
                glyphTop(laid, SPLIT_HEIGHT), now, ink);
        drawButton(state.viewMotion, true, false, under, Part.VIEW,
                LostTalesUiSheet.FULLSCREEN, LostTalesUiSheet.FULLSCREEN_HOVER,
                laid.viewX, glyphTop(laid, VIEW_HEIGHT), now, ink);
        if (laid.hasMembers) {
            drawButton(state.membersMotion, true,
                    front.isMemberListOut(window), under,
                    Part.MEMBERS_TOGGLE, LostTalesUiSheet.MEMBERS,
                    LostTalesUiSheet.MEMBERS_HOVER, laid.membersX,
                    glyphTop(laid, MEMBERS_HEIGHT), now, ink);
        }
        drawButton(state.helpMotion, true, out != null && out.help, under,
                Part.HELP, LostTalesUiSheet.QUESTION,
                LostTalesUiSheet.QUESTION_LIT, laid.helpX,
                glyphTop(laid, HELP_HEIGHT), now, ink);
        if (!laid.hasWell) {
            return;
        }
        boolean searching = WindowSearch.isOpenOn(window.getId());
        Minecraft minecraft = Minecraft.getMinecraft();
        int surfaceAlpha = Math.round(WindowStyle.INSET_ALPHA
                * alphaScale * WindowStyle.opacity(minecraft));
        // The well, in the hole the strip left for it: one surface, a
        // step darker than the strip, as the input bar's typing well is.
        LostTalesUiInk.fillRect(laid.wellLeft, laid.wellTop,
                laid.wellRight, laid.wellBottom,
                LostTalesUiInk.argb(LostTalesUiInk.SURFACE_RGB,
                        surfaceAlpha));
        LostTalesUiInk.beginContent();
        if (front.searchUnavailable().length() > 0) {
            drawGreyed(LostTalesUiSheet.SEARCH, laid.iconSlotLeft,
                    glyphTop(laid, LostTalesUiSheet.SEARCH.getHeight()), ink);
            return;
        }
        boolean typed = searching && WindowSearch.query().length() > 0;
        if (searching && this.field != null) {
            if (this.field.getText().length() == 0) {
                drawPrompt(font, laid, front, row,
                        this.field.xPosition + LostTalesUiCaret.WIDTH + 1,
                        ink);
            }
            this.field.drawTextBox();
        } else {
            drawPrompt(font, laid, front, row, laid.fieldX
                    + LostTalesUiCaret.WIDTH + 1, ink);
        }
        // The magnifier, lit while the field has the keys or the pointer
        // is on the well, crossing over to the cross while a search
        // stands in the well; it only moves for the pointer.
        boolean onWell = under == Part.FIELD || under == Part.ICON;
        state.iconMotion.advance(now,
                onWell || (searching && this.field != null
                        && this.field.isFocused()),
                under == Part.ICON,
                under == Part.ICON && Mouse.isButtonDown(0));
        float cleared = state.clearing.advance(now, typed);
        cleared = Math.max(0.0F, Math.min(1.0F, cleared));
        drawIcon(state, laid, LostTalesUiSheet.SEARCH,
                LostTalesUiSheet.SEARCH_HOVER, Math.round(ink * (1.0F - cleared)));
        drawIcon(state, laid, LostTalesUiSheet.CLOSE,
                LostTalesUiSheet.CLOSE_HOVER, Math.round(ink * cleared));
        if (!laid.counting) {
            return;
        }
        // The count, right-aligned against the chevrons, or against the
        // icon over a page.
        String count = front.searchCount();
        LostTalesUiInk.drawText(font, count,
                laid.countRight - font.getStringWidth(count), laid.textTop,
                front.searchFound() <= 0 ? WindowStyle.asideRgb()
                        : LostTalesUiInk.IVORY, ink);
        if (!laid.walking) {
            return;
        }
        state.previousMotion.advance(now, under == Part.PREVIOUS);
        state.nextMotion.advance(now, under == Part.NEXT);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CHEVRON_5,
                LostTalesUiSheet.CHEVRON_5_HOVER, state.previousMotion,
                laid.previousX, glyphTop(laid, LostTalesUiSheet.CHEVRON_5
                        .getHeight()), ink);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CHEVRON_1,
                LostTalesUiSheet.CHEVRON_1_HOVER, state.nextMotion, laid.nextX,
                glyphTop(laid, LostTalesUiSheet.CHEVRON_1.getHeight()), ink);
    }

    /**
     * What the well says while nothing is typed in it — {@code Search}
     * and the name of the conversation in front, or what a page says it
     * searches — in the aside tone and in italics, sinking into the well's
     * end where it is cut, as a cut tab name does.
     */
    private static void drawPrompt(FontRenderer font, Layout laid,
                                   WindowPage front, TabRow.Row row, int x,
                                   int alpha) {
        String prompt = "§o" + front.searchPrompt();
        int right = laid.iconSlotLeft - GAP;
        int room = right - x;
        if (room <= 0) {
            return;
        }
        int width = font.getStringWidth(prompt);
        float depth = LostTalesUiFading.sideFadeDepth(room);
        LostTalesUiFading.drawFadingText(Minecraft.getMinecraft(),
                font, prompt, x, 0.0F, laid.textTop,
                WindowStyle.asideRgb(), alpha,
                x + row.fractionX, right + row.fractionX, Double.NaN, depth,
                0.0F, LostTalesUiFading.sideFadeStrength(
                        width - room, depth));
    }

    /** One of the well's two icons, centred in its slot on the text's capitals. */
    private static void drawIcon(State state, Layout laid,
                                 LostTalesUiSheet resting,
                                 LostTalesUiSheet lit, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        LostTalesUiButton.drawGlyph(resting, lit, state.iconMotion,
                laid.iconSlotLeft + Math.floorDiv(
                        LostTalesUiSheet.SEARCH.getWidth() - resting.getWidth(), 2),
                glyphTop(laid, resting.getHeight()), alpha);
    }

    /**
     * One of the strip's buttons: resting lit while {@code lit} (what it
     * opens is out) or the pointer is on it, lifting under the pointer;
     * greyed with no motion while it has nothing to do.
     */
    private static void drawButton(LostTalesUiButtonMotion motion,
                                   boolean acts, boolean lit, Part under,
                                   Part part, LostTalesUiSheet glyph,
                                   LostTalesUiSheet litGlyph, int x, int y,
                                   long now, int ink) {
        if (!acts) {
            drawGreyed(glyph, x, y, ink);
            return;
        }
        motion.advance(now, lit || under == part, under == part,
                under == part && Mouse.isButtonDown(0));
        LostTalesUiButton.drawGlyph(glyph, litGlyph, motion, x, y, ink);
    }

    /**
     * The page's option buttons: each resting lit while it is on or its
     * words are out ({@code pickOut}), lifting under the pointer; one that
     * cannot be taken greyed and still.
     */
    private static void drawOptions(State state, Layout laid,
                                    PageOption under, String pickOut,
                                    long now, int ink) {
        for (int index = 0; index < laid.options.length; index++) {
            PageOption option = laid.options[index];
            OptionGlyph glyph = option.glyph;
            int x = laid.optionX[index];
            int y = glyphTop(laid, glyph.height());
            if (!option.isAvailable()) {
                glyph.draw(x, y, 0.0F,
                        Math.round(ink * WindowStyle.UNAVAILABLE_OPACITY));
                continue;
            }
            LostTalesUiButtonMotion motion = state.optionMotions.get(option.id);
            if (motion == null) {
                motion = new LostTalesUiButtonMotion(
                        LostTalesUiButtonMotion.Character.LIFT);
                state.optionMotions.put(option.id, motion);
            }
            boolean hovered = under != null && under.id.equals(option.id);
            boolean lit = option.on || hovered || option.id.equals(pickOut);
            motion.advance(now, lit, hovered,
                    hovered && Mouse.isButtonDown(0));
            LostTalesUiButton.beginPose(motion, x, y, glyph.width(),
                    glyph.height());
            try {
                glyph.draw(x, y, motion.lit(), ink);
            } finally {
                LostTalesUiButton.endPose();
            }
        }
    }

    /**
     * Which of what the tab in front opens is out: its settings, its split
     * view, its help, and the option whose words are out ({@code pick},
     * empty for none).
     */
    public static final class Out {
        final boolean settings;
        final boolean split;
        final boolean help;
        final String pick;

        public Out(boolean settings, boolean split, boolean help, String pick) {
            this.settings = settings;
            this.split = split;
            this.help = help;
            this.pick = pick == null ? "" : pick;
        }
    }

    /** A control with nothing to do: its resting glyph, faint, with no motion. */
    private static void drawGreyed(LostTalesUiSheet glyph, int x, int y,
                                   int alpha) {
        LostTalesUiSheet.drawPairWithShadow(glyph, glyph, 0.0F, x, y,
                Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY));
    }

    /**
     * Why a part of {@code window}'s strip has nothing to do for the tab
     * in front, for its tip: a cog with no settings, a split with no page
     * to stand beside, a well with nothing to search; empty while it acts,
     * and for what the padlock holds ({@link #heldByPadlock}).
     */
    static String greyedWhy(Part part, Window window) {
        WindowPage front = window == null ? null : window.getActiveTab();
        if (part == null || front == null || heldByPadlock(part, window)) {
            return "";
        }
        switch (part) {
            case SETTINGS:
                return front.settingsPlace() != null ? ""
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.window.cog.nothing",
                                front.title());
            case SPLIT:
                return TabMenus.canSplit(front) ? ""
                        : StatCollector.translateToLocalFormatted(
                                "gui.losttales.window.split.nothing",
                                front.title());
            case FIELD:
            case ICON:
                return front.searchUnavailable();
            default:
                return "";
        }
    }

    /**
     * Whether the padlock holds what a part of {@code window}'s strip
     * would do: a locked window's split. It stands greyed with no tip,
     * and a press lights the padlock.
     */
    static boolean heldByPadlock(Part part, Window window) {
        return part == Part.SPLIT && window != null && window.isLocked();
    }

    /** A glyph's top: centred on the well's capitals, the odd pixel up. */
    private static int glyphTop(Layout laid, int height) {
        return laid.textTop + WindowStyle.centredBoxTop(height);
    }

    /**
     * What a screen point lands on, or null off the strip's controls:
     * asked with the row's fraction taken off, as the tab row's own hit
     * tests are.
     */
    public Part partAt(WindowFrame frame, TabRow.Row row,
                double mouseX, double mouseY) {
        Layout laid = frame == null || row == null ? null
                : frame.toolStrip.layout;
        if (laid == null) {
            return null;
        }
        double x = mouseX - row.fractionX;
        double y = mouseY - row.fractionY;
        if (laid.panelWidth > 0 && glyphBox(laid, laid.panelX,
                laid.panelWidth, laid.panelHeight).contains(x, y)) {
            return Part.PANEL;
        }
        if (optionIndexAt(laid, x, y) >= 0) {
            return Part.OPTION;
        }
        if (glyphBox(laid, laid.settingsX, COG_WIDTH, COG_HEIGHT)
                .contains(x, y)) {
            return Part.SETTINGS;
        }
        if (glyphBox(laid, laid.splitX, SPLIT_WIDTH, SPLIT_HEIGHT)
                .contains(x, y)) {
            return Part.SPLIT;
        }
        if (glyphBox(laid, laid.viewX, VIEW_WIDTH, VIEW_HEIGHT).contains(x, y)) {
            return Part.VIEW;
        }
        if (laid.hasMembers && glyphBox(laid, laid.membersX, MEMBERS_WIDTH,
                MEMBERS_HEIGHT).contains(x, y)) {
            return Part.MEMBERS_TOGGLE;
        }
        if (glyphBox(laid, laid.helpX, HELP_WIDTH, HELP_HEIGHT).contains(x, y)) {
            return Part.HELP;
        }
        if (!laid.hasWell) {
            return null;
        }
        if (laid.walking) {
            if (glyphBox(laid, laid.nextX, LostTalesUiSheet.CHEVRON_1.getWidth(),
                    LostTalesUiSheet.CHEVRON_1.getHeight()).contains(x, y)) {
                return Part.NEXT;
            }
            if (glyphBox(laid, laid.previousX,
                    LostTalesUiSheet.CHEVRON_5.getWidth(),
                    LostTalesUiSheet.CHEVRON_5.getHeight()).contains(x, y)) {
                return Part.PREVIOUS;
            }
        }
        if (!LostTalesUiHitBox.contains(x, y, laid.wellLeft, laid.wellTop,
                laid.wellRight - laid.wellLeft, WELL_HEIGHT)) {
            return null;
        }
        return x >= laid.iconSlotLeft - SLACK ? Part.ICON : Part.FIELD;
    }

    /**
     * The option whose button a screen point lands on, or null: asked
     * with the row's fraction taken off, as {@link #partAt} is.
     */
    public PageOption optionAt(WindowFrame frame, TabRow.Row row,
                               double mouseX, double mouseY) {
        Layout laid = frame == null || row == null ? null
                : frame.toolStrip.layout;
        if (laid == null) {
            return null;
        }
        int index = optionIndexAt(laid, mouseX - row.fractionX,
                mouseY - row.fractionY);
        return index < 0 ? null : laid.options[index];
    }

    /** Which of the strip's option buttons a point lands on, or -1. */
    private static int optionIndexAt(Layout laid, double x, double y) {
        for (int index = 0; index < laid.options.length; index++) {
            OptionGlyph glyph = laid.options[index].glyph;
            if (glyphBox(laid, laid.optionX[index], glyph.width(),
                    glyph.height()).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    /** A glyph's ink with the clear pixels that answer with it. */
    private static LostTalesUiHitBox glyphBox(Layout laid, int x, int width,
                                              int height) {
        return new LostTalesUiHitBox(x, glyphTop(laid, height), width, height)
                .grown(SLACK);
    }

    /** A press on the field: the caret goes where the pointer is. */
    public void clickField(TabRow.Row row, double mouseX, double mouseY) {
        if (this.field != null && row != null) {
            this.field.mouseClicked((int) Math.floor(mouseX - row.fractionX),
                    (int) Math.floor(mouseY - row.fractionY), 0);
        }
    }

    /** The words beside the pointer for a control of {@code window}'s strip; none for the field. */
    public static String tipFor(Part part, Window window) {
        if (part == null) {
            return "";
        }
        WindowPage front = window == null ? null : window.getActiveTab();
        if (front == null) {
            return "";
        }
        String greyed = greyedWhy(part, window);
        if (greyed.length() > 0) {
            return greyed;
        }
        if (heldByPadlock(part, window)) {
            return "";
        }
        switch (part) {
            case PANEL: {
                Panel panel = front.panel();
                return panel == null ? ""
                        : StatCollector.translateToLocal(
                                front.isPanelOut(window)
                                        ? panel.hideKey : panel.showKey);
            }
            case OPTION:
                return "";
            case SETTINGS:
                return StatCollector.translateToLocal(
                        front.settingsPlace().titleKey);
            case SPLIT:
                return StatCollector.translateToLocal(
                        "gui.losttales.window.split.title");
            case HELP:
                return StatCollector.translateToLocalFormatted(
                        "gui.losttales.window.help", front.title());
            case MEMBERS_TOGGLE:
                return StatCollector.translateToLocal(
                        front.isMemberListOut(window)
                                ? "gui.losttales.window.members.hide"
                                : "gui.losttales.window.members.show");
            case VIEW:
                return StatCollector.translateToLocalFormatted(
                        "gui.losttales.window.view.enter", front.title());
            case ICON:
                return WindowSearch.isOpenOn(window.getId())
                        && WindowSearch.query().length() > 0
                        ? StatCollector.translateToLocal(
                                "gui.losttales.window.search.close")
                        : "";
            case PREVIOUS:
                return StatCollector.translateToLocal(
                        "gui.losttales.window.search.previous");
            case NEXT:
                return StatCollector.translateToLocal(
                        "gui.losttales.window.search.next");
            default:
                return "";
        }
    }
}
