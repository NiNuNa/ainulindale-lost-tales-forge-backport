package com.ninuna.losttales.character.state;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * An allowlisted unit of character-owned player state. Each component
 * versions its own compound and checks that version in
 * {@link #validate}, so a snapshot from another build is refused where
 * it is read rather than compared against a number kept apart from it.
 */
public interface CharacterStateComponent {

    String getId();

    CharacterStateApplyPhase getApplyPhase();

    NBTTagCompound capture(EntityPlayerMP player) throws CharacterStateValidationException;

    NBTTagCompound createDefault();

    void validate(NBTTagCompound state) throws CharacterStateValidationException;

    void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException;

    void synchronize(EntityPlayerMP player);
}
