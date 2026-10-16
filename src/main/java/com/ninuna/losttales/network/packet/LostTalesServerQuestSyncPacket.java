package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.ServerQuestFiles;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Quests a server wrote in its own folder ({@link ServerQuestFiles}),
 * which a client has no file for: sent as the client comes to need them,
 * added to what it holds, and after a reload in place of what it holds
 * ({@link com.ninuna.losttales.quest.ServerQuestSync}). A long list goes
 * in several packets, each under {@link #CHUNK_BYTES}; where the list
 * replaces the client's, its first packet says so. A packet that cannot
 * be read changes nothing.
 */
public final class LostTalesServerQuestSyncPacket implements IMessage {
    static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    /** What one packet is filled to before the next one starts. */
    static final int CHUNK_BYTES = 1024 * 1024;

    private final List<LostTalesQuestDefinition> quests =
            new ArrayList<LostTalesQuestDefinition>();
    private boolean replaces;
    private boolean malformed;

    public LostTalesServerQuestSyncPacket() {}

    private LostTalesServerQuestSyncPacket(boolean replaces,
                                           List<LostTalesQuestDefinition> quests) {
        this.replaces = replaces;
        this.quests.addAll(quests);
    }

    /**
     * The packets that carry every one of {@code quests}, each under
     * {@link #CHUNK_BYTES}, added to what the client holds or, where
     * {@code replace} says so, in place of it: then there is always one,
     * so an empty list still clears what a client held. A quest too large
     * for one packet alone is left out.
     */
    public static List<LostTalesServerQuestSyncPacket> packetsFor(
            Collection<LostTalesQuestDefinition> quests, boolean replace) {
        List<LostTalesServerQuestSyncPacket> packets =
                new ArrayList<LostTalesServerQuestSyncPacket>();
        List<LostTalesQuestDefinition> chunk =
                new ArrayList<LostTalesQuestDefinition>();
        int bytes = 0;
        if (quests != null) {
            for (LostTalesQuestDefinition quest : quests) {
                int size = LostTalesQuestDefinitionCodec.encodedSize(quest);
                if (size < 0 || size > CHUNK_BYTES) {
                    continue;
                }
                if (bytes + size > CHUNK_BYTES && !chunk.isEmpty()) {
                    packets.add(new LostTalesServerQuestSyncPacket(
                            replace && packets.isEmpty(), chunk));
                    chunk = new ArrayList<LostTalesQuestDefinition>();
                    bytes = 0;
                }
                chunk.add(quest);
                bytes += size;
            }
        }
        if (!chunk.isEmpty() || replace && packets.isEmpty()) {
            packets.add(new LostTalesServerQuestSyncPacket(
                    replace && packets.isEmpty(), chunk));
        }
        return packets;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.quests.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid packet size");
            }
            this.replaces = buf.readBoolean();
            int count = LostTalesPacketCodec.readCount(buf,
                    ServerQuestFiles.MAX_FILES, "server quest");
            for (int i = 0; i < count; i++) {
                this.quests.add(LostTalesQuestDefinitionCodec.read(buf));
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException malformedPayload) {
            this.quests.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.replaces);
        LostTalesPacketCodec.writeCount(buf, this.quests.size(),
                ServerQuestFiles.MAX_FILES, "server quest");
        for (LostTalesQuestDefinition quest : this.quests) {
            LostTalesQuestDefinitionCodec.write(buf, quest);
        }
    }

    /** Whether the client starts its list of server quests afresh with this packet. */
    public boolean replaces() {
        return this.replaces;
    }

    public List<LostTalesQuestDefinition> getQuests() {
        return Collections.unmodifiableList(this.quests);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Hands the quests to the client thread; nothing of a malformed packet goes on. */
    public static final class Handler
            implements IMessageHandler<LostTalesServerQuestSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerQuestSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleServerQuestSync(message);
                }
            });
            return null;
        }
    }
}
