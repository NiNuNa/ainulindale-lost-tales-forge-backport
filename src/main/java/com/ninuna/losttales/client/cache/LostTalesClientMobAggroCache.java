package com.ninuna.losttales.client.cache;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
/**
 * Client-side cache of mobs that the server recently confirmed are attacking
 * the local player.
 *
 * Forge 1.7.10 does not reliably expose every server AI target on the client,
 * especially for modded NPCs. The server refreshes this small ID list
 * periodically and the client keeps it for a short TTL so compass rendering can
 * remain smooth between packets.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientMobAggroCache {
    private static final Map<Integer, Long> AGGRO_UNTIL_TICK = new HashMap<Integer, Long>();
    private static final int TTL_TICKS = 20;

    private LostTalesClientMobAggroCache() {}

    public static synchronized void accept(Iterable<Integer> entityIds) {
        long now = getClientTick();
        long until = now + TTL_TICKS;
        if (entityIds != null) {
            for (Integer entityId : entityIds) {
                if (entityId != null) {
                    AGGRO_UNTIL_TICK.put(entityId, until);
                }
            }
        }
        pruneExpired(now);
    }

    public static synchronized boolean isAggro(int entityId) {
        long now = getClientTick();
        Long until = AGGRO_UNTIL_TICK.get(entityId);
        if (until == null) {
            return false;
        }
        if (until.longValue() <= now) {
            AGGRO_UNTIL_TICK.remove(entityId);
            return false;
        }
        return true;
    }

    public static synchronized void clear() {
        AGGRO_UNTIL_TICK.clear();
    }

    private static void pruneExpired(long now) {
        Iterator<Map.Entry<Integer, Long>> iterator = AGGRO_UNTIL_TICK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (entry.getValue().longValue() <= now) {
                iterator.remove();
            }
        }
    }

    private static long getClientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.theWorld != null ? minecraft.theWorld.getTotalWorldTime() : 0L;
    }
}
