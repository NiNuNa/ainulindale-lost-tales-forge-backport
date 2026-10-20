package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.progression.statue.ResetPrice;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A reset statue the player used (PROG-12): where it stands and what the
 * player's next reset there costs, coins and the item the server asks
 * for if any. Opens the statue's page; the server decides the rest when
 * the player picks a skill. A packet that cannot be read opens nothing.
 */
public final class ProgressionStatuePacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 1024;

    private int x;
    private int y;
    private int z;
    private int coins;
    private String item = "";
    private boolean malformed;

    public ProgressionStatuePacket() {}

    public ProgressionStatuePacket(int x, int y, int z, int coins, String item) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.coins = coins;
        this.item = item == null ? "" : item;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.coins = buf.readInt();
            this.item = LostTalesPacketCodec.readUtf8String(buf, ProgressionCodec.MAX_ID_BYTES);
            LostTalesPacketCodec.requireFinished(buf);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.coins);
        LostTalesPacketCodec.writeUtf8String(buf, this.item, ProgressionCodec.MAX_ID_BYTES);
    }

    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z)
                || this.coins < 0 || this.coins > ResetPrice.MOST) {
            throw new IllegalArgumentException("a statue out of shape");
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getCoins() {
        return this.coins;
    }

    /** The item each reset also takes, as the server names it; empty for none. */
    public String getItem() {
        return this.item;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<ProgressionStatuePacket, IMessage> {
        @Override
        public IMessage onMessage(final ProgressionStatuePacket message, MessageContext ctx) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleProgressionStatue(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
