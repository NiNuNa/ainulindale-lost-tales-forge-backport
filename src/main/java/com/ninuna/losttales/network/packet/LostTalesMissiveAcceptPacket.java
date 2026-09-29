package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.MissiveAcceptance;
import com.ninuna.losttales.quest.missive.MissiveBoardService;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

/**
 * A page's request to accept a missive: a notice on a board, named by
 * the board's world, place and slot (the board's page), or a letter in
 * the player's own inventory, named by its slot (the letter's page),
 * with the quest id the page read there. The server reads the stack
 * standing there now, checks it is that letter, starts its quest, and
 * only then takes the letter away; a board's answer is its notices
 * ({@link MissiveBoardService#accept}).
 */
public class LostTalesMissiveAcceptPacket implements IMessage {
    public static final int SOURCE_BOARD = 0;
    public static final int SOURCE_PLAYER_INVENTORY = 1;
    public static final int MAX_PACKET_BYTES = 1024;

    private int sourceType = -1;
    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private int slot;
    private String expectedQuestId = "";
    private boolean malformed;

    public LostTalesMissiveAcceptPacket() {}

    private LostTalesMissiveAcceptPacket(int sourceType, int dimensionId,
                                         int x, int y, int z, int slot,
                                         String expectedQuestId) {
        this.sourceType = sourceType;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.expectedQuestId = expectedQuestId == null ? "" : expectedQuestId;
        validate();
    }

    /** Accepts the notice in the board's {@code slot}, the letter of {@code expectedQuestId}. */
    public static LostTalesMissiveAcceptPacket fromBoard(int dimensionId,
                                                         int x, int y, int z,
                                                         int slot,
                                                         String expectedQuestId) {
        return new LostTalesMissiveAcceptPacket(SOURCE_BOARD, dimensionId,
                x, y, z, slot, expectedQuestId);
    }

    /** Accepts the letter in the inventory's {@code slot}, the letter of {@code expectedQuestId}. */
    public static LostTalesMissiveAcceptPacket fromPlayerInventory(
            int slot, String expectedQuestId) {
        return new LostTalesMissiveAcceptPacket(SOURCE_PLAYER_INVENTORY, 0,
                0, 0, 0, slot, expectedQuestId);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES
                    || buffer.readableBytes() < 6 * 4) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive acceptance size");
            }
            this.sourceType = buffer.readInt();
            this.dimensionId = buffer.readInt();
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.slot = buffer.readInt();
            this.expectedQuestId = LostTalesPacketCodec.readUtf8String(
                    buffer, LostTalesMissiveCodec.MAX_QUEST_ID_BYTES);
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
        buffer.writeInt(this.sourceType);
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeInt(this.slot);
        LostTalesPacketCodec.writeUtf8String(buffer, this.expectedQuestId,
                LostTalesMissiveCodec.MAX_QUEST_ID_BYTES);
    }

    /**
     * A source, a quest id as a letter holds one, and for a board a place
     * where a block may stand and one of its slots; for the inventory a
     * reasonable slot, and the board's fields left at zero.
     */
    private void validate() {
        boolean board = this.sourceType == SOURCE_BOARD;
        if (!board && this.sourceType != SOURCE_PLAYER_INVENTORY
                || this.expectedQuestId == null
                || this.expectedQuestId.length() == 0
                || !this.expectedQuestId.equals(this.expectedQuestId.trim())
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.expectedQuestId,
                        LostTalesMissiveCodec.MAX_QUEST_ID_BYTES)
                || (board ? !LostTalesPacketCodec.isValidBlockPosition(
                        this.x, this.y, this.z) || this.slot < 0
                        || this.slot >= LostTalesMissiveBoardStatePacket.MAX_NOTICES
                        : this.dimensionId != 0 || this.x != 0 || this.y != 0
                        || this.z != 0
                        || !LostTalesPacketCodec.isReasonableInventorySlot(
                                this.slot))) {
            throw new IllegalArgumentException(
                    "invalid missive acceptance request");
        }
    }

    public int getSourceType() { return this.sourceType; }
    public int getDimensionId() { return this.dimensionId; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public int getSlot() { return this.slot; }
    public String getExpectedQuestId() { return this.expectedQuestId; }
    public boolean isMalformed() { return this.malformed; }

    /**
     * The letter in the player's own inventory: still that letter in that
     * slot, its quest started, and only then the letter used up. A
     * refusal is said in the chat; the letter's page stays open on it.
     */
    private static void acceptFromInventory(EntityPlayerMP player, int slot,
                                            String expectedQuestId) {
        if (slot < 0 || slot >= player.inventory.getSizeInventory()) {
            say(player, "chat.losttales.missive.letter_gone");
            return;
        }
        ItemStack stack = player.inventory.getStackInSlot(slot);
        MissiveBoardStateReason refusal =
                MissiveAcceptance.check(player.worldObj, stack, expectedQuestId);
        if (refusal != null) {
            say(player, refusal == MissiveBoardStateReason.GONE
                    ? "chat.losttales.missive.letter_gone"
                    : refusal.getMessageKey());
            return;
        }
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        MissiveBoardStateReason outcome =
                MissiveAcceptance.start(player, missive);
        if (outcome == MissiveBoardStateReason.ACCEPTED) {
            player.inventory.setInventorySlotContents(slot, null);
            player.inventory.markDirty();
            player.worldObj.playSoundAtEntity(player, "random.pop", 0.45F,
                    1.25F);
        } else if (outcome.isSaidInChat()) {
            say(player, outcome.getMessageKey());
        }
    }

    /** A missive's word to the player, said by the Server. */
    private static void say(EntityPlayerMP player, String key) {
        if (player != null) {
            player.addChatMessage(new ChatComponentTranslation(key));
        }
    }

    public static class Handler implements IMessageHandler<LostTalesMissiveAcceptPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesMissiveAcceptPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.MISSIVE_ACCEPT,
                    message.isMalformed(),
                    "LostTalesMissiveAcceptPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            if (message.getSourceType() == SOURCE_BOARD) {
                                MissiveBoardService.accept(livePlayer,
                                        message.getDimensionId(),
                                        message.getX(), message.getY(),
                                        message.getZ(), message.getSlot(),
                                        message.getExpectedQuestId());
                            } else {
                                acceptFromInventory(livePlayer,
                                        message.getSlot(),
                                        message.getExpectedQuestId());
                            }
                        }
                    }
            );
            return null;
        }
    }
}
