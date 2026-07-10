package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.inventory.container.LostTalesContainerMissiveBoard;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * One-row inventory GUI for the missive board.
 *
 * Players take a generated missive letter from the board, right-click the letter
 * to read it, and then either accept it from the reader screen or place the
 * letter back into an empty board slot.
 */
public class LostTalesMissiveBoardGui extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    private final InventoryPlayer playerInventory;
    private final LostTalesTileEntityMissiveBoard board;

    public LostTalesMissiveBoardGui(InventoryPlayer playerInventory, LostTalesTileEntityMissiveBoard board) {
        super(new LostTalesContainerMissiveBoard(playerInventory, board));
        this.playerInventory = playerInventory;
        this.board = board;
        this.ySize = 132;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String boardName = this.board.hasCustomInventoryName()
                ? this.board.getInventoryName()
                : I18n.format(this.board.getInventoryName());
        this.fontRendererObj.drawString(boardName, 8, 6, 4210752);

        String availability = I18n.format("gui.losttales.missive_board.available", Integer.valueOf(this.board.countAvailableMissives()), Integer.valueOf(this.board.getMaxAvailableMissives()));
        this.fontRendererObj.drawString(availability, this.xSize - 8 - this.fontRendererObj.getStringWidth(availability), 6, 4210752);

        this.fontRendererObj.drawString(I18n.format("gui.losttales.missive_board.instructions"), 8, 38, 4210752);
        this.fontRendererObj.drawString(I18n.format(this.playerInventory.getInventoryName()), 8, this.ySize - 96 + 2, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int left = (this.width - this.xSize) / 2;
        int top = (this.height - this.ySize) / 2;

        // Top area: one board row.
        this.drawTexturedModalRect(left, top, 0, 0, this.xSize, 35);

        // Player inventory area from the vanilla generic container texture.
        this.drawTexturedModalRect(left, top + 35, 0, 126, this.xSize, 96);
    }
}
