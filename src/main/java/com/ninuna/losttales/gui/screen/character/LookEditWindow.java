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
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.creator.CreatorChoice;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorStepper;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTileGrid;
import com.ninuna.losttales.gui.screen.character.creator.PlainChoice;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
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
 * Change Look (R1 a): a character's skin, arm width and chest, in a
 * sub-window of the Characters tab's window, its player's to change at any
 * time. The skins are the grid of faces the creator shows — every skin the
 * character's race and sex may wear, the account's own among them where
 * the race allows it — scrolled a few rows at a time, the chosen one named
 * above it; under it the arm width and the chest, fixed where the skin
 * decides them. Name, race, sex and faction are not here. Save sends the
 * look and the window closes once the server has kept it; Cancel, the
 * cross and Escape leave it as it was. The figure on the page wears the
 * look while the window is open.
 */
final class LookEditWindow extends SubWindowContent {
    private static final String SAVE = "save";
    private static final String CANCEL = "cancel";
    private static final String CONTROL = "control";
    private static final int WIDTH = 300;
    private static final int PADDING = 6;
    private static final int CONTROL_GAP = 6;
    private static final int STATUS_HEIGHT = 12;
    /** The rows of faces the grid shows at once; the wheel scrolls the rest. */
    private static final int GRID_ROWS = 4;
    /** How often the rows keep time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;

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
    private CreatorTileGrid grid;
    private CreatorStepper body;
    private CreatorStepper chest;
    private final List<CreatorControl> controls = new ArrayList<CreatorControl>();
    /** How far the grid is scrolled, in pixels, a whole row of faces at a time. */
    private int gridScroll;
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
        CreatorContext context = new CreatorContext(minecraft,
                minecraft.fontRenderer, minecraft.thePlayer == null ? null
                        : minecraft.thePlayer.getUniqueID());
        this.grid = new CreatorTileGrid(context, null, new PlainChoice() {
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
        this.body = new CreatorStepper(context,
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
        this.chest = new CreatorStepper(context,
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

    /** The grid's box: as tall as its rows, a few of them at most. */
    private int gridBoxHeight(int width) {
        this.grid.place(0, 0, width);
        return Math.min(this.grid.height(),
                GRID_ROWS * CreatorTileGrid.ROW_PITCH);
    }

    private int gridTop(LostTalesUiHitBox box) {
        return (int)box.top + PADDING + WindowStyle.LINE_HEIGHT;
    }

    /** Places the rows from the box's top left; answers where the status line stands. */
    private int layOut(LostTalesUiHitBox box) {
        int left = (int)box.left + PADDING;
        int width = (int)box.width - 2 * PADDING;
        int gridHeight = gridBoxHeight(width);
        this.gridScroll = Math.max(0, Math.min(this.gridScroll,
                this.grid.height() - gridHeight));
        this.grid.place(left, gridTop(box) - this.gridScroll, width);
        int y = gridTop(box) + gridHeight + CONTROL_GAP;
        this.body.place(left, y, width);
        y += this.body.height() + CONTROL_GAP;
        this.chest.place(left, y, width);
        return y + this.chest.height() + CONTROL_GAP;
    }

    /** Whether a point is on the part of the grid the box shows. */
    private boolean inGridBox(LostTalesUiHitBox box, double x, double y) {
        int width = (int)box.width - 2 * PADDING;
        return LostTalesUiHitBox.contains(x, y, box.left + PADDING,
                gridTop(box), width, gridBoxHeight(width));
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

    @Override
    public int naturalHeight(int width) {
        int inner = width - 2 * PADDING;
        return PADDING + WindowStyle.LINE_HEIGHT + gridBoxHeight(inner)
                + CONTROL_GAP + this.body.height() + CONTROL_GAP
                + this.chest.height() + CONTROL_GAP + STATUS_HEIGHT
                + LostTalesUiFramedButton.HEIGHT + PADDING;
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
        int left = (int)box.left + PADDING;
        int inner = (int)box.width - 2 * PADDING;
        int statusTop = layOut(box);
        int mouseX = Double.isNaN(pointerX) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(pointerX);
        int mouseY = Double.isNaN(pointerY) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(pointerY);
        String part = partAt(font, box, pointerX, pointerY);
        String skinName = skinId().length() == 0 ? ""
                : ClientCharacterDisplayNames.skin(skinId());
        LostTalesUiInk.drawText(font, font.trimStringToWidth(I18n.format(
                        "gui.losttales.character.look.skin", skinName), inner),
                left, (int)box.top + PADDING + WindowStyle.ROW_TEXT_TOP,
                LostTalesUiInk.IVORY, alpha);
        int gridHeight = gridBoxHeight(inner);
        this.grid.place(left, gridTop(box) - this.gridScroll, inner);
        boolean clipped = SubWindowContent.beginClip(minecraft,
                clipX + PADDING, clipY + (gridTop(box) - box.top), inner,
                gridHeight);
        try {
            boolean overGrid = inGridBox(box, pointerX, pointerY);
            this.grid.draw(overGrid ? mouseX : Integer.MIN_VALUE / 2,
                    overGrid ? mouseY : Integer.MIN_VALUE / 2);
        } finally {
            SubWindowContent.endClip(clipped);
        }
        LostTalesUiInk.beginContent();
        this.body.draw(mouseX, mouseY);
        this.chest.draw(mouseX, mouseY);
        String said = statusText();
        if (said.length() > 0) {
            LostTalesUiInk.drawText(font, font.trimStringToWidth(said, inner),
                    left, statusTop, this.statusError
                            ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), alpha);
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
        double top = box.top + box.height - PADDING
                - LostTalesUiFramedButton.HEIGHT;
        double saveLeft = box.left + box.width - PADDING - saveWidth;
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

    /** The row under a point; the grid only where the box shows it. */
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

    /** The wheel over the grid scrolls it a row of faces; over a stepper it steps. */
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
