package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorNote;
import com.ninuna.losttales.gui.screen.character.creator.CreatorSlider;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorWidgets;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-only editor for what a character says about itself: its
 * description and its age, which its player may change at any time. The
 * rows are the creator's own, so a character is described here the way
 * it was described when it was made.
 *
 * <p>A lore character's description and age are its definition's; the
 * screen will not save them, and the server refuses them as well.</p>
 */
public final class LostTalesCharacterProfileEditGui extends GuiScreen
        implements LostTalesPointerInteractable {

    private static final int PANEL_WIDTH_MAX = 360;
    private static final int PANEL_PADDING = 14;
    /** The panel starts below the screen's header. */
    private static final int PANEL_TOP_MIN = 40;
    private static final int CONTROL_GAP = 6;
    /** The line the status is written on, between the rows and the buttons. */
    private static final int STATUS_HEIGHT = 14;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private final GuiScreen parent;
    private final UUID characterId;
    private final List<CreatorControl> controls = new ArrayList<CreatorControl>();
    private final LostTalesUiButtonMotion saveMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.SNAP);

    private CreatorTextControl descriptionControl;
    private CreatorControl focusedControl;
    private CreatorControl pressedControl;
    private int draftAge = CharacterValidator.MIN_AGE;
    private String draftDescription = "";
    private boolean initializedFromSnapshot;
    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int statusTop;
    private int buttonTop;

    public LostTalesCharacterProfileEditGui(GuiScreen parent, UUID characterId) {
        this.parent = parent;
        this.characterId = characterId;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (this.descriptionControl != null) {
            // A resize builds the rows again; what was typed stays.
            this.draftDescription = this.descriptionControl.getText();
        }
        initializeFromSnapshot();
        buildControls();
    }

    /** The character's own values, once the roster has them. */
    private void initializeFromSnapshot() {
        if (this.initializedFromSnapshot) {
            return;
        }
        CharacterSummary character = character();
        if (character == null) {
            return;
        }
        this.draftAge = Math.max(CharacterValidator.MIN_AGE, character.getAge());
        this.draftDescription = character.getDescription();
        this.initializedFromSnapshot = true;
    }

    private void buildControls() {
        this.controls.clear();
        this.focusedControl = null;
        this.pressedControl = null;
        CreatorContext context = new CreatorContext(this.mc, this.fontRendererObj,
                this.mc.thePlayer == null ? null : this.mc.thePlayer.getUniqueID());
        this.controls.add(new CreatorSlider(context,
                I18n.format("gui.losttales.character.age"), new CreatorSlider.IntValue() {
            @Override public int get() { return draftAge; }
            @Override public void set(int value) {
                draftAge = Math.max(CharacterValidator.MIN_AGE, value);
                clearError();
            }
            @Override public int typedMax() { return CharacterValidator.MAX_AGE; }
        }, I18n.format("gui.losttales.character.creator.age.oldest")));
        this.controls.add(new CreatorNote(context,
                I18n.format("gui.losttales.character.creator.age.hint")));
        this.descriptionControl = new CreatorTextControl(context,
                I18n.format("gui.losttales.character.description"),
                this.draftDescription, CharacterValidator.MAX_DESCRIPTION_LENGTH,
                true);
        this.controls.add(this.descriptionControl);
        this.controls.add(new CreatorNote(context,
                I18n.format("gui.losttales.character.description.hint")));
        layout();
        focus(this.descriptionControl);
    }

    /** Places the rows and measures the panel around them. */
    private void layout() {
        this.panelWidth = Math.min(PANEL_WIDTH_MAX, this.width - 32);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        int contentWidth = contentWidth();
        int rows = 0;
        for (CreatorControl control : this.controls) {
            // A note's height follows the width it wraps to.
            control.place(0, 0, contentWidth);
            rows += control.height() + CONTROL_GAP;
        }
        this.panelHeight = PANEL_PADDING + rows + STATUS_HEIGHT + BUTTON_HEIGHT
                + PANEL_PADDING;
        this.panelTop = Math.max(PANEL_TOP_MIN, (this.height - this.panelHeight) / 2);
        int y = this.panelTop + PANEL_PADDING;
        for (CreatorControl control : this.controls) {
            control.place(this.panelLeft + PANEL_PADDING, y, contentWidth);
            y += control.height() + CONTROL_GAP;
        }
        this.statusTop = y;
        this.buttonTop = y + STATUS_HEIGHT;
    }

    private int contentWidth() {
        return this.panelWidth - PANEL_PADDING * 2;
    }

    @Override
    public void updateScreen() {
        if (!this.initializedFromSnapshot) {
            initializeFromSnapshot();
            if (this.initializedFromSnapshot) {
                buildControls();
            }
        }
        for (CreatorControl control : this.controls) {
            control.tick();
        }
        handlePendingOperation();
    }

    private void handlePendingOperation() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(this.pendingRequestId)) {
            return;
        }
        int completedRequest = this.pendingRequestId;
        this.pendingRequestId = 0;
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(completedRequest);
        if (feedback == null) {
            return;
        }
        ClientCharacterRosterCache.clearOperation(completedRequest);
        if (!feedback.isSuccessful()) {
            setStatus(ClientCharacterDisplayNames.error(feedback), true);
            return;
        }
        this.mc.displayGuiScreen(this.parent);
    }

    private CharacterSummary character() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null || this.characterId == null
                ? null : snapshot.getCharacter(this.characterId);
    }

    private String currentDescription() {
        return this.descriptionControl == null
                ? this.draftDescription : this.descriptionControl.getText();
    }

    /** Whether the rows say something the character does not already. */
    private boolean hasChanges(CharacterSummary character) {
        return this.draftAge != character.getAge()
                || !CharacterValidator.normalizeDescription(currentDescription())
                        .equals(character.getDescription());
    }

    private boolean canSave() {
        CharacterSummary character = character();
        return this.pendingRequestId == 0 && character != null
                && ClientLoreCharacterCache.findOwnedCharacter(this.characterId) == null
                && hasChanges(character);
    }

    private void submit() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || !canSave()) {
            return;
        }
        setStatus(I18n.format("gui.losttales.character.profile_edit.saving"), false);
        this.pendingRequestId = ClientCharacterNetwork.updateProfile(
                snapshot.getRevision(), this.characterId, currentDescription(),
                this.draftAge);
    }

    private void setStatus(String message, boolean error) {
        this.statusMessage = message == null ? "" : message;
        this.statusError = error;
    }

    private void clearError() {
        if (this.statusError) {
            setStatus("", false);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        CharacterSummary character = character();
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.profile_edit.title"),
                character == null
                        ? I18n.format("gui.losttales.character.loading")
                        : character.getName(),
                this.width, 12);
        LostTalesSkyrimUiStyle.drawPanel(this.panelLeft, this.panelTop,
                this.panelWidth, this.panelHeight);
        for (CreatorControl control : this.controls) {
            control.draw(mouseX, mouseY);
        }
        if (this.statusMessage.length() > 0) {
            LostTalesSkyrimUiStyle.beginContent();
            String status = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                    this.statusMessage, contentWidth());
            this.fontRendererObj.drawStringWithShadow(status,
                    (this.width - this.fontRendererObj.getStringWidth(status)) / 2,
                    this.statusTop + 1,
                    this.statusError ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.GREEN);
        }
        int[] cancel = cancelBounds();
        int[] save = saveBounds();
        CreatorWidgets.drawButton(this.fontRendererObj, cancel[0], cancel[1],
                cancel[2], cancel[3], I18n.format("gui.cancel"), true,
                within(cancel, mouseX, mouseY), this.cancelMotion);
        CreatorWidgets.drawButton(this.fontRendererObj, save[0], save[1],
                save[2], save[3],
                I18n.format("gui.losttales.character.profile_edit.save"),
                canSave(), within(save, mouseX, mouseY), this.saveMotion);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int[] cancelBounds() {
        int width = (contentWidth() - BUTTON_GAP) / 2;
        return new int[] {this.panelLeft + PANEL_PADDING, this.buttonTop,
                width, BUTTON_HEIGHT};
    }

    private int[] saveBounds() {
        int width = (contentWidth() - BUTTON_GAP) / 2;
        int x = this.panelLeft + PANEL_PADDING + width + BUTTON_GAP;
        return new int[] {x, this.buttonTop,
                this.panelLeft + PANEL_PADDING + contentWidth() - x, BUTTON_HEIGHT};
    }

    private static boolean within(int[] bounds, int mouseX, int mouseY) {
        return CreatorWidgets.within(mouseX, mouseY,
                bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private CreatorControl controlAt(int mouseX, int mouseY) {
        for (CreatorControl control : this.controls) {
            if (control.contains(mouseX, mouseY)) {
                return control;
            }
        }
        return null;
    }

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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && within(cancelBounds(), mouseX, mouseY)) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button == 0 && within(saveBounds(), mouseX, mouseY)) {
            submit();
            return;
        }
        CreatorControl control = controlAt(mouseX, mouseY);
        if (control != null) {
            focus(control);
            if (control.mouseClicked(mouseX, mouseY, button)) {
                this.pressedControl = control;
                clearError();
            }
            return;
        }
        focus(null);
        super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * The same hit tests a left click takes, in the same order: the two
     * buttons, Save only while it can save, and a row only where a click
     * acts on it.
     */
    @Override
    public boolean isPointerOverInteractable(int x, int y) {
        if (within(cancelBounds(), x, y)) {
            return true;
        }
        if (within(saveBounds(), x, y)) {
            return canSave();
        }
        CreatorControl control = controlAt(x, y);
        return control != null && control.isPointerOverAction(x, y);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton,
                                  long timeSinceLastClick) {
        if (this.pressedControl != null) {
            this.pressedControl.mouseDragged(mouseX, mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state >= 0 && this.pressedControl != null) {
            this.pressedControl.mouseReleased();
            this.pressedControl = null;
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.mc.displayWidth > 0 && this.mc.displayHeight > 0) {
            int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int y = this.height - Mouse.getEventY() * this.height
                    / this.mc.displayHeight - 1;
            CreatorControl control = controlAt(
                    LostTalesGuiAnimations.inverseMouseX(this, x),
                    LostTalesGuiAnimations.inverseMouseY(this, y));
            if (control != null && control.mouseWheel(wheel > 0 ? 1 : -1)) {
                clearError();
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.focusedControl != null
                && this.focusedControl.keyTyped(typedChar, keyCode)) {
            clearError();
            return;
        }
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                this.mc.displayGuiScreen(this.parent);
                return;
            case Keyboard.KEY_TAB:
                focusNext(isShiftKeyDown() ? -1 : 1);
                return;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                submit();
                return;
            default:
                super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
