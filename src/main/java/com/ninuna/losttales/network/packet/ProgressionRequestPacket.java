package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.statue.ResetStatueService;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * What a player asks of their own growth: to take a perk's next rank on
 * the Skills page (PROG-06), or to reset one skill's perks at the statue
 * they stand at (PROG-12). The server trusts neither id nor place: it
 * checks the played character, the perk's needs and points, the statue's
 * block, the player's reach and the price itself.
 */
public final class ProgressionRequestPacket implements IMessage {
    public static final int TAKE_PERK = 0;
    public static final int RESET_AT_STATUE = 1;
    private static final int MAX_PACKET_BYTES = 1024;

    private int action;
    private String skillId = "";
    private String perkId = "";
    private int x;
    private int y;
    private int z;
    private boolean malformed;

    public ProgressionRequestPacket() {}

    public static ProgressionRequestPacket takePerk(String skillId, String perkId) {
        ProgressionRequestPacket packet = new ProgressionRequestPacket();
        packet.action = TAKE_PERK;
        packet.skillId = skillId == null ? "" : skillId;
        packet.perkId = perkId == null ? "" : perkId;
        packet.validate();
        return packet;
    }

    public static ProgressionRequestPacket resetAtStatue(int x, int y, int z, String skillId) {
        ProgressionRequestPacket packet = new ProgressionRequestPacket();
        packet.action = RESET_AT_STATUE;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.skillId = skillId == null ? "" : skillId;
        packet.validate();
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            this.action = buf.readUnsignedByte();
            if (this.action == TAKE_PERK) {
                this.skillId = ProgressionCodec.readId(buf);
                this.perkId = ProgressionCodec.readId(buf);
            } else if (this.action == RESET_AT_STATUE) {
                this.x = buf.readInt();
                this.y = buf.readInt();
                this.z = buf.readInt();
                this.skillId = ProgressionCodec.readId(buf);
            } else {
                throw new LostTalesPacketCodec.DecodeException("an unknown action");
            }
            LostTalesPacketCodec.requireFinished(buf);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        validate();
        buf.writeByte(this.action);
        if (this.action == TAKE_PERK) {
            ProgressionCodec.writeId(buf, this.skillId);
            ProgressionCodec.writeId(buf, this.perkId);
        } else {
            buf.writeInt(this.x);
            buf.writeInt(this.y);
            buf.writeInt(this.z);
            ProgressionCodec.writeId(buf, this.skillId);
        }
    }

    private void validate() {
        if (this.action != TAKE_PERK && this.action != RESET_AT_STATUE) {
            throw new IllegalArgumentException("an unknown progression action");
        }
        if (!LostTalesPacketCodec.isUtf8WithinLimit(this.skillId, ProgressionCodec.MAX_ID_BYTES)
                || this.skillId.length() == 0) {
            throw new IllegalArgumentException("a progression request names no skill");
        }
        if (this.action == TAKE_PERK && (this.perkId.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.perkId, ProgressionCodec.MAX_ID_BYTES))) {
            throw new IllegalArgumentException("a perk request names no perk");
        }
        if (this.action == RESET_AT_STATUE
                && !LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z)) {
            throw new IllegalArgumentException("a statue outside the world");
        }
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<ProgressionRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final ProgressionRequestPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.PROGRESSION_REQUEST,
                    message.isMalformed(),
                    "ProgressionRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            if (message.action == TAKE_PERK) {
                                ProgressionService.takePerk(serverPlayer,
                                        message.skillId, message.perkId);
                            } else {
                                ResetStatueService.reset(serverPlayer,
                                        message.x, message.y, message.z, message.skillId);
                            }
                        }
                    });
            return null;
        }
    }
}
