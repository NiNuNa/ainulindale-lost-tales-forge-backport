package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.quest.missive.MissiveBoardService;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * A missive board page's request to take a notice down into the
 * player's inventory, or to pin a letter from it onto the board. It names
 * the board by its world and place, the slot — the board's for a take,
 * the inventory's for a pin — and the quest id of the letter the page
 * read there, empty for a letter that cannot be read. The server checks
 * every part of it again ({@link MissiveBoardService}); accepting a
 * notice is {@link LostTalesMissiveAcceptPacket}'s.
 */
public final class LostTalesMissiveBoardRequestPacket implements IMessage {
    /** What the page asks for. */
    public enum Operation {
        /** The notice in a slot of the board, into the inventory. */
        TAKE(0),
        /** The letter in a slot of the inventory, onto the board. */
        PIN(1);

        private final int networkId;

        Operation(int networkId) {
            this.networkId = networkId;
        }

        static Operation fromNetworkId(int id) {
            for (Operation operation : values()) {
                if (operation.networkId == id) {
                    return operation;
                }
            }
            return null;
        }
    }

    public static final int MAX_PACKET_BYTES = 1024;

    private Operation operation;
    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private int slot;
    private String expectedQuestId = "";
    private boolean malformed;

    public LostTalesMissiveBoardRequestPacket() {}

    private LostTalesMissiveBoardRequestPacket(Operation operation,
                                               int dimensionId, int x, int y,
                                               int z, int slot,
                                               String expectedQuestId) {
        this.operation = operation;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.expectedQuestId = expectedQuestId == null ? "" : expectedQuestId;
        validate();
    }

    /** Takes the notice in the board's {@code slot}, the letter of {@code expectedQuestId}. */
    public static LostTalesMissiveBoardRequestPacket take(int dimensionId,
                                                          int x, int y, int z,
                                                          int slot,
                                                          String expectedQuestId) {
        return new LostTalesMissiveBoardRequestPacket(Operation.TAKE,
                dimensionId, x, y, z, slot, expectedQuestId);
    }

    /** Pins the letter in the inventory's {@code slot}, the letter of {@code expectedQuestId}. */
    public static LostTalesMissiveBoardRequestPacket pin(int dimensionId,
                                                         int x, int y, int z,
                                                         int slot,
                                                         String expectedQuestId) {
        return new LostTalesMissiveBoardRequestPacket(Operation.PIN,
                dimensionId, x, y, z, slot, expectedQuestId);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive board request size");
            }
            this.operation = Operation.fromNetworkId(
                    LostTalesMissiveCodec.readSmallCount(buffer, 255,
                            "operation"));
            if (buffer.readableBytes() < 5 * 4) {
                throw new LostTalesPacketCodec.DecodeException(
                        "truncated packet");
            }
            this.dimensionId = buffer.readInt();
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.slot = buffer.readInt();
            this.expectedQuestId = LostTalesPacketCodec.readUtf8String(buffer,
                    LostTalesMissiveCodec.MAX_QUEST_ID_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeByte(this.operation.networkId);
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeInt(this.slot);
        LostTalesPacketCodec.writeUtf8String(buffer, this.expectedQuestId,
                LostTalesMissiveCodec.MAX_QUEST_ID_BYTES);
    }

    /**
     * An operation, a place a block may stand, a slot of the board for a
     * take and a reasonable inventory slot for a pin, and a quest id as
     * a letter holds one: trimmed, and within its bound.
     */
    private void validate() {
        if (this.operation == null
                || !LostTalesPacketCodec.isValidBlockPosition(
                        this.x, this.y, this.z)
                || (this.operation == Operation.TAKE
                        ? this.slot < 0 || this.slot
                                >= LostTalesMissiveBoardStatePacket.MAX_NOTICES
                        : !LostTalesPacketCodec.isReasonableInventorySlot(
                                this.slot))
                || this.expectedQuestId == null
                || !this.expectedQuestId.equals(this.expectedQuestId.trim())
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.expectedQuestId,
                        LostTalesMissiveCodec.MAX_QUEST_ID_BYTES)) {
            throw new IllegalArgumentException(
                    "invalid missive board request");
        }
    }

    public Operation getOperation() { return this.operation; }
    public int getDimensionId() { return this.dimensionId; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public int getSlot() { return this.slot; }
    /** The letter's quest id as the page read it; empty for one that cannot be read. */
    public String getExpectedQuestId() { return this.expectedQuestId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesMissiveBoardRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesMissiveBoardRequestPacket message,
                MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.MISSIVE_BOARD,
                    message.isMalformed(),
                    "LostTalesMissiveBoardRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            if (message.getOperation() == Operation.TAKE) {
                                MissiveBoardService.take(serverPlayer,
                                        message.getDimensionId(),
                                        message.getX(), message.getY(),
                                        message.getZ(), message.getSlot(),
                                        message.getExpectedQuestId());
                            } else {
                                MissiveBoardService.pin(serverPlayer,
                                        message.getDimensionId(),
                                        message.getX(), message.getY(),
                                        message.getZ(), message.getSlot(),
                                        message.getExpectedQuestId());
                            }
                        }
                    });
            return null;
        }
    }
}
