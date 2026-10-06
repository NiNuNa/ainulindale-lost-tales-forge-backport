package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowDrawing;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

/**
 * The map's legend: which kinds of marker the map shows, listed as the
 * map's options menu lists them. The Show heading stands over a row for
 * each kind, its glyph before its name, struck through while the map
 * leaves the kind out; a kind the map shows rests lit, as a chosen row
 * does, and a press on a row shows or hides its kind. It stands on a
 * popup's surface in the window frame, centred over the map's foot, and
 * where the map is too short for every row the rows scroll as a menu's do.
 */
@SideOnly(Side.CLIENT)
final class LostTalesLotrMapLegend {
    static final int GAP_ABOVE_CONTROL_BAR = 4;

    /** Clear room the legend keeps from the map's sides and top. */
    private static final int OUTER_MARGIN = 6;
    /** The window frame a popup wears inside its footprint. */
    private static final int FRAME = WindowStyle.POPUP_FRAME;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The least room across, inside the margins, the legend stands in. */
    private static final int MIN_ROOM = 60;
    /** The fewest rows it shows: the heading and one kind. */
    private static final int MIN_ROWS = 2;
    /** Closer than this to its target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.1D;

    private LostTalesLotrMapLegend() {
    }

    /**
     * How far a map's legend is scrolled, kept by its map: the wheel's
     * target and the offset the rows are drawn at, gliding after it with
     * the windows' scroll motion, in pixels.
     */
    static final class Scroll {
        private double target;
        private double shown;
        private long nanos;

        /** Back at the top: the legend folded away. */
        void reset() {
            this.target = 0.0D;
            this.shown = 0.0D;
            this.nanos = 0L;
        }
    }

    static int getReservedHeight(LostTalesLotrMapGui gui) {
        Layout layout = currentLayout(gui);
        return layout.visible
                ? layout.panelHeight + GAP_ABOVE_CONTROL_BAR : 0;
    }

    /** Height followed by fixed overlays while the legend flies into place. */
    static int getAnimatedReservedHeight(LostTalesLotrMapGui gui) {
        int target = getReservedHeight(gui);
        if (target == 0) {
            return 0;
        }
        return Math.round(target * LostTalesMapPopupAnimation.easedProgress(
                gui.getMapLegendAnimationKey()));
    }

    static boolean render(
            LostTalesLotrMapGui gui, int mouseX, int mouseY) {
        Layout layout = currentLayout(gui);
        if (!layout.visible) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        Scroll scroll = gui.getMapLegendScroll();
        glide(scroll, layout);
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                gui.getMapLegendAnimationKey(), mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                gui.getMapLegendAnimationKey(), mouseY, pivotY);
        int hovered = categoryAt(layout, scroll.shown, localMouseX,
                localMouseY, categories.size());

        beginUntranslatedRender(gui, pivotX, pivotY);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawSurface(minecraft, layout, scroll, categories, hovered);
            drawRows(minecraft, font, layout, scroll, categories);
            // The fades hang from the frame on both edges.
            WindowLists.drawScroll(layout.boxLeft(), layout.boxTop(),
                    layout.boxRight(), layout.boxBottom(), layout.rowsTop(),
                    layout.rowsBottom(), scroll.shown, layout.maxScroll(),
                    255);
            LostTalesUiWindowFrame.drawEdges(layout.boxLeft(),
                    layout.boxTop(), layout.boxRight(), layout.boxBottom(),
                    255);
        } finally {
            endUntranslatedRender();
        }
        return true;
    }

    /**
     * What the legend stands on, as a sub-window does: what lies behind it
     * cut away and the world under it softened, the window's inset surface
     * with the frame's ring, and every row a press would find lit — the
     * kinds the map shows and the row under the pointer — as that surface
     * recoloured, cut to the band the rows glide in, the ring beside it
     * with it.
     */
    private static void drawSurface(Minecraft minecraft, Layout layout,
                                    Scroll scroll,
                                    List<LostTalesMapLegendCategory> categories,
                                    int hovered) {
        float left = layout.boxLeft();
        float top = layout.boxTop();
        float right = layout.boxRight();
        float bottom = layout.boxBottom();
        LostTalesUiHitBox stands = new LostTalesUiHitBox(left, top,
                right - left, bottom - top);
        WindowDrawing.cutBehind(stands, 1.0F);
        WindowDrawing.softenBehind(stands, 1.0F);
        int surface = WindowStyle.insetArgb(WindowStyle.opacity(minecraft));
        int surfaceAlpha = surface >>> 24;
        LostTalesUiInk.fillRect(left, top, right, bottom, surface);
        LostTalesUiWindowFrame.drawSurface(left, top, right, bottom, surface);
        int listTop = listTop(layout, scroll.shown);
        for (int index = 0; index < categories.size(); index++) {
            if (index != hovered && !LostTalesMapLegendRegistry
                    .isCategoryEnabled(categories.get(index).getId())) {
                continue;
            }
            // The heading stands first.
            int rowY = listTop + (index + 1) * layout.rowHeight;
            float litTop = Math.max(layout.rowsTop(), rowY);
            float litBottom = Math.min(layout.rowsBottom(),
                    rowY + layout.rowHeight);
            if (litBottom <= litTop) {
                continue;
            }
            WindowStyle.recolourFlat(left, litTop, right, litBottom,
                    surfaceAlpha, LostTalesUiInk.SURFACE_RGB,
                    LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
            WindowStyle.recolourRingBeside(left, right, left, litTop, right,
                    litBottom, surfaceAlpha, LostTalesUiInk.SURFACE_RGB,
                    LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
        }
    }

    /**
     * The heading and the kinds' rows the band shows, cut to it where they
     * glide past its edges: each kind's glyph in the icon column, struck
     * through while the map leaves the kind out, then its name in ivory.
     */
    private static void drawRows(Minecraft minecraft, FontRenderer font,
                                 Layout layout, Scroll scroll,
                                 List<LostTalesMapLegendCategory> categories) {
        boolean clipped = LostTalesUiClip.beginLocal(minecraft,
                layout.boxLeft(), layout.rowsTop(), layout.boxRight(),
                layout.rowsBottom());
        try {
            int listTop = listTop(layout, scroll.shown);
            int left = layout.boxLeft() + PADDING_X;
            int right = layout.boxRight() - PADDING_X;
            if (listTop + layout.rowHeight > layout.rowsTop()) {
                WindowLists.drawHeading(font, heading(), left, left, right,
                        listTop, layout.rowHeight, false, 255);
            }
            int labelLeft = left + TabIcons.SLOT + TabIcons.GAP;
            for (int index = 0; index < categories.size(); index++) {
                int rowY = listTop + (index + 1) * layout.rowHeight;
                if (rowY >= layout.rowsBottom()) {
                    break;
                }
                if (rowY + layout.rowHeight <= layout.rowsTop()) {
                    continue;
                }
                LostTalesMapLegendCategory category = categories.get(index);
                int labelTop = rowY + LostTalesUiInk.centredStart(
                        layout.rowHeight, LostTalesUiInk.CAP_HEIGHT);
                OptionGlyph glyph = LostTalesMapLegendGlyphs.of(category);
                if (!LostTalesMapLegendRegistry.isCategoryEnabled(
                        category.getId())) {
                    glyph = glyph.struck();
                }
                // Placed in the icon column as the options menu places it.
                glyph.draw(left + (TabIcons.SLOT - glyph.width()) / 2,
                        labelTop + LostTalesUiInk.centredStart(
                                LostTalesUiInk.CAP_HEIGHT, glyph.height()),
                        0.0F, 255);
                LostTalesUiInk.drawText(font, trimmed(font,
                                I18n.format(category.getTranslationKey()),
                                right - labelLeft), labelLeft, labelTop,
                        LostTalesUiInk.IVORY, 255);
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    static boolean handleMouseClick(
            LostTalesLotrMapGui gui, int mouseX, int mouseY, int button) {
        Layout layout = currentLayout(gui);
        if (!layout.visible) {
            return false;
        }
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                gui.getMapLegendAnimationKey(), mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                gui.getMapLegendAnimationKey(), mouseY, pivotY);
        if (!layout.containsPanel(mouseX, mouseY)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        int index = categoryAt(layout, gui.getMapLegendScroll().shown,
                mouseX, mouseY, categories.size());
        if (index >= 0) {
            LostTalesMapLegendRegistry.toggleCategory(
                    categories.get(index).getId());
            gui.onMapLegendFiltersChanged();
        }
        return true;
    }

    /** The kinds' rows, which a press shows or hides. */
    static boolean isPointerOverAction(
            LostTalesLotrMapGui gui, int mouseX, int mouseY) {
        Layout layout = currentLayout(gui);
        if (!layout.visible) {
            return false;
        }
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                gui.getMapLegendAnimationKey(), mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                gui.getMapLegendAnimationKey(), mouseY, pivotY);
        return categoryAt(layout, gui.getMapLegendScroll().shown, mouseX,
                mouseY, LostTalesMapLegendRegistry.getCategories().size())
                >= 0;
    }

    /**
     * A turn of the wheel over the legend moves its rows by whole rows, as
     * a menu's do, and never zooms the map under it.
     */
    static boolean handleMouseWheel(
            LostTalesLotrMapGui gui, int mouseX, int mouseY, int wheel) {
        Layout layout = currentLayout(gui);
        if (wheel == 0 || !layout.visible
                || !containsAnimatedPanel(gui, layout, mouseX, mouseY)) {
            return false;
        }
        Scroll scroll = gui.getMapLegendScroll();
        scroll.target = Math.max(0.0D, Math.min(layout.maxScroll(),
                scroll.target - WheelStep.pixels(WheelStep.menuRows(
                        WheelStep.lines(wheel, GuiScreen.isShiftKeyDown())),
                        layout.rowHeight)));
        return true;
    }

    private static Layout currentLayout(LostTalesLotrMapGui gui) {
        if (gui == null || !gui.isMapLegendOpen()
                || !LostTalesLotrMapLayout.hasFooterLayout(gui)) {
            return Layout.hidden();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft == null ? null : minecraft.fontRenderer;
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        if (font == null || categories.isEmpty()) {
            return Layout.hidden();
        }
        return calculateLayout(
                gui.width, gui.height,
                LostTalesLotrMapLayout.controlBarHeight(gui),
                categories.size(), contentWidth(font, categories),
                MenuWindow.rowHeight());
    }

    /** The heading over the kinds: the options menu's own. */
    private static String heading() {
        return I18n.format("gui.losttales.map.menu.show");
    }

    /**
     * The room the rows take across, as a menu measures its own: the
     * widest kind's name whole after the icon column, the heading, and the
     * padding either side.
     */
    private static int contentWidth(FontRenderer font,
                                    List<LostTalesMapLegendCategory> categories) {
        int widest = PADDING_X + font.getStringWidth(heading()) + PADDING_X;
        for (LostTalesMapLegendCategory category : categories) {
            widest = Math.max(widest, PADDING_X + TabIcons.SLOT
                    + TabIcons.GAP + font.getStringWidth(I18n.format(
                            category.getTranslationKey())) + PADDING_X);
        }
        return widest;
    }

    /** Glides the drawn offset toward its target, once per drawn frame, both kept within the list. */
    private static void glide(Scroll scroll, Layout layout) {
        double max = layout.maxScroll();
        scroll.target = Math.max(0.0D, Math.min(max, scroll.target));
        scroll.shown = Math.max(0.0D, Math.min(max, scroll.shown));
        long now = System.nanoTime();
        double elapsed = scroll.nanos == 0L ? 0.0D
                : (now - scroll.nanos) / 1.0E9D;
        scroll.nanos = now;
        if (Math.abs(scroll.target - scroll.shown) <= SCROLL_SNAP_PIXELS) {
            scroll.shown = scroll.target;
            return;
        }
        scroll.shown = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                scroll.shown, scroll.target, elapsed);
    }

    /** Where the heading's row is drawn: the drawn offset slides the rows up past the band. */
    private static int listTop(Layout layout, double shown) {
        return layout.rowsTop() - (int)Math.round(shown);
    }

    /**
     * The kind whose row is drawn under a point of the legend's own space
     * with the rows scrolled by {@code shown}; -1 off every kind's row,
     * the heading's included.
     */
    static int categoryAt(Layout layout, double shown, int pointX,
                          int pointY, int categoryCount) {
        if (!layout.visible || !LostTalesUiHitBox.contains(pointX, pointY,
                layout.boxLeft(), layout.rowsTop(),
                layout.boxRight() - layout.boxLeft(),
                layout.rowsBottom() - layout.rowsTop())) {
            return -1;
        }
        int index = Math.floorDiv(pointY - listTop(layout, shown),
                layout.rowHeight) - 1;
        return index >= 0 && index < categoryCount ? index : -1;
    }

    /** {@code text} cut to {@code width}, whole when it fits. */
    private static String trimmed(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) <= width ? text
                : LostTalesSkyrimUiStyle.trimToWidth(font, text,
                        Math.max(0, width));
    }

    /**
     * The legend on a map {@code screenWidth} by {@code screenHeight}
     * whose own strip takes {@code footer} rows at its foot: none in a
     * window, whose bar stands below the map. Its rows are
     * {@code rowHeight} tall and take {@code contentWidth} across inside
     * the frame; it shows every row it has room for, at least the heading
     * and one kind, else none.
     */
    static Layout calculateLayout(
            int screenWidth, int screenHeight, int footer,
            int categoryCount, int contentWidth, int rowHeight) {
        int room = screenWidth - OUTER_MARGIN * 2;
        int panelBottom = screenHeight - footer - GAP_ABOVE_CONTROL_BAR;
        if (categoryCount <= 0 || rowHeight <= 0 || room < MIN_ROOM) {
            return Layout.hidden();
        }
        int rowCount = 1 + categoryCount;
        int fixed = FRAME * 2 + PADDING_Y * 2;
        int shownRows = Math.min(rowCount,
                Math.max(0, panelBottom - OUTER_MARGIN - fixed) / rowHeight);
        if (shownRows < MIN_ROWS) {
            return Layout.hidden();
        }
        int panelWidth = Math.min(room, contentWidth + FRAME * 2);
        int panelHeight = fixed + shownRows * rowHeight;
        return new Layout(true,
                LostTalesUiInk.centredStart(screenWidth, panelWidth),
                panelBottom - panelHeight, panelWidth, panelHeight,
                rowHeight, rowCount);
    }

    private static boolean containsAnimatedPanel(
            LostTalesLotrMapGui gui, Layout layout,
            int mouseX, int mouseY) {
        int pivotX = layout.panelX + layout.panelWidth / 2;
        int pivotY = layout.panelY + layout.panelHeight / 2;
        return layout.containsPanel(
                LostTalesMapPopupAnimation.inverseMouseX(
                        gui.getMapLegendAnimationKey(), mouseX, pivotX),
                LostTalesMapPopupAnimation.inverseMouseY(
                        gui.getMapLegendAnimationKey(), mouseY, pivotY));
    }

    private static void beginUntranslatedRender(
            LostTalesLotrMapGui gui, int pivotX, int pivotY) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        LostTalesMapPopupAnimation.push(
                gui.getMapLegendAnimationKey(), pivotX, pivotY);
    }

    private static void endUntranslatedRender() {
        LostTalesMapPopupAnimation.pop();
        GL11.glPopAttrib();
    }

    /** Where the legend stands, in whole pixels of the map's own space. */
    static final class Layout {
        final boolean visible;
        /** The legend's footprint, the frame inside it. */
        final int panelX;
        final int panelY;
        final int panelWidth;
        final int panelHeight;
        final int rowHeight;
        /** Every row it lists: the heading and a row for each kind. */
        final int rowCount;

        private Layout(
                boolean visible,
                int panelX, int panelY, int panelWidth, int panelHeight,
                int rowHeight, int rowCount) {
            this.visible = visible;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.rowHeight = rowHeight;
            this.rowCount = rowCount;
        }

        static Layout hidden() {
            return new Layout(false, 0, 0, 0, 0, 0, 0);
        }

        /** The box inside the frame, which the lit rows run across. */
        int boxLeft() {
            return this.panelX + FRAME;
        }

        int boxTop() {
            return this.panelY + FRAME;
        }

        int boxRight() {
            return this.panelX + this.panelWidth - FRAME;
        }

        int boxBottom() {
            return this.panelY + this.panelHeight - FRAME;
        }

        /** The band the rows glide in: the box less the padding above and below. */
        int rowsTop() {
            return boxTop() + PADDING_Y;
        }

        int rowsBottom() {
            return boxBottom() - PADDING_Y;
        }

        /** The furthest the rows scroll: the last kind's row whole at the band's foot. */
        double maxScroll() {
            return Math.max(0, this.rowCount * this.rowHeight
                    - (rowsBottom() - rowsTop()));
        }

        boolean containsPanel(int pointX, int pointY) {
            return this.visible && LostTalesUiHitBox.contains(pointX, pointY,
                    this.panelX, this.panelY, this.panelWidth,
                    this.panelHeight);
        }
    }
}
