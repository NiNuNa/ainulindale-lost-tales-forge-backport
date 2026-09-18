package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.server.ChatPresenceService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: this player's whole presence, stated again whenever
 * any of it changes — whether anybody has been at the keyboard lately,
 * and the status chosen for each identity that has one: the account and
 * any of its characters. An identity left out is Online. One byte of
 * flags, one byte of count, then per identity its kind, the character's
 * id for a character, and the status's code. Offline is never chosen,
 * an identity named twice or past {@link #MAX_CHOICES} is malformed, and
 * a malformed payload is discarded whole.
 */
public final class LostTalesChatPresencePacket implements IMessage {
    /** Choices one payload may carry: the account, nine characters and lore ones besides. */
    public static final int MAX_CHOICES = 64;
    static final int KIND_ACCOUNT = 0;
    static final int KIND_CHARACTER = 1;
    private static final int FLAG_IDLE = 1;
    private static final int MAX_PACKET_BYTES = 2 + MAX_CHOICES * 18;

    private boolean idle;
    private Map<ChatPresenceIdentity, ChatPresence> choices =
            Collections.emptyMap();
    private boolean malformed;

    public LostTalesChatPresencePacket() {}

    public LostTalesChatPresencePacket(boolean idle,
                                       Map<ChatPresenceIdentity, ChatPresence> choices) {
        this.idle = idle;
        Map<ChatPresenceIdentity, ChatPresence> kept =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        if (choices != null) {
            for (Map.Entry<ChatPresenceIdentity, ChatPresence> entry
                    : choices.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().isChoosable()
                        && kept.size() < MAX_CHOICES) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.choices = Collections.unmodifiableMap(kept);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat presence size");
            }
            int flags = buffer.readUnsignedByte();
            if ((flags & ~FLAG_IDLE) != 0) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat presence flags");
            }
            int count = buffer.readUnsignedByte();
            if (count > MAX_CHOICES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many presence choices");
            }
            Map<ChatPresenceIdentity, ChatPresence> read =
                    new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
            for (int index = 0; index < count; index++) {
                ChatPresenceIdentity identity = readIdentity(buffer);
                ChatPresence presence = ChatPresence.fromCode(
                        buffer.readUnsignedByte());
                if (presence == null || !presence.isChoosable()
                        || read.containsKey(identity)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid presence choice");
                }
                read.put(identity, presence);
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.idle = (flags & FLAG_IDLE) != 0;
            this.choices = Collections.unmodifiableMap(read);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.idle = false;
            this.choices = Collections.emptyMap();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.idle ? FLAG_IDLE : 0);
        buffer.writeByte(this.choices.size());
        for (Map.Entry<ChatPresenceIdentity, ChatPresence> entry
                : this.choices.entrySet()) {
            writeIdentity(buffer, entry.getKey());
            buffer.writeByte(entry.getValue().code());
        }
    }

    /** An identity as both presence packets write it: its kind, then a character's id. */
    static void writeIdentity(ByteBuf buffer, ChatPresenceIdentity identity) {
        if (identity.isAccount()) {
            buffer.writeByte(KIND_ACCOUNT);
            return;
        }
        buffer.writeByte(KIND_CHARACTER);
        buffer.writeLong(identity.getCharacterId().getMostSignificantBits());
        buffer.writeLong(identity.getCharacterId().getLeastSignificantBits());
    }

    /** The identity {@link #writeIdentity} wrote; an unknown kind is malformed. */
    static ChatPresenceIdentity readIdentity(ByteBuf buffer) {
        int kind = buffer.readUnsignedByte();
        if (kind == KIND_ACCOUNT) {
            return ChatPresenceIdentity.ACCOUNT;
        }
        if (kind != KIND_CHARACTER) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid presence identity");
        }
        return ChatPresenceIdentity.character(
                new UUID(buffer.readLong(), buffer.readLong()));
    }

    /** Whether nobody has been at the player's keyboard for a while. */
    public boolean isIdle() {
        return this.idle;
    }

    /** The statuses chosen, by identity, in the order stated. */
    public Map<ChatPresenceIdentity, ChatPresence> getChoices() {
        return this.choices;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatPresencePacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatPresencePacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_PRESENCE,
                    message.isMalformed(), "LostTalesChatPresencePacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP sender) {
                            ChatPresenceService.state(sender,
                                    message.getChoices(), message.isIdle());
                        }
                    });
            return null;
        }
    }
}
