package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.camera.ThirdPersonCameraController;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
/** Shared Lost Tales and LOTR quest tracker plus sync-derived notifications. */
public final class LostTalesQuestHudRenderer {
    private static final int PANEL_WIDTH = 238;
    private static final int PANEL_PADDING = 6;
    private static final int QUEST_ENTRY_BASE_HEIGHT = 18;
    private static final int QUEST_ENTRY_LINE_HEIGHT = 10;
    private static final int QUEST_ENTRY_PROGRESS_HEIGHT = 5;
    private static final int QUEST_ENTRY_GAP = 5;
    private static final float QUEST_MARKER_VISIBLE_HALF_ANGLE_DEGREES = 42.0F;
    private static final double QUEST_MARKER_NEAR_DISTANCE_SQ = 16.0D;
    private static final int NOTIFICATION_WIDTH = 272;
    private static final int NOTIFICATION_HEIGHT = 34;
    private static final int MAX_VISIBLE_NOTIFICATIONS = 4;

    private LostTalesQuestHudRenderer() {}

    public static int getTrackerPlacementWidth() {
        return PANEL_WIDTH;
    }

    public static int getTrackerPlacementHeight() {
        int entries = getConfiguredTrackedQuestCount();
        int entryHeight = QUEST_ENTRY_BASE_HEIGHT
                + getConfiguredObjectiveLineCount() * QUEST_ENTRY_LINE_HEIGHT
                + QUEST_ENTRY_PROGRESS_HEIGHT;
        return PANEL_PADDING * 2 + 12
                + entries * entryHeight
                + Math.max(0, entries - 1) * QUEST_ENTRY_GAP;
    }

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

        int displayed = Math.min(getConfiguredTrackedQuestCount(), entries.size());
        int overflow = entries.size() - displayed;
        int panelHeight = PANEL_PADDING * 2 + (overflow > 0 ? 12 : 0);
        for (int i = 0; i < displayed; i++) {
            panelHeight += getEntryHeight(entries.get(i));
            if (i + 1 < displayed) {
                panelHeight += QUEST_ENTRY_GAP;
            }
        }

        HudPlacementLayout.Bounds placement = HudPlacementLayout.calculate(
                resolution.getScaledWidth(), resolution.getScaledHeight(),
                PANEL_WIDTH, panelHeight,
                LostTalesConfig.questHudOffsetX,
                LostTalesConfig.questHudOffsetY,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT);
        int x = placement.x;
        int y = placement.y;

        FontRenderer font = minecraft.fontRenderer;
        int lineY = y + PANEL_PADDING;
        for (int i = 0; i < displayed; i++) {
            TrackedQuestHudEntry entry = entries.get(i);
            int entryHeight = getEntryHeight(entry);
            drawQuestEntry(font, entry, x, lineY, PANEL_WIDTH, entryHeight);
            lineY += entryHeight + QUEST_ENTRY_GAP;
        }

        if (overflow > 0) {
            String more = StatCollector.translateToLocalFormatted(
                    "gui.losttales.quest.hud.more", String.valueOf(overflow));
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(more,
                    x + PANEL_WIDTH - PANEL_PADDING - font.getStringWidth(more),
                    lineY - 1,
                    LostTalesColors.rgb(LostTalesColors.TEXT_MUTED));
        }
    }

    private static int getEntryHeight(TrackedQuestHudEntry entry) {
        int lines = Math.max(1, Math.min(getConfiguredObjectiveLineCount(), entry.objectiveLines.size()));
        return QUEST_ENTRY_BASE_HEIGHT + lines * QUEST_ENTRY_LINE_HEIGHT + (entry.target > 1 ? QUEST_ENTRY_PROGRESS_HEIGHT : 0);
    }

    private static int getConfiguredTrackedQuestCount() {
        return Math.max(1, Math.min(8, LostTalesConfig.questHudMaxTrackedQuests));
    }

    private static int getConfiguredObjectiveLineCount() {
        return Math.max(1, Math.min(3, LostTalesConfig.questHudObjectiveLineCount));
    }

    private static void drawQuestEntry(FontRenderer font, TrackedQuestHudEntry entry, int x, int y, int width, int height) {
        int left = x + PANEL_PADDING;
        int right = x + width - PANEL_PADDING;
        int titleY = y + 1;
        String title = trimToWidth(font, entry.title, right - left - 28);
        int titleRgb = LostTalesColors.rgb(entry.complete
                ? LostTalesColors.GREEN : LostTalesColors.TEXT_BRIGHT);

        // The rule runs from the mark to the title and closes past it,
        // the way a section's rule does in the journal.
        int ruleColor = LostTalesColors.withAlpha(LostTalesColors.SAND, 0x88);
        Gui.drawRect(left + 17, titleY + 5,
                Math.max(left + 17, right - font.getStringWidth(title) - 10),
                titleY + 6, ruleColor);
        Gui.drawRect(right - 8, titleY + 5, right, titleY + 6, ruleColor);
        drawSmallDiamond(left + 5, titleY + 5, titleRgb);
        LostTalesSkyrimUiStyle.beginContent();
        font.drawStringWithShadow(title, right - font.getStringWidth(title),
                titleY, titleRgb);

        int objectiveY = y + 15;
        int maxLines = Math.max(1, Math.min(getConfiguredObjectiveLineCount(), entry.objectiveLines.size()));
        for (int i = 0; i < maxLines; i++) {
            String line = trimToWidth(font, entry.objectiveLines.get(i), right - left - 10);
            font.drawStringWithShadow(line, left + 4,
                    objectiveY + i * QUEST_ENTRY_LINE_HEIGHT,
                    LostTalesColors.rgb(entry.complete
                            ? LostTalesColors.GREEN : LostTalesColors.TEXT));
        }

        if (entry.target > 1) {
            int barY = y + height - 4;
            int barWidth = right - left - 8;
            int filled = MathHelper.clamp_int(barWidth * Math.min(entry.current, entry.target) / Math.max(1, entry.target), 0, barWidth);
            Gui.drawRect(left + 4, barY, left + 4 + barWidth, barY + 2,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x66));
            if (filled > 0) {
                Gui.drawRect(left + 4, barY, left + 4 + filled, barY + 2,
                        LostTalesColors.withAlpha(entry.complete
                                ? LostTalesColors.GREEN
                                : LostTalesColors.GOLD, 0xAA));
            }
            LostTalesSkyrimUiStyle.beginContent();
        }
    }

    private static void drawSmallDiamond(int centerX, int centerY, int color) {
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        Gui.drawRect(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color);
        LostTalesSkyrimUiStyle.beginContent();
    }

    private static List<TrackedQuestHudEntry> collectVisibleTrackedQuestEntries(Minecraft minecraft, float partialTicks) {
        List<TrackedQuestHudEntry> entries = new ArrayList<TrackedQuestHudEntry>();
        for (ClientQuestEntry entry : ClientQuestCatalog.getEntries(minecraft)) {
            if (!entry.isActive() || !entry.isTracked()) {
                continue;
            }
            LostTalesQuestDefinition quest = entry.getLostTalesDefinition();
            LostTalesQuestProgress progress = entry.getLostTalesProgress();
            if (quest != null
                    ? !shouldShowQuestForCurrentView(minecraft, quest,
                            progress, partialTicks)
                    : requiresReturnFacing(entry)
                            && !shouldShowQuestForCurrentView(minecraft,
                                    entry.getTargets(), partialTicks)) {
                continue;
            }
            ArrayList<String> objectiveLines = new ArrayList<String>();
            int current = 0;
            int target = 0;
            boolean complete = !entry.getObjectives().isEmpty();
            for (ClientQuestEntry.Objective objective : entry.getObjectives()) {
                String line = (objective.isComplete() ? "✓ " : "◇ ")
                        + objective.getText();
                objectiveLines.add(line);
                current += Math.min(objective.getCurrent(), objective.getTarget());
                target += objective.getTarget();
                complete &= objective.isComplete();
            }
            if (objectiveLines.isEmpty()) {
                objectiveLines.add("\u25c7 " + StatCollector.translateToLocal(
                        "gui.losttales.quest.objective.none"));
                target = 1;
                complete = false;
            }
            if (entry.hasDeadline()) {
                objectiveLines.set(0, objectiveLines.get(0) + " \u00b7 "
                        + StatCollector.translateToLocalFormatted(
                                "gui.losttales.quest.deadline",
                                entry.getRemainingTicks() > 0L
                                        ? LostTalesQuestTimeText.shortForm(
                                                entry.getRemainingTicks())
                                        : StatCollector.translateToLocal(
                                                "gui.losttales.quest.expired")));
            }
            entries.add(new TrackedQuestHudEntry(entry.getTitle(),
                    objectiveLines, current, Math.max(1, target), complete,
                    minecraft.fontRenderer));
        }
        return entries;
    }

    /** LOTR's stored target is its giver; it gates the HUD only at turn-in. */
    private static boolean requiresReturnFacing(ClientQuestEntry entry) {
        if (entry == null || entry.getObjectives().isEmpty()) {
            return false;
        }
        boolean required = false;
        for (ClientQuestEntry.Objective objective : entry.getObjectives()) {
            if (objective.isOptional()) continue;
            required = true;
            if (!objective.isComplete()) return false;
        }
        return required;
    }

    private static boolean shouldShowQuestForCurrentView(Minecraft minecraft,
            List<ClientQuestEntry.Target> targets, float partialTicks) {
        if (targets == null || targets.isEmpty() || minecraft == null
                || minecraft.theWorld == null) {
            return true;
        }
        int dimension = minecraft.theWorld.provider.dimensionId;
        EntityPlayer player = minecraft.thePlayer;
        LostTalesCompassHudRenderHelper.PlayerPos playerPos =
                LostTalesCompassHudRenderHelper.lerpPlayerPos(player,
                        partialTicks);
        float viewYaw = ThirdPersonCameraController.resolveViewYaw(
                player.prevRotationYaw, player.rotationYaw, partialTicks);
        float normalizedYaw = LostTalesCompassHudRenderHelper
                .normalizeViewYaw(viewYaw);
        boolean hasCurrentDimensionTarget = false;
        for (ClientQuestEntry.Target target : targets) {
            if (target.getDimensionId() != dimension) {
                continue;
            }
            hasCurrentDimensionTarget = true;
            double dx = target.getX() - playerPos.x;
            double dz = target.getZ() - playerPos.z;
            if (dx * dx + dz * dz <= QUEST_MARKER_NEAR_DISTANCE_SQ) {
                return true;
            }
            float targetDeg = LostTalesCompassHudRenderHelper
                    .angleDegToTarget(dx, dz);
            float delta = Math.abs(LostTalesCompassHudRenderHelper
                    .shortestDeltaDegrees(targetDeg, normalizedYaw));
            if (delta <= QUEST_MARKER_VISIBLE_HALF_ANGLE_DEGREES) {
                return true;
            }
        }
        return !hasCurrentDimensionTarget;
    }

    private static boolean shouldShowQuestForCurrentView(Minecraft minecraft,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            float partialTicks) {
        List<HudQuestTarget> targets = collectProgressibleTargets(minecraft,
                quest, progress);
        if (targets.isEmpty()) {
            return true;
        }

        EntityPlayer player = minecraft.thePlayer;
        LostTalesCompassHudRenderHelper.PlayerPos playerPos = LostTalesCompassHudRenderHelper.lerpPlayerPos(player, partialTicks);
        float viewYaw = ThirdPersonCameraController.resolveViewYaw(
                player.prevRotationYaw, player.rotationYaw, partialTicks);
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

    private static List<HudQuestTarget> collectProgressibleTargets(
            Minecraft minecraft, LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress) {
        List<HudQuestTarget> targets = new ArrayList<HudQuestTarget>();
        if (minecraft == null || minecraft.theWorld == null || quest == null
                || progress == null) {
            return targets;
        }
        int dimension = minecraft.theWorld.provider.dimensionId;
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, progress)) {
            if (objective == null || LostTalesQuestObjectiveSelection
                    .isComplete(progress, objective)) {
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
                Minecraft minecraft = Minecraft.getMinecraft();
                double fallbackY = minecraft == null
                        || minecraft.thePlayer == null
                        ? 64.0D : minecraft.thePlayer.posY;
                targets.add(new HudQuestTarget(
                        marker.getX(),
                        marker.getEffectiveY(
                                minecraft == null
                                        ? null : minecraft.theWorld,
                                fallbackY),
                        marker.getZ()));
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

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveType.GOTO.is(objective);
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
        if (!LostTalesConfig.showQuestHudNotifications) {
            return;
        }
        List<LostTalesClientQuestNotificationStore.Notification> notifications = LostTalesClientQuestNotificationStore.getVisibleNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        // Banners stack down the shared notification slot, under whatever
        // notice already stands in it this frame.
        int x = LostTalesNotificationHud.centerX(resolution.getScaledWidth(),
                resolution.getScaledHeight()) - NOTIFICATION_WIDTH / 2;
        long now = System.currentTimeMillis();

        for (int i = 0; i < Math.min(notifications.size(), MAX_VISIBLE_NOTIFICATIONS); i++) {
            LostTalesClientQuestNotificationStore.Notification notification = notifications.get(i);
            float alpha = notification.getAlpha(now);
            if (alpha <= 0.0F) {
                continue;
            }

            int a = MathHelper.clamp_int((int) (alpha * 210.0F), 0, 210);
            int y = LostTalesNotificationHud.claim(resolution.getScaledWidth(),
                    resolution.getScaledHeight(), NOTIFICATION_HEIGHT);
            int background = (a << 24);
            int border = (MathHelper.clamp_int((int) (alpha * 170.0F), 0, 170) << 24) | notification.getType().getColor();
            int textColor = LostTalesColors.withAlpha(LostTalesColors.TEXT_BRIGHT,
                    MathHelper.clamp_int((int)(alpha * 255.0F), 0, 255));
            int accentColor = (MathHelper.clamp_int((int) (alpha * 255.0F), 0, 255) << 24) | notification.getType().getColor();

            Gui.drawRect(x + 1, y + 1, x + NOTIFICATION_WIDTH + 1, y + NOTIFICATION_HEIGHT + 1, MathHelper.clamp_int((int) (alpha * 95.0F), 0, 95) << 24);
            Gui.drawRect(x, y, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, background);
            Gui.drawRect(x, y, x + 3, y + NOTIFICATION_HEIGHT, border);
            Gui.drawRect(x, y + NOTIFICATION_HEIGHT - 1, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, accentColor);
            String title = notification.getType().getDisplayTitle();
            font.drawStringWithShadow(trimToWidth(font, title, NOTIFICATION_WIDTH - 16), x + 8, y + 4, accentColor);
            font.drawStringWithShadow(trimToWidth(font, notification.getMessage(), NOTIFICATION_WIDTH - 16), x + 8, y + 17, textColor);
        }
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
        private final List<String> objectiveLines;
        private final int current;
        private final int target;
        private final boolean complete;

        private TrackedQuestHudEntry(String title, List<String> objectiveText,
                int current, int target, boolean complete, FontRenderer font) {
            this.title = title == null || title.length() == 0
                    ? StatCollector.translateToLocal(
                            "gui.losttales.quest.hud.untitled") : title;
            this.current = Math.max(0, current);
            this.target = Math.max(1, target);
            this.complete = complete;
            List<String> wrapped = new ArrayList<String>();
            if (objectiveText != null) {
                for (String objective : objectiveText) {
                    String safeObjective = objective == null
                            || objective.length() == 0
                            ? "\u25C7 No objective" : objective;
                    List<String> objectiveWrapped = font == null
                            ? Collections.singletonList(safeObjective)
                            : font.listFormattedStringToWidth(safeObjective,
                                    PANEL_WIDTH - PANEL_PADDING * 2 - 10);
                    wrapped.addAll(objectiveWrapped);
                }
            }
            if (wrapped.isEmpty()) {
                wrapped.add("\u25C7 No objective");
            }
            this.objectiveLines = wrapped;
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
