package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;

import java.util.List;

/** A few lines of muted text: a hint, a caveat, a note on what happens next. */
public final class CreatorNote extends CreatorControl {

    private static final int LINE = 10;

    private final String text;
    private final int color;

    public CreatorNote(CreatorContext context, String text) {
        this(context, text, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    public CreatorNote(CreatorContext context, String text, int color) {
        super(context);
        this.text = text == null ? "" : text;
        this.color = color;
    }

    @SuppressWarnings("unchecked")
    private List<String> lines() {
        return this.context.getFont().listFormattedStringToWidth(
                this.text, Math.max(20, this.width));
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
            font.drawStringWithShadow(line, this.x, lineY, this.color);
            lineY += LINE;
        }
    }
}
