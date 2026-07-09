package com.ninuna.losttales.gui.hud.mapmarker;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Draws Skyrim-style map marker discovery and small area-entry text. */
public final class LostTalesMapMarkerHudRenderer {
    private static final int MIN_TEXT_ALPHA = 4;
    private static final int DISCOVERY_LINE_WIDTH = 88;
    private static final int DISCOVERY_LINE_GAP = 9;

    private LostTalesMapMarkerHudRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showLostTalesHud || !LostTalesConfig.showCompassHud || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        LostTalesClientMapMarkerNotificationStore.updateAreaPresence(minecraft);

        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        renderDiscoveryNotice(minecraft, resolution);
        renderAreaNotice(minecraft, resolution);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void renderDiscoveryNotice(Minecraft minecraft, ScaledResolution resolution) {
        LostTalesClientMapMarkerNotificationStore.DiscoveryNotice notice = LostTalesClientMapMarkerNotificationStore.getDiscoveryNotice();
        if (notice == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float progress = notice.getAgeFraction(now);
        float alpha = getDiscoveryAlpha(progress);
        if (alpha <= 0.0F) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        int screenWidth = resolution.getScaledWidth();
        int y = resolution.getScaledHeight() / 3;
        String title = "Location Discovered";
        String name = notice.getName();

        int titleColor = colorWithAlpha(0xD9D1B8, alpha * 0.78F);
        int nameColor = colorWithAlpha(0xFFFFFF, alpha);
        int lineColor = colorWithAlpha(0xFFFFFF, alpha * 0.58F);
        if (nameColor == 0) {
            return;
        }

        int titleX = (screenWidth - font.getStringWidth(title)) / 2;
        int nameX = (screenWidth - font.getStringWidth(name)) / 2;
        font.drawStringWithShadow(title, titleX, y, titleColor);
        font.drawStringWithShadow(name, nameX, y + 13, nameColor);

        int centerX = screenWidth / 2;
        int lineY = y + 26;
        Gui.drawRect(centerX - DISCOVERY_LINE_GAP - DISCOVERY_LINE_WIDTH, lineY, centerX - DISCOVERY_LINE_GAP, lineY + 1, lineColor);
        Gui.drawRect(centerX + DISCOVERY_LINE_GAP, lineY, centerX + DISCOVERY_LINE_GAP + DISCOVERY_LINE_WIDTH, lineY + 1, lineColor);
        drawSmallDiamond(centerX, lineY, lineColor);
    }

    private static void renderAreaNotice(Minecraft minecraft, ScaledResolution resolution) {
        LostTalesClientMapMarkerNotificationStore.AreaNotice notice = LostTalesClientMapMarkerNotificationStore.getAreaNotice();
        if (notice == null || LostTalesClientMapMarkerNotificationStore.isDiscoveryNoticeActive(notice.getMarkerId())) {
            return;
        }

        long now = System.currentTimeMillis();
        float progress = notice.getAgeFraction(now);
        float alpha = getAreaAlpha(progress);
        if (alpha <= 0.0F) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int compassX = (screenWidth - LostTalesCompassHudRenderer.COMPASS_WIDTH) * LostTalesConfig.compassHudOffsetX / 100;
        int compassY = screenHeight * LostTalesConfig.compassHudOffsetY / 100 + font.FONT_HEIGHT + LostTalesCompassHudRenderer.MAP_MARKER_DISTANCE_LABEL_OFFSET_Y;
        int x = compassX + LostTalesCompassHudRenderer.COMPASS_WIDTH / 2;
        int y = compassY + LostTalesCompassHudRenderer.COMPASS_HEIGHT + font.FONT_HEIGHT + 14;

        String text = notice.getName();
        int color = colorWithAlpha(0xFFFFFF, alpha * 0.74F);
        if (color != 0) {
            float scale = 0.72F;
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            font.drawStringWithShadow(text, -font.getStringWidth(text) / 2, 0, color);
            GL11.glPopMatrix();
        }
    }

    private static float getDiscoveryAlpha(float progress) {
        if (progress < 0.16F) {
            return progress / 0.16F;
        }
        if (progress > 0.72F) {
            return Math.max(0.0F, (1.0F - progress) / 0.28F);
        }
        return 1.0F;
    }

    private static float getAreaAlpha(float progress) {
        if (progress < 0.18F) {
            return progress / 0.18F;
        }
        if (progress > 0.70F) {
            return Math.max(0.0F, (1.0F - progress) / 0.30F);
        }
        return 1.0F;
    }

    private static int colorWithAlpha(int rgb, float alphaF) {
        int alpha = MathHelper.clamp_int((int) (alphaF * 255.0F), 0, 255);
        if (alpha < MIN_TEXT_ALPHA) {
            return 0;
        }
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static void drawSmallDiamond(int centerX, int centerY, int color) {
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        Gui.drawRect(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color);
    }
}
