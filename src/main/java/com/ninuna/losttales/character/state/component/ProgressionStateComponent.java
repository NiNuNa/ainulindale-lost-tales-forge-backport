package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.ProgressionRules;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.ProgressionStateNbt;
import com.ninuna.losttales.progression.ProgressionSync;
import com.ninuna.losttales.progression.count.CountWatch;
import com.ninuna.losttales.progression.deed.DeedEvents;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * The character's progression (CHAR-28): skills, character level, perks,
 * deeds, title and resets. A new character starts with its race's
 * starting skills (PROG-05), which give no character level.
 */
public final class ProgressionStateComponent implements CharacterStateComponent {

    public static final String ID = "losttales_progression";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_PROGRESSION = "Progression";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        ProgressionPlayerData data = requireData(player);
        if (data.isReadOnly()) {
            throw new CharacterStateValidationException(
                    "Progression data is read-only or unsupported");
        }
        NBTTagCompound state = wrap(data.writeCharacterState());
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        return wrap(ProgressionStateNbt.write(new ProgressionState()));
    }

    /** A new character's state: its race's starting skills (PROG-05). */
    public NBTTagCompound createDefault(Map<String, Integer> startingSkills) {
        return wrap(ProgressionStateNbt.write(
                ProgressionRules.startingState(startingSkills)));
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || state.getInteger(TAG_VERSION) != VERSION) {
            throw new CharacterStateValidationException(
                    "Progression component version is missing or unsupported");
        }
        Set<?> keys = state.func_150296_c();
        if (keys.size() != 2 || !state.hasKey(TAG_PROGRESSION, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "Progression component is incomplete or holds unsupported fields");
        }
        try {
            ProgressionStateNbt.read(state.getCompoundTag(TAG_PROGRESSION));
        } catch (IllegalArgumentException malformed) {
            throw new CharacterStateValidationException(
                    "Progression state is malformed or unsupported", malformed);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        ProgressionPlayerData data = requireData(player);
        try {
            data.replaceCharacterState(state.getCompoundTag(TAG_PROGRESSION));
            // What the counting watched belonged to the character before.
            CountWatch.forget(player.getUniqueID());
            DeedEvents.checkSoon(player.getUniqueID());
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "Progression state could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        ProgressionSync.sendState(player);
    }

    private static ProgressionPlayerData requireData(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Progression state requires a connected server player");
        }
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null) {
            throw new CharacterStateValidationException(
                    "Progression data is unavailable");
        }
        return data;
    }

    private static NBTTagCompound wrap(NBTTagCompound progression) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_PROGRESSION, progression.copy());
        return state;
    }
}
