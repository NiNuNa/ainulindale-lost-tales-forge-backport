package com.ninuna.losttales.event;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.entity.LostTalesHostilityHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
/**
 * Server-side companion for hostile compass markers.
 *
 * Modern NeoForge can query mob targets on the logical server and sync a small
 * entity-id list to the client. This 1.7.10 version keeps the same purpose but
 * uses FML player ticks and SimpleNetworkWrapper packets.
 */
public class LostTalesMobAggroEventHandler {
    public static final int DEFAULT_AGGRO_MOB_SCAN_RADIUS = 48;
    private static final int PERIOD_TICKS = 10;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.side != Side.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.worldObj == null || player.ticksExisted % PERIOD_TICKS != 0) {
            return;
        }

        double radius = Math.max(8.0D, LostTalesConfig.hostileCompassMarkerScanRadius);
        AxisAlignedBB scanBox = player.boundingBox.expand(radius, radius, radius);
        List entities = player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, scanBox);
        List<Integer> aggroEntityIds = new ArrayList<Integer>();

        for (Object object : entities) {
            if (!(object instanceof EntityLivingBase) || object == player) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) object;
            if (LostTalesHostilityHelper.isActivelyTargetingPlayer(living, player)) {
                aggroEntityIds.add(living.getEntityId());
            }
        }

        LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesMobAggroSyncPacket(aggroEntityIds), player);
    }
}
