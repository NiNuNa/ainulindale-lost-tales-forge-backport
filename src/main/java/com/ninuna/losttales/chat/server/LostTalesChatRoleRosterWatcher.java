package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.permission.LostTalesPermissions;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

/**
 * Notices role changes the server never sees as an event. Operator status
 * is granted and revoked by {@code /op} and {@code /deop}, which fire
 * nothing a mod can subscribe to, so the role roster the chat access
 * carries — and with it the role hover card and the Operator tab — would
 * stay stale until the affected player relogged. Once a minute, and on
 * the tick a command runs ({@link #checkSoon}), this compares a
 * fingerprint of the current roster against the last one that went out
 * and re-broadcasts the access when they differ; the joins and leaves
 * that broadcast anyway report theirs here, so a quiet server pays one
 * string build per minute and sends nothing.
 *
 * <p>It also says so to the player it happened to: someone who has just
 * become an operator is told in their Client Console, and so is someone
 * who no longer is. Someone who has just come to read the Server Console
 * is sent what it holds, as they would have been on joining.</p>
 */
public final class LostTalesChatRoleRosterWatcher {
    /** One check a minute: staleness bound, and effectively free. */
    private static final int CHECK_INTERVAL_TICKS = 20 * 60;

    private static int ticksUntilCheck = CHECK_INTERVAL_TICKS;
    /** The last roster broadcast, as a fingerprint; null before any. */
    private static String lastSignature;
    /** Everyone online at the last check, and who of them were operators and read the console. */
    private static final Set<UUID> SEEN = new HashSet<UUID>();
    private static final Set<UUID> OPERATORS = new HashSet<UUID>();
    private static final Set<UUID> CONSOLE_READERS = new HashSet<UUID>();

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
        } else if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            LostTalesChatService.sendAccessToAll(null);
        }
        tellWhoseStandingChanged();
    }

    /** Looks at the roster at the end of this tick rather than the minute's end. */
    static void checkSoon() {
        ticksUntilCheck = 1;
    }

    /** Remembers where a player who has just logged in stands; their login told them. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP
                && event.player.getUniqueID() != null) {
            EntityPlayerMP player = (EntityPlayerMP)event.player;
            UUID id = player.getUniqueID();
            SEEN.add(id);
            if (LostTalesPermissions.isOperator(player)) {
                OPERATORS.add(id);
            }
            if (ChatChannelPolicy.readsConsole(player)) {
                CONSOLE_READERS.add(id);
            }
        }
    }

    /** Forgets a player who has left. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event != null && event.player != null) {
            UUID id = event.player.getUniqueID();
            SEEN.remove(id);
            OPERATORS.remove(id);
            CONSOLE_READERS.remove(id);
        }
    }

    /**
     * Tells each player on record — since their login or the last check —
     * whose operator standing or whose reading of the console changed
     * since, then remembers everyone as they stand now. A player not on
     * record only joins it.
     */
    private static void tellWhoseStandingChanged() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        Set<UUID> seen = new HashSet<UUID>();
        Set<UUID> operators = new HashSet<UUID>();
        Set<UUID> readers = new HashSet<UUID>();
        for (EntityPlayerMP player : online) {
            UUID id = player == null ? null : player.getUniqueID();
            if (id == null) {
                continue;
            }
            seen.add(id);
            boolean operator = LostTalesPermissions.isOperator(player);
            boolean reads = ChatChannelPolicy.readsConsole(player);
            if (operator) {
                operators.add(id);
            }
            if (reads) {
                readers.add(id);
            }
            if (!SEEN.contains(id)) {
                continue;
            }
            if (operator != OPERATORS.contains(id)) {
                // The Server's own word, in the yellow of a join or a leave.
                ChatComponentTranslation note = new ChatComponentTranslation(
                        operator ? "chat.losttales.operator.granted"
                                : "chat.losttales.operator.revoked");
                note.getChatStyle().setColor(EnumChatFormatting.YELLOW);
                player.addChatMessage(note);
            }
            if (reads && !CONSOLE_READERS.contains(id)) {
                LostTalesChatService.sendConsoleHistory(player);
            }
        }
        SEEN.clear();
        SEEN.addAll(seen);
        OPERATORS.clear();
        OPERATORS.addAll(operators);
        CONSOLE_READERS.clear();
        CONSOLE_READERS.addAll(readers);
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
        SEEN.clear();
        OPERATORS.clear();
        CONSOLE_READERS.clear();
    }
}
