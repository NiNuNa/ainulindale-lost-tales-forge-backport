package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.physics.CharacterRaceDimensions;
import com.ninuna.losttales.character.physics.CharacterSpawnOriginHelper;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.entity.projectile.LOTREntityProjectileBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;

/**
 * Corrects fresh player-created entity origins on the logical server.
 *
 * Motion, rotation, and horizontal position are intentionally left unchanged,
 * so this does not alter projectile trajectories or item-toss direction. The
 * final server spawn position is sent through the ordinary entity spawn packet
 * and therefore requires no per-action custom networking.
 */
public final class CharacterSpawnOriginHandler {

    private static final String TAG_ORIGIN_APPLIED =
            "LostTalesRaceSpawnOriginApplied";
    private static final double POSITION_EPSILON = 0.0001D;
    private static final double MAXIMUM_FRESH_PROJECTILE_DISTANCE_SQ = 16.0D;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemToss(ItemTossEvent event) {
        if (event == null || event.entityItem == null
                || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null
                || event.player.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP)event.player;
        CharacterRaceDimensions dimensions = resolveDimensions(player);
        if (dimensions == null) {
            return;
        }

        EntityItem item = event.entityItem;
        setEntityY(item,
                CharacterSpawnOriginHelper.getItemDropOriginY(
                        player, dimensions));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null || event.entity == null || event.world == null
                || event.world.isRemote) {
            return;
        }

        Entity projectile = event.entity;
        EntityPlayerMP shooter = findPlayerShooter(projectile);
        if (shooter == null
                || projectile.getEntityData().getBoolean(TAG_ORIGIN_APPLIED)
                || !isFreshProjectileNearShooter(projectile, shooter)) {
            return;
        }

        CharacterRaceDimensions dimensions = resolveDimensions(shooter);
        if (dimensions == null) {
            return;
        }

        setEntityY(projectile,
                CharacterSpawnOriginHelper.getProjectileOriginY(
                        shooter, dimensions));
        projectile.getEntityData().setBoolean(TAG_ORIGIN_APPLIED, true);
    }

    private static CharacterRaceDimensions resolveDimensions(
            EntityPlayerMP player) {
        RoleplayCharacter character = CharacterActiveResolver.get(player);
        if (character == null) {
            return null;
        }
        CharacterRaceGameplayProfile profile =
                LotrRaceProfileAdapter.getInstance().resolve(
                        player.worldObj, character.getRaceId());
        return CharacterRaceDimensions.fromProfile(
                character.getRaceId(), profile);
    }

    private static EntityPlayerMP findPlayerShooter(Entity entity) {
        Entity shooter = null;
        if (entity instanceof EntityArrow) {
            shooter = ((EntityArrow)entity).shootingEntity;
        } else if (entity instanceof EntityThrowable) {
            shooter = ((EntityThrowable)entity).getThrower();
        } else if (entity instanceof LOTREntityProjectileBase) {
            shooter = ((LOTREntityProjectileBase)entity).shootingEntity;
        }
        return shooter instanceof EntityPlayerMP
                ? (EntityPlayerMP)shooter : null;
    }

    private static boolean isFreshProjectileNearShooter(
            Entity projectile, EntityPlayerMP shooter) {
        return projectile.ticksExisted == 0
                && projectile.getDistanceSqToEntity(shooter)
                <= MAXIMUM_FRESH_PROJECTILE_DISTANCE_SQ;
    }

    private static void setEntityY(Entity entity, double y) {
        if (entity == null || Double.isNaN(y) || Double.isInfinite(y)
                || Math.abs(entity.posY - y) < POSITION_EPSILON) {
            return;
        }
        entity.setPosition(entity.posX, y, entity.posZ);
    }
}
