package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Asked before a marker the fellowship sees is disturbed: the "go here"
 * marker, or one of a fellowship's marks.
 *
 * <p>The "go here" marker is often the only thing telling everyone where
 * they are headed, so moving it is a decision rather than a side effect of
 * clicking the map. Its first placement asks nothing; every later click on
 * empty map comes through here, and clicking the marker itself asks the
 * same without a place to move to. A mark is asked about when it is
 * clicked, and Move It picks its new place on the map.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapMoveMarkerPrompt {
    enum Action {
        NONE,
        MOVE,
        LEAVE,
        REMOVE
    }

    private final String question;
    /** Whether Move It does something: a place was picked, or one is picked next. */
    private final boolean canMove;

    LostTalesMapMoveMarkerPrompt(String question, boolean canMove) {
        this.question = question == null ? "" : question;
        this.canMove = canMove;
    }

    void render(int screenWidth, int screenHeight,
                int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesMapPopupAnimation.begin(this);
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        screenWidth, screenHeight);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        int localMouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        int localMouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        LostTalesMapChoicePrompt.renderShadeFixed(
                screenWidth, screenHeight, null);
        LostTalesMapPopupAnimation.push(this, pivotX, pivotY);
        try {
            LostTalesMapChoicePrompt.renderPanelContents(font, layout,
                    this.question, null);
            LostTalesMapChoicePrompt.drawButton(font, layout.first,
                    I18n.format("gui.losttales.map.move_marker.move"),
                    layout.first.contains(localMouseX, localMouseY),
                    this.canMove);
            LostTalesMapChoicePrompt.drawButton(font, layout.second,
                    I18n.format("gui.losttales.map.move_marker.leave"),
                    layout.second.contains(localMouseX, localMouseY), true);
            LostTalesMapChoicePrompt.drawButton(font, layout.third,
                    I18n.format("gui.losttales.map.move_marker.remove"),
                    layout.third.contains(localMouseX, localMouseY), true);
        } finally {
            LostTalesMapPopupAnimation.pop();
        }
    }

    Action mouseClicked(int screenWidth, int screenHeight,
                        int mouseX, int mouseY, int button) {
        if (button != 0) {
            return Action.NONE;
        }
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(
                        screenWidth, screenHeight);
        int pivotX = layout.x + layout.width / 2;
        int pivotY = layout.y + layout.height / 2;
        mouseX = LostTalesMapPopupAnimation.inverseMouseX(
                this, mouseX, pivotX);
        mouseY = LostTalesMapPopupAnimation.inverseMouseY(
                this, mouseY, pivotY);
        if (layout.first.contains(mouseX, mouseY)) {
            return this.canMove ? Action.MOVE : Action.NONE;
        }
        if (layout.second.contains(mouseX, mouseY)) {
            return Action.LEAVE;
        }
        if (layout.third.contains(mouseX, mouseY)) {
            return Action.REMOVE;
        }
        return Action.NONE;
    }

    /** Whether a click here would do something; see the fast-travel popup. */
    boolean isPointerOverAction(int screenWidth, int screenHeight,
                                int mouseX, int mouseY) {
        return mouseClicked(screenWidth, screenHeight,
                mouseX, mouseY, 0) != Action.NONE;
    }

    Action keyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return Action.LEAVE;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            // Enter confirms what the popup was opened for; with nothing to
            // move to there is nothing to confirm.
            return this.canMove ? Action.MOVE : Action.NONE;
        }
        return Action.NONE;
    }
}
