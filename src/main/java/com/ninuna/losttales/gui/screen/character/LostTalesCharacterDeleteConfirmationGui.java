package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** Explicit client confirmation for deletion; the server still validates the request. */
public final class LostTalesCharacterDeleteConfirmationGui extends GuiScreen {

    private static final int BUTTON_DELETE = 1;
    private static final int BUTTON_CANCEL = 2;

    private final GuiScreen parent;
    private final CharacterSummary character;
    private int pendingRequestId;
    private String statusMessage = "";
    private GuiButton deleteButton;

    public LostTalesCharacterDeleteConfirmationGui(GuiScreen parent,
                                                    CharacterSummary character) {
        this.parent = parent;
        this.character = character;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int y = this.height / 2 + 42;
        this.deleteButton = new GuiButton(BUTTON_DELETE, this.width / 2 - 104,
                y, 100, 20, I18n.format("gui.losttales.character.confirm_delete"));
        this.buttonList.add(this.deleteButton);
        this.buttonList.add(new GuiButton(BUTTON_CANCEL, this.width / 2 + 4,
                y, 100, 20, I18n.format("gui.cancel")));
    }

    @Override
    public void updateScreen() {
        if (this.pendingRequestId != 0
                && !ClientCharacterRosterCache.isRequestPending(this.pendingRequestId)) {
            CharacterOperationFeedback feedback = ClientCharacterRosterCache.getOperation(
                    this.pendingRequestId);
            int completed = this.pendingRequestId;
            this.pendingRequestId = 0;
            if (feedback != null) {
                ClientCharacterRosterCache.clearOperation(completed);
                if (feedback.isSuccessful()) {
                    this.mc.displayGuiScreen(this.parent);
                    return;
                }
                this.statusMessage = ClientCharacterDisplayNames.error(feedback.getErrorId());
            }
        }
        this.deleteButton.enabled = this.pendingRequestId == 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CANCEL) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id != BUTTON_DELETE || this.pendingRequestId != 0
                || this.character == null) {
            return;
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || snapshot.getCharacter(this.character.getCharacterId()) == null) {
            this.statusMessage = I18n.format("gui.losttales.character.stale_detail");
            return;
        }
        this.statusMessage = I18n.format("gui.losttales.character.deleting");
        this.pendingRequestId = ClientCharacterNetwork.deleteCharacter(
                snapshot.getRevision(), this.character.getCharacterId());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.delete"),
                I18n.format("gui.losttales.character.delete_irreversible"),
                this.width, 12);

        int panelWidth = Math.min(400, this.width - 32);
        int panelHeight = 116;
        int x = (this.width - panelWidth) / 2;
        int y = this.height / 2 - 62;
        LostTalesSkyrimUiStyle.drawPanel(x, y, panelWidth, panelHeight);
        String name = this.character == null ? I18n.format("gui.losttales.character.unknown")
                : this.character.getName();
        drawCenteredString(this.fontRendererObj,
                I18n.format("gui.losttales.character.delete_question", name),
                this.width / 2, y + 24, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        drawCenteredString(this.fontRendererObj,
                I18n.format("gui.losttales.character.delete_slot_remains"),
                this.width / 2, y + 45, LostTalesSkyrimUiStyle.TEXT_MUTED);
        if (this.statusMessage.length() > 0) {
            drawCenteredString(this.fontRendererObj, this.statusMessage,
                    this.width / 2, y + 66, LostTalesSkyrimUiStyle.RED);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && this.pendingRequestId == 0) {
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
