package com.ninuna.losttales.client.character.room;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationOptions;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationProfile;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * The screen shown while the room is being left: it leaves the room for
 * the destination it was given. Shown for at most a tick, then that
 * destination is up.
 *
 * <p>Leaving stops the integrated server and must not happen inside
 * the screen change that shows this, so it waits for the screen's first
 * tick, which arrives on the client thread with nothing else in
 * flight.</p>
 */
public final class CharacterRoomLeaveScreen extends GuiScreen
        implements LostTalesGuiAnimationOptions {

    private final CharacterRoomLauncher.Destination destination;
    private boolean left;

    public CharacterRoomLeaveScreen(CharacterRoomLauncher.Destination destination) {
        this.destination = destination == null
                ? CharacterRoomLauncher.Destination.MAIN_MENU : destination;
    }

    @Override
    public void updateScreen() {
        if (this.left || this.mc == null) {
            return;
        }
        this.left = true;
        CharacterRoomLauncher.leave(this.mc, this.destination);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.beginContent();
        String label = I18n.format("gui.losttales.character.room.leaving");
        drawCenteredString(this.fontRendererObj, label, this.width / 2,
                this.height / 2 - this.fontRendererObj.FONT_HEIGHT / 2,
                LostTalesColors.TEXT_BRIGHT);
    }

    /** Leaving is already under way; a key changes nothing. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {}

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** No fade: the world behind it is about to go. */
    @Override
    public LostTalesGuiAnimationProfile getLostTalesGuiAnimationProfile() {
        return LostTalesGuiAnimationProfile.NONE;
    }
}
