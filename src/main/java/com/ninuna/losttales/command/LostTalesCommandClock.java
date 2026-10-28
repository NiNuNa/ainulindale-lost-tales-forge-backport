package com.ninuna.losttales.command;

import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.deletion.CharacterDeletionStorage;
import com.ninuna.losttales.character.deletion.CharacterDeletionWorldData;
import com.ninuna.losttales.chat.moderation.ChatAuditLog;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.storage.WorldClock;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * The world's clock (DATA-09, DATA-15). {@code /losttales clock} says
 * when the world last saw the server's clock, whether a jump of it holds
 * anything back, and whether it reads more than a day behind the world's
 * latest time; {@code accept} is an operator's word that the clock is
 * right. The world's latest time becomes the clock's reading, and what a
 * jump held expires: the deleted characters past their retention are
 * purged, a bounded number at once, the chat audit files past theirs are
 * removed, and the last-seen times past theirs are forgotten as their
 * store next loads.
 */
public final class LostTalesCommandClock extends LostTalesCommandBase {

    static final String SAY = "chat.losttales.command.clock.";
    /** The most deleted characters an accepted clock purges at once; the rest go at logins and starts. */
    private static final int PURGES_AT_ACCEPT = 64;

    public LostTalesCommandClock() {
        super("clock");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales clock [accept]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.SERVER_CONFIG;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        MinecraftServer server = MinecraftServer.getServer();
        World world = server == null ? null : server.worldServerForDimension(0);
        WorldClock.Store clock = WorldClock.store(world);
        if (clock == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_world");
            return;
        }
        // Looked at first, so what is said is the clock as it reads now.
        WorldClock.observe(server);
        if (args == null || args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            status(sender, world, clock);
        } else if ("accept".equalsIgnoreCase(args[0]) && args.length == 1) {
            accept(sender, world, clock);
        } else {
            usage(sender, getCommandUsage(sender));
        }
    }

    private static void status(ICommandSender sender, World world, WorldClock.Store clock) {
        if (clock.isPreserved()) {
            say(sender, EnumChatFormatting.RED, SAY + "unreadable");
            return;
        }
        long now = System.currentTimeMillis();
        say(sender, EnumChatFormatting.GRAY, SAY + "seen",
                WorldClock.format(clock.getLastSeen()), WorldClock.format(now));
        boolean behind = clock.isBehind(now);
        if (behind) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "behind");
        }
        if (clock.isHolding()) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "held",
                    WorldClock.format(clock.getHeldFrom()),
                    WorldClock.format(clock.getHeldTo()),
                    Integer.valueOf(heldDeletions(world, clock, now)));
        } else if (!behind) {
            say(sender, EnumChatFormatting.GREEN, SAY + "steady");
            return;
        }
        say(sender, EnumChatFormatting.GRAY, SAY + "accept_hint");
    }

    private static void accept(ICommandSender sender, World world, WorldClock.Store clock) {
        if (clock.isPreserved()) {
            say(sender, EnumChatFormatting.RED, SAY + "unreadable");
            return;
        }
        long now = System.currentTimeMillis();
        boolean behind = clock.isBehind(now);
        boolean holding = clock.isHolding();
        if (!holding && !behind) {
            say(sender, EnumChatFormatting.GRAY, SAY + "nothing_to_accept");
            return;
        }
        long latest = clock.getLastSeen();
        long from = clock.getHeldFrom();
        long to = clock.getHeldTo();
        clock.accept(now);
        if (behind) {
            LostTalesLog.warning("%s accepted the server's clock; the world's latest time moves"
                    + " back from %s to %s", sender.getCommandSenderName(),
                    WorldClock.format(latest), WorldClock.format(now));
            say(sender, EnumChatFormatting.GREEN, SAY + "accepted.behind",
                    WorldClock.format(now));
        }
        if (holding) {
            LostTalesLog.warning("%s accepted the server's clock; what its jump from %s to %s"
                    + " held may expire now", sender.getCommandSenderName(),
                    WorldClock.format(from), WorldClock.format(to));
            int purged = CharacterDeletionService.getInstance().purgeExpired(world,
                    PURGES_AT_ACCEPT);
            ChatAuditLog.pruneExpired();
            say(sender, EnumChatFormatting.GREEN, SAY + "accepted", Integer.valueOf(purged));
        }
    }

    /** How many deleted characters past their retention the clock holds; 0 for a journal that cannot be read. */
    private static int heldDeletions(World world, WorldClock.Hold hold, long now) {
        try {
            CharacterDeletionWorldData deletions = CharacterDeletionStorage.get(world);
            return deletions.isReadOnlyForNewerVersion() ? 0 : deletions.countHeld(now, hold);
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }
}
