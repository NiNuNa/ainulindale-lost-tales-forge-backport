package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

/**
 * Whether a missive may still be taken in a world, and the record that it
 * was ({@link MissivesTakenWorldData}, QUEST-34): once only, by anyone, and
 * only while its notice could still stand on a board, its days counted
 * from its first posting wherever its letter goes up again. Server only.
 */
public final class MissivesTaken {
    /**
     * The most days a notice may stand, the top of
     * {@code missiveBoardNoticeExpirationDays}: the record keeps a missive
     * taken this long from its first posting, so no setting the server may
     * choose brings a copy of its letter back.
     */
    static final int LONGEST_NOTICE_DAYS = 30;

    private MissivesTaken() {}

    /**
     * How long the world remembers a missive taken, from its first
     * posting: as long as a notice could stand under any setting while
     * notices expire; for good (0) while they never do.
     */
    static long keepTicks(boolean noticesExpire) {
        return noticesExpire ? LONGEST_NOTICE_DAYS * 24000L : 0L;
    }

    /**
     * Why the missive cannot be taken in this world now, or null where it
     * can: its notice's time is past, it was taken already, or the world's
     * record cannot be read.
     */
    public static MissiveBoardStateReason refusal(World world,
                                                  LostTalesMissiveData missive) {
        if (missive == null) {
            return MissiveBoardStateReason.DAMAGED;
        }
        long keep = LostTalesConfig.getMissiveBoardNoticeExpirationTicks();
        if (keep > 0L && world != null && missive.getGenerationWorldTime()
                + keep <= world.getTotalWorldTime()) {
            return MissiveBoardStateReason.EXPIRED;
        }
        MissivesTakenWorldData data = of(world);
        if (data == null || !data.isUsable()) {
            if (data != null && data.firstRefusal()) {
                LostTalesLog.warning("The record of missives taken in this world"
                        + " cannot be read; no missive can be accepted until it can");
            }
            return MissiveBoardStateReason.NOT_NOW;
        }
        return data.wasTaken(missive.getQuestId())
                ? MissiveBoardStateReason.TAKEN_BEFORE : null;
    }

    /**
     * Records the missive as taken; answers whether the world now
     * remembers it: not while the record is read-only or full.
     */
    public static boolean record(World world, LostTalesMissiveData missive) {
        MissivesTakenWorldData data = of(world);
        if (data == null || missive == null) {
            return false;
        }
        boolean taken = data.take(missive.getQuestId(),
                missive.getGenerationWorldTime(), world.getTotalWorldTime(),
                keepTicks(LostTalesConfig.expireMissiveBoardNotices));
        if (!taken && data.isUsable()
                && data.size() >= MissivesTakenWorldData.MAX_ENTRIES
                && data.firstRefusal()) {
            LostTalesLog.warning("The record of missives taken in this world holds"
                    + " %s already; no missive can be accepted until older ones"
                    + " are forgotten", Integer.valueOf(MissivesTakenWorldData.MAX_ENTRIES));
        }
        return taken;
    }

    /** The overworld's record, made on first use; null off the server. */
    static MissivesTakenWorldData of(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage;
        try {
            storage = LostTalesDimensionHelper.overworld(world).mapStorage;
        } catch (IllegalStateException noOverworld) {
            return null;
        }
        if (storage == null) {
            return null;
        }
        MissivesTakenWorldData data = (MissivesTakenWorldData) storage.loadData(
                MissivesTakenWorldData.class, MissivesTakenWorldData.DATA_NAME);
        if (data == null) {
            data = new MissivesTakenWorldData(MissivesTakenWorldData.DATA_NAME);
            storage.setData(MissivesTakenWorldData.DATA_NAME, data);
        }
        return data;
    }
}
