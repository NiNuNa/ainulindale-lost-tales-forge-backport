package com.ninuna.losttales.network.server;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gameplay.projectile.ChargeTierCalculator;
import com.ninuna.losttales.gameplay.projectile.ThirdPersonChargeItemPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lotr.common.entity.projectile.LOTREntityProjectileBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Server authority for charge-tier tracking and projectile bonuses. */
public final class LostTalesChargeService {
    private static final String CHARGE_TIER_TAG =
            "LostTalesChargeTier";
    private static final long RELEASE_STATE_MAXIMUM_AGE_TICKS = 2L;
    private static final double FEEDBACK_RADIUS = 48.0D;
    private static final Map<UUID, ChargeState> STATES =
            new HashMap<UUID, ChargeState>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        updatePlayer((EntityPlayerMP)event.player);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event != null && event.player != null) {
            clearPlayer(event.player.getUniqueID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event == null || event.isCanceled()
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || event.source == null) {
            return;
        }
        Entity projectile = event.source.getSourceOfDamage();
        int tier = getAppliedTier(projectile);
        if (tier <= 0) {
            return;
        }
        event.ammount *= (float)damageMultiplier(tier);
        applyBonusKnockback(
                event.entityLiving, projectile, knockbackBonus(tier));
    }

    public static synchronized boolean applyCharge(
            Entity projectile, EntityPlayerMP shooter) {
        if (!LostTalesConfig.enableChargeTiers
                || projectile == null || shooter == null
                || shooter.getUniqueID() == null
                || projectile.worldObj == null
                || projectile.worldObj != shooter.worldObj
                || projectile.ticksExisted != 0
                || projectile.getDistanceSqToEntity(shooter) > 16.0D) {
            return false;
        }
        ChargeState state = STATES.get(shooter.getUniqueID());
        int tier = resolveCurrentTier(shooter);
        if (isRecentState(state, shooter) && state.tier > tier) {
            tier = state.tier;
        }
        if (tier <= 0) {
            finishChargeFeedback(shooter, state, 0);
            return false;
        }

        double velocityMultiplier = velocityMultiplier(tier);
        projectile.motionX *= velocityMultiplier;
        projectile.motionY *= velocityMultiplier;
        projectile.motionZ *= velocityMultiplier;
        projectile.velocityChanged = true;
        projectile.getEntityData().setInteger(CHARGE_TIER_TAG, tier);
        if (tier >= 2) {
            if (projectile instanceof EntityArrow) {
                ((EntityArrow)projectile).setIsCritical(true);
            } else if (projectile instanceof LOTREntityProjectileBase) {
                ((LOTREntityProjectileBase)projectile)
                        .setIsCritical(true);
            }
        }
        finishChargeFeedback(shooter, state, tier);
        return true;
    }

    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static synchronized void clear() {
        STATES.clear();
    }

    static int getAppliedTier(Entity projectile) {
        return projectile == null ? 0 : Math.max(0, Math.min(3,
                projectile.getEntityData().getInteger(CHARGE_TIER_TAG)));
    }

    private static synchronized void updatePlayer(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        UUID playerId = player.getUniqueID();
        ItemStack stack = player.getItemInUse();
        if (!LostTalesConfig.enableChargeTiers
                || !ThirdPersonChargeItemPolicy
                .supportsChargeTiers(stack)) {
            ChargeState previous = STATES.remove(playerId);
            if (previous != null) {
                send(player, false, false, 0, 1.0D);
            }
            return;
        }

        int tier = resolveTier(player, stack);
        int slot = player.inventory.currentItem;
        Item item = stack.getItem();
        long worldTick = player.worldObj.getTotalWorldTime();
        ChargeState previous = STATES.get(playerId);
        if (previous == null || previous.item != item
                || previous.hotbarSlot != slot
                || previous.tier != tier) {
            send(player, true, false, tier,
                    velocityMultiplier(tier));
        }
        STATES.put(playerId, new ChargeState(
                item, slot, tier, worldTick));
    }

    private static int resolveCurrentTier(EntityPlayerMP player) {
        ItemStack stack = player == null ? null : player.getItemInUse();
        return ThirdPersonChargeItemPolicy.supportsChargeTiers(stack)
                ? resolveTier(player, stack) : 0;
    }

    private static int resolveTier(
            EntityPlayerMP player, ItemStack stack) {
        int useTicks = Math.max(0,
                stack.getMaxItemUseDuration()
                        - player.getItemInUseCount());
        return ChargeTierCalculator.resolveTier(
                useTicks,
                ThirdPersonChargeItemPolicy.getFullDrawTicks(stack),
                LostTalesConfig.chargeTierOneTicks,
                LostTalesConfig.chargeTierTwoTicks,
                LostTalesConfig.chargeTierThreeTicks);
    }

    private static boolean isRecentState(
            ChargeState state, EntityPlayerMP shooter) {
        if (state == null || shooter == null
                || shooter.worldObj == null
                || shooter.inventory.currentItem != state.hotbarSlot) {
            return false;
        }
        ItemStack current = shooter.inventory.getCurrentItem();
        long age = shooter.worldObj.getTotalWorldTime()
                - state.worldTick;
        return current != null && current.getItem() == state.item
                && age >= 0L
                && age <= RELEASE_STATE_MAXIMUM_AGE_TICKS;
    }

    private static void finishChargeFeedback(
            EntityPlayerMP shooter, ChargeState state, int tier) {
        if (state == null || shooter == null
                || shooter.getUniqueID() == null) {
            return;
        }
        STATES.remove(shooter.getUniqueID());
        send(shooter, false, tier > 0, tier,
                velocityMultiplier(tier));
    }

    private static void send(
            EntityPlayerMP player, boolean active,
            boolean released, int tier, double velocityMultiplier) {
        int dimension = player.worldObj.provider == null
                ? player.dimension
                : player.worldObj.provider.dimensionId;
        LostTalesNetworkHandler.CHANNEL.sendToAllAround(
                new LostTalesChargeTierSyncPacket(
                        player.getEntityId(), active, released,
                        tier, velocityMultiplier),
                new NetworkRegistry.TargetPoint(
                        dimension, player.posX, player.posY,
                        player.posZ, FEEDBACK_RADIUS));
    }

    private static double damageMultiplier(int tier) {
        switch (tier) {
            case 1:
                return LostTalesConfig.chargeTierOneDamageMultiplier;
            case 2:
                return LostTalesConfig.chargeTierTwoDamageMultiplier;
            case 3:
                return LostTalesConfig.chargeTierThreeDamageMultiplier;
            default:
                return 1.0D;
        }
    }

    private static double velocityMultiplier(int tier) {
        switch (tier) {
            case 1:
                return LostTalesConfig.chargeTierOneVelocityMultiplier;
            case 2:
                return LostTalesConfig.chargeTierTwoVelocityMultiplier;
            case 3:
                return LostTalesConfig.chargeTierThreeVelocityMultiplier;
            default:
                return 1.0D;
        }
    }

    private static double knockbackBonus(int tier) {
        switch (tier) {
            case 1:
                return LostTalesConfig.chargeTierOneKnockback;
            case 2:
                return LostTalesConfig.chargeTierTwoKnockback;
            case 3:
                return LostTalesConfig.chargeTierThreeKnockback;
            default:
                return 0.0D;
        }
    }

    private static void applyBonusKnockback(
            net.minecraft.entity.EntityLivingBase target,
            Entity projectile, double strength) {
        if (target == null || projectile == null || strength <= 0.0D) {
            return;
        }
        double horizontal = Math.sqrt(
                projectile.motionX * projectile.motionX
                        + projectile.motionZ * projectile.motionZ);
        if (horizontal <= 0.000001D) {
            return;
        }
        target.addVelocity(
                projectile.motionX / horizontal * strength,
                Math.min(0.12D, strength * 0.35D),
                projectile.motionZ / horizontal * strength);
    }

    private static final class ChargeState {
        private final Item item;
        private final int hotbarSlot;
        private final int tier;
        private final long worldTick;

        private ChargeState(
                Item item, int hotbarSlot, int tier, long worldTick) {
            this.item = item;
            this.hotbarSlot = hotbarSlot;
            this.tier = tier;
            this.worldTick = worldTick;
        }
    }
}
