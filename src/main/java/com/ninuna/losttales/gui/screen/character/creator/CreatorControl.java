package com.ninuna.losttales.gui.screen.character.creator;

/**
 * One row of the creator's column: a stepper, a slider, a field, a grid.
 *
 * <p>A control is placed by the column every frame — the column scrolls,
 * so where a control is drawn is not where it was last frame — and asked
 * for its height so the next one can be stacked under it. Input reaches a
 * control only when the pointer is on it, or, for keys, when it holds the
 * focus. Everything a control answers with is a boolean saying whether it
 * took the input, so the screen can fall through to its own handling.</p>
 */
public abstract class CreatorControl {

    /** The row's usual height; controls that need more say so. */
    protected static final int ROW_HEIGHT = 26;
    /** Between a row's label and its value. */
    protected static final int LABEL_HEIGHT = 10;

    protected final CreatorContext context;
    protected int x;
    protected int y;
    protected int width;
    private boolean focused;

    protected CreatorControl(CreatorContext context) {
        this.context = context;
    }

    /** Told where it is this frame, before it is drawn or hit-tested. */
    public final void place(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public final int getY() { return this.y; }

    /** How tall the row is, in pixels. */
    public abstract int height();

    /** Draws the row at its placement. */
    public abstract void draw(int mouseX, int mouseY);

    /** A click at that point; answers whether the control took it. */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /** The pointer moved while a button the control took is still held. */
    public void mouseDragged(int mouseX, int mouseY) {}

    /** The button the control took was released. */
    public void mouseReleased() {}

    /** The wheel turned over the control; answers whether it took that. */
    public boolean mouseWheel(int notches) {
        return false;
    }

    /** A key while the control holds the focus; answers whether it took it. */
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /** Whether the control can take the keyboard at all. */
    public boolean canFocus() {
        return false;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused && canFocus();
    }

    /** Once a tick, for cursors that blink. */
    public void tick() {}

    /** Whether that point is on the row. */
    public boolean contains(int mouseX, int mouseY) {
        return CreatorWidgets.within(mouseX, mouseY, this.x, this.y,
                this.width, height());
    }

    /** Where a row's value sits, under its label. */
    protected final int valueTop() {
        return this.y + LABEL_HEIGHT;
    }

    /** The row's label, in the top-left corner every labelled row shares. */
    protected final void drawLabel(String label) {
        CreatorWidgets.drawLabel(this.context.getFont(), label, this.x, this.y);
    }
}
