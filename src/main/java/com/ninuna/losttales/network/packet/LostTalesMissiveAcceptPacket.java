package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveQuestFactory;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * Client-to-server request to accept a missive.
 *
 * Requests may point either to a board slot or to a player inventory slot. In
 * both cases the logical server re-reads the authoritative stack, validates its
 * NBT and quest ID, starts the generated quest, and only then consumes/removes
 * the letter.
 */
public class LostTalesMissiveAcceptPacket implements IMessage {
    public static final int SOURCE_BOARD = 0;
    public static final int SOURCE_PLAYER_INVENTORY = 1;

    private int sourceType = SOURCE_BOARD;
    private int x;
    private int y;
    private int z;
    private int slot;
    private String expectedQuestId = "";
    private boolean malformed;

    public LostTalesMissiveAcceptPacket() {}

    public LostTalesMissiveAcceptPacket(int x, int y, int z, int slot, String expectedQuestId) {
        this.sourceType = SOURCE_BOARD;
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.expectedQuestId = expectedQuestId == null ? "" : expectedQuestId;
    }

    public static LostTalesMissiveAcceptPacket fromPlayerInventory(int slot, String expectedQuestId) {
        LostTalesMissiveAcceptPacket packet = new LostTalesMissiveAcceptPacket();
        packet.sourceType = SOURCE_PLAYER_INVENTORY;
        packet.slot = slot;
        packet.expectedQuestId = expectedQuestId == null ? "" : expectedQuestId;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.sourceType = buffer.readInt();
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.slot = buffer.readInt();
            this.expectedQuestId = LostTalesPacketCodec.readUtf8String(
                    buffer, LostTalesPacketCodec.MAX_IDENTIFIER_BYTES).trim();
            LostTalesPacketCodec.requireFinished(buffer);

            if ((this.sourceType != SOURCE_BOARD && this.sourceType != SOURCE_PLAYER_INVENTORY)
                    || !LostTalesPacketCodec.isReasonableInventorySlot(this.slot)
                    || this.expectedQuestId.length() == 0
                    || (this.sourceType == SOURCE_BOARD
                    && !LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z))) {
                throw new LostTalesPacketCodec.DecodeException("invalid missive acceptance request");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.sourceType);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeInt(this.slot);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.expectedQuestId == null ? "" : this.expectedQuestId,
                LostTalesPacketCodec.MAX_IDENTIFIER_BYTES);
    }

    private static void execute(
            EntityPlayerMP player, int sourceType, int expectedDimension,
            int x, int y, int z, int slot, String expectedQuestId) {
        if (sourceType == SOURCE_PLAYER_INVENTORY) {
            handlePlayerInventoryAcceptance(player, slot, expectedQuestId);
        } else if (sourceType == SOURCE_BOARD
                && player.worldObj.provider != null
                && player.worldObj.provider.dimensionId == expectedDimension) {
            handleBoardAcceptance(player, x, y, z, slot, expectedQuestId);
        }
    }

    private static void handleBoardAcceptance(
            EntityPlayerMP player, int x, int y, int z, int slot, String expectedQuestId) {
        TileEntity tileEntity = player.worldObj.getTileEntity(x, y, z);
        if (!(tileEntity instanceof LostTalesTileEntityMissiveBoard)) {
            send(player, EnumChatFormatting.RED + "That missive board is no longer available.");
            return;
        }

        LostTalesTileEntityMissiveBoard board = (LostTalesTileEntityMissiveBoard) tileEntity;
        if (!board.isUseableByPlayer(player)) {
            send(player, EnumChatFormatting.RED + "You are too far away from the missive board.");
            return;
        }
        if (slot < 0 || slot >= board.getSizeInventory()) {
            send(player, EnumChatFormatting.RED + "That missive is no longer available.");
            return;
        }

        ItemStack stack = board.getStackInSlot(slot);
        LostTalesMissiveData missive = readValidMissive(player, stack, expectedQuestId);
        if (missive == null) {
            return;
        }

        LostTalesQuestManager.StartResult result = startMissive(player, missive);
        if (result == LostTalesQuestManager.StartResult.STARTED) {
            if (missive.isFirstComeFirstServed()) {
                board.setInventorySlotContents(slot, null);
            } else {
                board.markDirty();
                player.worldObj.markBlockForUpdate(x, y, z);
            }
            player.worldObj.playSoundEffect(
                    (double) x + 0.5D,
                    (double) y + 0.5D,
                    (double) z + 0.5D,
                    "random.pop", 0.45F, 1.25F);
        } else {
            sendStartFailure(player, result);
        }
    }

    private static void handlePlayerInventoryAcceptance(
            EntityPlayerMP player, int slot, String expectedQuestId) {
        if (slot < 0 || slot >= player.inventory.getSizeInventory()) {
            send(player, EnumChatFormatting.RED + "That missive letter is no longer in your inventory.");
            return;
        }

        ItemStack stack = player.inventory.getStackInSlot(slot);
        LostTalesMissiveData missive = readValidMissive(player, stack, expectedQuestId);
        if (missive == null) {
            return;
        }

        LostTalesQuestManager.StartResult result = startMissive(player, missive);
        if (result == LostTalesQuestManager.StartResult.STARTED) {
            player.inventory.setInventorySlotContents(slot, null);
            player.inventory.markDirty();
            player.worldObj.playSoundAtEntity(player, "random.pop", 0.45F, 1.25F);
        } else {
            sendStartFailure(player, result);
        }
    }

    private static LostTalesMissiveData readValidMissive(
            EntityPlayerMP player, ItemStack stack, String expectedQuestId) {
        if (stack == null || stack.stackSize <= 0
                || stack.getItem() != ELostTalesItem.MISSIVE_LETTER.getItem()) {
            send(player, EnumChatFormatting.RED + "That missive is no longer available.");
            return null;
        }

        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (missive == null || !missive.isValid()) {
            send(player, EnumChatFormatting.RED + "That missive is damaged or incomplete.");
            return null;
        }

        String expected = expectedQuestId == null ? "" : expectedQuestId.trim();
        if (expected.length() == 0 || !expected.equals(missive.getQuestId())) {
            send(player, EnumChatFormatting.RED + "That missive changed before you could accept it.");
            return null;
        }
        return missive;
    }

    private static LostTalesQuestManager.StartResult startMissive(
            EntityPlayerMP player, LostTalesMissiveData missive) {
        LostTalesQuestDefinition quest = LostTalesMissiveQuestFactory.createQuestDefinition(missive);
        if (quest == null) {
            send(player, EnumChatFormatting.RED + "That missive cannot be accepted.");
            return LostTalesQuestManager.StartResult.UNKNOWN_QUEST;
        }
        return LostTalesQuestManager.startGeneratedQuest(
                player, quest, missive.getTimeLimitTicks());
    }

    private static void sendStartFailure(
            EntityPlayerMP player, LostTalesQuestManager.StartResult result) {
        if (result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE) {
            send(player, EnumChatFormatting.YELLOW + "You already have this missive active.");
        } else if (result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
            send(player, EnumChatFormatting.YELLOW + "You have already completed this missive.");
        } else if (result == LostTalesQuestManager.StartResult.REQUIREMENTS_NOT_MET) {
            // startGeneratedQuest already sends the specific prerequisite failure.
        } else {
            send(player, EnumChatFormatting.RED + "This missive cannot be accepted right now.");
        }
    }

    private static void send(EntityPlayerMP player, String message) {
        if (player != null && message != null && message.length() > 0) {
            player.addChatMessage(new ChatComponentText(message));
        }
    }

    public static class Handler implements IMessageHandler<LostTalesMissiveAcceptPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesMissiveAcceptPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || player.worldObj == null
                    || player.worldObj.provider == null || message == null) {
                return null;
            }

            final int sourceType = message.sourceType;
            final int expectedDimension = player.worldObj.provider.dimensionId;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final int slot = message.slot;
            final String expectedQuestId = message.expectedQuestId;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.MISSIVE_ACCEPT,
                    message.malformed,
                    "LostTalesMissiveAcceptPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            execute(livePlayer, sourceType, expectedDimension,
                                    x, y, z, slot, expectedQuestId);
                        }
                    }
            );
            return null;
        }
    }
}
