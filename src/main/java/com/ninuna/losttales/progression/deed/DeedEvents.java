package com.ninuna.losttales.progression.deed;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Feeds the deeds (PROG-11): who struck a creature, so a deed done
 * together finds its companions as it falls; the places a player
 * reaches, looked at every two seconds; and the deeds a character's state
 * already meets, checked again as a player joins or switches character.
 */
public final class DeedEvents {
    private static final int LOOK_TICKS = 40;
    /** Who struck each creature still standing; forgotten as it falls or unloads. */
    private static final Map<EntityLivingBase, Set<UUID>> STRIKERS =
            new WeakHashMap<EntityLivingBase, Set<UUID>>();
    private static final Set<UUID> DUE = new HashSet<UUID>();

    public static synchronized void clear() {
        STRIKERS.clear();
        DUE.clear();
    }

    /** The player's deeds are checked against their state on their next tick. */
    public static synchronized void checkSoon(UUID playerId) {
        DUE.add(playerId);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.entityLiving == null || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || !(event.source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        synchronized (DeedEvents.class) {
            Set<UUID> strikers = STRIKERS.get(event.entityLiving);
            if (strikers == null) {
                strikers = new LinkedHashSet<UUID>();
                STRIKERS.put(event.entityLiving, strikers);
            }
            strikers.add(event.source.getEntity().getUniqueID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.entityLiving == null || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote) {
            return;
        }
        Set<UUID> struck;
        synchronized (DeedEvents.class) {
            struck = STRIKERS.remove(event.entityLiving);
        }
        if (!(event.source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        List<EntityPlayerMP> strikers = new ArrayList<EntityPlayerMP>();
        MinecraftServer server = MinecraftServer.getServer();
        if (struck != null && server != null) {
            for (Object entry : server.getConfigurationManager().playerEntityList) {
                if (entry instanceof EntityPlayerMP
                        && struck.contains(((EntityPlayerMP) entry).getUniqueID())) {
                    strikers.add((EntityPlayerMP) entry);
                }
            }
        }
        DeedService.creatureSlain((EntityPlayerMP) event.source.getEntity(),
                event.entityLiving, strikers);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.player != null) {
            checkSoon(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        boolean due;
        synchronized (DeedEvents.class) {
            due = DUE.remove(player.getUniqueID());
        }
        if (due) {
            DeedService.evaluate(player);
        }
        if ((player.ticksExisted + player.getEntityId()) % LOOK_TICKS == 0) {
            DeedService.lookAround(player);
        }
    }
}
