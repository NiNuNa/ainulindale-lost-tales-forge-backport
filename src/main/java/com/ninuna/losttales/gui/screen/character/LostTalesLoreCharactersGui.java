package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Collections;
import java.util.List;

/** Browsable server-synchronized lore-character definitions and ownership. */
public final class LostTalesLoreCharactersGui extends GuiScreen {

    private static final int BUTTON_ACTION = 1;
    private static final int BUTTON_SLOT_PREVIOUS = 2;
    private static final int BUTTON_SLOT_NEXT = 3;
    private static final int BUTTON_BACK = 4;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 22;

    private final GuiScreen parent;
    private int selectedIndex;
    private int scrollOffset;
    private int targetSlot;
    private int pendingRequestId;
    private String status = "";
    private boolean statusError;
    private int listX;
    private int listY;
    private int listWidth;
    private int visibleRows = MAX_VISIBLE_ROWS;
    private GuiButton actionButton;
    private GuiButton previousSlotButton;
    private GuiButton nextSlotButton;

    public LostTalesLoreCharactersGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override public void initGui() {
        this.buttonList.clear();
        int y = this.height - 32;
        this.actionButton = new GuiButton(BUTTON_ACTION,
                this.width / 2 - 54, y, 108, 20, "");
        this.previousSlotButton = new GuiButton(BUTTON_SLOT_PREVIOUS,
                this.width / 2 - 132, y, 34, 20, "<");
        this.nextSlotButton = new GuiButton(BUTTON_SLOT_NEXT,
                this.width / 2 + 98, y, 34, 20, ">");
        this.buttonList.add(this.actionButton);
        this.buttonList.add(this.previousSlotButton);
        this.buttonList.add(this.nextSlotButton);
        this.buttonList.add(new GuiButton(BUTTON_BACK, 8, y, 80, 20,
                I18n.format("gui.back")));
        chooseFirstEmptySlot();
        clampSelection();
        updateButtons();
    }

    @Override public void updateScreen() {
        if (this.pendingRequestId != 0
                && !ClientCharacterRosterCache.isRequestPending(
                this.pendingRequestId)) {
            CharacterOperationFeedback feedback =
                    ClientCharacterRosterCache.getOperation(
                            this.pendingRequestId);
            int completed = this.pendingRequestId;
            this.pendingRequestId = 0;
            ClientCharacterRosterCache.clearOperation(completed);
            if (feedback != null) {
                this.statusError = !feedback.isSuccessful();
                this.status = feedback.isSuccessful()
                        ? ClientCharacterDisplayNames.operationSuccess(
                        feedback.getOperationType().getId())
                        : ClientCharacterDisplayNames.error(feedback);
            }
        }
        clampSelection();
        updateButtons();
    }

    private void updateButtons() {
        LoreCharacterSummary selected = selected();
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        LoreCharacterSnapshot lore = ClientLoreCharacterCache.getSnapshot();
        boolean pending = this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(
                this.pendingRequestId);
        boolean slotAvailable = roster != null
                && CharacterRoster.isValidSlotIndex(this.targetSlot)
                && roster.getSlotState(this.targetSlot)
                == CharacterSlotState.UNLOCKED;
        boolean active = selected != null && roster != null
                && selected.getOwnedCharacterId() != null
                && selected.getOwnedCharacterId().equals(
                roster.getActiveCharacterId());

        if (selected != null && selected.isOwnedByViewer()) {
            this.actionButton.displayString = active
                    ? I18n.format("gui.losttales.lore.active")
                    : I18n.format("gui.losttales.lore.release");
            this.actionButton.enabled = !pending && !active
                    && lore != null && lore.canMutate()
                    && !selected.isTransferInProgress();
        } else {
            this.actionButton.displayString = I18n.format(
                    "gui.losttales.lore.claim");
            this.actionButton.enabled = !pending && slotAvailable
                    && lore != null && lore.canMutate()
                    && selected != null && selected.isAvailable();
        }
        boolean choosingSlot = selected != null
                && !selected.isOwnedByViewer();
        this.previousSlotButton.enabled = choosingSlot && !pending
                && findEmptySlot(-1) >= 0;
        this.nextSlotButton.enabled = choosingSlot && !pending
                && findEmptySlot(1) >= 0;
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == BUTTON_SLOT_PREVIOUS) {
            int slot = findEmptySlot(-1);
            if (slot >= 0) this.targetSlot = slot;
            return;
        }
        if (button.id == BUTTON_SLOT_NEXT) {
            int slot = findEmptySlot(1);
            if (slot >= 0) this.targetSlot = slot;
            return;
        }
        if (button.id != BUTTON_ACTION || this.pendingRequestId != 0) return;
        LoreCharacterSummary selected = selected();
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (selected == null || roster == null) return;
        this.status = I18n.format("gui.losttales.lore.processing");
        this.statusError = false;
        this.pendingRequestId = selected.isOwnedByViewer()
                ? ClientCharacterNetwork.releaseLoreCharacter(
                roster.getRevision(), selected.getOwnershipRevision(),
                selected.getId())
                : ClientCharacterNetwork.claimLoreCharacter(
                roster.getRevision(), selected.getOwnershipRevision(),
                this.targetSlot, selected.getId());
    }

    @Override public void drawScreen(
            int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.lore.title"),
                I18n.format("gui.losttales.lore.subtitle"),
                this.width, 10);
        int panelWidth = Math.min(680, this.width - 24);
        int panelHeight = Math.max(150, this.height - 84);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = 40;
        LostTalesSkyrimUiStyle.drawPanel(
                panelX, panelY, panelWidth, panelHeight);
        this.listX = panelX + 10;
        this.listY = panelY + 12;
        this.listWidth = Math.min(210, panelWidth / 3);
        this.visibleRows = Math.max(4, Math.min(MAX_VISIBLE_ROWS,
                (panelHeight - 24) / ROW_HEIGHT));
        drawList(mouseX, mouseY);
        drawDetails(panelX + this.listWidth + 22, panelY + 12,
                panelWidth - this.listWidth - 32, panelHeight - 24);
        if (this.status.length() > 0) {
            drawCenteredString(this.fontRendererObj, this.status,
                    this.width / 2, this.height - 45,
                    this.statusError ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawList(int mouseX, int mouseY) {
        List<LoreCharacterSummary> characters = characters();
        for (int row = 0; row < this.visibleRows; row++) {
            int index = this.scrollOffset + row;
            int y = this.listY + row * ROW_HEIGHT;
            if (index >= characters.size()) break;
            LoreCharacterSummary character = characters.get(index);
            boolean selected = index == this.selectedIndex;
            boolean hovered = mouseX >= this.listX
                    && mouseX < this.listX + this.listWidth
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            Gui.drawRect(this.listX, y, this.listX + this.listWidth,
                    y + ROW_HEIGHT - 2,
                    selected ? LostTalesSkyrimUiStyle.PANEL_SELECTED
                            : hovered ? LostTalesSkyrimUiStyle.PANEL_HOVER
                            : LostTalesSkyrimUiStyle.PANEL_FILL_SOFT);
            int color = character.isOwnedByViewer()
                    ? LostTalesSkyrimUiStyle.GOLD
                    : character.isAvailable()
                    ? LostTalesSkyrimUiStyle.GREEN
                    : LostTalesSkyrimUiStyle.TEXT_MUTED;
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            character.getName(), this.listWidth - 12),
                    this.listX + 5, y + 6, color);
        }
    }

    private void drawDetails(int x, int y, int width, int height) {
        LoreCharacterSummary character = selected();
        if (character == null) {
            this.fontRendererObj.drawStringWithShadow(
                    I18n.format("gui.losttales.lore.loading"), x, y,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        this.fontRendererObj.drawStringWithShadow(character.getName(), x, y,
                LostTalesSkyrimUiStyle.GOLD);
        int line = y + 15;
        drawLine(x, line, width, I18n.format("gui.losttales.lore.race",
                ClientCharacterDisplayNames.race(character.getRaceId())));
        line += 11;
        drawLine(x, line, width, I18n.format("gui.losttales.lore.model",
                character.getModelId().length() == 0 ? "-"
                        : character.getModelId()));
        line += 11;
        drawLine(x, line, width, I18n.format("gui.losttales.lore.skin",
                character.getSkinId().length() == 0 ? "-"
                        : character.getSkinId()));
        line += 14;
        String availability = availability(character);
        this.fontRendererObj.drawStringWithShadow(availability, x, line,
                character.isAvailable() || character.isOwnedByViewer()
                        ? LostTalesSkyrimUiStyle.GREEN
                        : LostTalesSkyrimUiStyle.TEXT_MUTED);
        line += 17;
        if (character.isConfigured()) {
            CharacterRaceGameplayProfile profile =
                    ClientCharacterRaceAttributes.resolve(
                            this.mc.theWorld, character.getRaceId());
            drawLine(x, line, width,
                    I18n.format("gui.losttales.lore.statistics",
                            ClientCharacterRaceAttributes.formatHealth(profile),
                            ClientCharacterRaceAttributes.formatMovementSpeed(profile),
                            ClientCharacterRaceAttributes.formatAttackDamage(profile)));
            line += 15;
        }
        this.fontRendererObj.drawSplitString(character.getDescription(),
                x, line, width, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (!character.isOwnedByViewer() && roster != null) {
            String slot = CharacterRoster.isValidSlotIndex(this.targetSlot)
                    ? I18n.format("gui.losttales.character.slot",
                    Integer.valueOf(this.targetSlot + 1))
                    : I18n.format("gui.losttales.lore.no_slot");
            drawCenteredString(this.fontRendererObj,
                    I18n.format("gui.losttales.lore.target_slot", slot),
                    x + width / 2, y + height - 10,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
    }

    private String availability(LoreCharacterSummary character) {
        if (!character.isConfigured()) {
            return I18n.format("gui.losttales.lore.not_configured");
        }
        if (character.isTransferInProgress()) {
            return I18n.format("gui.losttales.lore.transfer_pending");
        }
        if (character.isOwnedByViewer()) {
            return I18n.format("gui.losttales.lore.owned_by_you");
        }
        if (character.isClaimed()) {
            return character.getOwnerName().length() == 0
                    ? I18n.format("gui.losttales.lore.claimed")
                    : I18n.format("gui.losttales.lore.owned_by",
                    character.getOwnerName());
        }
        return I18n.format("gui.losttales.lore.available");
    }

    private void drawLine(int x, int y, int width, String value) {
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, value, width),
                x, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && mouseX >= this.listX
                && mouseX < this.listX + this.listWidth
                && mouseY >= this.listY
                && mouseY < this.listY + this.visibleRows * ROW_HEIGHT) {
            int index = this.scrollOffset
                    + (mouseY - this.listY) / ROW_HEIGHT;
            if (index >= 0 && index < characters().size()) {
                this.selectedIndex = index;
                this.status = "";
                updateButtons();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maximum = Math.max(0,
                    characters().size() - this.visibleRows);
            this.scrollOffset = Math.max(0, Math.min(maximum,
                    this.scrollOffset + (wheel < 0 ? 1 : -1)));
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void clampSelection() {
        int size = characters().size();
        if (size == 0) { this.selectedIndex = 0; this.scrollOffset = 0; return; }
        this.selectedIndex = Math.max(0, Math.min(size - 1, this.selectedIndex));
        if (this.selectedIndex < this.scrollOffset) {
            this.scrollOffset = this.selectedIndex;
        } else if (this.selectedIndex
                >= this.scrollOffset + this.visibleRows) {
            this.scrollOffset = this.selectedIndex - this.visibleRows + 1;
        }
    }

    private LoreCharacterSummary selected() {
        List<LoreCharacterSummary> values = characters();
        return this.selectedIndex >= 0 && this.selectedIndex < values.size()
                ? values.get(this.selectedIndex) : null;
    }

    private List<LoreCharacterSummary> characters() {
        LoreCharacterSnapshot snapshot = ClientLoreCharacterCache.getSnapshot();
        return snapshot == null ? Collections.<LoreCharacterSummary>emptyList()
                : snapshot.getCharacters();
    }

    private void chooseFirstEmptySlot() {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null) { this.targetSlot = 0; return; }
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            if (roster.getSlotState(slot) == CharacterSlotState.UNLOCKED) {
                this.targetSlot = slot;
                return;
            }
        }
        this.targetSlot = -1;
    }

    private int findEmptySlot(int direction) {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null || direction == 0) return -1;
        int start = CharacterRoster.isValidSlotIndex(this.targetSlot)
                ? this.targetSlot : direction > 0 ? -1 : CharacterRoster.MAX_SLOTS;
        for (int slot = start + direction;
             slot >= 0 && slot < CharacterRoster.MAX_SLOTS;
             slot += direction) {
            if (roster.getSlotState(slot) == CharacterSlotState.UNLOCKED) return slot;
        }
        return -1;
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
