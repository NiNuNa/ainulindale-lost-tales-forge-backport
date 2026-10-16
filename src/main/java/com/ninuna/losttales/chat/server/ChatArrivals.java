package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresence;
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
 * names them by id like every other player a line names. Whether they
 * come Invisible is read here too, from the status their saved data
 * keeps ({@link ChatPresenceRecord}): their join line is then said to
 * nobody.
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
                event.entityPlayer.getGameProfile().getName()),
                ChatPresenceRecord.read(event.entityPlayer) == ChatPresence.INVISIBLE);
    }

    static synchronized void note(ChatNamedPlayer player, boolean invisible) {
        if (player == null || !player.isValid()) {
            return;
        }
        String key = player.getAccount().toLowerCase(Locale.ROOT);
        WAITING.remove(key);
        WAITING.put(key, new Arrival(player, invisible, System.currentTimeMillis()));
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

    /**
     * Whether the account logging in under {@code account} comes
     * Invisible, taken once answered so; one who comes visibly is left for
     * {@link #take}.
     */
    static synchronized boolean arrivesInvisible(String account) {
        if (account == null) {
            return false;
        }
        String key = account.trim().toLowerCase(Locale.ROOT);
        Arrival arrival = WAITING.get(key);
        if (arrival == null || !arrival.invisible
                || System.currentTimeMillis() - arrival.notedMillis > WAIT_MILLIS) {
            return false;
        }
        WAITING.remove(key);
        return true;
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        WAITING.clear();
    }

    private static final class Arrival {
        final ChatNamedPlayer player;
        final boolean invisible;
        final long notedMillis;

        Arrival(ChatNamedPlayer player, boolean invisible, long notedMillis) {
            this.player = player;
            this.invisible = invisible;
            this.notedMillis = notedMillis;
        }
    }
}
