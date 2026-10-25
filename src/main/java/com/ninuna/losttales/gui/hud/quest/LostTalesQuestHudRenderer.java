package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestNotificationStore;
import com.ninuna.losttales.client.quest.QuestMarks;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
/**
 * The quest tracker, Lost Tales and LOTR quests together, and the banners
 * the quest log's changes raise, each shown by its own switch (QUEST-46).
 * Every tracked running quest stands on the tracker, up to its size and
 * then a count of the rest (QUEST-21); where a quest's places lie is the
 * compass's and the world labels' to show.
 */
public final class LostTalesQuestHudRenderer {
    private static final int PANEL_WIDTH = 238;
    private static final int PANEL_PADDING = 6;
    private static final int QUEST_ENTRY_BASE_HEIGHT = 18;
    private static final int QUEST_ENTRY_LINE_HEIGHT = 10;
    private static final int QUEST_ENTRY_PROGRESS_HEIGHT = 5;
    private static final int QUEST_ENTRY_GAP = 5;
    private static final int NOTIFICATION_WIDTH = 272;
    private static final int NOTIFICATION_HEIGHT = 34;
    private static final int MAX_VISIBLE_NOTIFICATIONS = 4;
    /** The tracked quests the tracker's box holds, when as many are tracked. */
    static final int BOX_QUESTS = 2;

    private LostTalesQuestHudRenderer() {}

    public static int getTrackerPlacementWidth() {
        return PANEL_WIDTH;
    }

    /**
     * The tracker's box, which its two numbers place: the tracker as it
     * most often stands, two tracked quests (one when only one may be)
     * of one objective line each. A longer tracker grows down out of it,
     * held inside the screen.
     */
    public static int getTrackerPlacementHeight() {
        return boxHeight(getConfiguredTrackedQuestCount());
    }

    static int boxHeight(int trackedQuests) {
        int quests = Math.max(1, Math.min(BOX_QUESTS, trackedQuests));
        int entryHeight = QUEST_ENTRY_BASE_HEIGHT + QUEST_ENTRY_LINE_HEIGHT;
        return PANEL_PADDING * 2 + quests * entryHeight
                + (quests - 1) * QUEST_ENTRY_GAP;
    }

    /** The tracker and the banners, each where its own switch shows it (QUEST-46). */
    public static void render(Minecraft minecraft, float partialTicks) {
        QuestHudSwitches shown = QuestHudSwitches.current();
        if (!shown.tracker && !shown.banners || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        LostTalesClientQuestDefinitionStore.ensureLoaded(minecraft.getResourceManager());
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (shown.tracker) {
            renderActiveQuestTracker(minecraft, resolution);
        }
        if (shown.banners) {
            renderNotifications(minecraft, resolution);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void renderActiveQuestTracker(Minecraft minecraft, ScaledResolution resolution) {
        List<TrackedQuestHudEntry> entries = collectTrackedQuestEntries(minecraft);
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

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        HudPlacementLayout.Bounds placement = HudPlacementLayout.drawn(
                HudPlacementLayout.calculate(screenWidth, screenHeight,
                        PANEL_WIDTH, getTrackerPlacementHeight(),
                        LostTalesConfig.questHudOffsetX,
                        LostTalesConfig.questHudOffsetY),
                PANEL_WIDTH, panelHeight, screenWidth, screenHeight);
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
            LostTalesUiInk.beginContent();
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
        // A title or a line too long for the panel fades into its end;
        // nothing points at the HUD, so nothing slides (LOOK-37).
        String title = entry.title == null ? "" : entry.title;
        int titleRoom = Math.max(0, right - left - 28);
        int titleWidth = (int)LostTalesUiCutName.shownWidth(
                font.getStringWidth(title), titleRoom);
        int titleRgb = LostTalesColors.rgb(entry.complete
                ? LostTalesColors.GREEN : LostTalesColors.TEXT_BRIGHT);

        // The rule runs from the mark to the title and closes past it,
        // the way a section's rule does in the journal.
        int ruleColor = LostTalesColors.withAlpha(LostTalesColors.SAND, 0x88);
        Gui.drawRect(left + 17, titleY + 5,
                Math.max(left + 17, right - titleWidth - 10),
                titleY + 6, ruleColor);
        Gui.drawRect(right - 8, titleY + 5, right, titleY + 6, ruleColor);
        drawSmallDiamond(left + 5, titleY + 5, titleRgb);
        LostTalesUiInk.beginContent();
        LostTalesUiCutName.drawShadowed(font, title, right - titleWidth,
                titleY, titleRoom, titleRgb);

        int objectiveY = y + 15;
        int maxLines = Math.max(1, Math.min(getConfiguredObjectiveLineCount(), entry.objectiveLines.size()));
        for (int i = 0; i < maxLines; i++) {
            String line = entry.objectiveLines.get(i);
            LostTalesUiCutName.drawShadowed(font, line == null ? "" : line,
                    left + 4, objectiveY + i * QUEST_ENTRY_LINE_HEIGHT,
                    Math.max(0, right - left - 10),
                    LostTalesColors.rgb(entry.complete
                            ? LostTalesColors.GREEN : LostTalesColors.TEXT));
        }

        if (entry.target > 1) {
            int barY = y + height - 4;
            int barWidth = right - left - 8;
            int filled = MathHelper.clamp_int(barWidth * Math.min(entry.current, entry.target) / Math.max(1, entry.target), 0, barWidth);
            Gui.drawRect(left + 4, barY, left + 4 + barWidth, barY + 2,
                    LostTalesColors.withAlpha(LostTalesUiTheme.primaryRgb(), 0x66));
            if (filled > 0) {
                Gui.drawRect(left + 4, barY, left + 4 + filled, barY + 2,
                        LostTalesColors.withAlpha(entry.complete
                                ? LostTalesColors.GREEN
                                : LostTalesUiTheme.accentRgb(), 0xAA));
            }
            LostTalesUiInk.beginContent();
        }
    }

    private static void drawSmallDiamond(int centerX, int centerY, int color) {
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        Gui.drawRect(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color);
        LostTalesUiInk.beginContent();
    }

    private static List<TrackedQuestHudEntry> collectTrackedQuestEntries(Minecraft minecraft) {
        List<TrackedQuestHudEntry> entries = new ArrayList<TrackedQuestHudEntry>();
        for (ClientQuestEntry entry : ClientQuestCatalog.getEntries(minecraft)) {
            if (!entry.isActive() || !entry.isTracked()) {
                continue;
            }
            ArrayList<String> objectiveLines = new ArrayList<String>();
            int current = 0;
            int target = 0;
            boolean complete = !entry.getObjectives().isEmpty();
            for (ClientQuestEntry.Objective objective : entry.getObjectives()) {
                String line = (objective.isComplete() ? QuestMarks.DONE
                        : QuestMarks.OPEN) + " " + objective.getText();
                objectiveLines.add(line);
                current += Math.min(objective.getCurrent(), objective.getTarget());
                target += objective.getTarget();
                complete &= objective.isComplete();
            }
            if (objectiveLines.isEmpty()) {
                objectiveLines.add(noObjective());
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

    private static void renderNotifications(Minecraft minecraft, ScaledResolution resolution) {
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
            int accent = notification.getType().getColor();
            int background = LostTalesColors.withAlpha(LostTalesUiTheme.primaryRgb(), a);
            int border = LostTalesColors.withAlpha(accent,
                    MathHelper.clamp_int((int) (alpha * 170.0F), 0, 170));
            int textColor = LostTalesColors.withAlpha(LostTalesColors.TEXT_BRIGHT,
                    MathHelper.clamp_int((int)(alpha * 255.0F), 0, 255));
            int accentColor = LostTalesColors.withAlpha(accent,
                    MathHelper.clamp_int((int) (alpha * 255.0F), 0, 255));

            Gui.drawRect(x + 1, y + 1, x + NOTIFICATION_WIDTH + 1, y + NOTIFICATION_HEIGHT + 1,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK,
                            MathHelper.clamp_int((int) (alpha * 95.0F), 0, 95)));
            Gui.drawRect(x, y, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, background);
            Gui.drawRect(x, y, x + 3, y + NOTIFICATION_HEIGHT, border);
            Gui.drawRect(x, y + NOTIFICATION_HEIGHT - 1, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, accentColor);
            String title = notification.getType().getDisplayTitle();
            LostTalesUiInk.beginContent();
            // Words too long for the notice fade into its end (LOOK-37).
            String message = notification.getMessage();
            LostTalesUiCutName.drawShadowed(font, title == null ? "" : title,
                    x + 8, y + 4, NOTIFICATION_WIDTH - 16, accentColor);
            LostTalesUiCutName.drawShadowed(font,
                    message == null ? "" : message, x + 8, y + 17,
                    NOTIFICATION_WIDTH - 16, textColor);
        }
    }

    /** The tracker's line for a quest with nothing to do right now. */
    private static String noObjective() {
        return QuestMarks.OPEN + " " + StatCollector.translateToLocal(
                "gui.losttales.quest.objective.none");
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
                            ? noObjective() : objective;
                    List<String> objectiveWrapped = font == null
                            ? Collections.singletonList(safeObjective)
                            : font.listFormattedStringToWidth(safeObjective,
                                    PANEL_WIDTH - PANEL_PADDING * 2 - 10);
                    wrapped.addAll(objectiveWrapped);
                }
            }
            if (wrapped.isEmpty()) {
                wrapped.add(noObjective());
            }
            this.objectiveLines = wrapped;
        }
    }
}
