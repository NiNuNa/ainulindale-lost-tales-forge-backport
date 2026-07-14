package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrCustomWaypointStateAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;
import java.util.UUID;

/** Owned LOTR custom waypoints and their use counters for one character. */
public final class LotrCustomWaypointStateComponent
        implements CharacterStateComponent {

    public static final String ID = "lotr_custom_waypoints";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_WAYPOINTS = "Waypoints";

    private final LotrCustomWaypointStateAdapter adapter =
            new LotrCustomWaypointStateAdapter();

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
                    "LOTR custom waypoints could not be captured", exception);
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
                    "LOTR custom-waypoint component version is missing");
        }
        Set<?> keys = state.func_150296_c();
        if (state.getInteger(TAG_VERSION) != VERSION
                || keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_WAYPOINTS)
                || !state.hasKey(TAG_WAYPOINTS, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR custom-waypoint component is unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_WAYPOINTS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR custom-waypoint state is malformed", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_WAYPOINTS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR custom waypoints could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        this.adapter.synchronize(player);
    }

    public void clearRuntimeState(UUID ownerId) {
        this.adapter.clearRuntimeState(ownerId);
    }

    public void clearAllRuntimeState() {
        this.adapter.clearAllRuntimeState();
    }

    private static NBTTagCompound wrap(NBTTagCompound waypoints) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_WAYPOINTS, waypoints == null
                ? new NBTTagCompound() : waypoints.copy());
        return state;
    }
}
