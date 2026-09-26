package com.ninuna.losttales.gui.style;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/**
 * A field of several lines drawn in the mod's ink: a profile's longer
 * texts. Its words wrap at its width and scroll by whole lines to keep the
 * caret in sight; it takes typing, the caret's keys — by character, by
 * word with Ctrl, by line with Up and Down, to a line's ends with Home
 * and End — Shift to select, and the clipboard. Shift+Return starts a new
 * paragraph; Return itself is left to whatever the field stands in, as a
 * messenger's field sends. The one caret blinks after the text while it
 * holds the keys, and its hint reads while it is empty.
 */
public final class LostTalesUiTextArea {
    /** A line's stride; the words sit on it as a chat line's do. */
    public static final int LINE_HEIGHT = 10;

    private final FontRenderer font;
    private final LostTalesUiTextModel model;
    private final LostTalesUiTextModel.Measure measure;
    private int left;
    private int top;
    private int width;
    private int height;
    /** The first line in sight. */
    private int scroll;
    private boolean focused;
    private long caretNanos = System.nanoTime();

    public LostTalesUiTextArea(final FontRenderer font, int limit) {
        this.font = font;
        this.model = new LostTalesUiTextModel(limit);
        this.measure = new LostTalesUiTextModel.Measure() {
            @Override
            public int width(String text) {
                return font.getStringWidth(text);
            }
        };
    }

    /** Where the field stands and how large it is. */
    public void place(int x, int y, int boxWidth, int boxHeight) {
        this.left = x;
        this.top = y;
        this.width = Math.max(1, boxWidth);
        this.height = Math.max(LINE_HEIGHT, boxHeight);
    }

    public String getText() {
        return this.model.text();
    }

    public void setText(String text) {
        this.model.setText(text);
        this.scroll = 0;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean on) {
        if (on && !this.focused) {
            this.caretNanos = System.nanoTime();
        }
        this.focused = on;
    }

    /** Whether a point is on the field. */
    public boolean contains(int x, int y) {
        return LostTalesUiHitBox.contains(x, y, this.left, this.top,
                this.width, this.height);
    }

    private List<LostTalesUiTextModel.Line> lines() {
        return LostTalesUiTextModel.wrap(this.model.text(), this.width,
                this.measure);
    }

    private int visibleLines() {
        return Math.max(1, this.height / LINE_HEIGHT);
    }

    /** Scrolls by whole lines so the caret's line is in sight. */
    private void keepCaretInSight(List<LostTalesUiTextModel.Line> lines) {
        int line = LostTalesUiTextModel.lineOf(lines, this.model.caret());
        if (line < this.scroll) {
            this.scroll = line;
        } else if (line >= this.scroll + visibleLines()) {
            this.scroll = line - visibleLines() + 1;
        }
        this.scroll = Math.max(0, Math.min(this.scroll,
                Math.max(0, lines.size() - visibleLines())));
    }

    /** Draws the words, the selection, the caret, or the hint while it is empty, at {@code alpha}. */
    public void draw(String hint, int alpha) {
        List<LostTalesUiTextModel.Line> lines = lines();
        keepCaretInSight(lines);
        String text = this.model.text();
        boolean caretShown = this.focused
                && LostTalesUiCaret.isLit(this.caretNanos, System.nanoTime());
        int shown = Math.min(lines.size(), this.scroll + visibleLines());
        if (text.length() == 0 && !this.focused && hint != null) {
            LostTalesUiInk.drawText(this.font, "§o"
                    + this.font.trimStringToWidth(hint, this.width), this.left,
                    this.top, LostTalesColors.rgb(LostTalesColors.TEXT_DIM),
                    alpha);
        }
        for (int at = this.scroll; at < shown; at++) {
            LostTalesUiTextModel.Line line = lines.get(at);
            int y = this.top + (at - this.scroll) * LINE_HEIGHT;
            drawSelection(text, line, y, alpha);
            String words = text.substring(line.start, line.end);
            if (words.length() > 0) {
                LostTalesUiInk.drawText(this.font, words, this.left, y,
                        LostTalesUiInk.IVORY, alpha);
            }
        }
        if (caretShown) {
            int line = LostTalesUiTextModel.lineOf(lines, this.model.caret());
            if (line >= this.scroll && line < shown) {
                LostTalesUiTextModel.Line on = lines.get(line);
                int x = this.left + this.font.getStringWidth(text.substring(
                        on.start, Math.min(this.model.caret(), on.end)));
                int caretTop = LostTalesUiCaret.topFor(this.top
                        + (line - this.scroll) * LINE_HEIGHT);
                LostTalesUiCaret.drawShadow(x, caretTop,
                        LostTalesUiCaret.HEIGHT, alpha);
                LostTalesUiCaret.drawBar(x, caretTop, LostTalesUiCaret.HEIGHT,
                        alpha);
            }
        }
    }

    /** The part of a line inside the selection, in the selection's plum grey. */
    private void drawSelection(String text, LostTalesUiTextModel.Line line,
                               int y, int alpha) {
        int from = Math.max(line.start, this.model.selectionStart());
        int to = Math.min(line.end, this.model.selectionEnd());
        if (!this.model.hasSelection() || from >= to) {
            return;
        }
        int x0 = this.left + this.font.getStringWidth(
                text.substring(line.start, from));
        int x1 = this.left + this.font.getStringWidth(
                text.substring(line.start, to));
        int caretTop = LostTalesUiCaret.topFor(y);
        Gui.drawRect(x0, caretTop, x1, caretTop + LostTalesUiCaret.HEIGHT,
                LostTalesUiInk.argb(LostTalesColors.rgb(
                        LostTalesColors.PLUM_GRAY), Math.min(alpha, 0xB4)));
        LostTalesUiInk.beginContent();
    }

    /** A press puts the caret by the character drawn under it; Shift takes the selection there. */
    public void mouseClicked(int x, int y) {
        if (!contains(x, y)) {
            return;
        }
        List<LostTalesUiTextModel.Line> lines = lines();
        int line = Math.max(0, Math.min(lines.size() - 1,
                this.scroll + (y - this.top) / LINE_HEIGHT));
        this.model.moveTo(LostTalesUiTextModel.indexAt(this.model.text(),
                lines.get(line), x - this.left, this.measure),
                GuiScreen.isShiftKeyDown());
        this.caretNanos = System.nanoTime();
    }

    /** The wheel over the field scrolls its lines. */
    public void scrollBy(int lines) {
        this.scroll = Math.max(0, Math.min(this.scroll + lines,
                Math.max(0, lines().size() - visibleLines())));
    }

    /**
     * A key while the field holds the keys; answers whether it took it.
     * Return alone is not taken, so whatever holds the field may act on it.
     */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.focused) {
            return false;
        }
        boolean shift = GuiScreen.isShiftKeyDown();
        boolean command = GuiScreen.isCtrlKeyDown();
        List<LostTalesUiTextModel.Line> lines = lines();
        boolean taken = true;
        if (command && keyCode == Keyboard.KEY_A) {
            this.model.selectAll();
        } else if (command && keyCode == Keyboard.KEY_C) {
            GuiScreen.setClipboardString(this.model.selected());
        } else if (command && keyCode == Keyboard.KEY_X) {
            GuiScreen.setClipboardString(this.model.selected());
            this.model.type("");
        } else if (command && keyCode == Keyboard.KEY_V) {
            this.model.type(allowed(GuiScreen.getClipboardString()));
        } else if (keyCode == Keyboard.KEY_BACK) {
            this.model.backspace(command);
        } else if (keyCode == Keyboard.KEY_DELETE) {
            this.model.delete(command);
        } else if (keyCode == Keyboard.KEY_LEFT) {
            this.model.left(shift, command);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            this.model.right(shift, command);
        } else if (keyCode == Keyboard.KEY_UP) {
            this.model.vertical(-1, shift, lines, this.measure);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            this.model.vertical(1, shift, lines, this.measure);
        } else if (keyCode == Keyboard.KEY_HOME) {
            this.model.lineEdge(false, shift, lines);
        } else if (keyCode == Keyboard.KEY_END) {
            this.model.lineEdge(true, shift, lines);
        } else if ((keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) && shift) {
            this.model.type("\n");
        } else if (!command && ChatAllowedCharacters.isAllowedCharacter(
                typedChar)) {
            this.model.type(String.valueOf(typedChar));
        } else {
            taken = false;
        }
        if (taken) {
            this.caretNanos = System.nanoTime();
        }
        return taken;
    }

    /** What the field takes of pasted words: the characters it allows, and new lines. */
    static String allowed(String pasted) {
        if (pasted == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(pasted.length());
        for (int at = 0; at < pasted.length(); at++) {
            char each = pasted.charAt(at);
            if (each == '\n' || ChatAllowedCharacters.isAllowedCharacter(each)) {
                kept.append(each);
            }
        }
        return kept.toString();
    }
}
