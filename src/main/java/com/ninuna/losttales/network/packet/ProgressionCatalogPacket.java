package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.deed.DeedSummary;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every skill and deed the server knows, as the Skills page shows them,
 * sent as a player joins and to everyone after a reload, in place of
 * what each client held (PROG-01, PROG-11, PROG-13). A packet that
 * cannot be read changes nothing.
 */
public final class ProgressionCatalogPacket implements IMessage {
    static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    /** The most deeds a catalog holds: every bundled file and every file of a server's own. */
    public static final int MAX_DEEDS = 2 * DataFiles.MAX_FILES;

    private final List<SkillDefinition> skills = new ArrayList<SkillDefinition>();
    private final List<DeedSummary> deeds = new ArrayList<DeedSummary>();
    private boolean malformed;

    public ProgressionCatalogPacket() {}

    public ProgressionCatalogPacket(List<SkillDefinition> skills, List<DeedSummary> deeds) {
        if (skills != null) {
            this.skills.addAll(skills);
        }
        if (deeds != null) {
            this.deeds.addAll(deeds);
        }
        if (this.skills.size() > ProgressionState.MAX_SKILLS || this.deeds.size() > MAX_DEEDS) {
            throw new IllegalArgumentException("too many skills or deeds");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.skills.clear();
        this.deeds.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            int count = ProgressionCodec.readCount(buf, ProgressionState.MAX_SKILLS, "skill");
            Set<String> ids = new HashSet<String>();
            for (int index = 0; index < count; index++) {
                SkillDefinition skill = ProgressionCodec.readSkill(buf);
                if (!ids.add(skill.getId())) {
                    throw new LostTalesPacketCodec.DecodeException("a skill sent twice");
                }
                this.skills.add(skill);
            }
            int deedCount = ProgressionCodec.readCount(buf, MAX_DEEDS, "deed");
            Set<String> deedIds = new HashSet<String>();
            for (int index = 0; index < deedCount; index++) {
                DeedSummary deed = ProgressionCodec.readDeed(buf);
                if (!deedIds.add(deed.getId())) {
                    throw new LostTalesPacketCodec.DecodeException("a deed sent twice");
                }
                this.deeds.add(deed);
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException exception) {
            this.skills.clear();
            this.deeds.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ProgressionCodec.writeCount(buf, this.skills.size(), ProgressionState.MAX_SKILLS, "skill");
        for (SkillDefinition skill : this.skills) {
            ProgressionCodec.writeSkill(buf, skill);
        }
        ProgressionCodec.writeCount(buf, this.deeds.size(), MAX_DEEDS, "deed");
        for (DeedSummary deed : this.deeds) {
            ProgressionCodec.writeDeed(buf, deed);
        }
    }

    public List<SkillDefinition> getSkills() {
        return Collections.unmodifiableList(this.skills);
    }

    public List<DeedSummary> getDeeds() {
        return Collections.unmodifiableList(this.deeds);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<ProgressionCatalogPacket, IMessage> {
        @Override
        public IMessage onMessage(final ProgressionCatalogPacket message, MessageContext ctx) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleProgressionCatalog(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
