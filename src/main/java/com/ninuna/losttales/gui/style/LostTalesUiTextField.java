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
 * ivory under the one plum-black shadow, and the caret as a one-pixel
 * ivory bar with a shadow of its own, so a field reads as part of
 * whatever it stands in. Everything else — typing, selecting, the
 * clipboard — is vanilla's.</p>
 *
 * <p>The field scrolls itself rather than reading vanilla's private
 * offset: it keeps the caret in view and never scrolls past the text's
 * end, which is all a short field needs.</p>
 */
public class LostTalesUiTextField extends GuiTextField {

    /** The caret's width, wherever it stands. */
    public static final int CARET_WIDTH = 1;

    private final FontRenderer font;
    /** How tall the caret and the selection wash stand. */
    private final int contentHeight;
    /** The first character drawn; kept so the caret stays in view. */
    private int scroll;
    /** Frames since the field took the keys; the caret blinks on it. */
    private int blink;

    public LostTalesUiTextField(FontRenderer font, int x, int y, int width,
                                int height) {
        super(font, x, y, width, height);
        this.font = font;
        this.contentHeight = Math.max(8, height);
        setEnableBackgroundDrawing(false);
        setTextColor(LostTalesUiInk.IVORY);
    }

    @Override
    public void updateCursorCounter() {
        super.updateCursorCounter();
        this.blink++;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused != isFocused()) {
            this.blink = 0;
        }
        super.setFocused(focused);
    }

    @Override
    public void drawTextBox() {
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
        boolean caretShown = isFocused() && this.blink / 6 % 2 == 0;

        int selection = getSelectionEnd() - this.scroll;
        if (selection != caret) {
            int bounded = Math.max(0, Math.min(visible.length(), selection));
            int selectionX = left
                    + this.font.getStringWidth(visible.substring(0, bounded));
            drawSelection(Math.min(caretX, selectionX),
                    Math.max(caretX, selectionX), top);
        }
        if (caretShown) {
            drawCaretShadow(caretX, top);
        }
        LostTalesUiInk.beginContent();
        if (visible.length() > 0) {
            drawShadowedText(visible, left, top, LostTalesUiInk.IVORY, 0xFF);
        }
        if (caretShown) {
            drawCaretBar(caretX, top);
        }
    }

    /**
     * Writes one run in the mod's ink: the words a pixel down and right
     * in the shadow tone first, then the words themselves over them.
     */
    public void drawShadowedText(String text, int x, int y, int rgb,
                                 int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            this.font.drawString(text, x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        this.font.drawString(text, x, y, LostTalesUiInk.argb(rgb, alpha));
    }

    /** The placeholder shown while the field is empty and unfocused. */
    public void drawHint(String hint) {
        if (hint == null || hint.length() == 0 || getText().length() > 0
                || isFocused()) {
            return;
        }
        LostTalesUiInk.beginContent();
        drawShadowedText(this.font.trimStringToWidth(hint, getWidth()),
                this.xPosition, this.yPosition,
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

    private void drawCaretBar(int x, int textTop) {
        Gui.drawRect(x, textTop - 1, x + CARET_WIDTH,
                textTop - 1 + this.contentHeight,
                LostTalesUiInk.argb(LostTalesUiInk.IVORY, 0xFF));
        LostTalesUiInk.beginContent();
    }

    private void drawCaretShadow(int x, int textTop) {
        int left = x + LostTalesUiInk.SHADOW_OFFSET;
        int top = textTop - 1 + LostTalesUiInk.SHADOW_OFFSET;
        Gui.drawRect(left, top, left + CARET_WIDTH, top + this.contentHeight,
                LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                        LostTalesUiInk.shadowAlpha(0xFF)));
        LostTalesUiInk.beginContent();
    }

    private void drawSelection(int from, int to, int textTop) {
        Gui.drawRect(from, textTop - 1, to, textTop - 1 + this.contentHeight,
                LostTalesUiInk.argb(
                        LostTalesColors.rgb(LostTalesColors.PLUM_GRAY), 0xB4));
        LostTalesUiInk.beginContent();
    }
}
