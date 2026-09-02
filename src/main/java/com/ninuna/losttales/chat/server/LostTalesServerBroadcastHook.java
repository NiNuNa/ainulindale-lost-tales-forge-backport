package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.util.IChatComponent;

/**
 * Called by the coremod at the head of
 * {@code ServerConfigurationManager.sendChatMsg}, the one method every
 * line the whole server sees passes through: death messages, vanilla and
 * LOTR achievements, joins and leaves, {@code /say}. It is the server-side
 * twin of the seam the client routes the same lines into Global from —
 * the component is exactly what every player is about to be sent, other
 * mods' rewrites included — and it hands the line to whoever listens for
 * server-wide announcements; today that is the Discord bridge. Purely
 * observational: the broadcast is never changed, delayed or refused, and
 * a failure here is logged once and never reaches the caller.
 *
 * <p>Without the patch nothing calls this and the listeners simply hear
 * of nothing; the bridge says so at start-up when it is asked to relay
 * what only this seam can carry.</p>
 */
public final class LostTalesServerBroadcastHook {
    private static volatile boolean failureLogged;

    private LostTalesServerBroadcastHook() {}

    public static void onBroadcast(IChatComponent message) {
        try {
            if (message != null) {
                DiscordGameEventRelay.onServerBroadcast(message);
            }
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Could not relay a server "
                        + "broadcast: %s", throwable);
            }
        }
    }
}
