package com.ninuna.losttales.compat.lotr.structure;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.LOTRLevelData;
import lotr.common.item.LOTRItemStructureSpawner;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Who may spawn LOTR's structures with its structure spawners. LOTR
 * itself refuses everyone while its server-wide switch is on; the mod's
 * own bans ({@link LotrStructureBanWorldData}) refuse an account, as every
 * character it plays, or one character alone. A refused use answers in
 * LOTR's own words. LOTR's per-player flag is never written: lifting a ban
 * on an account must not leave any of its characters banned.
 */
public final class LotrStructureBans {
    /** LOTR's own line for a banned player's try. */
    private static final String BANNED_LINE = "chat.lotr.spawnStructure.banned";

    private static volatile boolean failureLogged;

    /** Whether LOTR's server-wide switch bans everyone. */
    public static boolean bannedForEveryone() {
        return LOTRLevelData.structuresBanned();
    }

    /** Turns LOTR's server-wide switch on or off. */
    public static void setBannedForEveryone(boolean banned) {
        LOTRLevelData.setStructuresBanned(banned);
    }

    /** Whether the player, as the character they play, is banned by the mod's own bans. */
    static boolean isBanned(EntityPlayer player) {
        UUID account = player.getUniqueID();
        UUID character = null;
        CharacterRoster roster = CharacterStorage.get(player.worldObj).getRoster(account);
        if (roster != null) {
            character = roster.getActiveCharacterId();
        }
        return LotrStructureBanStorage.get(player.worldObj).isBanned(account, character);
    }

    /** A structure spawner used on a block by someone banned does nothing. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || event.entityPlayer == null || event.entityPlayer.worldObj == null
                || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        ItemStack held = event.entityPlayer.getCurrentEquippedItem();
        if (held == null) {
            return;
        }
        try {
            // LOTR's own switch speaks for itself when it is on.
            if (!(held.getItem() instanceof LOTRItemStructureSpawner)
                    || bannedForEveryone() || !isBanned(event.entityPlayer)) {
                return;
            }
            event.setCanceled(true);
            event.entityPlayer.addChatMessage(new ChatComponentTranslation(BANNED_LINE));
        } catch (LinkageError incompatible) {
            logOnce(incompatible);
        } catch (RuntimeException unreadable) {
            logOnce(unreadable);
        }
    }

    private static void logOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[%s] Structure bans could not be checked and stand open: %s",
                LostTalesMetaData.MOD_ID, failure.toString());
    }
}
