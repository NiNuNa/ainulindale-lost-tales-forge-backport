package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A screen that finds what is under the pointer itself, once per frame,
 * and draws its highlights, tips and cards from that one answer. The
 * pointer's artwork asks it for the pose that answer chose, so the pose
 * is never worked out a second way somewhere else.
 *
 * <p>A screen that only knows which of its pixels answer to a click
 * implements {@link LostTalesPointerInteractable} instead; one that knows
 * neither is read for its buttons and its hovered slot. All three reach
 * the pointer through {@link LostTalesGuiPointerTargets#poseFor}.</p>
 */
@SideOnly(Side.CLIENT)
public interface LostTalesPointerOwner {
    /** The pose for the frame just drawn; null for the plain arrow. */
    LostTalesMapCursor.Pose pointerPose(int mouseX, int mouseY);
}
