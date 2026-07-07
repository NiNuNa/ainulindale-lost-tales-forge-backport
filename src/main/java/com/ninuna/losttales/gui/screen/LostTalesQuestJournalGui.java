package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
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
 * The screen intentionally stays client-only and data-only. It reads quest definitions from the
 * client cache and sends small action packets to the server when a quest is tracked.
 * The quest level bar is a placeholder hook until the real quest leveling system is introduced.
 */
public class LostTalesQuestJournalGui extends GuiScreen {
    private static final int OUTER_PADDING = 16;
    private static final int TOP_BAR_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 24;
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

        List<LostTalesQuestDefinition> quests = getVisibleQuests();
        JournalLayout layout = getLayout();
        drawVerticalDivider(layout);
        drawQuestList(quests, layout, mouseX, mouseY);
        drawQuestDetails(quests, layout);
        drawFooterHelp();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawWorldDimmedBackground() {
        drawGradientRect(0, 0, this.width, this.height, 0xD0000000, 0xC8000000);
        drawRect(0, 0, this.width, 1, LostTalesSkyrimUiStyle.BORDER);
        drawRect(0, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + 1, LostTalesSkyrimUiStyle.BORDER);
        drawRect(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    private void drawTopBar() {
        String title = "QUEST JOURNAL";
        this.fontRendererObj.drawStringWithShadow(title, OUTER_PADDING, 14, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        LostTalesSkyrimUiStyle.drawDiamond(OUTER_PADDING + this.fontRendererObj.getStringWidth(title) + 14, 18, LostTalesSkyrimUiStyle.TEXT_MUTED);

        drawQuestLevelBar(this.width / 2 - 92, 10, 184, 18);

        String filterInfo = "Filter: " + this.filter.displayName;
        this.fontRendererObj.drawStringWithShadow(filterInfo, OUTER_PADDING, 26, LostTalesSkyrimUiStyle.TEXT_MUTED);

        String worldInfo = getWorldTimeText();
        this.fontRendererObj.drawStringWithShadow(worldInfo, this.width - OUTER_PADDING - this.fontRendererObj.getStringWidth(worldInfo), 14, LostTalesSkyrimUiStyle.TEXT);
    }

    private void drawQuestLevelBar(int x, int y, int width, int height) {
        int level = getQuestLevel();
        int progressWidth = Math.max(0, Math.min(width - 60, (int)((width - 60) * getQuestLevelProgressPercent())));
        String levelText = "LEVEL " + level;
        this.fontRendererObj.drawStringWithShadow(levelText, x, y + 4, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        int barX = x + 50;
        int barY = y + 8;
        int barW = width - 60;
        drawRect(barX, barY, barX + barW, barY + 1, LostTalesSkyrimUiStyle.BORDER_DIM);
        drawRect(barX + 2, barY - 3, barX + barW - 2, barY + 4, 0x66000000);
        drawRect(barX + 4, barY - 1, barX + barW - 4, barY + 2, 0x5535424A);
        if (progressWidth > 0) {
            drawRect(barX + 4, barY - 1, barX + 4 + progressWidth, barY + 2, LostTalesSkyrimUiStyle.BLUE);
        }
        LostTalesSkyrimUiStyle.drawDiamond(barX, barY, LostTalesSkyrimUiStyle.TEXT_MUTED);
        LostTalesSkyrimUiStyle.drawDiamond(barX + barW, barY, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    /** Placeholder until a server-synced quest-level capability/NBT store exists. */
    private int getQuestLevel() {
        return 1;
    }

    /** Placeholder until the real quest leveling system can sync current XP/next-level XP. */
    private float getQuestLevelProgressPercent() {
        return 0.0F;
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

    private void drawQuestList(List<LostTalesQuestDefinition> quests, JournalLayout layout, int mouseX, int mouseY) {
        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = layout.contentBottom - layout.contentTop;
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }

        enableScissor(layout.leftX - 8, layout.contentTop - 2, layout.leftWidth + 20, visibleHeight + 4);
        int rowY = layout.contentTop - this.listScroll;
        for (QuestListRow row : rows) {
            if (rowY + row.height >= layout.contentTop && rowY <= layout.contentBottom) {
                if (row.category) {
                    drawCategoryHeader(row.label, layout.leftX, rowY, layout.leftWidth);
                } else if (row.quest != null) {
                    drawQuestRow(row.quest, row.questIndex, layout.leftX + 14, rowY, layout.leftWidth - 22, row.height, mouseX, mouseY);
                }
            }
            rowY += row.height;
        }
        disableScissor();
        drawListBottomFade(layout);

        LostTalesQuestDefinition selected = getSelectedQuest();
        if (selected != null) {
            int selectedY = getQuestRowY(rows, this.selectedQuestIndex, layout.contentTop - this.listScroll);
            if (selectedY >= layout.contentTop && selectedY <= layout.contentBottom - LIST_ROW_HEIGHT) {
                drawSelectorArrow(layout.leftX + layout.leftWidth - 6, selectedY + LIST_ROW_HEIGHT / 2, LostTalesClientQuestProgressStore.isQuestPinned(selected.getId()));
            }
        }

        if (rows.isEmpty()) {
            String message = LostTalesClientQuestDefinitionStore.getQuests().isEmpty()
                    ? "No quest definitions were loaded from assets/losttales/quest."
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

    private void drawQuestRow(LostTalesQuestDefinition quest, int questIndex, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean selected = questIndex == this.selectedQuestIndex;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        boolean active = LostTalesClientQuestProgressStore.isQuestActive(quest.getId());
        boolean completed = LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());
        boolean pinned = LostTalesClientQuestProgressStore.isQuestPinned(quest.getId());

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

        int titleColor = completed ? LostTalesSkyrimUiStyle.TEXT_DIM : selected ? LostTalesSkyrimUiStyle.TEXT_BRIGHT : active ? LostTalesSkyrimUiStyle.TEXT : LostTalesSkyrimUiStyle.TEXT_DIM;
        String title = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, quest.getTitle(), width - 48);
        this.fontRendererObj.drawStringWithShadow(title, x + 32, y + 8, titleColor);
    }

    private void drawSelectorArrow(int x, int y, boolean gold) {
        drawActiveQuestIcon(x - ACTIVE_ICON_WIDTH, y - ACTIVE_ICON_HEIGHT / 2, gold ? 1.0F : 0.8F);
    }

    private void drawActiveQuestIcon(int x, int y, float alpha) {
        LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(this.mc, ACTIVE_QUEST_ICON_TEXTURE, x, y, 0, 0, ACTIVE_ICON_WIDTH, ACTIVE_ICON_HEIGHT, ACTIVE_ICON_WIDTH, ACTIVE_ICON_HEIGHT, alpha);
    }

    private void drawQuestDetails(List<LostTalesQuestDefinition> quests, JournalLayout layout) {
        if (quests.isEmpty()) {
            String message = LostTalesClientQuestDefinitionStore.getQuests().isEmpty()
                    ? "The client did not load any quest JSON files. Check quest/index.json and bundled quest files."
                    : "Started quests will appear here after you collect or accept them in the world.";
            drawWrappedText(message, layout.rightX, layout.contentTop + 12, layout.rightWidth, LostTalesSkyrimUiStyle.TEXT_MUTED, layout.contentBottom - layout.contentTop - 24);
            this.detailScroll = 0;
            return;
        }

        LostTalesQuestDefinition quest = quests.get(this.selectedQuestIndex);
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

    private List<DetailLine> buildDetailLines(LostTalesQuestDefinition quest, int width) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        LostTalesQuestProgress progress = LostTalesClientQuestProgressStore.getActiveQuest(quest.getId());
        boolean completed = LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());
        boolean pinned = LostTalesClientQuestProgressStore.isQuestPinned(quest.getId());

        addTitleHeader(lines, quest.getTitle(), width);
        addQuestStatusLines(lines, quest, progress, completed, pinned, width);
        addBlankLine(lines);

        String loreText = getCurrentJournalText(quest, progress, completed);
        addWrappedLines(lines, loreText, completed ? LostTalesSkyrimUiStyle.TEXT_MUTED : LostTalesSkyrimUiStyle.TEXT, 8, width - 16);
        addBlankLine(lines);
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        addBlankLine(lines);

        addStageSummary(lines, quest, progress, completed, width);
        addRewardSummary(lines, quest, width, completed);

        if (pinned && progress != null && !completed) {
            addBlankLine(lines);
            addWrappedLines(lines, "This quest is being tracked.", LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
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

    private void addStageSummary(List<DetailLine> lines, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed, int width) {
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
            boolean upcoming = progress != null && !completed && i == currentStageIndex + 1;
            if (!completed && !stageComplete && !current && !upcoming) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                boolean objectiveComplete = isObjectiveComplete(progress, objective, current, stageComplete || completed);
                int objectiveColor = completed ? LostTalesSkyrimUiStyle.TEXT_DIM : objectiveComplete ? LostTalesSkyrimUiStyle.GREEN : current ? LostTalesSkyrimUiStyle.TEXT : LostTalesSkyrimUiStyle.TEXT_MUTED;
                String line = buildObjectiveLine(progress, objective, current, completed || stageComplete);
                addObjectiveWrappedLines(lines, line, objectiveColor, 12, width - 16, objectiveComplete, current && !objectiveComplete);
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

    private void addQuestStatusLines(List<DetailLine> lines, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed, boolean pinned, int width) {
        String status = completed ? "Completed" : progress != null ? "Active" : "Known";
        String tracking = pinned ? "tracked" : "not tracked";
        String stageText = progress == null ? "" : " | Stage " + (progress.getStageIndex() + 1) + "/" + Math.max(1, quest.getStages().size());
        addWrappedLines(lines, "Status: " + status + " | " + tracking + stageText, LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
        if (progress != null && !completed) {
            addWrappedLines(lines, "Press Space or Enter to " + (pinned ? "stop tracking this quest." : "track this quest on the HUD."), LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
        }
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
        return Math.max(0, Math.min(progress.getStageIndex(), Math.max(0, quest.getStages().size() - 1)));
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

    private void drawDecorativeSerpent(int x, int y, int color) {
        drawRect(x, y + 6, x + 22, y + 7, color);
        drawRect(x + 3, y + 4, x + 12, y + 5, color);
        drawRect(x + 12, y + 5, x + 27, y + 6, color);
        drawRect(x + 24, y + 3, x + 34, y + 4, color);
        LostTalesSkyrimUiStyle.drawDiamond(x + 35, y + 4, color);
    }

    private void drawLargeTextWithShadow(String text, int x, int y, int color, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void drawFooterHelp() {
        String sync = LostTalesClientQuestProgressStore.hasReceivedSync() ? "Server-synced" : "Waiting for server sync";
        String help = "Wheel: scroll   Click: select   Double-click/Space/Enter: track   F: filter   "
                + LostTalesKeyBindings.getQuestJournalKeyDisplayName() + "/Esc: close   "
                + LostTalesKeyBindings.getCharacterMenuKeyDisplayName() + ": character   " + sync;
        this.fontRendererObj.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, help, this.width - OUTER_PADDING * 2), OUTER_PADDING, this.height - FOOTER_HEIGHT + 11, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private List<LostTalesQuestDefinition> getVisibleQuests() {
        List<LostTalesQuestDefinition> allQuests = new ArrayList<LostTalesQuestDefinition>(LostTalesClientQuestDefinitionStore.getQuests());
        Collections.sort(allQuests, new Comparator<LostTalesQuestDefinition>() {
            @Override
            public int compare(LostTalesQuestDefinition a, LostTalesQuestDefinition b) {
                int categoryCompare = getQuestCategory(a).compareToIgnoreCase(getQuestCategory(b));
                if (categoryCompare != 0) {
                    return categoryCompare;
                }
                boolean aCompleted = LostTalesClientQuestProgressStore.isQuestCompleted(a.getId());
                boolean bCompleted = LostTalesClientQuestProgressStore.isQuestCompleted(b.getId());
                if (aCompleted != bCompleted) {
                    return aCompleted ? 1 : -1;
                }
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
        });

        List<LostTalesQuestDefinition> visible = new ArrayList<LostTalesQuestDefinition>();
        for (LostTalesQuestDefinition quest : allQuests) {
            if (quest == null || quest.getId() == null) {
                continue;
            }
            // The journal is a record of collected/started quests. Startable-but-not-collected
            // definitions stay hidden so the journal does not reveal future content.
            boolean active = LostTalesClientQuestProgressStore.isQuestActive(quest.getId());
            boolean completed = LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());
            if (!active && !completed) {
                continue;
            }
            if (this.filter == QuestFilter.ACTIVE && !active) {
                continue;
            }
            if (this.filter == QuestFilter.COMPLETED && !completed) {
                continue;
            }
            visible.add(quest);
        }
        return visible;
    }

    private List<QuestListRow> buildQuestListRows(List<LostTalesQuestDefinition> quests) {
        List<QuestListRow> rows = new ArrayList<QuestListRow>();
        String lastCategory = null;
        for (int i = 0; i < quests.size(); i++) {
            LostTalesQuestDefinition quest = quests.get(i);
            String category = getQuestCategory(quest);
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

    private String getQuestCategory(LostTalesQuestDefinition quest) {
        if (quest == null || quest.getId() == null) {
            return "Miscellaneous";
        }
        String id = quest.getId();
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String lower = path.toLowerCase(Locale.ENGLISH);
        if (lower.startsWith("missive/") || lower.startsWith("misc/") || lower.startsWith("miscellaneous/")) {
            return "Miscellaneous";
        }
        if (lower.startsWith("tutorial/")) {
            return "Tutorial";
        }
        if (lower.startsWith("path/")) {
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return "Paths: " + prettifyKey(parts[1]);
            }
            return "Paths";
        }
        if (lower.startsWith("faction/")) {
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return "Factions: " + prettifyKey(parts[1]);
            }
            return "Factions";
        }
        return "Miscellaneous";
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


    private LostTalesQuestDefinition getSelectedQuest() {
        List<LostTalesQuestDefinition> quests = getVisibleQuests();
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
        List<LostTalesQuestDefinition> quests = getVisibleQuests();
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
        List<LostTalesQuestDefinition> quests = getVisibleQuests();
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
        LostTalesQuestDefinition quest = getSelectedQuest();
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
        if (mouseButton == 0 && isMouseOverList(mouseX, mouseY)) {
            List<LostTalesQuestDefinition> quests = getVisibleQuests();
            List<QuestListRow> rows = buildQuestListRows(quests);
            JournalLayout layout = getLayout();
            int relativeY = mouseY - layout.contentTop + this.listScroll;
            int y = 0;
            for (QuestListRow row : rows) {
                if (relativeY >= y && relativeY < y + row.height) {
                    if (row.category) {
                        toggleCategory(row.label);
                        return;
                    }
                    if (row.quest != null) {
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
                    break;
                }
                y += row.height;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
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

    private void toggleSelectedQuestTracking(LostTalesQuestDefinition quest) {
        if (quest == null || !LostTalesClientQuestProgressStore.isQuestActive(quest.getId())) {
            return;
        }
        if (LostTalesClientQuestProgressStore.isQuestPinned(quest.getId())) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_UNPIN, quest.getId()));
        } else {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_PIN, quest.getId()));
        }
    }

    @Override
    public void handleMouseInput() {
        int eventMouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int eventMouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
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
        COMPLETED("Completed");

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
        private final LostTalesQuestDefinition quest;
        private final int questIndex;
        private final int height;

        private QuestListRow(boolean category, String label, LostTalesQuestDefinition quest, int questIndex, int height) {
            this.category = category;
            this.label = label;
            this.quest = quest;
            this.questIndex = questIndex;
            this.height = height;
        }

        private static QuestListRow category(String label) {
            return new QuestListRow(true, label == null ? "Miscellaneous" : label, null, -1, CATEGORY_ROW_HEIGHT);
        }

        private static QuestListRow quest(LostTalesQuestDefinition quest, int index) {
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
