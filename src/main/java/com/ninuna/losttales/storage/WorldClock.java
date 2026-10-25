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
 * looked at as the server starts and once a minute while it runs. A time
 * more than a day past it is a jump: it is logged, and the stretch it
 * spans is held. What would destroy data and falls due inside it waits
 * until an operator accepts the clock ({@code /losttales clock accept}):
 * a deleted character's purge, a chat audit file's removal, a last-seen
 * time forgotten. A deleted character held so can still be restored.
 *
 * <p>The world cannot tell a clock set forward from a server stopped for
 * more than a day; both hold until accepted. A second jump before the
 * first is accepted widens the one stretch held to cover both. A clock
 * set back moves nothing: the latest time seen stays.</p>
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
     * jump, and keeps the store for {@link #hold()}. As the server starts,
     * before anything that would let data expire, and once a minute.
     */
    public static void observe(MinecraftServer server) {
        Store store = store(server);
        if (store == null) {
            return;
        }
        current = store;
        Jump jump = store.observe(System.currentTimeMillis());
        if (jump != null) {
            LostTalesLog.warning("The server's clock reads %s, %s after the last time this"
                    + " world saw (%s). Whatever this brings past its time that would"
                    + " destroy data waits: deleted characters are not purged, and chat"
                    + " audit files and last-seen times are kept, until an operator runs"
                    + " /losttales clock accept.",
                    format(jump.getTo()), describe(jump.getTo() - jump.getFrom()),
                    format(jump.getFrom()));
        }
    }

    /** The hold of the running server's world; {@link #NONE} with no server. */
    public static Hold hold() {
        Store store = current;
        return store == null ? NONE : store;
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

    /** A jump the clock made: from the last time seen to the time it read. */
    public static final class Jump {
        private final long from;
        private final long to;

        Jump(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public long getFrom() {
            return this.from;
        }

        public long getTo() {
            return this.to;
        }
    }

    /**
     * The world's last time seen and the stretch a jump holds, in real
     * milliseconds. Data of another version, or of a shape this build
     * does not write, is kept as it was, and then everything holds: a
     * clock that cannot be read lets nothing expire that destroys data.
     */
    public static final class Store extends WorldSavedData implements Hold {
        public static final String DATA_NAME = "losttales_world_clock";
        static final int DATA_VERSION = 1;
        static final String TAG_VERSION = "DataVersion";
        static final String TAG_LAST_SEEN = "LastSeen";
        static final String TAG_HELD_FROM = "HeldFrom";
        static final String TAG_HELD_TO = "HeldTo";

        private long lastSeen;
        private long heldFrom;
        private long heldTo;
        private NBTTagCompound preserved;
        private boolean warned;

        public Store(String name) {
            super(name);
        }

        @Override
        public synchronized void readFromNBT(NBTTagCompound compound) {
            this.lastSeen = 0L;
            this.heldFrom = 0L;
            this.heldTo = 0L;
            this.preserved = null;
            this.warned = false;
            if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                    || !compound.hasKey(TAG_LAST_SEEN, Constants.NBT.TAG_LONG)
                    || !compound.hasKey(TAG_HELD_FROM, Constants.NBT.TAG_LONG)
                    || !compound.hasKey(TAG_HELD_TO, Constants.NBT.TAG_LONG)) {
                this.preserved = (NBTTagCompound)compound.copy();
                return;
            }
            long seen = compound.getLong(TAG_LAST_SEEN);
            long from = compound.getLong(TAG_HELD_FROM);
            long to = compound.getLong(TAG_HELD_TO);
            boolean holding = from != 0L || to != 0L;
            if (seen < 0L || holding && (from <= 0L || to <= from)) {
                this.preserved = (NBTTagCompound)compound.copy();
                return;
            }
            this.lastSeen = seen;
            this.heldFrom = from;
            this.heldTo = to;
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
        }

        /**
         * Looks at the clock reading {@code now}: a time more than a day
         * past the last one seen is a jump, and the stretch it spans is
         * held. The last time seen moves forward only. Answers the jump,
         * or null.
         */
        public synchronized Jump observe(long now) {
            if (this.preserved != null) {
                if (!this.warned) {
                    this.warned = true;
                    LostTalesLog.warning("The world's clock record cannot be read; nothing"
                            + " that would destroy data expires until it can");
                }
                return null;
            }
            if (this.lastSeen <= 0L) {
                this.lastSeen = now;
                markDirty();
                return null;
            }
            Jump jump = null;
            if (now - this.lastSeen > JUMP_MILLIS) {
                this.heldFrom = this.heldTo == 0L ? this.lastSeen
                        : Math.min(this.heldFrom, this.lastSeen);
                this.heldTo = Math.max(this.heldTo, now);
                jump = new Jump(this.lastSeen, now);
            }
            if (now > this.lastSeen) {
                this.lastSeen = now;
                markDirty();
            }
            return jump;
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

        /** Whether the record is kept as it was, unread. */
        public synchronized boolean isPreserved() {
            return this.preserved != null;
        }

        /**
         * An operator's word that the clock is right: nothing is held any
         * more. False, and nothing changes, while the record cannot be read.
         */
        public synchronized boolean accept(long now) {
            if (this.preserved != null) {
                return false;
            }
            this.heldFrom = 0L;
            this.heldTo = 0L;
            this.lastSeen = Math.max(this.lastSeen, now);
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
    }
}
