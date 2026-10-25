package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.sync.CharacterGear;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterGearPacket;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The sending of what one of a player's own characters wears in its
 * saved life, for the stage (CHAR-42): its armour and the stack in its
 * hand, read from the character's last saved inventory and never
 * written. Only the owner is told, and only of a character of their
 * roster other than the one they play; everything else is answered with
 * nothing, so the stage stands the character there bare.
 */
public final class CharacterGearViews {

    private CharacterGearViews() {}

    /** Answers {@code owner} with what the character wears, or with nothing. */
    public static void send(EntityPlayerMP owner, UUID characterId) {
        if (owner == null || characterId == null) {
            return;
        }
        CharacterGearPacket answer;
        try {
            answer = CharacterGearPacket.of(characterId,
                    savedGear(owner, characterId));
        } catch (IllegalArgumentException unsendable) {
            // The wire checks a stack as reading the inventory does, so
            // this stays out of reach; the character would stand bare.
            answer = CharacterGearPacket.of(characterId, CharacterGear.NONE);
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(answer, owner);
    }

    /**
     * The character's gear from its saved life; {@link CharacterGear#NONE}
     * unless it stands in the owner's roster, is not the one played, and
     * its saved life reads.
     */
    static CharacterGear savedGear(EntityPlayerMP owner, UUID characterId) {
        World world = owner.worldObj;
        UUID ownerId = owner.getUniqueID();
        if (world == null || world.isRemote || ownerId == null) {
            return CharacterGear.NONE;
        }
        CharacterRoster roster = CharacterStorage.get(world).getRoster(ownerId);
        if (!isOwnAndAside(roster, characterId)) {
            return CharacterGear.NONE;
        }
        CharacterPlayerStateWorldData states =
                CharacterPlayerStateStorage.find(world, ownerId);
        if (states == null || states.isReadOnlyForNewerVersion()
                || states.isOwnerBlocked(ownerId)) {
            return CharacterGear.NONE;
        }
        CharacterPlayerStateAccount account = states.getAccount(ownerId);
        CharacterPlayerStateRecord record = account == null ? null
                : account.getRecord(characterId);
        if (record == null || record.getCurrent() == null) {
            return CharacterGear.NONE;
        }
        return gearOf(characterId, record.getCurrent()
                .getComponent(VanillaInventoryStateComponent.ID));
    }

    /** Whether the character stands in the roster and is not the one it plays. */
    static boolean isOwnAndAside(CharacterRoster roster, UUID characterId) {
        return roster != null && characterId != null
                && roster.getCharacter(characterId) != null
                && !characterId.equals(roster.getActiveCharacterId());
    }

    /** The gear a saved inventory holds; nothing where the inventory does not read. */
    static CharacterGear gearOf(UUID characterId, NBTTagCompound inventory) {
        if (inventory == null) {
            return CharacterGear.NONE;
        }
        try {
            return CharacterGear.of(VanillaInventoryStateComponent.wornOf(inventory));
        } catch (CharacterStateValidationException unreadable) {
            // Playing the character refuses the same inventory, with its
            // own report; the stage stands it there bare meanwhile.
            LostTalesLog.warning("The saved inventory of character %s "
                    + "could not be read for the stage: %s", characterId,
                    unreadable.getMessage());
            return CharacterGear.NONE;
        }
    }
}
