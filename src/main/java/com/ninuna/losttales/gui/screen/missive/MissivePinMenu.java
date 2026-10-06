package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.MissiveWords;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The letters a player carries, to pin one onto the board their page
 * shows, in a menu hung from Pin Letter where more than one is carried:
 * each letter's title, and where it is — in hand, on the hotbar or in the
 * pack — the one in hand first. Taking a row pins that letter; the menu
 * closes with the board's page, and reads the inventory again as it
 * stands.
 */
final class MissivePinMenu {
    /** The menu's kind, registered as the client starts, before the layout file names it. */
    static final SubWindowKind KIND = SubWindowKind.register("missive_pin",
            "gui.losttales.missive_board.pin.title");
    /** Marks a letter's row; the rest of the id is its inventory slot. */
    private static final String SLOT_PREFIX = "slot:";
    /** The hotbar's slots, which a player sees at the bottom of the screen. */
    private static final int HOTBAR_SLOTS = 9;

    private MissivePinMenu() {}

    /** Gives every screen the menu. */
    static void install() {
        WindowMenus.registerShared(KIND, new Source());
    }

    /** Shows the menu, hung from the pointer, in the board page's window. */
    static void show(String windowId) {
        WindowScreen screen = WindowScreen.current();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (screen == null || minecraft == null) {
            return;
        }
        int x = (int)Math.floor(WindowPlacement.preciseMouseX(minecraft,
                screen.width));
        int y = (int)Math.floor(WindowPlacement.preciseMouseY(minecraft,
                screen.height));
        SubWindowAnchor anchor = SubWindowAnchor.inward(x, y, x, y,
                windowId == null ? null : WindowFrame.find(windowId),
                screen.width, screen.height);
        screen.menus().show(KIND, null, WindowMenus.hangingFrom(anchor),
                true);
    }

    private static final class Source extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            MissiveBoardPage page = MissiveBoardPage.current();
            return page != null && page.showsBoard();
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(StatCollector.translateToLocal(
                    "gui.losttales.missive_board.pin.title"), null);
            MissiveBoardPage page = MissiveBoardPage.current();
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            if (page == null || player == null) {
                menu.setRows(rows);
                return;
            }
            for (Integer slot : page.carriedLetters()) {
                ItemStack stack = player.inventory.getStackInSlot(slot.intValue());
                LostTalesMissiveData missive =
                        LostTalesMissiveNbt.readFromItemStack(stack);
                String title = missive == null
                        ? StatCollector.translateToLocal(
                                "gui.losttales.missive_board.unreadable")
                        : MissiveWords.title(missive);
                rows.add(new MenuWindow.Entry(SLOT_PREFIX + slot, title)
                        .withValue(where(slot.intValue(),
                                player.inventory.currentItem)));
            }
            menu.setRows(rows);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            MissiveBoardPage page = MissiveBoardPage.current();
            if (page != null && entry.id.startsWith(SLOT_PREFIX)) {
                try {
                    page.pin(Integer.parseInt(entry.id.substring(
                            SLOT_PREFIX.length())));
                } catch (NumberFormatException unreadable) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Where a letter is: in hand, on the hotbar by its number, or in the pack. */
    private static String where(int slot, int hand) {
        if (slot == hand) {
            return StatCollector.translateToLocal(
                    "gui.losttales.missive_board.pin.hand");
        }
        return slot < HOTBAR_SLOTS ? StatCollector.translateToLocalFormatted(
                "gui.losttales.missive_board.pin.hotbar",
                Integer.valueOf(slot + 1))
                : StatCollector.translateToLocal(
                        "gui.losttales.missive_board.pin.pack");
    }
}
