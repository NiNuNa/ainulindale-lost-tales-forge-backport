package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.util.ChunkCoordinates;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Where a LOTR miniquest was last seen, dimension and all.
 *
 * <p>{@code LOTRMiniQuest.getLastLocation()} answers only the
 * coordinates; the dimension beside them is the right half of the
 * private {@code lastLocation} pair. The field is resolved once and its
 * shape verified — a {@link Pair} whose halves are coordinates and a
 * number — rather than trusting the name, and a quest whose location
 * cannot be read simply has none, so a LOTR version that moved the field
 * costs a marker and nothing else.</p>
 */
public final class LotrMiniQuestLocationAccess {

    private static final Field LAST_LOCATION = findLastLocation();
    private static boolean failureLogged;

    private LotrMiniQuestLocationAccess() {}

    /** Whether a quest's dimension can be read at all. */
    public static boolean isAvailable() {
        return LAST_LOCATION != null;
    }

    /** Where the quest was last seen, or null for a quest with no place. */
    public static Location lastLocation(LOTRMiniQuest quest) {
        if (quest == null || LAST_LOCATION == null) {
            return null;
        }
        Object value;
        try {
            value = LAST_LOCATION.get(quest);
        } catch (IllegalAccessException unreadable) {
            logFailure(unreadable);
            return null;
        } catch (RuntimeException unreadable) {
            logFailure(unreadable);
            return null;
        }
        if (!(value instanceof Pair)) {
            return null;
        }
        Object where = ((Pair<?, ?>)value).getLeft();
        Object dimension = ((Pair<?, ?>)value).getRight();
        if (!(where instanceof ChunkCoordinates) || !(dimension instanceof Number)) {
            return null;
        }
        return new Location((ChunkCoordinates)where,
                ((Number)dimension).intValue());
    }

    /** A place in a world: the block and the dimension it is in. */
    public static final class Location {
        private final ChunkCoordinates coordinates;
        private final int dimensionId;

        Location(ChunkCoordinates coordinates, int dimensionId) {
            this.coordinates = coordinates;
            this.dimensionId = dimensionId;
        }

        public ChunkCoordinates getCoordinates() { return this.coordinates; }
        public int getDimensionId() { return this.dimensionId; }
    }

    private static Field findLastLocation() {
        try {
            Field field = LOTRMiniQuest.class.getDeclaredField("lastLocation");
            if (field.getType() != Pair.class) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            return null;
        } catch (SecurityException refused) {
            return null;
        }
    }

    private static synchronized void logFailure(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        FMLLog.warning("[%s] A LOTR quest's last location could not be read "
                + "(LOTRMiniQuest.lastLocation), so its quests carry no map "
                + "marker (%s)", LostTalesMetaData.MOD_ID,
                failure == null ? "no such field" : failure.toString());
    }
}
