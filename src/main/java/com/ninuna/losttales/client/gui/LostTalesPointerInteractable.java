package com.ninuna.losttales.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A screen that knows which of its own pixels answer to a click.
 *
 * <p>Buttons and container slots are found generically by
 * {@link LostTalesGuiPointerTargets}, so this is only for the widgets a screen
 * draws and hit-tests itself — map icons, legend tiles, popup actions. The
 * pointer is the one place that has to agree with every one of them, so the
 * answer comes from the screen that owns the hit test rather than from a copy
 * of it kept somewhere else.</p>
 *
 * <p>The coordinate is the untransformed one {@code drawScreen} is given: a
 * screen that animates or otherwise moves its widgets applies its own
 * transform here, exactly as it does when a click arrives.</p>
 */
@SideOnly(Side.CLIENT)
public interface LostTalesPointerInteractable {
    boolean isPointerOverInteractable(int mouseX, int mouseY);
}
