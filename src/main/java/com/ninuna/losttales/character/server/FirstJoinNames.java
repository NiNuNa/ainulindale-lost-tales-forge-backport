package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * An account joining this world for the first time under a name one of
 * the characters already holds still joins, and the Server Log names the
 * account, the character and the character's owner, for staff to settle
 * (CHAR-11). The player's saved data remembers that the first join was
 * looked at, so it is looked at once.
 */
public final class FirstJoinNames {
    /** Where the player's saved data remembers the first join. */
    static final String CHECKED_TAG = "LostTalesFirstJoinNamed";

    @SubscribeEvent
    public void onLogin(PlayerLoggedInEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)
                || !LostTalesServerPlayers.isServerPlayer((EntityPlayerMP)event.player)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        NBTTagCompound saved = LostTalesServerPlayers.persistedData(player);
        if (saved.getBoolean(CHECKED_TAG)) {
            return;
        }
        String account = SeenAccountNames.accountNameOf(player);
        Collection<CharacterRoster> rosters;
        try {
            rosters = CharacterStorage.get(player.worldObj).getRosters();
        } catch (RuntimeException unreadable) {
            // Looked at again at the next join, when the store may read.
            FMLLog.warning("[%s] Could not read the characters to compare %s's name"
                            + " with on a first join: %s", LostTalesMetaData.MOD_ID,
                    account, unreadable.toString());
            return;
        }
        saved.setBoolean(CHECKED_TAG, true);
        RoleplayCharacter holder = holderOf(rosters, player.getUniqueID(), account);
        if (holder == null) {
            return;
        }
        String owner = KnownAccounts.nameOf(holder.getOwnerId());
        LostTalesChatService.console(ChatConsoleEvent.Kind.WARNING,
                ChatConsoleEvent.Severity.WARNING, "",
                line(account, holder.getName(),
                        owner.length() == 0 ? holder.getOwnerId().toString() : owner));
    }

    /**
     * The character of another account that goes by {@code account}'s
     * name, as names are compared; null for none.
     */
    static RoleplayCharacter holderOf(Collection<CharacterRoster> rosters, UUID accountId,
                                      String account) {
        RoleplayCharacter holder = CharacterNames.named(rosters, account);
        return holder == null || holder.getOwnerId().equals(accountId) ? null : holder;
    }

    /** The Server Log's line: in English, as staff read the log. */
    static String line(String account, String character, String owner) {
        return account + " joined for the first time under the name of the character "
                + character + ", whose account is " + owner;
    }
}
