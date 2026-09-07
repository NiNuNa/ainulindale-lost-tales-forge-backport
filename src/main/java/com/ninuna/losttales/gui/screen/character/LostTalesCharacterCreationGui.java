package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.character.CharacterTemplate;
import com.ninuna.losttales.client.character.CharacterTemplateStore;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
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
    private static final int BUTTON_BODY_PREVIOUS = 16;
    private static final int BUTTON_BODY_NEXT = 17;
    private static final int BUTTON_CHEST_PREVIOUS = 18;
    private static final int BUTTON_CHEST_NEXT = 19;

    private static final int STEP_APPEARANCE = 0;
    private static final int STEP_IDENTITY = 1;

    /** The slot a template stands for: none, until a server names one. */
    private static final int TEMPLATE_SLOT = -1;

    private static final int PANEL_TOP = 46;
    /** Where the panel starts when the header has to share the room. */
    private static final int PANEL_TOP_TIGHT = 20;
    private static final int PANEL_HEIGHT_MAX = 420;
    private static final int PANEL_HEIGHT_MIN = 120;
    /** The button row's band at the foot of the screen. */
    private static final int BUTTON_ROW_BAND = 44;
    /** Below this the appearance step drops the race attributes. */
    private static final int RACE_ATTRIBUTES_MIN_PANEL = 314;
    /** Below this the identity step drops its hint lines. */
    private static final int HINTS_MIN_PANEL = 235;
    /** Below this the identity step drops its appearance summary. */
    private static final int SUMMARY_MIN_PANEL = 290;
    /** The face drawn where there is no body to dress, in pixels. */
    private static final int FACE_PREVIEW_SIZE = 48;
    /** How far above the body's baseline the face sits. */
    private static final int FACE_PREVIEW_LIFT = 30;

    private final GuiScreen parent;
    private final int slotIndex;
    /** Whether the form edits the account's template rather than a world's roster. */
    private final boolean templateMode;
    private boolean seededFromTemplate;

    private GuiTextField nameField;
    private GuiTextField ageField;
    private GuiTextField descriptionField;
    private List<String> raceIds = Collections.emptyList();
    private List<String> genderIds = Collections.emptyList();
    private List<String> skinIds = Collections.emptyList();
    private List<String> bodyTypeIds = Collections.emptyList();
    private List<String> chestTypeIds = Collections.emptyList();
    private List<String> factionIds = Collections.emptyList();
    private List<String> waypointIds = Collections.emptyList();
    private int raceIndex;
    private int genderIndex;
    private int skinIndex;
    private int bodyTypeIndex = -1;
    private int chestTypeIndex = -1;
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

    public LostTalesCharacterCreationGui(GuiScreen parent, int slotIndex) {
        this(parent, slotIndex, false);
    }

    /**
     * The same form as the account's own template editor: no server is
     * asked anything, and confirming writes the template this client
     * opens every later creation form from.
     */
    public static LostTalesCharacterCreationGui forTemplate(GuiScreen parent) {
        return new LostTalesCharacterCreationGui(parent, TEMPLATE_SLOT, true);
    }

    private LostTalesCharacterCreationGui(GuiScreen parent, int slotIndex,
                                          boolean templateMode) {
        this.parent = parent;
        this.slotIndex = slotIndex;
        this.templateMode = templateMode;
    }

    /**
     * Fills the form in from the account's template, once, after the
     * options are known. A value the options do not offer is left at the
     * form's own choice and named in the status line, so a template made
     * against one server never quietly becomes a different character on
     * another.
     */
    private void seedFromTemplate() {
        CharacterTemplate template = CharacterTemplateStore.load(
                LostTalesClientAccount.id());
        if (template.isEmpty()) {
            return;
        }
        this.draftName = template.getName();
        this.draftAge = template.getAge() > 0
                ? String.valueOf(template.getAge()) : this.draftAge;
        this.draftDescription = template.getDescription();
        this.unconventionalSettings = template.hasUnconventionalSettings();

        // Each choice narrows the ones under it, so they are seeded top
        // down and the lists below are rebuilt in between — the same
        // order the cyclers use. Seeding a skin against the list the
        // previous sex offered would silently drop it.
        List<String> unavailable = new java.util.ArrayList<String>();
        int race = this.raceIds.indexOf(template.getRaceId());
        if (race >= 0) {
            this.raceIndex = race;
        } else if (template.getRaceId().length() > 0) {
            unavailable.add(ClientCharacterDisplayNames.race(template.getRaceId()));
        }
        rebuildGenderOptions();
        this.genderIndex = seedIndex(this.genderIds, template.getGenderId(),
                this.genderIndex, template.getGenderId().length() > 0
                        ? ClientCharacterDisplayNames.gender(template.getGenderId())
                        : null, unavailable);
        rebuildAppearanceOptions();
        this.skinIndex = seedIndex(this.skinIds, template.getSkinId(),
                this.skinIndex, template.getSkinId().length() > 0
                        ? ClientCharacterDisplayNames.skin(template.getSkinId())
                        : null, unavailable);
        this.bodyTypeIndex = seedIndex(this.bodyTypeIds,
                template.getBodyTypeId(), this.bodyTypeIndex, null, unavailable);
        this.chestTypeIndex = seedIndex(this.chestTypeIds,
                template.getChestTypeId(), this.chestTypeIndex, null, unavailable);
        int faction = this.factionIds.indexOf(template.getStartingFactionId());
        if (faction >= 0) {
            this.factionIndex = faction;
        } else if (template.getStartingFactionId().length() > 0) {
            unavailable.add(ClientCharacterDisplayNames.faction(
                    template.getStartingFactionId()));
        }
        if (!unavailable.isEmpty()) {
            // Away from a world there is no server to name; the template
            // editor is answering out of this installation's own content.
            this.statusMessage = I18n.format(this.templateMode
                            ? "gui.losttales.character.template.unknown"
                            : "gui.losttales.character.template.unavailable",
                    join(unavailable));
            this.statusError = false;
        }
    }

    /**
     * The template's choice where the options offer it, and the form's own
     * otherwise. A choice that is dropped is named, so a template made
     * against one server never quietly becomes a different character on
     * another; {@code label} is null for a choice not worth naming.
     */
    private static int seedIndex(List<String> options, String id, int fallback,
                                 String label, List<String> unavailable) {
        int index = id == null ? -1 : options.indexOf(id);
        if (index >= 0) {
            return index;
        }
        if (label != null) {
            unavailable.add(label);
        }
        return fallback;
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return joined.toString();
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
        if (!this.seededFromTemplate) {
            this.seededFromTemplate = true;
            seedFromTemplate();
        }

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
            addCyclerButtons(BUTTON_BODY_PREVIOUS, BUTTON_BODY_NEXT,
                    fieldX, top + 90, fieldWidth);
            addCyclerButtons(BUTTON_CHEST_PREVIOUS, BUTTON_CHEST_NEXT,
                    fieldX, top + 120, fieldWidth);
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
            if (!this.templateMode) {
                // A template names no starting waypoint, so the form that
                // edits one does not ask for it.
                addCyclerButtons(BUTTON_WAYPOINT_PREVIOUS, BUTTON_WAYPOINT_NEXT,
                        fieldX, top + 120, fieldWidth);
            }
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
                    I18n.format(this.templateMode
                            ? "gui.losttales.character.template.save"
                            : "gui.losttales.character.create"));
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
        // The new character is on the roster; the player is still whoever
        // they were playing, and picks it from the roster when they want to.
        if (ClientCharacterRosterCache.getSnapshot() != null) {
            this.mc.displayGuiScreen(this.parent);
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
            if ((button.id == BUTTON_BODY_PREVIOUS
                    || button.id == BUTTON_BODY_NEXT)
                    && (this.bodyTypeIds.size() < 2 || !hasBodyTypeChoice())) {
                button.enabled = false;
            }
            if ((button.id == BUTTON_CHEST_PREVIOUS
                    || button.id == BUTTON_CHEST_NEXT)
                    && (this.chestTypeIds.size() < 2 || !hasChestChoice())) {
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
            // A template carries no starting waypoint — every server
            // resolves that against its own map — so it is not something
            // saving one can wait for.
            this.createButton.enabled = !pending && appearanceReady
                && (this.templateMode
                    || (this.factionIds.size() > 0
                        && this.waypointIds.size() > 0));
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
            case BUTTON_BODY_PREVIOUS:
                this.bodyTypeIndex = cycleIndex(
                        this.bodyTypeIndex, -1, this.bodyTypeIds.size());
                return;
            case BUTTON_BODY_NEXT:
                this.bodyTypeIndex = cycleIndex(
                        this.bodyTypeIndex, 1, this.bodyTypeIds.size());
                return;
            case BUTTON_CHEST_PREVIOUS:
                this.chestTypeIndex = cycleIndex(
                        this.chestTypeIndex, -1, this.chestTypeIds.size());
                return;
            case BUTTON_CHEST_NEXT:
                this.chestTypeIndex = cycleIndex(
                        this.chestTypeIndex, 1, this.chestTypeIds.size());
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
        // Sex only picks the defaults; the player can still cycle away from them.
        rebuildBodyTypes(true);
        rebuildChestTypes(true);
    }

    private void rebuildAppearanceOptions() {
        rebuildSkins();
        rebuildBodyTypes(false);
        rebuildChestTypes(false);
        rebuildFactions();
    }

    private void rebuildChestTypes(boolean resetToDefault) {
        this.chestTypeIds = ClientCharacterDisplayNames.getChestTypeIds();
        if (resetToDefault || this.chestTypeIndex < 0) {
            String genderId = selected(this.genderIds, this.genderIndex);
            int defaultIndex = this.chestTypeIds.indexOf(
                    CharacterChestTypeRegistry.defaultFor(genderId));
            this.chestTypeIndex = Math.max(0, defaultIndex);
        }
        this.chestTypeIndex = clampIndex(this.chestTypeIndex, this.chestTypeIds.size());
    }

    private boolean hasChestChoice() {
        return ClientCharacterDisplayNames.hasChestChoice(
                selected(this.skinIds, this.skinIndex));
    }

    private void rebuildBodyTypes(boolean resetToDefault) {
        this.bodyTypeIds = ClientCharacterDisplayNames.getBodyTypeIds();
        if (resetToDefault || this.bodyTypeIndex < 0) {
            String genderId = selected(this.genderIds, this.genderIndex);
            int defaultIndex = this.bodyTypeIds.indexOf(
                    CharacterBodyTypeRegistry.defaultFor(genderId));
            this.bodyTypeIndex = Math.max(0, defaultIndex);
        }
        this.bodyTypeIndex = clampIndex(this.bodyTypeIndex, this.bodyTypeIds.size());
    }

    private boolean hasBodyTypeChoice() {
        return ClientCharacterDisplayNames.hasBodyTypeChoice(
                selected(this.skinIds, this.skinIndex));
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
        if (this.templateMode) {
            saveTemplate();
            return;
        }
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
                        this.descriptionField.getText()),
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.chestTypeIds, this.chestTypeIndex));
        this.statusMessage = I18n.format("gui.losttales.character.creating");
        this.statusError = false;
        this.pendingRequestId = ClientCharacterNetwork.createCharacter(request);
        // What this account starts as on the next world it joins. The
        // creation itself is the server's answer; this only remembers the
        // choices that led to it.
        rememberTemplate(request);
    }

    /**
     * Writes the form as the account's template. Nothing is sent and
     * nothing is validated beyond a name worth keeping: which of these
     * choices a particular server offers is that server's to say, and is
     * asked when the form is opened against it.
     */
    private void saveTemplate() {
        String normalizedName = CharacterValidator.normalizeName(
                this.nameField == null ? this.draftName : this.nameField.getText());
        if (normalizedName.length() == 0) {
            this.statusMessage = ClientCharacterDisplayNames.error(
                    com.ninuna.losttales.character.validation.CharacterErrorId.INVALID_NAME_EMPTY);
            this.statusError = true;
            return;
        }
        int age;
        try {
            age = Integer.parseInt(this.ageField == null
                    ? this.draftAge.trim() : this.ageField.getText().trim());
        } catch (NumberFormatException notANumber) {
            this.statusMessage = ClientCharacterDisplayNames.error(
                    com.ninuna.losttales.character.validation.CharacterErrorId.INVALID_AGE);
            this.statusError = true;
            return;
        }
        CharacterTemplate template = new CharacterTemplate(
                normalizedName,
                selected(this.raceIds, this.raceIndex),
                selected(this.genderIds, this.genderIndex),
                selected(this.skinIds, this.skinIndex),
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.chestTypeIds, this.chestTypeIndex),
                selected(this.factionIds, this.factionIndex),
                CharacterValidator.normalizeDescription(
                        this.descriptionField == null
                                ? this.draftDescription
                                : this.descriptionField.getText()),
                age, this.unconventionalSettings);
        boolean saved = CharacterTemplateStore.save(
                LostTalesClientAccount.id(), template);
        this.statusMessage = I18n.format(saved
                ? "gui.losttales.character.template.saved"
                : "gui.losttales.character.template.unsaved");
        this.statusError = !saved;
        if (saved && this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    /** Remembers a creation as this account's template; failure costs nothing. */
    private void rememberTemplate(CharacterCreationRequest request) {
        CharacterTemplateStore.save(LostTalesClientAccount.id(),
                CharacterTemplate.of(request));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        String stepLabel = I18n.format(this.step == STEP_APPEARANCE
                ? "gui.losttales.character.creation.step.appearance"
                : "gui.losttales.character.creation.step.identity");
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format(this.templateMode
                        ? "gui.losttales.character.template.title"
                        : "gui.losttales.character.creation"),
                this.templateMode ? stepLabel : stepLabel + " - " + I18n.format(
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
            drawLabel(I18n.format("gui.losttales.character.body"),
                    labelX, rowY + 96);
            drawCenteredValue(hasBodyTypeChoice()
                            ? ClientCharacterDisplayNames.bodyType(
                                    selected(this.bodyTypeIds, this.bodyTypeIndex))
                            : I18n.format("gui.losttales.character.body.fixed"),
                    valueX + 22, rowY + 90, valueWidth - 44);
            drawLabel(I18n.format("gui.losttales.character.chest"),
                    labelX, rowY + 126);
            drawCenteredValue(hasChestChoice()
                            ? ClientCharacterDisplayNames.chestType(
                                    selected(this.chestTypeIds, this.chestTypeIndex))
                            : I18n.format("gui.losttales.character.chest.fixed"),
                    valueX + 22, rowY + 120, valueWidth - 44);
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

            if (panelHeight >= RACE_ATTRIBUTES_MIN_PANEL && !this.templateMode) {
                drawRaceAttributes(left + 18, rowY + 142,
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
            if (!this.templateMode) {
                drawLabel(I18n.format("gui.losttales.character.starting_waypoint"),
                        labelX, rowY + 126);
            }

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
            if (!this.templateMode) {
                drawCenteredValue(this.waypointIds.isEmpty()
                                ? I18n.format("gui.losttales.character.no_options")
                                : ClientCharacterDisplayNames.waypoint(
                                        selected(this.waypointIds,
                                                this.waypointIndex)),
                        valueX + 22, rowY + 120, valueWidth - 44);
            }

            if (panelHeight >= HINTS_MIN_PANEL) {
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
            }

            if (panelHeight >= SUMMARY_MIN_PANEL) {
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

    private int getPanelTop() {
        return this.height >= 340 ? PANEL_TOP : PANEL_TOP_TIGHT;
    }

    /**
     * What is left between the panel's top and the band the button row
     * needs at the foot of the screen.
     *
     * <p>The row is placed from the panel's bottom edge, so a panel with
     * a fixed floor takes the row off screen with it on any short one —
     * and short is common: 1.7.10 only keeps the scaled height at or
     * above 240, and 1920x1080 at the default GUI Scale Auto is 270.
     * Sections that do not fit what is left are dropped by the draw
     * pass rather than drawn past the panel.</p>
     */
    private int getPanelHeight() {
        return Math.max(PANEL_HEIGHT_MIN, Math.min(PANEL_HEIGHT_MAX,
                this.height - getPanelTop() - BUTTON_ROW_BAND));
    }

    /**
     * The character being chosen, drawn beside the choices.
     *
     * <p>In a world that is the player's own body wearing the appearance,
     * which is what it will actually look like. At the main menu there is
     * no body to dress, so the face is drawn on its own from the same
     * skin the body would have worn.</p>
     */
    private void drawAppearancePreview(int x, int y, int mouseX, int mouseY) {
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        String skinId = selected(this.skinIds, this.skinIndex);
        if (raceId.length() == 0 || genderId.length() == 0 || skinId.length() == 0) {
            return;
        }
        if (player == null || player.getUniqueID() == null) {
            drawFacePreview(skinId, x, y);
            return;
        }

        CharacterAppearance preview = new CharacterAppearance(
                player.getUniqueID(), raceId, genderId, skinId,
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.chestTypeIds, this.chestTypeIndex));
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

    /** The face alone, centred on the column the body would have filled. */
    private void drawFacePreview(String skinId, int centerX, int baselineY) {
        java.util.UUID account = LostTalesClientAccount.id();
        if (account == null) {
            return;
        }
        LostTalesSkyrimUiStyle.beginContent();
        LostTalesCharacterHeadIconRenderer.drawSnapshotHead(this.mc, account,
                skinId, centerX - FACE_PREVIEW_SIZE / 2,
                baselineY - FACE_PREVIEW_SIZE - FACE_PREVIEW_LIFT,
                FACE_PREVIEW_SIZE, 1.0F, 1.0F);
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
