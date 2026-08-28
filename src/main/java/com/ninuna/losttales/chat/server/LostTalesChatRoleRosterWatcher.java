package com.ninuna.losttales.chat.server;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.server.MinecraftServer;

/**
 * Notices role changes the server never sees as an event. Operator status
 * is granted and revoked by {@code /op} and {@code /deop}, which fire
 * nothing a mod can subscribe to, so the role roster the chat access
 * carries — and with it the role hover card and the Admin tab — would
 * stay stale until the affected player relogged. Once a minute this
 * compares a fingerprint of the current roster against the last one that
 * went out and re-broadcasts the access when they differ; the joins and
 * leaves that broadcast anyway report theirs here, so a quiet server pays
 * one string build per minute and sends nothing.
 */
public final class LostTalesChatRoleRosterWatcher {
    /** One check a minute: staleness bound, and effectively free. */
    private static final int CHECK_INTERVAL_TICKS = 20 * 60;

    private static int ticksUntilCheck = CHECK_INTERVAL_TICKS;
    /** The last roster broadcast, as a fingerprint; null before any. */
    private static String lastSignature;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || MinecraftServer.getServer() == null) {
            return;
        }
        if (--ticksUntilCheck > 0) {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        String signature = LostTalesChatService.roleRosterSignature();
        if (lastSignature == null) {
            // The login broadcasts already told everyone; only remember
            // what they said.
            lastSignature = signature;
            return;
        }
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            LostTalesChatService.sendAccessToAll(null);
        }
    }

    /**
     * Records a roster that just went out with an access broadcast, so
     * the next periodic check does not send the same roster again.
     */
    static void noteBroadcast(String signature) {
        lastSignature = signature;
    }

    public static void clear() {
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        lastSignature = null;
    }
}
