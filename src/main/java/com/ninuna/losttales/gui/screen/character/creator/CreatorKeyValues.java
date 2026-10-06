package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small table of facts under a section header: the race's health,
 * speed and reach, say. Labels at the left, values at the right; in a
 * window's rows the header is a heading over its hairline, each fact a
 * row, and the footnote a note.
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
        if (inRows()) {
            return (1 + this.rows.size()) * CreatorRows.height()
                    + (this.footnote.length() > 0 ? CreatorRows.noteHeight(1)
                            : 0);
        }
        return HEADER + this.rows.size() * ROW
                + (this.footnote.length() > 0 ? 12 : 0) + 4;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (inRows()) {
            drawRows();
            return;
        }
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

    /**
     * As a window's rows: the title as a heading, each fact a row with its
     * label in ivory and its value at the right end in the aside tone, and
     * the footnote a line of note in the aside tone.
     */
    private void drawRows() {
        FontRenderer font = this.context.getFont();
        int rowHeight = CreatorRows.height();
        int right = this.x + this.width;
        WindowLists.drawHeading(font, this.title, this.x, this.x, right,
                this.y, rowHeight, false, this.context.alpha());
        int rowY = this.y + rowHeight;
        for (String[] row : this.rows) {
            int valueLeft = CreatorRows.drawValue(this.context, row[1],
                    this.x + this.width / 2, right, rowY, false);
            CreatorRows.drawLabel(this.context, row[0], this.x, rowY,
                    valueLeft - MenuWindow.VALUE_GAP);
            rowY += rowHeight;
        }
        if (this.footnote.length() > 0) {
            CreatorRows.drawNote(this.context, Collections.singletonList(
                    CreatorRows.trimmed(font, this.footnote, this.width)),
                    this.x, rowY, WindowStyle.asideRgb());
        }
    }
}
