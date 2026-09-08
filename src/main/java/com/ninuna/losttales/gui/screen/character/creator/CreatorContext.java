package com.ninuna.losttales.gui.screen.character.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.UUID;

/** What every control on the creator draws with. */
public final class CreatorContext {

    private final Minecraft minecraft;
    private final FontRenderer font;
    private final UUID accountId;

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
}
