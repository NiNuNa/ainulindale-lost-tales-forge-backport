package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.creator.CreatorChoice;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorRows;
import com.ninuna.losttales.gui.screen.character.creator.CreatorStepper;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTileGrid;
import com.ninuna.losttales.gui.screen.character.creator.PlainChoice;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Change Look: a character's skin, arm width and chest, in a
 * sub-window of the Characters page's window, its player's to change at any
 * time. The skins are the grid of faces the creator shows — every skin the
 * character's race and sex may wear, the account's own among them where
 * the race allows it — under a heading naming the chosen one, scrolled by
 * whole rows of faces a few at a time; under it the arm width and the
 * chest as rows of the windows' Settings, fixed where the skin decides
 * them. Name, race, sex and faction are not here. Save sends the look and
 * the window closes once the server has kept it; Cancel, the cross and
 * Escape leave it as it was. The figure on the page wears the look while
 * the window is open.
 */
final class LookEditWindow extends SubWindowContent {
    private static final String SAVE = "save";
    private static final String CANCEL = "cancel";
    private static final String CONTROL = "control";
    private static final int WIDTH = 300;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The rows of faces the grid shows at once; the wheel scrolls the rest. */
    private static final int GRID_ROWS = 4;
    /** How often the rows keep time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;
    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;
    /** What is drawn under the pointer while it is off a part. */
    private static final int AWAY = Integer.MIN_VALUE / 2;

    private final UUID characterId;
    private final LostTalesUiButtonMotion saveMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private List<String> skinIds = Collections.emptyList();
    private final List<String> bodyTypeIds = new ArrayList<String>(
            ClientCharacterDisplayNames.getBodyTypeIds());
    private final List<String> chestTypeIds = new ArrayList<String>(
            ClientCharacterDisplayNames.getChestTypeIds());
    private int skinIndex;
    private int bodyTypeIndex;
    private int chestTypeIndex;
    private CreatorContext context;
    private CreatorTileGrid grid;
    private CreatorStepper body;
    private CreatorStepper chest;
    private final List<CreatorControl> controls = new ArrayList<CreatorControl>();
    /** Pixels the grid is asked to be scrolled by, a whole row of faces at a time; the drawn offset glides after it. */
    private int gridScroll;
    private double renderedGridScroll;
    private long gridScrollNanos;
    /** The height of the band the grid shows in, as last laid out. */
    private int gridBand;
    private CreatorControl focused;
    private CreatorControl held;
    private CreatorControl hovered;
    private int pendingRequestId;
    private String status = "";
    private boolean statusError;
    private long tickedNanos;

    LookEditWindow(UUID characterId) {
        this.characterId = characterId;
        restart();
    }

    /** The window open for that character; null while none is. */
    static LookEditWindow openFor(UUID characterId) {
        WindowScreen screen = WindowScreen.current();
        SubWindow window = screen == null || characterId == null ? null
                : screen.subWindows().find(CharacterSubWindows.LOOK_EDIT,
                        characterId.toString());
        return window != null && window.isOpen()
                && window.content instanceof LookEditWindow
                ? (LookEditWindow) window.content : null;
    }

    /** Starts again from the look the character wears now: the window opened afresh. */
    void restart() {
        CharacterSummary character = character();
        String raceId = character == null ? "" : character.getRaceId();
        String genderId = character == null ? "" : character.getGenderId();
        this.skinIds = ClientCharacterDisplayNames.getCompatibleSkinIds(raceId,
                genderId);
        this.skinIndex = character == null ? 0
                : Math.max(0, this.skinIds.indexOf(character.getSkinId()));
        this.bodyTypeIndex = Math.max(0, this.bodyTypeIds.indexOf(
                character == null ? CharacterBodyTypeRegistry.defaultFor(genderId)
                        : character.getBodyTypeId()));
        this.chestTypeIndex = Math.max(0, this.chestTypeIds.indexOf(
                character == null ? CharacterChestTypeRegistry.defaultFor(genderId)
                        : character.getChestTypeId()));
        build();
        this.gridScroll = 0;
        this.renderedGridScroll = 0.0D;
        this.status = "";
        this.statusError = false;
        this.pendingRequestId = 0;
    }

    private CharacterSummary character() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null ? null : snapshot.getCharacter(this.characterId);
    }

    private void build() {
        Minecraft minecraft = Minecraft.getMinecraft();
        this.context = new CreatorContext(minecraft, minecraft.fontRenderer,
                minecraft.thePlayer == null ? null
                        : minecraft.thePlayer.getUniqueID(),
                CreatorContext.Presentation.ROWS);
        this.grid = new CreatorTileGrid(this.context, null, new PlainChoice() {
            @Override public int count() { return skinIds.size(); }
            @Override public int index() { return skinIndex; }
            @Override public String id(int index) { return skinIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.skin(skinIds.get(index));
            }
            @Override public void choose(int index) {
                skinIndex = Math.max(0, Math.min(index, skinIds.size() - 1));
                clearError();
            }
        });
        this.body = new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.body"), new CreatorChoice() {
            @Override public int count() { return bodyTypeIds.size(); }
            @Override public int index() { return bodyTypeIndex; }
            @Override public String id(int index) { return bodyTypeIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.bodyType(bodyTypeIds.get(index));
            }
            @Override public void choose(int index) {
                bodyTypeIndex = Math.max(0, Math.min(index, bodyTypeIds.size() - 1));
                clearError();
            }
            @Override public boolean isFixed() {
                return !ClientCharacterDisplayNames.hasBodyTypeChoice(skinId());
            }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.body.fixed");
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        });
        this.chest = new CreatorStepper(this.context,
                I18n.format("gui.losttales.character.chest"), new CreatorChoice() {
            @Override public int count() { return chestTypeIds.size(); }
            @Override public int index() { return chestTypeIndex; }
            @Override public String id(int index) { return chestTypeIds.get(index); }
            @Override public String label(int index) {
                return ClientCharacterDisplayNames.chestType(chestTypeIds.get(index));
            }
            @Override public void choose(int index) {
                chestTypeIndex = Math.max(0, Math.min(index, chestTypeIds.size() - 1));
                clearError();
            }
            @Override public boolean isFixed() {
                return !ClientCharacterDisplayNames.hasChestChoice(skinId());
            }
            @Override public String fixedLabel() {
                return I18n.format("gui.losttales.character.chest.fixed");
            }
            @Override public String emptyLabel() {
                return I18n.format("gui.losttales.character.no_options");
            }
        });
        this.controls.clear();
        this.controls.add(this.grid);
        this.controls.add(this.body);
        this.controls.add(this.chest);
        this.focused = null;
        this.held = null;
        this.hovered = null;
    }

    private static String selected(List<String> ids, int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : "";
    }

    private String skinId() {
        return selected(this.skinIds, this.skinIndex);
    }

    private String bodyTypeId() {
        return selected(this.bodyTypeIds, this.bodyTypeIndex);
    }

    private String chestTypeId() {
        return selected(this.chestTypeIds, this.chestTypeIndex);
    }

    /** The look as the window has it, for the figure on the page; null once the character is gone. */
    CharacterAppearance draftLook(UUID ownerId) {
        CharacterSummary character = character();
        return character == null || ownerId == null ? null
                : CharacterAppearance.preview(ownerId, character.getRaceId(),
                        character.getGenderId(), skinId(), bodyTypeId(),
                        chestTypeId(), character.isMinecraftCapeVisible(),
                        character.getCosmeticCapeId());
    }

    /* ---- Where things stand ---- */

    private static int inner(LostTalesUiHitBox box) {
        return (int)box.width - 2 * PADDING_X;
    }

    /** The band the grid shows in: as tall as its rows, a few of them at most, ending on a row's foot. */
    private int gridBoxHeight(int width) {
        this.grid.place(0, 0, width);
        return Math.min(this.grid.height(),
                CreatorTileGrid.rowsHeight(GRID_ROWS));
    }

    /** Where the heading naming the chosen skin stands. */
    private static int headingTop(LostTalesUiHitBox box) {
        return (int)box.top + PADDING_Y;
    }

    /** Where the grid's band starts: under the heading, a padding clear of its hairline. */
    private static int gridTop(LostTalesUiHitBox box) {
        return headingTop(box) + CreatorRows.height() + PADDING_Y;
    }

    /** Where the status line stands: a note's height over Cancel and Save. */
    private static int statusTop(LostTalesUiHitBox box) {
        return (int)(box.top + box.height) - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT - CreatorRows.noteHeight(1);
    }

    /**
     * Places the rows from the box's top left: the grid from its drawn
     * offset, its scroll kept within its rows, and the two steppers under
     * its band. Answers the furthest the grid scrolls.
     */
    private int layOut(LostTalesUiHitBox box) {
        int left = (int)box.left + PADDING_X;
        int width = inner(box);
        this.gridBand = gridBoxHeight(width);
        int maxScroll = Math.max(0, this.grid.height() - this.gridBand);
        this.gridScroll = Math.max(0, Math.min(this.gridScroll, maxScroll));
        this.renderedGridScroll = Math.max(0.0D, Math.min(maxScroll,
                this.renderedGridScroll));
        placeGrid(box);
        int y = gridTop(box) + this.gridBand + PADDING_Y;
        this.body.place(left, y, width);
        this.chest.place(left, y + this.body.height(), width);
        return maxScroll;
    }

    /** The grid at its drawn offset in the band. */
    private void placeGrid(LostTalesUiHitBox box) {
        this.grid.place((int)box.left + PADDING_X, gridTop(box)
                - (int)Math.round(this.renderedGridScroll), inner(box));
    }

    /** The grid's drawn scroll glides after the asked one with the windows' shared scroll motion, as a menu's does. */
    private void glideGridScroll() {
        long now = System.nanoTime();
        double elapsed = this.gridScrollNanos == 0L ? 0.0D
                : (now - this.gridScrollNanos) / 1.0E9D;
        this.gridScrollNanos = now;
        if (Math.abs(this.gridScroll - this.renderedGridScroll)
                <= SCROLL_SNAP_PIXELS) {
            this.renderedGridScroll = this.gridScroll;
            return;
        }
        this.renderedGridScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.renderedGridScroll, this.gridScroll, elapsed);
    }

    /** Whether a point is on the band the grid shows in, across the whole box, as last laid out. */
    private boolean inGridBox(LostTalesUiHitBox box, double x, double y) {
        return LostTalesUiHitBox.contains(x, y, box.left, gridTop(box),
                box.width, this.gridBand);
    }

    /* ---- Saving ---- */

    private boolean canSave() {
        CharacterSummary character = character();
        return this.pendingRequestId == 0 && character != null
                && skinId().length() > 0
                && (!skinId().equals(character.getSkinId())
                        || !bodyTypeId().equals(character.getBodyTypeId())
                        || !chestTypeId().equals(character.getChestTypeId()));
    }

    private void save() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || !canSave()) {
            return;
        }
        this.status = I18n.format("gui.losttales.character.look.saving");
        this.statusError = false;
        this.pendingRequestId = ClientCharacterNetwork.updateLook(
                snapshot.getRevision(), this.characterId, skinId(),
                bodyTypeId(), chestTypeId());
    }

    /** The server's answer: kept closes the window, refused says why. */
    private void followRequest() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(
                        this.pendingRequestId)) {
            return;
        }
        int completed = this.pendingRequestId;
        this.pendingRequestId = 0;
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(completed);
        if (feedback == null) {
            return;
        }
        ClientCharacterRosterCache.clearOperation(completed);
        if (!feedback.isSuccessful()) {
            this.status = ClientCharacterDisplayNames.error(feedback);
            this.statusError = true;
            return;
        }
        close();
    }

    private void close() {
        WindowScreen screen = WindowScreen.current();
        SubWindow window = screen == null ? null : screen.subWindows().find(
                CharacterSubWindows.LOOK_EDIT, this.characterId.toString());
        if (window != null) {
            screen.subWindows().close(window);
        }
    }

    private void clearError() {
        if (this.statusError) {
            this.status = "";
            this.statusError = false;
        }
    }

    /* ---- The window ---- */

    @Override
    public String stripTitle() {
        CharacterSummary character = character();
        return character == null ? null : I18n.format(
                "gui.losttales.character.look.title_of", character.getName());
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    @Override
    public int naturalWidth() {
        return WIDTH;
    }

    /** The heading, the grid's band, the two steppers, the status line and the foot's buttons. */
    @Override
    public int naturalHeight(int width) {
        int inner = width - 2 * PADDING_X;
        return PADDING_Y + CreatorRows.height() + PADDING_Y
                + gridBoxHeight(inner) + PADDING_Y + this.body.height()
                + this.chest.height() + CreatorRows.noteHeight(1)
                + LostTalesUiFramedButton.HEIGHT + PADDING_Y;
    }

    @Override
    public int minWidth() {
        return 200;
    }

    @Override
    public int minHeight() {
        return naturalHeight(minWidth());
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        keepTime();
        followRequest();
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left + PADDING_X;
        int inner = inner(box);
        int maxScroll = layOut(box);
        glideGridScroll();
        placeGrid(box);
        this.context.frame(alpha, surfaceAlpha, (int)box.left,
                (int)(box.left + box.width));
        boolean pointed = !Double.isNaN(pointerX) && !Double.isNaN(pointerY);
        int mouseX = pointed ? (int)Math.floor(pointerX) : AWAY;
        int mouseY = pointed ? (int)Math.floor(pointerY) : AWAY;
        String part = partAt(font, box, pointerX, pointerY);
        String skinName = skinId().length() == 0 ? ""
                : ClientCharacterDisplayNames.skin(skinId());
        WindowLists.drawHeading(font, I18n.format(
                        "gui.losttales.character.look.skin", skinName), left,
                left, left + inner, headingTop(box), CreatorRows.height(),
                false, alpha);
        int gridHeight = this.gridBand;
        boolean clipped = SubWindowContent.beginClip(minecraft, clipX,
                clipY + (gridTop(box) - box.top), box.width, gridHeight);
        try {
            boolean overGrid = inGridBox(box, pointerX, pointerY);
            this.grid.draw(overGrid ? mouseX : AWAY, overGrid ? mouseY : AWAY);
        } finally {
            SubWindowContent.endClip(clipped);
        }
        WindowLists.drawScroll(box.left, gridTop(box), box.left + box.width,
                gridTop(box) + gridHeight, gridTop(box),
                gridTop(box) + gridHeight, this.renderedGridScroll, maxScroll,
                alpha);
        this.body.draw(mouseX, mouseY);
        this.chest.draw(mouseX, mouseY);
        String said = statusText();
        if (said.length() > 0) {
            CreatorRows.drawNote(this.context, Collections.singletonList(
                    font.trimStringToWidth(said, inner)), left, statusTop(box),
                    this.statusError ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb());
        }
        WordButton.draw(font, buttonBox(font, box, CANCEL), cancelLabel(),
                false, true, CANCEL.equals(part), this.cancelMotion, alpha,
                surfaceAlpha);
        WordButton.draw(font, buttonBox(font, box, SAVE), saveLabel(), false,
                canSave(), SAVE.equals(part), this.saveMotion, alpha,
                surfaceAlpha);
    }

    /** What the status line says: the server's refusal, or the save under way. */
    private String statusText() {
        if (this.status.length() > 0) {
            return this.status;
        }
        return this.skinIds.isEmpty()
                ? I18n.format("gui.losttales.character.no_options") : "";
    }

    /** The rows keep time as a screen's ticks would. */
    private void keepTime() {
        long now = System.nanoTime();
        if (now - this.tickedNanos < TICK_NANOS) {
            return;
        }
        this.tickedNanos = now;
        for (CreatorControl control : this.controls) {
            control.tick();
        }
    }

    private static String cancelLabel() {
        return I18n.format("gui.cancel");
    }

    private static String saveLabel() {
        return I18n.format("gui.losttales.character.look.save");
    }

    /** Cancel and Save at the foot's right, Save last, a framed button's gap apart. */
    private static LostTalesUiHitBox buttonBox(FontRenderer font,
                                               LostTalesUiHitBox box,
                                               String part) {
        int saveWidth = WordButton.width(font, saveLabel());
        int cancelWidth = WordButton.width(font, cancelLabel());
        double top = box.top + box.height - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT;
        double saveLeft = box.left + box.width - WindowStyle.EDGE_GAP
                - saveWidth;
        if (SAVE.equals(part)) {
            return new LostTalesUiHitBox(saveLeft, top, saveWidth,
                    LostTalesUiFramedButton.HEIGHT);
        }
        return new LostTalesUiHitBox(saveLeft - WindowBar.BUTTON_GAP
                - cancelWidth, top, cancelWidth,
                LostTalesUiFramedButton.HEIGHT);
    }

    private String partAt(FontRenderer font, LostTalesUiHitBox box, double x,
                          double y) {
        if (buttonBox(font, box, SAVE).contains(x, y)) {
            return SAVE;
        }
        if (buttonBox(font, box, CANCEL).contains(x, y)) {
            return CANCEL;
        }
        return controlAt(box, x, y) != null ? CONTROL : null;
    }

    /** The row under a point; the grid only where its band shows it. */
    private CreatorControl controlAt(LostTalesUiHitBox box, double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return null;
        }
        layOut(box);
        int pointX = (int)Math.floor(x);
        int pointY = (int)Math.floor(y);
        if (inGridBox(box, x, y)) {
            return this.grid.contains(pointX, pointY) ? this.grid : null;
        }
        if (this.body.contains(pointX, pointY)) {
            return this.body;
        }
        return this.chest.contains(pointX, pointY) ? this.chest : null;
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String part = partAt(font, box, x, y);
        this.hovered = controlAt(box, x, y);
        if (part == null) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.part = part;
        hover.acts = SAVE.equals(part) ? canSave() : !CONTROL.equals(part)
                || this.hovered.isPointerOverAction((int)Math.floor(x),
                        (int)Math.floor(y));
        return hover;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (CANCEL.equals(hover.part) && button == 0) {
            close();
            return true;
        }
        if (SAVE.equals(hover.part) && button == 0) {
            save();
            return true;
        }
        CreatorControl control = this.hovered;
        focus(control);
        if (control != null && control.mouseClicked((int)Math.floor(x),
                (int)Math.floor(y), button)) {
            this.held = control;
        }
        return true;
    }

    @Override
    public void dragged(double x, double y) {
        if (this.held != null) {
            this.held.mouseDragged((int)Math.floor(x), (int)Math.floor(y));
        }
    }

    @Override
    public void released() {
        if (this.held != null) {
            this.held.mouseReleased();
            this.held = null;
        }
    }

    /** The wheel over the grid scrolls it a whole row of faces; over a stepper it steps. */
    @Override
    public void scrollBy(int lines) {
        if (this.hovered == this.grid) {
            this.gridScroll = Math.max(0, this.gridScroll
                    + lines * CreatorTileGrid.ROW_PITCH);
            return;
        }
        if (this.hovered != null && this.hovered.mouseWheel(lines < 0 ? 1 : -1)) {
            clearError();
        }
    }

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
        }
    }

    /** The next row that takes the keys, round from either end. */
    private void focusNext(int step) {
        int at = this.controls.indexOf(this.focused);
        int next = at < 0 ? (step > 0 ? 0 : this.controls.size() - 1)
                : (at + step + this.controls.size()) % this.controls.size();
        focus(this.controls.get(next));
    }

    @Override
    public boolean holdsKeys() {
        return this.focused != null;
    }

    /** The grid takes the keys as the window comes in front. */
    @Override
    public void takeKeys() {
        if (this.focused == null) {
            focus(this.grid);
        }
    }

    @Override
    public void releaseKeys() {
        focus(null);
    }

    @Override
    public void closed() {
        releaseKeys();
    }

    /** A row takes its keys first; then Tab walks the rows and Return saves. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.focused != null && this.focused.keyTyped(typedChar, keyCode)) {
            clearError();
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            focusNext(GuiScreen.isShiftKeyDown() ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
            return true;
        }
        return false;
    }
}
