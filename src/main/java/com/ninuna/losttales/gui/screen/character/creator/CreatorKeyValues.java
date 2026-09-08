package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A small table of facts under a section header: the race's health,
 * speed and reach, say. Labels at the left, values at the right.
 */
public final class CreatorKeyValues extends CreatorControl {

    private static final int HEADER = 14;
    private static final int ROW = 11;

    private final String title;
    private final List<String[]> rows = new ArrayList<String[]>();
    private final String footnote;

    public CreatorKeyValues(CreatorContext context, String title,
                            String footnote) {
        super(context);
        this.title = title;
        this.footnote = footnote == null ? "" : footnote;
    }

    public CreatorKeyValues add(String label, String value) {
        this.rows.add(new String[] {label == null ? "" : label,
                value == null ? "" : value});
        return this;
    }

    @Override
    public int height() {
        return HEADER + this.rows.size() * ROW
                + (this.footnote.length() > 0 ? 12 : 0) + 4;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        LostTalesSkyrimUiStyle.beginContent();
        LostTalesSkyrimUiStyle.drawSectionHeader(font, this.title, this.x,
                this.y, this.width);
        LostTalesSkyrimUiStyle.beginContent();
        int rowY = this.y + HEADER;
        for (String[] row : this.rows) {
            String value = LostTalesSkyrimUiStyle.trimToWidth(font, row[1],
                    this.width / 2);
            int valueX = this.x + this.width - font.getStringWidth(value);
            font.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(font,
                    row[0], valueX - this.x - 6), this.x, rowY,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            font.drawStringWithShadow(value, valueX, rowY,
                    LostTalesSkyrimUiStyle.TEXT_BRIGHT);
            rowY += ROW;
        }
        if (this.footnote.length() > 0) {
            font.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.footnote, this.width), this.x, rowY + 1,
                    LostTalesSkyrimUiStyle.TEXT_DIM);
        }
    }
}
