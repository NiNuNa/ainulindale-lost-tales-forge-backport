package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.gui.screen.LostTalesCharacterInfoGui;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Client-only profile entry controller. It never treats a missing client cache
 * as an empty authoritative roster and routes only after a server snapshot.
 */
public final class LostTalesCharacterProfileRouterGui extends GuiScreen {

    private static final int BUTTON_RETRY = 1;
    private static final int BUTTON_BACK = 2;

    private final GuiScreen parent;
    private int requestId;
    private boolean routed;

    public LostTalesCharacterProfileRouterGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BUTTON_RETRY, this.width / 2 - 102,
                this.height / 2 + 32, 100, 20,
                I18n.format("gui.losttales.character.retry")));
        this.buttonList.add(new GuiButton(BUTTON_BACK, this.width / 2 + 2,
                this.height / 2 + 32, 100, 20,
                I18n.format("gui.back")));
        requestIfNecessary();
        updateButtons();
    }

    @Override
    public void updateScreen() {
        routeWhenReady();
        updateButtons();
    }

    private void requestIfNecessary() {
        ClientCharacterRosterCache.SyncState state = ClientCharacterRosterCache.getState();
        if (state == ClientCharacterRosterCache.SyncState.UNKNOWN
                || state == ClientCharacterRosterCache.SyncState.ERROR) {
            this.requestId = ClientCharacterNetwork.requestRoster();
        }
    }

    private void routeWhenReady() {
        if (this.routed || this.mc == null
                || ClientCharacterRosterCache.getState() != ClientCharacterRosterCache.SyncState.READY) {
            return;
        }
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null) {
            return;
        }
        this.routed = true;
        if (snapshot.getCharacterCount() == 0) {
            this.mc.displayGuiScreen(new LostTalesCharacterCreationGui(
                    this.parent, 0, true));
        } else if (snapshot.getActiveCharacter() == null) {
            this.mc.displayGuiScreen(new LostTalesCharacterRosterGui(
                    this.parent, true));
        } else {
            this.mc.displayGuiScreen(new LostTalesCharacterInfoGui(this.parent));
        }
    }

    private void updateButtons() {
        if (this.buttonList.size() < 2) {
            return;
        }
        ClientCharacterRosterCache.SyncState state = ClientCharacterRosterCache.getState();
        GuiButton retry = (GuiButton)this.buttonList.get(0);
        retry.visible = state == ClientCharacterRosterCache.SyncState.ERROR;
        retry.enabled = retry.visible && (this.requestId == 0
                || !ClientCharacterRosterCache.isRequestPending(this.requestId));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_RETRY) {
            this.requestId = ClientCharacterNetwork.requestRoster();
            return;
        }
        if (button.id == BUTTON_BACK && this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.profile"),
                I18n.format("gui.losttales.character.server_authoritative"),
                this.width, 12);

        int panelWidth = Math.min(340, this.width - 32);
        int panelHeight = 94;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2 - 18;
        LostTalesSkyrimUiStyle.drawPanel(x, y, panelWidth, panelHeight);

        ClientCharacterRosterCache.SyncState state = ClientCharacterRosterCache.getState();
        String title = state == ClientCharacterRosterCache.SyncState.ERROR
                ? I18n.format("gui.losttales.character.sync_failed")
                : I18n.format("gui.losttales.character.loading");
        String detail = state == ClientCharacterRosterCache.SyncState.ERROR
                ? I18n.format("gui.losttales.character.sync_failed_detail")
                : I18n.format("gui.losttales.character.loading_detail");
        int titleX = this.width / 2 - this.fontRendererObj.getStringWidth(title) / 2;
        this.fontRendererObj.drawStringWithShadow(title, titleX, y + 24,
                state == ClientCharacterRosterCache.SyncState.ERROR
                        ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        drawCenteredString(this.fontRendererObj, detail, this.width / 2, y + 46,
                LostTalesSkyrimUiStyle.TEXT_MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
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
