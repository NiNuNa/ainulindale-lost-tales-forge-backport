package com.ninuna.losttales.event;

import com.ninuna.losttales.quest.LostTalesQuestInteractionHelper;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.party.quest.PartyQuestProgressCoordinator;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
/**
 * Forge 1.7.10 objective event bridge.
 *
 * This replaces the modern NeoForge objective event hooks with stable old-Forge events.
 */
public final class LostTalesQuestObjectiveEventHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null || event.entityLiving == null || event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = resolveQuestKillCredit(event.source);
        if (player != null) {
            PartyQuestProgressCoordinator.getInstance().handleAuthoritativeKill(player, event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }

        EntityItem itemEntity = event.item;
        ItemStack stack = itemEntity == null ? null : itemEntity.getEntityItem();
        LostTalesQuestManager.handleItemPickedUp((EntityPlayerMP) event.entityPlayer, stack);
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP) || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        LostTalesQuestManager.handleItemCrafted((EntityPlayerMP) event.player, event.crafting);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack held = event.entityPlayer.inventory == null ? null : event.entityPlayer.inventory.getCurrentItem();
        if (held != null && held.hasTagCompound() && held.getTagCompound().hasKey("LostTalesQuestId")) {
            LostTalesQuestManager.startQuestFromItem((EntityPlayerMP) event.entityPlayer, held);
        }

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            Block block = event.world == null ? null : event.world.getBlock(event.x, event.y, event.z);
            int metadata = event.world == null ? 0 : event.world.getBlockMetadata(event.x, event.y, event.z);
            LostTalesQuestInteractionHelper.handleBlockInteraction((EntityPlayerMP) event.entityPlayer, block, metadata, event.x, event.y, event.z);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        LostTalesQuestInteractionHelper.handleEntityInteraction((EntityPlayerMP) event.entityPlayer, event.target);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        if (event.player.ticksExisted % 20 != 0) {
            return;
        }

        LostTalesQuestManager.handlePlayerTick((EntityPlayerMP) event.player);
    }

    private EntityPlayerMP resolveQuestKillCredit(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity direct = source.getEntity();
        EntityPlayerMP player = playerFromEntity(direct);
        if (player != null) {
            return player;
        }

        Entity sourceEntity = source.getSourceOfDamage();
        player = playerFromEntity(sourceEntity);
        if (player != null) {
            return player;
        }

        if (sourceEntity instanceof EntityArrow) {
            player = playerFromEntity(((EntityArrow) sourceEntity).shootingEntity);
            if (player != null) {
                return player;
            }
        }

        if (sourceEntity instanceof EntityThrowable) {
            player = playerFromEntity(((EntityThrowable) sourceEntity).getThrower());
            if (player != null) {
                return player;
            }
        }

        return null;
    }

    private EntityPlayerMP playerFromEntity(Entity entity) {
        if (entity instanceof EntityPlayerMP) {
            return (EntityPlayerMP) entity;
        }
        if (entity instanceof EntityTameable) {
            Entity owner = ((EntityTameable) entity).getOwner();
            if (owner instanceof EntityPlayerMP) {
                return (EntityPlayerMP) owner;
            }
        }
        return null;
    }
}
