package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

/**
 * A window's tool strip under its tab row: the controls that read the
 * tab in front rather than pick one. At its left, under the tab search,
 * the panel button: over a conversation the timestamp area's person, for
 * the heads the area holds, which drives the area out of the window and
 * back in; over a page the page's own panel, the journal's quest list.
 * At its right end the search, a well a third of the strip wide naming
 * what it searches — {@code Search Global}, {@code Search active quests}
 * — with its magnifier at the well's right end; before the well the
 * member list's button, two people, which a page has none of, and before
 * that the cog, which opens the tab's menu. The panel buttons rest lit
 * while their panels are out, and the cog while its menu is.
 * While a search stands in a well, the count stands inside the well
 * before its end, and the magnifier has crossed over to the cross that
 * clears it: over a conversation the match stood on of how many, with
 * the chevrons walking them; over a page how many entries it found.
 *
 * <p>Every window's strip shows its well; the search itself is one at a
 * time, over the window being typed in ({@link ChatSearch}), and its one
 * field moves to the window it is open on. Everything stands on the
 * strip's centre row, laid out from the strip's edges as the tabs are,
 * and each control answers the pointer on the box it is drawn in: a
 * glyph on its ink with {@link #SLACK} clear pixels round it, the field
 * on its well. Each window keeps its controls' motions in its own
 * {@link State}, on its frame.</p>
 */
final class ChatToolStrip {
    /** What a point on a strip lands on. */
    enum Part {
        /** The panel button at the left end: the timestamp area's, or a page's own panel's. */
        PANEL,
        /** The channel's cog: the menu of the tab in front. */
        SETTINGS,
        MEMBERS_TOGGLE,
        FIELD,
        /** The well's magnifier, or the cross it becomes while a search stands. */
        ICON,
        PREVIOUS,
        NEXT
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
    private static final int MEMBERS_WIDTH = LostTalesUiSheet.MEMBERS.getWidth();
    private static final int MEMBERS_HEIGHT =
            LostTalesUiSheet.MEMBERS.getHeight();
    private static final int COG_WIDTH = LostTalesUiSheet.COG.getWidth();
    private static final int COG_HEIGHT = LostTalesUiSheet.COG.getHeight();

    /** Where one window's strip stands this frame, in its row's space. */
    static final class Layout {
        int stripTop;
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
        int settingsX;
        int membersX;
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
        /** The cog only rises: a quarter turn leaves it as it was. */
        final LostTalesUiButtonMotion settingsMotion =
                new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
        final LostTalesUiButtonMotion membersMotion =
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
                new MotionTransition(MotionIds.CHAT_SEARCH_CLEAR);
    }

    private ChatInputField field;

    void bind(FontRenderer font) {
        if (this.field == null && font != null) {
            this.field = new ChatInputField(font, 0, 0, 10, WELL_HEIGHT - 3)
                    .plainText();
            this.field.setMaxStringLength(MAX_QUERY);
            this.field.setEnableBackgroundDrawing(false);
            this.field.setTextColor(LostTalesChatVisualStyle.IVORY);
        }
    }

    boolean isFocused() {
        return ChatSearch.isOpen() && this.field != null && this.field.isFocused();
    }

    void focus(boolean on) {
        if (this.field != null) {
            this.field.setFocused(on);
        }
    }

    String text() {
        return this.field == null ? "" : this.field.getText();
    }

    void setText(String text) {
        if (this.field != null) {
            this.field.setText(text == null ? "" : text);
            this.field.setCursorPositionEnd();
        }
    }

    boolean keyTyped(char typedChar, int keyCode) {
        return this.field != null && this.field.textboxKeyTyped(typedChar, keyCode);
    }

    /**
     * Lays a window's strip out for its row before the row is drawn, and
     * tells the row where the well is cut out of the strip's surface.
     * The search's field moves to the window its search is open on.
     */
    void prepare(FontRenderer font, ChatWindowFrame frame,
                 ChatChannelTabBar.Row row) {
        if (font == null || frame == null || row == null) {
            return;
        }
        ChatPageContent page = ChatPages.contentOf(frame.page);
        boolean typed = ChatSearch.isOpenOn(frame.windowId)
                && ChatSearch.query().length() > 0;
        LostTalesUiSheet panel = panelGlyph(page);
        Layout laid = layOut(ChatChannelTabBar.toolStripLeft(row),
                (int)Math.floor(frame.tabBar.toolStripRight(font, row)),
                row.rowBottom, ChatChannelTabBar.searchButtonLeft(row),
                ChatChannelTabBar.searchButtonSize(),
                panel == null ? 0 : panel.getWidth(),
                panel == null ? 0 : panel.getHeight(), page == null,
                !typed ? Count.NONE : page == null ? Count.WALK : Count.FOUND,
                font.getStringWidth(countText(page)));
        frame.toolStrip.layout = laid;
        frame.tabBar.setToolStripHole(laid.hasWell
                ? new LostTalesUiHitBox(laid.wellLeft, laid.wellTop,
                        laid.wellRight - laid.wellLeft, WELL_HEIGHT)
                : null);
        if (this.field != null && laid.hasWell
                && ChatSearch.isOpenOn(frame.windowId)) {
            this.field.xPosition = laid.fieldX;
            this.field.yPosition = laid.textTop;
            this.field.width = laid.fieldWidth;
        }
    }

    /**
     * Where everything on a strip stands: the panel button, a glyph
     * {@code panelWidth} by {@code panelHeight} (none for a width of 0),
     * centred under the tab search; the well against the strip's right
     * end, {@link #EDGE_MARGIN} in, a third of the strip wide; the member
     * list's button, where the strip has one, and the cog before it. A
     * strip whose third is too narrow for a well, or leaves the buttons no
     * room, keeps none, and the buttons stand at its right end. A
     * {@code count} stands its text, {@code countWidth} wide, inside the
     * well before its icon, with the chevrons before the icon for a
     * {@link Count#WALK}, where the field leaves them room. Row space.
     */
    static Layout layOut(int stripLeft, int stripRight, int stripTop,
                         int searchButtonLeft, int searchButtonSize,
                         int panelWidth, int panelHeight, boolean members,
                         Count count, int countWidth) {
        Layout laid = new Layout();
        laid.stripTop = stripTop;
        laid.wellTop = stripTop
                + (ChatWindowPlacement.TOOL_STRIP_HEIGHT - 1 - WELL_HEIGHT) / 2;
        laid.wellBottom = laid.wellTop + WELL_HEIGHT;
        laid.textTop = laid.wellTop + 2;
        laid.panelWidth = panelWidth;
        laid.panelHeight = panelHeight;
        laid.panelX = searchButtonLeft
                + Math.floorDiv(searchButtonSize - panelWidth, 2);
        laid.hasMembers = members;
        laid.wellRight = stripRight - EDGE_MARGIN;
        laid.wellLeft = laid.wellRight
                - Math.floorDiv(stripRight - stripLeft, 3);
        int buttons = COG_WIDTH + END_GAP
                + (members ? MEMBERS_WIDTH + END_GAP : 0);
        int floor = panelWidth > 0 ? laid.panelX + panelWidth + END_GAP
                : searchButtonLeft + searchButtonSize + END_GAP;
        laid.hasWell = laid.wellRight - laid.wellLeft >= MIN_WELL_WIDTH
                && laid.wellLeft - buttons >= floor;
        int buttonsRight = laid.hasWell ? laid.wellLeft - END_GAP
                : stripRight - EDGE_MARGIN;
        laid.membersX = buttonsRight - MEMBERS_WIDTH;
        laid.settingsX = (members ? laid.membersX - END_GAP : buttonsRight)
                - COG_WIDTH;
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
     * The count a standing search shows: over a conversation where the
     * walk is, of how many; over a page how many entries it found.
     */
    private static String countText(ChatPageContent page) {
        return page == null
                ? ChatSearch.position() + "/" + ChatSearch.matchCount()
                : String.valueOf(Math.max(0, page.found()));
    }

    /** The panel button's glyph: the timestamp area's person, a page's own, or null for none. */
    private static LostTalesUiSheet panelGlyph(ChatPageContent page) {
        if (page == null) {
            return LostTalesUiSheet.AREA;
        }
        ChatPageContent.Panel panel = page.panel();
        return panel == null ? null : panel.glyph;
    }

    /**
     * Draws a window's strip as laid out by {@link #prepare}, inside the
     * row's own matrix; {@code under} is the part the pointer is on, if
     * it is on this strip, and {@code menuOut} whether the menu of the
     * tab in front is out.
     */
    void draw(FontRenderer font, ChatWindowFrame frame, ChatWindow window,
              ChatChannelTabBar.Row row, float alphaScale, Part under,
              boolean menuOut) {
        State state = frame == null ? null : frame.toolStrip;
        Layout laid = state == null ? null : state.layout;
        if (laid == null || font == null || window == null || row == null) {
            return;
        }
        long now = System.nanoTime();
        int ink = Math.round(255.0F * alphaScale);
        ChatPageContent page = ChatPages.contentOf(frame.page);
        // Each panel's button rests lit while its panel is out, as a
        // messenger's member-list button does, and lifts under the
        // pointer either way.
        if (laid.panelWidth > 0) {
            ChatPageContent.Panel panel = page == null ? null : page.panel();
            boolean out = page == null ? !window.isAreaHidden()
                    : page.isPanelOut();
            state.panelMotion.advance(now, out || under == Part.PANEL,
                    under == Part.PANEL,
                    under == Part.PANEL && Mouse.isButtonDown(0));
            LostTalesUiButton.drawGlyph(
                    panel == null ? LostTalesUiSheet.AREA : panel.glyph,
                    panel == null ? LostTalesUiSheet.AREA_HOVER
                            : panel.litGlyph,
                    state.panelMotion, laid.panelX,
                    glyphTop(laid, laid.panelHeight), ink);
        }
        state.settingsMotion.advance(now, menuOut || under == Part.SETTINGS,
                under == Part.SETTINGS,
                under == Part.SETTINGS && Mouse.isButtonDown(0));
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.COG,
                LostTalesUiSheet.COG_HOVER, state.settingsMotion,
                laid.settingsX, glyphTop(laid, COG_HEIGHT), ink);
        if (laid.hasMembers) {
            state.membersMotion.advance(now, !window.isMembersHidden()
                            || under == Part.MEMBERS_TOGGLE,
                    under == Part.MEMBERS_TOGGLE,
                    under == Part.MEMBERS_TOGGLE && Mouse.isButtonDown(0));
            LostTalesUiButton.drawGlyph(LostTalesUiSheet.MEMBERS,
                    LostTalesUiSheet.MEMBERS_HOVER, state.membersMotion,
                    laid.membersX, glyphTop(laid, MEMBERS_HEIGHT), ink);
        }
        if (!laid.hasWell) {
            return;
        }
        boolean searching = ChatSearch.isOpenOn(window.getId());
        Minecraft minecraft = Minecraft.getMinecraft();
        int surfaceAlpha = Math.round(LostTalesChatVisualStyle.INSET_ALPHA
                * alphaScale * LostTalesChatVisualStyle.chatOpacity(minecraft));
        // The well, in the hole the strip left for it: one surface, a
        // step darker than the strip, as the input bar's typing well is.
        LostTalesUiInk.fillRect(laid.wellLeft, laid.wellTop,
                laid.wellRight, laid.wellBottom,
                LostTalesChatVisualStyle.argb(LostTalesChatVisualStyle.SURFACE_RGB,
                        surfaceAlpha));
        LostTalesChatVisualStyle.beginContent();
        boolean typed = searching && ChatSearch.query().length() > 0;
        if (searching && this.field != null) {
            if (this.field.getText().length() == 0) {
                drawPrompt(font, laid, window, page, row,
                        this.field.xPosition + LostTalesUiCaret.WIDTH + 1,
                        ink);
            }
            this.field.drawTextBox();
        } else {
            drawPrompt(font, laid, window, page, row, laid.fieldX
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
        String count = countText(page);
        int found = page == null ? ChatSearch.matchCount() : page.found();
        LostTalesChatVisualStyle.drawColored(font, count,
                laid.countRight - font.getStringWidth(count), laid.textTop,
                found <= 0 ? LostTalesChatVisualStyle.asideRgb()
                        : LostTalesChatVisualStyle.IVORY, ink);
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
     * What the well says while nothing is typed in it: {@code Search}
     * and the name of the tab in front, or what a page says it searches,
     * in the chat's aside tone and in italics, sinking into the well's end
     * where it is cut, as a cut tab name does.
     */
    private static void drawPrompt(FontRenderer font, Layout laid,
                                   ChatWindow window, ChatPageContent page,
                                   ChatChannelTabBar.Row row, int x,
                                   int alpha) {
        ChatTab tab = window.getActiveTab();
        String prompt = "§o" + (page != null ? page.searchPrompt()
                : StatCollector.translateToLocalFormatted(
                        "gui.losttales.chat.message_search.prompt",
                        tab == null ? ""
                                : ClientChatChannelState.displayName(tab)));
        int right = laid.iconSlotLeft - GAP;
        int room = right - x;
        if (room <= 0) {
            return;
        }
        int width = font.getStringWidth(prompt);
        float depth = LostTalesChatOverlayRenderer.sideFadeDepth(room);
        LostTalesChatOverlayRenderer.drawFadingText(Minecraft.getMinecraft(),
                font, prompt, x, 0.0F, laid.textTop,
                LostTalesChatVisualStyle.asideRgb(), alpha,
                x + row.fractionX, right + row.fractionX, Double.NaN, depth,
                0.0F, LostTalesChatOverlayRenderer.sideFadeStrength(
                        width - room, depth));
    }

    /** One of the well's two icons, centred in its slot on the text's capitals. */
    private static void drawIcon(State state, Layout laid,
                                 LostTalesUiSheet resting,
                                 LostTalesUiSheet lit, int alpha) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        LostTalesUiButton.drawGlyph(resting, lit, state.iconMotion,
                laid.iconSlotLeft + Math.floorDiv(
                        LostTalesUiSheet.SEARCH.getWidth() - resting.getWidth(), 2),
                glyphTop(laid, resting.getHeight()), alpha);
    }

    /** A glyph's top: centred on the well's capitals, the odd pixel up. */
    private static int glyphTop(Layout laid, int height) {
        return laid.textTop + LostTalesChatOverlayRenderer.centredBoxTop(height);
    }

    /**
     * What a screen point lands on, or null off the strip's controls:
     * asked with the row's fraction taken off, as the tab row's own hit
     * tests are.
     */
    Part partAt(ChatWindowFrame frame, ChatChannelTabBar.Row row,
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
        if (glyphBox(laid, laid.settingsX, COG_WIDTH, COG_HEIGHT)
                .contains(x, y)) {
            return Part.SETTINGS;
        }
        if (laid.hasMembers && glyphBox(laid, laid.membersX, MEMBERS_WIDTH,
                MEMBERS_HEIGHT).contains(x, y)) {
            return Part.MEMBERS_TOGGLE;
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

    /** A glyph's ink with the clear pixels that answer with it. */
    private static LostTalesUiHitBox glyphBox(Layout laid, int x, int width,
                                              int height) {
        return new LostTalesUiHitBox(x, glyphTop(laid, height), width, height)
                .grown(SLACK);
    }

    /** A press on the field: the caret goes where the pointer is. */
    void clickField(ChatChannelTabBar.Row row, double mouseX, double mouseY) {
        if (this.field != null && row != null) {
            this.field.mouseClicked((int) Math.floor(mouseX - row.fractionX),
                    (int) Math.floor(mouseY - row.fractionY), 0);
        }
    }

    /** The words beside the pointer for a control of {@code window}'s strip; none for the field. */
    static String tipFor(Part part, ChatWindow window) {
        if (part == null) {
            return "";
        }
        ChatTab front = window == null ? null : window.getActiveTab();
        ChatPageContent page = ChatPages.contentOf(front);
        switch (part) {
            case PANEL:
                if (page != null) {
                    ChatPageContent.Panel panel = page.panel();
                    return panel == null ? ""
                            : StatCollector.translateToLocal(page.isPanelOut()
                                    ? panel.hideKey : panel.showKey);
                }
                return StatCollector.translateToLocal(window != null
                        && window.isAreaHidden()
                        ? "gui.losttales.chat.area.show"
                        : "gui.losttales.chat.area.hide");
            case SETTINGS:
                return page != null
                        ? StatCollector.translateToLocalFormatted(
                                "gui.losttales.chat.page.settings",
                                ClientChatChannelState.displayName(front))
                        : StatCollector.translateToLocal(
                                "gui.losttales.chat.tab.settings");
            case MEMBERS_TOGGLE:
                return StatCollector.translateToLocal(window != null
                        && window.isMembersHidden()
                        ? "gui.losttales.chat.members.show"
                        : "gui.losttales.chat.members.hide");
            case ICON:
                return window != null && ChatSearch.isOpenOn(window.getId())
                        && ChatSearch.query().length() > 0
                        ? StatCollector.translateToLocal(
                                "gui.losttales.chat.search.close")
                        : "";
            case PREVIOUS:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.search.previous");
            case NEXT:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.search.next");
            default:
                return "";
        }
    }
}
