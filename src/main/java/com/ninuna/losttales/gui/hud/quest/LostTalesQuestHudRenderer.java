package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lightweight 1.7.10 quest HUD: tracked quest/objective list plus short sync-derived toasts. */
public final class LostTalesQuestHudRenderer {
    private static final int PANEL_WIDTH = 238;
    private static final int PANEL_PADDING = 6;
    private static final int QUEST_ENTRY_HEIGHT = 28;
    private static final int QUEST_ENTRY_GAP = 5;
    private static final int MAX_DISPLAYED_TRACKED_QUESTS = 4;
    private static final float QUEST_MARKER_VISIBLE_HALF_ANGLE_DEGREES = 42.0F;
    private static final double QUEST_MARKER_NEAR_DISTANCE_SQ = 16.0D;
    private static final int NOTIFICATION_WIDTH = 250;
    private static final int NOTIFICATION_HEIGHT = 18;

    private LostTalesQuestHudRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showLostTalesHud || !LostTalesConfig.showQuestHud || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        LostTalesClientQuestDefinitionStore.ensureLoaded(minecraft.getResourceManager());
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        renderActiveQuestTracker(minecraft, resolution, partialTicks);
        renderNotifications(minecraft, resolution);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void renderActiveQuestTracker(Minecraft minecraft, ScaledResolution resolution, float partialTicks) {
        List<TrackedQuestHudEntry> entries = collectVisibleTrackedQuestEntries(minecraft, partialTicks);
        if (entries.isEmpty()) {
            return;
        }

        int displayed = Math.min(MAX_DISPLAYED_TRACKED_QUESTS, entries.size());
        int overflow = entries.size() - displayed;
        int panelHeight = PANEL_PADDING * 2 + displayed * QUEST_ENTRY_HEIGHT + Math.max(0, displayed - 1) * QUEST_ENTRY_GAP + (overflow > 0 ? 12 : 0);

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int x = screenWidth * LostTalesConfig.questHudOffsetX / 100;
        int y = screenHeight * LostTalesConfig.questHudOffsetY / 100;
        if (x + PANEL_WIDTH > screenWidth) {
            x = screenWidth - PANEL_WIDTH - 4;
        }
        if (y + panelHeight > screenHeight) {
            y = screenHeight - panelHeight - 4;
        }
        x = Math.max(4, x);
        y = Math.max(4, y);

        FontRenderer font = minecraft.fontRenderer;
        int lineY = y + PANEL_PADDING;
        for (int i = 0; i < displayed; i++) {
            TrackedQuestHudEntry entry = entries.get(i);
            drawQuestEntry(font, entry, x, lineY, PANEL_WIDTH);
            lineY += QUEST_ENTRY_HEIGHT + QUEST_ENTRY_GAP;
        }

        if (overflow > 0) {
            String more = "+" + overflow;
            font.drawStringWithShadow(more, x + PANEL_WIDTH - PANEL_PADDING - font.getStringWidth(more), lineY - 1, 0xCCCCCC);
        }
    }

    private static void drawQuestEntry(FontRenderer font, TrackedQuestHudEntry entry, int x, int y, int width) {
        int left = x + PANEL_PADDING;
        int right = x + width - PANEL_PADDING;
        int titleY = y + 1;
        int objectiveY = y + 15;
        String title = trimToWidth(font, entry.title, right - left - 28);
        String objective = trimToWidth(font, "\u25C7 " + entry.objectiveText, right - left - 12);

        Gui.drawRect(left + 17, titleY + 5, right - font.getStringWidth(title) - 10, titleY + 6, 0x88FFFFFF);
        Gui.drawRect(right - 8, titleY + 5, right, titleY + 6, 0x88FFFFFF);
        drawSmallDiamond(left + 5, titleY + 5, 0xFFFBDE);
        font.drawStringWithShadow(title, right - font.getStringWidth(title), titleY, 0xFFFBDE);
        font.drawStringWithShadow(objective, left + 4, objectiveY, entry.complete ? 0x77DD77 : 0xDDDDDD);
    }

    private static void drawSmallDiamond(int centerX, int centerY, int color) {
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        Gui.drawRect(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color);
    }

    private static List<TrackedQuestHudEntry> collectVisibleTrackedQuestEntries(Minecraft minecraft, float partialTicks) {
        List<TrackedQuestHudEntry> entries = new ArrayList<TrackedQuestHudEntry>();
        Collection<LostTalesQuestProgress> tracked = LostTalesClientQuestProgressStore.getPinnedQuests();
        if (tracked.isEmpty()) {
            return entries;
        }

        for (LostTalesQuestProgress progress : tracked) {
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }
            LostTalesQuestStageDefinition stage = findStage(quest, progress);
            if (!shouldShowQuestForCurrentView(minecraft, quest, stage, partialTicks)) {
                continue;
            }
            LostTalesQuestObjectiveDefinition objective = findPrimaryObjective(progress, stage);
            String objectiveText = objective == null
                    ? (stage == null ? "No active stage" : "No objectives")
                    : LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, true, false, false, false);
            boolean complete = objective != null && progress.getObjectiveProgress(objective.getId()) >= LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            entries.add(new TrackedQuestHudEntry(quest.getTitle(), objectiveText, complete));
        }
        return entries;
    }

    private static boolean shouldShowQuestForCurrentView(Minecraft minecraft, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, float partialTicks) {
        List<HudQuestTarget> targets = collectCurrentStageTargets(minecraft, quest, stage);
        if (targets.isEmpty()) {
            return true;
        }

        EntityPlayer player = minecraft.thePlayer;
        LostTalesCompassHudRenderHelper.PlayerPos playerPos = LostTalesCompassHudRenderHelper.lerpPlayerPos(player, partialTicks);
        float viewYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float normalizedYaw = LostTalesCompassHudRenderHelper.normalizeViewYaw(viewYaw);

        for (HudQuestTarget target : targets) {
            double dx = target.x - playerPos.x;
            double dz = target.z - playerPos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq <= QUEST_MARKER_NEAR_DISTANCE_SQ) {
                return true;
            }
            float targetDeg = LostTalesCompassHudRenderHelper.angleDegToTarget(dx, dz);
            float delta = Math.abs(LostTalesCompassHudRenderHelper.shortestDeltaDegrees(targetDeg, normalizedYaw));
            if (delta <= QUEST_MARKER_VISIBLE_HALF_ANGLE_DEGREES) {
                return true;
            }
        }
        return false;
    }

    private static List<HudQuestTarget> collectCurrentStageTargets(Minecraft minecraft, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage) {
        List<HudQuestTarget> targets = new ArrayList<HudQuestTarget>();
        if (minecraft == null || minecraft.theWorld == null || stage == null) {
            return targets;
        }
        int dimension = minecraft.theWorld.provider.dimensionId;
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (objective == null) {
                continue;
            }
            addMarkerTargets(targets, objective, dimension);
            HudQuestTarget coordinateTarget = coordinateTargetFromObjective(quest, objective, dimension);
            if (coordinateTarget != null) {
                targets.add(coordinateTarget);
            }
        }
        return targets;
    }

    private static void addMarkerTargets(List<HudQuestTarget> targets, LostTalesQuestObjectiveDefinition objective, int dimension) {
        String markerId = firstParam(objective, "marker", "mapMarker", "map_marker", "targetMarker", "target_marker");
        if (markerId == null || markerId.length() == 0) {
            return;
        }
        String[] split = markerId.split(",");
        for (String part : split) {
            String normalized = LostTalesQuestMarkerHelper.normalizeMarkerId(part);
            if (normalized.length() == 0) {
                continue;
            }
            LostTalesMapMarkerData marker = LostTalesClientMapMarkerStore.getSharedMarker(normalized);
            if (marker != null && marker.getDimensionId() == dimension) {
                targets.add(new HudQuestTarget(marker.getX(), marker.getY(), marker.getZ()));
            }
        }
    }

    private static HudQuestTarget coordinateTargetFromObjective(LostTalesQuestDefinition quest, LostTalesQuestObjectiveDefinition objective, int currentDimension) {
        if (!isGotoObjective(objective)) {
            return null;
        }
        String xValue = firstParam(objective, "x", "posX", "targetX");
        String yValue = firstParam(objective, "y", "posY", "targetY");
        String zValue = firstParam(objective, "z", "posZ", "targetZ");
        if (xValue == null || zValue == null || xValue.length() == 0 || zValue.length() == 0) {
            return null;
        }
        int dimension = parseDimensionId(firstParam(objective, "dimension", "dim", "world"));
        if (dimension != currentDimension) {
            return null;
        }
        Double x = parseDouble(xValue);
        Double y = parseDouble(yValue == null || yValue.length() == 0 ? "64" : yValue);
        Double z = parseDouble(zValue);
        if (x == null || y == null || z == null) {
            return null;
        }
        return new HudQuestTarget(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private static LostTalesQuestObjectiveDefinition findPrimaryObjective(LostTalesQuestProgress progress, LostTalesQuestStageDefinition stage) {
        if (stage == null || stage.getObjectives().isEmpty()) {
            return null;
        }
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (objective == null) {
                continue;
            }
            int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            int current = progress == null ? 0 : progress.getObjectiveProgress(objective.getId());
            if (current < target) {
                return objective;
            }
        }
        return stage.getObjectives().get(0);
    }

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        String type = objective == null ? null : objective.getType();
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "goto".equals(normalized) || "go_to".equals(normalized) || "travel".equals(normalized) || "location".equals(normalized);
    }

    private static String firstParam(LostTalesQuestObjectiveDefinition objective, String... keys) {
        if (objective == null || keys == null) {
            return "";
        }
        Map<String, String> params = objective.getParams();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String value = params.get(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseDimensionId(String value) {
        if (value == null || value.trim().length() == 0) {
            return 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("minecraft:overworld".equals(normalized) || "overworld".equals(normalized) || "world".equals(normalized)) {
            return 0;
        }
        if ("minecraft:the_nether".equals(normalized) || "minecraft:nether".equals(normalized) || "the_nether".equals(normalized) || "nether".equals(normalized)) {
            return -1;
        }
        if ("minecraft:the_end".equals(normalized) || "minecraft:end".equals(normalized) || "the_end".equals(normalized) || "end".equals(normalized)) {
            return 1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void renderNotifications(Minecraft minecraft, ScaledResolution resolution) {
        List<LostTalesClientQuestNotificationStore.Notification> notifications = LostTalesClientQuestNotificationStore.getVisibleNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        int screenWidth = resolution.getScaledWidth();
        int baseY = resolution.getScaledHeight() - 72 - notifications.size() * (NOTIFICATION_HEIGHT + 4);
        int x = (screenWidth - NOTIFICATION_WIDTH) / 2;
        long now = System.currentTimeMillis();

        for (int i = 0; i < notifications.size(); i++) {
            LostTalesClientQuestNotificationStore.Notification notification = notifications.get(i);
            float alpha = notification.getAlpha(now);
            if (alpha <= 0.0F) {
                continue;
            }

            int a = MathHelper.clamp_int((int) (alpha * 210.0F), 0, 210);
            int y = baseY + i * (NOTIFICATION_HEIGHT + 4);
            int background = (a << 24);
            int border = (MathHelper.clamp_int((int) (alpha * 170.0F), 0, 170) << 24) | notification.getType().getColor();
            int textColor = (MathHelper.clamp_int((int) (alpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;
            int accentColor = (MathHelper.clamp_int((int) (alpha * 255.0F), 0, 255) << 24) | notification.getType().getColor();

            Gui.drawRect(x, y, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, background);
            Gui.drawRect(x, y, x + 3, y + NOTIFICATION_HEIGHT, border);
            font.drawStringWithShadow(trimToWidth(font, notification.getMessage(), NOTIFICATION_WIDTH - 12), x + 8, y + 5, textColor);
            Gui.drawRect(x, y + NOTIFICATION_HEIGHT - 1, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, accentColor);
        }
    }

    private static LostTalesQuestStageDefinition findStage(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        if (quest == null || progress == null || quest.getStages().isEmpty()) {
            return null;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage.getId() != null && stage.getId().equals(progress.getStageId())) {
                return stage;
            }
        }
        int index = progress.getStageIndex();
        return index >= 0 && index < quest.getStages().size() ? quest.getStages().get(index) : quest.getFirstStage();
    }

    private static String trimToWidth(FontRenderer font, String text, int width) {
        if (text == null || width <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.getStringWidth(ellipsis);
        String trimmed = text;
        while (trimmed.length() > 0 && font.getStringWidth(trimmed) + ellipsisWidth > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private static final class TrackedQuestHudEntry {
        private final String title;
        private final String objectiveText;
        private final boolean complete;

        private TrackedQuestHudEntry(String title, String objectiveText, boolean complete) {
            this.title = title == null || title.length() == 0 ? "Tracked Quest" : title;
            this.objectiveText = objectiveText == null || objectiveText.length() == 0 ? "No objective" : objectiveText;
            this.complete = complete;
        }
    }

    private static final class HudQuestTarget {
        private final double x;
        private final double y;
        private final double z;

        private HudQuestTarget(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
