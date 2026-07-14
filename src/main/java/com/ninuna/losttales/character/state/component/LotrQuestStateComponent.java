package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrQuestStateAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** Active/completed LOTR miniquests, bounties, tracking, and quest data. */
public final class LotrQuestStateComponent implements CharacterStateComponent {

    public static final String ID = "lotr_quests";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_QUESTS = "Quests";

    private final LotrQuestStateAdapter adapter = new LotrQuestStateAdapter();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        try {
            NBTTagCompound state = wrap(this.adapter.capture(player));
            validate(state);
            return state;
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR quest state could not be captured", exception);
        }
    }

    @Override
    public NBTTagCompound createDefault() {
        return wrap(this.adapter.createDefault());
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null
                || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "LOTR quest component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported LOTR quest component version " + version);
        }
        Set<?> keys = state.func_150296_c();
        if (keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_QUESTS)
                || !state.hasKey(TAG_QUESTS, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR quest component is incomplete or unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_QUESTS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR quest state is malformed or incompatible", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_QUESTS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR quest state could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // LotrProgressionStateComponent performs the one full LOTR sync after
        // every component has been applied; that sync includes miniquest packets.
    }

    private static NBTTagCompound wrap(NBTTagCompound quests) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_QUESTS, quests == null
                ? new NBTTagCompound() : quests.copy());
        return state;
    }
}
