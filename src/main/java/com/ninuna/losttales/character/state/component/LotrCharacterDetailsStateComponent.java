package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrCharacterDetailsStateAdapter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Set;

/** LOTR shield, alcohol tolerance, and last-death marker. */
public final class LotrCharacterDetailsStateComponent
        implements CharacterStateComponent {

    public static final String ID = "lotr_character_details";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_DETAILS = "Details";

    private final LotrCharacterDetailsStateAdapter adapter =
            new LotrCharacterDetailsStateAdapter();

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
                    "LOTR character details could not be captured", exception);
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
                    "LOTR character-details component version is missing");
        }
        Set<?> keys = state.func_150296_c();
        if (state.getInteger(TAG_VERSION) != VERSION
                || keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_DETAILS)
                || !state.hasKey(TAG_DETAILS, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "LOTR character-details component is unsupported");
        }
        try {
            this.adapter.validate(state.getCompoundTag(TAG_DETAILS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR character details are malformed", exception);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        try {
            this.adapter.apply(player, state.getCompoundTag(TAG_DETAILS));
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "LOTR character details could not be applied", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        try {
            this.adapter.synchronize(player);
        } catch (RuntimeException exception) {
            // The durable target state remains authoritative; a dimension or
            // login synchronization will retry the public shield broadcast.
            FMLLog.warning("[%s] Unable to synchronize LOTR character details for %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player == null ? "unknown" : player.getUniqueID(),
                    exception.toString());
        }
    }

    private static NBTTagCompound wrap(NBTTagCompound details) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_DETAILS, details == null
                ? new NBTTagCompound() : details.copy());
        return state;
    }
}
