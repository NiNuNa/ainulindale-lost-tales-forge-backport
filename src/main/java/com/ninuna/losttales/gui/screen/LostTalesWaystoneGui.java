package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.client.mapmarker.LostTalesClientWaystoneStateStore;
import com.ninuna.losttales.client.mapmarker.LostTalesClientWaystoneTravelContext;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.inventory.container.LostTalesContainerWaystone;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import java.text.DecimalFormat;
import lotr.common.LOTRDimension;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Paged, server-authoritative editor for all mutable JSON marker fields.
 * Stable ID/link identity remain visible but immutable. Location and
 * structure-integrity fields are operator-only.
 */
public final class LostTalesWaystoneGui extends GuiContainer {
    private static final int PAGE_MARKER = 0;
    private static final int PAGE_LOCATION = 1;
    private static final int PAGE_RULES = 2;
    private static final int PAGE_SHARING = 3;
    private static final int PAGE_COUNT = 4;
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.##");

    static {
        NUMBER_FORMAT.setGroupingUsed(false);
    }

    private final LostTalesTileEntityWaystone waystone;
    private final GuiTextField[] markerFields = new GuiTextField[6];
    private final GuiTextField[] locationFields = new GuiTextField[6];
    private GuiTextField structureField;
    private GuiTextField sharedPlayerField;
    private final GuiButton[] tabButtons = new GuiButton[PAGE_COUNT];
    private GuiButton visibilityButton;
    private GuiButton fastTravelButton;
    private GuiButton discoverableButton;
    private GuiButton hiddenButton;
    private GuiButton regionButton;
    private GuiButton hasWaystoneButton;
    private GuiButton shareButton;
    private GuiButton unshareButton;
    private GuiButton saveButton;
    private GuiButton destinationsButton;
    private LostTalesWaystoneStatePacket state;
    private LostTalesMapMarkerVisibility selectedVisibility =
            LostTalesMapMarkerVisibility.PRIVATE;
    private boolean selectedFastTravel;
    private boolean selectedDiscoverable;
    private boolean selectedHidden;
    private boolean selectedRequiresRegion;
    private boolean selectedHasWaystone = true;
    private int page;
    private String status = "";
    private int statusColor = 0xD6B45B;

    public LostTalesWaystoneGui(
            LostTalesTileEntityWaystone waystone) {
        super(new LostTalesContainerWaystone(waystone));
        this.waystone = waystone;
        this.xSize = 390;
        this.ySize = 260;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        createFields();
        createButtons();
        acceptLatestState();
        updateControls();
    }

    private void createFields() {
        int fieldX = this.guiLeft + 126;
        int fieldWidth = this.xSize - 138;
        for (int index = 0; index < 6; index++) {
            this.markerFields[index] = field(
                    fieldX, this.guiTop + 55 + index * 25,
                    fieldWidth, index == 4 ? 1024 : 256);
            this.locationFields[index] = field(
                    fieldX, this.guiTop + 55 + index * 25,
                    fieldWidth, 32);
        }
        this.structureField = field(
                fieldX, this.guiTop + 60, fieldWidth, 128);
        this.sharedPlayerField = field(
                fieldX, this.guiTop + 70, fieldWidth, 32);
    }

    private GuiTextField field(
            int x, int y, int width, int maximumLength) {
        GuiTextField result = new GuiTextField(
                this.fontRendererObj, x, y, width, 18);
        result.setMaxStringLength(maximumLength);
        return result;
    }

    private void createButtons() {
        int tabWidth = 90;
        for (int index = 0; index < PAGE_COUNT; index++) {
            this.tabButtons[index] = new GuiButton(
                    20 + index,
                    this.guiLeft + 12 + index * 92,
                    this.guiTop + 25, tabWidth, 20,
                    I18n.format("gui.losttales.waystone.tab." + index));
            this.buttonList.add(this.tabButtons[index]);
        }
        this.discoverableButton = button(30, 12, 90, 178);
        this.hiddenButton = button(31, 200, 90, 178);
        this.regionButton = button(32, 12, 116, 178);
        this.fastTravelButton = button(33, 200, 116, 178);
        this.hasWaystoneButton = button(34, 12, 142, 178);
        this.visibilityButton = button(35, 200, 142, 178);
        this.shareButton = button(
                36, 126, 100, 116,
                I18n.format("gui.losttales.waystone.share"));
        this.unshareButton = button(
                37, 252, 100, 116,
                I18n.format("gui.losttales.waystone.unshare"));

        this.destinationsButton = button(
                40, 12, 230, 116,
                I18n.format("gui.losttales.waystone.destinations"));
        this.saveButton = button(
                41, 137, 230, 116,
                I18n.format("gui.losttales.waystone.save"));
        button(42, 262, 230, 116, I18n.format("gui.done"));
    }

    private GuiButton button(
            int id, int x, int y, int width) {
        return button(id, x, y, width, "");
    }

    private GuiButton button(
            int id, int x, int y, int width, String text) {
        GuiButton result = new GuiButton(
                id, this.guiLeft + x, this.guiTop + y,
                width, 20, text);
        this.buttonList.add(result);
        return result;
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : activeFields()) {
            field.updateCursorCounter();
        }
        acceptLatestState();
        updateControls();
    }

    private void acceptLatestState() {
        LostTalesWaystoneStatePacket latest =
                LostTalesClientWaystoneStateStore.get(
                        this.mc.thePlayer == null
                                ? 0 : this.mc.thePlayer.dimension,
                        this.waystone.xCoord, this.waystone.yCoord,
                        this.waystone.zCoord);
        if (latest == null || latest.isMalformed()
                || latest == this.state
                || !this.waystone.getMarkerId().equals(
                        latest.getMarkerId())) {
            return;
        }
        this.state = latest;
        this.markerFields[0].setText(latest.getName());
        this.markerFields[1].setText(latest.getIconName());
        this.markerFields[2].setText(latest.getColor());
        this.markerFields[3].setText(latest.getCategoryName());
        this.markerFields[4].setText(latest.getDescription());
        this.markerFields[5].setText(
                latest.getFastTravelWaypointCode());
        this.locationFields[0].setText(
                String.valueOf(latest.getMarkerDimensionId()));
        this.locationFields[1].setText(
                format(latest.getMarkerX()));
        this.locationFields[2].setText(
                LostTalesMapMarkerHeightResolver.isAutomatic(
                        latest.getMarkerY())
                        ? "auto" : format(latest.getMarkerY()));
        this.locationFields[3].setText(
                format(latest.getMarkerZ()));
        this.locationFields[4].setText(
                format(latest.getCompassFadeInRadius()));
        this.locationFields[5].setText(
                format(latest.getDiscoveryRadius()));
        this.structureField.setText(
                latest.getWaystoneStructureType());
        this.selectedVisibility = latest.getVisibility();
        this.selectedFastTravel = latest.hasFastTravel();
        this.selectedDiscoverable = latest.isDiscoverable();
        this.selectedHidden = latest.isHiddenUntilDiscovered();
        this.selectedRequiresRegion =
                latest.requiresRegionUnlock();
        this.selectedHasWaystone = latest.hasWaystone();
        this.status = I18n.format(
                "gui.losttales.waystone.shared_count",
                Integer.valueOf(latest.getSharedPlayerCount()));
        this.statusColor = 0xD6B45B;
    }

    private void updateControls() {
        boolean editable = this.state != null && this.state.canEdit();
        boolean advanced = editable
                && this.state.canEditAdvanced();
        for (GuiTextField field : this.markerFields) {
            field.setEnabled(editable);
        }
        for (GuiTextField field : this.locationFields) {
            field.setEnabled(advanced);
        }
        this.markerFields[5].setEnabled(advanced);
        this.structureField.setEnabled(advanced);
        this.sharedPlayerField.setEnabled(editable);
        this.discoverableButton.enabled = editable;
        this.hiddenButton.enabled =
                editable && this.selectedDiscoverable;
        this.regionButton.enabled = editable;
        this.fastTravelButton.enabled = editable;
        /*
         * This screen exists on a live linked physical waystone. Turning
         * hasWaystone off would make the record contradict the block/link,
         * so the field is shown as part of the JSON schema but cannot be
         * toggled here.
         */
        this.hasWaystoneButton.enabled = false;
        this.visibilityButton.enabled = editable;
        this.shareButton.enabled = editable;
        this.unshareButton.enabled = editable;
        this.saveButton.enabled = editable;
        this.destinationsButton.enabled = this.state != null
                && this.state.hasFastTravel()
                && this.mc.thePlayer != null
                && this.mc.thePlayer.dimension
                        == LOTRDimension.MIDDLE_EARTH.dimensionID;

        for (int index = 0; index < PAGE_COUNT; index++) {
            this.tabButtons[index].enabled = index != this.page;
        }
        setVisible(this.discoverableButton, this.page == PAGE_RULES);
        setVisible(this.hiddenButton, this.page == PAGE_RULES);
        setVisible(this.regionButton, this.page == PAGE_RULES);
        setVisible(this.fastTravelButton, this.page == PAGE_RULES);
        setVisible(this.hasWaystoneButton, this.page == PAGE_RULES);
        setVisible(this.visibilityButton, this.page == PAGE_RULES);
        setVisible(this.shareButton, this.page == PAGE_SHARING);
        setVisible(this.unshareButton, this.page == PAGE_SHARING);

        this.discoverableButton.displayString = toggleLabel(
                "discoverable", this.selectedDiscoverable);
        this.hiddenButton.displayString = toggleLabel(
                "hidden", this.selectedHidden);
        this.regionButton.displayString = toggleLabel(
                "region", this.selectedRequiresRegion);
        this.fastTravelButton.displayString = toggleLabel(
                "fast_travel_short", this.selectedFastTravel);
        this.hasWaystoneButton.displayString = toggleLabel(
                "has_waystone", this.selectedHasWaystone);
        this.visibilityButton.displayString = I18n.format(
                "gui.losttales.waystone.visibility",
                I18n.format("gui.losttales.waystone.visibility."
                        + this.selectedVisibility.getSerializedName()));
    }

    private static void setVisible(
            GuiButton button, boolean visible) {
        button.visible = visible;
    }

    private static String toggleLabel(String field, boolean enabled) {
        return I18n.format(
                "gui.losttales.waystone." + field,
                I18n.format(enabled ? "options.on" : "options.off"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        if (button.id >= 20 && button.id < 20 + PAGE_COUNT) {
            this.page = button.id - 20;
            clearFocus();
        } else if (button.id == 42) {
            this.mc.thePlayer.closeScreen();
            return;
        } else if (button.id == 40 && this.state != null
                && this.state.hasFastTravel()) {
            LostTalesClientWaystoneTravelContext.begin(
                    this.mc.thePlayer.dimension,
                    this.waystone.xCoord, this.waystone.yCoord,
                    this.waystone.zCoord, this.state.getMarkerId());
            this.mc.displayGuiScreen(new LostTalesLotrMapGui());
            return;
        } else if (this.state == null || !this.state.canEdit()) {
            return;
        } else if (button.id == 30) {
            this.selectedDiscoverable =
                    !this.selectedDiscoverable;
            if (!this.selectedDiscoverable) {
                this.selectedHidden = false;
            }
        } else if (button.id == 31) {
            this.selectedHidden = !this.selectedHidden;
        } else if (button.id == 32) {
            this.selectedRequiresRegion =
                    !this.selectedRequiresRegion;
        } else if (button.id == 33) {
            this.selectedFastTravel = !this.selectedFastTravel;
        } else if (button.id == 35) {
            cycleVisibility();
        } else if (button.id == 36 || button.id == 37) {
            sendShare(button.id == 37);
        } else if (button.id == 41) {
            sendSave();
        }
        updateControls();
    }

    private void cycleVisibility() {
        if (this.selectedVisibility
                == LostTalesMapMarkerVisibility.PRIVATE) {
            this.selectedVisibility =
                    LostTalesMapMarkerVisibility.SHARED;
        } else if (this.selectedVisibility
                == LostTalesMapMarkerVisibility.SHARED
                && this.state.canMakePublic()) {
            this.selectedVisibility =
                    LostTalesMapMarkerVisibility.PUBLIC;
        } else {
            this.selectedVisibility =
                    LostTalesMapMarkerVisibility.PRIVATE;
        }
    }

    private void sendSave() {
        try {
            LostTalesMapMarkerEditableSettings settings =
                    new LostTalesMapMarkerEditableSettings(
                            text(this.markerFields[0]),
                            text(this.markerFields[1]),
                            text(this.markerFields[2]),
                            text(this.markerFields[3]),
                            text(this.markerFields[4]),
                            this.selectedFastTravel,
                            text(this.markerFields[5]),
                            Integer.parseInt(
                                    text(this.locationFields[0])),
                            number(this.locationFields[1]),
                            markerY(this.locationFields[2]),
                            number(this.locationFields[3]),
                            number(this.locationFields[4]),
                            number(this.locationFields[5]),
                            this.selectedHidden,
                            this.selectedDiscoverable,
                            this.selectedRequiresRegion,
                            this.selectedHasWaystone,
                            text(this.structureField),
                            this.selectedVisibility);
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    LostTalesWaystoneSettingsRequestPacket.save(
                            this.waystone.xCoord,
                            this.waystone.yCoord,
                            this.waystone.zCoord,
                            this.state.getMarkerId(),
                            this.state.getRevision(), settings));
            this.status = I18n.format(
                    "gui.losttales.waystone.saving");
            this.statusColor = 0xD6B45B;
        } catch (RuntimeException exception) {
            setError("gui.losttales.waystone.invalid_settings");
        }
    }

    private static String text(GuiTextField field) {
        return field.getText().trim();
    }

    private static double number(GuiTextField field) {
        return Double.parseDouble(text(field));
    }

    private static double markerY(GuiTextField field) {
        String value = text(field);
        return value.length() == 0 || "auto".equalsIgnoreCase(value)
                ? LostTalesMapMarkerHeightResolver.AUTOMATIC_Y
                : Double.parseDouble(value);
    }

    private void sendShare(boolean remove) {
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    LostTalesWaystoneSettingsRequestPacket.share(
                            remove,
                            this.waystone.xCoord,
                            this.waystone.yCoord,
                            this.waystone.zCoord,
                            this.state.getMarkerId(),
                            this.state.getRevision(),
                            this.sharedPlayerField.getText()));
            this.status = I18n.format(
                    "gui.losttales.waystone.saving");
            this.statusColor = 0xD6B45B;
        } catch (IllegalArgumentException exception) {
            setError("gui.losttales.waystone.invalid_player");
        }
    }

    private void setError(String key) {
        this.status = I18n.format(key);
        this.statusColor = 0xE57373;
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            super.keyTyped(character, keyCode);
            return;
        }
        for (GuiTextField field : activeFields()) {
            if (field.textboxKeyTyped(character, keyCode)) {
                return;
            }
        }
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(
            int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : activeFields()) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(
            int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (GuiTextField field : activeFields()) {
            field.drawTextBox();
        }
    }

    private GuiTextField[] activeFields() {
        if (this.page == PAGE_MARKER) {
            return this.markerFields;
        }
        if (this.page == PAGE_LOCATION) {
            return this.locationFields;
        }
        if (this.page == PAGE_RULES) {
            return new GuiTextField[] {this.structureField};
        }
        return new GuiTextField[] {this.sharedPlayerField};
    }

    private void clearFocus() {
        for (GuiTextField field : this.markerFields) {
            field.setFocused(false);
        }
        for (GuiTextField field : this.locationFields) {
            field.setFocused(false);
        }
        this.structureField.setFocused(false);
        this.sharedPlayerField.setFocused(false);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(
            int mouseX, int mouseY) {
        String title = I18n.format(
                "gui.losttales.waystone.title");
        this.fontRendererObj.drawString(
                title, (this.xSize
                        - this.fontRendererObj.getStringWidth(title)) / 2,
                8, 0xF4E6C1);
        if (this.page == PAGE_MARKER) {
            drawLabels(new String[] {
                    "name", "icon", "color", "category",
                    "description", "fast_travel_code"
            });
        } else if (this.page == PAGE_LOCATION) {
            drawLabels(new String[] {
                    "dimension", "x", "y", "z",
                    "compass_radius", "radius"
            });
            if (this.state != null && !this.state.canEditAdvanced()) {
                drawNote("gui.losttales.waystone.operator_fields");
            }
        } else if (this.page == PAGE_RULES) {
            this.fontRendererObj.drawString(
                    I18n.format(
                            "gui.losttales.waystone.structure_type"),
                    12, 65, 0xE0D7C4);
            if (this.state != null) {
                this.fontRendererObj.drawString(
                        I18n.format("gui.losttales.waystone.marker_id",
                                this.state.getMarkerId()),
                        12, 177, 0xA8A8A8);
            }
            drawNote("gui.losttales.waystone.linked_waystone_note");
        } else {
            this.fontRendererObj.drawString(
                    I18n.format("gui.losttales.waystone.player"),
                    12, 75, 0xE0D7C4);
            if (this.state != null) {
                this.fontRendererObj.drawString(
                        I18n.format(
                                "gui.losttales.waystone.shared_count",
                                Integer.valueOf(
                                        this.state
                                                .getSharedPlayerCount())),
                        12, 135, 0xD6B45B);
            }
        }
        if (this.state == null) {
            this.fontRendererObj.drawString(
                    I18n.format("gui.losttales.waystone.loading"),
                    12, 216, 0xD6B45B);
        } else if (!this.state.canEdit()) {
            this.fontRendererObj.drawString(
                    EnumChatFormatting.RED
                            + I18n.format(
                                    "gui.losttales.waystone.read_only"),
                    12, 216, 0xFFFFFF);
        } else if (this.status.length() > 0) {
            this.fontRendererObj.drawString(
                    this.status, 12, 216, this.statusColor);
        }
    }

    private void drawLabels(String[] labels) {
        for (int index = 0; index < labels.length; index++) {
            this.fontRendererObj.drawString(
                    I18n.format("gui.losttales.waystone."
                            + labels[index]),
                    12, 60 + index * 25, 0xE0D7C4);
        }
    }

    private void drawNote(String key) {
        this.fontRendererObj.drawString(
                I18n.format(key), 12, 202, 0xA8A8A8);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(
            float partialTicks, int mouseX, int mouseY) {
        GL11.glDisable(GL11.GL_LIGHTING);
        drawRect(this.guiLeft, this.guiTop,
                this.guiLeft + this.xSize,
                this.guiTop + this.ySize, 0xF0141414);
        drawRect(this.guiLeft + 5, this.guiTop + 5,
                this.guiLeft + this.xSize - 5,
                this.guiTop + this.ySize - 5, 0x802E2921);
        drawRect(this.guiLeft + 10, this.guiTop + 48,
                this.guiLeft + this.xSize - 10,
                this.guiTop + 49, 0xFFD6B45B);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private static String format(double value) {
        return NUMBER_FORMAT.format(value);
    }
}
