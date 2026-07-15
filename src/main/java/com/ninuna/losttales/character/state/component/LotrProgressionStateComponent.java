package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrProgressionStateAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** Alignment, faction counters, pledges, LOTR achievements, and title. */
public final class LotrProgressionStateComponent implements CharacterStateComponent {

    public static final String ID = "lotr_progression";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_PROGRESSION = "Progression";

    private final LotrProgressionStateAdapter adapter =
            new LotrProgressionStateAdapter();

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
                    "LOTR progression could not be captured", exception);
        }
    }

    @Override
    public NBTTagCompound createDefault() {
        return wrap(this.adapter.createDefault());
    }

    /** Creates a clean character state seeded with the selected faction. */
    public NBTTagCompound createDefault(String startingFactionId) {
        return wrap(this.adapter.createDefault(startingFactionId));
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null
                || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "LOTR progression component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported LOTR progression component version " + version);
        }
        Set<?> keys = state.func_150296_c();
        if (keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_PROGRESSION)
                || !state.hasKey(TAG_PROGRESSION, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR progression component is incomplete or unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_PROGRESSION));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR progression is malformed or incompatible", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_PROGRESSION));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR progression could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        this.adapter.synchronize(player);
    }

    private static NBTTagCompound wrap(NBTTagCompound progression) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_PROGRESSION, progression == null
                ? new NBTTagCompound() : progression.copy());
        return state;
    }
}
