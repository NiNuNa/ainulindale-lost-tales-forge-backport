package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * The players logging in right now, by account. The game announces a
 * join before it lists the player who joined, so the join line cannot
 * find them among the players online; it finds them here instead, noted
 * as their saved data was read a moment before the announcement, and so
 * names them by id like every other player a line names.
 */
public final class ChatArrivals {
    /** How long an arrival waits for its join line; the two happen on one tick. */
    private static final long WAIT_MILLIS = 60000L;
    /** More arrivals waiting than this is more than a tick holds; the oldest go. */
    private static final int MAX_WAITING = 64;
    private static final Map<String, Arrival> WAITING =
            new LinkedHashMap<String, Arrival>();

    /** Notes the player whose saved data the server has just read. */
    @SubscribeEvent
    public void onLoadFromFile(PlayerEvent.LoadFromFile event) {
        if (event == null || event.entityPlayer == null
                || event.entityPlayer.getGameProfile() == null
                || event.entityPlayer.getUniqueID() == null) {
            return;
        }
        note(ChatNamedPlayer.account(event.entityPlayer.getUniqueID(),
                event.entityPlayer.getGameProfile().getName()));
    }

    static synchronized void note(ChatNamedPlayer player) {
        if (player == null || !player.isValid()) {
            return;
        }
        String key = player.getAccount().toLowerCase(Locale.ROOT);
        WAITING.remove(key);
        WAITING.put(key, new Arrival(player, System.currentTimeMillis()));
        Iterator<String> oldest = WAITING.keySet().iterator();
        while (WAITING.size() > MAX_WAITING && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The account logging in under {@code account}, no longer waiting
     * once taken, or null when nobody by that name is arriving.
     */
    static synchronized ChatNamedPlayer take(String account) {
        if (account == null) {
            return null;
        }
        Arrival arrival = WAITING.remove(account.trim().toLowerCase(Locale.ROOT));
        if (arrival == null
                || System.currentTimeMillis() - arrival.notedMillis > WAIT_MILLIS) {
            return null;
        }
        return arrival.player;
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        WAITING.clear();
    }

    private static final class Arrival {
        final ChatNamedPlayer player;
        final long notedMillis;

        Arrival(ChatNamedPlayer player, long notedMillis) {
            this.player = player;
            this.notedMillis = notedMillis;
        }
    }
}
