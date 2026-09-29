package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.world.WorldQuestNbtCodec;
import com.ninuna.losttales.quest.world.WorldQuestRun;
import com.ninuna.losttales.quest.world.WorldQuestView;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every world quest the world keeps, running or ended, as one player sees
 * them ({@link WorldQuestView}): sent as they join, once a second while
 * anything changed, and whenever they play another identity. The whole list
 * each time, so a client never has to piece it together; a packet that
 * cannot be read changes nothing.
 */
public final class LostTalesWorldQuestSyncPacket implements IMessage {
    static final int MAX_PACKET_BYTES = 64 * 1024;
    static final int MAX_ID_BYTES = 256;

    private final List<WorldQuestView> views = new ArrayList<WorldQuestView>();
    private boolean malformed;

    public LostTalesWorldQuestSyncPacket() {}

    private LostTalesWorldQuestSyncPacket(List<WorldQuestView> views) {
        this.views.addAll(views);
    }

    /** The runs as the identity {@code viewer} sees them. */
    public static LostTalesWorldQuestSyncPacket of(
            Collection<WorldQuestRun> runs, UUID viewer) {
        List<WorldQuestView> views = new ArrayList<WorldQuestView>();
        if (runs != null) {
            for (WorldQuestRun run : runs) {
                if (run != null && views.size() < WorldQuestNbtCodec.MAX_RUNS) {
                    views.add(WorldQuestView.of(run, viewer));
                }
            }
        }
        return new LostTalesWorldQuestSyncPacket(views);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.views.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid packet size");
            }
            int count = LostTalesPacketCodec.readCount(buf,
                    WorldQuestNbtCodec.MAX_RUNS, "world quest");
            for (int i = 0; i < count; i++) {
                String questId = LostTalesPacketCodec.readUtf8String(buf,
                        MAX_ID_BYTES);
                int state = buf.readUnsignedByte();
                long endsAt = buf.readLong();
                long endedAt = buf.readLong();
                int helpers = buf.readInt();
                int mine = buf.readInt();
                Map<String, Integer> counts =
                        new LinkedHashMap<String, Integer>();
                int countCount = LostTalesPacketCodec.readCount(buf,
                        WorldQuestNbtCodec.MAX_COUNTS, "world quest count");
                for (int j = 0; j < countCount; j++) {
                    String objective = LostTalesPacketCodec.readUtf8String(buf,
                            MAX_ID_BYTES);
                    int value = buf.readInt();
                    if (objective.length() == 0 || value < 0) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid world quest count");
                    }
                    counts.put(objective, Integer.valueOf(value));
                }
                if (questId.length() == 0
                        || state >= WorldQuestRun.State.values().length
                        || helpers < 0 || mine < 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid world quest");
                }
                this.views.add(new WorldQuestView(questId,
                        WorldQuestRun.State.values()[state], endsAt, endedAt,
                        counts, helpers, mine));
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException malformedPayload) {
            this.views.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        LostTalesPacketCodec.writeCount(buf, this.views.size(),
                WorldQuestNbtCodec.MAX_RUNS, "world quest");
        for (WorldQuestView view : this.views) {
            LostTalesPacketCodec.writeUtf8String(buf, view.getQuestId(),
                    MAX_ID_BYTES);
            buf.writeByte(view.getState().ordinal());
            buf.writeLong(view.getEndsAt());
            buf.writeLong(view.getEndedAt());
            buf.writeInt(view.getHelpers());
            buf.writeInt(view.getMine());
            LostTalesPacketCodec.writeCount(buf, view.getCounts().size(),
                    WorldQuestNbtCodec.MAX_COUNTS, "world quest count");
            for (Map.Entry<String, Integer> count
                    : view.getCounts().entrySet()) {
                LostTalesPacketCodec.writeUtf8String(buf, count.getKey(),
                        MAX_ID_BYTES);
                buf.writeInt(count.getValue().intValue());
            }
        }
    }

    public List<WorldQuestView> getViews() {
        return Collections.unmodifiableList(this.views);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Hands the runs to the client thread; nothing of a malformed packet goes on. */
    public static final class Handler
            implements IMessageHandler<LostTalesWorldQuestSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesWorldQuestSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleWorldQuestSync(message);
                }
            });
            return null;
        }
    }
}
