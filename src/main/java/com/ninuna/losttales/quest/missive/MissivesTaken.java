package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

/**
 * Whether a missive may still be taken in a world, and the record that it
 * was ({@link MissivesTakenWorldData}, QUEST-34): once only, by anyone, and
 * only while its notice could still stand on a board. Server only.
 */
public final class MissivesTaken {
    private MissivesTaken() {}

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

    /** Records the missive as taken; answers whether the world now remembers it. */
    public static boolean record(World world, LostTalesMissiveData missive) {
        MissivesTakenWorldData data = of(world);
        return data != null && missive != null && data.take(missive.getQuestId(),
                missive.getGenerationWorldTime(), world.getTotalWorldTime(),
                LostTalesConfig.getMissiveBoardNoticeExpirationTicks());
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
