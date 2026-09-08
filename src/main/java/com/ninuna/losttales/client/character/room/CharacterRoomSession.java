package com.ninuna.losttales.client.character.room;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.CharacterTemplate;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.gui.screen.character.LostTalesCharacterCreationGui;
import com.ninuna.losttales.world.room.CharacterRoomWorldType;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiOpenEvent;

import java.util.UUID;

/**
 * Whether this client is in the character room, and what a visit there
 * goes through.
 *
 * <p>Set when the room is entered from the main menu and cleared when
 * the world is left, by whichever path. The visit opens with the creator,
 * as the account's template, on the first moment the player stands in
 * the room with no screen up: once the terrain screen has gone. Closing
 * the creator, by saving or by escaping, returns to the room itself, in
 * first person, where the player walks about, and the room menu is a key
 * away with the rest: the creator again, the play menus, or the main
 * menu. The pause menu is that menu here, so every way out of the room
 * is one that cleans up after it.</p>
 *
 * <p>The room world made its own default character from the template
 * as it stood when the world began. What the creator saves during the
 * visit is worn from then on as a preview on the player's own body, so
 * the character in the room is the one that was just saved; the world's
 * record is left alone, since the world is thrown away.</p>
 */
public final class CharacterRoomSession {

    private static boolean active;
    /** The creator has opened once this visit; it is not opened again unasked. */
    private static boolean creatorOpened;
    /** The creator was up on the last tick; the room takes the view back when it goes. */
    private static boolean creatorShowing;
    /** What the creator saved this visit, worn by the player's body while it lasts. */
    private static CharacterAppearance savedAppearance;
    private static boolean savedAppearanceWorn;

    private CharacterRoomSession() {}

    /** The room is being entered. */
    public static synchronized void begin() {
        active = true;
        creatorOpened = false;
        creatorShowing = false;
        savedAppearance = null;
        savedAppearanceWorn = false;
    }

    /** Cleared with every other client cache when the world is left. */
    public static synchronized void clear() {
        active = false;
        creatorOpened = false;
        creatorShowing = false;
        savedAppearance = null;
        savedAppearanceWorn = false;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    /** Whether this client stands in a character room right now. */
    public static boolean isInRoom(Minecraft minecraft) {
        return isActive() && minecraft != null && minecraft.theWorld != null
                && minecraft.thePlayer != null
                && CharacterRoomWorldType.isRoom(minecraft.theWorld);
    }

    /**
     * The creator saved the template. Remembered so the player's body
     * wears it for the rest of the visit; outside a visit nothing is
     * affected.
     */
    public static synchronized void onTemplateSaved(CharacterTemplate template) {
        if (!active || template == null) {
            return;
        }
        UUID account = LostTalesClientAccount.id();
        savedAppearance = account == null ? null : template.toAppearance(account);
        savedAppearanceWorn = false;
    }

    /** Called every client tick; runs the visit. */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END || !isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isInRoom(minecraft)) {
            return;
        }
        boolean screenOpen = minecraft.currentScreen != null;
        if (shouldOpenCreator(creatorOpened, screenOpen)) {
            synchronized (CharacterRoomSession.class) {
                creatorOpened = true;
            }
            openCreator(minecraft);
            return;
        }
        if (screenOpen) {
            // The creator takes the preview off when it closes; it is
            // put back the next time the room is bare.
            synchronized (CharacterRoomSession.class) {
                savedAppearanceWorn = false;
                creatorShowing = minecraft.currentScreen
                        instanceof LostTalesCharacterCreationGui;
            }
            return;
        }
        wearSavedAppearance(minecraft);
        settlePerspective(minecraft);
    }

    /**
     * Opens the creator over the room, as the account's template. Closing
     * it returns to the room.
     */
    public static void openCreator(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        minecraft.displayGuiScreen(LostTalesCharacterCreationGui.forTemplate(null));
    }

    /**
     * Opens the room menu, if the player stands in the room with nothing
     * else up. Answers whether it did, so the key it shares can fall
     * through to its ordinary meaning anywhere else.
     */
    public static boolean openMenu(Minecraft minecraft) {
        if (!isInRoom(minecraft) || minecraft.currentScreen != null) {
            return false;
        }
        minecraft.displayGuiScreen(new CharacterRoomMenuGui());
        return true;
    }

    /**
     * In the room the pause menu is the room menu: vanilla's would offer
     * a way out that leaves the room's world behind.
     */
    public static void replacePauseMenu(GuiOpenEvent event) {
        if (event == null || event.gui == null
                || event.gui.getClass() != GuiIngameMenu.class) {
            return;
        }
        if (isInRoom(Minecraft.getMinecraft())) {
            event.gui = new CharacterRoomMenuGui();
        }
    }

    /**
     * The creator opens by itself once a visit, on the first moment the
     * player stands in the room with nothing on screen: not over the
     * terrain screen while the world is still arriving.
     */
    static boolean shouldOpenCreator(boolean creatorAlreadyOpened, boolean screenOpen) {
        return !creatorAlreadyOpened && !screenOpen;
    }

    /**
     * The perspective the room is in once a screen has gone: first person
     * when the screen was the creator, which had the camera out looking at
     * the character; otherwise whatever the player had.
     */
    static int perspectiveAfterScreen(boolean creatorWasShowing, int current) {
        return creatorWasShowing ? 0 : current;
    }

    private static void wearSavedAppearance(Minecraft minecraft) {
        CharacterAppearance appearance;
        synchronized (CharacterRoomSession.class) {
            if (savedAppearance == null || savedAppearanceWorn) {
                return;
            }
            savedAppearanceWorn = true;
            appearance = savedAppearance;
        }
        UUID player = minecraft.thePlayer.getUniqueID();
        if (player != null && player.equals(appearance.getPlayerId())) {
            ClientCharacterAppearanceCache.setPreview(appearance);
        }
    }

    private static void settlePerspective(Minecraft minecraft) {
        boolean creatorWasShowing;
        synchronized (CharacterRoomSession.class) {
            creatorWasShowing = creatorShowing;
            creatorShowing = false;
        }
        if (creatorWasShowing && minecraft.gameSettings != null) {
            minecraft.gameSettings.thirdPersonView = perspectiveAfterScreen(true,
                    minecraft.gameSettings.thirdPersonView);
        }
    }
}
