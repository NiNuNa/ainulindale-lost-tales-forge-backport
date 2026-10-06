package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;

/**
 * In-character lines the server holds back for a moment. On a first visit
 * the account character bears the account's name until the player's look
 * file arrives and the world takes its name from it; an achievement or a
 * death in those first seconds would name the account in character. Such
 * a line waits until the file is taken or refused, then goes out naming
 * the character as it stands. It never waits longer than ten seconds, nor
 * past the player leaving.
 */
public final class ChatLinesAwaitingNames {
    /** How long a line waits at most, and how long after a login a line may wait at all. */
    static final long WAIT_MILLIS = 10000L;
    /** More lines waiting than this is more than a first visit holds; the oldest go out. */
    private static final int MAX_HELD = 64;
    /** How often the tick looks for lines that have waited long enough: once a second. */
    private static final int CHECK_TICKS = 20;

    private static final List<Held> HELD = new ArrayList<Held>();
    /** When each player online last logged in. */
    private static final Map<UUID, Long> ARRIVALS = new HashMap<UUID, Long>();
    /** Set while a held line goes out, so the broadcast seam lets it pass. */
    private static boolean releasing;
    private static int ticks;

    /**
     * Holds the line when it is in character and names a player who logged
     * in a moment ago and whose look file the world has not taken yet.
     * Answers whether it was held; a held line is not sent now.
     */
    static synchronized boolean hold(IChatComponent line, ChatChannel channel) {
        if (releasing || line == null || channel == null
                || channel.getPresentation()
                        == ChatPresentationMode.OUT_OF_CHARACTER) {
            return false;
        }
        long now = System.currentTimeMillis();
        Set<UUID> waiting = new LinkedHashSet<UUID>();
        for (String account : LostTalesServerBroadcastHook.namedAccounts(line)) {
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(account);
            if (player != null && arrivedWithin(player.getUniqueID(), now)
                    && awaitsLook(player)) {
                waiting.add(player.getUniqueID());
            }
        }
        if (waiting.isEmpty()) {
            return false;
        }
        while (HELD.size() >= MAX_HELD) {
            send(HELD.remove(0));
        }
        HELD.add(new Held(line, waiting, now));
        return true;
    }

    /**
     * The world has taken or refused the player's look file: every line
     * waiting only for them goes out, naming them as they now stand.
     */
    public static synchronized void release(UUID account) {
        if (account == null) {
            return;
        }
        Iterator<Held> iterator = HELD.iterator();
        List<Held> ready = new ArrayList<Held>();
        while (iterator.hasNext()) {
            Held held = iterator.next();
            if (held.waiting.remove(account) && held.waiting.isEmpty()) {
                iterator.remove();
                ready.add(held);
            }
        }
        for (Held held : ready) {
            send(held);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            noteArrival(event.player.getUniqueID(), System.currentTimeMillis());
        }
    }

    /** A player leaving takes nothing with them: what waited for them goes out. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            UUID account = event.player.getUniqueID();
            release(account);
            forget(account);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < CHECK_TICKS) {
            return;
        }
        ticks = 0;
        sendOverdue(System.currentTimeMillis());
    }

    static synchronized void noteArrival(UUID account, long now) {
        ARRIVALS.put(account, Long.valueOf(now));
    }

    private static synchronized void forget(UUID account) {
        ARRIVALS.remove(account);
    }

    /**
     * Whether this world has not yet read the player's look file: until
     * it has, the account character bears the account's name.
     */
    private static boolean awaitsLook(EntityPlayerMP player) {
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            return roster == null || !roster.isTemplateTaken();
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean arrivedWithin(UUID account, long now) {
        Long arrived = ARRIVALS.get(account);
        return arrived != null && now - arrived.longValue() < WAIT_MILLIS;
    }

    /** Every line that has waited its ten seconds goes out as it is. */
    static synchronized void sendOverdue(long now) {
        Iterator<Held> iterator = HELD.iterator();
        List<Held> due = new ArrayList<Held>();
        while (iterator.hasNext()) {
            Held held = iterator.next();
            if (now - held.heldMillis >= WAIT_MILLIS) {
                iterator.remove();
                due.add(held);
            }
        }
        for (Held held : due) {
            send(held);
        }
    }

    /**
     * Sends a held line through the broadcast seam, each player it names
     * written in by their name as it stands now.
     */
    private static void send(Held held) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        IChatComponent line = LostTalesServerBroadcastHook.swapNames(held.line,
                new LostTalesServerBroadcastHook.NameSwap() {
                    @Override
                    public IChatComponent swap(IChatComponent name, String account) {
                        EntityPlayerMP player = LostTalesServerPlayers.findOnline(account);
                        return player == null ? null : player.func_145748_c_();
                    }
                });
        releasing = true;
        try {
            server.getConfigurationManager().sendChatMsg(line);
        } finally {
            releasing = false;
        }
    }

    /** Cleared with the rest of the server's chat state; nothing waiting goes out. */
    public static synchronized void clear() {
        HELD.clear();
        ARRIVALS.clear();
        releasing = false;
        ticks = 0;
    }

    /** A line waiting for the players it names. */
    private static final class Held {
        final IChatComponent line;
        final Set<UUID> waiting;
        final long heldMillis;

        Held(IChatComponent line, Set<UUID> waiting, long heldMillis) {
            this.line = line;
            this.waiting = waiting;
            this.heldMillis = heldMillis;
        }
    }
}
