package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateNbtUtil;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraftforge.common.util.Constants;

/** Health, absorption, food, exhaustion, and vanilla experience. */
public final class VanillaVitalsStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_vitals";
    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_HEALTH = "Health";
    private static final String TAG_FULL_HEALTH = "FullHealth";
    private static final String TAG_ABSORPTION = "Absorption";
    private static final String TAG_FOOD = "Food";
    private static final String TAG_EXPERIENCE = "Experience";
    private static final String TAG_EXPERIENCE_LEVEL = "ExperienceLevel";
    private static final String TAG_EXPERIENCE_TOTAL = "ExperienceTotal";
    private static final float MAX_STORED_HEALTH = 65536.0F;
    private static final float MAX_STORED_ABSORPTION = 65536.0F;

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
        return CharacterStateApplyPhase.AFTER_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.getFoodStats() == null) {
            throw new CharacterStateValidationException("Player vitals are unavailable");
        }
        NBTTagCompound food = new NBTTagCompound();
        player.getFoodStats().writeNBT(food);
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setFloat(TAG_HEALTH, player.getHealth());
        state.setBoolean(TAG_FULL_HEALTH, false);
        state.setFloat(TAG_ABSORPTION, player.getAbsorptionAmount());
        state.setTag(TAG_FOOD, food);
        state.setFloat(TAG_EXPERIENCE, player.experience);
        state.setInteger(TAG_EXPERIENCE_LEVEL, player.experienceLevel);
        state.setInteger(TAG_EXPERIENCE_TOTAL, player.experienceTotal);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", 20);
        food.setInteger("foodTickTimer", 0);
        food.setFloat("foodSaturationLevel", 5.0F);
        food.setFloat("foodExhaustionLevel", 0.0F);

        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setFloat(TAG_HEALTH, 20.0F);
        state.setBoolean(TAG_FULL_HEALTH, true);
        state.setFloat(TAG_ABSORPTION, 0.0F);
        state.setTag(TAG_FOOD, food);
        state.setFloat(TAG_EXPERIENCE, 0.0F);
        state.setInteger(TAG_EXPERIENCE_LEVEL, 0);
        state.setInteger(TAG_EXPERIENCE_TOTAL, 0);
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        requireVersion(state);
        float health = state.getFloat(TAG_HEALTH);
        float absorption = state.getFloat(TAG_ABSORPTION);
        float experience = state.getFloat(TAG_EXPERIENCE);
        if (!CharacterStateNbtUtil.isFinite(health)
                || health < 0.0F || health > MAX_STORED_HEALTH) {
            throw new CharacterStateValidationException("Stored health is invalid");
        }
        if (!CharacterStateNbtUtil.isFinite(absorption)
                || absorption < 0.0F || absorption > MAX_STORED_ABSORPTION) {
            throw new CharacterStateValidationException("Stored absorption is invalid");
        }
        if (!CharacterStateNbtUtil.isFinite(experience)
                || experience < 0.0F || experience > 1.0F) {
            throw new CharacterStateValidationException("Stored experience progress is invalid");
        }
        if (state.getInteger(TAG_EXPERIENCE_LEVEL) < 0
                || state.getInteger(TAG_EXPERIENCE_TOTAL) < 0) {
            throw new CharacterStateValidationException("Stored experience totals are invalid");
        }
        if (!state.hasKey(TAG_FOOD, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException("Food state is missing");
        }
        NBTTagCompound food = state.getCompoundTag(TAG_FOOD);
        if (!food.hasKey("foodLevel", Constants.NBT.TAG_INT)
                || !food.hasKey("foodTickTimer", Constants.NBT.TAG_INT)
                || !food.hasKey("foodSaturationLevel", Constants.NBT.TAG_FLOAT)
                || !food.hasKey("foodExhaustionLevel", Constants.NBT.TAG_FLOAT)) {
            throw new CharacterStateValidationException(
                    "Food state is incomplete or has invalid field types");
        }
        int foodLevel = food.getInteger("foodLevel");
        int foodTickTimer = food.getInteger("foodTickTimer");
        float saturation = food.getFloat("foodSaturationLevel");
        float exhaustion = food.getFloat("foodExhaustionLevel");
        if (foodLevel < 0 || foodLevel > 20
                || foodTickTimer < 0 || foodTickTimer > 1000
                || !CharacterStateNbtUtil.isFinite(saturation)
                || saturation < 0.0F || saturation > 20.0F
                || !CharacterStateNbtUtil.isFinite(exhaustion)
                || exhaustion < 0.0F || exhaustion > 40.0F) {
            throw new CharacterStateValidationException("Food state is invalid");
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        if (player == null || player.getFoodStats() == null) {
            throw new CharacterStateValidationException("Player vitals are unavailable");
        }
        validate(state);
        player.getFoodStats().readNBT((NBTTagCompound) state.getCompoundTag(TAG_FOOD).copy());
        player.experience = state.getFloat(TAG_EXPERIENCE);
        player.experienceLevel = state.getInteger(TAG_EXPERIENCE_LEVEL);
        player.experienceTotal = state.getInteger(TAG_EXPERIENCE_TOTAL);
        player.setAbsorptionAmount(state.getFloat(TAG_ABSORPTION));

        float maximum = getMaximumHealth(player);
        float requested = state.getBoolean(TAG_FULL_HEALTH)
                ? maximum : state.getFloat(TAG_HEALTH);
        // Character switching is forbidden while dead. A corrupt zero-health
        // target must not force an implicit death during an otherwise valid switch.
        player.setHealth(CharacterStateNbtUtil.clamp(requested, 0.0001F, maximum));
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        player.playerNetServerHandler.sendPacket(new S06PacketUpdateHealth(
                player.getHealth(),
                player.getFoodStats().getFoodLevel(),
                player.getFoodStats().getSaturationLevel()));
        player.playerNetServerHandler.sendPacket(new S1FPacketSetExperience(
                player.experience, player.experienceTotal, player.experienceLevel));
    }

    private static float getMaximumHealth(EntityPlayerMP player) {
        IAttributeInstance attribute = player.getEntityAttribute(
                SharedMonsterAttributes.maxHealth);
        double value = attribute == null ? 20.0D : attribute.getAttributeValue();
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
            return 20.0F;
        }
        return (float) Math.min(MAX_STORED_HEALTH, value);
    }

    private static void requireVersion(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException("Vitals component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported vitals component version " + version);
        }
    }
}
