package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestProgressPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.common.FMLLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Sends a player's quest log to their client at what the change costs:
 * the whole log when anything but a running quest's counts changed since
 * the client was last sent it, only the quests whose counts moved when
 * nothing else did, and nothing when nothing changed. A kill, a pickup or
 * a craft so costs one quest's counts, never the log with every missive
 * it keeps.
 *
 * <p>What each client was last sent is kept by account until its player
 * leaves: the log's shape, a digest of everything but the counts, and
 * each running quest's counts. Static state, cleared with the server's
 * other stores.</p>
 */
public final class QuestLogSync {
    private static final Map<UUID, Sent> SENT = new HashMap<UUID, Sent>();
    /** The players already told of in the log for a log that cannot be sent. */
    private static final Set<UUID> WARNED = new HashSet<UUID>();

    private QuestLogSync() {}

    /** What a client was last sent of its log. */
    static final class Sent {
        final byte[] shape;
        final Map<String, Map<String, Integer>> counts;

        Sent(byte[] shape, Map<String, Map<String, Integer>> counts) {
            this.shape = shape;
            this.counts = counts;
        }
    }

    /** What brings a client from what it was sent to the log: one packet, or none. */
    static final class Step {
        final LostTalesQuestSyncPacket whole;
        final LostTalesQuestProgressPacket counts;
        final Sent sent;

        Step(LostTalesQuestSyncPacket whole, LostTalesQuestProgressPacket counts,
             Sent sent) {
            this.whole = whole;
            this.counts = counts;
            this.sent = sent;
        }
    }

    /**
     * Brings the player's client up to {@code log}. A log that does not
     * fit one packet is not sent; the client keeps the log it has, and
     * the server's log says so once for each player.
     */
    public static void send(EntityPlayerMP player, LostTalesQuestSyncPacket log) {
        if (player == null || player.getUniqueID() == null || log == null) {
            return;
        }
        UUID account = player.getUniqueID();
        Step step;
        try {
            step = step(SENT.get(account), log);
        } catch (IllegalStateException unsendable) {
            if (WARNED.add(account)) {
                FMLLog.warning("[%s] The quest log of player %s cannot be sent "
                                + "(%s); their client keeps the log it has.",
                        LostTalesMetaData.MOD_ID, account, unsendable.getMessage());
            }
            return;
        }
        if (step.whole != null) {
            LostTalesNetworkHandler.CHANNEL.sendTo(step.whole, player);
        } else if (step.counts != null) {
            LostTalesNetworkHandler.CHANNEL.sendTo(step.counts, player);
        }
        SENT.put(account, step.sent);
    }

    /**
     * What brings a client that was sent {@code previous}, or nothing for
     * null, up to {@code log}. Throws {@link IllegalStateException} for a
     * log that cannot be written into one packet.
     */
    static Step step(Sent previous, LostTalesQuestSyncPacket log) {
        Sent sent = new Sent(digest(log.withoutObjectiveCounts()), countsOf(log));
        if (previous == null || !Arrays.equals(previous.shape, sent.shape)) {
            fits(log);
            return new Step(log, null, sent);
        }
        List<LostTalesQuestProgress> moved = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : log.getActiveQuests()) {
            if (!progress.getObjectiveProgress().equals(
                    previous.counts.get(progress.getQuestId()))) {
                moved.add(progress);
            }
        }
        return new Step(null, moved.isEmpty() ? null
                : new LostTalesQuestProgressPacket(moved), sent);
    }

    /** Forgets what the player's client was sent; when they leave. */
    public static void forget(UUID account) {
        if (account != null) {
            SENT.remove(account);
            WARNED.remove(account);
        }
    }

    /** Forgets every client; with the server's other stores. */
    public static void clear() {
        SENT.clear();
        WARNED.clear();
    }

    private static Map<String, Map<String, Integer>> countsOf(
            LostTalesQuestSyncPacket log) {
        Map<String, Map<String, Integer>> counts =
                new HashMap<String, Map<String, Integer>>();
        for (LostTalesQuestProgress progress : log.getActiveQuests()) {
            counts.put(progress.getQuestId(), new LinkedHashMap<String, Integer>(
                    progress.getObjectiveProgress()));
        }
        return counts;
    }

    /** Throws {@link IllegalStateException} where the log does not write into one packet. */
    private static void fits(LostTalesQuestSyncPacket log) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            log.toBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    /** A SHA-256 digest of the packet's bytes. */
    private static byte[] digest(LostTalesQuestSyncPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available", missing);
        } finally {
            buffer.release();
        }
    }
}
