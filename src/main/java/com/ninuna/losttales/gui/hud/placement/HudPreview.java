package com.ninuna.losttales.gui.hud.placement;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.fellowship.LostTalesFellowshipHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.mapmarker.LostTalesMapMarkerHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import org.lwjgl.opengl.GL11;

/**
 * The HUD Placement page's preview: the game's screen in small as it will
 * look while playing. Behind, the screen's own picture, taken before the
 * windows were drawn; over it the game's hotbar and the rows over it, from
 * the game's own artwork and the player's own health, armour, food and
 * experience; over those the mod's panels, each drawn by its own renderer
 * into the small screen, so what shows is what the panel shows now. Under
 * everything each panel's box stands as a faint outline, so a panel with
 * nothing to show now — no fellowship travelled with, no quest pinned, no
 * container looked at, an empty chat feed — still shows where it stands.
 */
final class HudPreview {
    private static final ResourceLocation WIDGETS =
            new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation ICONS =
            new ResourceLocation("textures/gui/icons.png");
    /** The game's own HUD artwork sheets are this wide and tall. */
    private static final float SHEET = 256.0F;
    /** Half the hotbar's width, which the rows over it share. */
    private static final int HALF_HOTBAR = 91;
    /** The faint outlines' strength, a third. */
    private static final int OUTLINE_ALPHA = 0x55;
    /** Whether a panel failing to draw itself here was told once already. */
    private static boolean failureTold;

    private HudPreview() {}

    /**
     * Draws the preview in {@code shown} (left, top, width, height and the
     * scale the screen is shrunk by), the game's screen {@code screen}
     * wide and tall; {@code clipX} and {@code clipY} are where the box's
     * top left really stands, for the cut that keeps everything in it.
     */
    static void draw(Minecraft minecraft, double[] shown, int[] screen,
                     float partialTicks, int alpha, double clipX, double clipY) {
        float left = (float)shown[0];
        float top = (float)shown[1];
        float right = left + (float)shown[2];
        float bottom = top + (float)shown[3];
        float scale = (float)shown[4];
        boolean clipped = LostTalesUiClip.begin(minecraft, clipX,
                clipX + shown[2], clipY, clipY + shown[3], true);
        try {
            if (!LostTalesGuiRegionBlur.getInstance().drawScreenInto(left,
                    top, right, bottom, alpha / 255.0F)) {
                LostTalesUiInk.fillRect(left, top, right, bottom,
                        LostTalesUiInk.argb(LostTalesUiTheme.primaryRgb(),
                                alpha));
            }
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_CURRENT_BIT);
            try {
                GL11.glTranslatef(left, top, 0.0F);
                GL11.glScalef(scale, scale, 1.0F);
                drawOutlines(minecraft, screen, scale, alpha);
                drawGameHud(minecraft, screen, alpha);
                drawPanels(minecraft, partialTicks);
            } finally {
                GL11.glPopAttrib();
                GL11.glPopMatrix();
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    /** Each panel's box as a faint line a display pixel wide, in the screen's own pixels. */
    private static void drawOutlines(Minecraft minecraft, int[] screen,
                                     float scale, int alpha) {
        float line = 1.0F / Math.max(0.01F, scale);
        int argb = LostTalesUiInk.argb(LostTalesColors.rgb(LostTalesColors.SAND),
                Math.round(alpha * OUTLINE_ALPHA / 255.0F));
        for (HudPanel panel : HudPanel.values()) {
            double[] box = panel.box(minecraft, screen[0], screen[1]);
            float left = (float)box[0];
            float top = (float)box[1];
            float right = left + (float)box[2];
            float bottom = top + (float)box[3];
            LostTalesUiInk.fillRect(left, top, right, top + line, argb);
            LostTalesUiInk.fillRect(left, bottom - line, right, bottom, argb);
            LostTalesUiInk.fillRect(left, top + line, left + line,
                    bottom - line, argb);
            LostTalesUiInk.fillRect(right - line, top + line, right,
                    bottom - line, argb);
        }
    }

    /**
     * The game's hotbar and the rows over it, as the game lays them out:
     * the hearts and the armour on the left, the food on the right, the
     * experience bar between them and the hotbar, each from the player's
     * own values; the hotbar alone where the game draws no rows.
     */
    private static void drawGameHud(Minecraft minecraft, int[] screen,
                                    int alpha) {
        EntityPlayer player = minecraft.thePlayer;
        if (player == null || minecraft.playerController == null) {
            return;
        }
        int middle = screen[0] / 2;
        int left = middle - HALF_HOTBAR;
        int right = middle + HALF_HOTBAR;
        int foot = screen[1];
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        minecraft.getTextureManager().bindTexture(WIDGETS);
        blit(left, foot - 22, 0, 0, 182, 22);
        blit(left - 1 + player.inventory.currentItem * 20, foot - 23, 0, 22,
                24, 24);
        if (!minecraft.playerController.shouldDrawHUD()) {
            return;
        }
        minecraft.getTextureManager().bindTexture(ICONS);
        if (minecraft.playerController.gameIsSurvivalOrAdventure()) {
            blit(left, foot - 29, 0, 64, 182, 5);
            int filled = (int)(player.experience * 183.0F);
            if (filled > 0) {
                blit(left, foot - 29, 0, 69, filled, 5);
            }
        }
        int health = MathHelper.ceiling_float_int(player.getHealth());
        int armour = ForgeHooks.getTotalArmorValue(player);
        int food = player.getFoodStats().getFoodLevel();
        for (int index = 0; index < 10; index++) {
            int x = left + index * 8;
            blit(x, foot - 39, 16, 0, 9, 9);
            if (index * 2 + 1 < health) {
                blit(x, foot - 39, 52, 0, 9, 9);
            } else if (index * 2 + 1 == health) {
                blit(x, foot - 39, 61, 0, 9, 9);
            }
            if (armour > 0) {
                int u = index * 2 + 1 < armour ? 34
                        : index * 2 + 1 == armour ? 25 : 16;
                blit(x, foot - 49, u, 9, 9, 9);
            }
            int foodX = right - index * 8 - 9;
            blit(foodX, foot - 39, 16, 27, 9, 9);
            if (index * 2 + 1 < food) {
                blit(foodX, foot - 39, 52, 27, 9, 9);
            } else if (index * 2 + 1 == food) {
                blit(foodX, foot - 39, 61, 27, 9, 9);
            }
        }
    }

    /** A piece of the bound game sheet at its own size, at {@code x}, {@code y}. */
    private static void blit(int x, int y, int u, int v, int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, u / SHEET,
                (v + height) / SHEET);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D,
                (u + width) / SHEET, (v + height) / SHEET);
        tessellator.addVertexWithUV(x + width, y, 0.0D, (u + width) / SHEET,
                v / SHEET);
        tessellator.addVertexWithUV(x, y, 0.0D, u / SHEET, v / SHEET);
        tessellator.draw();
    }

    /**
     * The mod's panels, each by its own renderer, in the order the HUD
     * draws them, the notices claiming their slot afresh. Quick loot shows
     * only what is already known of the container looked at, so the
     * preview asks the server nothing. One that fails to draw here leaves
     * the rest of the page standing, and the log is told once.
     */
    private static void drawPanels(Minecraft minecraft, float partialTicks) {
        try {
            LostTalesNotificationHud.beginFrame();
            LostTalesQuickLootHudRenderer.renderKnown(minecraft);
            LostTalesCompassHudRenderer.render(minecraft, partialTicks);
            LostTalesMapMarkerHudRenderer.render(minecraft, partialTicks);
            LostTalesFellowshipHudRenderer.render(minecraft, partialTicks);
            LostTalesQuestHudRenderer.render(minecraft, partialTicks);
        } catch (RuntimeException failure) {
            if (!failureTold) {
                failureTold = true;
                FMLLog.warning("[losttales] A HUD panel could not draw itself"
                        + " in the HUD Placement page's preview: %s", failure);
            }
        }
    }
}
