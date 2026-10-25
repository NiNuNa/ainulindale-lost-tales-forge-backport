package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.camera.StageShot;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.Stage;
import com.ninuna.losttales.client.window.StageSubject;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.client.window.PageAnswer;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.screen.character.creator.CreatorChoice;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorKeyValues;
import com.ninuna.losttales.gui.screen.character.creator.CreatorList;
import com.ninuna.losttales.gui.screen.character.creator.CreatorNote;
import com.ninuna.losttales.gui.screen.character.creator.CreatorSlider;
import com.ninuna.losttales.gui.screen.character.creator.CreatorStepper;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextArea;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTileGrid;
import com.ninuna.losttales.gui.screen.character.creator.CreatorToggle;
import com.ninuna.losttales.gui.screen.character.creator.PlainChoice;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * The Character Editor (CHAR-41): a page of the Profile view that makes a
 * character and changes one's look. Only the game opens it, in a window of
 * its own over the view's window ({@link WindowScreen#openOver}): Create
 * Character on an empty slot, a first visit, and Change Look or Capes on
 * the Characters page. Its sections stand in the side panel, the one
 * picked beside them; the world is its stage, the player standing in the
 * free part of the screen wearing the choices as they stand
 * ({@link StageSubject}).
 *
 * <p>Making a character shows Race, Body, Skin, Identity, Origin and
 * Capes, and Create sends them to the server. Changing one shows Body, Skin
 * and Capes, and Save sends the look, then the capes; name, race, body type and
 * faction stay (CHAR-14), and a lore character keeps its look but may
 * change its capes. Cancel, or the server's yes, closes it. A first visit
 * waits in it and has no Cancel: Create plays the new character at once
 * and the screen closes once the server says so.</p>
 *
 * <p>The server checks every choice again; what the page refuses on its
 * own is only what it can see is empty.</p>
 */
public final class CharacterEditorPage extends PageContent
        implements StageSubject {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "character_editor";
    /** The item its tab wears: a tunic. */
    public static final ItemStack ICON = new ItemStack(Items.leather_chestplate);

    private static final String CREATE = "create";
    private static final String SAVE = "save";
    private static final String CANCEL = "cancel";
    /** What the age starts at when nothing chose one. */
    private static final int DEFAULT_AGE = 25;
    /** Between stacked rows. */
    private static final int ROW_GAP = 4;
    /** Pixels one line of the wheel scrolls the section. */
    private static final int SCROLL_LINE = 10;
    /** The lines the History's box shows at once, as many as Edit Profile's. */
    private static final int HISTORY_LINES = 8;
    /** What is handed to a row while the pointer is off it. */
    private static final int AWAY = Integer.MIN_VALUE / 2;
    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;

    private static final ToolStrip.Panel SECTIONS_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.ITEM, LostTalesUiSheet.ITEM_HOVER,
            "gui.losttales.character.editor.sections.show",
            "gui.losttales.character.editor.sections.hide");

    /** Whether the page makes a character, waits as a first visit, or changes one's look. */
    private enum Purpose {
        CREATE, FIRST, CHANGE
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final PageRows sectionList;
    private Purpose purpose = Purpose.CREATE;
    private int slotIndex;
    /** The character whose look changes; null while one is made. */
    private UUID characterId;

    private List<String> raceIds = Collections.emptyList();
    private List<String> bodyTypeIds = Collections.emptyList();
    private List<String> skinIds = Collections.emptyList();
    private List<String> armsIds = Collections.emptyList();
    private List<String> chestTypeIds = Collections.emptyList();
    private List<String> factionIds = Collections.emptyList();
    private List<String> waypointIds = Collections.emptyList();
    private int raceIndex;
    private int bodyTypeIndex;
    private int skinIndex;
    private int armsIndex = -1;
    private int chestTypeIndex = -1;
    private int factionIndex;
    private int waypointIndex;
    private String draftName = "";
    private int draftAge = DEFAULT_AGE;
    private String draftHistory = "";
    private boolean unconventionalSettings;
    private boolean showMinecraftCape = true;
    /** The cape chosen; it stands while the race and faction may wear it (CHAR-39). */
    private String capeId = CharacterCapeCatalog.NONE_ID;

    private EditorSection section = EditorSection.RACE;
    private boolean listOut = true;
    private CreatorContext context;
    private final List<CreatorControl> controls = new ArrayList<CreatorControl>();
    private CreatorControl focused;
    private CreatorControl held;
    private CreatorTextControl nameControl;
    private CreatorTextArea historyControl;
    /** Pixels the section is asked to be scrolled by, and where it stands, gliding there. */
    private int scroll;
    private double shownScroll;
    private long scrollNanos;
    private int contentHeight;
    /** The rows' box as last laid out, in screen pixels. */
    private LostTalesUiHitBox rowsBox;

    /** The request on its way: a creation, a look, the capes; 0 for none. */
    private int pendingRequestId;
    /** The roster revision the look went with, and whether the capes wait for a newer one. */
    private long lookRevision = -1L;
    private boolean capesWaiting;
    /** Whether a first character was made and the page waits for the roster to say it is played. */
    private boolean joining;

    public CharacterEditorPage() {
        this.sectionList = new PageRows(new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part, boolean back,
                             LostTalesUiHitBox row) {
                EditorSection picked = EditorSection.byId(entry.id);
                if (!back && picked != null) {
                    showSection(picked);
                }
            }
        });
    }

    /* ---- Opening ---- */

    /** The page as registered; null while it is not. */
    private static CharacterEditorPage page() {
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        return content instanceof CharacterEditorPage
                ? (CharacterEditorPage)content : null;
    }

    /** Opens the editor to make a character in {@code slot}, from its first choices (CHAR-04). */
    public static void openToCreate(int slot) {
        CharacterEditorPage page = page();
        if (page != null) {
            page.begin(Purpose.CREATE, slot, null, EditorSection.RACE);
            WindowScreen.openOver(page.tab());
        }
    }

    /**
     * A first visit waits in the editor: opened once for the visit and
     * shown again as it was whenever the player comes back to it.
     */
    public static void openFirst() {
        CharacterEditorPage page = page();
        if (page == null) {
            return;
        }
        if (page.purpose != Purpose.FIRST || !WindowLayout.isOpen(page.tab())) {
            page.begin(Purpose.FIRST, 0, null, EditorSection.RACE);
        }
        WindowScreen.openOver(page.tab());
    }

    /** Opens the editor to change a character's look, at {@code section}: Change Look and Capes. */
    public static void openToChange(UUID characterId, EditorSection section) {
        CharacterEditorPage page = page();
        if (page != null && characterId != null) {
            page.begin(Purpose.CHANGE, 0, characterId, section);
            WindowScreen.openOver(page.tab());
        }
    }

    /** Starts afresh for a purpose: the choices as a new character's, or the character's own. */
    private void begin(Purpose purpose, int slot, UUID character,
                       EditorSection first) {
        this.purpose = purpose;
        this.slotIndex = slot;
        this.characterId = character;
        this.pendingRequestId = 0;
        this.capesWaiting = false;
        this.joining = false;
        this.draftName = "";
        this.draftAge = DEFAULT_AGE;
        this.draftHistory = "";
        this.unconventionalSettings = false;
        this.showMinecraftCape = true;
        this.capeId = CharacterCapeCatalog.NONE_ID;
        this.raceIndex = 0;
        this.bodyTypeIndex = 0;
        this.skinIndex = 0;
        this.armsIndex = -1;
        this.chestTypeIndex = -1;
        this.factionIndex = 0;
        this.waypointIndex = 0;
        this.bodyTypeIds = Collections.emptyList();
        this.skinIds = Collections.emptyList();
        this.factionIds = Collections.emptyList();
        this.waypointIds = Collections.emptyList();
        clearAnswer();
        loadChoices();
        this.section = sections().contains(first) ? first : sections().get(0);
        rebuildControls();
    }

    /** The race, body type, look and capes to start from: the first of each, or the character's own. */
    private void loadChoices() {
        this.raceIds = ClientCharacterDisplayNames.getRaceIds();
        CharacterSummary character = character();
        if (character == null) {
            selectRace(0);
            return;
        }
        this.raceIds = Collections.singletonList(character.getRaceId());
        this.raceIndex = 0;
        this.bodyTypeIds = Collections.singletonList(character.getBodyTypeId());
        this.bodyTypeIndex = 0;
        rebuildSkins();
        this.skinIndex = Math.max(0, this.skinIds.indexOf(character.getSkinId()));
        this.armsIds = ClientCharacterDisplayNames.getArmsIds();
        this.armsIndex = Math.max(0, this.armsIds.indexOf(
                character.getArmsId()));
        this.chestTypeIds = ClientCharacterDisplayNames.getChestTypeIds();
        this.chestTypeIndex = Math.max(0, this.chestTypeIds.indexOf(
                character.getChestTypeId()));
        this.factionIds = Collections.singletonList(character.getFactionId());
        this.factionIndex = 0;
        this.showMinecraftCape = character.isMinecraftCapeVisible();
        this.capeId = character.getCosmeticCapeId();
    }

    /** The character whose look changes, as the roster has it; null while one is made or once it is gone. */
    private CharacterSummary character() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return this.characterId == null || snapshot == null ? null
                : snapshot.getCharacter(this.characterId);
    }

    private boolean isLore() {
        return this.characterId != null && ClientLoreCharacterCache
                .findOwnedCharacter(this.characterId) != null;
    }

    private boolean isChanging() {
        return this.purpose == Purpose.CHANGE;
    }

    /** The sections the page shows: every one while a character is made, Body, Skin and Capes while one changes. */
    private List<EditorSection> sections() {
        return isChanging() ? Arrays.asList(EditorSection.BODY,
                EditorSection.SKIN, EditorSection.CAPES)
                : Arrays.asList(EditorSection.values());
    }

    /** Why a section cannot be opened now: a lore character keeps its look (CHAR-14). */
    private String sectionClosedWhy(EditorSection section) {
        return isChanging() && isLore() && section != EditorSection.CAPES
                ? ClientCharacterDisplayNames.error(
                        CharacterErrorId.LORE_CHARACTER_KEEPS_LOOK)
                : "";
    }

    /* ---- The choices ---- */

    private void selectRace(int index) {
        this.raceIndex = clampIndex(index, this.raceIds.size());
        rebuildBodyTypeOptions();
        rebuildSkins();
        rebuildArms(false);
        rebuildChestTypes(false);
        rebuildFactions();
    }

    private void rebuildBodyTypeOptions() {
        String previousBodyType = selected(this.bodyTypeIds, this.bodyTypeIndex);
        this.bodyTypeIds = ClientCharacterDisplayNames.getCompatibleBodyTypeIds(
                selected(this.raceIds, this.raceIndex));
        int previousIndex = this.bodyTypeIds.indexOf(previousBodyType);
        this.bodyTypeIndex = previousIndex >= 0 ? previousIndex : 0;
    }

    private void selectBodyType(int index) {
        this.bodyTypeIndex = clampIndex(index, this.bodyTypeIds.size());
        rebuildSkins();
        // The body type only picks the first arms and chest; the player may pick others.
        rebuildArms(true);
        rebuildChestTypes(true);
    }

    private void rebuildChestTypes(boolean resetToDefault) {
        this.chestTypeIds = ClientCharacterDisplayNames.getChestTypeIds();
        if (resetToDefault || this.chestTypeIndex < 0) {
            this.chestTypeIndex = Math.max(0, this.chestTypeIds.indexOf(
                    CharacterChestTypeRegistry.defaultFor(selected(
                            this.bodyTypeIds, this.bodyTypeIndex))));
        }
        this.chestTypeIndex = clampIndex(this.chestTypeIndex,
                this.chestTypeIds.size());
    }

    private void rebuildArms(boolean resetToDefault) {
        this.armsIds = ClientCharacterDisplayNames.getArmsIds();
        if (resetToDefault || this.armsIndex < 0) {
            this.armsIndex = Math.max(0, this.armsIds.indexOf(
                    CharacterArmsRegistry.defaultFor(selected(
                            this.bodyTypeIds, this.bodyTypeIndex))));
        }
        this.armsIndex = clampIndex(this.armsIndex,
                this.armsIds.size());
    }

    private void rebuildSkins() {
        String previousSkin = selected(this.skinIds, this.skinIndex);
        this.skinIds = ClientCharacterDisplayNames.getCompatibleSkinIds(
                selected(this.raceIds, this.raceIndex),
                selected(this.bodyTypeIds, this.bodyTypeIndex));
        int previousIndex = this.skinIds.indexOf(previousSkin);
        this.skinIndex = previousIndex >= 0 ? previousIndex
                : clampIndex(this.skinIndex, this.skinIds.size());
    }

    private void rebuildFactions() {
        String previousFaction = selected(this.factionIds, this.factionIndex);
        this.factionIds = ClientCharacterDisplayNames.getFactionIds(
                selected(this.raceIds, this.raceIndex),
                this.unconventionalSettings);
        int previousIndex = this.factionIds.indexOf(previousFaction);
        this.factionIndex = previousIndex >= 0 ? previousIndex
                : clampIndex(this.factionIndex, this.factionIds.size());
        rebuildWaypoints();
    }

    private void rebuildWaypoints() {
        String previousWaypoint = selected(this.waypointIds, this.waypointIndex);
        this.waypointIds = ClientCharacterDisplayNames.getStartingWaypointIds(
                selected(this.factionIds, this.factionIndex),
                this.unconventionalSettings);
        int previousIndex = this.waypointIds.indexOf(previousWaypoint);
        this.waypointIndex = previousIndex >= 0 ? previousIndex
                : clampIndex(this.waypointIndex, this.waypointIds.size());
    }

    private boolean hasArmsChoice() {
        return ClientCharacterDisplayNames.hasArmsChoice(
                selected(this.skinIds, this.skinIndex));
    }

    private boolean hasChestChoice() {
        return ClientCharacterDisplayNames.hasChestChoice(
                selected(this.skinIds, this.skinIndex));
    }

    /**
     * The capes the race and the faction may wear, none first; a changing
     * character keeps the one it wears among them, though its file limits
     * it now (CHAR-39).
     */
    private List<String> capeChoices() {
        CharacterSummary character = character();
        return CharacterCapeCatalog.choicesFor(selected(this.raceIds,
                this.raceIndex), selected(this.factionIds, this.factionIndex),
                character == null ? CharacterCapeCatalog.NONE_ID
                        : character.getCosmeticCapeId());
    }

    /** The cape chosen, or none once the race or faction may not wear it. */
    private String selectedCapeId() {
        return capeChoices().contains(this.capeId) ? this.capeId
                : CharacterCapeCatalog.NONE_ID;
    }

    /* ---- The rows ---- */

    /** Turns to a section: its rows, and the camera's glide to its shot. */
    private void showSection(EditorSection next) {
        if (next == null || next == this.section
                || !sections().contains(next)
                || sectionClosedWhy(next).length() > 0) {
            return;
        }
        captureTextDraft();
        this.section = next;
        rebuildControls();
    }

    /** The section before or after the one shown, round from either end, passing those that cannot open. */
    private void walkSections(int step) {
        List<EditorSection> all = sections();
        int at = all.indexOf(this.section);
        for (int tried = 1; tried < all.size(); tried++) {
            EditorSection next = all.get(((at + step * tried) % all.size()
                    + all.size()) % all.size());
            if (sectionClosedWhy(next).length() == 0) {
                showSection(next);
                return;
            }
        }
    }

    /**
     * Builds the section's rows. Each reads its choice live, so a change in
     * one section shows in the others without building them again.
     */
    private void rebuildControls() {
        captureTextDraft();
        this.context = new CreatorContext(this.mc, this.mc.fontRenderer,
                this.mc.thePlayer == null ? null
                        : this.mc.thePlayer.getUniqueID());
        this.controls.clear();
        this.focused = null;
        this.held = null;
        this.nameControl = null;
        this.historyControl = null;
        this.scroll = 0;
        this.shownScroll = 0.0D;
        if (this.purpose == Purpose.FIRST) {
            this.controls.add(new CreatorNote(this.context,
                    I18n.format("gui.losttales.character.first.subtitle")));
        }
        switch (this.section) {
            case RACE:
                buildRace();
                break;
            case BODY:
                buildBody();
                break;
            case SKIN:
                buildSkin();
                break;
            case IDENTITY:
                buildIdentity();
                break;
            case ORIGIN:
                buildOrigin();
                break;
            case CAPES:
            default:
                buildCapes();
                break;
        }
    }

    private void buildRace() {
        this.controls.add(new CreatorList(this.context, new PlainChoice() {
            @Override public int count() { return raceIds.size(); }
            @Override public int index() { return raceIndex; }
            @Override public String id(int index) { return raceIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.race(raceIds.get(index));
            }
            @Override public void choose(int index) { selectRace(index); }
        }));
        this.controls.add(new RaceAttributes(this.context));
    }

    private void buildBody() {
        if (!isChanging()) {
            this.controls.add(new CreatorStepper(this.context,
                    I18n.format("gui.losttales.character.body_type"),
                    new PlainChoice() {
                @Override public int count() { return bodyTypeIds.size(); }
                @Override public int index() { return bodyTypeIndex; }
                @Override public String id(int index) { return bodyTypeIds.get(index); }
                @Override public String label(int index) {
                    return ClientCharacterDisplayNames.bodyType(bodyTypeIds.get(index));
                }
                @Override public void choose(int index) { selectBodyType(index); }
            }));
        }
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.arms"), new CreatorChoice() {
            @Override public int count() { return armsIds.size(); }
            @Override public int index() { return armsIndex; }
            @Override public String id(int index) { return armsIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.arms(armsIds.get(index));
            }
            @Override public void choose(int index) {
                armsIndex = clampIndex(index, armsIds.size());
                clearRefusal();
            }
            @Override public boolean isFixed() { return !hasArmsChoice(); }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.arms.fixed");
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
                clearRefusal();
            }
            @Override public boolean isFixed() { return !hasChestChoice(); }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.chest.fixed");
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        }));
        if (!isChanging()) {
            this.controls.add(new CreatorNote(this.context,
                    I18n.format("gui.losttales.character.creator.body.note")));
        }
    }

    private void buildSkin() {
        this.controls.add(new CreatorTileGrid(this.context,
                I18n.format("gui.losttales.character.skin"), new PlainChoice() {
            @Override public int count() { return skinIds.size(); }
            @Override public int index() { return skinIndex; }
            @Override public String id(int index) { return skinIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.skin(skinIds.get(index));
            }
            @Override public void choose(int index) {
                skinIndex = clampIndex(index, skinIds.size());
                clearRefusal();
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.skin.note")));
    }

    private void buildIdentity() {
        this.nameControl = new CreatorTextControl(this.context,
                I18n.format("gui.losttales.character.name"), this.draftName,
                CharacterValidator.MAX_NAME_LENGTH, true);
        this.nameControl.setHint(accountNameHint());
        this.controls.add(this.nameControl);
        this.controls.add(new CreatorSlider(this.context,
                I18n.format("gui.losttales.character.age"), new CreatorSlider.IntValue() {
            @Override public int get() { return draftAge; }
            @Override public void set(int value) {
                draftAge = Math.max(CharacterValidator.MIN_AGE, value);
            }
            @Override public int typedMax() { return CharacterValidator.MAX_AGE; }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.age.hint")));
        // A box of several lines, as Edit Profile's, so the History keeps
        // its paragraphs.
        this.historyControl = new CreatorTextArea(this.context,
                I18n.format("gui.losttales.character.profile.history"),
                this.draftHistory,
                CharacterProfile.Section.HISTORY.getMaxLength(), HISTORY_LINES);
        this.controls.add(this.historyControl);
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.profile.history.hint")));
    }

    private void buildOrigin() {
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.starting_faction"),
                new PlainChoice() {
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
            @Override public String emptyLabel() {
                return I18n.format(ClientCharacterDisplayNames.isLotrIntegrationAvailable()
                        ? "gui.losttales.character.no_compatible_faction"
                        : "gui.losttales.character.integration_unavailable");
            }
        }));
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.starting_waypoint"),
                new PlainChoice() {
            @Override public int count() { return waypointIds.size(); }
            @Override public int index() { return waypointIndex; }
            @Override public String id(int index) { return waypointIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.waypoint(waypointIds.get(index));
            }
            @Override public void choose(int index) {
                waypointIndex = clampIndex(index, waypointIds.size());
            }
        }));
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
    }

    private void buildCapes() {
        this.controls.add(new CreatorToggle(this.context,
                I18n.format("gui.losttales.character.creator.cape.minecraft"),
                I18n.format("gui.losttales.character.creator.toggle.on"),
                I18n.format("gui.losttales.character.creator.toggle.off"),
                new CreatorToggle.BooleanValue() {
            @Override public boolean get() { return showMinecraftCape; }
            @Override public void set(boolean value) {
                showMinecraftCape = value;
                clearRefusal();
            }
        }));
        this.controls.add(new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.creator.cape.cosmetic"),
                new PlainChoice() {
            @Override public int count() { return capeChoices().size(); }
            @Override public int index() { return capeChoices().indexOf(selectedCapeId()); }
            @Override public String id(int index) { return capeChoices().get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.cape(capeChoices().get(index));
            }
            @Override public void choose(int index) {
                List<String> choices = capeChoices();
                capeId = choices.get(clampIndex(index, choices.size()));
                clearRefusal();
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.cape.none");
            }
        }));
        this.controls.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.cape.cosmetic_precedence")));
    }

    /** The race's numbers, for whichever race is chosen. */
    private final class RaceAttributes extends CreatorControl {
        /** The table for the race and width it was last built for. */
        private CreatorKeyValues table;
        private String tableRaceId;
        private int tableWidth = -1;

        private RaceAttributes(CreatorContext context) {
            super(context);
        }

        private CreatorKeyValues table() {
            String raceId = selected(raceIds, raceIndex);
            if (this.table == null || this.tableWidth != this.width
                    || !raceId.equals(this.tableRaceId)) {
                CharacterRaceGameplayProfile profile =
                        ClientCharacterRaceAttributes.resolve(
                                mc.theWorld, raceId);
                this.table = new CreatorKeyValues(this.context,
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
                this.tableRaceId = raceId;
                this.tableWidth = this.width;
            }
            this.table.place(this.x, this.y, this.width);
            return this.table;
        }

        @Override
        public int height() {
            return table().height();
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            table().draw(mouseX, mouseY);
        }
    }

    /* ---- Focus and the section's scroll ---- */

    private void focus(CreatorControl control) {
        if (this.focused == control) {
            return;
        }
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        this.focused = control != null && control.canFocus() ? control : null;
        if (this.focused != null) {
            this.focused.setFocused(true);
            scrollIntoView(this.focused);
        }
    }

    private void focusNext(int step) {
        List<CreatorControl> focusable = new ArrayList<CreatorControl>();
        for (CreatorControl control : this.controls) {
            if (control.canFocus()) {
                focusable.add(control);
            }
        }
        if (focusable.isEmpty()) {
            return;
        }
        int at = focusable.indexOf(this.focused);
        int next = at < 0 ? (step > 0 ? 0 : focusable.size() - 1)
                : (at + step + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    /** Lays the rows out from the box's top at the drawn scroll; answers their full height. */
    private int placeControls(LostTalesUiHitBox box) {
        int x = (int)Math.floor(box.left);
        int width = (int)Math.floor(box.width);
        int y = (int)Math.floor(box.top) - (int)Math.round(this.shownScroll);
        int total = 0;
        for (CreatorControl control : this.controls) {
            control.place(x, y, width);
            int height = control.height();
            y += height + ROW_GAP;
            total += height + ROW_GAP;
        }
        this.contentHeight = Math.max(0, total - ROW_GAP);
        return this.contentHeight;
    }

    private int maxScroll() {
        return this.rowsBox == null ? 0
                : Math.max(0, this.contentHeight - (int)this.rowsBox.height);
    }

    private void scrollIntoView(CreatorControl control) {
        if (this.rowsBox == null) {
            return;
        }
        placeControls(this.rowsBox);
        int top = control.getY();
        int bottom = top + control.height();
        if (top < this.rowsBox.top) {
            this.scroll -= (int)(this.rowsBox.top - top);
        } else if (bottom > this.rowsBox.bottom()) {
            this.scroll += (int)(bottom - this.rowsBox.bottom());
        }
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
    }

    /** The drawn scroll glides after the asked one, as a menu's does. */
    private void glideScroll() {
        long now = System.nanoTime();
        double elapsed = this.scrollNanos == 0L ? 0.0D
                : (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        if (Math.abs(this.scroll - this.shownScroll) <= SCROLL_SNAP_PIXELS) {
            this.shownScroll = this.scroll;
            return;
        }
        this.shownScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.shownScroll, this.scroll, elapsed);
    }

    /** The row under a point, where the rows show it; null for none. */
    private CreatorControl controlAt(double x, double y) {
        if (this.rowsBox == null || Double.isNaN(x) || Double.isNaN(y)
                || !this.rowsBox.contains(x, y)) {
            return null;
        }
        placeControls(this.rowsBox);
        int pointX = (int)Math.floor(x);
        int pointY = (int)Math.floor(y);
        for (CreatorControl control : this.controls) {
            if (control.contains(pointX, pointY)) {
                return control;
            }
        }
        return null;
    }

    /* ---- Drawing ---- */

    private CharacterEditorLayout layout(LostTalesUiHitBox box) {
        return new CharacterEditorLayout((int)Math.floor(box.width),
                (int)Math.floor(box.height), this.listOut);
    }

    /** A page box's part, in screen pixels. */
    private static LostTalesUiHitBox within(LostTalesUiHitBox box,
                                            LostTalesUiHitBox part) {
        return new LostTalesUiHitBox(box.left + part.left, box.top + part.top,
                part.width, part.height);
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        CharacterEditorLayout layout = layout(box);
        LostTalesUiHitBox list = within(box, layout.list());
        if (list.width > 0) {
            this.sectionList.setRows(sectionRows());
            this.sectionList.draw(minecraft, list, clipX + (list.left - box.left),
                    clipY + (list.top - box.top), pointerX, pointerY, alpha);
        }
        LostTalesUiHitBox divider = within(box, layout.divider());
        if (divider.width > 0) {
            // The halves part on the windows' standing rule.
            LostTalesUiRules.drawStandingRule((float)divider.left,
                    (float)divider.right(), (float)divider.top,
                    (float)divider.bottom(), alpha);
        }
        LostTalesUiHitBox rows = within(box, layout.section());
        this.rowsBox = rows.width > 0 ? rows : null;
        if (this.rowsBox == null) {
            return;
        }
        placeControls(rows);
        glideScroll();
        placeControls(rows);
        this.context.frame(alpha, WindowLists.pageSurfaceAlpha(minecraft, alpha),
                (int)Math.floor(rows.left), (int)Math.ceil(rows.right()));
        boolean over = !Double.isNaN(pointerX) && !Double.isNaN(pointerY)
                && rows.contains(pointerX, pointerY);
        int mouseX = over ? (int)Math.floor(pointerX) : AWAY;
        int mouseY = over ? (int)Math.floor(pointerY) : AWAY;
        boolean clipped = LostTalesUiClip.beginLocal(minecraft,
                (float)rows.left, (float)rows.top, (float)rows.right(),
                (float)rows.bottom());
        try {
            for (CreatorControl control : this.controls) {
                int top = control.getY();
                if (top + control.height() < rows.top || top > rows.bottom()) {
                    continue;
                }
                control.draw(mouseX, mouseY);
            }
        } finally {
            LostTalesUiClip.end(clipped);
        }
        WindowLists.drawScroll(rows.left, box.top, rows.right(), box.bottom(),
                rows.top, rows.bottom(), this.shownScroll, maxScroll(), alpha);
    }

    /** The section list's rows: each section's name, the one shown chosen, one that cannot open greyed. */
    private List<MenuWindow.Entry> sectionRows() {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (EditorSection each : sections()) {
            MenuWindow.Entry row = new MenuWindow.Entry(each.id(),
                    I18n.format(each.getLabelKey())).chosen(each == this.section);
            String closed = sectionClosedWhy(each);
            rows.add(closed.length() > 0 ? row.unavailable(closed) : row);
        }
        return rows;
    }

    /* ---- The pointer ---- */

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        LostTalesUiHitBox list = within(box, layout(box).list());
        if (list.width > 0 && list.contains(x, y)) {
            return this.sectionList.press(list, x, y, button);
        }
        CreatorControl control = controlAt(x, y);
        if (control != null) {
            focus(control);
            if (control.mouseClicked((int)Math.floor(x), (int)Math.floor(y),
                    button)) {
                this.held = control;
                clearRefusal();
            }
            return true;
        }
        focus(null);
        return false;
    }

    @Override
    public void mouseDragged(Minecraft minecraft, LostTalesUiHitBox box,
                             double x, double y, int button) {
        if (this.held != null && this.rowsBox != null) {
            placeControls(this.rowsBox);
            this.held.mouseDragged((int)Math.floor(x), (int)Math.floor(y));
        }
    }

    @Override
    public void mouseReleased(Minecraft minecraft, LostTalesUiHitBox box,
                              double x, double y, int button) {
        if (this.held != null) {
            this.held.mouseReleased();
            this.held = null;
        }
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        LostTalesUiHitBox list = within(box, layout(box).list());
        if (list.width > 0 && list.contains(x, y)) {
            return this.sectionList.acts(list, x, y);
        }
        CreatorControl control = controlAt(x, y);
        return control != null && control.isPointerOverAction(
                (int)Math.floor(x), (int)Math.floor(y));
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        LostTalesUiHitBox list = within(box, layout(box).list());
        return list.width > 0 && list.contains(x, y)
                ? this.sectionList.tipAt(list, x, y) : "";
    }

    /** The wheel over a row that steps does so; anywhere else on the section it scrolls. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        LostTalesUiHitBox list = within(box, layout(box).list());
        if (list.width > 0 && list.contains(x, y)) {
            this.sectionList.scroll(lines);
            return true;
        }
        if (this.rowsBox == null || !this.rowsBox.contains(x, y) || lines == 0) {
            return false;
        }
        CreatorControl control = controlAt(x, y);
        if (control == null || !control.mouseWheel(lines < 0 ? 1 : -1)) {
            this.scroll = Math.max(0, Math.min(maxScroll(),
                    this.scroll + lines * SCROLL_LINE));
        }
        return true;
    }

    /* ---- The keys ---- */

    @Override
    public boolean holdsKeys() {
        return this.focused != null && this.focused.holdsKeys();
    }

    @Override
    public void focusChanged(boolean hasKeys) {
        if (!hasKeys) {
            focus(null);
        }
    }

    /**
     * A row holding the focus takes its keys first; then Tab walks the
     * rows, Q and E or Page Up and Page Down the sections, Return creates
     * or saves, and L lights the character on the stage.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.focused != null && this.focused.keyTyped(typedChar, keyCode)) {
            clearRefusal();
            return true;
        }
        switch (keyCode) {
            case Keyboard.KEY_TAB:
                focusNext(GuiScreen.isShiftKeyDown() ? -1 : 1);
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                if (whyNot().length() == 0) {
                    confirm();
                }
                return true;
            case Keyboard.KEY_Q:
            case Keyboard.KEY_PRIOR:
                walkSections(-1);
                return true;
            case Keyboard.KEY_E:
            case Keyboard.KEY_NEXT:
                walkSections(1);
                return true;
            case Keyboard.KEY_L:
                Stage.toggleLight();
                return true;
            default:
                return false;
        }
    }

    @Override
    public List<PageKeys.Area> keyAreas() {
        List<PageKeys.Area> areas = new ArrayList<PageKeys.Area>();
        areas.add(PageKeys.area("gui.losttales.page.character_editor",
                PageKeys.pageKey(PAGE_ID, "sections", Keyboard.KEY_Q,
                        PageKeys.OR, Keyboard.KEY_E),
                PageKeys.pageKey(PAGE_ID, "next_row", Keyboard.KEY_TAB),
                PageKeys.pageKey(PAGE_ID, "confirm", Keyboard.KEY_RETURN),
                PageKeys.pageKey(PAGE_ID, "light", Keyboard.KEY_L)));
        areas.add(Stage.keyArea());
        return areas;
    }

    /* ---- The window's strip ---- */

    @Override
    public ToolStrip.Panel panel() {
        return SECTIONS_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.listOut;
    }

    @Override
    public void togglePanel() {
        this.listOut = !this.listOut;
    }

    @Override
    public void resetPanel() {
        this.listOut = true;
    }

    /** The tab says what the editor does: *New Character*, or *Change Look* (CHAR-41). */
    @Override
    public String tabTitle() {
        return I18n.format(isChanging()
                ? "gui.losttales.page.character_editor.change"
                : "gui.losttales.page.character_editor.new");
    }

    /** While a look changes, only as long as its character stands in the roster. */
    @Override
    public boolean isAvailable() {
        return this.purpose != Purpose.CHANGE || character() != null;
    }

    /* ---- The bar ---- */

    /**
     * Create Character, or Save while a look changes, then Cancel; a first
     * visit has no Cancel. Create and Save stand greyed with the reason
     * while they cannot be taken.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(2);
        String why = whyNot();
        if (isChanging()) {
            String save = I18n.format("gui.losttales.character.look.save");
            items.add(orWhy(BarItem.button(SAVE, save, new ItemStack(Items.paper))
                    .tip(WindowBar.withKey(save, Keyboard.KEY_RETURN)), why));
        } else {
            String create = I18n.format("gui.losttales.character.create");
            items.add(orWhy(BarItem.button(CREATE, create,
                    LostTalesUiSheet.PLUS, LostTalesUiSheet.PLUS_ADD)
                    .tip(WindowBar.withKey(create, Keyboard.KEY_RETURN)), why));
        }
        if (this.purpose != Purpose.FIRST) {
            String cancel = I18n.format("gui.cancel");
            items.add(BarItem.button(CANCEL, cancel, LostTalesUiSheet.REPLY,
                    LostTalesUiSheet.REPLY_HOVER).tip(cancel));
        }
        return items;
    }

    private static BarItem orWhy(BarItem item, String why) {
        return why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (CANCEL.equals(id)) {
            close();
        } else if ((CREATE.equals(id) || SAVE.equals(id))
                && whyNot().length() == 0) {
            confirm();
        }
    }

    /** Why Create or Save cannot be taken now; empty while it can. */
    private String whyNot() {
        if (isPending() || this.joining) {
            return I18n.format("gui.losttales.character.working");
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null) {
            return I18n.format("gui.losttales.character.loading_detail");
        }
        if (this.purpose == Purpose.FIRST && snapshot.isStoreUnreadable()) {
            return I18n.format("gui.losttales.character.first.unreadable");
        }
        if (isChanging()) {
            return lookChanged() || capesChanged() ? ""
                    : I18n.format("gui.losttales.character.editor.unchanged");
        }
        return this.raceIds.isEmpty() || this.bodyTypeIds.isEmpty()
                || this.skinIds.isEmpty() || this.factionIds.isEmpty()
                || this.waypointIds.isEmpty()
                ? I18n.format("gui.losttales.character.no_options") : "";
    }

    /* ---- Requests ---- */

    private boolean isPending() {
        return this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(
                        this.pendingRequestId);
    }

    private void confirm() {
        captureTextDraft();
        if (isChanging()) {
            save();
        } else {
            create();
        }
    }

    private void create() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null) {
            return;
        }
        String name = CharacterValidator.normalizeName(this.draftName);
        if (name.length() == 0) {
            name = accountNameHint();
        }
        if (name.length() == 0) {
            sayRefused(ClientCharacterDisplayNames.error(
                    CharacterErrorId.INVALID_NAME_EMPTY));
            showSection(EditorSection.IDENTITY);
            return;
        }
        CharacterCreationRequest request = new CharacterCreationRequest(
                snapshot.getRevision(), this.slotIndex, name,
                selected(this.raceIds, this.raceIndex),
                selected(this.bodyTypeIds, this.bodyTypeIndex),
                selected(this.skinIds, this.skinIndex), this.draftAge,
                selected(this.factionIds, this.factionIndex),
                selected(this.waypointIds, this.waypointIndex),
                this.unconventionalSettings,
                CharacterValidator.normalizeSection(this.draftHistory),
                selected(this.armsIds, this.armsIndex),
                selected(this.chestTypeIds, this.chestTypeIndex),
                this.showMinecraftCape, selectedCapeId());
        sayWorking(I18n.format("gui.losttales.character.creating"));
        this.pendingRequestId = ClientCharacterNetwork.createCharacter(request);
    }

    /** Save: the look first, then the capes once the roster that kept the look has come back. */
    private void save() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || character() == null) {
            return;
        }
        if (lookChanged()) {
            this.lookRevision = snapshot.getRevision();
            this.capesWaiting = capesChanged();
            sayWorking(I18n.format("gui.losttales.character.look.saving"));
            this.pendingRequestId = ClientCharacterNetwork.updateLook(
                    this.lookRevision, this.characterId,
                    selected(this.skinIds, this.skinIndex),
                    selected(this.armsIds, this.armsIndex),
                    selected(this.chestTypeIds, this.chestTypeIndex));
        } else if (capesChanged()) {
            sendCapes(snapshot);
        }
    }

    private void sendCapes(CharacterRosterSnapshot snapshot) {
        this.capesWaiting = false;
        sayWorking(I18n.format("gui.losttales.character.cape.saving"));
        this.pendingRequestId = ClientCharacterNetwork.updateCapeSettings(
                snapshot.getRevision(), this.characterId,
                this.showMinecraftCape, selectedCapeId());
    }

    /** Whether the look differs from the character's, which a lore character keeps whatever is chosen. */
    private boolean lookChanged() {
        CharacterSummary character = character();
        return character != null && !isLore()
                && !(selected(this.skinIds, this.skinIndex).equals(character.getSkinId())
                        && selected(this.armsIds, this.armsIndex)
                                .equals(character.getArmsId())
                        && selected(this.chestTypeIds, this.chestTypeIndex)
                                .equals(character.getChestTypeId()));
    }

    private boolean capesChanged() {
        CharacterSummary character = character();
        return character != null
                && (this.showMinecraftCape != character.isMinecraftCapeVisible()
                        || !selectedCapeId().equals(character.getCosmeticCapeId()));
    }

    /** Follows the request on its way, and a first visit's wait for the roster to play its character. */
    @Override
    public void tick() {
        for (CreatorControl control : this.controls) {
            control.tick();
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        // A first visit says over the bar why it waits while the server
        // cannot read characters, for as long as that lasts.
        if (this.purpose == Purpose.FIRST && snapshot != null) {
            boolean unreadable = snapshot.isStoreUnreadable();
            boolean saying = lastAnswer().kind() == PageAnswer.Kind.FAULT;
            if (unreadable && !saying) {
                sayFault(I18n.format("gui.losttales.character.first.unreadable"));
            } else if (!unreadable && saying) {
                clearAnswer();
            }
        }
        if (this.joining) {
            if (snapshot != null && !snapshot.isWaitingForFirstCharacter()) {
                this.joining = false;
                close();
                WindowScreen screen = WindowScreen.current();
                if (screen != null) {
                    screen.closeScreen();
                }
            }
            return;
        }
        if (this.capesWaiting && this.pendingRequestId == 0 && snapshot != null
                && snapshot.getRevision() > this.lookRevision) {
            sendCapes(snapshot);
            return;
        }
        if (this.pendingRequestId == 0 || isPending()) {
            return;
        }
        int completed = this.pendingRequestId;
        this.pendingRequestId = 0;
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(completed);
        if (feedback == null) {
            clearAnswer();
            this.capesWaiting = false;
            return;
        }
        ClientCharacterRosterCache.clearOperation(completed);
        if (!feedback.isSuccessful()) {
            this.capesWaiting = false;
            sayRefused(ClientCharacterDisplayNames.error(feedback));
            return;
        }
        if (this.capesWaiting) {
            // The capes go with the revision the kept look brought; with no
            // roster to follow, the one known now is that revision.
            if (!feedback.isRosterFollows() && snapshot != null) {
                sendCapes(snapshot);
            }
            return;
        }
        finish(feedback);
    }

    /**
     * The server's yes: a first character waits for the roster to play
     * it; a new character is picked on the Characters page; a look saved
     * says so there. The editor closes.
     */
    private void finish(CharacterOperationFeedback feedback) {
        if (this.purpose == Purpose.FIRST) {
            this.joining = true;
            sayWorking(ClientCharacterDisplayNames.operationSuccess("create"));
            return;
        }
        String done = ClientCharacterDisplayNames.operationSuccess(
                feedback.getOperationType().getId());
        int slot = this.purpose == Purpose.CREATE ? this.slotIndex : -1;
        UUID changed = this.characterId;
        close();
        CharactersPage characters = CharactersPage.current();
        if (characters != null) {
            characters.afterEditor(slot, changed, done);
        }
    }

    /** Closes the page, its window with it: Cancel, or the server's yes. */
    private void close() {
        captureTextDraft();
        final WindowPage tab = tab();
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowPage open) {
                return open.equals(tab);
            }
        });
        WindowLayout.persist();
    }

    private void clearRefusal() {
        if (lastAnswer().kind() == PageAnswer.Kind.REFUSED) {
            clearAnswer();
        }
    }

    /* ---- The stage ---- */

    @Override
    public CharacterAppearance stageLook(UUID playerId) {
        String raceId = selected(this.raceIds, this.raceIndex);
        String bodyTypeId = selected(this.bodyTypeIds, this.bodyTypeIndex);
        String skinId = selected(this.skinIds, this.skinIndex);
        if (playerId == null || raceId.length() == 0 || bodyTypeId.length() == 0
                || skinId.length() == 0) {
            return null;
        }
        return CharacterAppearance.preview(playerId, raceId, bodyTypeId, skinId,
                selected(this.armsIds, this.armsIndex),
                selected(this.chestTypeIds, this.chestTypeIndex),
                this.showMinecraftCape, selectedCapeId());
    }

    /**
     * What the player wears with the choices (CHAR-42): nothing of theirs
     * while a character is made; while a look changes, that character's
     * own armour and held item, the player's own for the one played.
     */
    @Override
    public StageSubject.Gear stageGear() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (this.purpose != Purpose.CHANGE || character() == null
                || snapshot == null) {
            return StageSubject.Gear.NONE;
        }
        return this.characterId.equals(snapshot.getActiveCharacterId())
                ? StageSubject.Gear.OWN
                : StageSubject.Gear.of(this.characterId);
    }

    @Override
    public StageShot stageShot() {
        return this.section.getShot();
    }

    @Override
    public String stageName() {
        CharacterSummary character = character();
        if (character != null) {
            return character.getName();
        }
        String name = CharacterValidator.normalizeName(currentName());
        return name.length() > 0 ? name
                : I18n.format("gui.losttales.character.creator.unnamed");
    }

    @Override
    public String stageFacts() {
        CharacterSummary character = character();
        int age = character != null ? character.getAge() : this.draftAge;
        return I18n.format("gui.losttales.character.creator.nameplate.facts",
                ClientCharacterDisplayNames.race(selected(this.raceIds,
                        this.raceIndex)),
                ClientCharacterDisplayNames.bodyType(selected(this.bodyTypeIds,
                        this.bodyTypeIndex)), Integer.valueOf(Math.max(1, age)));
    }

    /* ---- Names ---- */

    /** The name typed, or the account's name shown as the example while nothing is. */
    private String currentName() {
        String typed = this.nameControl != null ? this.nameControl.getText()
                : this.draftName;
        return CharacterValidator.normalizeName(typed).length() > 0 ? typed
                : accountNameHint();
    }

    /**
     * The account's own name, offered faintly in the empty name field as
     * the name a character takes when none is typed: while it has a
     * character name's shape and none of the player's characters goes by
     * it. Empty otherwise.
     */
    private String accountNameHint() {
        String account = this.mc.thePlayer == null ? ""
                : this.mc.thePlayer.getCommandSenderName();
        if (account == null || !CharacterValidator.isWellFormedName(account)) {
            return "";
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null) {
            for (CharacterSummary character : snapshot.getCharacters()) {
                if (CharacterNames.same(account, character.getName())) {
                    return "";
                }
            }
        }
        return CharacterValidator.normalizeName(account);
    }

    private void captureTextDraft() {
        if (this.nameControl != null) {
            this.draftName = this.nameControl.getText();
        }
        if (this.historyControl != null) {
            this.draftHistory = this.historyControl.getText();
        }
        this.draftAge = Math.max(CharacterValidator.MIN_AGE,
                Math.min(CharacterValidator.MAX_AGE, this.draftAge));
    }

    private static int clampIndex(int current, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(current, size - 1));
    }

    private static String selected(List<String> values, int index) {
        return values == null || values.isEmpty() || index < 0
                || index >= values.size() ? "" : values.get(index);
    }
}
