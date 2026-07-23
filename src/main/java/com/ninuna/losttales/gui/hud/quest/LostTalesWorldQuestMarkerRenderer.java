package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestMarkerHelper;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
/**
 * Lightweight 1.7.10 world-space quest marker labels.
 *
 * The modern branch renders map markers through the level render stage. This
 * backport uses the old nameplate-style GL transform so it remains compatible
 * with Forge 1.7.10 and does not require any modern rendering classes.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesWorldQuestMarkerRenderer {
    private static final float LABEL_SCALE = 0.02666667F;
    private static final double LABEL_Y_OFFSET = 2.25D;

    private LostTalesWorldQuestMarkerRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!shouldRender(minecraft)) {
            return;
        }

        EntityPlayer player = minecraft.thePlayer;
        Map<String, String> activeQuestMarkers = LostTalesClientQuestMarkerHelper.collectActiveQuestMarkerLabels();
        Set<LostTalesClientQuestMarkerHelper.ActiveCoordinateMarker> activeCoordinateMarkers = LostTalesClientQuestMarkerHelper.collectActiveCoordinateMarkers();
        if (activeQuestMarkers.isEmpty() && activeCoordinateMarkers.isEmpty()) {
            return;
        }

        List<LostTalesMapMarkerData> markers = LostTalesClientMapMarkerStore.getSharedMarkers();
        if (markers.isEmpty() && activeCoordinateMarkers.isEmpty()) {
            return;
        }

        double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        int dimension = minecraft.theWorld.provider.dimensionId;
        double maxDistance = Math.max(24.0D, LostTalesConfig.worldQuestMarkerMaxDistance);
        double maxDistanceSq = maxDistance * maxDistance;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        try {
            for (LostTalesMapMarkerData marker : markers) {
                if (marker == null || marker.getId() == null || marker.getDimensionId() != dimension) {
                    continue;
                }

                boolean pinned = false;
                boolean activeQuestMarker = activeQuestMarkers.containsKey(marker.getId());
                if (!activeQuestMarker) {
                    continue;
                }

                double dxPlayer = player.posX - marker.getX();
                double markerY = marker.getEffectiveY(
                        minecraft.theWorld, player.posY);
                double dyPlayer = player.posY - markerY;
                double dzPlayer = player.posZ - marker.getZ();
                double distSq = dxPlayer * dxPlayer + dyPlayer * dyPlayer + dzPlayer * dzPlayer;
                if (distSq > maxDistanceSq && !activeQuestMarker) {
                    continue;
                }
                if (distSq > maxDistanceSq * 4.0D) {
                    continue;
                }

                String label = activeQuestMarker ? activeQuestMarkers.get(marker.getId()) : marker.getName();
                renderMarkerLabel(minecraft.fontRenderer, label, marker.getX(), markerY, marker.getZ(), pinned, activeQuestMarker, Math.sqrt(distSq), maxDistance, cameraX, cameraY, cameraZ);
            }

            for (LostTalesClientQuestMarkerHelper.ActiveCoordinateMarker marker : activeCoordinateMarkers) {
                if (marker == null || marker.getDimensionId() != dimension) {
                    continue;
                }
                double dxPlayer = player.posX - marker.getX();
                double dyPlayer = player.posY - marker.getY();
                double dzPlayer = player.posZ - marker.getZ();
                double distSq = dxPlayer * dxPlayer + dyPlayer * dyPlayer + dzPlayer * dzPlayer;
                if (distSq > maxDistanceSq * 4.0D) {
                    continue;
                }
                renderMarkerLabel(minecraft.fontRenderer, marker.getLabel(), marker.getX(), marker.getY(), marker.getZ(), false, true, Math.sqrt(distSq), maxDistance, cameraX, cameraY, cameraZ);
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
        }
    }

    private static boolean shouldRender(Minecraft minecraft) {
        return LostTalesConfig.showLostTalesHud
                && LostTalesConfig.showQuestHud
                && LostTalesConfig.showWorldQuestMarkers
                && minecraft != null
                && minecraft.thePlayer != null
                && minecraft.theWorld != null
                && minecraft.fontRenderer != null
                && minecraft.gameSettings != null
                && !minecraft.gameSettings.hideGUI;
    }

    private static void renderMarkerLabel(FontRenderer fontRenderer, String markerName, double markerX, double markerY, double markerZ, boolean pinned, boolean activeQuestMarker, double distance, double maxDistance, double cameraX, double cameraY, double cameraZ) {
        double renderX = markerX + 0.5D - cameraX;
        double renderY = markerY + LABEL_Y_OFFSET - cameraY;
        double renderZ = markerZ + 0.5D - cameraZ;

        float alpha = distance <= maxDistance * 0.65D || activeQuestMarker
                ? 1.0F
                : 1.0F - MathHelper.clamp_float((float) ((distance - maxDistance * 0.65D) / (maxDistance * 0.35D)), 0.0F, 0.75F);
        int alphaByte = MathHelper.clamp_int((int) (alpha * 255.0F), 48, 255);
        int textColor = (alphaByte << 24) | (pinned ? 0xFFD37A : activeQuestMarker ? 0xFFFBDE : 0xFFFFFF);
        int shadowColor = (MathHelper.clamp_int((int) (alpha * 160.0F), 32, 160) << 24);
        String safeName = markerName == null || markerName.length() == 0 ? "Quest Marker" : markerName;
        String label = (pinned ? "* " : activeQuestMarker ? "^ " : "^ ") + safeName;
        String distanceLabel = Math.round(distance) + "m";

        RenderManager renderManager = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(renderX, renderY, renderZ);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        int labelWidth = fontRenderer.getStringWidth(label) / 2;
        int distanceWidth = fontRenderer.getStringWidth(distanceLabel) / 2;
        fontRenderer.drawString(label, -labelWidth + 1, 1, shadowColor);
        fontRenderer.drawString(label, -labelWidth, 0, textColor);
        fontRenderer.drawString(distanceLabel, -distanceWidth + 1, 11, shadowColor);
        fontRenderer.drawString(distanceLabel, -distanceWidth, 10, (alphaByte << 24) | 0xAAAAAA);

        GL11.glPopMatrix();
    }
}
