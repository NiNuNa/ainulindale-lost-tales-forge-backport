package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Client-only roster and character-management view: the Minecraft account
 * as the first playable identity in a row of its own, then the nine
 * character slots. Whichever is being played is marked active, and any
 * other can be selected.
 */
public final class LostTalesCharacterRosterGui extends GuiScreen {

    private static final int BUTTON_PRIMARY = 1;
    private static final int BUTTON_DELETE = 2;
    private static final int BUTTON_BACK = 3;
    private static final int BUTTON_REFRESH = 4;
    private static final int BUTTON_LORE_CHARACTERS = 5;

    /** The account's row stands in for a slot index it does not have. */
    private static final int ACCOUNT_SLOT = -1;
    private static final int ACCOUNT_ROW_HEIGHT = 26;

    private final GuiScreen parent;
    private int selectedSlot = ACCOUNT_SLOT;
    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;

    private int accountX;
    private int accountY;
    private int accountWidth;
    private int gridX;
    private int gridY;
    private int cellWidth;
    private int cellHeight;
    private int gap;

    private GuiButton primaryButton;
    private GuiButton deleteButton;
    private GuiButton refreshButton;

    public LostTalesCharacterRosterGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int y = this.height - 34;
        this.primaryButton = new GuiButton(BUTTON_PRIMARY, this.width / 2 - 186, y, 90, 20, "");
        this.deleteButton = new GuiButton(BUTTON_DELETE, this.width / 2 - 92, y, 90, 20,
                I18n.format("gui.losttales.character.delete"));
        this.buttonList.add(new GuiButton(BUTTON_LORE_CHARACTERS,
                this.width / 2 + 2, y, 90, 20,
                I18n.format("gui.losttales.lore.open")));
        this.refreshButton = new GuiButton(BUTTON_REFRESH, this.width / 2 + 96, y, 90, 20,
                I18n.format("gui.losttales.character.refresh"));
        this.buttonList.add(this.primaryButton);
        this.buttonList.add(this.deleteButton);
        this.buttonList.add(this.refreshButton);
        this.buttonList.add(new GuiButton(BUTTON_BACK, 8, y, 80, 20,
                I18n.format("gui.back")));

        if (ClientCharacterRosterCache.getState() == ClientCharacterRosterCache.SyncState.UNKNOWN
                || ClientCharacterRosterCache.getState() == ClientCharacterRosterCache.SyncState.ERROR) {
            this.pendingRequestId = ClientCharacterNetwork.requestRoster();
        }
        ensureSelection();
        updateButtonState();
    }

    @Override
    public void updateScreen() {
        handlePendingOperation();
        ensureSelection();
        updateButtonState();
    }

    private void handlePendingOperation() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(this.pendingRequestId)) {
            return;
        }
        CharacterOperationFeedback feedback = ClientCharacterRosterCache.getOperation(this.pendingRequestId);
        int completedRequest = this.pendingRequestId;
        this.pendingRequestId = 0;
        if (feedback == null) {
            return;
        }
        ClientCharacterRosterCache.clearOperation(completedRequest);
        if (!feedback.isSuccessful()) {
            this.statusMessage = ClientCharacterDisplayNames.error(feedback);
            this.statusError = true;
            return;
        }
        this.statusMessage = ClientCharacterDisplayNames.operationSuccess(
                feedback.getOperationType().getId());
        this.statusError = false;
    }

    private void ensureSelection() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || this.selectedSlot == ACCOUNT_SLOT) {
            return;
        }
        if (!CharacterRoster.isValidSlotIndex(this.selectedSlot)
                || snapshot.getSlotState(this.selectedSlot) == CharacterSlotState.HIDDEN) {
            CharacterSummary active = snapshot.getActiveCharacter();
            this.selectedSlot = active == null ? ACCOUNT_SLOT : active.getSlotIndex();
        }
    }

    private boolean isAccountSelected() {
        return this.selectedSlot == ACCOUNT_SLOT;
    }

    private CharacterSummary selectedCharacter(CharacterRosterSnapshot snapshot) {
        return snapshot == null || isAccountSelected()
                ? null : snapshot.getCharacterAtSlot(this.selectedSlot);
    }

    private CharacterSlotState selectedSlotState(CharacterRosterSnapshot snapshot) {
        return snapshot == null || !CharacterRoster.isValidSlotIndex(this.selectedSlot)
                ? CharacterSlotState.HIDDEN : snapshot.getSlotState(this.selectedSlot);
    }

    private void updateButtonState() {
        if (this.primaryButton == null) {
            return;
        }
        boolean pending = this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(this.pendingRequestId);
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary selected = selectedCharacter(snapshot);
        CharacterSlotState state = selectedSlotState(snapshot);

        if (isAccountSelected() && snapshot != null) {
            boolean onAccount = snapshot.getActiveCharacterId() == null;
            this.primaryButton.displayString = I18n.format(onAccount
                    ? "gui.losttales.character.active"
                    : "gui.losttales.character.select_account");
            this.primaryButton.enabled = !onAccount && !pending;
        } else if (state == CharacterSlotState.UNLOCKED) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.create");
            this.primaryButton.enabled = !pending;
        } else if (selected != null && selected.getCharacterId().equals(snapshot.getActiveCharacterId())) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.active");
            this.primaryButton.enabled = false;
        } else if (selected != null) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.select");
            this.primaryButton.enabled = !pending;
        } else {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.unavailable");
            this.primaryButton.enabled = false;
        }
        this.deleteButton.enabled = selected != null && !pending
                && ClientLoreCharacterCache.findOwnedCharacter(
                selected.getCharacterId()) == null;
        this.refreshButton.enabled = !pending;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == BUTTON_REFRESH) {
            this.statusMessage = "";
            this.pendingRequestId = ClientCharacterNetwork.requestRoster();
            return;
        }
        if (button.id == BUTTON_LORE_CHARACTERS) {
            this.mc.displayGuiScreen(new LostTalesLoreCharactersGui(this));
            return;
        }
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        CharacterSummary selected = selectedCharacter(snapshot);
        CharacterSlotState state = selectedSlotState(snapshot);
        if (button.id == BUTTON_PRIMARY) {
            if (isAccountSelected()) {
                if (snapshot.getActiveCharacterId() != null) {
                    this.statusMessage = I18n.format("gui.losttales.character.selecting_account");
                    this.statusError = false;
                    this.pendingRequestId = ClientCharacterNetwork.selectAccount(
                            snapshot.getRevision(), snapshot.getOwnerId());
                }
            } else if (state == CharacterSlotState.UNLOCKED) {
                this.mc.displayGuiScreen(new LostTalesCharacterCreationGui(
                        this, this.selectedSlot));
            } else if (selected != null
                    && !selected.getCharacterId().equals(snapshot.getActiveCharacterId())) {
                this.statusMessage = I18n.format("gui.losttales.character.selecting");
                this.statusError = false;
                this.pendingRequestId = ClientCharacterNetwork.selectCharacter(
                        snapshot.getRevision(), selected.getCharacterId());
            }
            return;
        }
        if (button.id == BUTTON_DELETE && selected != null) {
            this.mc.displayGuiScreen(new LostTalesCharacterDeleteConfirmationGui(this, selected));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.roster"),
                I18n.format("gui.losttales.character.manage_subtitle"),
                this.width, 12);

        int panelWidth = Math.min(560, this.width - 28);
        int panelHeight = Math.max(178 + ACCOUNT_ROW_HEIGHT, this.height - 92);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = 44;
        LostTalesSkyrimUiStyle.drawPanel(panelX, panelY, panelWidth, panelHeight);

        this.gap = 8;
        this.accountX = panelX + 12;
        this.accountY = panelY + 14;
        this.accountWidth = panelWidth - 24;
        this.gridX = panelX + 12;
        this.gridY = this.accountY + ACCOUNT_ROW_HEIGHT + this.gap;
        this.cellWidth = (panelWidth - 24 - this.gap * 2) / 3;
        this.cellHeight = Math.max(38,
                (panelHeight - 70 - ACCOUNT_ROW_HEIGHT - this.gap * 3) / 3);

        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        drawAccountRow(snapshot, this.accountX, this.accountY, this.accountWidth,
                mouseX, mouseY);
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            int column = slot % 3;
            int row = slot / 3;
            int x = this.gridX + column * (this.cellWidth + this.gap);
            int y = this.gridY + row * (this.cellHeight + this.gap);
            drawSlot(snapshot, slot, x, y, mouseX, mouseY);
        }
        drawSelectedDetails(snapshot, panelX + 12, panelY + panelHeight - 39,
                panelWidth - 24);

        if (this.statusMessage.length() > 0) {
            drawCenteredString(this.fontRendererObj, this.statusMessage, this.width / 2,
                    this.height - 48,
                    this.statusError ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSelectedDetails(CharacterRosterSnapshot snapshot, int x, int y, int width) {
        Gui.drawRect(x, y - 5, x + width, y - 4, LostTalesSkyrimUiStyle.BORDER_DIM);
        if (snapshot == null) {
            this.fontRendererObj.drawStringWithShadow(
                    I18n.format("gui.losttales.character.loading_detail"),
                    x, y + 2, LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        if (isAccountSelected()) {
            String lineOne = getAccountName() + "  •  "
                    + ClientCharacterDisplayNames.race(CharacterRaceRegistry.HUMAN) + "  •  "
                    + I18n.format("gui.losttales.character.account_faction_none");
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, lineOne, width),
                    x, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            I18n.format("gui.losttales.character.account_detail"), width),
                    x, y + 11, LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        CharacterSummary character = snapshot.getCharacterAtSlot(this.selectedSlot);
        CharacterSlotState state = snapshot.getSlotState(this.selectedSlot);
        if (character == null) {
            String message = state == CharacterSlotState.UNLOCKED
                    ? I18n.format("gui.losttales.character.empty_detail")
                    : I18n.format("gui.losttales.character.unavailable_detail");
            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, message, width),
                    x, y + 2, LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        String lineOne = character.getName() + "  •  "
                + ClientCharacterDisplayNames.race(character.getRaceId()) + "  •  "
                + ClientCharacterDisplayNames.gender(character.getGenderId()) + "  •  "
                + I18n.format("gui.losttales.character.age_value",
                Integer.valueOf(character.getAge()));
        String lineTwo = ClientCharacterDisplayNames.faction(character.getStartingFactionId())
                + "  •  " + I18n.format("gui.losttales.character.level_short",
                Integer.valueOf(character.getRoleplayLevel()));
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, lineOne, width),
                x, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, lineTwo, width),
                x, y + 11, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    /** The account's row: the identity the player has before any character. */
    private void drawAccountRow(CharacterRosterSnapshot snapshot, int x, int y, int width,
                                int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + ACCOUNT_ROW_HEIGHT;
        drawTileFrame(x, y, width, ACCOUNT_ROW_HEIGHT, isAccountSelected(), hovered);
        this.fontRendererObj.drawStringWithShadow(
                I18n.format("gui.losttales.character.account_tile"), x + 6, y + 4,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (snapshot == null) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.loading"),
                    x + 6, y + 14, LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        boolean active = snapshot.getActiveCharacterId() == null;
        int nameWidth = width / 2 - 12;
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, getAccountName(), nameWidth),
                x + 6, y + 14,
                active ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        String details = ClientCharacterDisplayNames.race(CharacterRaceRegistry.HUMAN)
                + "  •  " + I18n.format("gui.losttales.character.account_faction_none");
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, details, nameWidth - 42),
                x + width / 2, y + 14, LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (active) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.active_marker"),
                    x + width - 42, y + 4, LostTalesSkyrimUiStyle.GREEN);
        }
    }

    private void drawSlot(CharacterRosterSnapshot snapshot, int slot, int x, int y,
                          int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + this.cellWidth
                && mouseY >= y && mouseY < y + this.cellHeight;
        drawTileFrame(x, y, this.cellWidth, this.cellHeight, slot == this.selectedSlot, hovered);

        String slotLabel = I18n.format("gui.losttales.character.slot", Integer.valueOf(slot + 1));
        this.fontRendererObj.drawStringWithShadow(slotLabel, x + 6, y + 5,
                LostTalesSkyrimUiStyle.TEXT_MUTED);

        if (snapshot == null) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.loading"),
                    x + 6, y + 20, LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        CharacterSlotState state = snapshot.getSlotState(slot);
        CharacterSummary character = snapshot.getCharacterAtSlot(slot);
        if (state == CharacterSlotState.HIDDEN) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.hidden"),
                    x + 6, y + 20, LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        if (state == CharacterSlotState.VISIBLE) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.locked"),
                    x + 6, y + 20, LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        if (character == null) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.empty_unlocked"),
                    x + 6, y + 20, LostTalesSkyrimUiStyle.GOLD);
            return;
        }

        boolean active = character.getCharacterId().equals(snapshot.getActiveCharacterId());
        String name = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                character.getName(), this.cellWidth - 12);
        this.fontRendererObj.drawStringWithShadow(name, x + 6, y + 19,
                active ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        String details = ClientCharacterDisplayNames.race(character.getRaceId())
                + "  " + I18n.format("gui.losttales.character.level_short",
                Integer.valueOf(character.getRoleplayLevel()));
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, details, this.cellWidth - 12),
                x + 6, y + 31, LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (active) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.active_marker"),
                    x + this.cellWidth - 42, y + 5, LostTalesSkyrimUiStyle.GREEN);
        }
    }

    /** One tile's fill and border, the same for the account row and every slot. */
    private static void drawTileFrame(int x, int y, int width, int height,
                                      boolean selected, boolean hovered) {
        int fill = selected ? LostTalesSkyrimUiStyle.PANEL_SELECTED
                : hovered ? LostTalesSkyrimUiStyle.PANEL_HOVER
                : LostTalesSkyrimUiStyle.PANEL_FILL_SOFT;
        Gui.drawRect(x, y, x + width, y + height, fill);
        Gui.drawRect(x, y, x + width, y + 1,
                selected ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(x, y, x + 1, y + height, LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(x + width - 1, y, x + width, y + height, LostTalesSkyrimUiStyle.BORDER_DIM);
        Gui.drawRect(x, y + height - 1, x + width, y + height, LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    private String getAccountName() {
        return this.mc == null || this.mc.thePlayer == null
                ? I18n.format("gui.losttales.character.unknown")
                : this.mc.thePlayer.getCommandSenderName();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            if (mouseX >= this.accountX && mouseX < this.accountX + this.accountWidth
                    && mouseY >= this.accountY && mouseY < this.accountY + ACCOUNT_ROW_HEIGHT) {
                this.selectedSlot = ACCOUNT_SLOT;
                this.statusMessage = "";
                updateButtonState();
                return;
            }
            for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
                int column = slot % 3;
                int row = slot / 3;
                int x = this.gridX + column * (this.cellWidth + this.gap);
                int y = this.gridY + row * (this.cellHeight + this.gap);
                if (mouseX >= x && mouseX < x + this.cellWidth
                        && mouseY >= y && mouseY < y + this.cellHeight) {
                    CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
                    if (snapshot != null && snapshot.getSlotState(slot) != CharacterSlotState.HIDDEN) {
                        this.selectedSlot = slot;
                        this.statusMessage = "";
                        updateButtonState();
                    }
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
