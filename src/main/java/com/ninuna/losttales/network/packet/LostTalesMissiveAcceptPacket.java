package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveQuestFactory;
import cpw.mods.fml.common.network.ByteBufUtils;
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
 * both cases the server re-reads the authoritative stack, validates its NBT and
 * quest ID, starts the generated quest, and only then consumes/removes the
 * letter. This lets players take a letter, read it, and put it back if they do
 * not want the work, while still preventing forged/stale client acceptance.
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
    public void fromBytes(ByteBuf buf) {
        this.sourceType = buf.readInt();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.slot = buf.readInt();
        this.expectedQuestId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.sourceType);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.slot);
        ByteBufUtils.writeUTF8String(buf, this.expectedQuestId == null ? "" : this.expectedQuestId);
    }

    public static class Handler implements IMessageHandler<LostTalesMissiveAcceptPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesMissiveAcceptPacket message, MessageContext ctx) {
            if (message == null || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null || player.worldObj.isRemote) {
                return null;
            }

            if (message.sourceType == SOURCE_PLAYER_INVENTORY) {
                handlePlayerInventoryAcceptance(player, message);
            } else {
                handleBoardAcceptance(player, message);
            }
            return null;
        }

        private static void handleBoardAcceptance(EntityPlayerMP player, LostTalesMissiveAcceptPacket message) {
            TileEntity tileEntity = player.worldObj.getTileEntity(message.x, message.y, message.z);
            if (!(tileEntity instanceof LostTalesTileEntityMissiveBoard)) {
                send(player, EnumChatFormatting.RED + "That missive board is no longer available.");
                return;
            }

            LostTalesTileEntityMissiveBoard board = (LostTalesTileEntityMissiveBoard) tileEntity;
            if (!board.isUseableByPlayer(player)) {
                send(player, EnumChatFormatting.RED + "You are too far away from the missive board.");
                return;
            }
            if (message.slot < 0 || message.slot >= board.getSizeInventory()) {
                send(player, EnumChatFormatting.RED + "That missive is no longer available.");
                return;
            }

            ItemStack stack = board.getStackInSlot(message.slot);
            LostTalesMissiveData missive = readValidMissive(player, stack, message.expectedQuestId);
            if (missive == null) {
                return;
            }

            LostTalesQuestManager.StartResult result = startMissive(player, missive);
            if (result == LostTalesQuestManager.StartResult.STARTED) {
                if (missive.isFirstComeFirstServed()) {
                    board.setInventorySlotContents(message.slot, null);
                } else {
                    board.markDirty();
                    player.worldObj.markBlockForUpdate(message.x, message.y, message.z);
                }
                player.worldObj.playSoundEffect((double) message.x + 0.5D, (double) message.y + 0.5D, (double) message.z + 0.5D, "random.pop", 0.45F, 1.25F);
            } else {
                sendStartFailure(player, result);
            }
        }

        private static void handlePlayerInventoryAcceptance(EntityPlayerMP player, LostTalesMissiveAcceptPacket message) {
            if (message.slot < 0 || message.slot >= player.inventory.getSizeInventory()) {
                send(player, EnumChatFormatting.RED + "That missive letter is no longer in your inventory.");
                return;
            }

            ItemStack stack = player.inventory.getStackInSlot(message.slot);
            LostTalesMissiveData missive = readValidMissive(player, stack, message.expectedQuestId);
            if (missive == null) {
                return;
            }

            LostTalesQuestManager.StartResult result = startMissive(player, missive);
            if (result == LostTalesQuestManager.StartResult.STARTED) {
                player.inventory.setInventorySlotContents(message.slot, null);
                player.inventory.markDirty();
                player.worldObj.playSoundAtEntity(player, "random.pop", 0.45F, 1.25F);
            } else {
                sendStartFailure(player, result);
            }
        }

        private static LostTalesMissiveData readValidMissive(EntityPlayerMP player, ItemStack stack, String expectedQuestId) {
            if (stack == null || stack.stackSize <= 0 || stack.getItem() != ELostTalesItem.MISSIVE_LETTER.getItem()) {
                send(player, EnumChatFormatting.RED + "That missive is no longer available.");
                return null;
            }

            LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
            if (missive == null || !missive.isValid()) {
                send(player, EnumChatFormatting.RED + "That missive is damaged or incomplete.");
                return null;
            }

            String expected = expectedQuestId == null ? "" : expectedQuestId.trim();
            if (expected.length() > 0 && !expected.equals(missive.getQuestId())) {
                send(player, EnumChatFormatting.RED + "That missive changed before you could accept it.");
                return null;
            }
            return missive;
        }

        private static LostTalesQuestManager.StartResult startMissive(EntityPlayerMP player, LostTalesMissiveData missive) {
            LostTalesQuestDefinition quest = LostTalesMissiveQuestFactory.createQuestDefinition(missive);
            if (quest == null) {
                send(player, EnumChatFormatting.RED + "That missive cannot be accepted.");
                return LostTalesQuestManager.StartResult.UNKNOWN_QUEST;
            }
            return LostTalesQuestManager.startGeneratedQuest(player, quest, missive.getTimeLimitTicks());
        }

        private static void sendStartFailure(EntityPlayerMP player, LostTalesQuestManager.StartResult result) {
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
    }
}
