package com.ninuna.losttales.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A screen that wants the world behind it bare: no hotbar, no hearts, no
 * compass, none of the HUD this mod or the game draws over the world.
 *
 * <p>The game keeps drawing its overlay behind an open screen, so a screen
 * cannot simply not draw it. The client event handler cancels each element
 * of the overlay, and holds back this mod's own panels, while the open
 * screen is one of these.</p>
 */
@SideOnly(Side.CLIENT)
public interface LostTalesHudHidingScreen {
}
