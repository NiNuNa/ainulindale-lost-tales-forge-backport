package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * The search bar in a window's tool strip, the strip's reason to be:
 * the magnifier, lit while the field takes the keys or the pointer is
 * on its well, a field in a well cut out of the strip, the count of
 * matches and the place stood on, an up and a down chevron to walk
 * them, and a cross to close. Everything stands on the strip's one
 * centre row, laid out from the strip's edges as the tabs are, and
 * each control answers the pointer on the box it is drawn in — the
 * chevrons and the cross on nine-pixel squares round their glyphs,
 * the field on its well. Drawn for the window being typed in alone.
 */
final class ChatSearchBar {
    /** What a point on the bar lands on. */
    enum Part {
        FIELD,
        PREVIOUS,
        NEXT,
        CLOSE
    }

    static final int GAP = 3;
    /** The well: one message row, a clear row above and below the capitals' seven. */
    static final int WELL_HEIGHT = 12;
    /** The square a glyph control answers on. */
    static final int CONTROL_BOX = 9;
    private static final int MAX_QUERY = 64;
    private static final String WIDEST_COUNT = "999/999";

    /** Where everything stands, in the row's own space. */
    private static final class Layout {
        int stripTop;
        int wellLeft;
        int wellTop;
        int wellRight;
        int wellBottom;
        int textTop;
        int magnifierX;
        int fieldX;
        int fieldWidth;
        int countRight;
        int previousX;
        int nextX;
        int closeX;
    }

    private ChatInputField field;
    private Layout layout;
    private String layoutWindowId;
    /**
     * The bar's four buttons, each keeping its own beat: the magnifier
     * turns on its handle, the two chevrons rise, and the cross answers
     * like a switch, exactly as the strip's do.
     */
    private final LostTalesUiButtonMotion magnifierMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.TURN);
    private final LostTalesUiButtonMotion previousMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion nextMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion closeMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.SNAP);
    private long frameNanos;

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

    /** Ticks the caret blink; called from the screen's updateScreen. */
    void tick() {
        if (this.field != null) {
            this.field.updateCursorCounter();
        }
    }

    /**
     * Lays the bar out for a window's row before the row is drawn, and
     * tells the strip where the well is cut out of it. Nothing while the
     * search is not open there.
     */
    void prepare(FontRenderer font, ChatWindowFrame frame,
                 ChatChannelTabBar.Row row) {
        if (font == null || frame == null || row == null
                || !ChatSearch.isOpenOn(frame.windowId)) {
            this.layout = null;
            this.layoutWindowId = null;
            if (frame != null) {
                frame.tabBar.setToolStripHole(null);
            }
            return;
        }
        Layout laid = new Layout();
        int stripLeft = ChatChannelTabBar.toolStripLeft(row);
        int stripRight = (int) Math.floor(frame.tabBar.toolStripRight(font, row));
        laid.stripTop = row.rowBottom;
        laid.wellTop = laid.stripTop
                + (ChatWindowPlacement.TOOL_STRIP_HEIGHT - 1 - WELL_HEIGHT) / 2;
        laid.wellBottom = laid.wellTop + WELL_HEIGHT;
        laid.textTop = laid.wellTop + 2;
        laid.closeX = stripRight - GAP - LostTalesUiSheet.CLOSE.getWidth();
        laid.nextX = laid.closeX - GAP - LostTalesUiSheet.CHEVRON_1.getWidth();
        laid.previousX = laid.nextX - GAP - LostTalesUiSheet.CHEVRON_5.getWidth();
        laid.countRight = laid.previousX - GAP;
        int countSlot = font.getStringWidth(WIDEST_COUNT);
        laid.wellLeft = stripLeft + GAP;
        laid.wellRight = laid.countRight - countSlot - GAP;
        laid.magnifierX = laid.wellLeft + 2;
        laid.fieldX = laid.magnifierX + LostTalesUiSheet.SEARCH.getWidth() + GAP;
        laid.fieldWidth = laid.wellRight - 2 - ChatInputField.CARET_WIDTH - laid.fieldX;
        if (laid.fieldWidth < 24) {
            // Too narrow a window for a search bar: nothing is drawn and
            // the strip keeps its surface whole.
            this.layout = null;
            this.layoutWindowId = null;
            frame.tabBar.setToolStripHole(null);
            return;
        }
        this.layout = laid;
        this.layoutWindowId = frame.windowId;
        frame.tabBar.setToolStripHole(new LostTalesUiHitBox(laid.wellLeft, laid.wellTop,
                laid.wellRight - laid.wellLeft, WELL_HEIGHT));
        if (this.field != null) {
            this.field.xPosition = laid.fieldX;
            this.field.yPosition = laid.textTop;
            this.field.width = laid.fieldWidth;
        }
    }

    /**
     * Draws the bar as laid out by {@link #prepare}, inside the row's
     * own matrix, with the pointer only while it is on the bar.
     */
    void draw(FontRenderer font, ChatWindowFrame frame, double mouseX,
              double mouseY, float alphaScale, Part under) {
        Layout laid = this.layout;
        if (laid == null || font == null || frame == null
                || !frame.windowId.equals(this.layoutWindowId)) {
            return;
        }
        long now = System.nanoTime();
        double elapsed = this.frameNanos == 0L ? 0.0D : (now - this.frameNanos) / 1.0E9D;
        this.frameNanos = now;
        Minecraft minecraft = Minecraft.getMinecraft();
        int surfaceAlpha = Math.round(LostTalesChatVisualStyle.INSET_ALPHA * alphaScale
                * LostTalesChatVisualStyle.chatOpacity(minecraft));
        int ink = Math.round(255.0F * alphaScale);
        // The well, in the hole the strip left for it: one surface, a
        // step darker than the strip, as the input bar's typing well is.
        LostTalesChatOverlayRenderer.fillRect(laid.wellLeft, laid.wellTop,
                laid.wellRight, laid.wellBottom,
                LostTalesChatVisualStyle.argb(LostTalesChatVisualStyle.SURFACE_RGB,
                        surfaceAlpha));
        LostTalesChatVisualStyle.beginContent();
        boolean onField = under == Part.FIELD;
        // The magnifier is lit while the field has the keys as well as
        // while the pointer is on it, but it only moves for the pointer.
        this.magnifierMotion.advance(now,
                onField || (this.field != null && this.field.isFocused()),
                onField, onField && org.lwjgl.input.Mouse.isButtonDown(0),
                LostTalesConfig.enableChatAnimations);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.SEARCH,
                LostTalesUiSheet.SEARCH_HOVER, this.magnifierMotion,
                laid.magnifierX,
                laid.textTop + LostTalesChatOverlayRenderer.centredBoxTop(
                        LostTalesUiSheet.SEARCH.getHeight()), ink);
        if (this.field != null) {
            if (this.field.getText().length() == 0) {
                int x = this.field.xPosition + ChatInputField.CARET_WIDTH + 1;
                String prompt = LostTalesSkyrimUiStyle.trimToWidth(font,
                        StatCollector.translateToLocal("gui.losttales.chat.search.prompt"),
                        this.field.xPosition + this.field.getWidth() - x);
                LostTalesChatVisualStyle.drawColored(font, "§o" + prompt, x,
                        laid.textTop, LostTalesChatVisualStyle.asideRgb(), ink);
            }
            this.field.drawTextBox();
        }
        // The count, right-aligned in its slot so it never moves the field.
        String count = ChatSearch.query().length() == 0 ? ""
                : ChatSearch.position() + "/" + ChatSearch.matchCount();
        if (count.length() > 0) {
            LostTalesChatVisualStyle.drawColored(font, count,
                    laid.countRight - font.getStringWidth(count), laid.textTop,
                    ChatSearch.matchCount() == 0
                            ? LostTalesChatVisualStyle.asideRgb()
                            : LostTalesChatVisualStyle.IVORY, ink);
        }
        boolean animate = LostTalesConfig.enableChatAnimations;
        this.previousMotion.advance(now, under == Part.PREVIOUS, animate);
        this.nextMotion.advance(now, under == Part.NEXT, animate);
        this.closeMotion.advance(now, under == Part.CLOSE, animate);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CHEVRON_5,
                LostTalesUiSheet.CHEVRON_5_HOVER, this.previousMotion,
                laid.previousX, glyphTop(laid, LostTalesUiSheet.CHEVRON_5),
                ink);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CHEVRON_1,
                LostTalesUiSheet.CHEVRON_1_HOVER, this.nextMotion, laid.nextX,
                glyphTop(laid, LostTalesUiSheet.CHEVRON_1), ink);
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CLOSE,
                LostTalesUiSheet.CLOSE_HOVER, this.closeMotion, laid.closeX,
                glyphTop(laid, LostTalesUiSheet.CLOSE), ink);
    }

    /** A glyph's top: centred on the capitals, its odd pixel below their middle. */
    private static int glyphTop(Layout laid, LostTalesUiSheet glyph) {
        return laid.textTop + LostTalesChatOverlayRenderer.centredBoxTop(glyph.getHeight());
    }

    /**
     * What a screen point lands on, or null off the bar: asked with the
     * row's fraction taken off, as the strip's own hit tests are.
     */
    Part partAt(ChatWindowFrame frame, ChatChannelTabBar.Row row, double mouseX,
                double mouseY) {
        Layout laid = this.layout;
        if (laid == null || frame == null || row == null
                || !frame.windowId.equals(this.layoutWindowId)) {
            return null;
        }
        double x = mouseX - row.fractionX;
        double y = mouseY - row.fractionY;
        if (controlBox(laid.closeX, laid, LostTalesUiSheet.CLOSE).contains(x, y)) {
            return Part.CLOSE;
        }
        if (controlBox(laid.nextX, laid, LostTalesUiSheet.CHEVRON_1).contains(x, y)) {
            return Part.NEXT;
        }
        if (controlBox(laid.previousX, laid, LostTalesUiSheet.CHEVRON_5).contains(x, y)) {
            return Part.PREVIOUS;
        }
        if (LostTalesUiHitBox.contains(x, y, laid.wellLeft, laid.wellTop,
                laid.wellRight - laid.wellLeft, WELL_HEIGHT)) {
            return Part.FIELD;
        }
        return null;
    }

    /** The nine-pixel square round a glyph, centred on it. */
    private static LostTalesUiHitBox controlBox(int glyphX, Layout laid, LostTalesUiSheet glyph) {
        double left = glyphX + glyph.getWidth() / 2.0D - CONTROL_BOX / 2.0D;
        double top = glyphTop(laid, glyph) + glyph.getHeight() / 2.0D - CONTROL_BOX / 2.0D;
        return new LostTalesUiHitBox(left, top, CONTROL_BOX, CONTROL_BOX);
    }

    /** A press on the field: the caret goes where the pointer is. */
    void clickField(ChatChannelTabBar.Row row, double mouseX, double mouseY) {
        if (this.field != null && row != null) {
            this.field.mouseClicked((int) Math.floor(mouseX - row.fractionX),
                    (int) Math.floor(mouseY - row.fractionY), 0);
        }
    }

    /** The words beside the pointer for a control; none for the field. */
    static String tipFor(Part part) {
        if (part == Part.PREVIOUS) {
            return StatCollector.translateToLocal("gui.losttales.chat.search.previous");
        }
        if (part == Part.NEXT) {
            return StatCollector.translateToLocal("gui.losttales.chat.search.next");
        }
        if (part == Part.CLOSE) {
            return StatCollector.translateToLocal("gui.losttales.chat.search.close");
        }
        return "";
    }
}
