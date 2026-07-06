package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Lightweight 1.7.10 quest HUD: active quest tracker plus short sync-derived toasts. */
public final class LostTalesQuestHudRenderer {
    private static final int PANEL_WIDTH = 216;
    private static final int PANEL_PADDING = 6;
    private static final int OBJECTIVE_LINE_HEIGHT = 10;
    private static final int NOTIFICATION_WIDTH = 250;
    private static final int NOTIFICATION_HEIGHT = 18;

    private LostTalesQuestHudRenderer() {}

    public static void render(Minecraft minecraft) {
        if (!LostTalesConfig.showLostTalesHud || !LostTalesConfig.showQuestHud || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        LostTalesClientQuestDefinitionStore.ensureLoaded(minecraft.getResourceManager());
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        renderActiveQuestTracker(minecraft, resolution);
        renderNotifications(minecraft, resolution);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void renderActiveQuestTracker(Minecraft minecraft, ScaledResolution resolution) {
        LostTalesQuestProgress progress = pickTrackedQuest();
        if (progress == null) {
            return;
        }

        LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
        if (quest == null) {
            return;
        }

        LostTalesQuestStageDefinition stage = findStage(quest, progress);
        List<LostTalesQuestObjectiveDefinition> objectives = stage == null ? new ArrayList<LostTalesQuestObjectiveDefinition>() : stage.getObjectives();
        int maxObjectives = MathHelper.clamp_int(LostTalesConfig.questHudMaxObjectives, 1, 6);
        int objectiveRows = Math.min(maxObjectives, objectives.size());
        int panelHeight = PANEL_PADDING * 2 + 22 + objectiveRows * OBJECTIVE_LINE_HEIGHT + (objectives.size() > objectiveRows ? OBJECTIVE_LINE_HEIGHT : 0);

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
        Gui.drawRect(x, y, x + PANEL_WIDTH, y + panelHeight, 0xAA000000);
        Gui.drawRect(x, y, x + PANEL_WIDTH, y + 1, 0x66FFFFFF);
        Gui.drawRect(x, y + panelHeight - 1, x + PANEL_WIDTH, y + panelHeight, 0x66000000);

        int lineY = y + PANEL_PADDING;
        font.drawStringWithShadow(trimToWidth(font, quest.getTitle(), PANEL_WIDTH - PANEL_PADDING * 2), x + PANEL_PADDING, lineY, 0xFFD37A);
        lineY += 11;
        String stageLabel = stage == null ? "No active stage" : "Stage: " + readableStageName(progress, stage);
        font.drawStringWithShadow(trimToWidth(font, stageLabel, PANEL_WIDTH - PANEL_PADDING * 2), x + PANEL_PADDING, lineY, 0xAADDFF);
        lineY += 11;

        if (objectives.isEmpty()) {
            font.drawStringWithShadow("No objectives", x + PANEL_PADDING, lineY, 0xAAAAAA);
            return;
        }

        for (int i = 0; i < objectiveRows; i++) {
            LostTalesQuestObjectiveDefinition objective = objectives.get(i);
            int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            int current = Math.min(target, progress.getObjectiveProgress(objective.getId()));
            boolean done = current >= target;
            int color = done ? 0x77DD77 : 0xDDDDDD;
            String text = LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, true, false, false, true);
            text = (done ? "[x] " : "[ ] ") + text;
            font.drawStringWithShadow(trimToWidth(font, text, PANEL_WIDTH - PANEL_PADDING * 2), x + PANEL_PADDING, lineY, color);
            lineY += OBJECTIVE_LINE_HEIGHT;
        }

        if (objectives.size() > objectiveRows) {
            font.drawStringWithShadow("+" + (objectives.size() - objectiveRows) + " more objective(s)", x + PANEL_PADDING, lineY, 0x888888);
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

    private static LostTalesQuestProgress pickTrackedQuest() {
        LostTalesQuestProgress pinned = LostTalesClientQuestProgressStore.getPinnedQuest();
        if (pinned != null) {
            return pinned;
        }

        Collection<LostTalesQuestProgress> activeQuests = LostTalesClientQuestProgressStore.getActiveQuests();
        for (LostTalesQuestProgress progress : activeQuests) {
            return progress;
        }
        return null;
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

    private static String readableStageName(LostTalesQuestProgress progress, LostTalesQuestStageDefinition stage) {
        if (stage != null && stage.getId() != null && stage.getId().length() > 0) {
            return stage.getId();
        }
        if (progress.getStageId() != null && progress.getStageId().length() > 0) {
            return progress.getStageId();
        }
        return String.valueOf(progress.getStageIndex());
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
}
