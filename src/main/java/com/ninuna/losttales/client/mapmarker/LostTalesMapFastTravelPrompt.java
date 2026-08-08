package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** Modal, map-local confirmation shown before an existing travel request. */
@SideOnly(Side.CLIENT)
final class LostTalesMapFastTravelPrompt {
    enum Action {
        NONE,
        YES,
        NO,
        PLACE_MARKER
    }

    private final String destinationName;
    /**
     * Localization key explaining why travel is refused, or null while it is
     * offered. The client only decides what to show; the server re-derives
     * eligibility for every request it receives.
     */
    private final String blockedReasonKey;

    LostTalesMapFastTravelPrompt(String destinationName) {
        this(destinationName, null);
    }

    LostTalesMapFastTravelPrompt(
            String destinationName, String blockedReasonKey) {
        String normalized = destinationName == null
                ? "" : destinationName.trim();
        this.destinationName = normalized.length() == 0
                ? I18n.format("gui.losttales.map.fast_travel.unknown")
                : normalized;
        this.blockedReasonKey =
                blockedReasonKey == null || blockedReasonKey.length() == 0
                        ? null : blockedReasonKey;
    }

    String getDestinationName() {
        return this.destinationName;
    }

    boolean isTravelOffered() {
        return this.blockedReasonKey == null;
    }

    /** Where the destination this popup is about should be framed. */
    static float focusAnchorY(int screenWidth, int screenHeight) {
        return LostTalesMapChoicePrompt.focusAnchorY(
                screenWidth, screenHeight);
    }

    void render(LostTalesLotrMapGui gui, int mouseX, int mouseY,
                boolean canPlaceMarker) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (gui == null || minecraft == null
                || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        gui.width, gui.height);
        // The destination keeps its full brightness inside its own frame,
        // so the marker being asked about is the one thing on the map the
        // popup does not dim.
        int[] litArea = new int[4];
        LostTalesMapChoicePrompt.renderPanel(font, layout,
                gui.width, gui.height,
                I18n.format("gui.losttales.map.fast_travel.prompt",
                        this.destinationName),
                this.blockedReasonKey == null
                        ? null : I18n.format(this.blockedReasonKey),
                LostTalesLotrMapMarkerIconOverlay
                        .getSelectedMarkerFrame(gui, litArea)
                        ? litArea : null);
        LostTalesMapChoicePrompt.drawButton(font, layout.first,
                I18n.format("gui.losttales.map.fast_travel.yes"),
                layout.first.contains(mouseX, mouseY),
                isTravelOffered());
        LostTalesMapChoicePrompt.drawButton(font, layout.second,
                I18n.format("gui.losttales.map.fast_travel.no"),
                layout.second.contains(mouseX, mouseY), true);
        LostTalesMapChoicePrompt.drawButton(font, layout.third,
                I18n.format(
                        "gui.losttales.map.fast_travel.place_marker"),
                layout.third.contains(mouseX, mouseY),
                canPlaceMarker);
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button,
                        boolean canPlaceMarker) {
        if (button != 0) {
            return Action.NONE;
        }
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        screenWidth, screenHeight);
        if (layout.first.contains(mouseX, mouseY)) {
            return isTravelOffered() ? Action.YES : Action.NONE;
        }
        if (layout.second.contains(mouseX, mouseY)) {
            return Action.NO;
        }
        if (canPlaceMarker && layout.third.contains(mouseX, mouseY)) {
            return Action.PLACE_MARKER;
        }
        return Action.NONE;
    }

    Action keyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE
                || keyCode == Keyboard.KEY_N) {
            return Action.NO;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER
                || keyCode == Keyboard.KEY_Y) {
            return isTravelOffered() ? Action.YES : Action.NONE;
        }
        return Action.NONE;
    }
}
