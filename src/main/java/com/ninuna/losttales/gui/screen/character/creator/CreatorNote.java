package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;

import java.util.List;

/** A few lines of muted text: a hint, a caveat, a note on what happens next. */
public final class CreatorNote extends CreatorControl {

    private static final int LINE = 10;

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
        return lines().size() * LINE + 4;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        LostTalesSkyrimUiStyle.beginContent();
        int lineY = this.y;
        for (String line : lines()) {
            font.drawStringWithShadow(line, this.x, lineY,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            lineY += LINE;
        }
    }
}
