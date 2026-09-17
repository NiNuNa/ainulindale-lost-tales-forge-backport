package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.gui.LostTalesGuiPointerTargets;
import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar.Hint;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
/**
 * Skyrim-inspired quest journal layout for 1.7.10.
 *
 * The screen stays client-only and reads the shared presentation assembled from
 * Lost Tales and LOTR's synchronized quest state.
 */
public class LostTalesQuestJournalGui extends GuiScreen
        implements LostTalesPointerInteractable {
    private static final int OUTER_PADDING = 16;
    private static final int TOP_BAR_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = LostTalesControlBar.HEIGHT;
    private static final int LEFT_WIDTH = 270;
    private static final int LIST_ROW_HEIGHT = 24;
    private static final int CATEGORY_ROW_HEIGHT = 24;
    private static final int DETAIL_LINE_HEIGHT = 10;
    private static final long DOUBLE_CLICK_TRACK_MS = 350L;

    private static final int QUEST_BUTTON_WIDTH = 175;
    private static final int QUEST_BUTTON_HEIGHT = 18;
    private static final int CATEGORY_HEADER_WIDTH = 192;
    private static final int CATEGORY_HEADER_HEIGHT = 24;
    private static final int ACTIVE_ICON_WIDTH = 11;
    private static final int ACTIVE_ICON_HEIGHT = 10;

    private static final ResourceLocation QUEST_BUTTON_TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/quest/button_quest.png");
    private static final ResourceLocation CATEGORY_HEADER_TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/quest/header_quest.png");
    private static final ResourceLocation ACTIVE_QUEST_ICON_TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/quest/active_quest_icon.png");

    private final GuiScreen parent;
    private final Set<String> collapsedCategories = new HashSet<String>();
    private int selectedQuestIndex;
    private int listScroll;
    private int detailScroll;
    private QuestFilter filter = QuestFilter.ALL;
    private int lastClickedQuestIndex = -1;
    private long lastQuestClickMs;

    public LostTalesQuestJournalGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        LostTalesClientQuestDefinitionStore.ensureLoaded(this.mc.getResourceManager());
        this.buttonList.clear();
        clampSelectionAndScroll();
        ensureSelectedQuestVisible();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        clampSelectionAndScroll();

        drawWorldDimmedBackground();
        drawTopBar();

        List<ClientQuestEntry> quests = getVisibleQuests();
        JournalLayout layout = getLayout();
        drawVerticalDivider(layout);
        drawQuestList(quests, layout, mouseX, mouseY);
        drawQuestDetails(quests, layout);
        drawFooterHelp();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawWorldDimmedBackground() {
        if (!LostTalesGuiAnimations.isManagingBackdrop(this)) {
            drawGradientRect(0, 0, this.width, this.height,
                    0xD0000000, 0xC8000000);
        }
        drawRect(0, 0, this.width, 1, LostTalesSkyrimUiStyle.BORDER);
        drawRect(0, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + 1, LostTalesSkyrimUiStyle.BORDER);
    }

    private void drawTopBar() {
        String title = "QUEST JOURNAL";
        this.fontRendererObj.drawStringWithShadow(title, OUTER_PADDING, 14, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        LostTalesSkyrimUiStyle.drawDiamond(OUTER_PADDING + this.fontRendererObj.getStringWidth(title) + 14, 18, LostTalesSkyrimUiStyle.TEXT_MUTED);

        String filterInfo = "Filter: " + this.filter.displayName;
        this.fontRendererObj.drawStringWithShadow(filterInfo, OUTER_PADDING, 26, LostTalesSkyrimUiStyle.TEXT_MUTED);

        String worldInfo = getWorldTimeText();
        this.fontRendererObj.drawStringWithShadow(worldInfo, this.width - OUTER_PADDING - this.fontRendererObj.getStringWidth(worldInfo), 14, LostTalesSkyrimUiStyle.TEXT);
    }

    private String getWorldTimeText() {
        World world = this.mc == null ? null : this.mc.theWorld;
        if (world == null) {
            return "Middle-earth";
        }
        long total = world.getWorldTime();
        long day = total / 24000L + 1L;
        long timeOfDay = (total + 6000L) % 24000L;
        int hour = (int)(timeOfDay / 1000L);
        int minute = (int)((timeOfDay % 1000L) * 60L / 1000L);
        String time = (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
        String dimension = "Middle-earth";
        try {
            if (world.provider != null && world.provider.getDimensionName() != null) {
                dimension = world.provider.getDimensionName();
            }
        } catch (Throwable ignored) {
            // Some 1.7.10 dimension providers can be fragile during early GUI init.
        }
        return dimension + ", Day " + day + ", " + time;
    }

    private void drawVerticalDivider(JournalLayout layout) {
        int x = layout.dividerX;
        drawRect(x, layout.contentTop, x + 1, layout.contentBottom, LostTalesSkyrimUiStyle.BORDER);
        drawRect(x + 5, layout.contentTop, x + 6, layout.contentBottom, LostTalesSkyrimUiStyle.BORDER_DIM);
        LostTalesSkyrimUiStyle.drawDiamond(x + 3, layout.contentTop + 54, LostTalesSkyrimUiStyle.GOLD);
        LostTalesSkyrimUiStyle.drawDiamond(x + 3, layout.contentTop + 64, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private void drawQuestList(List<ClientQuestEntry> quests, JournalLayout layout, int mouseX, int mouseY) {
        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = layout.contentBottom - layout.contentTop;
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }

        QuestListRow hoveredRow = rowAt(rows, mouseX, mouseY);
        enableScissor(layout.leftX - 8, layout.contentTop - 2, layout.leftWidth + 20, visibleHeight + 4);
        int rowY = layout.contentTop - this.listScroll;
        for (QuestListRow row : rows) {
            if (rowY + row.height >= layout.contentTop && rowY <= layout.contentBottom) {
                if (row.category) {
                    drawCategoryHeader(row.label, layout.leftX, rowY, layout.leftWidth);
                } else if (row.quest != null) {
                    drawQuestRow(row.quest, row.questIndex, layout.leftX + 14, rowY, layout.leftWidth - 22, row.height, row == hoveredRow);
                }
            }
            rowY += row.height;
        }
        disableScissor();
        drawListBottomFade(layout);

        ClientQuestEntry selected = getSelectedQuest();
        if (selected != null) {
            int selectedY = getQuestRowY(rows, this.selectedQuestIndex, layout.contentTop - this.listScroll);
            if (selectedY >= layout.contentTop && selectedY <= layout.contentBottom - LIST_ROW_HEIGHT) {
                drawSelectorArrow(layout.leftX + layout.leftWidth - 6,
                        selectedY + LIST_ROW_HEIGHT / 2,
                        selected.isTracked());
            }
        }

        if (rows.isEmpty()) {
            String message = LostTalesClientQuestDefinitionStore.getQuests().isEmpty()
                    ? "No quest definitions were loaded from assets/losttales/quests."
                    : "No started quests yet. Collect or accept a quest to add it to the journal.";
            drawWrappedText(message, layout.leftX + 8, layout.contentTop + 10, layout.leftWidth - 16, LostTalesSkyrimUiStyle.TEXT_MUTED, visibleHeight - 20);
        }
    }

    private void drawListBottomFade(JournalLayout layout) {
        int fadeHeight = Math.min(36, Math.max(0, layout.contentBottom - layout.contentTop));
        if (fadeHeight <= 0) {
            return;
        }
        drawGradientRect(layout.leftX - 8, layout.contentBottom - fadeHeight, layout.leftX + layout.leftWidth + 12, layout.contentBottom + 2, 0x00000000, 0xDD000000);
    }

    private void drawCategoryHeader(String label, int x, int y, int width) {
        boolean collapsed = this.collapsedCategories.contains(label);
        int texX = x + Math.max(0, width - CATEGORY_HEADER_WIDTH - 2);
        LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(this.mc, CATEGORY_HEADER_TEXTURE, texX, y, 0, 0, CATEGORY_HEADER_WIDTH, CATEGORY_HEADER_HEIGHT, CATEGORY_HEADER_WIDTH, CATEGORY_HEADER_HEIGHT, 1.0F);
        String collapse = collapsed ? "+ " : "- ";
        String text = collapse + LostTalesSkyrimUiStyle.uppercase(label);
        this.fontRendererObj.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, text, Math.max(40, width - 36)), x + 12, y + 8, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
    }

    private void drawQuestRow(ClientQuestEntry quest, int questIndex, int x, int y, int width, int height, boolean hovered) {
        boolean selected = questIndex == this.selectedQuestIndex;
        boolean active = quest.isActive();
        boolean completed = quest.isCompleted();
        boolean failed = quest.isFailed();
        boolean abandoned = quest.isAbandoned();
        boolean pinned = quest.isTracked();

        if (selected) {
            int texW = Math.min(QUEST_BUTTON_WIDTH, width);
            LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(this.mc, QUEST_BUTTON_TEXTURE, x + width - texW, y + 3, QUEST_BUTTON_WIDTH - texW, 0, texW, QUEST_BUTTON_HEIGHT, QUEST_BUTTON_WIDTH, QUEST_BUTTON_HEIGHT, completed ? 0.46F : 1.0F);
        } else if (hovered) {
            drawRect(x + Math.max(0, width - QUEST_BUTTON_WIDTH), y + 3, x + width, y + height - 3, completed ? 0x24111111 : 0x342B2D31);
        }

        int indicatorX = x + 12;
        int indicatorY = y + height / 2;
        LostTalesSkyrimUiStyle.drawObjectiveIndicator(indicatorX, indicatorY, completed, active);
        if (pinned) {
            drawActiveQuestIcon(x + width - 15, indicatorY - ACTIVE_ICON_HEIGHT / 2, completed ? 0.45F : 1.0F);
        }

        int titleColor = failed ? LostTalesSkyrimUiStyle.RED
                : abandoned ? LostTalesSkyrimUiStyle.GOLD
                : completed ? LostTalesSkyrimUiStyle.TEXT_DIM
                : selected ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                : active ? LostTalesSkyrimUiStyle.TEXT
                : LostTalesSkyrimUiStyle.TEXT_DIM;
        String title = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, quest.getTitle(), width - 48);
        this.fontRendererObj.drawStringWithShadow(title, x + 32, y + 8, titleColor);
    }

    private void drawSelectorArrow(int x, int y, boolean gold) {
        drawActiveQuestIcon(x - ACTIVE_ICON_WIDTH, y - ACTIVE_ICON_HEIGHT / 2, gold ? 1.0F : 0.8F);
    }

    private void drawActiveQuestIcon(int x, int y, float alpha) {
        LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(this.mc, ACTIVE_QUEST_ICON_TEXTURE, x, y, 0, 0, ACTIVE_ICON_WIDTH, ACTIVE_ICON_HEIGHT, ACTIVE_ICON_WIDTH, ACTIVE_ICON_HEIGHT, alpha);
    }

    private void drawQuestDetails(List<ClientQuestEntry> quests, JournalLayout layout) {
        if (quests.isEmpty()) {
            String message = LostTalesClientQuestDefinitionStore.getQuests().isEmpty()
                    ? "The client did not load any quest JSON files. Check quests/index.json and bundled quest files."
                    : "Started quests will appear here after you collect or accept them in the world.";
            drawWrappedText(message, layout.rightX, layout.contentTop + 12, layout.rightWidth, LostTalesSkyrimUiStyle.TEXT_MUTED, layout.contentBottom - layout.contentTop - 24);
            this.detailScroll = 0;
            return;
        }

        ClientQuestEntry quest = quests.get(this.selectedQuestIndex);
        List<DetailLine> lines = buildDetailLines(quest, layout.rightWidth - 20);
        int visibleHeight = layout.contentBottom - layout.contentTop;
        int maxScroll = Math.max(0, lines.size() * DETAIL_LINE_HEIGHT - visibleHeight + 4);
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
        if (this.detailScroll > maxScroll) {
            this.detailScroll = maxScroll;
        }

        enableScissor(layout.rightX - 2, layout.contentTop - 2, layout.rightWidth + 4, visibleHeight + 4);
        int y = layout.contentTop - this.detailScroll;
        for (DetailLine line : lines) {
            if (y + DETAIL_LINE_HEIGHT >= layout.contentTop && y <= layout.contentBottom) {
                if (line.separator) {
                    drawRect(layout.rightX + line.indent, y + 4, layout.rightX + layout.rightWidth, y + 5, line.color);
                } else if (line.objective) {
                    LostTalesSkyrimUiStyle.drawObjectiveIndicator(layout.rightX + line.indent + 8, y + 4, line.complete, line.active);
                    this.fontRendererObj.drawStringWithShadow(line.text, layout.rightX + line.indent + 22, y, line.color);
                } else if (line.centered) {
                    this.fontRendererObj.drawStringWithShadow(line.text, layout.rightX + (layout.rightWidth - this.fontRendererObj.getStringWidth(line.text)) / 2, y, line.color);
                } else {
                    this.fontRendererObj.drawStringWithShadow(line.text, layout.rightX + line.indent, y, line.color);
                }
            }
            y += DETAIL_LINE_HEIGHT;
        }
        disableScissor();

        if (maxScroll > 0) {
            String count = (this.detailScroll + 1) + " / " + (maxScroll + 1);
            this.fontRendererObj.drawStringWithShadow(count, layout.rightX + layout.rightWidth - this.fontRendererObj.getStringWidth(count), layout.contentBottom - 10, LostTalesSkyrimUiStyle.TEXT_DIM);
        }
    }

    private List<DetailLine> buildDetailLines(ClientQuestEntry entry,
            int width) {
        LostTalesQuestDefinition quest = entry == null
                ? null : entry.getLostTalesDefinition();
        if (quest == null) {
            return buildExternalDetailLines(entry, width);
        }
        List<DetailLine> lines = new ArrayList<DetailLine>();
        LostTalesQuestProgress progress = entry.getLostTalesProgress();
        boolean completed = entry.isCompleted();
        boolean failed = entry.isFailed();
        boolean abandoned = entry.isAbandoned();
        boolean pinned = entry.isTracked();

        addTitleHeader(lines, quest.getTitle(), width);
        addQuestStatusLines(lines, quest, progress, completed, failed,
                abandoned, pinned, width);
        addHistoryLines(lines, entry.getHistoryEntry(), width);
        addBlankLine(lines);

        String loreText = getCurrentJournalText(quest, progress, completed);
        addWrappedLines(lines, loreText, completed ? LostTalesSkyrimUiStyle.TEXT_MUTED : LostTalesSkyrimUiStyle.TEXT, 8, width - 16);
        addBlankLine(lines);
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        addBlankLine(lines);

        addStageSummary(lines, quest, progress, completed,
                entry.getHistoryEntry(), width);
        addRewardSummary(lines, quest, width, completed || failed);

        if (pinned && progress != null && !completed) {
            addBlankLine(lines);
            addWrappedLines(lines, "This quest is being tracked.", LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
        }

        return lines;
    }

    private List<DetailLine> buildExternalDetailLines(
            ClientQuestEntry quest, int width) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        if (quest == null) {
            return lines;
        }
        addTitleHeader(lines, quest.getTitle(), width);
        String status = quest.isFailed() ? "Failed"
                : quest.isAbandoned() ? "Abandoned"
                : quest.isCompleted() ? "Completed" : "Active";
        String tracking = quest.isTracked() ? "tracked" : "not tracked";
        String stage = quest.getStageCount() > 1
                ? " | Stage " + quest.getStageNumber() + "/"
                        + quest.getStageCount() : "";
        addWrappedLines(lines, "Status: " + status + " | " + tracking
                + stage, quest.isFailed() ? LostTalesSkyrimUiStyle.RED
                        : quest.isAbandoned() ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_MUTED,
                8, width - 16);
        if (quest.getSubtitle().length() > 0) {
            addWrappedLines(lines, quest.getSubtitle(),
                    LostTalesSkyrimUiStyle.GOLD, 8, width - 16);
        }
        if (quest.isActive()) {
            addWrappedLines(lines, "Press Space or Enter to "
                    + (quest.isTracked() ? "stop tracking this quest."
                            : "track this quest on the HUD."),
                    LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
        }
        addBlankLine(lines);
        addWrappedLines(lines, quest.getJournalText(),
                quest.isCompleted() ? LostTalesSkyrimUiStyle.TEXT_MUTED
                        : LostTalesSkyrimUiStyle.TEXT,
                8, width - 16);
        addBlankLine(lines);
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        addSectionTitle(lines, "Objectives");
        for (ClientQuestEntry.Objective objective : quest.getObjectives()) {
            int color = objective.isComplete()
                    ? LostTalesSkyrimUiStyle.GREEN
                    : LostTalesSkyrimUiStyle.TEXT;
            addObjectiveWrappedLines(lines, objective.getText(), color,
                    12, width - 16, objective.isComplete(),
                    quest.isActive() && !objective.isComplete());
        }
        if (!quest.getRewards().isEmpty()) {
            addSectionTitle(lines, "Rewards");
            for (String reward : quest.getRewards()) {
                addWrappedLines(lines, reward,
                        quest.isCompleted() ? LostTalesSkyrimUiStyle.TEXT_DIM
                                : LostTalesSkyrimUiStyle.TEXT_MUTED,
                        16, width - 16);
            }
            addBlankLine(lines);
        }
        return lines;
    }

    private void addTitleHeader(List<DetailLine> lines, String title, int width) {
        String safeTitle = LostTalesSkyrimUiStyle.uppercase(title);
        String trimmedTitle = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, safeTitle, Math.max(40, width - 70));
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        lines.add(new DetailLine(trimmedTitle, LostTalesSkyrimUiStyle.TEXT_BRIGHT, 0, true));
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    private String getCurrentJournalText(LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed) {
        String best = null;
        if (!quest.getJournalLog().isEmpty()) {
            int currentValue = completed ? Integer.MAX_VALUE : getProgressStageNumber(progress);
            int bestValue = Integer.MIN_VALUE;
            for (Map.Entry<String, String> entry : quest.getJournalLog().entrySet()) {
                int value = parseStageNumber(entry.getKey(), Integer.MIN_VALUE);
                if (value <= currentValue && value >= bestValue) {
                    best = entry.getValue();
                    bestValue = value;
                }
            }
            if (best == null && !quest.getJournalLog().isEmpty()) {
                best = quest.getJournalLog().values().iterator().next();
            }
        }
        if (best == null || best.length() == 0) {
            best = quest.getDescription();
        }
        return best == null || best.length() == 0 ? "No journal text has been written for this quest yet." : best;
    }

    private void addStageSummary(List<DetailLine> lines,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean completed, LostTalesQuestHistoryEntry history,
            int width) {
        addSectionTitle(lines, "Objectives");
        if (quest.getStages().isEmpty()) {
            addWrappedLines(lines, "No objectives are written for this quest.", LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
            return;
        }

        int currentStageIndex = getCurrentStageIndex(progress, completed, quest);
        boolean addedAny = false;
        for (int i = 0; i < quest.getStages().size(); i++) {
            LostTalesQuestStageDefinition stage = quest.getStages().get(i);
            boolean stageComplete = completed || i < currentStageIndex;
            boolean current = progress != null && i == currentStageIndex && !completed;
            if (!completed && !stageComplete && !current) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                boolean lingeringOptional = !completed && i < currentStageIndex
                        && objective.isOptional();
                boolean objectiveActive = current || lingeringOptional;
                boolean recordedOptionalComplete = completed
                        && objective.isOptional() && history != null
                        && history.isOptionalObjectiveCompleted(
                        objective.getId());
                boolean implicitlyComplete = (stageComplete || completed)
                        && !objective.isOptional();
                boolean objectiveComplete = isObjectiveComplete(progress,
                        objective, objectiveActive,
                        implicitlyComplete || recordedOptionalComplete);
                int objectiveColor = completed
                        ? LostTalesSkyrimUiStyle.TEXT_DIM
                        : objectiveComplete ? LostTalesSkyrimUiStyle.GREEN
                        : objectiveActive ? LostTalesSkyrimUiStyle.TEXT
                        : LostTalesSkyrimUiStyle.TEXT_MUTED;
                String line = buildObjectiveLine(progress, objective,
                        objectiveActive,
                        implicitlyComplete || recordedOptionalComplete);
                addObjectiveWrappedLines(lines, line, objectiveColor, 12,
                        width - 16, objectiveComplete,
                        objectiveActive && !objectiveComplete);
                addedAny = true;
            }
        }

        if (!addedAny) {
            addWrappedLines(lines, completed ? "Quest complete." : "No current objective.", completed ? LostTalesSkyrimUiStyle.TEXT_DIM : LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
        }
        if (completed) {
            addWrappedLines(lines, "Quest complete.", LostTalesSkyrimUiStyle.TEXT_DIM, 16, width - 16);
        }
        addBlankLine(lines);
    }

    private void addRewardSummary(List<DetailLine> lines, LostTalesQuestDefinition quest, int width, boolean completed) {
        if (quest.getRewards().isEmpty()) {
            return;
        }
        addSectionTitle(lines, "Rewards");
        for (String rewardLine : buildRewardLines(quest)) {
            addWrappedLines(lines, rewardLine, completed ? LostTalesSkyrimUiStyle.TEXT_DIM : LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
        }
        addBlankLine(lines);
    }

    private void addQuestStatusLines(List<DetailLine> lines, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed, boolean failed, boolean abandoned, boolean pinned, int width) {
        String status = failed ? "Failed" : abandoned ? "Abandoned"
                : completed ? "Completed"
                : progress != null ? "Active" : "Known";
        String tracking = pinned ? "tracked" : "not tracked";
        String stageText = progress == null ? "" : " | Stage "
                + (LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest, progress) + 1) + "/"
                + Math.max(1, quest.getStages().size());
        int color = failed ? LostTalesSkyrimUiStyle.RED
                : abandoned ? LostTalesSkyrimUiStyle.GOLD
                : LostTalesSkyrimUiStyle.TEXT_MUTED;
        addWrappedLines(lines, "Status: " + status + " | " + tracking + stageText, color, 8, width - 16);
        if (progress != null && progress.hasTimeLimit() && this.mc != null && this.mc.theWorld != null) {
            String remaining = formatRemainingTime(progress.getRemainingTicks(this.mc.theWorld.getTotalWorldTime()));
            addWrappedLines(lines, "Time remaining: " + remaining, remaining.equals("expired") ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.GOLD, 8, width - 16);
        }
        if (progress != null && !completed) {
            addWrappedLines(lines, "Press Space or Enter to " + (pinned ? "stop tracking this quest." : "track this quest on the HUD."), LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
        }
    }

    private void addHistoryLines(List<DetailLine> lines,
            LostTalesQuestHistoryEntry history, int width) {
        if (history == null) {
            return;
        }
        if (history.getDetail().length() > 0) {
            addWrappedLines(lines,
                    (history.isCompleted() ? "Outcome: " : "Reason: ")
                            + history.getDetail(),
                    history.isFailed() ? LostTalesSkyrimUiStyle.RED
                            : history.isCompleted()
                            ? LostTalesSkyrimUiStyle.GREEN
                            : LostTalesSkyrimUiStyle.GOLD,
                    8, width - 16);
        }
        addWrappedLines(lines, "Recorded: "
                + formatWorldDate(history.getWorldTime()),
                LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
    }

    private String formatWorldDate(long worldTime) {
        long safeTime = Math.max(0L, worldTime);
        long day = safeTime / 24000L + 1L;
        long timeOfDay = safeTime % 24000L;
        long totalMinutes = (timeOfDay * 60L / 1000L + 360L) % 1440L;
        long hour = totalMinutes / 60L;
        long minute = totalMinutes % 60L;
        return "Day " + day + ", " + (hour < 10L ? "0" : "") + hour
                + ":" + (minute < 10L ? "0" : "") + minute;
    }

    private List<String> buildRewardLines(LostTalesQuestDefinition quest) {
        List<String> result = new ArrayList<String>();
        if (quest == null || quest.getRewards().isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : quest.getRewards().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if ("experience".equalsIgnoreCase(key) || "xp".equalsIgnoreCase(key) || "experiencePoints".equalsIgnoreCase(key)) {
                result.add(value + " experience");
            } else if ("levels".equalsIgnoreCase(key) || "experienceLevels".equalsIgnoreCase(key) || "xpLevels".equalsIgnoreCase(key)) {
                result.add(value + " experience level" + ("1".equals(value) ? "" : "s"));
            } else if ("items".equalsIgnoreCase(key) || "stacks".equalsIgnoreCase(key) || "itemStacks".equalsIgnoreCase(key)) {
                String[] parts = value.replace(';', ',').split(",");
                for (String part : parts) {
                    String reward = formatItemReward(part);
                    if (reward.length() > 0) {
                        result.add(reward);
                    }
                }
            } else if ("item".equalsIgnoreCase(key) || "itemId".equalsIgnoreCase(key) || "stack".equalsIgnoreCase(key)) {
                String reward = formatItemReward(value);
                if (reward.length() > 0) {
                    result.add(reward);
                }
            } else if (value.length() > 0) {
                result.add(prettifyKey(key) + ": " + value);
            }
        }
        if (result.isEmpty()) {
            result.add("A reward will be given when the quest is completed.");
        }
        return result;
    }

    private String formatItemReward(String value) {
        String spec = value == null ? "" : value.trim();
        if (spec.length() == 0) {
            return "";
        }
        int count = 1;
        int meta = 0;
        int star = spec.lastIndexOf('*');
        if (star >= 0 && star + 1 < spec.length()) {
            count = Math.max(1, parseStageNumber(spec.substring(star + 1), 1));
            spec = spec.substring(0, star);
        }
        int at = spec.lastIndexOf('@');
        if (at >= 0 && at + 1 < spec.length()) {
            meta = Math.max(0, parseStageNumber(spec.substring(at + 1), 0));
            spec = spec.substring(0, at);
        }
        if (spec.indexOf(':') < 0) {
            spec = "minecraft:" + spec;
        }
        Object object = Item.itemRegistry.getObject(spec);
        String name = spec;
        if (object instanceof Item) {
            try {
                name = new ItemStack((Item)object, 1, meta).getDisplayName();
            } catch (RuntimeException ignored) {
                name = prettifyKey(spec.substring(spec.indexOf(':') + 1));
            }
        } else if (spec.indexOf(':') >= 0) {
            name = prettifyKey(spec.substring(spec.indexOf(':') + 1));
        }
        return (count > 1 ? count + "x " : "") + name;
    }

    private String buildObjectiveLine(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, currentStage, questCompleted, false, false);
    }

    private boolean isObjectiveComplete(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean stageComplete) {
        int target = getObjectiveTargetCount(objective);
        int current = stageComplete ? target : currentStage && progress != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        return current >= target;
    }

    private int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
    }

    private int getCurrentStageIndex(LostTalesQuestProgress progress, boolean completed, LostTalesQuestDefinition quest) {
        if (completed) {
            return Math.max(0, quest.getStages().size() - 1);
        }
        if (progress == null) {
            return 0;
        }
        return LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest, progress);
    }

    private int getProgressStageNumber(LostTalesQuestProgress progress) {
        if (progress == null) {
            return Integer.MIN_VALUE;
        }
        int fromId = parseStageNumber(progress.getStageId(), Integer.MIN_VALUE);
        if (fromId != Integer.MIN_VALUE) {
            return fromId;
        }
        return progress.getStageIndex();
    }

    private String formatRemainingTime(long ticks) {
        if (ticks <= 0L) {
            return "expired";
        }
        long days = ticks / 24000L;
        long remainder = ticks % 24000L;
        long hours = remainder / 1000L;
        long minutes = (remainder % 1000L) * 60L / 1000L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    private int parseStageNumber(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void addSectionTitle(List<DetailLine> lines, String title) {
        addBlankLine(lines);
        lines.add(new DetailLine(LostTalesSkyrimUiStyle.uppercase(title), LostTalesSkyrimUiStyle.GOLD, 8, false));
    }

    private void addBlankLine(List<DetailLine> lines) {
        lines.add(new DetailLine("", LostTalesSkyrimUiStyle.TEXT, 0, false));
    }

    private void addSeparator(List<DetailLine> lines, int indent, int color) {
        DetailLine line = new DetailLine("", color, indent, false);
        line.separator = true;
        lines.add(line);
    }

    private void addObjectiveLine(List<DetailLine> lines, String text, int color, int indent, boolean complete, boolean active) {
        DetailLine line = new DetailLine(text, color, indent, false);
        line.objective = true;
        line.complete = complete;
        line.active = active;
        lines.add(line);
    }

    private void addObjectiveWrappedLines(List<DetailLine> lines, String text, int color, int indent, int width, boolean complete, boolean active) {
        if (text == null || text.length() == 0) {
            return;
        }
        int wrapWidth = Math.max(30, width - indent - 24);
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text, wrapWidth);
        if (wrapped.isEmpty()) {
            addObjectiveLine(lines, text, color, indent, complete, active);
            return;
        }
        for (int i = 0; i < wrapped.size(); i++) {
            if (i == 0) {
                addObjectiveLine(lines, wrapped.get(i), color, indent, complete, active);
            } else {
                lines.add(new DetailLine(wrapped.get(i), color, indent + 22, false));
            }
        }
    }

    private void addWrappedLines(List<DetailLine> lines, String text, int color, int indent, int width) {
        if (text == null || text.length() == 0) {
            return;
        }
        int wrapWidth = Math.max(20, width - Math.max(0, indent));
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text, wrapWidth);
        for (String line : wrapped) {
            lines.add(new DetailLine(line, color, indent, false));
        }
    }

    private int drawWrappedText(String text, int x, int y, int width, int color, int maxHeight) {
        if (text == null || text.length() == 0 || maxHeight <= 0) {
            return y;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        int lineY = y;
        int bottom = y + maxHeight;
        for (String line : lines) {
            if (lineY + 9 > bottom) {
                this.fontRendererObj.drawStringWithShadow("...", x, lineY, color);
                return bottom;
            }
            this.fontRendererObj.drawStringWithShadow(line, x, lineY, color);
            lineY += 10;
        }
        return lineY;
    }

    private void drawFooterHelp() {
        if (this.mc == null || this.fontRendererObj == null) {
            return;
        }
        List<Hint> hints = new ArrayList<Hint>();
        hints.add(Hint.wheel(this.mc, this.fontRendererObj, "Scroll"));
        hints.add(Hint.mouseButton(
                this.mc, this.fontRendererObj, 0, "Select"));
        hints.add(Hint.key(this.mc, this.fontRendererObj,
                Keyboard.KEY_SPACE, "Track"));
        hints.add(Hint.key(this.mc, this.fontRendererObj,
                Keyboard.KEY_F, "Filter"));
        hints.add(Hint.binding(this.mc, this.fontRendererObj,
                LostTalesKeyBindings.getQuestJournalKeyBinding(), "Close"));
        hints.add(Hint.binding(this.mc, this.fontRendererObj,
                LostTalesKeyBindings.getCharacterMenuKeyBinding(),
                "Character"));
        String sync = LostTalesClientQuestProgressStore.hasReceivedSync()
                ? "Server-synced" : "Waiting for sync";
        LostTalesControlBar.render(this, this.mc, this.fontRendererObj,
                this.width, this.height, hints, 4, 0,
                Arrays.asList(sync), true);
    }

    private List<ClientQuestEntry> getVisibleQuests() {
        List<ClientQuestEntry> visible = new ArrayList<ClientQuestEntry>();
        for (ClientQuestEntry quest : ClientQuestCatalog.getEntries(this.mc)) {
            if (this.filter == QuestFilter.ACTIVE && !quest.isActive()) {
                continue;
            }
            if (this.filter == QuestFilter.COMPLETED && !quest.isCompleted()) {
                continue;
            }
            if (this.filter == QuestFilter.HISTORY
                    && !quest.isFailed() && !quest.isAbandoned()) {
                continue;
            }
            visible.add(quest);
        }
        return visible;
    }

    private List<QuestListRow> buildQuestListRows(List<ClientQuestEntry> quests) {
        List<QuestListRow> rows = new ArrayList<QuestListRow>();
        String lastCategory = null;
        for (int i = 0; i < quests.size(); i++) {
            ClientQuestEntry quest = quests.get(i);
            String category = quest.getCategory().length() == 0
                    ? "Miscellaneous" : quest.getCategory();
            if (!category.equals(lastCategory)) {
                rows.add(QuestListRow.category(category));
                lastCategory = category;
            }
            if (!this.collapsedCategories.contains(category)) {
                rows.add(QuestListRow.quest(quest, i));
            }
        }
        return rows;
    }

    private int getRowsHeight(List<QuestListRow> rows) {
        int height = 0;
        for (QuestListRow row : rows) {
            height += row.height;
        }
        return height;
    }

    private int getQuestRowY(List<QuestListRow> rows, int questIndex, int startY) {
        int y = startY;
        for (QuestListRow row : rows) {
            if (!row.category && row.questIndex == questIndex) {
                return y;
            }
            y += row.height;
        }
        return Integer.MIN_VALUE;
    }

    private int getQuestCategoryStartY(List<QuestListRow> rows, int questIndex) {
        int y = 0;
        for (QuestListRow row : rows) {
            if (!row.category && row.questIndex == questIndex) {
                return y;
            }
            y += row.height;
        }
        return 0;
    }

    private String prettifyKey(String key) {
        if (key == null || key.length() == 0) {
            return "";
        }
        String[] parts = key.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ENGLISH));
            }
        }
        return builder.length() == 0 ? key : builder.toString();
    }


    private ClientQuestEntry getSelectedQuest() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty() || this.selectedQuestIndex < 0 || this.selectedQuestIndex >= quests.size()) {
            return null;
        }
        return quests.get(this.selectedQuestIndex);
    }

    private void setSelectedQuestIndex(int index) {
        if (index != this.selectedQuestIndex) {
            this.detailScroll = 0;
        }
        this.selectedQuestIndex = index;
        clampSelectionAndScroll();
        ensureSelectedQuestVisible();
    }

    private void clampSelectionAndScroll() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty()) {
            this.selectedQuestIndex = 0;
            this.listScroll = 0;
            this.detailScroll = 0;
            return;
        }

        if (this.selectedQuestIndex < 0) {
            this.selectedQuestIndex = 0;
        }
        if (this.selectedQuestIndex >= quests.size()) {
            this.selectedQuestIndex = quests.size() - 1;
        }

        JournalLayout layout = getLayout();
        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = Math.max(20, layout.contentBottom - layout.contentTop);
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }

        int maxDetailScroll = getDetailMaxScroll();
        if (this.detailScroll > maxDetailScroll) {
            this.detailScroll = maxDetailScroll;
        }
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
    }

    private void ensureSelectedQuestVisible() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty()) {
            return;
        }
        JournalLayout layout = getLayout();
        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = Math.max(20, layout.contentBottom - layout.contentTop);
        int selectedY = getQuestCategoryStartY(rows, this.selectedQuestIndex);
        int selectedBottom = selectedY + LIST_ROW_HEIGHT;
        if (selectedY < this.listScroll + 32) {
            this.listScroll = Math.max(0, selectedY - 32);
        } else if (selectedBottom > this.listScroll + visibleHeight - 20) {
            this.listScroll = selectedBottom - visibleHeight + 20;
        }
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }
    }

    private int getDetailMaxScroll() {
        ClientQuestEntry quest = getSelectedQuest();
        if (quest == null || this.fontRendererObj == null) {
            return 0;
        }
        JournalLayout layout = getLayout();
        List<DetailLine> lines = buildDetailLines(quest, layout.rightWidth - 20);
        int visibleHeight = layout.contentBottom - layout.contentTop;
        return Math.max(0, lines.size() * DETAIL_LINE_HEIGHT - visibleHeight + 4);
    }

    private boolean isMouseOverDetails(int mouseX, int mouseY) {
        JournalLayout layout = getLayout();
        return mouseX >= layout.rightX && mouseX < layout.rightX + layout.rightWidth && mouseY >= layout.contentTop && mouseY < layout.contentBottom;
    }

    private boolean isMouseOverList(int mouseX, int mouseY) {
        JournalLayout layout = getLayout();
        return mouseX >= layout.leftX && mouseX < layout.leftX + layout.leftWidth && mouseY >= layout.contentTop && mouseY < layout.contentBottom;
    }

    /**
     * The list row under the point, or null outside the list viewport. A row
     * spans the list's full width, so the hover highlight, the click and the
     * pointer all answer for the same pixels.
     */
    private QuestListRow rowAt(List<QuestListRow> rows, int mouseX, int mouseY) {
        if (rows == null || !isMouseOverList(mouseX, mouseY)) {
            return null;
        }
        int relativeY = mouseY - getLayout().contentTop + this.listScroll;
        int y = 0;
        for (QuestListRow row : rows) {
            if (relativeY >= y && relativeY < y + row.height) {
                return row;
            }
            y += row.height;
        }
        return null;
    }

    private void scrollDetails(int amount) {
        this.detailScroll += amount;
        int max = getDetailMaxScroll();
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
        if (this.detailScroll > max) {
            this.detailScroll = max;
        }
    }

    private void scrollList(int amount) {
        List<QuestListRow> rows = buildQuestListRows(getVisibleQuests());
        JournalLayout layout = getLayout();
        int maxScroll = Math.max(0, getRowsHeight(rows) - (layout.contentBottom - layout.contentTop));
        this.listScroll += amount;
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            QuestListRow row = rowAt(buildQuestListRows(getVisibleQuests()), mouseX, mouseY);
            if (row != null && row.category) {
                toggleCategory(row.label);
                return;
            }
            if (row != null && row.quest != null) {
                long now = System.currentTimeMillis();
                boolean doubleClick = this.lastClickedQuestIndex == row.questIndex && now - this.lastQuestClickMs <= DOUBLE_CLICK_TRACK_MS;
                setSelectedQuestIndex(row.questIndex);
                if (doubleClick) {
                    toggleSelectedQuestTracking(row.quest);
                }
                this.lastClickedQuestIndex = row.questIndex;
                this.lastQuestClickMs = now;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Every list row answers to a click: a category header folds or unfolds
     * its category and a quest row selects its quest.
     */
    @Override
    public boolean isPointerOverInteractable(int mouseX, int mouseY) {
        QuestListRow row = rowAt(buildQuestListRows(getVisibleQuests()), mouseX, mouseY);
        if (row != null && (row.category || row.quest != null)) {
            return true;
        }
        return LostTalesGuiPointerTargets.isOverEnabledButton(this, mouseX, mouseY);
    }

    private void toggleCategory(String category) {
        if (category == null || category.length() == 0) {
            return;
        }
        if (this.collapsedCategories.contains(category)) {
            this.collapsedCategories.remove(category);
        } else {
            this.collapsedCategories.add(category);
        }
        clampSelectionAndScroll();
    }

    private void toggleSelectedQuestTracking(ClientQuestEntry quest) {
        if (quest == null || !quest.isActive()) {
            return;
        }
        if (quest.isTracked()) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesQuestActionPacket(
                            LostTalesQuestActionPacket.ACTION_UNPIN,
                            quest.getReference()));
        } else {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesQuestActionPacket(
                            LostTalesQuestActionPacket.ACTION_PIN,
                            quest.getReference()));
        }
    }

    @Override
    public void handleMouseInput() {
        int eventMouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int eventMouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        eventMouseX = LostTalesGuiAnimations.inverseMouseX(
                this, eventMouseX);
        eventMouseY = LostTalesGuiAnimations.inverseMouseY(
                this, eventMouseY);
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int notches = wheel / 120;
            if (notches == 0) {
                notches = wheel > 0 ? 1 : -1;
            }
            int amount = -notches * 24;
            if (isMouseOverDetails(eventMouseX, eventMouseY)) {
                scrollDetails(amount);
            } else if (isMouseOverList(eventMouseX, eventMouseY)) {
                scrollList(amount);
            } else {
                JournalLayout layout = getLayout();
                if (eventMouseX >= layout.dividerX) {
                    scrollDetails(amount);
                } else {
                    scrollList(amount);
                }
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || LostTalesKeyBindings.isQuestJournalKey(keyCode)) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (LostTalesKeyBindings.isCharacterMenuKey(keyCode)) {
            this.mc.displayGuiScreen(new LostTalesCharacterMenuGui(this.parent));
            return;
        }
        if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN) {
            toggleSelectedQuestTracking(getSelectedQuest());
            return;
        }
        if (keyCode == Keyboard.KEY_F) {
            cycleFilter();
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            setSelectedQuestIndex(this.selectedQuestIndex - 1);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            setSelectedQuestIndex(this.selectedQuestIndex + 1);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollDetails(-80);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollDetails(80);
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            this.detailScroll = 0;
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            this.detailScroll = getDetailMaxScroll();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void cycleFilter() {
        this.filter = this.filter.next();
        this.selectedQuestIndex = 0;
        this.listScroll = 0;
        this.detailScroll = 0;
        clampSelectionAndScroll();
    }

    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, this.mc.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private JournalLayout getLayout() {
        int contentTop = TOP_BAR_HEIGHT + 8;
        int contentBottom = this.height - FOOTER_HEIGHT - 6;
        int leftWidth = Math.min(LEFT_WIDTH, Math.max(170, this.width / 3));
        int leftX = OUTER_PADDING;
        int dividerX = leftX + leftWidth + 20;
        int rightX = dividerX + 28;
        int rightWidth = Math.max(120, this.width - rightX - OUTER_PADDING);
        return new JournalLayout(leftX, leftWidth, rightX, rightWidth, dividerX, contentTop, contentBottom);
    }

    private enum QuestFilter {
        ALL("All"),
        ACTIVE("Active"),
        COMPLETED("Completed"),
        HISTORY("History");

        private final String displayName;

        QuestFilter(String displayName) {
            this.displayName = displayName;
        }

        private QuestFilter next() {
            QuestFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final class JournalLayout {
        private final int leftX;
        private final int leftWidth;
        private final int rightX;
        private final int rightWidth;
        private final int dividerX;
        private final int contentTop;
        private final int contentBottom;

        private JournalLayout(int leftX, int leftWidth, int rightX, int rightWidth, int dividerX, int contentTop, int contentBottom) {
            this.leftX = leftX;
            this.leftWidth = leftWidth;
            this.rightX = rightX;
            this.rightWidth = rightWidth;
            this.dividerX = dividerX;
            this.contentTop = contentTop;
            this.contentBottom = contentBottom;
        }
    }

    private static final class QuestListRow {
        private final boolean category;
        private final String label;
        private final ClientQuestEntry quest;
        private final int questIndex;
        private final int height;

        private QuestListRow(boolean category, String label,
                ClientQuestEntry quest, int questIndex, int height) {
            this.category = category;
            this.label = label;
            this.quest = quest;
            this.questIndex = questIndex;
            this.height = height;
        }

        private static QuestListRow category(String label) {
            return new QuestListRow(true, label == null ? "Miscellaneous" : label, null, -1, CATEGORY_ROW_HEIGHT);
        }

        private static QuestListRow quest(ClientQuestEntry quest, int index) {
            return new QuestListRow(false, null, quest, index, LIST_ROW_HEIGHT);
        }
    }

    private static final class DetailLine {
        private final String text;
        private final int color;
        private final int indent;
        private final boolean centered;
        private boolean separator;
        private boolean objective;
        private boolean complete;
        private boolean active;

        private DetailLine(String text, int color, int indent, boolean centered) {
            this.text = text == null ? "" : text;
            this.color = color;
            this.indent = Math.max(0, indent);
            this.centered = centered;
        }
    }
}
