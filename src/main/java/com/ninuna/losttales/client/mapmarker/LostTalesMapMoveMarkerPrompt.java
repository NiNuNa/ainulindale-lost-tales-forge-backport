package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Asked before an existing "go here" marker is disturbed.
 *
 * <p>The marker is shared with the party and is often the only thing telling
 * everyone where they are headed, so moving it is a decision rather than a
 * side effect of clicking the map. The first placement asks nothing; every
 * later click on empty map comes through here.</p>
 *
 * <p>Clicking the marker itself opens the same question without a destination
 * to move to, which is how removing it is asked for explicitly instead of
 * happening the moment the marker is touched.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapMoveMarkerPrompt {
    enum Action {
        NONE,
        MOVE,
        LEAVE,
        REMOVE
    }

    /** Whether a new position was picked, which is what Move would apply. */
    private final boolean hasDestination;

    LostTalesMapMoveMarkerPrompt(boolean hasDestination) {
        this.hasDestination = hasDestination;
    }

    boolean hasDestination() {
        return this.hasDestination;
    }

    void render(int screenWidth, int screenHeight,
                int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        screenWidth, screenHeight);
        LostTalesMapChoicePrompt.renderPanel(font, layout,
                screenWidth, screenHeight,
                I18n.format("gui.losttales.map.move_marker.prompt"),
                null);
        LostTalesMapChoicePrompt.drawButton(font, layout.first,
                I18n.format("gui.losttales.map.move_marker.move"),
                layout.first.contains(mouseX, mouseY),
                this.hasDestination);
        LostTalesMapChoicePrompt.drawButton(font, layout.second,
                I18n.format("gui.losttales.map.move_marker.leave"),
                layout.second.contains(mouseX, mouseY), true);
        LostTalesMapChoicePrompt.drawButton(font, layout.third,
                I18n.format("gui.losttales.map.move_marker.remove"),
                layout.third.contains(mouseX, mouseY), true);
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button) {
        if (button != 0) {
            return Action.NONE;
        }
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        screenWidth, screenHeight);
        if (layout.first.contains(mouseX, mouseY)) {
            return this.hasDestination ? Action.MOVE : Action.NONE;
        }
        if (layout.second.contains(mouseX, mouseY)) {
            return Action.LEAVE;
        }
        if (layout.third.contains(mouseX, mouseY)) {
            return Action.REMOVE;
        }
        return Action.NONE;
    }

    Action keyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return Action.LEAVE;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            // Enter confirms what the popup was opened for; with nothing to
            // move to there is nothing to confirm.
            return this.hasDestination ? Action.MOVE : Action.NONE;
        }
        return Action.NONE;
    }
}
