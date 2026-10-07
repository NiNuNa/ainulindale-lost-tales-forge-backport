package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

/**
 * On a first visit the player waits in the character creator, held as a
 * ghost, until their first character is made. Whenever no other screen is
 * open while they wait, the creator stands on screen, the same one each
 * time, so what they chose before a look at the game menu or the chat is
 * still chosen. It goes as their roster plays a character.
 */
@SideOnly(Side.CLIENT)
public final class FirstCharacterCreator {
    /** The creator a first visit waits in, kept for the visit. */
    private static LostTalesCharacterCreationGui creator;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null || !roster.isWaitingForFirstCharacter()) {
            creator = null;
            return;
        }
        if (minecraft.currentScreen == null) {
            if (creator == null) {
                creator = LostTalesCharacterCreationGui.forFirstCharacter();
            }
            minecraft.displayGuiScreen(creator);
        }
    }

    /** Leaving the world ends the visit; the next begins afresh. */
    public static void clear() {
        creator = null;
    }
}
