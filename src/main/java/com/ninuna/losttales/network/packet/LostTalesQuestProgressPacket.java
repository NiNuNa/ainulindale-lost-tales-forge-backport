package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The running quests whose counts moved, each as the whole log carries it,
 * sent in place of the whole log when nothing else in it changed: a kill,
 * a pickup or a craft costs one quest's counts, not the log. The client
 * lays them over the log it holds.
 */
public final class LostTalesQuestProgressPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;

    private final List<LostTalesQuestProgress> quests =
            new ArrayList<LostTalesQuestProgress>();
    private boolean malformed;

    public LostTalesQuestProgressPacket() {}

    public LostTalesQuestProgressPacket(Collection<LostTalesQuestProgress> quests) {
        if (quests != null) {
            for (LostTalesQuestProgress progress : quests) {
                if (progress != null) {
                    this.quests.add(progress.copy());
                }
            }
        }
        if (this.quests.size() > LostTalesQuestSyncPacket.MAX_ACTIVE_QUESTS) {
            throw new IllegalArgumentException("too many running quests");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.quests.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid quest-progress packet size");
            }
            int count = LostTalesPacketCodec.readCount(buf,
                    LostTalesQuestSyncPacket.MAX_ACTIVE_QUESTS, "running quest");
            for (int index = 0; index < count; index++) {
                this.quests.add(LostTalesQuestSyncPacket.readProgress(buf));
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException exception) {
            this.quests.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int startIndex = buf.writerIndex();
        LostTalesPacketCodec.writeCount(buf, this.quests.size(),
                LostTalesQuestSyncPacket.MAX_ACTIVE_QUESTS, "running quest");
        for (LostTalesQuestProgress progress : this.quests) {
            LostTalesQuestSyncPacket.writeProgress(buf, progress);
        }
        if (buf.writerIndex() - startIndex > MAX_PACKET_BYTES) {
            throw new IllegalStateException(
                    "quest progress exceeds packet limit");
        }
    }

    /** The running quests whose counts moved, as copies. */
    public List<LostTalesQuestProgress> getQuests() {
        List<LostTalesQuestProgress> copy =
                new ArrayList<LostTalesQuestProgress>(this.quests.size());
        for (LostTalesQuestProgress progress : this.quests) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesQuestProgressPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuestProgressPacket message,
                                  MessageContext ctx) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleQuestProgress(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
