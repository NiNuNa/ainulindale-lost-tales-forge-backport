package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

/**
 * Lightweight client-side HUD placement preview for Minecraft 1.7.10.
 *
 * It edits the same percentage offsets as the legacy config. The preview is
 * deliberately simple rectangles/text so it does not depend on the live HUD
 * state, inventories, quest sync, or modern NeoForge GUI APIs.
 */
public class LostTalesHudPlacementGui extends GuiScreen {
    private static final int STEP_NORMAL = 2;
    private static final int STEP_FAST = 10;

    private final GuiScreen parent;
    private HudElement selected = HudElement.COMPASS;

    public LostTalesHudPlacementGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int centerX = this.width / 2;
        int bottomY = this.height - 28;

        addButton(0, centerX - 100, bottomY, 200, 20, "Done");

        int y = 36;
        addButton(10, centerX - 155, y, 100, 20, elementLabel(HudElement.COMPASS));
        addButton(11, centerX - 50, y, 100, 20, elementLabel(HudElement.QUICK_LOOT));
        addButton(12, centerX + 55, y, 100, 20, elementLabel(HudElement.QUEST));

        y += 28;
        addButton(20, centerX - 102, y, 50, 20, "Up");
        addButton(21, centerX - 102, y + 48, 50, 20, "Down");
        addButton(22, centerX - 158, y + 24, 50, 20, "Left");
        addButton(23, centerX - 46, y + 24, 50, 20, "Right");

        addButton(30, centerX + 18, y, 66, 20, "Default");
        addButton(31, centerX + 88, y, 76, 20, "LOTR-safe");
        addButton(32, centerX + 18, y + 24, 66, 20, "Compact");
        addButton(33, centerX + 88, y + 24, 76, 20, "Minimal");
    }

    private void addButton(int id, int x, int y, int width, int height, String text) {
        this.buttonList.add(new GuiButton(id, x, y, width, height, text));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        if (button.id == 0) {
            LostTalesConfig.save();
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 10) {
            selected = HudElement.COMPASS;
            initGui();
            return;
        }
        if (button.id == 11) {
            selected = HudElement.QUICK_LOOT;
            initGui();
            return;
        }
        if (button.id == 12) {
            selected = HudElement.QUEST;
            initGui();
            return;
        }

        int step = isPlacementShiftKeyDown() ? STEP_FAST : STEP_NORMAL;
        if (button.id == 20) {
            moveSelected(0, -step);
        } else if (button.id == 21) {
            moveSelected(0, step);
        } else if (button.id == 22) {
            moveSelected(-step, 0);
        } else if (button.id == 23) {
            moveSelected(step, 0);
        } else if (button.id == 30) {
            LostTalesConfig.applyHudPreset("default");
        } else if (button.id == 31) {
            LostTalesConfig.applyHudPreset("lotr-safe");
        } else if (button.id == 32) {
            LostTalesConfig.applyHudPreset("compact");
        } else if (button.id == 33) {
            LostTalesConfig.applyHudPreset("minimal");
        }
    }

    private void moveSelected(int dx, int dy) {
        LostTalesConfig.moveHudOffset(selected.configKey, dx, dy);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            LostTalesConfig.save();
            this.mc.displayGuiScreen(parent);
            return;
        }
        int step = isPlacementShiftKeyDown() ? STEP_FAST : STEP_NORMAL;
        if (keyCode == Keyboard.KEY_UP) {
            moveSelected(0, -step);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            moveSelected(0, step);
        } else if (keyCode == Keyboard.KEY_LEFT) {
            moveSelected(-step, 0);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            moveSelected(step, 0);
        } else if (keyCode == Keyboard.KEY_TAB) {
            selected = selected.next();
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, "Lost Tales HUD Placement", this.width / 2, 12, 0xFFD37A);
        drawCenteredString(this.fontRendererObj, "Selected: " + selected.displayName + "  Offset: " + selected.offsetText(), this.width / 2, 24, 0xFFFFFF);

        drawPreview();

        String help = "Arrow buttons/keys move by 2%. Hold Shift for 10%. Presets save immediately.";
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + help, this.width / 2, this.height - 42, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview() {
        ScaledResolution resolution = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();

        drawCompassPreview(screenWidth, screenHeight);
        drawQuickLootPreview(screenWidth, screenHeight);
        drawQuestPreview(screenWidth, screenHeight);
    }

    private void drawCompassPreview(int screenWidth, int screenHeight) {
        int width = Math.min(LostTalesCompassHudRenderer.COMPASS_WIDTH, screenWidth - 8);
        int height = 32;
        int x = (screenWidth - width) * LostTalesConfig.compassHudOffsetX / 100;
        int y = screenHeight * LostTalesConfig.compassHudOffsetY / 100 + this.fontRendererObj.FONT_HEIGHT + 4;
        drawPreviewBox(x, y, width, height, HudElement.COMPASS, "Compass / marker label");
    }

    private void drawQuickLootPreview(int screenWidth, int screenHeight) {
        int width = 280;
        int height = 25 + Math.max(1, Math.min(5, LostTalesConfig.quickLootHudMaxRows)) * 24 + 30;
        int x = screenWidth / 2 + screenWidth / 2 * LostTalesConfig.quickLootHudOffsetX / 100;
        int y = screenHeight * LostTalesConfig.quickLootHudOffsetY / 100;
        if (x + width > screenWidth - 4) {
            x = screenWidth - width - 4;
        }
        drawPreviewBox(x, y, width, height, HudElement.QUICK_LOOT, "Quick Loot HUD");
    }

    private void drawQuestPreview(int screenWidth, int screenHeight) {
        int width = 216;
        int height = 72;
        int x = screenWidth * LostTalesConfig.questHudOffsetX / 100;
        int y = screenHeight * LostTalesConfig.questHudOffsetY / 100;
        if (x + width > screenWidth - 4) {
            x = screenWidth - width - 4;
        }
        drawPreviewBox(x, y, width, height, HudElement.QUEST, "Tracked Quest HUD");
    }

    private void drawPreviewBox(int x, int y, int width, int height, HudElement element, String label) {
        x = MathHelper.clamp_int(x, 4, Math.max(4, this.width - width - 4));
        y = MathHelper.clamp_int(y, 76, Math.max(76, this.height - height - 52));

        int fill = element == selected ? 0xAA302000 : 0x66000000;
        int border = element == selected ? 0xFFFFD37A : 0x99FFFFFF;
        drawRect(x, y, x + width, y + height, fill);
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        drawString(this.fontRendererObj, label, x + 5, y + 5, 0xFFFFFF);
        drawString(this.fontRendererObj, element.offsetText(), x + 5, y + 17, 0xAAAAAA);
    }

    private String elementLabel(HudElement element) {
        return element == selected ? "> " + element.displayName + " <" : element.displayName;
    }

    private boolean isPlacementShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private enum HudElement {
        COMPASS("Compass", "compass"),
        QUICK_LOOT("Quick Loot", "quickloot"),
        QUEST("Quest", "quest");

        private final String displayName;
        private final String configKey;

        HudElement(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }

        private HudElement next() {
            HudElement[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private String offsetText() {
            if (this == COMPASS) {
                return LostTalesConfig.compassHudOffsetX + ", " + LostTalesConfig.compassHudOffsetY;
            }
            if (this == QUICK_LOOT) {
                return LostTalesConfig.quickLootHudOffsetX + ", " + LostTalesConfig.quickLootHudOffsetY;
            }
            return LostTalesConfig.questHudOffsetX + ", " + LostTalesConfig.questHudOffsetY;
        }
    }
}
