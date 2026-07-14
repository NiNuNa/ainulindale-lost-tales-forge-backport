package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.util.Constants;

import java.util.Collection;

/** Active vanilla potion effects, applied before final health restoration. */
public final class VanillaPotionStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_potions";
    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_EFFECTS = "Effects";
    private static final int MAX_EFFECTS = 64;

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
        if (player == null) {
            throw new CharacterStateValidationException("Player is unavailable");
        }
        NBTTagList effects = new NBTTagList();
        Collection active = player.getActivePotionEffects();
        if (active != null) {
            for (Object value : active) {
                if (!(value instanceof PotionEffect)) {
                    continue;
                }
                NBTTagCompound effectTag = new NBTTagCompound();
                ((PotionEffect) value).writeCustomPotionEffectToNBT(effectTag);
                effects.appendTag(effectTag);
            }
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_EFFECTS, effects);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_EFFECTS, new NBTTagList());
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        requireVersion(state);
        if (!state.hasKey(TAG_EFFECTS, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException("Potion effect list is missing");
        }
        NBTTagList effects = state.getTagList(TAG_EFFECTS, Constants.NBT.TAG_COMPOUND);
        if (effects.tagCount() > 0
                && effects.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Potion effect list has an invalid element type");
        }
        if (effects.tagCount() > MAX_EFFECTS) {
            throw new CharacterStateValidationException(
                    "Too many active potion effects: " + effects.tagCount());
        }
        boolean[] seen = new boolean[256];
        for (int index = 0; index < effects.tagCount(); index++) {
            PotionEffect effect;
            try {
                effect = PotionEffect.readCustomPotionEffectFromNBT(
                        effects.getCompoundTagAt(index));
            } catch (RuntimeException exception) {
                throw new CharacterStateValidationException(
                        "Potion effect " + index + " could not be decoded", exception);
            }
            if (effect == null || effect.getPotionID() < 0
                    || effect.getPotionID() >= seen.length
                    || Potion.potionTypes[effect.getPotionID()] == null
                    || effect.getDuration() <= 0
                    || effect.getAmplifier() < 0
                    || effect.getAmplifier() > 255) {
                throw new CharacterStateValidationException(
                        "Potion effect " + index + " is invalid");
            }
            if (seen[effect.getPotionID()]) {
                throw new CharacterStateValidationException(
                        "Duplicate potion effect " + effect.getPotionID());
            }
            seen[effect.getPotionID()] = true;
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        if (player == null) {
            throw new CharacterStateValidationException("Player is unavailable");
        }
        validate(state);
        player.clearActivePotions();
        NBTTagList effects = state.getTagList(TAG_EFFECTS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < effects.tagCount(); index++) {
            PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(
                    effects.getCompoundTagAt(index));
            if (effect == null) {
                throw new CharacterStateValidationException(
                        "Potion effect " + index + " became invalid during apply");
            }
            player.addPotionEffect(new PotionEffect(effect));
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // EntityPlayerMP add/remove potion hooks send the authoritative packets.
    }

    private static void requireVersion(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException("Potion component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported potion component version " + version);
        }
    }
}
