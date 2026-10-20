package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.progression.CharacterLevelRule;
import com.ninuna.losttales.progression.ProgressionSnapshot;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillCurve;
import com.ninuna.losttales.progression.SkillStanding;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The played character's growth, whole each time (PROG-13): its skills
 * with what each needs next, its character level, the perk points left,
 * the perks taken and its deeds. A packet that cannot be read changes
 * nothing.
 */
public final class ProgressionStatePacket implements IMessage {
    static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;

    private ProgressionSnapshot snapshot;
    private boolean malformed;

    public ProgressionStatePacket() {}

    public ProgressionStatePacket(ProgressionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("a progression snapshot is needed");
        }
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.snapshot = null;
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            ProgressionState state = new ProgressionState();
            Map<String, Double> needed = new LinkedHashMap<String, Double>();
            int skills = ProgressionCodec.readCount(buf, ProgressionState.MAX_SKILLS, "skill");
            for (int index = 0; index < skills; index++) {
                String id = ProgressionCodec.readId(buf);
                int level = buf.readUnsignedByte();
                double into = ProgressionCodec.finite(buf.readDouble());
                double next = ProgressionCodec.finite(buf.readDouble());
                if (level < SkillCurve.FIRST_LEVEL || level > SkillCurve.TOP_LEVEL
                        || into < 0.0D || next < 0.0D || needed.containsKey(id)) {
                    throw new LostTalesPacketCodec.DecodeException("a skill out of shape");
                }
                state.setSkill(id, new SkillStanding(level, into));
                needed.put(id, Double.valueOf(next));
            }
            int characterLevel = buf.readInt();
            long characterInto = buf.readLong();
            long characterNeeded = buf.readLong();
            int points = buf.readInt();
            int resets = buf.readInt();
            if (characterLevel < 1 || characterLevel > CharacterLevelRule.MAX_LEVEL
                    || characterInto < 0L || characterNeeded < 0L || points < 0
                    || resets < 0 || resets > ProgressionState.MAX_RESETS) {
                throw new LostTalesPacketCodec.DecodeException("a character out of shape");
            }
            state.setCharacter(characterLevel, characterInto);
            state.setResets(resets);
            int perks = ProgressionCodec.readCount(buf, ProgressionState.MAX_PERKS, "perk");
            for (int index = 0; index < perks; index++) {
                String skill = ProgressionCodec.readId(buf);
                String perk = ProgressionCodec.readId(buf);
                int rank = buf.readUnsignedByte();
                if (rank < 1 || rank > ProgressionState.MAX_RANK
                        || skill.indexOf('/') >= 0 || state.perkRank(skill, perk) != 0) {
                    throw new LostTalesPacketCodec.DecodeException("a perk out of shape");
                }
                state.setPerkRank(skill, perk, rank);
            }
            int deeds = ProgressionCodec.readCount(buf, ProgressionState.MAX_DEEDS, "deed");
            for (int index = 0; index < deeds; index++) {
                if (!state.addDeed(ProgressionCodec.readId(buf))) {
                    throw new LostTalesPacketCodec.DecodeException("a deed sent twice");
                }
            }
            int counters = ProgressionCodec.readCount(buf,
                    ProgressionState.MAX_DEED_COUNTERS, "deed counter");
            for (int index = 0; index < counters; index++) {
                String id = ProgressionCodec.readId(buf);
                long count = buf.readLong();
                if (count < 1L || state.deedCounter(id) != 0L || state.hasDeed(id)) {
                    throw new LostTalesPacketCodec.DecodeException("a deed counter out of shape");
                }
                state.setDeedCounter(id, count);
            }
            LostTalesPacketCodec.requireFinished(buf);
            this.snapshot = new ProgressionSnapshot(state, needed, characterNeeded, points);
        } catch (RuntimeException exception) {
            this.snapshot = null;
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ProgressionState state = this.snapshot.view();
        ProgressionCodec.writeCount(buf, state.skills().size(), ProgressionState.MAX_SKILLS, "skill");
        for (Map.Entry<String, SkillStanding> skill : state.skills().entrySet()) {
            ProgressionCodec.writeId(buf, skill.getKey());
            buf.writeByte(skill.getValue().getLevel());
            buf.writeDouble(skill.getValue().getInto());
            buf.writeDouble(this.snapshot.skillNeeded(skill.getKey()));
        }
        buf.writeInt(state.getCharacterLevel());
        buf.writeLong(state.getCharacterInto());
        buf.writeLong(this.snapshot.getCharacterNeeded());
        buf.writeInt(this.snapshot.getAvailablePoints());
        buf.writeInt(state.getResets());
        ProgressionCodec.writeCount(buf, state.perks().size(), ProgressionState.MAX_PERKS, "perk");
        for (Map.Entry<String, Integer> perk : state.perks().entrySet()) {
            int slash = perk.getKey().indexOf('/');
            ProgressionCodec.writeId(buf, perk.getKey().substring(0, slash));
            ProgressionCodec.writeId(buf, perk.getKey().substring(slash + 1));
            buf.writeByte(perk.getValue().intValue());
        }
        ProgressionCodec.writeCount(buf, state.deeds().size(), ProgressionState.MAX_DEEDS, "deed");
        for (String deed : state.deeds()) {
            ProgressionCodec.writeId(buf, deed);
        }
        ProgressionCodec.writeCount(buf, state.deedCounters().size(),
                ProgressionState.MAX_DEED_COUNTERS, "deed counter");
        for (Map.Entry<String, Long> counter : state.deedCounters().entrySet()) {
            ProgressionCodec.writeId(buf, counter.getKey());
            buf.writeLong(counter.getValue().longValue());
        }
    }

    public ProgressionSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<ProgressionStatePacket, IMessage> {
        @Override
        public IMessage onMessage(final ProgressionStatePacket message, MessageContext ctx) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleProgressionState(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
