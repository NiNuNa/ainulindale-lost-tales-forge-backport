package com.ninuna.losttales.character.state;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Versioned allowlisted unit of character-owned player state. */
public interface CharacterStateComponent {

    String getId();

    int getVersion();

    CharacterStateApplyPhase getApplyPhase();

    NBTTagCompound capture(EntityPlayerMP player) throws CharacterStateValidationException;

    NBTTagCompound createDefault();

    void validate(NBTTagCompound state) throws CharacterStateValidationException;

    void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException;

    void synchronize(EntityPlayerMP player);
}
