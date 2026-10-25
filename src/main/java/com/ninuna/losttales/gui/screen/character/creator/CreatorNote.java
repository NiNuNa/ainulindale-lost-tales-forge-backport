package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.WindowStyle;

import java.util.List;

/**
 * A few lines of note in the aside tone, at a menu note's pitch: a hint,
 * a caveat, a note on what happens next.
 */
public final class CreatorNote extends CreatorControl {

    private final String text;

    public CreatorNote(CreatorContext context, String text) {
        super(context);
        this.text = text == null ? "" : text;
    }

    /** The text wrapped to the width it was last asked for. */
    private List<String> lines;
    private int linesWidth = -1;

    @SuppressWarnings("unchecked")
    private List<String> lines() {
        int width = Math.max(20, this.width);
        if (this.lines == null || this.linesWidth != width) {
            this.lines = this.context.getFont().listFormattedStringToWidth(
                    this.text, width);
            this.linesWidth = width;
        }
        return this.lines;
    }

    @Override
    public int height() {
        return CreatorRows.noteHeight(lines().size());
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        CreatorRows.drawNote(this.context, lines(), this.x, this.y,
                WindowStyle.asideRgb());
    }
}
