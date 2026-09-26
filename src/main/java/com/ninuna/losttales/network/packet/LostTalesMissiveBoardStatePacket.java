package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A missive board's notices as the server holds them, for the board's
 * page: sent as the player uses the board, which opens the page on it,
 * as the answer to every request the page sends, and whenever the board
 * changes while the player stands at it, saying which
 * ({@link #getReason}). A notice whose letter is too large to send
 * travels as one that cannot be read.
 */
public final class LostTalesMissiveBoardStatePacket implements IMessage {
    /** The most notices a board holds: its nine slots. */
    public static final int MAX_NOTICES = 9;
    /** A notice's slot, time left and whether its letter follows. */
    private static final int NOTICE_HEAD_BYTES = 1 + 8 + 1;
    private static final int HEAD_BYTES = 4 * 4 + 1 + 1 + 1;
    public static final int MAX_PACKET_BYTES = HEAD_BYTES + MAX_NOTICES
            * (NOTICE_HEAD_BYTES + LostTalesMissiveCodec.MAX_MISSIVE_BYTES);

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private MissiveBoardStateReason reason;
    private int maxNotices;
    private List<MissiveNotice> notices = Collections.emptyList();
    private boolean malformed;

    public LostTalesMissiveBoardStatePacket() {}

    /**
     * The notices of the board at {@code x}/{@code y}/{@code z} of
     * {@code dimensionId}, which posts up to {@code maxNotices}, slot by
     * slot; a letter that does not {@link LostTalesMissiveCodec#fits fit}
     * goes as one that cannot be read.
     */
    public LostTalesMissiveBoardStatePacket(int dimensionId, int x, int y,
                                            int z,
                                            MissiveBoardStateReason reason,
                                            int maxNotices,
                                            List<MissiveNotice> notices) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.reason = reason;
        this.maxNotices = maxNotices;
        List<MissiveNotice> sent = new ArrayList<MissiveNotice>();
        if (notices != null) {
            for (MissiveNotice notice : notices) {
                if (notice == null) {
                    throw new IllegalArgumentException("missing notice");
                }
                LostTalesMissiveData missive = notice.getMissive();
                sent.add(missive == null || LostTalesMissiveCodec.fits(missive)
                        ? notice : new MissiveNotice(notice.getSlot(),
                                notice.getTicksLeft(), null));
            }
        }
        this.notices = Collections.unmodifiableList(sent);
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive board state packet size");
            }
            this.dimensionId = readInt(buffer);
            this.x = readInt(buffer);
            this.y = readInt(buffer);
            this.z = readInt(buffer);
            this.reason = MissiveBoardStateReason.fromNetworkId(
                    LostTalesMissiveCodec.readSmallCount(buffer, 255,
                            "reason"));
            this.maxNotices = LostTalesMissiveCodec.readSmallCount(buffer,
                    MAX_NOTICES, "notice limit");
            int count = LostTalesMissiveCodec.readSmallCount(buffer,
                    MAX_NOTICES, "notice");
            List<MissiveNotice> read = new ArrayList<MissiveNotice>(count);
            for (int index = 0; index < count; index++) {
                int slot = LostTalesMissiveCodec.readSmallCount(buffer,
                        MAX_NOTICES - 1, "slot");
                long ticksLeft = LostTalesMissiveCodec.readLong(buffer);
                if (ticksLeft < MissiveNotice.STAYS_UP) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid notice time");
                }
                LostTalesMissiveData missive =
                        LostTalesMissiveCodec.readBoolean(buffer)
                                ? LostTalesMissiveCodec.read(buffer) : null;
                read.add(new MissiveNotice(slot, ticksLeft, missive));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.notices = Collections.unmodifiableList(read);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.notices = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.reason.getNetworkId());
        buffer.writeByte(this.maxNotices);
        buffer.writeByte(this.notices.size());
        for (MissiveNotice notice : this.notices) {
            buffer.writeByte(notice.getSlot());
            buffer.writeLong(notice.getTicksLeft());
            buffer.writeBoolean(notice.isReadable());
            if (notice.isReadable()) {
                LostTalesMissiveCodec.write(buffer, notice.getMissive());
            }
        }
    }

    /**
     * A board where a block may stand, a reason, a limit within the
     * board's slots, and each notice in a slot of its own, in slot order.
     */
    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z)
                || this.reason == null || this.maxNotices < 0
                || this.maxNotices > MAX_NOTICES || this.notices == null
                || this.notices.size() > MAX_NOTICES) {
            throw new IllegalArgumentException("invalid missive board state");
        }
        int lastSlot = -1;
        for (MissiveNotice notice : this.notices) {
            if (notice.getSlot() <= lastSlot
                    || notice.getSlot() >= MAX_NOTICES) {
                throw new IllegalArgumentException(
                        "invalid missive board slot");
            }
            lastSlot = notice.getSlot();
        }
    }

    private static int readInt(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            throw new LostTalesPacketCodec.DecodeException("truncated packet");
        }
        return buffer.readInt();
    }

    public int getDimensionId() { return this.dimensionId; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    /** Why it was sent: an opening, a change, or the answer to a request. */
    public MissiveBoardStateReason getReason() { return this.reason; }
    /** The most notices the board posts. */
    public int getMaxNotices() { return this.maxNotices; }
    /** The notices, in slot order. */
    public List<MissiveNotice> getNotices() { return this.notices; }
    /** Whether the player used the board, and the page opens on it. */
    public boolean isOpening() {
        return this.reason == MissiveBoardStateReason.OPENED;
    }
    public boolean isMalformed() { return this.malformed; }

    /** Whether it is about the board at that place. */
    public boolean isAbout(int dimensionId, int x, int y, int z) {
        return this.dimensionId == dimensionId && this.x == x
                && this.y == y && this.z == z;
    }

    /**
     * What the page shows of the notices, free of how long each stays up:
     * the same board posting the same letters in the same slots gives the
     * same number, so a watcher is sent the notices again only once they
     * changed.
     */
    public long getFingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, this.maxNotices);
        for (MissiveNotice notice : this.notices) {
            hash = mix(hash, notice.getSlot());
            hash = mix(hash, notice.isReadable() ? 1 : 0);
            String questId = notice.getQuestId();
            for (int index = 0; index < questId.length(); index++) {
                hash = mix(hash, questId.charAt(index));
            }
            hash = mix(hash, -1);
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        return (hash ^ (value & 0xFFFFFFFFL)) * 0x100000001b3L;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesMissiveBoardStatePacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesMissiveBoardStatePacket message,
                MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleMissiveBoardState(message);
                }
            });
            return null;
        }
    }
}
