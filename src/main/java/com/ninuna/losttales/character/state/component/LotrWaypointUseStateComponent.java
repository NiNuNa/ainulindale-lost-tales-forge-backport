package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrWaypointUseStateAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** Native LOTR waypoint use counts that control per-destination travel time. */
public final class LotrWaypointUseStateComponent
        implements CharacterStateComponent {

    public static final String ID = "lotr_waypoint_uses";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_USES = "Uses";

    private final LotrWaypointUseStateAdapter adapter =
            new LotrWaypointUseStateAdapter();

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
                    "LOTR waypoint uses could not be captured", exception);
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
                    "LOTR waypoint-use component version is missing");
        }
        Set<?> keys = state.func_150296_c();
        if (state.getInteger(TAG_VERSION) != VERSION
                || keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_USES)
                || !state.hasKey(TAG_USES, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR waypoint-use component is unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_USES));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR waypoint-use state is malformed", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_USES));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR waypoint uses could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // LotrProgressionStateComponent performs one full LOTR sync after all
        // navigation and progression components have applied.
    }

    private static NBTTagCompound wrap(NBTTagCompound uses) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_USES, uses == null
                ? new NBTTagCompound() : uses.copy());
        return state;
    }
}
