package com.ninuna.losttales.gui.screen.character.creator;

/**
 * Where everything on the creator goes, from the screen's size alone.
 *
 * <p>The screen is split the way a character editor reads: the choices
 * down a column on the left, and the character itself on a stage taking
 * the rest. The column is a third of the screen where a third is enough
 * for its rows, never narrower than the rows need, and never so wide on
 * a large window that the stage becomes a strip. Below both runs the
 * control bar every full-screen menu here ends in, and above them the
 * title.</p>
 *
 * <p>Pure geometry, so the split can be proven at every size 1.7.10 can
 * scale a window to — from a 427 by 240 phone-sized GUI Scale Auto on a
 * small window to a 1920 by 1080 one at scale one.</p>
 */
public final class CharacterCreatorLayout {

    /** The header's band across the top: a title, a subtitle, and a breath. */
    public static final int HEADER_HEIGHT = 34;
    /** What the control bar takes at the foot, plus a breath above it. */
    public static final int FOOTER_HEIGHT = 30 + 6;
    /** Clear of the screen's sides. */
    public static final int MARGIN = 8;
    /** Between the column and the stage. */
    public static final int GAP = 10;
    /** The column's narrowest: a label, a value and two arrows still fit. */
    public static final int PANEL_MIN_WIDTH = 168;
    /** Its widest, so a large window still gives the stage most of itself. */
    public static final int PANEL_MAX_WIDTH = 280;
    /** The tab strip across the top of the column. */
    public static final int TAB_STRIP_HEIGHT = 18;
    /** The row of buttons at the foot of the column, with its own breath. */
    public static final int BUTTON_ROW_HEIGHT = 30;
    /** Inside the column's frame. */
    public static final int PANEL_PADDING = 8;
    /** How much of the stage's height the figure is fitted into. */
    private static final float FIGURE_FIT = 0.78F;
    /** Where the figure stands, as a share of the stage's height from its top. */
    private static final float BASELINE_FRACTION = 0.88F;

    private final int width;
    private final int height;
    private final int panelLeft;
    private final int panelTop;
    private final int panelWidth;
    private final int panelHeight;

    public CharacterCreatorLayout(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        int third = this.width / 3;
        int room = Math.max(PANEL_MIN_WIDTH, this.width - MARGIN * 2 - GAP - PANEL_MIN_WIDTH);
        this.panelWidth = Math.min(room,
                Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, third)));
        this.panelLeft = MARGIN;
        this.panelTop = HEADER_HEIGHT;
        this.panelHeight = Math.max(60,
                this.height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    public int getPanelLeft() { return this.panelLeft; }
    public int getPanelTop() { return this.panelTop; }
    public int getPanelWidth() { return this.panelWidth; }
    public int getPanelHeight() { return this.panelHeight; }
    public int getPanelRight() { return this.panelLeft + this.panelWidth; }
    public int getPanelBottom() { return this.panelTop + this.panelHeight; }

    /** The tab strip inside the column's top edge. */
    public int getTabStripTop() { return this.panelTop + 1; }
    public int getTabStripBottom() {
        return getTabStripTop() + TAB_STRIP_HEIGHT;
    }

    /** The scrolling content between the tab strip and the button row. */
    public int getContentLeft() { return this.panelLeft + PANEL_PADDING; }
    public int getContentTop() { return getTabStripBottom() + PANEL_PADDING; }
    public int getContentWidth() {
        return this.panelWidth - PANEL_PADDING * 2;
    }
    public int getContentBottom() {
        return getButtonRowTop() - PANEL_PADDING / 2;
    }
    public int getContentHeight() {
        return Math.max(0, getContentBottom() - getContentTop());
    }

    /** The row of buttons at the foot of the column. */
    public int getButtonRowTop() {
        return getPanelBottom() - BUTTON_ROW_HEIGHT;
    }

    /** The stage the character stands on: everything right of the column. */
    public int getStageLeft() { return getPanelRight() + GAP; }
    public int getStageTop() { return this.panelTop; }
    public int getStageWidth() {
        return Math.max(0, this.width - MARGIN - getStageLeft());
    }
    public int getStageHeight() { return this.panelHeight; }
    public int getStageRight() { return getStageLeft() + getStageWidth(); }
    public int getStageBottom() { return getStageTop() + getStageHeight(); }

    /** Where the figure stands across the stage. */
    public float getFigureCenterX() {
        return getStageLeft() + getStageWidth() / 2.0F;
    }

    /** Where the figure's feet are, before any zoom. */
    public float getFigureBaselineY() {
        return getStageTop() + getStageHeight() * BASELINE_FRACTION;
    }

    /** The height the figure is fitted into at a zoom of one. */
    public int getFigureFitHeight() {
        return Math.max(16, Math.round(getStageHeight() * FIGURE_FIT));
    }

    /** Whether the stage is wide enough to be worth drawing at all. */
    public boolean hasStage() {
        return getStageWidth() >= 48;
    }

    public boolean isInPanel(int x, int y) {
        return x >= this.panelLeft && x < getPanelRight()
                && y >= this.panelTop && y < getPanelBottom();
    }

    public boolean isInContent(int x, int y) {
        return x >= this.panelLeft && x < getPanelRight()
                && y >= getContentTop() && y < getContentBottom();
    }

    public boolean isInTabStrip(int x, int y) {
        return x >= this.panelLeft && x < getPanelRight()
                && y >= getTabStripTop() && y < getTabStripBottom();
    }

    public boolean isOnStage(int x, int y) {
        return hasStage() && x >= getStageLeft() && x < getStageRight()
                && y >= getStageTop() && y < getStageBottom();
    }
}
