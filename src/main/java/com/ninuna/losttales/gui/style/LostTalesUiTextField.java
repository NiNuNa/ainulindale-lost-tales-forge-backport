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
 * <p>The field scrolls itself rather than reading vanilla's private
 * offset: it keeps the caret in view and never scrolls past the text's
 * end, which is all a short field needs, and a click lands on the
 * character drawn under it.</p>
 */
public class LostTalesUiTextField extends GuiTextField {

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

    /** Words shown faintly while the field is empty, an example the first key typed replaces; none while null. */
    private String hint;

    public void setHint(String hint) {
        this.hint = hint;
    }

    @Override
    public void drawTextBox() {
        drawTextBox(0xFF);
    }

    /** The field at {@code alpha} of its full strength, as a window fading in draws it. */
    public void drawTextBox(int alpha) {
        if (!getVisible()) {
            return;
        }
        String text = getText();
        keepCaretInView(text);
        String visible = this.font.trimStringToWidth(
                text.substring(Math.min(this.scroll, text.length())),
                getWidth());
        int left = this.xPosition;
        int top = this.yPosition;

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
                    Math.max(caretX, selectionX), top, alpha);
        }
        int caretTop = LostTalesUiCaret.topFor(top);
        if (caretShown) {
            LostTalesUiCaret.drawShadow(caretX, caretTop,
                    LostTalesUiCaret.HEIGHT, alpha);
        }
        LostTalesUiInk.beginContent();
        if (visible.length() > 0) {
            drawShadowedText(visible, left, top, LostTalesUiInk.IVORY, alpha);
        } else if (this.hint != null && this.hint.length() > 0) {
            drawShadowedText(this.font.trimStringToWidth(this.hint, getWidth()),
                    left, top, LostTalesColors.rgb(LostTalesColors.TEXT_MUTED),
                    alpha);
        }
        if (caretShown) {
            LostTalesUiCaret.drawBar(caretX, caretTop,
                    LostTalesUiCaret.HEIGHT, alpha);
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
                Math.max(0, mouseX - this.xPosition)).length());
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

    private void drawSelection(int from, int to, int textTop, int alpha) {
        int top = LostTalesUiCaret.topFor(textTop);
        Gui.drawRect(from, top, to, top + LostTalesUiCaret.HEIGHT,
                LostTalesUiInk.argb(
                        LostTalesColors.rgb(LostTalesColors.PLUM_GRAY),
                        Math.min(alpha, 0xB4)));
        LostTalesUiInk.beginContent();
    }
}
