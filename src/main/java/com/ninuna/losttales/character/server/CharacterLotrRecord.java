package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Keeps what LOTR says of the played character on its record: its pledge
 * and its title, so a character's faction answers and its title shows
 * while it is not played too. Both are read from the player data of the
 * character being played: at login, around a switch, at the periodic
 * checkpoint and as the player leaves.
 *
 * <p>The roster revision stays as it is. The pledge and the title are
 * LOTR's to change, not a player's edit, and a request made against the
 * roster just before is still good.</p>
 */
public final class CharacterLotrRecord {

    private CharacterLotrRecord() {}

    /**
     * Reads the pledge and the title of the character the player plays
     * onto its record. Answers whether the record changed.
     */
    public static boolean refresh(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return false;
        }
        try {
            CharacterWorldData data = CharacterStorage.get(player.worldObj);
            if (data.isReadOnlyForNewerVersion()) {
                return false;
            }
            CharacterRoster roster = data.getRoster(player.getUniqueID());
            return roster != null && refresh(player, data, roster,
                    roster.getActiveCharacter());
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] The LOTR pledge and title of %s's character could not be kept: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(),
                    exception.toString());
            return false;
        }
    }

    /**
     * Reads the player's live pledge and title onto {@code character},
     * which must be the one whose LOTR data the player holds right now.
     * What LOTR cannot say is left as the record had it. Answers whether
     * the record changed; it never throws, so a switch can call it after
     * its commit.
     */
    public static boolean refresh(EntityPlayerMP player, CharacterWorldData data,
                                  CharacterRoster roster,
                                  RoleplayCharacter character) {
        if (character == null || data == null || roster == null
                || data.isReadOnlyForNewerVersion()) {
            return false;
        }
        try {
            LotrCharacterAdapter lotr = LotrCharacterAdapter.getInstance();
            String pledged = lotr.getPledgedFactionId(player);
            String title = lotr.getTitleName(player);
            boolean changed = pledged != null && character.setPledgedFactionId(
                    pledged, System.currentTimeMillis());
            changed |= title != null && character.setLotrTitle(title);
            if (!changed) {
                return false;
            }
            data.saveRoster(roster);
            return true;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] The LOTR pledge and title of character %s could not be kept: %s",
                    LostTalesMetaData.MOD_ID, character.getCharacterId(),
                    exception.toString());
            return false;
        }
    }

    /**
     * Refreshes the record of the character played and, when it changed,
     * sends the owner the roster and the chat's access, and everyone the
     * player's appearance, so the Faction Chat, the colour and the name
     * follow at once.
     */
    public static void refreshAndSync(EntityPlayerMP player) {
        if (!refresh(player)) {
            return;
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            if (roster != null) {
                CharacterSyncManager.sendRoster(player,
                        CharacterSyncManager.UNSOLICITED_REQUEST_ID, roster);
                CharacterAppearanceSyncManager.broadcastPlayer(player, roster);
                LostTalesChatService.sendAccess(player);
            }
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] A changed LOTR record of %s's character could not be sent: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(),
                    exception.toString());
        }
    }
}
