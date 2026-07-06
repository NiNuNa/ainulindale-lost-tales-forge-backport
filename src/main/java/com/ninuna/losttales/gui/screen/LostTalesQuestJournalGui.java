package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LostTalesQuestJournalGui extends GuiScreen {
    private static final int LIST_WIDTH = 190;
    private static final int ROW_HEIGHT = 27;
    private static final int OUTER_PADDING = 18;
    private static final int BUTTON_START = 1;
    private static final int BUTTON_ABANDON = 2;
    private static final int BUTTON_SCAN = 3;
    private static final int BUTTON_PIN = 4;
    private static final int BUTTON_MARKER = 5;
    private static final int BUTTON_FILTER = 6;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_ACTIVE = 1;
    private static final int FILTER_STARTABLE = 2;
    private static final int FILTER_COMPLETED = 3;
    private static final int FILTER_COUNT = 4;

    private final GuiScreen parent;
    private int selectedQuestIndex;
    private int listScroll;
    private int detailScroll;
    private int filterMode;
    private int lastMouseX;
    private int lastMouseY;
    private GuiButton startButton;
    private GuiButton abandonButton;
    private GuiButton scanButton;
    private GuiButton pinButton;
    private GuiButton markerButton;
    private GuiButton filterButton;

    public LostTalesQuestJournalGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        LostTalesClientQuestDefinitionStore.ensureLoaded(this.mc.getResourceManager());
        clampSelectionAndScroll();

        this.buttonList.clear();
        int buttonY = this.height - 54;
        int buttonX = OUTER_PADDING;
        this.filterButton = new GuiButton(BUTTON_FILTER, OUTER_PADDING, 12, 128, 20, getFilterButtonLabel());
        this.startButton = new GuiButton(BUTTON_START, buttonX, buttonY, 64, 20, "Start");
        this.abandonButton = new GuiButton(BUTTON_ABANDON, buttonX + 70, buttonY, 74, 20, "Abandon");
        this.pinButton = new GuiButton(BUTTON_PIN, buttonX + 150, buttonY, 70, 20, "Track");
        this.scanButton = new GuiButton(BUTTON_SCAN, buttonX + 226, buttonY, 108, 20, "Scan Inventory");
        this.markerButton = new GuiButton(BUTTON_MARKER, buttonX + 340, buttonY, 112, 20, "Marker");
        this.buttonList.add(this.filterButton);
        this.buttonList.add(this.startButton);
        this.buttonList.add(this.abandonButton);
        this.buttonList.add(this.pinButton);
        this.buttonList.add(this.scanButton);
        this.buttonList.add(this.markerButton);
        updateActionButtons(getVisibleQuests());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "Quest Journal", this.width / 2, 18, 0xFFFFFF);
        clampSelectionAndScroll();

        List<LostTalesQuestDefinition> quests = getVisibleQuests();
        int listX = OUTER_PADDING;
        int listY = 38;
        int listHeight = Math.max(80, this.height - 108);
        int detailsX = listX + LIST_WIDTH + 14;
        int detailsY = listY;
        int detailsWidth = this.width - detailsX - OUTER_PADDING;

        drawPanel(listX - 4, listY - 4, LIST_WIDTH + 8, listHeight + 8, 0xAA000000, 0x66FFFFFF);
        drawPanel(detailsX - 4, detailsY - 4, detailsWidth + 8, listHeight + 8, 0xAA000000, 0x66FFFFFF);

        drawQuestList(quests, listX, listY, listHeight, mouseX, mouseY);
        drawQuestDetails(quests, detailsX, detailsY, detailsWidth, listHeight);
        updateActionButtons(quests);

        String footer = LostTalesClientQuestProgressStore.hasReceivedSync()
                ? "Quest state is synced from the server. Use J/Esc to close, Shift to character menu, mouse wheel to scroll."
                : "Quest definitions loaded. Waiting for server quest-state sync.";
        this.drawCenteredString(this.fontRendererObj, footer, this.width / 2, this.height - 22, 0x888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawQuestList(List<LostTalesQuestDefinition> quests, int x, int y, int height, int mouseX, int mouseY) {
        this.fontRendererObj.drawString(getQuestListHeading(), x, y, 0xFFD37A);

        if (quests.isEmpty()) {
            String message = LostTalesClientQuestDefinitionStore.getQuests().isEmpty()
                    ? "No quest definitions were loaded from assets/losttales/quest."
                    : "No quests match this filter.";
            drawWrappedText(message, x, y + 18, LIST_WIDTH - 8, 0xAAAAAA, height - 18);
            return;
        }

        int firstRowY = y + 16;
        int visibleRows = Math.max(1, (height - 20) / ROW_HEIGHT);
        int lastIndex = Math.min(quests.size(), this.listScroll + visibleRows);

        for (int i = this.listScroll; i < lastIndex; i++) {
            LostTalesQuestDefinition quest = quests.get(i);
            int rowY = firstRowY + (i - this.listScroll) * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = i == this.selectedQuestIndex;

            if (selected) {
                drawRect(x - 2, rowY - 1, x + LIST_WIDTH - 2, rowY + ROW_HEIGHT - 2, 0x66336699);
            } else if (hovered) {
                drawRect(x - 2, rowY - 1, x + LIST_WIDTH - 2, rowY + ROW_HEIGHT - 2, 0x44222222);
            }

            String status = getQuestStatusLabel(quest);
            int statusColor = getQuestStatusColor(quest);
            this.fontRendererObj.drawString(trimToWidth(quest.getTitle(), LIST_WIDTH - 8), x + 4, rowY + 3, selected ? 0xFFFFFF : 0xCCCCCC);
            this.fontRendererObj.drawString(status, x + 4, rowY + 14, statusColor);
            this.fontRendererObj.drawString(trimToWidth(quest.getId(), LIST_WIDTH - this.fontRendererObj.getStringWidth(status) - 14), x + 8 + this.fontRendererObj.getStringWidth(status), rowY + 14, 0x777777);
        }

        if (quests.size() > visibleRows) {
            this.fontRendererObj.drawString((this.selectedQuestIndex + 1) + " / " + quests.size(), x + 4, y + height - 10, 0x777777);
        }
    }

    private void drawQuestDetails(List<LostTalesQuestDefinition> quests, int x, int y, int width, int height) {
        if (quests.isEmpty()) {
            if (LostTalesClientQuestDefinitionStore.getQuests().isEmpty()) {
                drawWrappedText("The modern branch has quest JSON files, but none could be read by the 1.7.10 loader. Check quest/index.json and the copied quest files.", x, y, width, 0xAAAAAA, height);
            } else {
                drawWrappedText("Change the filter to see more quests.", x, y, width, 0xAAAAAA, height);
            }
            this.detailScroll = 0;
            return;
        }

        LostTalesQuestDefinition quest = quests.get(this.selectedQuestIndex);
        List<DetailLine> lines = buildDetailLines(quest, width);
        int visibleLines = Math.max(1, (height - 8) / 10);
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
        if (this.detailScroll > maxScroll) {
            this.detailScroll = maxScroll;
        }

        int lineY = y;
        int lastLine = Math.min(lines.size(), this.detailScroll + visibleLines);
        for (int i = this.detailScroll; i < lastLine; i++) {
            DetailLine line = lines.get(i);
            this.fontRendererObj.drawString(line.text, x + line.indent, lineY, line.color);
            lineY += 10;
        }

        if (maxScroll > 0) {
            String scrollText = (this.detailScroll + 1) + "-" + lastLine + " / " + lines.size();
            this.fontRendererObj.drawString(scrollText, x + width - this.fontRendererObj.getStringWidth(scrollText), y + height - 10, 0x777777);
        }
    }

    private List<DetailLine> buildDetailLines(LostTalesQuestDefinition quest, int width) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        LostTalesQuestProgress progress = LostTalesClientQuestProgressStore.getActiveQuest(quest.getId());
        boolean completed = LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());

        addLine(lines, quest.getTitle(), 0xFFD37A, 0);
        addLine(lines, quest.getId(), 0x777777, 0);
        addLine(lines, getQuestStatusLabel(quest), getQuestStatusColor(quest), 0);
        addLine(lines, quest.isRepeatable() ? "Repeatable" : "One-time", quest.isRepeatable() ? 0x77DD77 : 0xAAAAAA, 0);
        addLine(lines, "Start: " + getStartModeDescription(quest), quest.canStartFromJournal() ? 0xAAAAAA : 0xDD9977, 0);

        if (LostTalesClientQuestProgressStore.isQuestPinned(quest.getId())) {
            addLine(lines, "Tracked on HUD", 0xDDBBFF, 0);
        }
        String pinnedMarkerId = LostTalesClientQuestProgressStore.getPinnedMapMarkerId();
        if (pinnedMarkerId.length() > 0) {
            addWrappedLines(lines, "Tracked marker: " + describeMarkerId(pinnedMarkerId), 0xDDBBFF, 0, width);
        }

        if (progress != null) {
            addLine(lines, "Current stage: " + readableStageName(progress), 0xAADDFF, 0);
        } else if (completed) {
            addLine(lines, "Completed on this character", 0x77DD77, 0);
        }
        addBlankLine(lines);

        addWrappedLines(lines, quest.getDescription(), 0xCCCCCC, 0, width);
        addBlankLine(lines);

        addMapSection(lines, "Prerequisites", quest.getPrerequisites(), width);
        addMapSection(lines, "Rewards", quest.getRewards(), width);
        addMapSection(lines, "Interaction start", quest.getInteraction(), width);
        addMarkerSection(lines, quest, width);
        addJournalLogSection(lines, quest, width);
        addStagesSection(lines, quest, progress, completed, width);

        return lines;
    }

    private void addMapSection(List<DetailLine> lines, String title, Map<String, String> values, int width) {
        if (values == null || values.isEmpty()) {
            return;
        }
        addSectionTitle(lines, title);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            addWrappedLines(lines, entry.getKey() + ": " + entry.getValue(), 0xAAAAAA, 8, width - 8);
        }
        addBlankLine(lines);
    }

    private void addMarkerSection(List<DetailLine> lines, LostTalesQuestDefinition quest, int width) {
        if (quest.getMarkers().isEmpty()) {
            return;
        }
        addSectionTitle(lines, "Map marker hints");
        for (Map.Entry<String, String> entry : quest.getMarkers().entrySet()) {
            List<String> markerIds = getMarkerIds(entry.getValue());
            if (markerIds.isEmpty()) {
                addWrappedLines(lines, entry.getKey() + ": none", 0xAAAAAA, 8, width - 8);
                continue;
            }
            for (String markerId : markerIds) {
                int color = LostTalesClientQuestProgressStore.isMapMarkerPinned(markerId) ? 0xDDBBFF : LostTalesClientQuestProgressStore.isMarkerDiscovered(markerId) ? 0xAADDFF : 0x777777;
                addWrappedLines(lines, entry.getKey() + ": " + describeMarkerId(markerId, LostTalesQuestMarkerHelper.isDynamicQuestGiverMarkerKey(entry.getKey())), color, 8, width - 8);
            }
        }
        addBlankLine(lines);
    }

    private void addJournalLogSection(List<DetailLine> lines, LostTalesQuestDefinition quest, int width) {
        if (quest.getJournalLog().isEmpty()) {
            return;
        }
        addSectionTitle(lines, "Journal log");
        for (Map.Entry<String, String> entry : quest.getJournalLog().entrySet()) {
            addWrappedLines(lines, "Stage " + entry.getKey() + ": " + entry.getValue(), 0xAAAAAA, 8, width - 8);
        }
        addBlankLine(lines);
    }

    private void addStagesSection(List<DetailLine> lines, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed, int width) {
        addSectionTitle(lines, "Stages");
        if (quest.getStages().isEmpty()) {
            addLine(lines, "No stages defined.", 0xAAAAAA, 8);
            return;
        }

        for (int i = 0; i < quest.getStages().size(); i++) {
            LostTalesQuestStageDefinition stage = quest.getStages().get(i);
            boolean currentStage = isCurrentStage(progress, stage, i);
            int stageColor = currentStage ? 0xAADDFF : completed ? 0x77DD77 : 0xFFFFFF;
            String stageLabel = (currentStage ? "> " : "") + "Stage " + stage.getId();
            addLine(lines, stageLabel, stageColor, 4);

            if (stage.getObjectives().isEmpty()) {
                addLine(lines, "- No objectives", 0x888888, 14);
            } else {
                for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                    String line = buildObjectiveLine(progress, objective, currentStage, completed);
                    int objectiveColor = getObjectiveColor(progress, objective, currentStage, completed);
                    addWrappedLines(lines, line, objectiveColor, 14, width - 14);
                }
            }
            addBlankLine(lines);
        }
    }

    private String buildObjectiveLine(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, currentStage, questCompleted, true, true);
    }

    private int getObjectiveColor(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        int target = getObjectiveTargetCount(objective);
        int current = questCompleted ? target : currentStage && progress != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        if (current >= target) {
            return 0x77DD77;
        }
        return currentStage ? 0xDDDDDD : 0xAAAAAA;
    }

    private int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
    }

    private String getQuestStatusLabel(LostTalesQuestDefinition quest) {
        if (LostTalesClientQuestProgressStore.isQuestPinned(quest.getId())) {
            return "Tracked";
        }
        if (LostTalesClientQuestProgressStore.isQuestActive(quest.getId())) {
            return "Active";
        }
        if (LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId())) {
            return quest.isRepeatable() ? "Completed / Repeatable" : "Completed";
        }
        if (quest.canStartFromJournal()) {
            return "Available";
        }
        if (quest.canStartFromItem()) {
            return "Use Item";
        }
        if (quest.canStartFromInteraction()) {
            return "Find NPC/Block";
        }
        return "Locked";
    }

    private int getQuestStatusColor(LostTalesQuestDefinition quest) {
        if (LostTalesClientQuestProgressStore.isQuestPinned(quest.getId())) {
            return 0xDDBBFF;
        }
        if (LostTalesClientQuestProgressStore.isQuestActive(quest.getId())) {
            return 0xAADDFF;
        }
        if (LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId())) {
            return 0x77DD77;
        }
        if (quest.canStartFromJournal()) {
            return 0xAAAAAA;
        }
        if (quest.canStartFromItem() || quest.canStartFromInteraction()) {
            return 0xDD9977;
        }
        return 0x777777;
    }

    private boolean isCurrentStage(LostTalesQuestProgress progress, LostTalesQuestStageDefinition stage, int index) {
        if (progress == null) {
            return false;
        }
        return progress.getStageIndex() == index || (stage.getId() != null && stage.getId().equals(progress.getStageId()));
    }

    private String readableStageName(LostTalesQuestProgress progress) {
        if (progress.getStageId() != null && progress.getStageId().length() > 0) {
            return progress.getStageId();
        }
        return String.valueOf(progress.getStageIndex());
    }

    private List<LostTalesQuestDefinition> getVisibleQuests() {
        List<LostTalesQuestDefinition> allQuests = LostTalesClientQuestDefinitionStore.getQuests();
        if (this.filterMode == FILTER_ALL) {
            return allQuests;
        }

        List<LostTalesQuestDefinition> visible = new ArrayList<LostTalesQuestDefinition>();
        for (LostTalesQuestDefinition quest : allQuests) {
            if (quest == null) {
                continue;
            }
            if (this.filterMode == FILTER_ACTIVE && LostTalesClientQuestProgressStore.isQuestActive(quest.getId())) {
                visible.add(quest);
            } else if (this.filterMode == FILTER_STARTABLE && isQuestStartableNow(quest)) {
                visible.add(quest);
            } else if (this.filterMode == FILTER_COMPLETED && LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId())) {
                visible.add(quest);
            }
        }
        return visible;
    }

    private boolean isQuestStartableNow(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return false;
        }
        boolean active = LostTalesClientQuestProgressStore.isQuestActive(quest.getId());
        boolean completed = LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());
        return quest.canStartFromJournal() && !active && (!completed || quest.isRepeatable());
    }

    private String getQuestListHeading() {
        String label = "Loaded Quests";
        if (this.filterMode == FILTER_ACTIVE) {
            label = "Active Quests";
        } else if (this.filterMode == FILTER_STARTABLE) {
            label = "Can Start";
        } else if (this.filterMode == FILTER_COMPLETED) {
            label = "Completed Quests";
        }
        int count = getVisibleQuests().size();
        return label + " (" + count + ")";
    }

    private String getFilterButtonLabel() {
        if (this.filterMode == FILTER_ACTIVE) {
            return "Filter: Active";
        }
        if (this.filterMode == FILTER_STARTABLE) {
            return "Filter: Can Start";
        }
        if (this.filterMode == FILTER_COMPLETED) {
            return "Filter: Completed";
        }
        return "Filter: All";
    }

    private String getStartModeDescription(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return "unknown";
        }
        if (quest.canStartFromJournal()) {
            return "Journal";
        }
        if (quest.canStartFromItem()) {
            return "Item";
        }
        if (quest.canStartFromInteraction()) {
            return "NPC / block interaction";
        }
        return "Locked / command";
    }

    private void addSectionTitle(List<DetailLine> lines, String text) {
        addLine(lines, text, 0xFFD37A, 0);
    }

    private void addBlankLine(List<DetailLine> lines) {
        lines.add(new DetailLine("", 0xFFFFFF, 0));
    }

    private void addLine(List<DetailLine> lines, String text, int color, int indent) {
        if (text == null) {
            text = "";
        }
        lines.add(new DetailLine(text, color, Math.max(0, indent)));
    }

    private void addWrappedLines(List<DetailLine> lines, String text, int color, int indent, int width) {
        if (text == null || text.length() == 0) {
            return;
        }
        int wrapWidth = Math.max(20, width - Math.max(0, indent));
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text, wrapWidth);
        for (String line : wrapped) {
            addLine(lines, line, color, indent);
        }
    }

    private static final class DetailLine {
        private final String text;
        private final int color;
        private final int indent;

        private DetailLine(String text, int color, int indent) {
            this.text = text == null ? "" : text;
            this.color = color;
            this.indent = indent;
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
                this.fontRendererObj.drawString("...", x, lineY, color);
                return bottom;
            }
            this.fontRendererObj.drawString(line, x, lineY, color);
            lineY += 10;
        }
        return lineY;
    }

    private int drawBoundedLine(String text, int x, int y, int width, int color, int bottom) {
        if (y + 10 > bottom) {
            this.fontRendererObj.drawString("...", x, bottom - 10, color);
            return -1;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        for (String line : lines) {
            if (y + 10 > bottom) {
                this.fontRendererObj.drawString("...", x, bottom - 10, color);
                return -1;
            }
            this.fontRendererObj.drawString(line, x, y, color);
            y += 10;
        }
        return y;
    }

    private void drawPanel(int x, int y, int width, int height, int fillColor, int borderColor) {
        drawRect(x, y, x + width, y + height, fillColor);
        drawRect(x, y, x + width, y + 1, borderColor);
        drawRect(x, y + height - 1, x + width, y + height, borderColor);
        drawRect(x, y, x + 1, y + height, borderColor);
        drawRect(x + width - 1, y, x + width, y + height, borderColor);
    }

    private String trimToWidth(String text, int width) {
        if (text == null || width <= 0) return "";
        if (this.fontRendererObj.getStringWidth(text) <= width) return text;
        String ellipsis = "...";
        int ellipsisWidth = this.fontRendererObj.getStringWidth(ellipsis);
        String trimmed = text;
        while (trimmed.length() > 0 && this.fontRendererObj.getStringWidth(trimmed) + ellipsisWidth > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            List<LostTalesQuestDefinition> quests = getVisibleQuests();
            int listX = OUTER_PADDING;
            int listY = 38 + 16;
            int listHeight = Math.max(80, this.height - 108) - 20;
            if (mouseX >= listX && mouseX < listX + LIST_WIDTH && mouseY >= listY && mouseY < listY + listHeight) {
                int row = (mouseY - listY) / ROW_HEIGHT;
                int index = this.listScroll + row;
                if (index >= 0 && index < quests.size()) {
                    setSelectedQuestIndex(index);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (isMouseOverDetails(this.lastMouseX, this.lastMouseY)) {
                scrollDetails(wheel > 0 ? -3 : 3);
            } else {
                if (wheel > 0) {
                    this.listScroll--;
                } else {
                    this.listScroll++;
                }
                clampSelectionAndScroll();
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        if (button.id == BUTTON_FILTER) {
            this.filterMode = (this.filterMode + 1) % FILTER_COUNT;
            this.filterButton.displayString = getFilterButtonLabel();
            setSelectedQuestIndex(0);
            clampSelectionAndScroll();
            return;
        }
        if (button.id == BUTTON_SCAN) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_SCAN, ""));
            return;
        }

        LostTalesQuestDefinition selectedQuest = getSelectedQuest();
        if (selectedQuest == null) {
            return;
        }
        if (button.id == BUTTON_START) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_START, selectedQuest.getId()));
        } else if (button.id == BUTTON_ABANDON) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_ABANDON, selectedQuest.getId()));
        } else if (button.id == BUTTON_PIN) {
            if (LostTalesClientQuestProgressStore.isQuestPinned(selectedQuest.getId())) {
                LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_UNPIN, ""));
            } else {
                LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_PIN, selectedQuest.getId()));
            }
        } else if (button.id == BUTTON_MARKER) {
            String markerId = getBestMarkerId(selectedQuest);
            if (markerId.length() == 0) {
                return;
            }
            if (!areQuestMarkersDiscovered(selectedQuest)) {
                LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_REVEAL_MARKERS, selectedQuest.getId()));
            } else if (LostTalesClientQuestProgressStore.isMapMarkerPinned(markerId)) {
                LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_UNPIN_MARKER, ""));
            } else {
                LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_PIN_MARKER, markerId));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_J) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_CAPITAL) {
            this.mc.displayGuiScreen(new LostTalesCharacterMenuGui(this.parent));
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
            scrollDetails(-8);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollDetails(8);
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
        if (keyCode == Keyboard.KEY_TAB) {
            this.filterMode = (this.filterMode + 1) % FILTER_COUNT;
            if (this.filterButton != null) {
                this.filterButton.displayString = getFilterButtonLabel();
            }
            setSelectedQuestIndex(0);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void setSelectedQuestIndex(int index) {
        if (index != this.selectedQuestIndex) {
            this.detailScroll = 0;
        }
        this.selectedQuestIndex = index;
        clampSelectionAndScroll();
    }

    private LostTalesQuestDefinition getSelectedQuest() {
        List<LostTalesQuestDefinition> quests = getVisibleQuests();
        if (quests.isEmpty() || this.selectedQuestIndex < 0 || this.selectedQuestIndex >= quests.size()) {
            return null;
        }
        return quests.get(this.selectedQuestIndex);
    }

    private String describeMarkerValue(String value) {
        List<String> markerIds = getMarkerIds(value);
        if (markerIds.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (String markerId : markerIds) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(describeMarkerId(markerId));
        }
        return builder.toString();
    }

    private String describeMarkerId(String markerId) {
        return describeMarkerId(markerId, false);
    }

    private String describeMarkerId(String markerId, boolean dynamicQuestGiverHint) {
        if (markerId == null || markerId.trim().length() == 0) {
            return "none";
        }
        markerId = markerId.trim();
        LostTalesMapMarkerData marker = LostTalesClientMapMarkerStore.getSharedMarker(markerId);
        StringBuilder builder = new StringBuilder();
        if (marker != null && marker.getName() != null && marker.getName().length() > 0) {
            builder.append(marker.getName()).append(" (").append(markerId).append(")");
        } else {
            builder.append(markerId).append(dynamicQuestGiverHint ? " (dynamic quest-giver marker, not discovered yet)" : " (missing marker JSON)");
        }

        if (LostTalesClientQuestProgressStore.isMapMarkerPinned(markerId)) {
            builder.append(" - tracked");
        } else if (LostTalesClientQuestProgressStore.isMarkerDiscovered(markerId)) {
            builder.append(" - discovered");
        } else {
            builder.append(" - hidden");
        }

        if (marker != null) {
            builder.append(" @ ")
                    .append((int)Math.round(marker.getX())).append(", ")
                    .append((int)Math.round(marker.getY())).append(", ")
                    .append((int)Math.round(marker.getZ()));
            builder.append(" dim ").append(marker.getDimensionId());
        }
        return builder.toString();
    }

    private List<String> getMarkerIds(String value) {
        List<String> markerIds = new ArrayList<String>();
        if (value == null || value.trim().length() == 0) {
            return markerIds;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            String markerId = part == null ? "" : part.trim();
            if (markerId.length() > 0) {
                markerIds.add(markerId);
            }
        }
        return markerIds;
    }

    private List<String> getQuestMarkerIds(LostTalesQuestDefinition quest) {
        List<String> markerIds = new ArrayList<String>();
        if (quest == null || quest.getMarkers().isEmpty()) {
            return markerIds;
        }
        for (String value : quest.getMarkers().values()) {
            markerIds.addAll(getMarkerIds(value));
        }
        return markerIds;
    }

    private String getBestMarkerId(LostTalesQuestDefinition quest) {
        List<String> markerIds = getQuestMarkerIds(quest);
        if (markerIds.isEmpty()) {
            return "";
        }
        for (String markerId : markerIds) {
            if (LostTalesClientQuestProgressStore.isMapMarkerPinned(markerId)) {
                return markerId;
            }
        }
        for (String markerId : markerIds) {
            if (LostTalesClientQuestProgressStore.isMarkerDiscovered(markerId)) {
                return markerId;
            }
        }
        return markerIds.get(0);
    }

    private boolean areQuestMarkersDiscovered(LostTalesQuestDefinition quest) {
        List<String> markerIds = getQuestMarkerIds(quest);
        if (markerIds.isEmpty()) {
            return false;
        }
        for (String markerId : markerIds) {
            if (!LostTalesClientQuestProgressStore.isMarkerDiscovered(markerId)) {
                return false;
            }
        }
        return true;
    }

    private void updateActionButtons(List<LostTalesQuestDefinition> quests) {
        if (this.startButton == null || this.abandonButton == null || this.scanButton == null || this.pinButton == null || this.markerButton == null) {
            return;
        }
        if (this.filterButton != null) {
            this.filterButton.displayString = getFilterButtonLabel();
        }

        LostTalesQuestDefinition quest = getSelectedQuest();
        boolean hasQuest = quest != null;
        boolean active = hasQuest && LostTalesClientQuestProgressStore.isQuestActive(quest.getId());
        boolean completed = hasQuest && LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId());
        boolean canStart = hasQuest && isQuestStartableNow(quest);
        String markerId = hasQuest ? getBestMarkerId(quest) : "";
        boolean hasMarker = markerId.length() > 0;
        boolean markersDiscovered = hasQuest && areQuestMarkersDiscovered(quest);

        if (!hasQuest) {
            this.startButton.displayString = "Start";
            this.startButton.enabled = false;
            this.abandonButton.enabled = false;
            this.pinButton.displayString = "Track";
            this.pinButton.enabled = false;
            this.markerButton.displayString = "No Marker";
            this.markerButton.enabled = false;
            this.scanButton.enabled = LostTalesClientQuestProgressStore.hasAnyState();
            return;
        }

        if (completed && quest.isRepeatable()) {
            this.startButton.displayString = "Restart";
        } else if (!quest.canStartFromJournal() && quest.canStartFromItem()) {
            this.startButton.displayString = "Use Item";
        } else if (!quest.canStartFromJournal() && quest.canStartFromInteraction()) {
            this.startButton.displayString = "Find Start";
        } else if (!quest.canStartFromJournal()) {
            this.startButton.displayString = "Locked";
        } else {
            this.startButton.displayString = "Start";
        }
        this.startButton.enabled = canStart;
        this.abandonButton.enabled = active;
        this.pinButton.displayString = LostTalesClientQuestProgressStore.isQuestPinned(quest.getId()) ? "Untrack" : "Track";
        this.pinButton.enabled = active;
        this.scanButton.enabled = LostTalesClientQuestProgressStore.hasAnyState();

        if (!hasMarker) {
            this.markerButton.displayString = "No Marker";
            this.markerButton.enabled = false;
        } else if (!markersDiscovered) {
            this.markerButton.displayString = "Reveal Markers";
            this.markerButton.enabled = active || completed;
        } else if (LostTalesClientQuestProgressStore.isMapMarkerPinned(markerId)) {
            this.markerButton.displayString = "Untrack Marker";
            this.markerButton.enabled = true;
        } else {
            this.markerButton.displayString = "Track Marker";
            this.markerButton.enabled = true;
        }
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

        int visibleRows = Math.max(1, (this.height - 128) / ROW_HEIGHT);
        int maxScroll = Math.max(0, quests.size() - visibleRows);
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.selectedQuestIndex < this.listScroll) {
            this.listScroll = this.selectedQuestIndex;
        }
        if (this.selectedQuestIndex >= this.listScroll + visibleRows) {
            this.listScroll = this.selectedQuestIndex - visibleRows + 1;
        }
        int maxDetailScroll = getDetailMaxScroll();
        if (this.detailScroll > maxDetailScroll) {
            this.detailScroll = maxDetailScroll;
        }
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
    }

    private boolean isMouseOverDetails(int mouseX, int mouseY) {
        int detailsX = OUTER_PADDING + LIST_WIDTH + 14;
        int detailsY = 38;
        int detailsWidth = this.width - detailsX - OUTER_PADDING;
        int detailsHeight = Math.max(80, this.height - 108);
        return mouseX >= detailsX && mouseX < detailsX + detailsWidth && mouseY >= detailsY && mouseY < detailsY + detailsHeight;
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

    private int getDetailMaxScroll() {
        LostTalesQuestDefinition quest = getSelectedQuest();
        if (quest == null || this.fontRendererObj == null) {
            return 0;
        }
        int detailsX = OUTER_PADDING + LIST_WIDTH + 14;
        int detailsWidth = this.width - detailsX - OUTER_PADDING;
        int detailsHeight = Math.max(80, this.height - 108);
        int visibleLines = Math.max(1, (detailsHeight - 8) / 10);
        return Math.max(0, buildDetailLines(quest, detailsWidth).size() - visibleLines);
    }
}
