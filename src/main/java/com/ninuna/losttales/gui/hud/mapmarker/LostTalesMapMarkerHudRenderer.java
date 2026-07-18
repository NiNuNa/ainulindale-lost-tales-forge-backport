package com.ninuna.losttales.gui.hud.mapmarker;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerNotificationStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
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
    private static final int DISCOVERY_NOTICE_WIDTH =
            (DISCOVERY_LINE_WIDTH + DISCOVERY_LINE_GAP) * 2;
    private static final int DISCOVERY_NOTICE_HEIGHT = 30;
    private static final int AREA_NOTICE_WIDTH = 180;
    private static final int AREA_NOTICE_HEIGHT = 12;

    private LostTalesMapMarkerHudRenderer() {}

    public static int getDiscoveryPlacementWidth() {
        return DISCOVERY_NOTICE_WIDTH;
    }

    public static int getDiscoveryPlacementHeight() {
        return DISCOVERY_NOTICE_HEIGHT;
    }

    public static int getAreaPlacementWidth() {
        return AREA_NOTICE_WIDTH;
    }

    public static int getAreaPlacementHeight() {
        return AREA_NOTICE_HEIGHT;
    }

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
        HudPlacementLayout.Bounds placement = HudPlacementLayout.calculate(
                resolution.getScaledWidth(), resolution.getScaledHeight(),
                DISCOVERY_NOTICE_WIDTH, DISCOVERY_NOTICE_HEIGHT,
                LostTalesConfig.mapDiscoveryHudOffsetX,
                LostTalesConfig.mapDiscoveryHudOffsetY,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT);
        int centerX = placement.x + placement.width / 2;
        int y = placement.y;
        String title = "Location Discovered";
        String name = trimToWidth(font, notice.getName(), placement.width);

        int titleColor = colorWithAlpha(0xD9D1B8, alpha * 0.78F);
        int nameColor = colorWithAlpha(0xFFFFFF, alpha);
        int lineColor = colorWithAlpha(0xFFFFFF, alpha * 0.58F);
        if (nameColor == 0) {
            return;
        }

        int titleX = centerX - font.getStringWidth(title) / 2;
        int nameX = centerX - font.getStringWidth(name) / 2;
        font.drawStringWithShadow(title, titleX, y, titleColor);
        font.drawStringWithShadow(name, nameX, y + 13, nameColor);

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
        HudPlacementLayout.Bounds placement = HudPlacementLayout.calculate(
                resolution.getScaledWidth(), resolution.getScaledHeight(),
                AREA_NOTICE_WIDTH, AREA_NOTICE_HEIGHT,
                LostTalesConfig.areaNoticeHudOffsetX,
                LostTalesConfig.areaNoticeHudOffsetY,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT,
                HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT);
        int x = placement.x + placement.width / 2;
        int y = placement.y;

        String text = trimToWidth(font, notice.getName(),
                Math.round(AREA_NOTICE_WIDTH / 0.72F));
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

    private static String trimToWidth(FontRenderer font,
                                      String text, int width) {
        if (text == null || text.length() == 0) {
            return "";
        }
        return font.trimStringToWidth(text, Math.max(1, width));
    }
}
