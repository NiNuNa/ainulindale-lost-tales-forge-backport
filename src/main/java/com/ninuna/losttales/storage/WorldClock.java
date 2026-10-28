package com.ninuna.losttales.storage;

import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * The last time this world saw on the server machine's clock (DATA-09).
 * Invitations, cooldowns, a deleted character's retention and timed
 * missives count time on that clock, so a clock set forward brings them
 * all due at once. The world remembers the latest time it has seen,
 * looked at as the server starts, once a minute while it runs, as it
 * stops, and each time something asks what is held ({@link #hold()}).
 *
 * <p>A clean stop writes a mark with its time ({@link #stopped}), and the
 * time from that stop to the next start counts as time passed. A time
 * more than a day past the latest one seen, while the server runs or
 * after a stop that left no mark (a crash), is a jump: it is logged, and
 * the stretch it spans is held. What would destroy data and falls due
 * inside it waits until an operator accepts the clock
 * ({@code /losttales clock accept}): a deleted character's purge, a chat
 * audit file's removal, a last-seen time forgotten. A deleted character
 * held so can still be restored. A clock set forward while the server is
 * stopped cleanly goes unseen. A second jump before the first is
 * accepted widens the one stretch held to cover both.</p>
 *
 * <p>A clock more than a day behind the latest time seen is logged once
 * a run and offered for accept, which sets the world's latest time to
 * the clock's reading (DATA-15); until then the latest time stays.</p>
 *
 * <p>The store of the running server is kept here for the guards that
 * ask from anywhere ({@link #hold()}); {@link #reset()} forgets it as the
 * server starts and stops.</p>
 */
public final class WorldClock {

    /** How far past the last time seen the clock may read before it is a jump. */
    public static final long JUMP_MILLIS = 24L * 60L * 60L * 1000L;
    /** How often the running server looks at the clock: once a minute. */
    static final long CHECK_EVERY_TICKS = 1200L;

    /** Whatever falls due holds back while this says so. */
    public interface Hold {
        /** Whether something that falls due at {@code dueAt} waits for an operator. */
        boolean holds(long dueAt);
    }

    /** The hold of a world that holds nothing. */
    public static final Hold NONE = new Hold() {
        @Override
        public boolean holds(long dueAt) {
            return false;
        }
    };

    private static volatile Store current;
    private static long ticks;

    /** Looks at the clock once a minute while the server runs. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks < CHECK_EVERY_TICKS) {
            return;
        }
        ticks = 0L;
        observe(MinecraftServer.getServer());
    }

    /** Forgets the running server's store; as the server starts and as it stops. */
    public static void reset() {
        current = null;
        ticks = 0L;
    }

    /**
     * Looks at the clock against the world's last time seen, logging a
     * jump or a clock behind it, and keeps the store for {@link #hold()}.
     * As the server starts, before anything that would let data expire,
     * and once a minute. The first look of a run takes the clean stop's
     * mark, and the save is written at once, so a crash before the next
     * autosave finds no mark.
     */
    public static void observe(MinecraftServer server) {
        World world = server == null ? null : server.worldServerForDimension(0);
        Store store = store(world);
        if (store == null) {
            return;
        }
        current = store;
        Look look = store.observe(System.currentTimeMillis());
        report(look);
        if (look.getCleanStop() != 0L) {
            world.mapStorage.saveAllData();
        }
    }

    /**
     * A clean stop: the clock is looked at once more and the stop is
     * marked with its time, for the save the worlds are written with
     * after the stopping event.
     */
    public static void stopped(MinecraftServer server) {
        Store store = store(server);
        if (store == null) {
            return;
        }
        current = store;
        report(store.stop(System.currentTimeMillis()));
    }

    /**
     * The hold of the running server's world, after a look at the clock;
     * {@link #NONE} with no server.
     */
    public static Hold hold() {
        Store store = current;
        return store == null ? NONE : hold(store, System.currentTimeMillis());
    }

    /**
     * The store's hold once it has looked at the clock reading {@code now}:
     * a jump since the last look is held before the caller asks.
     */
    static Hold hold(Store store, long now) {
        report(store.observe(now));
        return store;
    }

    private static void report(Look look) {
        if (look.isJump()) {
            LostTalesLog.warning("The server's clock reads %s, %s after the last time this"
                    + " world saw (%s), with the server running or after a stop that left"
                    + " no mark. Whatever this brings past its time that would destroy"
                    + " data waits: deleted characters are not purged, and chat audit"
                    + " files and last-seen times are kept, until an operator runs"
                    + " /losttales clock accept.",
                    format(look.getTo()), describe(look.getTo() - look.getFrom()),
                    format(look.getFrom()));
        }
        if (look.isNewlyBehind()) {
            LostTalesLog.warning("The server's clock reads %s, %s behind the latest time this"
                    + " world saw (%s). Nothing expires early, and a jump forward is"
                    + " measured from that latest time. If the clock is right, an operator"
                    + " runs /losttales clock accept to set the world's time to it.",
                    format(look.getTo()), describe(look.getFrom() - look.getTo()),
                    format(look.getFrom()));
        }
    }

    /** The clock's store of the world the server runs; null with no world. */
    public static Store store(MinecraftServer server) {
        return server == null ? null : store(server.worldServerForDimension(0));
    }

    /** The clock's store, in dimension 0's map storage; null with no world. */
    public static Store store(World world) {
        MapStorage storage = world == null ? null : world.mapStorage;
        if (storage == null) {
            return null;
        }
        Store store = (Store)storage.loadData(Store.class, Store.DATA_NAME);
        if (store == null) {
            store = new Store(Store.DATA_NAME);
            storage.setData(Store.DATA_NAME, store);
        }
        return store;
    }

    /** A time as the log writes it, in UTC. */
    public static String format(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    /** A stretch of time in whole days and hours, as the log writes it. */
    static String describe(long millis) {
        long hours = Math.max(0L, millis) / (60L * 60L * 1000L);
        return (hours / 24L) + " days " + (hours % 24L) + " hours";
    }

    /**
     * One look at the clock: the latest time seen before it, the time
     * the clock read, and what the look found.
     */
    public static final class Look {
        static final Look NOTHING = new Look(0L, 0L, false, 0L, false);

        private final long from;
        private final long to;
        private final boolean jump;
        private final long cleanStop;
        private final boolean newlyBehind;

        Look(long from, long to, boolean jump, long cleanStop, boolean newlyBehind) {
            this.from = from;
            this.to = to;
            this.jump = jump;
            this.cleanStop = cleanStop;
            this.newlyBehind = newlyBehind;
        }

        /** The latest time the world had seen before the look. */
        public long getFrom() {
            return this.from;
        }

        /** The time the clock read. */
        public long getTo() {
            return this.to;
        }

        /** Whether the clock read more than a day past the latest time seen, and that stretch is held. */
        public boolean isJump() {
            return this.jump;
        }

        /** The time of the clean stop whose mark this look took, the gap since counted as passed; 0 for none. */
        public long getCleanStop() {
            return this.cleanStop;
        }

        /** Whether this look is the run's first to find the clock more than a day behind. */
        public boolean isNewlyBehind() {
            return this.newlyBehind;
        }
    }

    /**
     * The world's last time seen, the stretch a jump holds and the clean
     * stop's mark, in real milliseconds. Data of another version, or of a
     * shape this build does not write, is kept as it was, and then
     * everything holds: a clock that cannot be read lets nothing expire
     * that destroys data.
     */
    public static final class Store extends WorldSavedData implements Hold {
        public static final String DATA_NAME = "losttales_world_clock";
        static final int DATA_VERSION = 2;
        static final String TAG_VERSION = "DataVersion";
        static final String TAG_LAST_SEEN = "LastSeen";
        static final String TAG_HELD_FROM = "HeldFrom";
        static final String TAG_HELD_TO = "HeldTo";
        static final String TAG_STOPPED_AT = "StoppedAt";

        private long lastSeen;
        private long heldFrom;
        private long heldTo;
        /** When the server last stopped cleanly; 0 while it runs, or after a stop that left no mark. */
        private long stoppedAt;
        private NBTTagCompound preserved;
        private boolean warned;
        /** Whether this run has taken its first look; the first takes the clean stop's mark. */
        private boolean started;
        /** Whether this run has said the clock reads behind; again once it has caught up. */
        private boolean behindSaid;

        public Store(String name) {
            super(name);
        }

        @Override
        public synchronized void readFromNBT(NBTTagCompound compound) {
            this.lastSeen = 0L;
            this.heldFrom = 0L;
            this.heldTo = 0L;
            this.stoppedAt = 0L;
            this.preserved = null;
            this.warned = false;
            this.started = false;
            this.behindSaid = false;
            if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                    || !compound.hasKey(TAG_LAST_SEEN, Constants.NBT.TAG_LONG)
                    || !compound.hasKey(TAG_HELD_FROM, Constants.NBT.TAG_LONG)
                    || !compound.hasKey(TAG_HELD_TO, Constants.NBT.TAG_LONG)
                    || !compound.hasKey(TAG_STOPPED_AT, Constants.NBT.TAG_LONG)) {
                this.preserved = (NBTTagCompound)compound.copy();
                return;
            }
            long seen = compound.getLong(TAG_LAST_SEEN);
            long from = compound.getLong(TAG_HELD_FROM);
            long to = compound.getLong(TAG_HELD_TO);
            long stopped = compound.getLong(TAG_STOPPED_AT);
            boolean holding = from != 0L || to != 0L;
            if (seen < 0L || stopped < 0L || holding && (from <= 0L || to <= from)) {
                this.preserved = (NBTTagCompound)compound.copy();
                return;
            }
            this.lastSeen = seen;
            this.heldFrom = from;
            this.heldTo = to;
            this.stoppedAt = stopped;
        }

        @Override
        public synchronized void writeToNBT(NBTTagCompound compound) {
            if (this.preserved != null) {
                NbtTags.copyContents(this.preserved, compound);
                return;
            }
            compound.setInteger(TAG_VERSION, DATA_VERSION);
            compound.setLong(TAG_LAST_SEEN, this.lastSeen);
            compound.setLong(TAG_HELD_FROM, this.heldFrom);
            compound.setLong(TAG_HELD_TO, this.heldTo);
            compound.setLong(TAG_STOPPED_AT, this.stoppedAt);
        }

        /**
         * Looks at the clock reading {@code now}. The run's first look
         * takes the clean stop's mark, if the stop left one, and the gap
         * since counts as time passed. Otherwise a time more than a day
         * past the last one seen is a jump, and the stretch it spans is
         * held. The last time seen moves forward only; a clock more than
         * a day behind it is said once a run.
         */
        public synchronized Look observe(long now) {
            if (this.preserved != null) {
                if (!this.warned) {
                    this.warned = true;
                    LostTalesLog.warning("The world's clock record cannot be read; nothing"
                            + " that would destroy data expires until it can");
                }
                return Look.NOTHING;
            }
            long cleanStop = 0L;
            if (!this.started) {
                this.started = true;
                cleanStop = this.stoppedAt;
                if (this.stoppedAt != 0L) {
                    this.stoppedAt = 0L;
                    markDirty();
                }
            }
            if (this.lastSeen <= 0L) {
                this.lastSeen = now;
                markDirty();
                return Look.NOTHING;
            }
            long from = this.lastSeen;
            boolean jump = cleanStop == 0L && now - this.lastSeen > JUMP_MILLIS;
            if (jump) {
                this.heldFrom = this.heldTo == 0L ? this.lastSeen
                        : Math.min(this.heldFrom, this.lastSeen);
                this.heldTo = Math.max(this.heldTo, now);
            }
            if (now > this.lastSeen) {
                this.lastSeen = now;
                markDirty();
            }
            boolean behind = isBehind(now);
            boolean newlyBehind = behind && !this.behindSaid;
            this.behindSaid = behind;
            return new Look(from, now, jump, cleanStop, newlyBehind);
        }

        /**
         * A clean stop: one more look at the clock reading {@code now},
         * then the mark with the stop's time. The looks after it in this
         * run leave the mark alone; the next run's first takes it.
         */
        public synchronized Look stop(long now) {
            Look look = observe(now);
            if (this.preserved == null) {
                this.stoppedAt = Math.max(1L, now);
                markDirty();
            }
            return look;
        }

        /** Whether something falling due at {@code dueAt} waits: inside the stretch held. */
        @Override
        public synchronized boolean holds(long dueAt) {
            return this.preserved != null
                    || this.heldTo != 0L && dueAt > this.heldFrom && dueAt <= this.heldTo;
        }

        /** Whether anything is held. */
        public synchronized boolean isHolding() {
            return this.preserved != null || this.heldTo != 0L;
        }

        /** Whether the clock reading {@code now} is more than a day behind the latest time seen. */
        public synchronized boolean isBehind(long now) {
            return this.preserved == null && this.lastSeen - now > JUMP_MILLIS;
        }

        /** Whether the record is kept as it was, unread. */
        public synchronized boolean isPreserved() {
            return this.preserved != null;
        }

        /**
         * An operator's word that the clock reading {@code now} is right:
         * nothing is held any more, and the world's latest time is the
         * clock's reading, behind it or not. False, and nothing changes,
         * while the record cannot be read.
         */
        public synchronized boolean accept(long now) {
            if (this.preserved != null) {
                return false;
            }
            this.heldFrom = 0L;
            this.heldTo = 0L;
            this.lastSeen = now;
            this.behindSaid = false;
            markDirty();
            return true;
        }

        /** The latest time the world has seen; 0 before the first look. */
        public synchronized long getLastSeen() {
            return this.lastSeen;
        }

        /** Where the stretch held begins; 0 for none. */
        public synchronized long getHeldFrom() {
            return this.heldFrom;
        }

        /** Where the stretch held ends; 0 for none. */
        public synchronized long getHeldTo() {
            return this.heldTo;
        }

        /** When the server last stopped cleanly, a mark the next run's first look takes; 0 for none. */
        public synchronized long getStoppedAt() {
            return this.stoppedAt;
        }
    }
}
