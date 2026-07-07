package com.ninuna.losttales.gui.hud.loot;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.inventory.LostTalesQuickLootInventoryHelper;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuickLootDropItemPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootRequestPacket;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class LostTalesQuickLootHudRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/quickloothud.png");

    private static final int TEXTURE_WIDTH = 280;
    private static final int TEXTURE_HEIGHT = 160;
    private static final int TOP_HEIGHT = 23;
    private static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_HEIGHT = 8;
    private static final int SELECTION_WIDTH = 211;
    private static final int SELECTION_HEIGHT = 20;
    private static final int SELECTION_OFFSET_X = 13;

    private static final int ORNAMENT_HORIZONTAL_HEIGHT = 10;
    private static final int ORNAMENT_VERTICAL_WIDTH = 22;
    private static final int ORNAMENT_VERTICAL_HEIGHT = 22;
    private static final int ORNAMENT_VERTICAL_OFFSET_X = -5;
    private static final int ORNAMENT_VERTICAL_LINE_OFFSET_X = 5;

    private static final int KEYS_HEIGHT = 13;
    private static final int KEY_R_WIDTH = 14;
    private static final int KEY_ALT_WIDTH = 20;
    private static final int KEY_MIN_WIDTH = 14;
    private static final int KEY_SCROLL_WIDTH = 16;

    private static final int ARROW_WIDTH = 5;
    private static final int ARROW_HEIGHT = 3;
    private static final int ARROW_OFFSET_X = SELECTION_OFFSET_X + SELECTION_WIDTH + 5;

    private static final long REQUEST_INTERVAL_MS = 250L;
    private static final RenderItem RENDER_ITEM = new RenderItem();

    private static int selectedRow;
    private static int scrollOffset;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastY = Integer.MIN_VALUE;
    private static int lastZ = Integer.MIN_VALUE;
    private static long lastRequestTime;
    private static boolean wasLookingAtContainer;

    private LostTalesQuickLootHudRenderer() {}

    public static void render(Minecraft minecraft) {
        if (!LostTalesConfig.showLostTalesHud || !LostTalesConfig.showQuickLootHud || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            resetIfNeeded(false);
            return;
        }

        Target target = getLookTarget(minecraft);
        if (target == null) {
            resetIfNeeded(false);
            return;
        }

        requestSnapshotIfNeeded(target);
        LostTalesClientQuickLootCache.Snapshot snapshot = LostTalesClientQuickLootCache.get(target.x, target.y, target.z);
        if (snapshot == null) {
            resetIfNeeded(true);
            return;
        }

        resetIfNeeded(true);
        renderSnapshot(minecraft, snapshot);
    }

    public static void moveSelection(int delta) {
        Target target = getLookTarget(Minecraft.getMinecraft());
        if (target == null) return;

        LostTalesClientQuickLootCache.Snapshot snapshot = LostTalesClientQuickLootCache.get(target.x, target.y, target.z);
        if (snapshot == null) return;

        List<Integer> slots = snapshot.getNonEmptySlots();
        int maxIndex = slots.size() - 1;
        if (maxIndex < 0) {
            selectedRow = 0;
            scrollOffset = 0;
            return;
        }

        int maxRows = Math.max(1, LostTalesConfig.quickLootHudMaxRows);
        selectedRow = Math.max(0, Math.min(selectedRow + delta, maxIndex));

        if (selectedRow < scrollOffset) {
            scrollOffset = selectedRow;
        } else if (selectedRow >= scrollOffset + maxRows) {
            scrollOffset = selectedRow - maxRows + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, maxIndex - maxRows + 1)));
    }

    public static void dropSelectedItem() {
        Target target = getLookTarget(Minecraft.getMinecraft());
        if (target == null) return;

        LostTalesClientQuickLootCache.Snapshot snapshot = LostTalesClientQuickLootCache.get(target.x, target.y, target.z);
        if (snapshot == null || snapshot.sealed) return;

        List<Integer> slots = snapshot.getNonEmptySlots();
        if (selectedRow < 0 || selectedRow >= slots.size()) return;

        LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuickLootDropItemPacket(target.x, target.y, target.z, slots.get(selectedRow)));
    }

    public static boolean isLookingAtContainer() {
        return getLookTarget(Minecraft.getMinecraft()) != null;
    }

    public static void resetHud() {
        selectedRow = 0;
        scrollOffset = 0;
        lastX = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
        lastRequestTime = 0L;
        wasLookingAtContainer = false;
        LostTalesClientQuickLootCache.clear();
    }

    private static void renderSnapshot(Minecraft minecraft, LostTalesClientQuickLootCache.Snapshot snapshot) {
        FontRenderer font = minecraft.fontRenderer;
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();

        int panelX = screenWidth / 2 + screenWidth / 2 * LostTalesConfig.quickLootHudOffsetX / 100;
        int panelY = screenHeight * LostTalesConfig.quickLootHudOffsetY / 100;
        int estimatedPanelHeight = TOP_HEIGHT + Math.max(1, LostTalesConfig.quickLootHudMaxRows) * ROW_HEIGHT + BOTTOM_HEIGHT + KEYS_HEIGHT + 8;
        panelX = MathHelper.clamp_int(panelX, 4, Math.max(4, screenWidth - TEXTURE_WIDTH - 4));
        panelY = MathHelper.clamp_int(panelY, 4, Math.max(4, screenHeight - estimatedPanelHeight - 4));
        int itemX = panelX + 17;
        int itemNameX = itemX + 21;
        int rowY = panelY + TOP_HEIGHT;
        int visibleRows = Math.max(1, LostTalesConfig.quickLootHudMaxRows);

        List<Integer> slots = snapshot.getNonEmptySlots();
        selectedRow = MathHelper.clamp_int(selectedRow, 0, Math.max(0, slots.size() - 1));
        scrollOffset = MathHelper.clamp_int(scrollOffset, 0, Math.max(0, slots.size() - visibleRows));

        int rowsToDraw = Math.min(visibleRows, Math.max(1, slots.size() - scrollOffset));
        if (slots.isEmpty()) rowsToDraw = 1;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        drawQuickLootTexture(minecraft, TEXTURE, panelX, panelY, 0, 0, TEXTURE_WIDTH, TOP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        renderHorizontalOrnament(minecraft, font, panelX, panelY, snapshot.title);
        font.drawStringWithShadow(snapshot.title == null ? "Container" : snapshot.title, panelX + 3, panelY + font.FONT_HEIGHT / 2, 0xFFFFFF);

        if (slots.isEmpty()) {
            drawQuickLootTexture(minecraft, TEXTURE, panelX, rowY, 0, 25, TEXTURE_WIDTH, ROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
            font.drawStringWithShadow(StatCollector.translateToLocal("quickLootHud.losttales.empty"), itemNameX, rowY + 7, 0xFFFFFF);
        } else {
            for (int i = 0; i < rowsToDraw; i++) {
                int actualRow = scrollOffset + i;
                int slot = slots.get(actualRow);
                ItemStack stack = snapshot.getStack(slot);
                int y = rowY + i * ROW_HEIGHT;

                drawQuickLootTexture(minecraft, TEXTURE, panelX, y, 0, 25, TEXTURE_WIDTH, ROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
                if (actualRow == selectedRow) {
                    drawQuickLootTexture(minecraft, TEXTURE, panelX + SELECTION_OFFSET_X, y + 1, 0, 74, SELECTION_WIDTH, SELECTION_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
                }
                renderStack(minecraft, stack, itemX, y + 3);
                String name = stack == null ? "" : stack.getDisplayName();
                font.drawStringWithShadow(name, itemNameX, y + 7, 0xFFFFFF);
            }
        }

        int bottomY = panelY + TOP_HEIGHT + rowsToDraw * ROW_HEIGHT;
        drawQuickLootTexture(minecraft, TEXTURE, panelX, bottomY, 0, 49, TEXTURE_WIDTH, BOTTOM_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        renderScrollArrows(minecraft, panelX, rowY, bottomY, rowsToDraw, slots.size());
        renderHints(minecraft, font, panelX + 3, bottomY + BOTTOM_HEIGHT + 3, !snapshot.sealed);
        renderVerticalOrnament(minecraft, panelX, panelY, rowsToDraw);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    /**
     * Draws the authored quick-loot HUD texture without alpha testing.
     *
     * <p>The PNG already contains its own soft transparent fade, just like the
     * compass HUD art.  Minecraft 1.7.10 GUI rendering can leave alpha testing
     * enabled, which clips those low-alpha pixels and makes the background look
     * harsh or pixelated.  We keep the normal texture filtering untouched and
     * only disable alpha testing during these HUD texture blits.</p>
     */
    private static void drawQuickLootTexture(Minecraft minecraft, ResourceLocation texture, float x, float y, int u, int v, int width, int height, int textureWidth, int textureHeight, float alpha) {
        LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(minecraft, texture, x, y, u, v, width, height, textureWidth, textureHeight, alpha);
    }

    private static void renderHorizontalOrnament(Minecraft minecraft, FontRenderer font, int panelX, int panelY, String title) {
        int ornamentX = panelX + 3 + font.getStringWidth(title == null ? "" : title) + 5;
        int ornamentY = panelY + font.FONT_HEIGHT / 2 - 1;
        drawQuickLootTexture(
                minecraft,
                TEXTURE,
                ornamentX,
                ornamentY,
                0,
                120,
                TEXTURE_WIDTH,
                ORNAMENT_HORIZONTAL_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                1.0F
        );
    }

    private static void renderScrollArrows(Minecraft minecraft, int panelX, int rowY, int bottomY, int rowsToDraw, int totalRows) {
        int arrowX = panelX + ARROW_OFFSET_X;
        if (scrollOffset > 0) {
            drawQuickLootTexture(minecraft, TEXTURE, arrowX, rowY - ARROW_HEIGHT, 6, 132, ARROW_WIDTH, ARROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        }
        if (scrollOffset + rowsToDraw < totalRows) {
            drawQuickLootTexture(minecraft, TEXTURE, arrowX, bottomY, 0, 132, ARROW_WIDTH, ARROW_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        }
    }

    private static void renderVerticalOrnament(Minecraft minecraft, int panelX, int panelY, int rowsToDraw) {
        int ornamentX = panelX + ORNAMENT_VERTICAL_OFFSET_X;
        int ornamentY = panelY + TOP_HEIGHT + ROW_HEIGHT * rowsToDraw / 2 - ORNAMENT_VERTICAL_HEIGHT / 2;
        int lineX = panelX + ORNAMENT_VERTICAL_LINE_OFFSET_X;
        int extraLineHeight = Math.max(0, (rowsToDraw - 1) * ROW_HEIGHT / 2 + 3);

        drawQuickLootTexture(
                minecraft,
                TEXTURE,
                ornamentX,
                ornamentY,
                0,
                96,
                ORNAMENT_VERTICAL_WIDTH,
                ORNAMENT_VERTICAL_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                1.0F
        );
        if (extraLineHeight > 0) {
            LostTalesCompassHudRenderHelper.drawVerticalGradientLine(lineX, ornamentY - extraLineHeight, ornamentY, 0.0F, 1.0F);
            LostTalesCompassHudRenderHelper.drawVerticalGradientLine(lineX, ornamentY + ORNAMENT_VERTICAL_HEIGHT, ornamentY + ORNAMENT_VERTICAL_HEIGHT + extraLineHeight, 1.0F, 0.0F);
        }
    }

    /**
     * Renders item stacks with a normal inventory-like GUI state.
     *
     * <p>The quick-loot panel itself is drawn with alpha testing disabled so the
     * authored PNG fades are preserved.  Item icons should not inherit that HUD
     * state: vanilla inventory rendering expects alpha testing and GUI item
     * lighting, and leaving the translucent HUD state active can make block
     * icons look dark or as though they are behind the panel.  This method
     * temporarily switches to a clean item-render state and then restores the
     * HUD state for the remaining overlay text and ornaments.</p>
     */
    private static void renderStack(Minecraft minecraft, ItemStack stack, int x, int y) {
        if (stack == null) return;

        float previousZLevel = RENDER_ITEM.zLevel;
        float previousLightmapX = OpenGlHelper.lastBrightnessX;
        float previousLightmapY = OpenGlHelper.lastBrightnessY;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            /*
             * Render the item using a clean vanilla GUI item state.  The quick-loot panel is
             * intentionally drawn with alpha testing disabled so its soft PNG fades survive,
             * but block item icons inherit that state very badly in 1.7.10 and can become
             * almost black.  Pushing the full GL attribute state here keeps the HUD's translucent
             * state away from RenderItem and makes block stacks match the inventory/hotbar more
             * closely.
             */
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            RenderHelper.enableGUIStandardItemLighting();
            RENDER_ITEM.zLevel = 200.0F;
            RENDER_ITEM.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
            RENDER_ITEM.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        } finally {
            RENDER_ITEM.zLevel = previousZLevel;
            RenderHelper.disableStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightmapX, previousLightmapY);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderHints(Minecraft minecraft, FontRenderer font, int x, int y, boolean drawDropKey) {
        int textY = y + KEYS_HEIGHT / 2 - font.FONT_HEIGHT / 2;
        int cursorX = x;

        if (drawDropKey) {
            cursorX += drawUseKey(minecraft, font, cursorX, y) + 3;
            String drop = StatCollector.translateToLocal("quickLootHud.losttales.drop");
            font.drawStringWithShadow(drop, cursorX, textY, 0xFFFFFF);
            cursorX += font.getStringWidth(drop) + 7;
        }

        cursorX += drawModifierKey(minecraft, font, cursorX, y) + 3;
        String plus = StatCollector.translateToLocal("quickLootHud.losttales.plus");
        font.drawStringWithShadow(plus, cursorX, textY, 0xFFFFFF);
        cursorX += font.getStringWidth(plus) + 3;

        drawQuickLootTexture(minecraft, TEXTURE, cursorX, y, 36, 59, KEY_SCROLL_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        cursorX += KEY_SCROLL_WIDTH + 3;
        String scroll = StatCollector.translateToLocal("quickLootHud.losttales.scroll");
        font.drawStringWithShadow(scroll, cursorX, textY, 0xFFFFFF);
    }

    private static int drawUseKey(Minecraft minecraft, FontRenderer font, int x, int y) {
        String label = LostTalesKeyBindings.getUseKeyDisplayName();
        if (isUseKeySprite(label)) {
            drawQuickLootTexture(minecraft, TEXTURE, x, y, 0, 59, KEY_R_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
            return KEY_R_WIDTH;
        }
        return drawKeyLabel(font, label, x, y);
    }

    private static int drawModifierKey(Minecraft minecraft, FontRenderer font, int x, int y) {
        String label = LostTalesKeyBindings.getModifierKeyDisplayName();
        if (isAltKeySprite(label)) {
            drawQuickLootTexture(minecraft, TEXTURE, x, y, 15, 59, KEY_ALT_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
            return KEY_ALT_WIDTH;
        }
        return drawKeyLabel(font, label, x, y);
    }

    private static boolean isUseKeySprite(String label) {
        return label != null && "R".equalsIgnoreCase(label.trim());
    }

    private static boolean isAltKeySprite(String label) {
        if (label == null) return false;
        String normalized = label.trim().toLowerCase();
        return "left alt".equals(normalized) || "lmenu".equals(normalized) || "alt".equals(normalized);
    }

    private static int drawKeyLabel(FontRenderer font, String label, int x, int y) {
        if (label == null || label.length() == 0) label = "?";
        int width = Math.max(KEY_MIN_WIDTH, font.getStringWidth(label) + 8);
        drawSolidRect(x, y, x + width, y + KEYS_HEIGHT, 0xAA080808);
        drawSolidRect(x, y, x + width, y + 1, 0xCCB8B8B8);
        drawSolidRect(x, y + KEYS_HEIGHT - 1, x + width, y + KEYS_HEIGHT, 0xCC303030);
        drawSolidRect(x, y, x + 1, y + KEYS_HEIGHT, 0xCC909090);
        drawSolidRect(x + width - 1, y, x + width, y + KEYS_HEIGHT, 0xCC303030);
        font.drawStringWithShadow(label, x + 4, y + KEYS_HEIGHT / 2 - font.FONT_HEIGHT / 2, 0xFFFFFF);
        return width;
    }

    private static void drawSolidRect(int left, int top, int right, int bottom, int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();

        if (previousTexture2D) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        if (previousBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Target getLookTarget(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null || minecraft.objectMouseOver == null || minecraft.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }

        LostTalesQuickLootInventoryHelper.InventoryAccess access = LostTalesQuickLootInventoryHelper.resolve(
                minecraft.theWorld,
                minecraft.objectMouseOver.blockX,
                minecraft.objectMouseOver.blockY,
                minecraft.objectMouseOver.blockZ
        );
        if (access == null) return null;
        return new Target(access.getX(), access.getY(), access.getZ());
    }

    private static void requestSnapshotIfNeeded(Target target) {
        long now = System.currentTimeMillis();
        boolean newTarget = target.x != lastX || target.y != lastY || target.z != lastZ;
        if (newTarget) {
            selectedRow = 0;
            scrollOffset = 0;
            lastX = target.x;
            lastY = target.y;
            lastZ = target.z;
            lastRequestTime = 0L;
            LostTalesClientQuickLootCache.clear();
        }

        if (now - lastRequestTime >= REQUEST_INTERVAL_MS) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuickLootRequestPacket(target.x, target.y, target.z));
            lastRequestTime = now;
        }
    }

    private static void resetIfNeeded(boolean lookingAtContainer) {
        if (!lookingAtContainer && wasLookingAtContainer) {
            selectedRow = 0;
            scrollOffset = 0;
            lastX = Integer.MIN_VALUE;
            lastY = Integer.MIN_VALUE;
            lastZ = Integer.MIN_VALUE;
            lastRequestTime = 0L;
            LostTalesClientQuickLootCache.clear();
        }
        wasLookingAtContainer = lookingAtContainer;
    }

    private static final class Target {
        final int x;
        final int y;
        final int z;

        Target(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
