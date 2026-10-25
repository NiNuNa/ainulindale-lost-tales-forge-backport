package com.ninuna.losttales.quest;

import com.ninuna.losttales.quickloot.LostTalesQuickLootInventoryHelper;
import com.ninuna.losttales.fellowship.quest.FellowshipQuestProgressCoordinator;
import com.ninuna.losttales.quest.world.WorldQuests;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.ninuna.losttales.compat.lotr.LotrNpcRelations;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
/**
 * The game events quests count by, on the server: kills, pickups, crafts,
 * right-clicks on items, blocks and creatures, every tick the hand-overs
 * due, and a look once a second at deadlines, places and markers.
 * Registered on both Forge's bus and FML's, since the events come from
 * both.
 */
public final class LostTalesQuestObjectiveEventHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null || event.entityLiving == null || event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) {
            return;
        }

        LostTalesQuestManager.handleDeath(event.entityLiving);
        EntityPlayerMP player = resolveQuestKillCredit(event.source);
        if (player != null) {
            FellowshipQuestProgressCoordinator.getInstance().handleAuthoritativeKill(player, event.entityLiving);
            WorldQuests.handleKill(player, event.entityLiving);
        }
    }

    /**
     * An item really picked up: FML's event, fired once the item is in the
     * inventory. Forge's pickup event fires on every touch, before the
     * inventory takes anything, so it is not listened to.
     */
    @SubscribeEvent
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP) || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        EntityItem itemEntity = event.pickedUp;
        ItemStack stack = itemEntity == null ? null : itemEntity.getEntityItem();
        LostTalesQuestManager.handleItemReceived((EntityPlayerMP) event.player, stack);
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP) || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        LostTalesQuestManager.handleItemCrafted((EntityPlayerMP) event.player, event.crafting);
        WorldQuests.handleCraft((EntityPlayerMP) event.player, event.crafting);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // A missive board's request asks other mods as a right-click would;
        // it is no touch and starts nothing.
        if (LostTalesQuickLootInventoryHelper.isAskingQuietly()) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        ItemStack held = player.inventory == null ? null : player.inventory.getCurrentItem();
        String startsQuest = QuestStarters.questIdOf(held);
        if (startsQuest.length() > 0) {
            QuestStartGate.start(player, startsQuest, QuestStartProof.item(held));
        }

        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            QuestStartGate.touchedBlock(player, event.x, event.y, event.z);
        }
    }

    /**
     * A creature touched: the quests it gives or waits on answer first.
     * Where they did and it is a LOTR NPC, LOTR's own talk, trade or hire
     * waits (QUEST-11): the touch goes no further.
     */
    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (QuestStartGate.touchedCreature((EntityPlayerMP) event.entityPlayer, event.target)
                && LotrNpcRelations.isNpc(event.target) && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null
                || event.player.worldObj.isRemote) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            LostTalesWaypointFastTravelPolicy
                    .cancelUnauthorizedTarget(
                            (EntityPlayerMP)event.player);
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // A stage that ended inside a click (a craft is counted in the
        // middle of the inventory's own handling) hands its items over
        // here, on the tick after.
        LostTalesQuestManager.settleHandOvers((EntityPlayerMP) event.player);
        if (event.player.ticksExisted % 20 != 0) {
            return;
        }

        FellowshipQuestProgressCoordinator.getInstance()
                .handleAuthoritativeTravel((EntityPlayerMP) event.player);
        LostTalesQuestManager.handlePlayerTick((EntityPlayerMP) event.player);
        WorldQuests.handlePlayerTick((EntityPlayerMP) event.player);
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
