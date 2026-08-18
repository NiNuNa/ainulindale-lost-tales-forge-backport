package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/**
 * Whether the thing under the pointer answers to a click.
 *
 * <p>The only consumer is the pointer's own artwork, so a wrong answer costs a
 * hand where an arrow belonged and nothing more. Everything here therefore
 * fails closed: an unreadable screen reads as "nothing to click", and the
 * pointer keeps its arrow.</p>
 *
 * <p>Two things are found without the screen's help. Buttons, because every
 * screen keeps them in the same list and hit-tests them the same way; and the
 * container slot under the pointer, which {@code GuiContainer} has already
 * resolved for this frame by the time the pointer is drawn — reading its
 * answer keeps the hand on exactly the slots the GUI itself considers hit.
 * Anything a screen draws and hit-tests on its own is its own to report,
 * through {@link LostTalesPointerInteractable}.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesGuiPointerTargets {
    private static Field buttonListField;
    private static Field hoveredSlotField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LostTalesGuiPointerTargets() {}

    public static boolean isOverInteractable(
            GuiScreen gui, int mouseX, int mouseY) {
        if (gui == null) {
            return false;
        }
        try {
            // A screen that answers for itself answers for all of itself: a
            // popup of its own can cover a button, and only the screen knows
            // that the button behind it is no longer reachable.
            if (gui instanceof LostTalesPointerInteractable) {
                return ((LostTalesPointerInteractable)gui)
                        .isPointerOverInteractable(mouseX, mouseY);
            }
            return isOverEnabledButton(gui, mouseX, mouseY)
                    || isOverContainerSlot(gui);
        } catch (Throwable ignored) {
            // A screen that cannot answer keeps the plain pointer rather than
            // taking the frame down with it.
            return false;
        }
    }

    /** Exposed so a screen answering for itself can still ask about buttons. */
    public static boolean isOverEnabledButton(
            GuiScreen gui, int mouseX, int mouseY) {
        if (!ensureReflection()) {
            return false;
        }
        List<?> buttons;
        try {
            buttons = (List<?>)buttonListField.get(gui);
        } catch (Throwable throwable) {
            markReflectionFailed(throwable);
            return false;
        }
        if (buttons == null) {
            return false;
        }
        for (int index = 0; index < buttons.size(); index++) {
            Object value = buttons.get(index);
            if (!(value instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton)value;
            // A hidden button is not there, and a disabled one is there to be
            // read rather than pressed; neither takes a click.
            if (button.visible && button.enabled
                    && mouseX >= button.xPosition
                    && mouseX < button.xPosition + button.width
                    && mouseY >= button.yPosition
                    && mouseY < button.yPosition + button.height) {
                return true;
            }
        }
        return false;
    }

    /** The slot {@code GuiContainer} resolved for this frame, if any. */
    public static boolean isOverContainerSlot(GuiScreen gui) {
        if (!(gui instanceof GuiContainer) || !ensureReflection()) {
            return false;
        }
        try {
            return hoveredSlotField.get(gui) != null;
        } catch (Throwable throwable) {
            markReflectionFailed(throwable);
            return false;
        }
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            // Obfuscated and deobfuscated names both, and the type checked
            // rather than assumed: GuiScreen keeps two lists side by side and
            // GuiContainer several slots, so the name picks the member and
            // the shape confirms it is the one meant.
            buttonListField = field(GuiScreen.class, List.class,
                    "buttonList", "field_146292_n");
            hoveredSlotField = field(GuiContainer.class, Slot.class,
                    "theSlot", "field_147006_u");
            reflectionReady = true;
            return true;
        } catch (Throwable throwable) {
            markReflectionFailed(throwable);
            return false;
        }
    }

    private static Field field(
            Class<?> owner, Class<?> type, String... names)
            throws NoSuchFieldException {
        for (int index = 0; index < names.length; index++) {
            try {
                Field candidate = owner.getDeclaredField(names[index]);
                if (type.isAssignableFrom(candidate.getType())) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            } catch (NoSuchFieldException ignored) {
                // Try the other naming convention.
            }
        }
        throw new NoSuchFieldException(
                owner.getName() + "." + names[0]);
    }

    private static synchronized void markReflectionFailed(Throwable cause) {
        if (reflectionFailed) {
            return;
        }
        reflectionReady = false;
        reflectionFailed = true;
        buttonListField = null;
        hoveredSlotField = null;
        FMLLog.warning(
                "[%s] Interactive pointer artwork disabled; the plain pointer"
                        + " is drawn everywhere (%s)",
                LostTalesMetaData.MOD_ID,
                cause == null ? "unknown error" : cause.toString());
    }
}
