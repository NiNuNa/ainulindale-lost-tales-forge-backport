package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatPresence;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-to-client: the presence of one or more accounts. One entry
 * when someone's presence changes, to everyone; the whole roster to a
 * player who has just joined. Online travels like any other status and
 * tells the client to forget the account's entry. A payload naming an
 * account twice, an unknown status or more entries than a server holds
 * players is refused whole.
 */
public final class LostTalesChatPresenceSyncPacket implements IMessage {
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_PACKET_BYTES = 2 + MAX_ENTRIES * 17;

    private Map<UUID, ChatPresence> entries = Collections.emptyMap();
    private boolean malformed;

    public LostTalesChatPresenceSyncPacket() {}

    public LostTalesChatPresenceSyncPacket(Map<UUID, ChatPresence> entries) {
        Map<UUID, ChatPresence> kept = new LinkedHashMap<UUID, ChatPresence>();
        if (entries != null) {
            for (Map.Entry<UUID, ChatPresence> entry : entries.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && kept.size() < MAX_ENTRIES) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.entries = Collections.unmodifiableMap(kept);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat presence sync size");
            }
            int count = buffer.readUnsignedShort();
            if (count > MAX_ENTRIES) {
                throw new LostTalesPacketCodec.DecodeException("too many presences");
            }
            Map<UUID, ChatPresence> read = new LinkedHashMap<UUID, ChatPresence>();
            for (int index = 0; index < count; index++) {
                UUID account = new UUID(buffer.readLong(), buffer.readLong());
                ChatPresence presence = ChatPresence.fromCode(buffer.readUnsignedByte());
                if (presence == null || read.containsKey(account)) {
                    throw new LostTalesPacketCodec.DecodeException("invalid presence entry");
                }
                read.put(account, presence);
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.entries = Collections.unmodifiableMap(read);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.entries = Collections.emptyMap();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeShort(this.entries.size());
        for (Map.Entry<UUID, ChatPresence> entry : this.entries.entrySet()) {
            buffer.writeLong(entry.getKey().getMostSignificantBits());
            buffer.writeLong(entry.getKey().getLeastSignificantBits());
            buffer.writeByte(entry.getValue().code());
        }
    }

    /** The presences stated, by account, in the order stated. */
    public Map<UUID, ChatPresence> getEntries() {
        return this.entries;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatPresenceSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatPresenceSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatPresence(message);
                }
            });
            return null;
        }
    }
}
