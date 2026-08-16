package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar.Hint;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lotr.client.LOTRKeyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** Map-specific action declarations for the shared Lost Tales control bar. */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapControlBar {
    public static final int HEIGHT = LostTalesControlBar.HEIGHT;
    private static final int LEFT_GROUP_SIZE = 3;
    private static final int CENTER_RESERVED = 180;

    private LostTalesLotrMapControlBar() {}

    static boolean render(LostTalesLotrMapGui gui) {
        if (!LostTalesLotrMapLayout.isControlBarVisible(gui)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft == null ? null : minecraft.fontRenderer;
        if (minecraft == null || font == null || gui == null) {
            return false;
        }
        return LostTalesControlBar.render(gui, minecraft, font,
                gui.width, gui.height, collectHints(minecraft, font, gui),
                LEFT_GROUP_SIZE, CENTER_RESERVED,
                Arrays.asList(LostTalesLotrMapCalendar.describe()), true);
    }

    private static List<Hint> collectHints(
            Minecraft minecraft, FontRenderer font,
            LostTalesLotrMapGui gui) {
        ArrayList<Hint> hints = new ArrayList<Hint>();
        hints.add(Hint.alternative(minecraft, font,
                LostTalesKeyBindings.getMapKeyBinding(),
                Keyboard.KEY_ESCAPE,
                I18n.format("gui.losttales.map.control.close")));
        hints.add(Hint.wheel(minecraft, font,
                I18n.format("gui.losttales.map.control.zoom")));
        hints.add(Hint.binding(minecraft, font,
                LostTalesKeyBindings.getMapLegendKeyBinding(),
                I18n.format("gui.losttales.map.control.legend")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.FIND_LOCATION_KEY,
                I18n.format("gui.losttales.map.control.find")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.CURRENT_LOCATION_KEY,
                I18n.format("gui.losttales.map.control.location")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.CREATE_WAYPOINT_KEY,
                I18n.format("gui.losttales.map.control.waypoint")));
        if (gui.isPlayerOp) {
            hints.add(Hint.binding(minecraft, font,
                    LOTRKeyHandler.keyBindingMapTeleport,
                    I18n.format("gui.losttales.map.control.teleport")));
        }
        return hints;
    }
}
