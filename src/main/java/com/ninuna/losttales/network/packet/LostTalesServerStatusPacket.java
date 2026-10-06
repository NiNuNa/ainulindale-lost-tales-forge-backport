package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatStatusLine;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: the server's status, which the Server's status line in
 * every member list and its card show. How many players show as online
 * (an Invisible one is not counted) against the cap, 0 for none; the
 * address players join by, empty while the server names none; the ticks
 * it runs a second; and how long it has taken players, 0 before it has.
 * Sent to everyone as any of the first three changes, and to a player as
 * they join; the client counts the time up on from there itself. A payload
 * of any other shape is refused whole.
 */
public final class LostTalesServerStatusPacket implements IMessage {
    /** Far past any server's player count. */
    public static final int MAX_PLAYERS = 1000000;
    public static final int MAX_TICKS_PER_SECOND = 20;
    /** The address as a status line holds it. */
    public static final int MAX_ADDRESS_BYTES = ChatStatusLine.MAX_BYTES;
    private static final int MAX_PACKET_BYTES = 4 + 4 + 5 + MAX_ADDRESS_BYTES
            + 1 + 8;

    private int players;
    private int maxPlayers;
    private String address = "";
    private int ticksPerSecond;
    private long upMillis;
    private boolean malformed;

    public LostTalesServerStatusPacket() {}

    public LostTalesServerStatusPacket(int players, int maxPlayers,
                                       String address, int ticksPerSecond,
                                       long upMillis) {
        this.players = players;
        this.maxPlayers = maxPlayers;
        this.address = ChatStatusLine.clean(address);
        this.ticksPerSecond = ticksPerSecond;
        this.upMillis = upMillis;
        validate();
    }

    private void validate() {
        if (this.players < 0 || this.players > MAX_PLAYERS
                || this.maxPlayers < 0 || this.maxPlayers > MAX_PLAYERS
                || this.ticksPerSecond < 0
                || this.ticksPerSecond > MAX_TICKS_PER_SECOND
                || this.upMillis < 0L || this.address == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.address,
                        MAX_ADDRESS_BYTES)) {
            throw new LostTalesPacketCodec.DecodeException("invalid server status");
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            this.players = buffer.readInt();
            this.maxPlayers = buffer.readInt();
            this.address = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_ADDRESS_BYTES);
            this.ticksPerSecond = buffer.readUnsignedByte();
            this.upMillis = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.players = 0;
            this.maxPlayers = 0;
            this.address = "";
            this.ticksPerSecond = 0;
            this.upMillis = 0L;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.players);
        buffer.writeInt(this.maxPlayers);
        LostTalesPacketCodec.writeUtf8String(buffer, this.address,
                MAX_ADDRESS_BYTES);
        buffer.writeByte(this.ticksPerSecond);
        buffer.writeLong(this.upMillis);
    }

    public int getPlayers() {
        return this.players;
    }

    /** The cap; 0 for none. */
    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    /** The address players join by; empty while the server names none. */
    public String getAddress() {
        return this.address;
    }

    public int getTicksPerSecond() {
        return this.ticksPerSecond;
    }

    /** How long the server had taken players as this was sent; 0 before it had. */
    public long getUpMillis() {
        return this.upMillis;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesServerStatusPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerStatusPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleServerStatus(message);
                }
            });
            return null;
        }
    }
}
