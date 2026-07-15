package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.gui.screen.LostTalesCharacterInfoGui;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.List;

/** Client-side creation form. The server remains authoritative for all validation. */
public final class LostTalesCharacterCreationGui extends GuiScreen {

    private static final int BUTTON_RACE_PREVIOUS = 1;
    private static final int BUTTON_RACE_NEXT = 2;
    private static final int BUTTON_GENDER_PREVIOUS = 3;
    private static final int BUTTON_GENDER_NEXT = 4;
    private static final int BUTTON_SKIN_PREVIOUS = 5;
    private static final int BUTTON_SKIN_NEXT = 6;
    private static final int BUTTON_FACTION_PREVIOUS = 7;
    private static final int BUTTON_FACTION_NEXT = 8;
    private static final int BUTTON_WAYPOINT_PREVIOUS = 9;
    private static final int BUTTON_WAYPOINT_NEXT = 10;
    private static final int BUTTON_UNCONVENTIONAL = 11;
    private static final int BUTTON_CONTINUE = 12;
    private static final int BUTTON_CREATE = 13;
    private static final int BUTTON_BACK = 14;
    private static final int BUTTON_CANCEL = 15;

    private static final int STEP_APPEARANCE = 0;
    private static final int STEP_IDENTITY = 1;

    private final GuiScreen parent;
    private final int slotIndex;
    private final boolean firstCharacterFlow;

    private GuiTextField nameField;
    private GuiTextField ageField;
    private GuiTextField descriptionField;
    private List<String> raceIds = Collections.emptyList();
    private List<String> genderIds = Collections.emptyList();
    private List<String> skinIds = Collections.emptyList();
    private List<String> factionIds = Collections.emptyList();
    private List<String> waypointIds = Collections.emptyList();
    private int raceIndex;
    private int genderIndex;
    private int skinIndex;
    private int factionIndex;
    private int waypointIndex;
    private int step = STEP_APPEARANCE;
    private String draftName = "";
    private String draftAge = "";
    private String draftDescription = "";
    private boolean unconventionalSettings;
    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;
    private GuiButton createButton;
    private GuiButton continueButton;
    private GuiButton unconventionalButton;

    public LostTalesCharacterCreationGui(GuiScreen parent, int slotIndex,
                                         boolean firstCharacterFlow) {
        this.parent = parent;
        this.slotIndex = slotIndex;
        this.firstCharacterFlow = firstCharacterFlow;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        captureTextDraft();
        this.buttonList.clear();
        this.raceIds = ClientCharacterDisplayNames.getRaceIds();
        this.raceIndex = clampIndex(this.raceIndex, this.raceIds.size());
        rebuildGenderOptions();
        rebuildAppearanceOptions();

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelTop = getPanelTop();
        int left = (this.width - panelWidth) / 2;
        int fieldX = left + 142;
        int previewWidth = panelWidth >= 420 ? 110 : 0;
        if (this.step == STEP_IDENTITY) {
            previewWidth = 0;
        }
        int fieldWidth = panelWidth - 160 - previewWidth;
        int top = panelTop + 16;
        this.nameField = null;
        this.ageField = null;
        this.descriptionField = null;
        this.createButton = null;
        this.continueButton = null;
        this.unconventionalButton = null;
        int bottom = panelTop + panelHeight - 30;
        if (this.step == STEP_APPEARANCE) {
            addCyclerButtons(BUTTON_RACE_PREVIOUS, BUTTON_RACE_NEXT,
                    fieldX, top, fieldWidth);
            addCyclerButtons(BUTTON_GENDER_PREVIOUS, BUTTON_GENDER_NEXT,
                    fieldX, top + 30, fieldWidth);
            addCyclerButtons(BUTTON_SKIN_PREVIOUS, BUTTON_SKIN_NEXT,
                    fieldX, top + 60, fieldWidth);
            this.continueButton = new GuiButton(
                    BUTTON_CONTINUE, this.width / 2 - 104, bottom, 100, 20,
                    I18n.format("gui.losttales.character.continue"));
            this.buttonList.add(this.continueButton);
            this.buttonList.add(new GuiButton(BUTTON_CANCEL,
                    this.width / 2 + 4, bottom, 100, 20,
                    I18n.format("gui.cancel")));
        } else {
            this.nameField = new GuiTextField(this.fontRendererObj,
                    fieldX, top, fieldWidth, 20);
            this.nameField.setMaxStringLength(
                    CharacterValidator.MAX_NAME_LENGTH + 8);
            this.nameField.setText(this.draftName);
            this.nameField.setFocused(true);
            this.ageField = new GuiTextField(this.fontRendererObj,
                    fieldX, top + 30, Math.min(90, fieldWidth), 20);
            this.ageField.setMaxStringLength(6);
            this.ageField.setText(this.draftAge);
            this.descriptionField = new GuiTextField(this.fontRendererObj,
                    fieldX, top + 60, fieldWidth, 20);
            this.descriptionField.setMaxStringLength(
                    CharacterValidator.MAX_DESCRIPTION_LENGTH);
            this.descriptionField.setText(this.draftDescription);
            addCyclerButtons(BUTTON_FACTION_PREVIOUS, BUTTON_FACTION_NEXT,
                    fieldX, top + 90, fieldWidth);
            addCyclerButtons(BUTTON_WAYPOINT_PREVIOUS, BUTTON_WAYPOINT_NEXT,
                    fieldX, top + 120, fieldWidth);
            this.unconventionalButton = new GuiButton(
                    BUTTON_UNCONVENTIONAL, fieldX, top + 150,
                    fieldWidth, 20, "");
            this.buttonList.add(this.unconventionalButton);
            updateUnconventionalButtonLabel();

            this.buttonList.add(new GuiButton(BUTTON_BACK,
                    this.width / 2 - 155, bottom, 98, 20,
                    I18n.format("gui.losttales.character.back")));
            this.createButton = new GuiButton(BUTTON_CREATE,
                    this.width / 2 - 49, bottom, 98, 20,
                    I18n.format("gui.losttales.character.create"));
            this.buttonList.add(this.createButton);
            this.buttonList.add(new GuiButton(BUTTON_CANCEL,
                    this.width / 2 + 57, bottom, 98, 20,
                    I18n.format("gui.cancel")));
        }
        updateButtonState();
    }

    private void addCyclerButtons(int previousId, int nextId,
                                  int x, int y, int width) {
        this.buttonList.add(new GuiButton(previousId, x, y, 20, 20, "<"));
        this.buttonList.add(new GuiButton(nextId, x + width - 20, y, 20, 20, ">"));
    }

    @Override
    public void updateScreen() {
        if (this.nameField != null) {
            this.nameField.updateCursorCounter();
        }
        if (this.ageField != null) {
            this.ageField.updateCursorCounter();
        }
        if (this.descriptionField != null) {
            this.descriptionField.updateCursorCounter();
        }
        handlePendingOperation();
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
            this.statusMessage = ClientCharacterDisplayNames.error(feedback.getErrorId());
            this.statusError = true;
            return;
        }
        this.statusMessage = ClientCharacterDisplayNames.operationSuccess("create");
        this.statusError = false;
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null) {
            if (this.firstCharacterFlow && snapshot.getActiveCharacter() != null) {
                this.mc.displayGuiScreen(new LostTalesCharacterInfoGui(this.parent));
            } else {
                this.mc.displayGuiScreen(this.parent);
            }
        }
    }

    private void updateButtonState() {
        boolean pending = this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(this.pendingRequestId);
        for (Object object : this.buttonList) {
            GuiButton button = (GuiButton)object;
            button.enabled = button.id == BUTTON_CANCEL || !pending;
            if ((button.id == BUTTON_GENDER_PREVIOUS
                    || button.id == BUTTON_GENDER_NEXT)
                    && this.genderIds.size() < 2) {
                button.enabled = false;
            }
            if ((button.id == BUTTON_FACTION_PREVIOUS
                    || button.id == BUTTON_FACTION_NEXT)
                    && this.factionIds.size() < 2) {
                button.enabled = false;
            }
            if ((button.id == BUTTON_WAYPOINT_PREVIOUS
                    || button.id == BUTTON_WAYPOINT_NEXT)
                    && this.waypointIds.size() < 2) {
                button.enabled = false;
            }
        }
        boolean appearanceReady = this.raceIds.size() > 0
                && this.genderIds.size() > 0
                && this.skinIds.size() > 0;
        if (this.continueButton != null) {
            this.continueButton.enabled = !pending && appearanceReady;
        }
        if (this.createButton != null) {
            this.createButton.enabled = !pending && appearanceReady
                && this.factionIds.size() > 0
                && this.waypointIds.size() > 0;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CANCEL) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.pendingRequestId != 0) {
            return;
        }
        switch (button.id) {
            case BUTTON_RACE_PREVIOUS:
                cycleRace(-1);
                return;
            case BUTTON_RACE_NEXT:
                cycleRace(1);
                return;
            case BUTTON_GENDER_PREVIOUS:
                cycleGender(-1);
                return;
            case BUTTON_GENDER_NEXT:
                cycleGender(1);
                return;
            case BUTTON_SKIN_PREVIOUS:
                this.skinIndex = cycleIndex(this.skinIndex, -1, this.skinIds.size());
                return;
            case BUTTON_SKIN_NEXT:
                this.skinIndex = cycleIndex(this.skinIndex, 1, this.skinIds.size());
                return;
            case BUTTON_FACTION_PREVIOUS:
                this.factionIndex = cycleIndex(this.factionIndex, -1, this.factionIds.size());
                rebuildWaypoints();
                return;
            case BUTTON_FACTION_NEXT:
                this.factionIndex = cycleIndex(this.factionIndex, 1, this.factionIds.size());
                rebuildWaypoints();
                return;
            case BUTTON_WAYPOINT_PREVIOUS:
                this.waypointIndex = cycleIndex(
                        this.waypointIndex, -1, this.waypointIds.size());
                return;
            case BUTTON_WAYPOINT_NEXT:
                this.waypointIndex = cycleIndex(
                        this.waypointIndex, 1, this.waypointIds.size());
                return;
            case BUTTON_UNCONVENTIONAL:
                this.unconventionalSettings = !this.unconventionalSettings;
                rebuildFactions();
                updateUnconventionalButtonLabel();
                return;
            case BUTTON_CONTINUE:
                if (this.continueButton != null
                        && this.continueButton.enabled) {
                    this.step = STEP_IDENTITY;
                    initGui();
                }
                return;
            case BUTTON_BACK:
                captureTextDraft();
                this.step = STEP_APPEARANCE;
                initGui();
                return;
            case BUTTON_CREATE:
                submitCreation();
                return;
            default:
                return;
        }
    }

    private void cycleRace(int direction) {
        this.raceIndex = cycleIndex(this.raceIndex, direction, this.raceIds.size());
        rebuildGenderOptions();
        rebuildAppearanceOptions();
    }

    private void rebuildGenderOptions() {
        String previousGender = selected(this.genderIds, this.genderIndex);
        String raceId = selected(this.raceIds, this.raceIndex);
        this.genderIds = ClientCharacterDisplayNames.getCompatibleGenderIds(raceId);
        int previousIndex = this.genderIds.indexOf(previousGender);
        this.genderIndex = previousIndex >= 0 ? previousIndex : 0;
    }

    private void cycleGender(int direction) {
        this.genderIndex = cycleIndex(this.genderIndex, direction, this.genderIds.size());
        rebuildSkins();
    }

    private void rebuildAppearanceOptions() {
        rebuildSkins();
        rebuildFactions();
    }

    private void rebuildSkins() {
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        this.skinIds = ClientCharacterDisplayNames.getCompatibleSkinIds(raceId, genderId);
        this.skinIndex = clampIndex(this.skinIndex, this.skinIds.size());
    }

    private void rebuildFactions() {
        String raceId = selected(this.raceIds, this.raceIndex);
        String previousFaction = selected(this.factionIds, this.factionIndex);
        this.factionIds = ClientCharacterDisplayNames.getFactionIds(
                raceId, this.unconventionalSettings);
        int previousIndex = this.factionIds.indexOf(previousFaction);
        this.factionIndex = previousIndex >= 0
                ? previousIndex : clampIndex(this.factionIndex, this.factionIds.size());
        rebuildWaypoints();
        if (!ClientCharacterDisplayNames.isLotrIntegrationAvailable()) {
            this.statusMessage = ClientCharacterDisplayNames.error(
                    com.ninuna.losttales.character.validation.CharacterErrorId.LOTR_INTEGRATION_UNAVAILABLE);
            this.statusError = true;
        }
    }

    private void rebuildWaypoints() {
        String previousWaypoint = selected(this.waypointIds, this.waypointIndex);
        String factionId = selected(this.factionIds, this.factionIndex);
        this.waypointIds = ClientCharacterDisplayNames.getStartingWaypointIds(
                factionId, this.unconventionalSettings);
        int previousIndex = this.waypointIds.indexOf(previousWaypoint);
        this.waypointIndex = previousIndex >= 0
                ? previousIndex : clampIndex(this.waypointIndex, this.waypointIds.size());
    }

    private void updateUnconventionalButtonLabel() {
        if (this.unconventionalButton != null) {
            this.unconventionalButton.displayString = I18n.format(
                    this.unconventionalSettings
                            ? "gui.losttales.character.unconventional.on"
                            : "gui.losttales.character.unconventional.off");
        }
    }

    private void submitCreation() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null) {
            this.statusMessage = I18n.format("gui.losttales.character.loading_detail");
            this.statusError = true;
            return;
        }
        String normalizedName = CharacterValidator.normalizeName(this.nameField.getText());
        if (normalizedName.length() == 0) {
            this.statusMessage = ClientCharacterDisplayNames.error(
                    com.ninuna.losttales.character.validation.CharacterErrorId.INVALID_NAME_EMPTY);
            this.statusError = true;
            return;
        }
        int age;
        try {
            age = Integer.parseInt(this.ageField.getText().trim());
        } catch (NumberFormatException exception) {
            this.statusMessage = ClientCharacterDisplayNames.error(
                    com.ninuna.losttales.character.validation.CharacterErrorId.INVALID_AGE);
            this.statusError = true;
            return;
        }
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        String skinId = selected(this.skinIds, this.skinIndex);
        String factionId = selected(this.factionIds, this.factionIndex);
        String waypointId = selected(this.waypointIds, this.waypointIndex);
        if (raceId.length() == 0 || genderId.length() == 0
                || skinId.length() == 0 || factionId.length() == 0
                || waypointId.length() == 0) {
            this.statusMessage = I18n.format("gui.losttales.character.no_options");
            this.statusError = true;
            return;
        }

        CharacterCreationRequest request = new CharacterCreationRequest(
                snapshot.getRevision(), this.slotIndex, normalizedName,
                raceId, genderId, skinId, age, factionId,
                waypointId, this.unconventionalSettings,
                CharacterValidator.normalizeDescription(
                        this.descriptionField.getText()));
        this.statusMessage = I18n.format("gui.losttales.character.creating");
        this.statusError = false;
        this.pendingRequestId = ClientCharacterNetwork.createCharacter(request);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        String stepLabel = I18n.format(this.step == STEP_APPEARANCE
                ? "gui.losttales.character.creation.step.appearance"
                : "gui.losttales.character.creation.step.identity");
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.creation"),
                stepLabel + " - " + I18n.format(
                        "gui.losttales.character.slot",
                        Integer.valueOf(this.slotIndex + 1)),
                this.width, 12);

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int left = (this.width - panelWidth) / 2;
        int top = getPanelTop();
        LostTalesSkyrimUiStyle.drawPanel(left, top, panelWidth, panelHeight);
        int labelX = left + 18;
        int valueX = left + 142;
        int previewWidth = this.step == STEP_APPEARANCE && panelWidth >= 420
                ? 110 : 0;
        int valueWidth = panelWidth - 160 - previewWidth;
        int rowY = top + 20;

        if (this.step == STEP_APPEARANCE) {
            drawLabel(I18n.format("gui.losttales.character.race"),
                    labelX, rowY + 6);
            drawLabel(I18n.format("gui.losttales.character.gender"),
                    labelX, rowY + 36);
            drawLabel(I18n.format("gui.losttales.character.skin"),
                    labelX, rowY + 66);
            drawCenteredValue(ClientCharacterDisplayNames.race(
                            selected(this.raceIds, this.raceIndex)),
                    valueX + 22, rowY, valueWidth - 44);
            drawCenteredValue(ClientCharacterDisplayNames.gender(
                            selected(this.genderIds, this.genderIndex)),
                    valueX + 22, rowY + 30, valueWidth - 44);
            drawCenteredValue(this.skinIds.isEmpty()
                            ? I18n.format("gui.losttales.character.no_options")
                            : ClientCharacterDisplayNames.skin(
                                    selected(this.skinIds, this.skinIndex)),
                    valueX + 22, rowY + 60, valueWidth - 44);

            if (panelHeight >= 314) {
                drawRaceAttributes(left + 18, rowY + 112,
                        panelWidth - previewWidth - 36);
            }
            if (previewWidth > 0) {
                drawAppearancePreview(
                        left + panelWidth - previewWidth / 2 - 8,
                        top + panelHeight - 48, mouseX, mouseY);
            }
        } else {
            drawLabel(I18n.format("gui.losttales.character.name"),
                    labelX, rowY + 6);
            drawLabel(I18n.format("gui.losttales.character.age"),
                    labelX, rowY + 36);
            drawLabel(I18n.format("gui.losttales.character.description"),
                    labelX, rowY + 66);
            drawLabel(I18n.format("gui.losttales.character.starting_faction"),
                    labelX, rowY + 96);
            drawLabel(I18n.format("gui.losttales.character.starting_waypoint"),
                    labelX, rowY + 126);

            if (this.nameField != null) {
                this.nameField.drawTextBox();
                this.ageField.drawTextBox();
                this.descriptionField.drawTextBox();
            }
            drawCenteredValue(this.factionIds.isEmpty()
                            ? I18n.format(ClientCharacterDisplayNames
                                    .isLotrIntegrationAvailable()
                                    ? "gui.losttales.character.no_compatible_faction"
                                    : "gui.losttales.character.integration_unavailable")
                            : ClientCharacterDisplayNames.faction(
                                    selected(this.factionIds, this.factionIndex)),
                    valueX + 22, rowY + 90, valueWidth - 44);
            drawCenteredValue(this.waypointIds.isEmpty()
                            ? I18n.format("gui.losttales.character.no_options")
                            : ClientCharacterDisplayNames.waypoint(
                                    selected(this.waypointIds,
                                            this.waypointIndex)),
                    valueX + 22, rowY + 120, valueWidth - 44);

            this.fontRendererObj.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            I18n.format("gui.losttales.character.description.hint"),
                            panelWidth - 36),
                    left + 18, rowY + 177,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            if (this.unconventionalSettings) {
                this.fontRendererObj.drawStringWithShadow(
                        LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                                I18n.format(
                                        "gui.losttales.character.unconventional.hint"),
                                panelWidth - 36),
                        left + 18, rowY + 191,
                        LostTalesSkyrimUiStyle.TEXT_MUTED);
            }

            LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                    I18n.format("gui.losttales.character.appearance_summary"),
                    left + 18, rowY + 218, panelWidth - 36);
            drawCompactAttribute(I18n.format("gui.losttales.character.race"),
                    ClientCharacterDisplayNames.race(
                            selected(this.raceIds, this.raceIndex)),
                    left + 18, rowY + 235, (panelWidth - 48) / 2);
            drawCompactAttribute(I18n.format("gui.losttales.character.skin"),
                    ClientCharacterDisplayNames.skin(
                            selected(this.skinIds, this.skinIndex)),
                    left + panelWidth / 2, rowY + 235,
                    panelWidth / 2 - 18);
        }

        if (this.statusMessage.length() > 0) {
            int statusWidth = panelWidth - previewWidth - 30;
            int statusCenterX = left + (panelWidth - previewWidth) / 2;
            drawCenteredString(this.fontRendererObj,
                    LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                            this.statusMessage, statusWidth),
                    statusCenterX, top + panelHeight - 48,
                    this.statusError ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    private void drawRaceAttributes(int x, int y, int width) {
        String raceId = selected(this.raceIds, this.raceIndex);
        CharacterRaceGameplayProfile profile = ClientCharacterRaceAttributes.resolve(
                this.mc == null ? null : this.mc.theWorld, raceId);

        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.race_attributes"),
                x, y, Math.max(80, width));
        int rowY = y + 17;
        int gap = 12;
        int columnWidth = Math.max(90, (width - gap) / 2);
        drawCompactAttribute(I18n.format("gui.losttales.character.attribute.health"),
                ClientCharacterRaceAttributes.formatHealth(profile),
                x, rowY, columnWidth);
        drawCompactAttribute(I18n.format("gui.losttales.character.attribute.movement_speed"),
                ClientCharacterRaceAttributes.formatMovementSpeed(profile),
                x + columnWidth + gap, rowY, columnWidth);
        rowY += 12;
        drawCompactAttribute(I18n.format("gui.losttales.character.attribute.attack_damage"),
                ClientCharacterRaceAttributes.formatAttackDamage(profile),
                x, rowY, columnWidth);
        drawCompactAttribute(I18n.format("gui.losttales.character.attribute.eye_height"),
                ClientCharacterRaceAttributes.formatEyeHeight(profile),
                x + columnWidth + gap, rowY, columnWidth);
        rowY += 12;
        drawCompactAttribute(I18n.format("gui.losttales.character.attribute.hitbox"),
                ClientCharacterRaceAttributes.formatHitbox(profile),
                x, rowY, width);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        I18n.format("gui.losttales.character.attribute.lotr_source"), width),
                x, rowY + 14, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private void drawCompactAttribute(String label, String value,
                                      int x, int y, int width) {
        String text = label + ": " + value;
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, text, width),
                x, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
    }

    private int getPanelWidth() {
        return Math.min(560, this.width - 30);
    }

    private int getPanelHeight() {
        return Math.min(420, Math.max(314, this.height - 74));
    }

    private int getPanelTop() {
        return Math.max(20, Math.min(46, this.height - getPanelHeight() - 28));
    }

    private void drawAppearancePreview(int x, int y, int mouseX, int mouseY) {
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        String skinId = selected(this.skinIds, this.skinIndex);
        if (raceId.length() == 0 || genderId.length() == 0 || skinId.length() == 0) {
            return;
        }

        CharacterAppearance preview = new CharacterAppearance(
                player.getUniqueID(), raceId, genderId, skinId);
        ClientCharacterAppearanceCache.setPreview(preview);
        boolean previousDebugBoundingBox = RenderManager.debugBoundingBox;
        try {
            int previewY = CharacterGuiPreviewLayout.baselineY(raceId, y);
            int previewScale = CharacterGuiPreviewLayout.scale(raceId, 42);
            RenderManager.debugBoundingBox = false;
            GuiInventory.func_147046_a(
                    x, previewY, previewScale,
                    (float)(x - mouseX), (float)(previewY - 65 - mouseY), player);
        } finally {
            RenderManager.debugBoundingBox = previousDebugBoundingBox;
            ClientCharacterAppearanceCache.clearPreview(player.getUniqueID());
        }
    }

    private void drawLabel(String label, int x, int y) {
        this.fontRendererObj.drawStringWithShadow(label + ":", x, y,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private void drawCenteredValue(String value, int x, int y, int width) {
        String text = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, value, width);
        this.fontRendererObj.drawStringWithShadow(text,
                x + (width - this.fontRendererObj.getStringWidth(text)) / 2,
                y + 6, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.step == STEP_IDENTITY && this.pendingRequestId == 0) {
                captureTextDraft();
                this.step = STEP_APPEARANCE;
                initGui();
            } else {
                this.mc.displayGuiScreen(this.parent);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_TAB && this.nameField != null) {
            boolean nameFocused = this.nameField.isFocused();
            boolean ageFocused = this.ageField.isFocused();
            this.nameField.setFocused(!nameFocused && !ageFocused);
            this.ageField.setFocused(nameFocused);
            this.descriptionField.setFocused(ageFocused);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.step == STEP_APPEARANCE
                    && this.continueButton != null
                    && this.continueButton.enabled) {
                this.step = STEP_IDENTITY;
                initGui();
            } else if (this.createButton != null && this.createButton.enabled) {
                submitCreation();
            }
            return;
        }
        if (this.nameField != null
                && (this.nameField.textboxKeyTyped(typedChar, keyCode)
                || this.ageField.textboxKeyTyped(typedChar, keyCode)
                || this.descriptionField.textboxKeyTyped(
                        typedChar, keyCode))) {
            this.statusMessage = "";
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, button);
            this.ageField.mouseClicked(mouseX, mouseY, button);
            this.descriptionField.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void onGuiClosed() {
        captureTextDraft();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void captureTextDraft() {
        if (this.nameField != null) {
            this.draftName = this.nameField.getText();
        }
        if (this.ageField != null) {
            this.draftAge = this.ageField.getText();
        }
        if (this.descriptionField != null) {
            this.draftDescription = this.descriptionField.getText();
        }
    }

    private static int cycleIndex(int current, int direction, int size) {
        if (size <= 0) {
            return 0;
        }
        int next = (current + direction) % size;
        return next < 0 ? next + size : next;
    }

    private static int clampIndex(int current, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(current, size - 1));
    }

    private static String selected(List<String> values, int index) {
        return values == null || values.isEmpty() || index < 0 || index >= values.size()
                ? "" : values.get(index);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
