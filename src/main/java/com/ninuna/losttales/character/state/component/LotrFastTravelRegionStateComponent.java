package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrFastTravelRegionStateAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** LOTR biome/waypoint regions visited by an individual character. */
public final class LotrFastTravelRegionStateComponent
        implements CharacterStateComponent {

    public static final String ID = "lotr_fast_travel_regions";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_REGIONS = "Regions";

    private final LotrFastTravelRegionStateAdapter adapter =
            new LotrFastTravelRegionStateAdapter();

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
                    "LOTR fast-travel regions could not be captured", exception);
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
                    "LOTR fast-travel region component version is missing");
        }
        Set<?> keys = state.func_150296_c();
        if (state.getInteger(TAG_VERSION) != VERSION
                || keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_REGIONS)
                || !state.hasKey(TAG_REGIONS, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR fast-travel region component is unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_REGIONS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR fast-travel region state is malformed", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_REGIONS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR fast-travel regions could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // LotrProgressionStateComponent performs one full LOTR sync after all
        // LOTR components have applied, including the region packet data.
    }

    private static NBTTagCompound wrap(NBTTagCompound regions) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_REGIONS, regions == null
                ? new NBTTagCompound() : regions.copy());
        return state;
    }
}
