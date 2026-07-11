package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterProfileRouterGui;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Skyrim-style radial character menu. */
public class LostTalesCharacterMenuGui extends GuiScreen {
    private static final int NONE = -1;
    private static final int OPTION_PROFILE = 0;
    private static final int OPTION_QUESTS = 1;
    private static final int OPTION_ITEMS = 2;
    private static final int OPTION_MAP = 3;

    private final GuiScreen parent;
    private int hoveredOption = NONE;

    public LostTalesCharacterMenuGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        if (this.mc != null) {
            LostTalesClientQuestDefinitionStore.ensureLoaded(this.mc.getResourceManager());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int radius = Math.max(72, Math.min(this.width, this.height) / 4);
        this.hoveredOption = getOptionAt(mouseX, mouseY, centerX, centerY, radius);

        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj, "Character Menu", getHoveredSubtitle(), this.width, 12);

        drawHoverQuarter(centerX, centerY, radius);
        drawRadialFrame(centerX, centerY, radius);
        drawRadialOption("PROFILE", OPTION_PROFILE, centerX, centerY - radius, centerX, centerY - 18);
        drawRadialOption("QUESTS", OPTION_QUESTS, centerX - radius, centerY, centerX - 22, centerY);
        drawRadialOption("ITEMS", OPTION_ITEMS, centerX + radius, centerY, centerX + 22, centerY);
        drawRadialOption("MAP", OPTION_MAP, centerX, centerY + radius, centerX, centerY + 22);
        drawCenterOrnament(centerX, centerY);
        drawFooterHelp();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHoverQuarter(int centerX, int centerY, int radius) {
        if (this.hoveredOption == NONE) {
            return;
        }
        int color = 0x22FFFFFF;
        switch (this.hoveredOption) {
            case OPTION_PROFILE:
                Gui.drawRect(centerX - radius, centerY - radius, centerX + radius, centerY, color);
                break;
            case OPTION_QUESTS:
                Gui.drawRect(centerX - radius, centerY - radius, centerX, centerY + radius, color);
                break;
            case OPTION_ITEMS:
                Gui.drawRect(centerX, centerY - radius, centerX + radius, centerY + radius, color);
                break;
            case OPTION_MAP:
                Gui.drawRect(centerX - radius, centerY, centerX + radius, centerY + radius, color);
                break;
            default:
                break;
        }
    }

    private void drawRadialFrame(int centerX, int centerY, int radius) {
        drawLine(centerX - radius, centerY, centerX - 18, centerY, LostTalesSkyrimUiStyle.BORDER_DIM);
        drawLine(centerX + 18, centerY, centerX + radius, centerY, LostTalesSkyrimUiStyle.BORDER_DIM);
        drawLine(centerX, centerY - radius, centerX, centerY - 18, LostTalesSkyrimUiStyle.BORDER_DIM);
        drawLine(centerX, centerY + 18, centerX, centerY + radius, LostTalesSkyrimUiStyle.BORDER_DIM);
        drawLine(centerX - radius / 2, centerY, centerX, centerY - radius / 2, 0x44D8D1C3);
        drawLine(centerX, centerY - radius / 2, centerX + radius / 2, centerY, 0x44D8D1C3);
        drawLine(centerX + radius / 2, centerY, centerX, centerY + radius / 2, 0x44D8D1C3);
        drawLine(centerX, centerY + radius / 2, centerX - radius / 2, centerY, 0x44D8D1C3);
    }

    private void drawRadialOption(String label, int option, int labelX, int labelY, int lineX, int lineY) {
        boolean hovered = this.hoveredOption == option;
        int color = hovered ? LostTalesSkyrimUiStyle.TEXT_BRIGHT : LostTalesSkyrimUiStyle.TEXT_MUTED;
        int accent = hovered ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.BORDER_DIM;
        String text = LostTalesSkyrimUiStyle.uppercase(label);
        int textWidth = this.fontRendererObj.getStringWidth(text);
        int drawX = labelX - textWidth / 2;
        int drawY = labelY - 4;
        this.fontRendererObj.drawStringWithShadow(text, drawX, drawY, color);
        LostTalesSkyrimUiStyle.drawDiamond(lineX, lineY, accent);
    }

    private void drawCenterOrnament(int centerX, int centerY) {
        LostTalesSkyrimUiStyle.drawDiamond(centerX, centerY, this.hoveredOption == NONE ? LostTalesSkyrimUiStyle.TEXT_MUTED : LostTalesSkyrimUiStyle.GOLD);
        Gui.drawRect(centerX - 12, centerY - 1, centerX - 5, centerY, LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(centerX + 5, centerY - 1, centerX + 12, centerY, LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(centerX - 1, centerY - 12, centerX, centerY - 5, LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(centerX - 1, centerY + 5, centerX, centerY + 12, LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    private void drawLine(int x1, int y1, int x2, int y2, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1 + 0.5F, y1 + 0.5F);
        GL11.glVertex2f(x2 + 0.5F, y2 + 0.5F);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private int getOptionAt(int mouseX, int mouseY, int centerX, int centerY, int radius) {
        int dx = mouseX - centerX;
        int dy = mouseY - centerY;
        int deadZone = 18;
        if (Math.abs(dx) < deadZone && Math.abs(dy) < deadZone) {
            return NONE;
        }
        if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
            return NONE;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx < 0 ? OPTION_QUESTS : OPTION_ITEMS;
        }
        return dy < 0 ? OPTION_PROFILE : OPTION_MAP;
    }

    private String getHoveredSubtitle() {
        switch (this.hoveredOption) {
            case OPTION_PROFILE:
                return "Character Info";
            case OPTION_QUESTS:
                return "Quest Journal";
            case OPTION_ITEMS:
                return "Inventory";
            case OPTION_MAP:
                return "Middle-earth Map";
            default:
                return "Choose a path";
        }
    }

    private void drawFooterHelp() {
        String help = "Hover a direction and click to select   "
                + LostTalesKeyBindings.getQuestJournalKeyDisplayName() + ": quest journal   "
                + LostTalesKeyBindings.getCharacterMenuKeyDisplayName() + "/Esc: close";
        this.fontRendererObj.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, help, this.width - 24), 12, this.height - 20, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && this.hoveredOption != NONE) {
            openOption(this.hoveredOption);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private void openOption(int option) {
        if (this.mc == null) {
            return;
        }
        switch (option) {
            case OPTION_PROFILE:
                this.mc.displayGuiScreen(new LostTalesCharacterProfileRouterGui(this));
                break;
            case OPTION_QUESTS:
                this.mc.displayGuiScreen(new LostTalesQuestJournalGui(this.parent));
                break;
            case OPTION_ITEMS:
                if (this.mc.thePlayer != null) {
                    this.mc.displayGuiScreen(new GuiInventory(this.mc.thePlayer));
                }
                break;
            case OPTION_MAP:
                try {
                    this.mc.displayGuiScreen(new LOTRGuiMap());
                } catch (Throwable ignored) {
                    this.mc.displayGuiScreen(new LostTalesCharacterProfileRouterGui(this));
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || LostTalesKeyBindings.isCharacterMenuKey(keyCode)) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (LostTalesKeyBindings.isQuestJournalKey(keyCode)) {
            this.mc.displayGuiScreen(new LostTalesQuestJournalGui(this.parent));
            return;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_W) {
            openOption(OPTION_PROFILE);
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_A) {
            openOption(OPTION_QUESTS);
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_D) {
            openOption(OPTION_ITEMS);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_S) {
            openOption(OPTION_MAP);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
