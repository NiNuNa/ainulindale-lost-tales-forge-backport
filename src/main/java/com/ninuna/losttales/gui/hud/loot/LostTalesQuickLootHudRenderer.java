package com.ninuna.losttales.gui.hud.loot;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.LostTalesBlockUrnTall;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuickLootDropItemPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.util.List;

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

        boolean previousAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        float previousZLevel = RENDER_ITEM.zLevel;

        float previousLightmapX = OpenGlHelper.lastBrightnessX;
        float previousLightmapY = OpenGlHelper.lastBrightnessY;

        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            /*
             * In-game HUD rendering still has the world depth buffer around.
             * If the inventory item renderer is allowed to test against that
             * stale world depth, parts of block icons can fail the depth test
             * and appear dark or as if they are behind the translucent HUD.
             * Clearing the depth buffer here mirrors the clean GUI state used
             * by vanilla inventory/hotbar item rendering, while the HUD panel
             * itself has already been drawn with depth disabled.
             */
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);

            /*
             * Keep quick-loot stack icons full-bright like normal GUI items.
             * Without this, 1.7.10 can inherit the world's current lightmap
             * values, which makes block items look much darker than they do
             * in the inventory or hotbar, especially under trees or at night.
             */
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

            if (previousTexture2D) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            if (previousAlphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (previousBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (previousDepthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            if (previousLighting) {
                GL11.glEnable(GL11.GL_LIGHTING);
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
            }
            GL11.glDepthMask(previousDepthMask);
            GL11.glPopMatrix();
        }
    }

    private static void renderHints(Minecraft minecraft, FontRenderer font, int x, int y, boolean drawDropKey) {
        int textY = y + KEYS_HEIGHT / 2 - font.FONT_HEIGHT / 2;
        int textureAltX;

        if (drawDropKey) {
            drawQuickLootTexture(minecraft, TEXTURE, x, y, 0, 59, KEY_R_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
            int textDropX = x + KEY_R_WIDTH + 3;
            String drop = StatCollector.translateToLocal("quickLootHud.losttales.drop");
            font.drawStringWithShadow(drop, textDropX, textY, 0xFFFFFF);
            textureAltX = textDropX + font.getStringWidth(drop) + 7;
        } else {
            textureAltX = x;
        }

        String plus = StatCollector.translateToLocal("quickLootHud.losttales.plus");
        String scroll = StatCollector.translateToLocal("quickLootHud.losttales.scroll");
        int textAltX = textureAltX + KEY_ALT_WIDTH + 3;
        int textureScrollX = textAltX + font.getStringWidth(plus) + 3;
        int textScrollX = textureScrollX + KEY_SCROLL_WIDTH + 3;

        drawQuickLootTexture(minecraft, TEXTURE, textureAltX, y, 15, 59, KEY_ALT_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        font.drawStringWithShadow(plus, textAltX, textY, 0xFFFFFF);
        drawQuickLootTexture(minecraft, TEXTURE, textureScrollX, y, 36, 59, KEY_SCROLL_WIDTH, KEYS_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT, 1.0F);
        font.drawStringWithShadow(scroll, textScrollX, textY, 0xFFFFFF);
    }

    private static Target getLookTarget(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null || minecraft.objectMouseOver == null || minecraft.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }

        int x = minecraft.objectMouseOver.blockX;
        int y = minecraft.objectMouseOver.blockY;
        int z = minecraft.objectMouseOver.blockZ;
        if (minecraft.theWorld.getBlock(x, y, z) instanceof LostTalesBlockUrnTall && minecraft.theWorld.getBlockMetadata(x, y, z) == 4) {
            y--;
        }

        TileEntity tileEntity = minecraft.theWorld.getTileEntity(x, y, z);
        if (!(tileEntity instanceof net.minecraft.inventory.IInventory)) return null;
        return new Target(x, y, z);
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
