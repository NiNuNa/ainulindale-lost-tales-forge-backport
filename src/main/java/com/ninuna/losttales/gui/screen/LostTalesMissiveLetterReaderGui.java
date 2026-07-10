package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMissiveAcceptPacket;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.item.LostTalesItemMissiveLetter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

/**
 * Client-side reader for generated missive letters.
 *
 * This screen is deliberately display-only except for the Accept button. The
 * button sends a small server request naming the player's inventory slot and
 * expected quest ID; the server re-reads the actual stack before starting the
 * quest or consuming the letter.
 */
public class LostTalesMissiveLetterReaderGui extends GuiScreen {
    private static final int BUTTON_ACCEPT = 0;
    private static final int BUTTON_CLOSE = 1;

    private final ItemStack displayStack;
    private final int inventorySlot;
    private LostTalesMissiveData missive;
    private GuiButton acceptButton;
    private GuiButton closeButton;

    public LostTalesMissiveLetterReaderGui(ItemStack stack, int inventorySlot) {
        this.displayStack = stack == null ? null : stack.copy();
        this.inventorySlot = inventorySlot;
        this.missive = LostTalesMissiveNbt.readFromItemStack(this.displayStack);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        int panelWidth = 250;
        int panelHeight = 196;
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        this.acceptButton = new GuiButton(BUTTON_ACCEPT, left + panelWidth - 108, top + panelHeight - 26, 48, 20, I18n.format("gui.losttales.missive_letter.accept"));
        this.closeButton = new GuiButton(BUTTON_CLOSE, left + panelWidth - 56, top + panelHeight - 26, 48, 20, I18n.format("gui.done"));
        this.acceptButton.enabled = this.missive != null && this.missive.isValid() && this.inventorySlot >= 0;
        this.buttonList.add(this.acceptButton);
        this.buttonList.add(this.closeButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        if (button.id == BUTTON_CLOSE) {
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
            return;
        }
        if (button.id == BUTTON_ACCEPT) {
            if (this.missive != null && this.missive.isValid() && this.inventorySlot >= 0) {
                LostTalesNetworkHandler.CHANNEL.sendToServer(LostTalesMissiveAcceptPacket.fromPlayerInventory(this.inventorySlot, this.missive.getQuestId()));
            }
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int panelWidth = 250;
        int panelHeight = 196;
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        this.drawParchmentPanel(left, top, panelWidth, panelHeight);
        this.drawMissiveText(left + 18, top + 14, panelWidth - 36, panelHeight - 50);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawParchmentPanel(int left, int top, int width, int height) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawRect(left + 2, top + 2, left + width + 2, top + height + 2, 0x90000000);
        drawRect(left, top, left + width, top + height, 0xFFE6D3A8);
        drawRect(left + 4, top + 4, left + width - 4, top + height - 4, 0xFFEEDDB6);
        drawRect(left, top, left + width, top + 2, 0xFF8C6D3E);
        drawRect(left, top + height - 2, left + width, top + height, 0xFF8C6D3E);
        drawRect(left, top, left + 2, top + height, 0xFF8C6D3E);
        drawRect(left + width - 2, top, left + width, top + height, 0xFF8C6D3E);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @SuppressWarnings("rawtypes")
    private void drawMissiveText(int left, int top, int width, int height) {
        if (this.missive == null || !this.missive.isValid()) {
            this.fontRendererObj.drawSplitString(EnumChatFormatting.DARK_GRAY + I18n.format("gui.losttales.missive_letter.invalid"), left, top, width, 0x3F2C18);
            return;
        }

        int y = top;
        String title = this.missive.getTitle();
        if (title == null || title.length() == 0) {
            title = I18n.format("item.missive_letter.name");
        }
        this.fontRendererObj.drawString(EnumChatFormatting.BOLD + title, left, y, 0x2A1608);
        y += 18;

        if (this.missive.getIssuer().length() > 0) {
            y = this.drawWrapped(I18n.format("gui.losttales.missive_letter.issuer", this.missive.getIssuer()), left, y, width, 0x3F2C18, 9);
            y += 4;
        }

        String body = this.missive.getFlavorText().length() > 0 ? this.missive.getFlavorText() : this.missive.getDescription();
        if (body.length() > 0) {
            y = this.drawWrapped(body, left, y, width, 0x2A1608, 10);
            y += 6;
        }

        if (!this.missive.getObjectives().isEmpty()) {
            this.fontRendererObj.drawString(I18n.format("gui.losttales.missive_letter.objectives"), left, y, 0x4A2D0B);
            y += 11;
            for (LostTalesMissiveObjectiveData objective : this.missive.getObjectives()) {
                if (objective != null && objective.isValid()) {
                    y = this.drawWrapped("- " + LostTalesItemMissiveLetter.buildObjectiveSummary(objective), left + 4, y, width - 4, 0x2A1608, 9);
                }
            }
            y += 4;
        }

        String reward = LostTalesItemMissiveLetter.buildRewardSummary(this.missive);
        if (reward.length() > 0) {
            y = this.drawWrapped(I18n.format("gui.losttales.missive_letter.reward", reward), left, y, width, 0x2E5C20, 9);
        }
        if (this.missive.hasTimeLimit()) {
            this.drawWrapped(I18n.format("gui.losttales.missive_letter.time_limit", LostTalesItemMissiveLetter.formatTicks(this.missive.getTimeLimitTicks())), left, y, width, 0x8A1C1C, 9);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int drawWrapped(String text, int left, int y, int width, int color, int lineHeight) {
        if (text == null || text.length() == 0) {
            return y;
        }
        List lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        ArrayList copy = new ArrayList(lines);
        for (Object line : copy) {
            if (line != null) {
                this.fontRendererObj.drawString(String.valueOf(line), left, y, color);
                y += lineHeight;
            }
        }
        return y;
    }
}
