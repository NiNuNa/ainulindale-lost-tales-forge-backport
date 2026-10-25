package com.ninuna.losttales.gui.screen.character.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.UUID;

/**
 * What every control draws with: the game, the font and the account. The
 * controls stand as a window's rows, and the window tells them each frame
 * how strongly it is drawn and how wide a lit row reaches.
 */
public final class CreatorContext {

    private final Minecraft minecraft;
    private final FontRenderer font;
    private final UUID accountId;
    private int alpha = 255;
    private int surfaceAlpha = 255;
    private int rowLeft;
    private int rowRight;

    public CreatorContext(Minecraft minecraft, FontRenderer font,
                          UUID accountId) {
        this.minecraft = minecraft;
        this.font = font;
        this.accountId = accountId;
    }

    public Minecraft getMinecraft() { return this.minecraft; }
    public FontRenderer getFont() { return this.font; }
    /** The signed-in account, whose own skin some tiles show; may be null. */
    public UUID getAccountId() { return this.accountId; }

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
