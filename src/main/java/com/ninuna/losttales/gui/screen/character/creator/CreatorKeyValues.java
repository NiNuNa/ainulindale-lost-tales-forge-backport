package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
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
        // Words too long for their room fade into its end (LOOK-37).
        int rowY = this.y + HEADER;
        for (String[] row : this.rows) {
            int valueRoom = this.width / 2;
            int valueX = this.x + this.width - (int)LostTalesUiCutName
                    .shownWidth(font.getStringWidth(row[1]), valueRoom);
            LostTalesUiCutName.drawShadowed(font, row[0], this.x, rowY,
                    valueX - this.x - 6, LostTalesSkyrimUiStyle.TEXT_MUTED);
            LostTalesUiCutName.drawShadowed(font, row[1], valueX, rowY,
                    valueRoom, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
            rowY += ROW;
        }
        if (this.footnote.length() > 0) {
            LostTalesUiCutName.drawShadowed(font, this.footnote, this.x,
                    rowY + 1, this.width, LostTalesSkyrimUiStyle.TEXT_DIM);
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
            // A note's one line, fading into its end where it is too long.
            LostTalesUiCutName.drawText(font, this.footnote, this.x,
                    rowY + MenuWindow.NOTE_PADDING
                            + LostTalesUiInk.centredStart(MenuWindow.NOTE_LINE,
                                    LostTalesUiInk.CAP_HEIGHT),
                    this.width, WindowStyle.asideRgb(),
                    this.context.alpha());
        }
    }
}
