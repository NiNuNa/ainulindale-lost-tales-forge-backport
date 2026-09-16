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
import com.ninuna.losttales.client.gui.LostTalesGuiPointerTargets;
import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;

import java.util.UUID;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Client-only roster and character-management view: the account's default
 * character in a row of its own at the top, then the nine character slots.
 * Whichever is being played is marked active, and any other can be
 * selected.
 *
 * <p>The first row is the account's own identity as a character record,
 * not a second thing beside it. It reads from the same summary every
 * other row does, is selected and played the same way, and differs only
 * in that it is always there and the delete button stays off for it.
 * Until a world has minted the record the row falls back to the account's
 * own name and face, which is what it stands for.</p>
 */
public final class LostTalesCharacterRosterGui extends GuiScreen
        implements LostTalesPointerInteractable {

    private static final int BUTTON_PRIMARY = 1;
    private static final int BUTTON_DELETE = 2;
    private static final int BUTTON_BACK = 3;
    private static final int BUTTON_REFRESH = 4;
    private static final int BUTTON_LORE_CHARACTERS = 5;
    private static final int BUTTON_CAPE = 6;

    /** The default character's own slot, outside the nine. */
    private static final int DEFAULT_ROW_SLOT = CharacterRoster.DEFAULT_SLOT_INDEX;
    private static final int DEFAULT_ROW_HEIGHT = 26;
    /** The head drawn at the left of the default character's row. */
    private static final int DEFAULT_HEAD_SIZE = 16;
    /** What {@link #tileAt} answers where there is no tile. */
    private static final int NO_TILE = Integer.MIN_VALUE;

    private static final int PANEL_TOP = 44;
    /** Where the panel starts when the header has to share the room. */
    private static final int PANEL_TOP_TIGHT = 30;
    private static final int PANEL_HEIGHT_MIN = 120;
    /** The button row's band at the foot of the screen. */
    private static final int BUTTON_ROW_BAND = 44;
    /** Below this the detail line under the grid is dropped. */
    private static final int DETAILS_MIN_PANEL = 200;
    /** A tile still has to hold a name and a line under it. */
    private static final int CELL_HEIGHT_MIN = 30;

    private final GuiScreen parent;
    private int selectedSlot = DEFAULT_ROW_SLOT;
    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;

    private int defaultRowX;
    private int defaultRowY;
    private int defaultRowWidth;
    private int gridX;
    private int gridY;
    private int cellWidth;
    private int cellHeight;
    private int gap;

    private GuiButton primaryButton;
    private GuiButton deleteButton;
    private GuiButton refreshButton;
    private GuiButton capeButton;

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
        // The cape editor edits the identity being played; offered from the
        // roster while that identity is the one selected here.
        this.capeButton = new GuiButton(BUTTON_CAPE, this.width - 88, y, 80, 20,
                I18n.format("gui.losttales.character.cape.button"));
        this.buttonList.add(this.capeButton);

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
        if (snapshot == null || this.selectedSlot == DEFAULT_ROW_SLOT) {
            return;
        }
        if (!CharacterRoster.isValidSlotIndex(this.selectedSlot)
                || snapshot.getSlotState(this.selectedSlot) == CharacterSlotState.HIDDEN) {
            CharacterSummary active = snapshot.getActiveCharacter();
            this.selectedSlot = active == null ? DEFAULT_ROW_SLOT : active.getSlotIndex();
        }
    }

    private boolean isDefaultRowSelected() {
        return this.selectedSlot == DEFAULT_ROW_SLOT;
    }

    private CharacterSummary selectedCharacter(CharacterRosterSnapshot snapshot) {
        return snapshot == null
                ? null : snapshot.getCharacterAtSlot(this.selectedSlot);
    }

    /**
     * Whether a summary is the identity being played. A world that has
     * not minted the default character yet names no active character at
     * all, and the account is what is being played then, so the default
     * row answers for it.
     */
    private static boolean isPlayed(CharacterRosterSnapshot snapshot,
                                    CharacterSummary summary) {
        if (snapshot == null || summary == null) {
            return false;
        }
        return snapshot.getActiveCharacterId() == null
                ? summary.isDefault()
                : summary.getCharacterId().equals(snapshot.getActiveCharacterId());
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

        boolean played = isPlayed(snapshot, selected);
        if (played) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.active");
            this.primaryButton.enabled = false;
        } else if (selected != null) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.select");
            this.primaryButton.enabled = !pending;
        } else if (isDefaultRowSelected()) {
            // The world has not minted the record yet; there is nothing
            // to switch to, and the account is already what is played.
            this.primaryButton.displayString = I18n.format("gui.losttales.character.active");
            this.primaryButton.enabled = false;
        } else if (state == CharacterSlotState.UNLOCKED) {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.create");
            this.primaryButton.enabled = !pending;
        } else {
            this.primaryButton.displayString = I18n.format("gui.losttales.character.unavailable");
            this.primaryButton.enabled = false;
        }
        // The account's own identity is always there; the server refuses
        // to delete it, so the button never offers it.
        this.deleteButton.enabled = selected != null && !selected.isDefault()
                && !pending
                && ClientLoreCharacterCache.findOwnedCharacter(
                selected.getCharacterId()) == null;
        this.refreshButton.enabled = !pending;
        if (this.capeButton != null) {
            this.capeButton.enabled = played && !pending;
        }
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
        if (button.id == BUTTON_CAPE) {
            this.mc.displayGuiScreen(new LostTalesCharacterCapeGui(this));
            return;
        }
        if (snapshot == null || this.pendingRequestId != 0) {
            return;
        }
        CharacterSummary selected = selectedCharacter(snapshot);
        CharacterSlotState state = selectedSlotState(snapshot);
        if (button.id == BUTTON_PRIMARY) {
            if (selected != null && !isPlayed(snapshot, selected)) {
                this.statusMessage = I18n.format("gui.losttales.character.selecting");
                this.statusError = false;
                this.pendingRequestId = ClientCharacterNetwork.selectCharacter(
                        snapshot.getRevision(), selected.getCharacterId());
            } else if (selected == null && !isDefaultRowSelected()
                    && state == CharacterSlotState.UNLOCKED) {
                this.mc.displayGuiScreen(new LostTalesCharacterCreationGui(
                        this, this.selectedSlot));
            }
            return;
        }
        if (button.id == BUTTON_DELETE && selected != null
                && !selected.isDefault()) {
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
        int panelX = (this.width - panelWidth) / 2;
        int panelY = this.height >= 300 ? PANEL_TOP : PANEL_TOP_TIGHT;
        // Never past the band the button row sits in: 1.7.10 keeps the
        // scaled height at or above 240 and no higher, and a panel with a
        // fixed floor runs under the row on every window that short.
        int panelHeight = Math.max(PANEL_HEIGHT_MIN,
                this.height - panelY - BUTTON_ROW_BAND);
        LostTalesSkyrimUiStyle.drawPanel(panelX, panelY, panelWidth, panelHeight);

        this.gap = 8;
        this.defaultRowX = panelX + 12;
        this.defaultRowY = panelY + 14;
        this.defaultRowWidth = panelWidth - 24;
        this.gridX = panelX + 12;
        this.gridY = this.defaultRowY + DEFAULT_ROW_HEIGHT + this.gap;
        this.cellWidth = (panelWidth - 24 - this.gap * 2) / 3;
        boolean roomForDetails = panelHeight >= DETAILS_MIN_PANEL;
        int gridSpace = panelHeight - DEFAULT_ROW_HEIGHT - this.gap * 3
                - (roomForDetails ? 70 : 30);
        this.cellHeight = Math.max(CELL_HEIGHT_MIN, gridSpace / 3);

        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        int hoveredTile = tileAt(mouseX, mouseY);
        drawDefaultRow(snapshot, this.defaultRowX, this.defaultRowY,
                this.defaultRowWidth, hoveredTile == DEFAULT_ROW_SLOT);
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            drawSlot(snapshot, slot, slotX(slot), slotY(slot),
                    hoveredTile == slot);
        }
        if (roomForDetails) {
            drawSelectedDetails(snapshot, panelX + 12, panelY + panelHeight - 39,
                    panelWidth - 24);
        }

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
        CharacterSummary character = snapshot.getCharacterAtSlot(this.selectedSlot);
        CharacterSlotState state = snapshot.getSlotState(this.selectedSlot);
        if (character == null) {
            String message = isDefaultRowSelected()
                    ? I18n.format("gui.losttales.character.account_detail")
                    : state == CharacterSlotState.UNLOCKED
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
        String lineTwo = ClientCharacterDisplayNames.faction(
                character.getStartingFactionId()) + "  •  "
                + I18n.format("gui.losttales.character.level_short",
                Integer.valueOf(character.getRoleplayLevel()));
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, lineOne, width),
                x, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, lineTwo, width),
                x, y + 11, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    /**
     * The default character's row: the account's own identity, drawn from
     * its record. Until a world has minted that record the row shows the
     * account's name and face, which is what the record will carry.
     */
    private void drawDefaultRow(CharacterRosterSnapshot snapshot, int x, int y,
                                int width, boolean hovered) {
        drawTileFrame(x, y, width, DEFAULT_ROW_HEIGHT, isDefaultRowSelected(), hovered);
        CharacterSummary character = snapshot == null
                ? null : snapshot.getCharacterAtSlot(DEFAULT_ROW_SLOT);
        int headY = y + (DEFAULT_ROW_HEIGHT - DEFAULT_HEAD_SIZE) / 2;
        int textX = x + 6;
        if (this.mc != null && this.mc.thePlayer != null
                && drawDefaultHead(character, x + 5, headY)) {
            textX += DEFAULT_HEAD_SIZE + 4;
        }
        this.fontRendererObj.drawStringWithShadow(
                I18n.format("gui.losttales.character.account_tile"), textX, y + 4,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (snapshot == null) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.loading"),
                    textX, y + 14, LostTalesSkyrimUiStyle.TEXT_DIM);
            return;
        }
        boolean active = character == null
                ? snapshot.getActiveCharacterId() == null
                : isPlayed(snapshot, character);
        int nameWidth = width / 2 - 12;
        String name = character == null ? getAccountName() : character.getName();
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, name,
                        nameWidth - (textX - x - 6)),
                textX, y + 14,
                active ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        String details = character == null
                ? ClientCharacterDisplayNames.race(CharacterRaceRegistry.HUMAN)
                : ClientCharacterDisplayNames.race(character.getRaceId()) + "  •  "
                        + I18n.format("gui.losttales.character.level_short",
                        Integer.valueOf(character.getRoleplayLevel()));
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, details, nameWidth - 42),
                x + width / 2, y + 14, LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (active) {
            this.fontRendererObj.drawStringWithShadow(I18n.format("gui.losttales.character.active_marker"),
                    x + width - 42, y + 4, LostTalesSkyrimUiStyle.GREEN);
        }
    }

    /** The record's face where there is one, the account's own until then. */
    private boolean drawDefaultHead(CharacterSummary character, int x, int y) {
        UUID ownerId = this.mc.thePlayer.getUniqueID();
        return character == null
                ? LostTalesCharacterHeadIconRenderer.drawAccountHead(
                        this.mc, ownerId, x, y, DEFAULT_HEAD_SIZE, 1.0F, 1.0F)
                : LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                        this.mc, ownerId, character.getSkinId(), x, y,
                        DEFAULT_HEAD_SIZE, 1.0F, 1.0F);
    }

    private void drawSlot(CharacterRosterSnapshot snapshot, int slot, int x, int y,
                          boolean hovered) {
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

    private int slotX(int slot) {
        return this.gridX + (slot % 3) * (this.cellWidth + this.gap);
    }

    private int slotY(int slot) {
        return this.gridY + (slot / 3) * (this.cellHeight + this.gap);
    }

    /**
     * The tile under the point: {@link #DEFAULT_ROW_SLOT} for the account's
     * row, a slot index for one of the nine, or {@link #NO_TILE}.
     */
    private int tileAt(int mouseX, int mouseY) {
        if (mouseX >= this.defaultRowX
                && mouseX < this.defaultRowX + this.defaultRowWidth
                && mouseY >= this.defaultRowY
                && mouseY < this.defaultRowY + DEFAULT_ROW_HEIGHT) {
            return DEFAULT_ROW_SLOT;
        }
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            int x = slotX(slot);
            int y = slotY(slot);
            if (mouseX >= x && mouseX < x + this.cellWidth
                    && mouseY >= y && mouseY < y + this.cellHeight) {
                return slot;
            }
        }
        return NO_TILE;
    }

    /**
     * Whether a click on the tile selects it: the account's row always,
     * one of the nine once the roster is known and the slot is not hidden.
     */
    private static boolean isSelectable(CharacterRosterSnapshot snapshot, int tile) {
        if (tile == DEFAULT_ROW_SLOT) {
            return true;
        }
        return tile != NO_TILE && snapshot != null
                && snapshot.getSlotState(tile) != CharacterSlotState.HIDDEN;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            int tile = tileAt(mouseX, mouseY);
            if (tile != NO_TILE) {
                // A tile that cannot be selected still takes the click.
                if (isSelectable(ClientCharacterRosterCache.getSnapshot(), tile)) {
                    this.selectedSlot = tile;
                    this.statusMessage = "";
                    updateButtonState();
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * The same hit tests a click takes, in the same order, at the point the
     * screen's own draw and clicks see. A hidden slot, and every slot before the roster arrives, lights
     * under the pointer but selects nothing, so it keeps the arrow.
     */
    @Override
    public boolean isPointerOverInteractable(int x, int y) {
        int tile = tileAt(x, y);
        if (tile != NO_TILE) {
            return isSelectable(ClientCharacterRosterCache.getSnapshot(), tile);
        }
        return LostTalesGuiPointerTargets.isOverEnabledButton(this, x, y);
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
