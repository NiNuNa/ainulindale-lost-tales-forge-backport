package com.ninuna.losttales.gui.style;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/**
 * A plain one-line field drawn in the mod's own ink.
 *
 * <p>Vanilla's {@code GuiTextField} writes its text with
 * {@code drawStringWithShadow}, whose shadow is a quarter of the text's
 * colour at full opacity — a different shadow from every other glyph
 * beside it — and marks the end of the text with an underscore that
 * hangs past the field. This draws the text in {@link LostTalesUiInk}
 * ivory under the one plum-black shadow, and the mod's one caret
 * ({@link LostTalesUiCaret}), so a field reads as part of whatever it
 * stands in. Everything else — typing, selecting, the clipboard — is
 * vanilla's.</p>
 *
 * <p>With its background switched on it stands in a box of its own, as
 * vanilla's field does, in the palette's panel and border tones, and
 * its text sits inside the box where vanilla's would.</p>
 *
 * <p>The field scrolls itself rather than reading vanilla's private
 * offset: it keeps the caret in view and never scrolls past the text's
 * end, which is all a short field needs, and a click lands on the
 * character drawn under it.</p>
 */
public class LostTalesUiTextField extends GuiTextField {

    /** How far vanilla's field sets its text in from the box around it. */
    private static final int BOX_INSET = 4;

    private final FontRenderer font;
    /** The first character drawn; kept so the caret stays in view. */
    private int scroll;
    /** When the field last took a key or moved its caret: the caret's blink starts there. */
    private long caretNanos = System.nanoTime();

    public LostTalesUiTextField(FontRenderer font, int x, int y, int width,
                                int height) {
        super(font, x, y, width, height);
        this.font = font;
        setEnableBackgroundDrawing(false);
        setTextColor(LostTalesUiInk.IVORY);
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) {
            this.caretNanos = System.nanoTime();
        }
        super.setFocused(focused);
    }

    @Override
    public void setCursorPosition(int position) {
        super.setCursorPosition(position);
        this.caretNanos = System.nanoTime();
    }

    @Override
    public void setSelectionPos(int position) {
        super.setSelectionPos(position);
        this.caretNanos = System.nanoTime();
    }

    /** Where the text starts across: inside the box when there is one. */
    private int textLeft() {
        return getEnableBackgroundDrawing() ? this.xPosition + BOX_INSET
                : this.xPosition;
    }

    /** Where the text's top stands: centred in the box when there is one. */
    private int textTop() {
        return getEnableBackgroundDrawing()
                ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
    }

    @Override
    public void drawTextBox() {
        if (!getVisible()) {
            return;
        }
        if (getEnableBackgroundDrawing()) {
            drawBox();
        }
        String text = getText();
        keepCaretInView(text);
        String visible = this.font.trimStringToWidth(
                text.substring(Math.min(this.scroll, text.length())),
                getWidth());
        int left = textLeft();
        int top = textTop();

        int caret = Math.max(0,
                Math.min(visible.length(), getCursorPosition() - this.scroll));
        int caretX = left + this.font.getStringWidth(visible.substring(0, caret));
        boolean caretShown = isFocused()
                && LostTalesUiCaret.isLit(this.caretNanos, System.nanoTime());

        int selection = getSelectionEnd() - this.scroll;
        if (selection != caret) {
            int bounded = Math.max(0, Math.min(visible.length(), selection));
            int selectionX = left
                    + this.font.getStringWidth(visible.substring(0, bounded));
            drawSelection(Math.min(caretX, selectionX),
                    Math.max(caretX, selectionX), top);
        }
        int caretTop = LostTalesUiCaret.topFor(top);
        if (caretShown) {
            LostTalesUiCaret.drawShadow(caretX, caretTop,
                    LostTalesUiCaret.HEIGHT, 0xFF);
        }
        LostTalesUiInk.beginContent();
        if (visible.length() > 0) {
            drawShadowedText(visible, left, top, LostTalesUiInk.IVORY, 0xFF);
        }
        if (caretShown) {
            LostTalesUiCaret.drawBar(caretX, caretTop,
                    LostTalesUiCaret.HEIGHT, 0xFF);
        }
    }

    /**
     * A click in the field puts the caret by the character drawn under
     * it: vanilla's own placement reads an offset this field does not
     * keep, so it is put right against the text as drawn.
     */
    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        boolean inside = mouseX >= this.xPosition
                && mouseX < this.xPosition + this.width
                && mouseY >= this.yPosition
                && mouseY < this.yPosition + this.height;
        if (!isFocused() || button != 0 || !inside) {
            return;
        }
        String text = getText();
        int from = Math.min(this.scroll, text.length());
        String visible = this.font.trimStringToWidth(text.substring(from),
                getWidth());
        setCursorPosition(from + this.font.trimStringToWidth(visible,
                Math.max(0, mouseX - textLeft())).length());
    }

    /**
     * Writes one run in the mod's ink: the words a pixel down and right
     * in the shadow tone first, then the words themselves over them —
     * one picture while they fade, so the shadow never shows through
     * their strokes.
     */
    public void drawShadowedText(final String text, final int x,
                                 final int y, final int rgb,
                                 final int alpha) {
        LostTalesUiFlatLayers.Layers layers = new LostTalesUiFlatLayers.Layers() {
            @Override
            public void draw() {
                int shadow = LostTalesUiInk.shadowAlpha(alpha);
                if (shadow >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                    font.drawString(text, x + LostTalesUiInk.SHADOW_OFFSET,
                            y + LostTalesUiInk.SHADOW_OFFSET,
                            LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
                    LostTalesUiFlatLayers.nextLayer();
                }
                font.drawString(text, x, y, LostTalesUiInk.argb(rgb, alpha));
            }
        };
        if (alpha >= 255 || LostTalesUiFlatLayers.isActive()) {
            layers.draw();
            return;
        }
        LostTalesUiFlatLayers.draw(alpha, x, y,
                x + this.font.getStringWidth(text)
                        + LostTalesUiInk.SHADOW_OFFSET,
                y + this.font.FONT_HEIGHT + LostTalesUiInk.SHADOW_OFFSET,
                layers);
    }

    /** The placeholder shown while the field is empty and unfocused. */
    public void drawHint(String hint) {
        if (hint == null || hint.length() == 0 || getText().length() > 0
                || isFocused()) {
            return;
        }
        LostTalesUiInk.beginContent();
        drawShadowedText(this.font.trimStringToWidth(hint, getWidth()),
                textLeft(), textTop(),
                LostTalesColors.rgb(LostTalesColors.TEXT_DIM), 0xC8);
    }

    /**
     * Moves the first drawn character so the caret is always on screen,
     * and never leaves blank room at the end while text runs off the
     * front.
     */
    private void keepCaretInView(String text) {
        int caret = Math.max(0, Math.min(text.length(), getCursorPosition()));
        if (this.scroll > caret) {
            this.scroll = caret;
        }
        if (this.scroll > text.length()) {
            this.scroll = text.length();
        }
        while (this.scroll < caret && this.font.getStringWidth(
                text.substring(this.scroll, caret)) > getWidth()) {
            this.scroll++;
        }
        while (this.scroll > 0 && this.font.getStringWidth(
                text.substring(this.scroll - 1)) <= getWidth()) {
            this.scroll--;
        }
    }

    /**
     * The box a field with its background on stands in, where vanilla's
     * stands: a one-pixel frame in the border tone round the field and
     * the panel's surface inside it, side by side rather than one over
     * the other.
     */
    private void drawBox() {
        int left = this.xPosition - 1;
        int top = this.yPosition - 1;
        int right = this.xPosition + this.width + 1;
        int bottom = this.yPosition + this.height + 1;
        int border = LostTalesColors.BORDER;
        Gui.drawRect(left, top, right, top + 1, border);
        Gui.drawRect(left, bottom - 1, right, bottom, border);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, border);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, border);
        Gui.drawRect(this.xPosition, this.yPosition,
                this.xPosition + this.width, this.yPosition + this.height,
                LostTalesColors.PANEL_FILL);
        LostTalesUiInk.beginContent();
    }

    private void drawSelection(int from, int to, int textTop) {
        int top = LostTalesUiCaret.topFor(textTop);
        Gui.drawRect(from, top, to, top + LostTalesUiCaret.HEIGHT,
                LostTalesUiInk.argb(
                        LostTalesColors.rgb(LostTalesColors.PLUM_GRAY), 0xB4));
        LostTalesUiInk.beginContent();
    }
}
