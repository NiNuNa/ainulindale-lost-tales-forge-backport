package com.ninuna.losttales.gui.screen.character.creator;

/**
 * One row of a window's form, or a few rows standing together: a
 * stepper, a slider, a field, a grid. It is drawn from the shapes in
 * {@link CreatorRows}.
 *
 * <p>A control is placed by the form every frame — the form scrolls,
 * so where a control is drawn is not where it was last frame — and asked
 * for its height so the next one can be stacked under it. Input reaches a
 * control only when the pointer is on it, or, for keys, when it holds the
 * focus. Everything a control answers with is a boolean saying whether it
 * took the input, so the screen can fall through to its own handling.</p>
 */
public abstract class CreatorControl {

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

    /**
     * Whether a left click at that point would act on the row: choose,
     * step, flip or set something. Taking the focus alone does not count,
     * and neither does placing a text cursor.
     */
    public boolean isPointerOverAction(int mouseX, int mouseY) {
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

    /**
     * Whether the control keeps every key while it holds the focus, as a
     * field being typed in does: the page's own keys wait.
     */
    public boolean holdsKeys() {
        return false;
    }

    public void setFocused(boolean focused) {
        this.focused = focused && canFocus();
    }

    /** Once a tick, for cursors that blink. */
    public void tick() {}

    /** Whether that point is on the row. */
    public boolean contains(int mouseX, int mouseY) {
        return within(mouseX, mouseY, this.x, this.y, this.width, height());
    }

    /** Whether that point is in the box from {@code x}, {@code y}, {@code width} wide and {@code height} high. */
    protected static boolean within(int mouseX, int mouseY, int x, int y,
                                    int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
