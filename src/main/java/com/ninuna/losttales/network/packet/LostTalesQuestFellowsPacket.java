package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.QuestFellowCount;
import com.ninuna.losttales.quest.QuestFellows;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the members of the player's fellowships stand in the quests the
 * player runs too (QUEST-25), the whole list each time: each member's name,
 * whether past the player's stage, whether travelling with the player, and
 * the counts. The journal shows them under each objective. A list that
 * fails to read changes nothing.
 */
public final class LostTalesQuestFellowsPacket implements IMessage {
    private static final int MAX_NAME_BYTES = QuestFellowCount.MAX_NAME_CHARACTERS * 4;

    private final List<QuestFellowCount> counts = new ArrayList<QuestFellowCount>();
    private boolean malformed;

    public LostTalesQuestFellowsPacket() {}

    public LostTalesQuestFellowsPacket(List<QuestFellowCount> counts) {
        if (counts != null) {
            for (QuestFellowCount count : counts) {
                if (count != null) {
                    this.counts.add(count);
                }
            }
        }
        if (this.counts.size() > QuestFellows.MAX_COUNTS) {
            throw new IllegalArgumentException("too many fellow counts");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.counts.clear();
        this.malformed = false;
        try {
            int count = LostTalesPacketCodec.readCount(buf, QuestFellows.MAX_COUNTS,
                    "fellow count");
            for (int index = 0; index < count; index++) {
                String questId = LostTalesPacketCodec.readUtf8String(buf,
                        LostTalesQuestSyncPacket.MAX_IDENTIFIER_BYTES);
                String name = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
                if (questId.length() == 0 || name.length() == 0
                        || name.length() > QuestFellowCount.MAX_NAME_CHARACTERS) {
                    throw new LostTalesPacketCodec.DecodeException("invalid fellow count");
                }
                boolean past = buf.readBoolean();
                boolean travelling = buf.readBoolean();
                int objectives = LostTalesPacketCodec.readCount(buf,
                        past ? 0 : QuestFellowCount.MAX_OBJECTIVES, "fellow objective");
                Map<String, Integer> each = new LinkedHashMap<String, Integer>();
                for (int objective = 0; objective < objectives; objective++) {
                    String objectiveId = LostTalesPacketCodec.readUtf8String(buf,
                            LostTalesQuestSyncPacket.MAX_IDENTIFIER_BYTES);
                    int value = buf.readInt();
                    if (objectiveId.length() == 0 || value < 0
                            || each.put(objectiveId, Integer.valueOf(value)) != null) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid fellow objective");
                    }
                }
                this.counts.add(new QuestFellowCount(questId, name, past,
                        travelling, each));
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException exception) {
            this.counts.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        LostTalesPacketCodec.writeCount(buf, this.counts.size(), QuestFellows.MAX_COUNTS,
                "fellow count");
        for (QuestFellowCount count : this.counts) {
            LostTalesPacketCodec.writeUtf8String(buf, count.getQuestId(),
                    LostTalesQuestSyncPacket.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.writeUtf8String(buf, count.getName(), MAX_NAME_BYTES);
            buf.writeBoolean(count.isPast());
            buf.writeBoolean(count.isTravelling());
            LostTalesPacketCodec.writeCount(buf, count.getCounts().size(),
                    count.isPast() ? 0 : QuestFellowCount.MAX_OBJECTIVES,
                    "fellow objective");
            for (Map.Entry<String, Integer> objective : count.getCounts().entrySet()) {
                LostTalesPacketCodec.writeUtf8String(buf, objective.getKey(),
                        LostTalesQuestSyncPacket.MAX_IDENTIFIER_BYTES);
                buf.writeInt(objective.getValue().intValue());
            }
        }
    }

    public List<QuestFellowCount> getCounts() {
        return Collections.unmodifiableList(this.counts);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesQuestFellowsPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuestFellowsPacket message,
                                  MessageContext ctx) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleQuestFellows(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
