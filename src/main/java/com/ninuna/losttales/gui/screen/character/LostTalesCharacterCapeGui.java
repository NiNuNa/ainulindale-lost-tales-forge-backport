package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-only editor for server-authoritative persistent cape settings. */
public final class LostTalesCharacterCapeGui extends GuiScreen {

    private static final int BUTTON_NORMAL_CAPE = 1;
    private static final int BUTTON_CAPE_PREVIOUS = 2;
    private static final int BUTTON_CAPE_NEXT = 3;
    private static final int BUTTON_SAVE = 4;
    private static final int BUTTON_CANCEL = 5;

    private final GuiScreen parent;
    private List<Integer> cosmeticCapeIds = Collections.emptyList();
    private boolean initializedFromSnapshot;
    private boolean showMinecraftCape = true;
    private int cosmeticCapeIndex;
    private int pendingRequestId;
    private String statusMessage = "";
    private boolean statusError;
    private GuiButton normalCapeButton;
    private GuiButton saveButton;

    public LostTalesCharacterCapeGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        rebuildCapeIds();
        initializeFromSnapshot();

        int panelWidth = Math.min(470, this.width - 32);
        int panelHeight = Math.min(286, Math.max(230, this.height - 76));
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(36, (this.height - panelHeight) / 2);
        int controlsX = left + 32;
        int controlsWidth = Math.max(180, panelWidth - 190);

        this.normalCapeButton = new GuiButton(
                BUTTON_NORMAL_CAPE,
                controlsX,
                top + 58,
                controlsWidth,
                20,
                "");
        this.buttonList.add(this.normalCapeButton);
        this.buttonList.add(new GuiButton(
                BUTTON_CAPE_PREVIOUS,
                controlsX,
                top + 108,
                24,
                20,
                "<"));
        this.buttonList.add(new GuiButton(
                BUTTON_CAPE_NEXT,
                controlsX + controlsWidth - 24,
                top + 108,
                24,
                20,
                ">"));

        int bottom = top + panelHeight - 30;
        this.saveButton = new GuiButton(
                BUTTON_SAVE,
                this.width / 2 - 104,
                bottom,
                100,
                20,
                I18n.format("gui.losttales.character.cape.save"));
        this.buttonList.add(this.saveButton);
        this.buttonList.add(new GuiButton(
                BUTTON_CANCEL,
                this.width / 2 + 4,
                bottom,
                100,
                20,
                I18n.format("gui.cancel")));
        updateButtonState();
    }

    private void rebuildCapeIds() {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ids.add(Integer.valueOf(CharacterCapeCatalog.NONE_ID));
        for (CharacterCapeDefinition definition : CharacterCapeCatalog.getDefinitions()) {
            ids.add(Integer.valueOf(definition.getNetworkId()));
        }
        this.cosmeticCapeIds = Collections.unmodifiableList(ids);
        this.cosmeticCapeIndex = clampIndex(
                this.cosmeticCapeIndex, this.cosmeticCapeIds.size());
    }

    private void initializeFromSnapshot() {
        if (this.initializedFromSnapshot) {
            return;
        }
        CharacterSummary active = getActiveCharacter();
        if (active == null) {
            return;
        }
        this.showMinecraftCape = active.isMinecraftCapeVisible();
        int index = this.cosmeticCapeIds.indexOf(
                Integer.valueOf(active.getCosmeticCapeId()));
        this.cosmeticCapeIndex = index < 0 ? 0 : index;
        this.initializedFromSnapshot = true;
    }

    @Override
    public void updateScreen() {
        initializeFromSnapshot();
        handlePendingOperation();
        updateButtonState();
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
            this.statusMessage = ClientCharacterDisplayNames.error(
                    feedback.getErrorId());
            this.statusError = true;
            return;
        }
        this.statusMessage = ClientCharacterDisplayNames.operationSuccess(
                "cape_update");
        this.statusError = false;
        if (this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    private void updateButtonState() {
        boolean pending = this.pendingRequestId != 0
                && ClientCharacterRosterCache.isRequestPending(this.pendingRequestId);
        CharacterSummary active = getActiveCharacter();
        for (Object object : this.buttonList) {
            GuiButton button = (GuiButton)object;
            button.enabled = button.id == BUTTON_CANCEL || !pending;
        }
        if (this.normalCapeButton != null) {
            this.normalCapeButton.displayString = I18n.format(
                    this.showMinecraftCape
                            ? "gui.losttales.character.cape.normal_enabled"
                            : "gui.losttales.character.cape.normal_disabled");
        }
        if (this.saveButton != null) {
            this.saveButton.enabled = !pending && active != null;
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
            case BUTTON_NORMAL_CAPE:
                this.showMinecraftCape = !this.showMinecraftCape;
                this.statusMessage = "";
                return;
            case BUTTON_CAPE_PREVIOUS:
                this.cosmeticCapeIndex = cycleIndex(
                        this.cosmeticCapeIndex, -1, this.cosmeticCapeIds.size());
                this.statusMessage = "";
                return;
            case BUTTON_CAPE_NEXT:
                this.cosmeticCapeIndex = cycleIndex(
                        this.cosmeticCapeIndex, 1, this.cosmeticCapeIds.size());
                this.statusMessage = "";
                return;
            case BUTTON_SAVE:
                submitUpdate();
                return;
            default:
                return;
        }
    }

    private void submitUpdate() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null ? null : snapshot.getActiveCharacter();
        if (snapshot == null || active == null) {
            this.statusMessage = I18n.format(
                    "gui.losttales.character.loading_detail");
            this.statusError = true;
            return;
        }
        int capeId = selectedCapeId();
        this.statusMessage = I18n.format(
                "gui.losttales.character.cape.saving");
        this.statusError = false;
        this.pendingRequestId = ClientCharacterNetwork.updateCapeSettings(
                snapshot.getRevision(),
                active.getCharacterId(),
                this.showMinecraftCape,
                capeId);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        CharacterSummary active = getActiveCharacter();
        LostTalesSkyrimUiStyle.drawCenteredHeader(
                this.fontRendererObj,
                I18n.format("gui.losttales.character.cape.editor"),
                active == null
                        ? I18n.format("gui.losttales.character.loading")
                        : active.getName(),
                this.width,
                12);

        int panelWidth = Math.min(470, this.width - 32);
        int panelHeight = Math.min(286, Math.max(230, this.height - 76));
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(36, (this.height - panelHeight) / 2);
        int controlsX = left + 32;
        int controlsWidth = Math.max(180, panelWidth - 190);
        LostTalesSkyrimUiStyle.drawPanel(left, top, panelWidth, panelHeight);

        LostTalesSkyrimUiStyle.drawSectionHeader(
                this.fontRendererObj,
                I18n.format("gui.losttales.character.cape.normal"),
                controlsX,
                top + 35,
                controlsWidth);
        LostTalesSkyrimUiStyle.drawSectionHeader(
                this.fontRendererObj,
                I18n.format("gui.losttales.character.cape.cosmetic"),
                controlsX,
                top + 86,
                controlsWidth);

        String capeName = ClientCharacterDisplayNames.cape(selectedCapeId());
        String trimmedCapeName = LostTalesSkyrimUiStyle.trimToWidth(
                this.fontRendererObj, capeName, controlsWidth - 56);
        this.fontRendererObj.drawStringWithShadow(
                trimmedCapeName,
                controlsX + (controlsWidth - this.fontRendererObj.getStringWidth(
                        trimmedCapeName)) / 2,
                top + 114,
                LostTalesSkyrimUiStyle.TEXT_BRIGHT);

        String policy = I18n.format(
                "gui.losttales.character.cape.policy_allowlist");
        drawCenteredString(
                this.fontRendererObj,
                LostTalesSkyrimUiStyle.trimToWidth(
                        this.fontRendererObj, policy, controlsWidth),
                controlsX + controlsWidth / 2,
                top + 143,
                LostTalesSkyrimUiStyle.TEXT_MUTED);

        if (selectedCapeId() != CharacterCapeCatalog.NONE_ID) {
            String precedence = I18n.format(
                    "gui.losttales.character.cape.cosmetic_precedence");
            drawCenteredString(
                    this.fontRendererObj,
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj, precedence, controlsWidth),
                    controlsX + controlsWidth / 2,
                    top + 158,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
        }

        if (panelWidth >= 360) {
            drawAppearancePreview(
                    left + panelWidth - 76,
                    top + panelHeight - 45,
                    mouseX,
                    mouseY);
        }

        if (this.statusMessage.length() > 0) {
            drawCenteredString(
                    this.fontRendererObj,
                    LostTalesSkyrimUiStyle.trimToWidth(
                            this.fontRendererObj,
                            this.statusMessage,
                            panelWidth - 34),
                    this.width / 2,
                    top + panelHeight - 49,
                    this.statusError
                            ? LostTalesSkyrimUiStyle.RED
                            : LostTalesSkyrimUiStyle.GREEN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawAppearancePreview(int x, int y, int mouseX, int mouseY) {
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        CharacterSummary active = getActiveCharacter();
        if (player == null || player.getUniqueID() == null || active == null) {
            return;
        }
        CharacterAppearance preview = new CharacterAppearance(
                player.getUniqueID(),
                active.getRaceId(),
                active.getGenderId(),
                active.getSkinId(),
                this.showMinecraftCape,
                selectedCapeId());
        ClientCharacterAppearanceCache.setPreview(preview);
        boolean previousDebugBoundingBox = RenderManager.debugBoundingBox;
        try {
            int previewY = CharacterGuiPreviewLayout.baselineY(
                    active.getRaceId(), y);
            int previewScale = CharacterGuiPreviewLayout.scale(
                    active.getRaceId(), 42);
            RenderManager.debugBoundingBox = false;
            GuiInventory.func_147046_a(
                    x,
                    previewY,
                    previewScale,
                    (float)(x - mouseX),
                    (float)(previewY - 65 - mouseY),
                    player);
        } finally {
            RenderManager.debugBoundingBox = previousDebugBoundingBox;
            ClientCharacterAppearanceCache.clearPreview(player.getUniqueID());
        }
    }

    private CharacterSummary getActiveCharacter() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null ? null : snapshot.getActiveCharacter();
    }

    private int selectedCapeId() {
        return this.cosmeticCapeIds.isEmpty()
                || this.cosmeticCapeIndex < 0
                || this.cosmeticCapeIndex >= this.cosmeticCapeIds.size()
                ? CharacterCapeCatalog.NONE_ID
                : this.cosmeticCapeIds.get(this.cosmeticCapeIndex).intValue();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.saveButton != null && this.saveButton.enabled) {
                submitUpdate();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        if (player != null && player.getUniqueID() != null) {
            ClientCharacterAppearanceCache.clearPreview(player.getUniqueID());
        }
        super.onGuiClosed();
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
