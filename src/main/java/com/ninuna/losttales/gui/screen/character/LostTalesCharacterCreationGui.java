package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.camera.InspectionCameraMath;
import com.ninuna.losttales.client.camera.ThirdPersonCameraInspection;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.client.character.CharacterTemplate;
import com.ninuna.losttales.client.character.CharacterTemplateStore;
import com.ninuna.losttales.client.character.room.CharacterRoomSession;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.CreatorCharacterLight;
import com.ninuna.losttales.client.gui.LostTalesHudHidingScreen;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationOptions;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationProfile;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar.Hint;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.screen.character.creator.AgeSliderScale;
import com.ninuna.losttales.gui.screen.character.creator.CharacterCreatorCategory;
import com.ninuna.losttales.gui.screen.character.creator.CharacterCreatorLayout;
import com.ninuna.losttales.gui.screen.character.creator.CharacterStagePose;
import com.ninuna.losttales.gui.screen.character.creator.CreatorChoice;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorKeyValues;
import com.ninuna.losttales.gui.screen.character.creator.CreatorList;
import com.ninuna.losttales.gui.screen.character.creator.CreatorNote;
import com.ninuna.losttales.gui.screen.character.creator.CreatorSlider;
import com.ninuna.losttales.gui.screen.character.creator.CreatorStepper;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTileGrid;
import com.ninuna.losttales.gui.screen.character.creator.CreatorToggle;
import com.ninuna.losttales.gui.screen.character.creator.CreatorWidgets;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The character creator: the choices down a column on the left, the
 * character itself on a stage taking the rest of the screen, and the
 * control bar every full-screen menu here ends in.
 *
 * <p>The column has five pages — race, body, skin, identity, origin — and
 * the player moves between them freely; nothing is a step that has to be
 * finished before the next. In a world the stage is the world itself: the
 * screen borrows the third-person camera, stands it in front of the
 * player wearing the choices as they stand, and the player orbits it by
 * dragging and brings it nearer with the wheel. The main menu opens it
 * in the character room, a world of one room made for the visit, so the
 * stage is the world there too. Only with no world to stand in does the
 * stage draw the figure from the same body model instead, turned and
 * tilted by the same drag, its head following the pointer.</p>
 *
 * <p>Two things open it. The roster opens it against a world, and
 * confirming sends a creation request the server validates and answers.
 * The character room opens it as the account's own template: nothing is
 * sent, and confirming writes the template this account starts every
 * later world from, then returns to the room. The screen is the same
 * either way; only what confirming does differs, and the pages that only
 * a world can answer — the starting waypoint — are left out of the
 * template.</p>
 *
 * <p>The server remains authoritative for every validation. What this
 * screen refuses on its own is only what it can see is empty.</p>
 */
public final class LostTalesCharacterCreationGui extends GuiScreen
        implements LostTalesGuiAnimationOptions, LostTalesHudHidingScreen {

    /** The slot a template stands for: none, until a server names one. */
    private static final int TEMPLATE_SLOT = -1;
    /** What the age slider starts at when nothing chose one. */
    private static final int DEFAULT_AGE = 25;
    /** Between stacked controls in the column. */
    private static final int CONTROL_GAP = 6;
    /** How far one wheel notch scrolls the column. */
    private static final int SCROLL_STEP = 20;
    /** Inside the tab strip's ends. */
    private static final int TAB_INSET = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    /** Where the figure's head is, in blocks above its feet. */
    private static final float HEAD_HEIGHT_BLOCKS = 1.6F;
    /** A biped is two blocks tall, which is what the stage fits. */
    private static final float FIGURE_HEIGHT_BLOCKS = 2.0F;
    /** The face drawn when there is no account to build a body for. */
    private static final int FACE_FALLBACK_SIZE = 64;
    /** Lights the character in a world; a key of this screen's, not a control of the game's. */
    private static final int LIGHT_KEY = Keyboard.KEY_L;

    private final GuiScreen parent;
    private final int slotIndex;
    /** Whether the form edits the account's template rather than a world's roster. */
    private final boolean templateMode;
    private boolean seededFromTemplate;

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
    private String draftName = "";
    private int draftAge = DEFAULT_AGE;
    private String draftDescription = "";
    private boolean unconventionalSettings;
    private boolean showMinecraftCape = true;
    /** None first, then the catalogue in its own order. */
    private List<Integer> capeIds = Collections.emptyList();
    private int capeIndex;

    private CharacterCreatorCategory category = CharacterCreatorCategory.RACE;
    private CharacterCreatorLayout layout;
    private CreatorContext context;
    private final List<CreatorControl> controls = new ArrayList<CreatorControl>();
    private CreatorControl focusedControl;
    private CreatorControl pressedControl;
    private CreatorTextControl nameControl;
    private CreatorTextControl descriptionControl;
    private int scroll;
    private int contentHeight;

    private final CharacterStagePose pose = new CharacterStagePose();
    /**
     * Whether the stage is the world seen through the borrowed camera
     * rather than a drawn figure. True in a world whose camera seams are
     * patched; false at the main menu, where there is no world to show.
     */
    private boolean worldCamera;
    /**
     * Whether the stage is being dragged. A screen's mouse events arrive
     * once a game tick, which is twenty times a second and reads as a
     * stutter on a camera, so while the stage is held the pointer is read
     * straight from the window every frame instead, in window pixels.
     */
    private boolean draggingStage;
    /** Whether the stage is being slid with the right button, read the same way. */
    private boolean panningStage;
    private int dragWindowX;
    private int dragWindowY;

    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;

    public LostTalesCharacterCreationGui(GuiScreen parent, int slotIndex) {
        this(parent, slotIndex, false);
    }

    /**
     * The same screen as the account's own template editor: no server is
     * asked anything, and confirming writes the template this client
     * opens every later creation from.
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

    // ------------------------------------------------------------------
    // Options and the template that seeds them
    // ------------------------------------------------------------------

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
                ? template.getAge() : this.draftAge;
        this.draftDescription = template.getDescription();
        this.unconventionalSettings = template.hasUnconventionalSettings();
        this.showMinecraftCape = template.isMinecraftCapeVisible();
        int cape = this.capeIds.indexOf(Integer.valueOf(template.getCosmeticCapeId()));
        this.capeIndex = Math.max(0, cape);

        // Each choice narrows the ones under it, so they are seeded top
        // down and the lists below are rebuilt in between, the same order
        // choosing them does. Seeding a skin against the list the previous
        // sex offered would silently drop it.
        List<String> unavailable = new ArrayList<String>();
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
            rebuildWaypoints();
        } else if (template.getStartingFactionId().length() > 0) {
            unavailable.add(ClientCharacterDisplayNames.faction(
                    template.getStartingFactionId()));
        }
        if (!unavailable.isEmpty()) {
            // Away from a world there is no server to name; the template
            // editor is answering out of this installation's own content.
            setStatus(I18n.format(this.templateMode
                            ? "gui.losttales.character.template.unknown"
                            : "gui.losttales.character.template.unavailable",
                    join(unavailable)), false);
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

    private void selectRace(int index) {
        this.raceIndex = clampIndex(index, this.raceIds.size());
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

    private void selectGender(int index) {
        this.genderIndex = clampIndex(index, this.genderIds.size());
        rebuildSkins();
        // Sex only picks the defaults; the player can still step away from them.
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
        String previousSkin = selected(this.skinIds, this.skinIndex);
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        this.skinIds = ClientCharacterDisplayNames.getCompatibleSkinIds(raceId, genderId);
        int previousIndex = this.skinIds.indexOf(previousSkin);
        this.skinIndex = previousIndex >= 0
                ? previousIndex : clampIndex(this.skinIndex, this.skinIds.size());
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
            setStatus(ClientCharacterDisplayNames.error(
                    CharacterErrorId.LOTR_INTEGRATION_UNAVAILABLE), true);
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

    // ------------------------------------------------------------------
    // Screen lifecycle
    // ------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        captureTextDraft();
        this.buttonList.clear();
        this.layout = new CharacterCreatorLayout(this.width, this.height);
        this.context = new CreatorContext(this.mc, this.fontRendererObj,
                LostTalesClientAccount.id());
        this.raceIds = ClientCharacterDisplayNames.getRaceIds();
        this.raceIndex = clampIndex(this.raceIndex, this.raceIds.size());
        rebuildGenderOptions();
        rebuildAppearanceOptions();
        rebuildCapeOptions();
        if (!this.seededFromTemplate) {
            this.seededFromTemplate = true;
            seedFromTemplate();
        }
        this.worldCamera = this.mc != null && this.mc.theWorld != null
                && this.mc.thePlayer != null
                && ThirdPersonCameraInspection.isAvailable();
        if (this.worldCamera) {
            ThirdPersonCameraInspection.begin(this, this.mc);
            CreatorCharacterLight.bind(this);
        }
        this.pose.setShot(this.category.getShot());
        rebuildControls();
    }

    private void rebuildCapeOptions() {
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(Integer.valueOf(CharacterCapeCatalog.NONE_ID));
        for (CharacterCapeDefinition definition : CharacterCapeCatalog.getDefinitions()) {
            ids.add(Integer.valueOf(definition.getNetworkId()));
        }
        this.capeIds = Collections.unmodifiableList(ids);
        this.capeIndex = clampIndex(this.capeIndex, this.capeIds.size());
    }

    private int selectedCapeId() {
        return this.capeIds.isEmpty()
                ? CharacterCapeCatalog.NONE_ID
                : this.capeIds.get(clampIndex(this.capeIndex, this.capeIds.size())).intValue();
    }

    /**
     * No fade and no blur: in a world the screen is looking at the world,
     * and a veil over it would be a veil over the character.
     */
    @Override
    public LostTalesGuiAnimationProfile getLostTalesGuiAnimationProfile() {
        return LostTalesGuiAnimationProfile.NONE;
    }

    @Override
    public void updateScreen() {
        for (CreatorControl control : this.controls) {
            control.tick();
        }
        handlePendingOperation();
    }

    @Override
    public void onGuiClosed() {
        captureTextDraft();
        Keyboard.enableRepeatEvents(false);
        CreatorCharacterLight.unbind(this);
        if (this.mc != null) {
            ThirdPersonCameraInspection.end(this, this.mc);
            if (this.mc.thePlayer != null) {
                ClientCharacterAppearanceCache.clearPreview(
                        this.mc.thePlayer.getUniqueID());
            }
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void handlePendingOperation() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(this.pendingRequestId)) {
            return;
        }
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(this.pendingRequestId);
        int completedRequest = this.pendingRequestId;
        this.pendingRequestId = 0;
        if (feedback == null) {
            return;
        }
        ClientCharacterRosterCache.clearOperation(completedRequest);
        if (!feedback.isSuccessful()) {
            setStatus(ClientCharacterDisplayNames.error(feedback.getErrorId()), true);
            return;
        }
        setStatus(ClientCharacterDisplayNames.operationSuccess("create"), false);
        // The new character is on the roster; the player is still whoever
        // they were playing, and picks it from the roster when they want to.
        if (ClientCharacterRosterCache.getSnapshot() != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    private boolean isPending() {
        return this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(this.pendingRequestId);
    }

    private void setStatus(String message, boolean error) {
        this.statusMessage = message == null ? "" : message;
        this.statusError = error;
    }

    // ------------------------------------------------------------------
    // The column's pages
    // ------------------------------------------------------------------

    private void showCategory(CharacterCreatorCategory next) {
        if (next == null || next == this.category) {
            return;
        }
        captureTextDraft();
        this.category = next;
        // The camera glides to the page's own view of the character.
        this.pose.setShot(next.getShot());
        rebuildControls();
    }

    /**
     * Builds the current page's controls. Every control reads the options
     * live through its choice, so a change on one page — a race, say —
     * shows on the others without rebuilding them; only turning the page
     * does that.
     */
    private void rebuildControls() {
        captureTextDraft();
        this.controls.clear();
        this.focusedControl = null;
        this.pressedControl = null;
        this.nameControl = null;
        this.descriptionControl = null;
        this.scroll = 0;
        switch (this.category) {
            case RACE:
                buildRacePage();
                break;
            case BODY:
                buildBodyPage();
                break;
            case SKIN:
                buildSkinPage();
                break;
            case IDENTITY:
                buildIdentityPage();
                break;
            case ORIGIN:
                buildOriginPage();
                break;
            case CAPES:
            default:
                buildCapesPage();
                break;
        }
        // The first thing on the page takes the keyboard, so the arrows
        // and the letters do something the moment the page opens.
        for (CreatorControl control : this.controls) {
            if (control.canFocus()) {
                focus(control);
                break;
            }
        }
    }

    private void buildRacePage() {
        this.controls.add(new CreatorList(this.context, new CreatorChoice() {
            @Override public int count() { return raceIds.size(); }
            @Override public int index() { return raceIndex; }
            @Override public String id(int index) { return raceIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.race(raceIds.get(index));
            }
            @Override public void choose(int index) { selectRace(index); }
            @Override public boolean isFixed() { return false; }
            @Override public String fixedLabel() { return ""; }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        if (!this.templateMode) {
            this.controls.add(new RaceAttributes(this.context));
        } else {
            this.controls.add(new CreatorNote(this.context,
                    I18n.format("gui.losttales.character.creator.race.template_note")));
        }
    }

    private void buildBodyPage() {
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.gender"), new CreatorChoice() {
            @Override public int count() { return genderIds.size(); }
            @Override public int index() { return genderIndex; }
            @Override public String id(int index) { return genderIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.gender(genderIds.get(index));
            }
            @Override public void choose(int index) { selectGender(index); }
            @Override public boolean isFixed() { return false; }
            @Override public String fixedLabel() { return ""; }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.body"), new CreatorChoice() {
            @Override public int count() { return bodyTypeIds.size(); }
            @Override public int index() { return bodyTypeIndex; }
            @Override public String id(int index) { return bodyTypeIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.bodyType(bodyTypeIds.get(index));
            }
            @Override public void choose(int index) {
                bodyTypeIndex = clampIndex(index, bodyTypeIds.size());
            }
            @Override public boolean isFixed() { return !hasBodyTypeChoice(); }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.body.fixed");
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.chest"), new CreatorChoice() {
            @Override public int count() { return chestTypeIds.size(); }
            @Override public int index() { return chestTypeIndex; }
            @Override public String id(int index) { return chestTypeIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.chestType(chestTypeIds.get(index));
            }
            @Override public void choose(int index) {
                chestTypeIndex = clampIndex(index, chestTypeIds.size());
            }
            @Override public boolean isFixed() { return !hasChestChoice(); }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.chest.fixed");
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.body.note")));
    }

    private void buildSkinPage() {
        this.controls.add(new CreatorTileGrid(this.context,
                I18n.format("gui.losttales.character.skin"), new CreatorChoice() {
            @Override public int count() { return skinIds.size(); }
            @Override public int index() { return skinIndex; }
            @Override public String id(int index) { return skinIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.skin(skinIds.get(index));
            }
            @Override public void choose(int index) {
                skinIndex = clampIndex(index, skinIds.size());
            }
            @Override public boolean isFixed() { return false; }
            @Override public String fixedLabel() { return ""; }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.skin.note")));
    }

    private void buildIdentityPage() {
        this.nameControl = new CreatorTextControl(this.context,
                I18n.format("gui.losttales.character.name"), this.draftName,
                CharacterValidator.MAX_NAME_LENGTH, true);
        this.controls.add(this.nameControl);
        this.controls.add(new CreatorSlider(this.context,
                I18n.format("gui.losttales.character.age"), new CreatorSlider.IntValue() {
            @Override public int get() { return draftAge; }
            @Override public void set(int value) {
                draftAge = Math.max(CharacterValidator.MIN_AGE, value);
            }
            @Override public int typedMax() { return CharacterValidator.MAX_AGE; }
        }, I18n.format("gui.losttales.character.creator.age.oldest")));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.age.hint")));
        this.descriptionControl = new CreatorTextControl(this.context,
                I18n.format("gui.losttales.character.description"),
                this.draftDescription, CharacterValidator.MAX_DESCRIPTION_LENGTH,
                true);
        this.controls.add(this.descriptionControl);
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.description.hint")));
    }

    private void buildOriginPage() {
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.starting_faction"),
                new CreatorChoice() {
            @Override public int count() { return factionIds.size(); }
            @Override public int index() { return factionIndex; }
            @Override public String id(int index) { return factionIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.faction(factionIds.get(index));
            }
            @Override public void choose(int index) {
                factionIndex = clampIndex(index, factionIds.size());
                rebuildWaypoints();
            }
            @Override public boolean isFixed() { return false; }
            @Override public String fixedLabel() { return ""; }
            @Override public String emptyLabel() {
                return I18n.format(ClientCharacterDisplayNames.isLotrIntegrationAvailable()
                        ? "gui.losttales.character.no_compatible_faction"
                        : "gui.losttales.character.integration_unavailable");
            }
        }));
        if (!this.templateMode) {
            // A template names no starting waypoint: every server resolves
            // that against its own map, so only a world asks for it.
            this.controls.add(new CreatorStepper(this.context,
                    I18n.format("gui.losttales.character.starting_waypoint"),
                    new CreatorChoice() {
                @Override public int count() { return waypointIds.size(); }
                @Override public int index() { return waypointIndex; }
                @Override public String id(int index) { return waypointIds.get(index); }
                @Override public String label(int index) {
                    return ClientCharacterDisplayNames.waypoint(waypointIds.get(index));
                }
                @Override public void choose(int index) {
                    waypointIndex = clampIndex(index, waypointIds.size());
                }
                @Override public boolean isFixed() { return false; }
                @Override public String fixedLabel() { return ""; }
                @Override public String emptyLabel() {
                    return I18n.format("gui.losttales.character.no_options");
                }
            }));
        }
        this.controls.add(new CreatorToggle(this.context,
                I18n.format("gui.losttales.character.creator.unconventional"),
                I18n.format("gui.losttales.character.creator.toggle.on"),
                I18n.format("gui.losttales.character.creator.toggle.off"),
                new CreatorToggle.BooleanValue() {
            @Override public boolean get() { return unconventionalSettings; }
            @Override public void set(boolean value) {
                unconventionalSettings = value;
                rebuildFactions();
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.unconventional.hint")));
        if (this.templateMode) {
            this.controls.add(new CreatorNote(this.context,
                    I18n.format("gui.losttales.character.creator.origin.template_note")));
        }
    }

    private void buildCapesPage() {
        this.controls.add(new CreatorToggle(this.context,
                I18n.format("gui.losttales.character.creator.cape.minecraft"),
                I18n.format("gui.losttales.character.creator.toggle.on"),
                I18n.format("gui.losttales.character.creator.toggle.off"),
                new CreatorToggle.BooleanValue() {
            @Override public boolean get() { return showMinecraftCape; }
            @Override public void set(boolean value) { showMinecraftCape = value; }
        }));
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.creator.cape.cosmetic"),
                new CreatorChoice() {
            @Override public int count() { return capeIds.size(); }
            @Override public int index() { return capeIndex; }
            @Override public String id(int index) {
                return String.valueOf(capeIds.get(index));
            }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.cape(capeIds.get(index).intValue());
            }
            @Override public void choose(int index) {
                capeIndex = clampIndex(index, capeIds.size());
            }
            @Override public boolean isFixed() { return false; }
            @Override public String fixedLabel() { return ""; }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.cape.none");
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.cape.cosmetic_precedence")));
        if (!this.templateMode) {
            this.controls.add(new CreatorNote(this.context,
                    I18n.format("gui.losttales.character.cape.policy_allowlist")));
        }
    }

    /** The race's numbers, read fresh each frame from whichever race is chosen. */
    private final class RaceAttributes extends CreatorControl {
        private RaceAttributes(CreatorContext context) {
            super(context);
        }

        private CreatorKeyValues table() {
            String raceId = selected(raceIds, raceIndex);
            CharacterRaceGameplayProfile profile = ClientCharacterRaceAttributes.resolve(
                    mc == null ? null : mc.theWorld, raceId);
            return new CreatorKeyValues(this.context,
                    I18n.format("gui.losttales.character.race_attributes"),
                    I18n.format("gui.losttales.character.attribute.lotr_source"))
                    .add(I18n.format("gui.losttales.character.attribute.health"),
                            ClientCharacterRaceAttributes.formatHealth(profile))
                    .add(I18n.format("gui.losttales.character.attribute.movement_speed"),
                            ClientCharacterRaceAttributes.formatMovementSpeed(profile))
                    .add(I18n.format("gui.losttales.character.attribute.attack_damage"),
                            ClientCharacterRaceAttributes.formatAttackDamage(profile))
                    .add(I18n.format("gui.losttales.character.attribute.eye_height"),
                            ClientCharacterRaceAttributes.formatEyeHeight(profile))
                    .add(I18n.format("gui.losttales.character.attribute.hitbox"),
                            ClientCharacterRaceAttributes.formatHitbox(profile));
        }

        @Override
        public int height() {
            CreatorKeyValues table = table();
            table.place(this.x, this.y, this.width);
            return table.height();
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            CreatorKeyValues table = table();
            table.place(this.x, this.y, this.width);
            table.draw(mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Focus and the column's scroll
    // ------------------------------------------------------------------

    private void focus(CreatorControl control) {
        if (this.focusedControl == control) {
            return;
        }
        if (this.focusedControl != null) {
            this.focusedControl.setFocused(false);
        }
        this.focusedControl = control != null && control.canFocus() ? control : null;
        if (this.focusedControl != null) {
            this.focusedControl.setFocused(true);
            scrollIntoView(this.focusedControl);
        }
    }

    private void focusNext(int direction) {
        List<CreatorControl> focusable = new ArrayList<CreatorControl>();
        for (CreatorControl control : this.controls) {
            if (control.canFocus()) {
                focusable.add(control);
            }
        }
        if (focusable.isEmpty()) {
            return;
        }
        int current = focusable.indexOf(this.focusedControl);
        int next = current < 0
                ? (direction > 0 ? 0 : focusable.size() - 1)
                : (current + direction + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    /** Lays the page out at the current scroll and answers its full height. */
    private int placeControls() {
        int x = this.layout.getContentLeft();
        int width = this.layout.getContentWidth();
        int y = this.layout.getContentTop() - this.scroll;
        int total = 0;
        for (CreatorControl control : this.controls) {
            control.place(x, y, width);
            int height = control.height();
            y += height + CONTROL_GAP;
            total += height + CONTROL_GAP;
        }
        this.contentHeight = Math.max(0, total - CONTROL_GAP);
        return this.contentHeight;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.layout.getContentHeight());
    }

    private void scrollBy(int amount) {
        placeControls();
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + amount));
    }

    private void scrollIntoView(CreatorControl control) {
        placeControls();
        int top = control.getY();
        int bottom = top + control.height();
        if (top < this.layout.getContentTop()) {
            this.scroll -= this.layout.getContentTop() - top;
        } else if (bottom > this.layout.getContentBottom()) {
            this.scroll += bottom - this.layout.getContentBottom();
        }
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
    }

    private CreatorControl controlAt(int mouseX, int mouseY) {
        if (!this.layout.isInContent(mouseX, mouseY)) {
            return null;
        }
        placeControls();
        for (CreatorControl control : this.controls) {
            if (control.contains(mouseX, mouseY)) {
                return control;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.layout == null) {
            return;
        }
        pollStageDrag();
        this.pose.advance(System.nanoTime());
        if (this.worldCamera) {
            // The world is the stage, so only the bands the header and the
            // control bar sit on are shaded; the character stays in daylight.
            Gui.drawRect(0, 0, this.width, CharacterCreatorLayout.HEADER_HEIGHT,
                    LostTalesSkyrimUiStyle.withAlpha(LostTalesSkyrimUiStyle.PLUM_BLACK, 0x72));
            Gui.drawRect(0, this.height - CharacterCreatorLayout.FOOTER_HEIGHT,
                    this.width, this.height,
                    LostTalesSkyrimUiStyle.withAlpha(LostTalesSkyrimUiStyle.PLUM_BLACK, 0x72));
        } else {
            LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        }
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format(this.templateMode
                        ? "gui.losttales.character.template.title"
                        : "gui.losttales.character.creation"),
                this.templateMode
                        ? I18n.format("gui.losttales.character.template.subtitle")
                        : I18n.format("gui.losttales.character.slot",
                                Integer.valueOf(this.slotIndex + 1)),
                this.width, 8);

        drawStage(mouseX, mouseY);
        drawColumn(mouseX, mouseY);
        drawStatus();
        drawControlBar();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawColumn(int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawPanel(this.layout.getPanelLeft(),
                this.layout.getPanelTop(), this.layout.getPanelWidth(),
                this.layout.getPanelHeight());
        drawTabs(mouseX, mouseY);

        placeControls();
        int visibleMouseX = this.layout.isInContent(mouseX, mouseY) ? mouseX : -1;
        int visibleMouseY = this.layout.isInContent(mouseX, mouseY) ? mouseY : -1;
        boolean clipped = beginContentClip();
        try {
            LostTalesSkyrimUiStyle.beginContent();
            for (CreatorControl control : this.controls) {
                int top = control.getY();
                if (top + control.height() < this.layout.getContentTop()
                        || top > this.layout.getContentBottom()) {
                    continue;
                }
                control.draw(visibleMouseX, visibleMouseY);
            }
        } finally {
            endContentClip(clipped);
        }
        drawScrollMarks();
        drawButtons(mouseX, mouseY);
    }

    /** The page names across the top of the column, the open one marked. */
    private void drawTabs(int mouseX, int mouseY) {
        int[][] bounds = tabBounds();
        CharacterCreatorCategory[] all = CharacterCreatorCategory.values();
        int stripBottom = this.layout.getTabStripBottom();
        Gui.drawRect(this.layout.getPanelLeft() + 1, stripBottom,
                this.layout.getPanelRight() - 1, stripBottom + 1,
                LostTalesSkyrimUiStyle.BORDER_DIM);
        for (int index = 0; index < all.length; index++) {
            if (all[index] == this.category) {
                Gui.drawRect(bounds[index][0], stripBottom - 2,
                        bounds[index][0] + bounds[index][1], stripBottom,
                        LostTalesSkyrimUiStyle.GOLD);
            }
        }
        LostTalesSkyrimUiStyle.beginContent();
        FontRenderer font = this.fontRendererObj;
        int textY = this.layout.getTabStripTop()
                + (CharacterCreatorLayout.TAB_STRIP_HEIGHT - font.FONT_HEIGHT) / 2;
        for (int index = 0; index < all.length; index++) {
            boolean current = all[index] == this.category;
            boolean hovered = !current && mouseX >= bounds[index][0]
                    && mouseX < bounds[index][0] + bounds[index][1]
                    && this.layout.isInTabStrip(mouseX, mouseY);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    I18n.format(all[index].getLabelKey()), bounds[index][1]);
            font.drawStringWithShadow(label,
                    bounds[index][0] + (bounds[index][1] - font.getStringWidth(label)) / 2,
                    textY, current ? LostTalesSkyrimUiStyle.GOLD
                            : hovered ? LostTalesSkyrimUiStyle.TEXT_BRIGHT
                            : LostTalesSkyrimUiStyle.TEXT_MUTED);
        }
    }

    /**
     * Each tab's left edge and width. The names take their natural width
     * with the slack shared between them; a column too narrow for that
     * gives every tab an equal share and trims the names to it.
     */
    private int[][] tabBounds() {
        CharacterCreatorCategory[] all = CharacterCreatorCategory.values();
        FontRenderer font = this.fontRendererObj;
        int left = this.layout.getPanelLeft() + TAB_INSET;
        int available = this.layout.getPanelWidth() - TAB_INSET * 2;
        int[] widths = new int[all.length];
        int natural = 0;
        for (int index = 0; index < all.length; index++) {
            widths[index] = font.getStringWidth(I18n.format(all[index].getLabelKey())) + 4;
            natural += widths[index];
        }
        int[][] bounds = new int[all.length][2];
        if (natural > available) {
            int share = available / all.length;
            for (int index = 0; index < all.length; index++) {
                bounds[index][0] = left + share * index;
                bounds[index][1] = share;
            }
            return bounds;
        }
        int slack = (available - natural) / all.length;
        int x = left;
        for (int index = 0; index < all.length; index++) {
            bounds[index][0] = x;
            bounds[index][1] = widths[index] + slack;
            x += bounds[index][1];
        }
        return bounds;
    }

    /** Thin marks at the content's edges while there is more above or below. */
    private void drawScrollMarks() {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int left = this.layout.getContentLeft();
        int right = left + this.layout.getContentWidth();
        if (this.scroll > 0) {
            Gui.drawRect(left, this.layout.getContentTop() - 3, right,
                    this.layout.getContentTop() - 2,
                    LostTalesSkyrimUiStyle.withAlpha(LostTalesSkyrimUiStyle.GOLD, 0x90));
        }
        if (this.scroll < max) {
            Gui.drawRect(left, this.layout.getContentBottom() + 1, right,
                    this.layout.getContentBottom() + 2,
                    LostTalesSkyrimUiStyle.withAlpha(LostTalesSkyrimUiStyle.GOLD, 0x90));
        }
        // The scroll's place, as a short bar down the column's inner edge.
        int trackTop = this.layout.getContentTop();
        int trackHeight = this.layout.getContentHeight();
        int barHeight = Math.max(8, trackHeight * trackHeight
                / Math.max(1, this.contentHeight));
        int barTop = trackTop + (trackHeight - barHeight) * this.scroll / max;
        int barX = this.layout.getPanelRight() - 3;
        Gui.drawRect(barX, barTop, barX + 1, barTop + barHeight,
                LostTalesSkyrimUiStyle.BORDER);
        LostTalesSkyrimUiStyle.beginContent();
    }

    private int[] primaryButtonBounds() {
        int width = (this.layout.getContentWidth() - BUTTON_GAP) / 2;
        int x = this.layout.getContentLeft() + width + BUTTON_GAP;
        int y = this.layout.getButtonRowTop()
                + (CharacterCreatorLayout.BUTTON_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        return new int[] {x, y, this.layout.getContentLeft()
                + this.layout.getContentWidth() - x, BUTTON_HEIGHT};
    }

    private int[] secondaryButtonBounds() {
        int width = (this.layout.getContentWidth() - BUTTON_GAP) / 2;
        int y = this.layout.getButtonRowTop()
                + (CharacterCreatorLayout.BUTTON_ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        return new int[] {this.layout.getContentLeft(), y, width, BUTTON_HEIGHT};
    }

    private void drawButtons(int mouseX, int mouseY) {
        int[] primary = primaryButtonBounds();
        int[] secondary = secondaryButtonBounds();
        Gui.drawRect(this.layout.getPanelLeft() + 1, this.layout.getButtonRowTop(),
                this.layout.getPanelRight() - 1, this.layout.getButtonRowTop() + 1,
                LostTalesSkyrimUiStyle.BORDER_DIM);
        CreatorWidgets.drawButton(this.fontRendererObj, secondary[0], secondary[1],
                secondary[2], secondary[3], I18n.format("gui.cancel"), true,
                within(secondary, mouseX, mouseY));
        CreatorWidgets.drawButton(this.fontRendererObj, primary[0], primary[1],
                primary[2], primary[3], primaryLabel(), canSubmit(),
                within(primary, mouseX, mouseY));
    }

    private String primaryLabel() {
        return I18n.format(this.templateMode
                ? "gui.losttales.character.template.save"
                : "gui.losttales.character.create");
    }

    private boolean appearanceReady() {
        return this.raceIds.size() > 0 && this.genderIds.size() > 0
                && this.skinIds.size() > 0;
    }

    private boolean canSubmit() {
        // A template carries no starting waypoint, since every server
        // resolves that against its own map, so it is not something
        // saving one can wait for.
        return !isPending() && appearanceReady()
                && (this.templateMode
                        || (this.factionIds.size() > 0 && this.waypointIds.size() > 0));
    }

    /** The message about the last thing that happened, over the stage. */
    private void drawStatus() {
        if (this.statusMessage.length() == 0 || !this.layout.hasStage()) {
            return;
        }
        LostTalesSkyrimUiStyle.beginContent();
        int width = this.layout.getStageWidth() - 16;
        @SuppressWarnings("unchecked")
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(
                this.statusMessage, Math.max(40, width));
        int y = this.layout.getStageTop() + 4;
        int color = this.statusError
                ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.GREEN;
        for (int index = 0; index < lines.size() && index < 2; index++) {
            String line = lines.get(index);
            this.fontRendererObj.drawStringWithShadow(line,
                    Math.round(this.layout.getFigureCenterX())
                            - this.fontRendererObj.getStringWidth(line) / 2,
                    y, color);
            y += 10;
        }
    }

    // ------------------------------------------------------------------
    // The stage
    // ------------------------------------------------------------------

    private CharacterAppearance currentAppearance(UUID ownerId) {
        String raceId = selected(this.raceIds, this.raceIndex);
        String genderId = selected(this.genderIds, this.genderIndex);
        String skinId = selected(this.skinIds, this.skinIndex);
        if (raceId.length() == 0 || genderId.length() == 0 || skinId.length() == 0) {
            return null;
        }
        return CharacterAppearance.preview(ownerId, raceId, genderId, skinId,
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.chestTypeIds, this.chestTypeIndex),
                this.showMinecraftCape, selectedCapeId());
    }

    /** The account the figure is built for: the signed-in one, else the player. */
    private UUID figureOwner() {
        UUID account = LostTalesClientAccount.id();
        if (account != null) {
            return account;
        }
        return this.mc != null && this.mc.thePlayer != null
                ? this.mc.thePlayer.getUniqueID() : null;
    }

    private void drawStage(int mouseX, int mouseY) {
        if (!this.layout.hasStage()) {
            if (this.worldCamera) {
                frameWorldCamera();
            }
            return;
        }
        if (this.worldCamera) {
            frameWorldCamera();
            drawNameplate();
            return;
        }
        float scale = this.layout.getFigureFitHeight() / FIGURE_HEIGHT_BLOCKS
                * this.pose.getShownZoom();
        // The same framing the borrowed camera uses: the page's point on
        // the body at the middle of the stage, slid by the pan.
        float centerX = this.layout.getFigureCenterX() + this.pose.getShownPanX();
        float stageMiddleY = this.layout.getFigureBaselineY()
                - this.layout.getFigureFitHeight() / 2.0F + this.pose.getShownPanY();
        float feetY = stageMiddleY
                + this.pose.getShownFocus() * FIGURE_HEIGHT_BLOCKS * scale;
        drawStageFloor(centerX, feetY, scale);
        UUID owner = figureOwner();
        CharacterAppearance appearance = currentAppearance(owner);
        boolean drawn = false;
        if (appearance != null && owner != null) {
            float headY = feetY - HEAD_HEIGHT_BLOCKS * scale;
            float headYaw = -this.pose.headYawToward(mouseX - centerX);
            float headPitch = this.pose.headPitchToward(mouseY - headY);
            LostTalesSkyrimUiStyle.beginContent();
            drawn = LostTalesCharacterFigureRenderer.drawPosedFigure(this.mc,
                    owner, appearance, centerX, feetY, scale,
                    this.pose.getShownYaw(), this.pose.getShownPitch(),
                    headYaw, headPitch, 1.0F, 1.0F);
        }
        if (!drawn && appearance != null) {
            // No body could be built, so the face stands in for it.
            LostTalesSkyrimUiStyle.beginContent();
            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(this.mc, owner,
                    appearance.getSkinId(), centerX - FACE_FALLBACK_SIZE / 2.0F,
                    feetY - FACE_FALLBACK_SIZE - 20, FACE_FALLBACK_SIZE, 1.0F, 1.0F);
        }
        drawNameplate();
    }

    /**
     * Tells the borrowed camera where the character should sit this frame
     * and dresses the player in the choices as they stand. The stage's
     * geometry is the same one the drawn figure uses, so the character
     * lands where the figure would have: its middle at the middle of the
     * stage, filling the same share of the stage's height, nearer or
     * farther by the same zoom.
     */
    private void frameWorldCamera() {
        if (this.mc == null || this.mc.thePlayer == null) {
            return;
        }
        // The pan slides the point the character is framed at, the same
        // way it slides the drawn figure: right and down with the pointer.
        double centerX = (this.layout.hasStage()
                ? this.layout.getFigureCenterX() : this.width / 2.0D)
                + this.pose.getShownPanX();
        double centerY = this.layout.getFigureBaselineY()
                - this.layout.getFigureFitHeight() / 2.0D + this.pose.getShownPanY();
        ThirdPersonCameraInspection.frame(this, this.pose.getShownYaw(),
                this.pose.getShownPitch(), this.pose.getShownZoom(),
                InspectionCameraMath.screenX(centerX, this.width),
                InspectionCameraMath.screenY(centerY, this.height),
                this.layout.getFigureFitHeight() / (double)Math.max(1, this.height),
                this.pose.getShownFocus());
        UUID player = this.mc.thePlayer.getUniqueID();
        CharacterAppearance appearance = currentAppearance(player);
        if (appearance != null) {
            // Presentation only: the cache keeps physics on the synced record.
            ClientCharacterAppearanceCache.setPreview(appearance);
        } else {
            ClientCharacterAppearanceCache.clearPreview(player);
        }
    }

    /** A soft pool of light where the figure stands, so it is not afloat. */
    private void drawStageFloor(float centerX, float feetY, float scale) {
        float halfWidth = Math.max(24.0F, scale * 1.4F);
        float left = centerX - halfWidth;
        float right = centerX + halfWidth;
        int color = LostTalesSkyrimUiStyle.SAND;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float peak = 0.30F;
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Two quads across, meeting under the feet, so the band fades in
        // from either end; then the same again below, fading downward.
        floorQuad(tessellator, left, centerX, feetY - 1.0F, feetY + 1.5F,
                red, green, blue, 0.0F, peak, 0.0F, peak);
        floorQuad(tessellator, centerX, right, feetY - 1.0F, feetY + 1.5F,
                red, green, blue, peak, 0.0F, peak, 0.0F);
        floorQuad(tessellator, left, centerX, feetY + 1.5F, feetY + 9.0F,
                red, green, blue, 0.0F, peak * 0.5F, 0.0F, 0.0F);
        floorQuad(tessellator, centerX, right, feetY + 1.5F, feetY + 9.0F,
                red, green, blue, peak * 0.5F, 0.0F, 0.0F, 0.0F);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /** One quad with its own alpha at each corner: top-left, top-right, bottom-left, bottom-right. */
    private static void floorQuad(Tessellator tessellator, float left, float right,
                                  float top, float bottom, float red, float green,
                                  float blue, float topLeft, float topRight,
                                  float bottomLeft, float bottomRight) {
        tessellator.setColorRGBA_F(red, green, blue, topRight);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, topLeft);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, bottomLeft);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, bottomRight);
        tessellator.addVertex(right, bottom, 0.0D);
    }

    /** Who this is, along the foot of the stage: the name, and what it is. */
    private void drawNameplate() {
        FontRenderer font = this.fontRendererObj;
        LostTalesSkyrimUiStyle.beginContent();
        String name = CharacterValidator.normalizeName(currentName());
        if (name.length() == 0) {
            name = I18n.format("gui.losttales.character.creator.unnamed");
        }
        String race = ClientCharacterDisplayNames.race(selected(this.raceIds, this.raceIndex));
        String gender = ClientCharacterDisplayNames.gender(
                selected(this.genderIds, this.genderIndex));
        String facts = I18n.format("gui.losttales.character.creator.nameplate.facts",
                race, gender, Integer.valueOf(Math.max(1, this.draftAge)));
        int y = this.layout.getStageBottom() - 11;
        int left = this.layout.getStageLeft() + 6;
        int right = this.layout.getStageRight() - 6;
        String nameLabel = LostTalesSkyrimUiStyle.uppercase(
                I18n.format("gui.losttales.character.name"));
        int nameLabelWidth = font.getStringWidth(nameLabel);
        int factsWidth = font.getStringWidth(facts);
        int nameRoom = Math.max(30, right - left - nameLabelWidth - factsWidth - 24);
        String shownName = LostTalesSkyrimUiStyle.trimToWidth(font, name, nameRoom);
        Gui.drawRect(left, y - 4, right, y - 3, LostTalesSkyrimUiStyle.BORDER_DIM);
        LostTalesSkyrimUiStyle.beginContent();
        font.drawStringWithShadow(nameLabel, left, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
        font.drawStringWithShadow(shownName, left + nameLabelWidth + 6, y,
                LostTalesSkyrimUiStyle.GOLD);
        font.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(font, facts,
                Math.max(20, right - left - nameLabelWidth - 6 - font.getStringWidth(shownName) - 12)),
                right - factsWidth, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
    }

    private String currentName() {
        return this.nameControl != null ? this.nameControl.getText() : this.draftName;
    }

    private String currentDescription() {
        return this.descriptionControl != null
                ? this.descriptionControl.getText() : this.draftDescription;
    }

    // ------------------------------------------------------------------
    // The control bar
    // ------------------------------------------------------------------

    private void drawControlBar() {
        if (this.mc == null || this.fontRendererObj == null) {
            return;
        }
        List<Hint> hints = new ArrayList<Hint>();
        hints.add(Hint.mouseButton(this.mc, this.fontRendererObj, 0,
                I18n.format("gui.losttales.character.creator.hint.rotate")));
        hints.add(Hint.wheel(this.mc, this.fontRendererObj,
                I18n.format("gui.losttales.character.creator.hint.zoom")));
        hints.add(Hint.mouseButton(this.mc, this.fontRendererObj, 1,
                I18n.format("gui.losttales.character.creator.hint.pan")));
        hints.add(Hint.key(this.mc, this.fontRendererObj, Keyboard.KEY_R,
                I18n.format("gui.losttales.character.creator.hint.reset_view")));
        int leftHints = 4;
        if (this.worldCamera) {
            hints.add(Hint.key(this.mc, this.fontRendererObj, LIGHT_KEY,
                    I18n.format("gui.losttales.character.creator.hint.light")));
            leftHints = 5;
        }
        hints.add(Hint.keyCluster(this.mc, this.fontRendererObj,
                new int[] {Keyboard.KEY_Q, Keyboard.KEY_E}, null,
                I18n.format("gui.losttales.character.creator.hint.page")));
        hints.add(Hint.key(this.mc, this.fontRendererObj, Keyboard.KEY_TAB,
                I18n.format("gui.losttales.character.creator.hint.next_field")));
        hints.add(Hint.key(this.mc, this.fontRendererObj, Keyboard.KEY_RETURN,
                primaryLabel()));
        hints.add(Hint.key(this.mc, this.fontRendererObj, Keyboard.KEY_ESCAPE,
                I18n.format("gui.cancel")));
        String status = I18n.format(this.templateMode
                ? "gui.losttales.character.creator.status.template"
                : "gui.losttales.character.creator.status.server");
        LostTalesControlBar.render(this, this.mc, this.fontRendererObj,
                this.width, this.height, hints, leftHints, 0,
                Arrays.asList(status), true);
    }

    // ------------------------------------------------------------------
    // Clipping
    // ------------------------------------------------------------------

    /** Scissors the column's content in window pixels; false if unavailable. */
    private boolean beginContentClip() {
        if (this.mc == null) {
            return false;
        }
        try {
            ScaledResolution resolution = new ScaledResolution(this.mc,
                    this.mc.displayWidth, this.mc.displayHeight);
            int factor = Math.max(1, resolution.getScaleFactor());
            int left = this.layout.getPanelLeft() + 1;
            int top = this.layout.getContentTop() - 2;
            int right = this.layout.getPanelRight() - 1;
            int bottom = this.layout.getContentBottom() + 1;
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left * factor,
                    (resolution.getScaledHeight() - bottom) * factor,
                    Math.max(0, right - left) * factor,
                    Math.max(0, bottom - top) * factor);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static void endContentClip(boolean clipped) {
        if (clipped) {
            GL11.glPopAttrib();
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.layout == null) {
            return;
        }
        if (button == 0 && this.layout.isInTabStrip(mouseX, mouseY)) {
            int[][] bounds = tabBounds();
            CharacterCreatorCategory[] all = CharacterCreatorCategory.values();
            for (int index = 0; index < all.length; index++) {
                if (mouseX >= bounds[index][0]
                        && mouseX < bounds[index][0] + bounds[index][1]) {
                    showCategory(all[index]);
                    return;
                }
            }
            return;
        }
        if (button == 0 && within(secondaryButtonBounds(), mouseX, mouseY)) {
            cancel();
            return;
        }
        if (button == 0 && within(primaryButtonBounds(), mouseX, mouseY)) {
            if (canSubmit()) {
                submit();
            }
            return;
        }
        CreatorControl control = controlAt(mouseX, mouseY);
        if (control != null) {
            focus(control);
            if (control.mouseClicked(mouseX, mouseY, button)) {
                this.pressedControl = control;
                setStatus("", false);
            }
            return;
        }
        if (this.layout.isInContent(mouseX, mouseY)) {
            focus(null);
            return;
        }
        if (this.layout.isOnStage(mouseX, mouseY)) {
            if (button == 0 || button == 1) {
                // Left turns, right slides; both are read per frame.
                this.draggingStage = button == 0;
                this.panningStage = button == 1;
                this.dragWindowX = Mouse.getX();
                this.dragWindowY = Mouse.getY();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton,
                                  long timeSinceLastClick) {
        if ((this.draggingStage && clickedButton == 0)
                || (this.panningStage && clickedButton == 1)) {
            // The stage's drag is read per frame in pollStageDrag.
            return;
        }
        if (this.pressedControl != null) {
            placeControls();
            this.pressedControl.mouseDragged(mouseX, mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
    }

    /**
     * Reads the pointer straight from the window while the stage is held,
     * so the orbit follows it every frame rather than every tick. Window
     * pixels are brought to interface pixels by the GUI scale, and the
     * window's y runs upward where the screen's runs down.
     */
    private void pollStageDrag() {
        if (!this.draggingStage && !this.panningStage) {
            return;
        }
        int button = this.draggingStage ? 0 : 1;
        if (!Mouse.isButtonDown(button)) {
            this.draggingStage = false;
            this.panningStage = false;
            return;
        }
        int x = Mouse.getX();
        int y = Mouse.getY();
        float toInterface = this.mc == null || this.mc.displayWidth <= 0
                ? 1.0F : this.width / (float)this.mc.displayWidth;
        float deltaX = (x - this.dragWindowX) * toInterface;
        float deltaY = -(y - this.dragWindowY) * toInterface;
        if (this.draggingStage) {
            this.pose.drag(deltaX, deltaY);
        } else {
            this.pose.pan(deltaX, deltaY);
        }
        this.dragWindowX = x;
        this.dragWindowY = y;
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == 0) {
            this.draggingStage = false;
        } else if (state == 1) {
            this.panningStage = false;
        }
        if (this.pressedControl != null) {
            this.pressedControl.mouseReleased();
            this.pressedControl = null;
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.layout != null) {
            int eventMouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int eventMouseY = this.height
                    - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            eventMouseX = LostTalesGuiAnimations.inverseMouseX(this, eventMouseX);
            eventMouseY = LostTalesGuiAnimations.inverseMouseY(this, eventMouseY);
            int notches = wheel / 120;
            if (notches == 0) {
                notches = wheel > 0 ? 1 : -1;
            }
            if (this.layout.isOnStage(eventMouseX, eventMouseY)) {
                this.pose.wheel(notches);
            } else if (this.layout.isInPanel(eventMouseX, eventMouseY)) {
                CreatorControl control = controlAt(eventMouseX, eventMouseY);
                if (control == null || !control.mouseWheel(notches)) {
                    scrollBy(-notches * SCROLL_STEP);
                }
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.focusedControl != null
                && this.focusedControl.keyTyped(typedChar, keyCode)) {
            if (this.statusError) {
                setStatus("", false);
            }
            return;
        }
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                cancel();
                return;
            case Keyboard.KEY_TAB:
                focusNext(isShiftKeyDown() ? -1 : 1);
                return;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                if (canSubmit()) {
                    submit();
                }
                return;
            case Keyboard.KEY_Q:
            case Keyboard.KEY_PRIOR:
                showCategory(this.category.previous());
                return;
            case Keyboard.KEY_E:
            case Keyboard.KEY_NEXT:
                showCategory(this.category.next());
                return;
            case Keyboard.KEY_R:
                this.pose.reset();
                return;
            default:
                if (this.worldCamera && keyCode == LIGHT_KEY) {
                    CreatorCharacterLight.toggle(this);
                    return;
                }
                super.keyTyped(typedChar, keyCode);
        }
    }

    private void cancel() {
        if (this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    private static boolean within(int[] bounds, int mouseX, int mouseY) {
        return mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3];
    }

    // ------------------------------------------------------------------
    // Confirming
    // ------------------------------------------------------------------

    private void submit() {
        captureTextDraft();
        if (isPending()) {
            return;
        }
        if (this.templateMode) {
            saveTemplate();
            return;
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null) {
            setStatus(I18n.format("gui.losttales.character.loading_detail"), true);
            return;
        }
        String normalizedName = CharacterValidator.normalizeName(this.draftName);
        if (normalizedName.length() == 0) {
            setStatus(ClientCharacterDisplayNames.error(
                    CharacterErrorId.INVALID_NAME_EMPTY), true);
            showCategory(CharacterCreatorCategory.IDENTITY);
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
            setStatus(I18n.format("gui.losttales.character.no_options"), true);
            return;
        }
        CharacterCreationRequest request = new CharacterCreationRequest(
                snapshot.getRevision(), this.slotIndex, normalizedName,
                raceId, genderId, skinId, this.draftAge, factionId,
                waypointId, this.unconventionalSettings,
                CharacterValidator.normalizeDescription(this.draftDescription),
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.chestTypeIds, this.chestTypeIndex),
                this.showMinecraftCape, selectedCapeId());
        setStatus(I18n.format("gui.losttales.character.creating"), false);
        this.pendingRequestId = ClientCharacterNetwork.createCharacter(request);
        // What this account starts as on the next world it joins. The
        // creation itself is the server's answer; this only remembers the
        // choices that led to it.
        CharacterTemplateStore.save(LostTalesClientAccount.id(),
                CharacterTemplate.of(request));
    }

    /**
     * Writes the form as the account's template. Nothing is sent and
     * nothing is validated beyond a name worth keeping: which of these
     * choices a particular server offers is that server's to say, and is
     * asked when the form is opened against it.
     */
    private void saveTemplate() {
        String normalizedName = CharacterValidator.normalizeName(this.draftName);
        if (normalizedName.length() == 0) {
            setStatus(ClientCharacterDisplayNames.error(
                    CharacterErrorId.INVALID_NAME_EMPTY), true);
            showCategory(CharacterCreatorCategory.IDENTITY);
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
                CharacterValidator.normalizeDescription(this.draftDescription),
                this.draftAge, this.unconventionalSettings,
                this.showMinecraftCape, selectedCapeId());
        boolean saved = CharacterTemplateStore.save(
                LostTalesClientAccount.id(), template);
        setStatus(I18n.format(saved
                ? "gui.losttales.character.template.saved"
                : "gui.losttales.character.template.unsaved"), !saved);
        if (saved) {
            // In the character room the body keeps wearing what was saved.
            CharacterRoomSession.onTemplateSaved(template);
        }
        if (saved && this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    private void captureTextDraft() {
        if (this.nameControl != null) {
            this.draftName = this.nameControl.getText();
        }
        if (this.descriptionControl != null) {
            this.draftDescription = this.descriptionControl.getText();
        }
        this.draftAge = Math.max(CharacterValidator.MIN_AGE,
                Math.min(CharacterValidator.MAX_AGE, this.draftAge));
    }

    private static int clampIndex(int current, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(current, size - 1));
    }

    private static String selected(List<String> values, int index) {
        return values == null || values.isEmpty() || index < 0 || index >= values.size()
                ? "" : values.get(index);
    }

}
