package com.ninuna.losttales.client.character.room;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.world.room.CharacterRoomWorldType;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveFormat;

/**
 * Takes the player from the main menu into the character room and out
 * of it again, to the main menu or straight on to one of the play menus.
 *
 * <p>The room is a single-player world made fresh every time: one save
 * folder of its own, deleted before it is made and again once it is
 * left, so nothing is carried from one visit to the next and the saves
 * list never keeps it. A fresh world makes the account's default
 * character and takes the account's template for it, exactly as any
 * other new world does, which is what stands the player in the room
 * already looking like their default character.</p>
 *
 * <p>Adventure mode, so the player is placed exactly at the layout's
 * spawn and cannot dig; the room's own handler guards the rest.</p>
 */
public final class CharacterRoomLauncher {

    /** Where a visit ends. */
    public enum Destination {
        MAIN_MENU, SINGLEPLAYER, MULTIPLAYER
    }

    /** The save folder; never listed for long, since it is deleted on leaving. */
    static final String FOLDER = "losttales_character_room";
    /** What the level file calls the world. */
    static final String WORLD_NAME = "Character Room";
    /** The room is the same whatever the seed; a fixed one keeps it so. */
    static final long SEED = 0x1057A1E5L;

    /** The perspective the player had before the room turned the view; -1 for none. */
    private static int perspectiveOnEntry = -1;

    private CharacterRoomLauncher() {}

    /**
     * Enters the room. False, with nothing changed, when there is no
     * type to build it from or a world is already open; false after
     * logging when the world could not be started, so the caller can
     * fall back to a creator that needs no world.
     */
    public static boolean enter(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld != null) {
            return false;
        }
        CharacterRoomWorldType type = CharacterRoomWorldType.get();
        if (type == null) {
            return false;
        }
        deleteSave(minecraft);
        WorldSettings settings = new WorldSettings(SEED,
                WorldSettings.GameType.ADVENTURE, false, false, type);
        perspectiveOnEntry = minecraft.gameSettings == null
                ? -1 : minecraft.gameSettings.thirdPersonView;
        CharacterRoomSession.begin();
        try {
            minecraft.launchIntegratedServer(FOLDER, WORLD_NAME, settings);
        } catch (RuntimeException exception) {
            CharacterRoomSession.clear();
            FMLLog.warning("[%s] The character room could not be opened: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return false;
        }
        return true;
    }

    /**
     * Leaves the room the way the pause menu's quit does, removes the
     * world once its server has stopped, gives the view back, and shows
     * the destination: the main menu, or one of the play menus with the
     * main menu behind it.
     */
    public static void leave(Minecraft minecraft, Destination destination) {
        if (minecraft == null) {
            return;
        }
        if (minecraft.theWorld != null) {
            minecraft.theWorld.sendQuittingDisconnectingPacket();
        }
        minecraft.loadWorld((WorldClient) null);
        deleteSave(minecraft);
        if (perspectiveOnEntry >= 0 && minecraft.gameSettings != null) {
            minecraft.gameSettings.thirdPersonView = perspectiveOnEntry;
        }
        perspectiveOnEntry = -1;
        CharacterRoomSession.clear();
        minecraft.displayGuiScreen(screenFor(destination));
    }

    private static GuiScreen screenFor(Destination destination) {
        GuiMainMenu menu = new GuiMainMenu();
        if (destination == Destination.SINGLEPLAYER) {
            return new GuiSelectWorld(menu);
        }
        if (destination == Destination.MULTIPLAYER) {
            return new GuiMultiplayer(menu);
        }
        return menu;
    }

    private static void deleteSave(Minecraft minecraft) {
        ISaveFormat saves = minecraft.getSaveLoader();
        if (saves == null) {
            return;
        }
        try {
            if (!saves.deleteWorldDirectory(FOLDER)) {
                FMLLog.warning("[%s] The character room's save could not be removed",
                        LostTalesMetaData.MOD_ID);
            }
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] The character room's save could not be removed: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }
    }
}
