package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRaceAttributes;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterCapeGui;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterProfileRouterGui;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterRosterGui;
import com.ninuna.losttales.gui.screen.party.LostTalesPartyManagementGui;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Client-only character overview/details screen reached from the radial character menu. */
public class LostTalesCharacterInfoGui extends GuiScreen {
    private static final int PANEL_PADDING = 10;
    private static final int BUTTON_MANAGE = 1;
    private static final int BUTTON_BACK = 2;
    private static final int BUTTON_CAPE = 3;
    private static final int BUTTON_PARTY = 4;

    private final GuiScreen parent;
    private int modelPanelX;
    private int modelPanelY;
    private int modelPanelWidth;
    private int modelPanelHeight;
    private boolean draggingModel;
    private int lastDragX;
    private int lastDragY;
    private float modelYaw = 25.0F;
    private float modelPitch = 0.0F;
    private int modelScale = 48;
    private int rosterRequestId;

    public LostTalesCharacterInfoGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        if (this.mc != null) {
            LostTalesClientQuestDefinitionStore.ensureLoaded(this.mc.getResourceManager());
        }
        int actionGap = 6;
        int actionWidth = Math.max(64, Math.min(112,
                (this.width - 104 - actionGap * 2) / 3));
        int actionStart = this.width - 8
                - actionWidth * 3 - actionGap * 2;
        this.buttonList.add(new GuiButton(BUTTON_PARTY, actionStart,
                this.height - 28, actionWidth, 20,
                I18n.format("gui.losttales.party.button")));
        this.buttonList.add(new GuiButton(BUTTON_CAPE,
                actionStart + actionWidth + actionGap,
                this.height - 28, actionWidth, 20,
                I18n.format("gui.losttales.character.cape.button")));
        this.buttonList.add(new GuiButton(BUTTON_MANAGE,
                actionStart + (actionWidth + actionGap) * 2,
                this.height - 28, actionWidth, 20,
                I18n.format("gui.losttales.character.manage")));
        this.buttonList.add(new GuiButton(BUTTON_BACK, 8,
                this.height - 28, 72, 20, I18n.format("gui.back")));
        if (ClientCharacterRosterCache.getState() == ClientCharacterRosterCache.SyncState.UNKNOWN
                || ClientCharacterRosterCache.getState() == ClientCharacterRosterCache.SyncState.ERROR) {
            this.rosterRequestId = ClientCharacterNetwork.requestRoster();
        }
    }

    @Override
    public void updateScreen() {
        ClientCharacterRosterCache.SyncState state = ClientCharacterRosterCache.getState();
        if (state == ClientCharacterRosterCache.SyncState.READY) {
            CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
            if (snapshot == null || snapshot.getActiveCharacter() == null) {
                this.mc.displayGuiScreen(new LostTalesCharacterProfileRouterGui(this.parent));
            }
        } else if (state == ClientCharacterRosterCache.SyncState.ERROR
                && (this.rosterRequestId == 0
                || !ClientCharacterRosterCache.isRequestPending(this.rosterRequestId))) {
            this.mc.displayGuiScreen(new LostTalesCharacterProfileRouterGui(this.parent));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj, I18n.format("gui.losttales.character.profile"), getCharacterName(), this.width, 12);

        int gap = 14;
        int panelTop = 48;
        int panelHeight = Math.max(126, this.height - 96);
        int available = Math.max(360, this.width - 36);
        int sideWidth = Math.min(220, Math.max(160, (available - 160 - gap * 2) / 2));
        int modelWidth = Math.min(170, Math.max(118, available - sideWidth * 2 - gap * 2));
        int totalWidth = sideWidth * 2 + modelWidth + gap * 2;
        int leftX = Math.max(12, (this.width - totalWidth) / 2);
        int modelX = leftX + sideWidth + gap;
        int rightX = modelX + modelWidth + gap;

        this.modelPanelX = modelX;
        this.modelPanelY = panelTop;
        this.modelPanelWidth = modelWidth;
        this.modelPanelHeight = panelHeight;

        LostTalesSkyrimUiStyle.drawPanel(leftX, panelTop, sideWidth, panelHeight);
        LostTalesSkyrimUiStyle.drawPanel(modelX, panelTop, modelWidth, panelHeight);
        LostTalesSkyrimUiStyle.drawPanel(rightX, panelTop, sideWidth, panelHeight);

        drawCharacterPanel(leftX + PANEL_PADDING, panelTop + PANEL_PADDING, sideWidth - PANEL_PADDING * 2, panelHeight - PANEL_PADDING * 2);
        drawPlayerModelPanel(modelX + PANEL_PADDING, panelTop + PANEL_PADDING, modelWidth - PANEL_PADDING * 2, panelHeight - PANEL_PADDING * 2, mouseX, mouseY);
        drawQuestPanel(rightX + PANEL_PADDING, panelTop + PANEL_PADDING, sideWidth - PANEL_PADDING * 2, panelHeight - PANEL_PADDING * 2);
        drawFooterHelp();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCharacterPanel(int x, int y, int width, int height) {
        CharacterSummary character = getActiveCharacter();
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.profile"), x, y, width);
        int lineY = y + 16;
        if (character == null) {
            drawWrapped(I18n.format("gui.losttales.character.loading_detail"),
                    x, lineY, width, LostTalesSkyrimUiStyle.TEXT_MUTED, height);
            return;
        }
        lineY = drawLabelValue(I18n.format("gui.losttales.character.name"),
                character.getName(), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.level"),
                String.valueOf(character.getRoleplayLevel()), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.race"),
                ClientCharacterDisplayNames.race(character.getRaceId()), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.gender"),
                ClientCharacterDisplayNames.gender(character.getGenderId()), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.skin"),
                ClientCharacterDisplayNames.skin(character.getSkinId()), x, lineY, width);
        if (character.getDescription().length() > 0) {
            this.fontRendererObj.drawStringWithShadow(
                    I18n.format("gui.losttales.character.description") + ":",
                    x, lineY, LostTalesSkyrimUiStyle.TEXT_MUTED);
            lineY = drawWrapped(character.getDescription(), x + 8,
                    lineY + 11, width - 8,
                    LostTalesSkyrimUiStyle.TEXT_BRIGHT, 30);
        }
        lineY = drawLabelValue(I18n.format("gui.losttales.character.age"),
                String.valueOf(character.getAge()), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.starting_faction"),
                ClientCharacterDisplayNames.faction(character.getStartingFactionId()), x, lineY, width);
        lineY += 8;

        CharacterRaceGameplayProfile raceProfile = ClientCharacterRaceAttributes.resolve(
                this.mc == null ? null : this.mc.theWorld, character.getRaceId());
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.race_attributes"), x, lineY, width);
        lineY += 16;
        lineY = drawLabelValue(I18n.format("gui.losttales.character.attribute.health"),
                ClientCharacterRaceAttributes.formatHealth(raceProfile), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.attribute.movement_speed"),
                ClientCharacterRaceAttributes.formatMovementSpeed(raceProfile), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.attribute.attack_damage"),
                ClientCharacterRaceAttributes.formatAttackDamage(raceProfile), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.attribute.hitbox"),
                ClientCharacterRaceAttributes.formatHitbox(raceProfile), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.attribute.eye_height"),
                ClientCharacterRaceAttributes.formatEyeHeight(raceProfile), x, lineY, width);
        lineY += 8;

        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.account_state"), x, lineY, width);
        lineY += 16;
        lineY = drawLabelValue(I18n.format("gui.losttales.character.minecraft_account"),
                getAccountName(), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.location"),
                getDimensionName(), x, lineY, width);
        lineY = drawLabelValue(I18n.format("gui.losttales.character.world_time"),
                getWorldTimeText(), x, lineY, width);
        lineY += 8;

        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj,
                I18n.format("gui.losttales.character.shared_state"), x, lineY, width);
        lineY += 16;
        drawWrapped(I18n.format("gui.losttales.character.shared_state_detail"),
                x, lineY, width, LostTalesSkyrimUiStyle.TEXT_MUTED,
                Math.max(18, height - (lineY - y)));
    }

    private void drawPlayerModelPanel(int x, int y, int width, int height, int mouseX, int mouseY) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj, I18n.format("gui.losttales.character.character"), x, y, width);
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        int modelX = x + width / 2;
        int baseModelY = y + Math.max(78, height - 28);
        CharacterSummary activeCharacter = getActiveCharacter();
        String raceId = activeCharacter == null ? "" : activeCharacter.getRaceId();
        int modelY = CharacterGuiPreviewLayout.baselineY(raceId, baseModelY);
        int effectiveScale = CharacterGuiPreviewLayout.scale(raceId, this.modelScale);
        if (player != null) {
            drawEntityModel(modelX, modelY, effectiveScale,
                    this.modelYaw, this.modelPitch, player);
        }

        int helpY = y + height - 28;
        drawWrapped("Drag to rotate. Mouse wheel scales the model.", x, helpY, width, LostTalesSkyrimUiStyle.TEXT_MUTED, 24);
        if (isMouseInsideModelPanel(mouseX, mouseY)) {
            LostTalesSkyrimUiStyle.drawDiamond(modelX, y + 19, LostTalesSkyrimUiStyle.GOLD);
        }
    }

    private void drawQuestPanel(int x, int y, int width, int height) {
        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj, "Quests", x, y, width);
        int lineY = y + 16;
        Collection<LostTalesQuestProgress> active = LostTalesClientQuestProgressStore.getActiveQuests();
        Collection<LostTalesQuestProgress> pinned = LostTalesClientQuestProgressStore.getPinnedQuests();
        int completed = LostTalesClientQuestProgressStore.getCompletedQuestIds().size();
        lineY = drawLabelValue("Active", String.valueOf(active.size()), x, lineY, width);
        lineY = drawLabelValue("Tracked", String.valueOf(pinned.size()), x, lineY, width);
        lineY = drawLabelValue("Completed", String.valueOf(completed), x, lineY, width);
        lineY += 8;

        LostTalesSkyrimUiStyle.drawSectionHeader(this.fontRendererObj, "Tracked Objectives", x, lineY, width);
        lineY += 16;
        List<LostTalesQuestProgress> tracked = new ArrayList<LostTalesQuestProgress>(pinned);
        if (tracked.isEmpty()) {
            drawWrapped("No quest is currently tracked. Open the quest journal to select one.", x, lineY, width, LostTalesSkyrimUiStyle.TEXT_MUTED, Math.max(18, height - (lineY - y)));
            return;
        }

        int bottom = y + height;
        for (int i = 0; i < tracked.size() && i < 5 && lineY + 20 < bottom; i++) {
            LostTalesQuestProgress progress = tracked.get(i);
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            String title = quest == null ? progress.getQuestId() : quest.getTitle();
            this.fontRendererObj.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, title, width), x, lineY, LostTalesSkyrimUiStyle.GOLD);
            lineY += 11;
            String objective = getPrimaryObjectiveText(quest, progress);
            lineY = drawWrapped(objective, x + 8, lineY, width - 8, LostTalesSkyrimUiStyle.TEXT, Math.max(10, bottom - lineY));
            lineY += 5;
        }
    }

    private int drawLabelValue(String label, String value, int x, int y, int width) {
        String safeValue = value == null ? "" : value;
        String labelText = label + ":";
        int labelWidth = Math.min(Math.max(74, this.fontRendererObj.getStringWidth(labelText) + 8),
                Math.max(74, width / 2));
        this.fontRendererObj.drawStringWithShadow(labelText, x, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, safeValue,
                        Math.max(20, width - labelWidth)),
                x + labelWidth, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        return y + 11;
    }

    private int drawWrapped(String text, int x, int y, int width, int color, int maxHeight) {
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text == null ? "" : text, Math.max(20, width));
        int bottom = y + maxHeight;
        int lineY = y;
        for (String line : lines) {
            if (lineY + 9 > bottom) {
                this.fontRendererObj.drawStringWithShadow("...", x, lineY, color);
                return bottom;
            }
            this.fontRendererObj.drawStringWithShadow(line, x, lineY, color);
            lineY += 10;
        }
        return lineY;
    }

    private String getPrimaryObjectiveText(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        LostTalesQuestStageDefinition stage = findStage(quest, progress);
        if (stage == null || stage.getObjectives().isEmpty()) {
            return "No active objective.";
        }
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            int target = LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
            int current = progress == null ? 0 : progress.getObjectiveProgress(objective.getId());
            if (current < target) {
                return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, true, false, false, false);
            }
        }
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, stage.getObjectives().get(0), true, false, false, false);
    }

    private LostTalesQuestStageDefinition findStage(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        if (quest == null || progress == null || quest.getStages().isEmpty()) {
            return null;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage.getId() != null && stage.getId().equals(progress.getStageId())) {
                return stage;
            }
        }
        int index = progress.getStageIndex();
        return index >= 0 && index < quest.getStages().size() ? quest.getStages().get(index) : quest.getFirstStage();
    }

    private void drawEntityModel(int x, int y, int scale, float yaw, float pitch, EntityLivingBase entity) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();

        float previousRenderYawOffset = entity.renderYawOffset;
        float previousRotationYaw = entity.rotationYaw;
        float previousRotationPitch = entity.rotationPitch;
        float previousPrevRotationYawHead = entity.prevRotationYawHead;
        float previousRotationYawHead = entity.rotationYawHead;
        float previousPlayerViewY = RenderManager.instance.playerViewY;
        boolean previousDebugBoundingBox = RenderManager.debugBoundingBox;

        try {
            GL11.glTranslatef((float)x, (float)y, 50.0F);
            GL11.glScalef((float)(-scale), (float)scale, (float)scale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            // GuiInventory applies this compensation before RenderPlayer, which
            // subtracts yOffset from its render Y. Without it, a player model is
            // displaced downward by roughly 1.62 * preview scale pixels.
            GL11.glTranslatef(0.0F, entity.yOffset, 0.0F);

            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-pitch, 1.0F, 0.0F, 0.0F);
            entity.renderYawOffset = yaw;
            entity.rotationYaw = yaw;
            entity.rotationYawHead = yaw;
            entity.prevRotationYawHead = yaw;
            entity.rotationPitch = pitch * 0.25F;
            RenderManager.instance.playerViewY = 180.0F;
            // F3+B is useful in-world but should not be rendered inside a GUI.
            RenderManager.debugBoundingBox = false;
            RenderManager.instance.renderEntityWithPosYaw(
                    entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        } finally {
            entity.renderYawOffset = previousRenderYawOffset;
            entity.rotationYaw = previousRotationYaw;
            entity.rotationPitch = previousRotationPitch;
            entity.prevRotationYawHead = previousPrevRotationYawHead;
            entity.rotationYawHead = previousRotationYawHead;
            RenderManager.instance.playerViewY = previousPlayerViewY;
            RenderManager.debugBoundingBox = previousDebugBoundingBox;
            GL11.glPopMatrix();
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawFooterHelp() {
        String help = I18n.format("gui.losttales.character.profile_help",
                LostTalesKeyBindings.getQuestJournalKeyDisplayName(),
                LostTalesKeyBindings.getCharacterMenuKeyDisplayName());
        this.fontRendererObj.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, help, this.width - 180), 92, this.height - 22, LostTalesSkyrimUiStyle.TEXT_MUTED);
    }

    private CharacterSummary getActiveCharacter() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null ? null : snapshot.getActiveCharacter();
    }

    private String getCharacterName() {
        CharacterSummary character = getActiveCharacter();
        return character == null ? I18n.format("gui.losttales.character.loading")
                : character.getName();
    }

    private String getAccountName() {
        EntityPlayer player = this.mc == null ? null : this.mc.thePlayer;
        return player == null ? I18n.format("gui.losttales.character.unknown")
                : player.getCommandSenderName();
    }

    private String getDimensionName() {
        World world = this.mc == null ? null : this.mc.theWorld;
        if (world == null || world.provider == null) {
            return "Unknown";
        }
        try {
            return world.provider.getDimensionName();
        } catch (Throwable ignored) {
            return "Dimension " + world.provider.dimensionId;
        }
    }

    private String getWorldTimeText() {
        World world = this.mc == null ? null : this.mc.theWorld;
        if (world == null) {
            return "Unknown";
        }
        long total = world.getWorldTime();
        long day = total / 24000L + 1L;
        long timeOfDay = (total + 6000L) % 24000L;
        int hour = (int)(timeOfDay / 1000L);
        int minute = (int)((timeOfDay % 1000L) * 60L / 1000L);
        return "Day " + day + ", " + (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_PARTY) {
            this.mc.displayGuiScreen(new LostTalesPartyManagementGui(this));
            return;
        }
        if (button.id == BUTTON_CAPE) {
            this.mc.displayGuiScreen(new LostTalesCharacterCapeGui(this));
            return;
        }
        if (button.id == BUTTON_MANAGE) {
            this.mc.displayGuiScreen(new LostTalesCharacterRosterGui(this, false));
            return;
        }
        if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isMouseInsideModelPanel(mouseX, mouseY)) {
            this.draggingModel = true;
            this.lastDragX = mouseX;
            this.lastDragY = mouseY;
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.draggingModel && clickedMouseButton == 0) {
            int dx = mouseX - this.lastDragX;
            int dy = mouseY - this.lastDragY;
            this.modelYaw = normalizeDegrees(this.modelYaw + dx * 1.4F);
            this.modelPitch = clamp(this.modelPitch + dy * 0.45F, -35.0F, 35.0F);
            this.lastDragX = mouseX;
            this.lastDragY = mouseY;
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        this.draggingModel = false;
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int delta = wheel > 0 ? 4 : -4;
            this.modelScale = (int)clamp(this.modelScale + delta, 28.0F, 76.0F);
        }
    }

    private boolean isMouseInsideModelPanel(int mouseX, int mouseY) {
        return mouseX >= this.modelPanelX && mouseX < this.modelPanelX + this.modelPanelWidth && mouseY >= this.modelPanelY && mouseY < this.modelPanelY + this.modelPanelHeight;
    }

    private float normalizeDegrees(float value) {
        value %= 360.0F;
        if (value < 0.0F) {
            value += 360.0F;
        }
        return value;
    }

    private float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || LostTalesKeyBindings.isCharacterMenuKey(keyCode)) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (LostTalesKeyBindings.isQuestJournalKey(keyCode)) {
            this.mc.displayGuiScreen(new LostTalesQuestJournalGui(this.parent));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
