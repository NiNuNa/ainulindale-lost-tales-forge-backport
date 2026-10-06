package com.ninuna.losttales.gui.screen.character.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.UUID;

/**
 * What every control draws with: the game, the font, the account, and how
 * the controls stand. The creator's controls stand boxed in its column; a
 * window's stand as its rows, and the window tells them each frame how
 * strongly it is drawn and how wide a lit row reaches.
 */
public final class CreatorContext {

    /** How the controls stand. */
    public enum Presentation {
        /** The creator's column: a label over a boxed field, arrows in boxes. */
        BOXED,
        /**
         * A window's rows, as its Settings stand: one row a control, its
         * label at the left and its value at the right end.
         */
        ROWS
    }

    private final Minecraft minecraft;
    private final FontRenderer font;
    private final UUID accountId;
    private final Presentation presentation;
    private int alpha = 255;
    private int surfaceAlpha = 255;
    private int rowLeft;
    private int rowRight;

    /** The creator's boxed controls. */
    public CreatorContext(Minecraft minecraft, FontRenderer font,
                          UUID accountId) {
        this(minecraft, font, accountId, Presentation.BOXED);
    }

    public CreatorContext(Minecraft minecraft, FontRenderer font,
                          UUID accountId, Presentation presentation) {
        this.minecraft = minecraft;
        this.font = font;
        this.accountId = accountId;
        this.presentation = presentation;
    }

    public Minecraft getMinecraft() { return this.minecraft; }
    public FontRenderer getFont() { return this.font; }
    /** The signed-in account, whose own skin some tiles show; may be null. */
    public UUID getAccountId() { return this.accountId; }

    /** Whether the controls stand as a window's rows. */
    public boolean inRows() {
        return this.presentation == Presentation.ROWS;
    }

    /**
     * The window's frame about to be drawn: its strength, its surface's,
     * which a lit row recolours, and the box a lit row reaches across,
     * whose sides are the window's frame.
     */
    public void frame(int alpha, int surfaceAlpha, int rowLeft, int rowRight) {
        this.alpha = alpha;
        this.surfaceAlpha = surfaceAlpha;
        this.rowLeft = rowLeft;
        this.rowRight = rowRight;
    }

    public int alpha() { return this.alpha; }
    public int surfaceAlpha() { return this.surfaceAlpha; }
    public int rowLeft() { return this.rowLeft; }
    public int rowRight() { return this.rowRight; }
}
