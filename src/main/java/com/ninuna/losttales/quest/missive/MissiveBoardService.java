package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardStatePacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * Main-thread server authority for a missive board's page. Using the
 * board sends its notices as an opening; every request the page sends —
 * accept, take, pin — is checked against the world as it stands (the
 * player's world, the board at the place named, the player's reach, the
 * slot's range and the letter in it, as the page read it) and answered
 * with the board's notices and why. Whoever else watches the board is
 * sent the change by {@link MissiveBoardWatches}.
 */
public final class MissiveBoardService {
    private MissiveBoardService() {}

    /**
     * The player used a board: where they may use it, its notices go to
     * them as an opening, which opens the board's page, and they watch it.
     */
    public static boolean open(EntityPlayerMP player,
                               LostTalesTileEntityMissiveBoard board) {
        if (player == null || board == null
                || !board.isUseableByPlayer(player)) {
            return false;
        }
        send(player, board, MissiveBoardStateReason.OPENED);
        return true;
    }

    /** Accepts the notice in the board's {@code slot}: its quest starts, and a first-come notice comes down. */
    public static void accept(EntityPlayerMP player, int dimensionId, int x,
                              int y, int z, int slot,
                              String expectedQuestId) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        if (slot < 0 || slot >= board.getSizeInventory()) {
            answer(player, board, MissiveBoardStateReason.GONE);
            return;
        }
        ItemStack stack = board.getStackInSlot(slot);
        MissiveBoardStateReason refusal =
                MissiveAcceptance.check(player.worldObj, stack, expectedQuestId);
        if (refusal != null) {
            answer(player, board, refusal);
            return;
        }
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
        MissiveBoardStateReason outcome =
                MissiveAcceptance.start(player, missive);
        if (outcome == MissiveBoardStateReason.ACCEPTED) {
            if (missive.isFirstComeFirstServed()) {
                board.setInventorySlotContents(slot, null);
            }
            pop(player, board);
        }
        answer(player, board, outcome);
    }

    /** Takes the notice in the board's {@code slot} down into the player's inventory. */
    public static void take(EntityPlayerMP player, int dimensionId, int x,
                            int y, int z, int slot, String expectedQuestId) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        ItemStack stack = slot >= 0 && slot < board.getSizeInventory()
                ? board.getStackInSlot(slot) : null;
        String questId = MissiveAcceptance.pageQuestId(stack);
        if (questId == null) {
            answer(player, board, MissiveBoardStateReason.GONE);
            return;
        }
        if (!questId.equals(expectedQuestId)) {
            answer(player, board, MissiveBoardStateReason.NOTICE_CHANGED);
            return;
        }
        ItemStack taken = stack.copy();
        taken.stackSize = 1;
        if (player.inventory.getFirstEmptyStack() < 0
                || !player.inventory.addItemStackToInventory(taken)) {
            answer(player, board, MissiveBoardStateReason.INVENTORY_FULL);
            return;
        }
        board.setInventorySlotContents(slot, null);
        player.inventory.markDirty();
        pop(player, board);
        answer(player, board, MissiveBoardStateReason.TAKEN);
    }

    /**
     * Pins the letter in the inventory's {@code slot} onto the board, where
     * it has room. The letter is posted anew, at the board's time, and
     * sealed again, so the board keeps it for its whole expiry.
     */
    public static void pin(EntityPlayerMP player, int dimensionId, int x,
                           int y, int z, int slot, String expectedQuestId) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        // Only the main inventory holds a letter; the armour slots never do.
        ItemStack stack = slot >= 0 && slot < player.inventory.mainInventory.length
                ? player.inventory.getStackInSlot(slot) : null;
        String questId = MissiveAcceptance.pageQuestId(stack);
        if (questId == null || !questId.equals(expectedQuestId)) {
            answer(player, board, MissiveBoardStateReason.LETTER_GONE);
            return;
        }
        if (!MissiveSeals.isGenuine(player.worldObj, stack)) {
            answer(player, board, MissiveBoardStateReason.DAMAGED);
            return;
        }
        ItemStack posted = repost(player.worldObj, stack);
        if (!board.hasRoomForMissive() || !board.addMissive(posted)) {
            answer(player, board, MissiveBoardStateReason.BOARD_FULL);
            return;
        }
        player.inventory.decrStackSize(slot, 1);
        player.inventory.markDirty();
        pop(player, board);
        answer(player, board, MissiveBoardStateReason.PINNED);
    }

    /**
     * A copy of a genuine letter posted at the world's time now, sealed
     * again with the world's key. Only a letter whose seal was checked is
     * handed here.
     */
    static ItemStack repost(World world, ItemStack letter) {
        ItemStack posted = letter.copy();
        posted.stackSize = 1;
        LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(letter);
        LostTalesMissiveNbt.writeToItemStack(posted,
                missive.postedAt(world.getTotalWorldTime()));
        MissiveSeals.seal(world, posted);
        return posted;
    }

    /**
     * The board at the place a page names, where this player may use it
     * now: in the player's own world, standing there, and within reach.
     * Where it is not, the chat says why and nothing is sent: the page
     * closes by itself once the player is away from the board.
     */
    private static LostTalesTileEntityMissiveBoard resolve(
            EntityPlayerMP player, int dimensionId, int x, int y, int z) {
        if (player == null || player.worldObj == null
                || player.worldObj.provider == null) {
            return null;
        }
        if (player.worldObj.provider.dimensionId != dimensionId) {
            say(player, "chat.losttales.missive.board_gone");
            return null;
        }
        // The distance first, so a place far off never has its chunk
        // loaded to be asked.
        if (!LostTalesTileEntityMissiveBoard.isWithinReach(player, x, y, z)) {
            say(player, "chat.losttales.missive.too_far");
            return null;
        }
        TileEntity tile = player.worldObj.getTileEntity(x, y, z);
        if (!(tile instanceof LostTalesTileEntityMissiveBoard)) {
            say(player, "chat.losttales.missive.board_gone");
            return null;
        }
        LostTalesTileEntityMissiveBoard board =
                (LostTalesTileEntityMissiveBoard)tile;
        if (!board.isUseableByPlayer(player)) {
            say(player, "chat.losttales.missive.too_far");
            return null;
        }
        return board;
    }

    /** The board's notices, slot by slot, as its page shows them. */
    public static LostTalesMissiveBoardStatePacket stateOf(
            LostTalesTileEntityMissiveBoard board,
            MissiveBoardStateReason reason) {
        long worldTime = board.getWorldObj() == null ? 0L
                : board.getWorldObj().getTotalWorldTime();
        long expiration = LostTalesConfig.getMissiveBoardNoticeExpirationTicks();
        int slots = Math.min(board.getSizeInventory(),
                LostTalesMissiveBoardStatePacket.MAX_NOTICES);
        List<MissiveNotice> notices = new ArrayList<MissiveNotice>();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = board.getStackInSlot(slot);
            if (!MissiveAcceptance.isLetter(stack)) {
                continue;
            }
            LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
            notices.add(new MissiveNotice(slot,
                    ticksLeft(missive, expiration, worldTime), missive));
        }
        int dimension = board.getWorldObj() == null
                || board.getWorldObj().provider == null ? 0
                : board.getWorldObj().provider.dimensionId;
        return new LostTalesMissiveBoardStatePacket(dimension, board.xCoord,
                board.yCoord, board.zCoord, reason,
                Math.max(0, Math.min(LostTalesMissiveBoardStatePacket.MAX_NOTICES,
                        LostTalesTileEntityMissiveBoard.getMaxAvailableMissives())),
                notices);
    }

    /**
     * World ticks until the board takes a notice down, as its own
     * expiry reads the letter: never for a letter that cannot be read,
     * one with no time it was posted at, or with expiry off.
     */
    static long ticksLeft(LostTalesMissiveData missive, long expiration,
                          long worldTime) {
        if (missive == null || expiration <= 0L) {
            return MissiveNotice.STAYS_UP;
        }
        long postedAt = missive.getGenerationWorldTime();
        if (postedAt <= 0L || worldTime < postedAt) {
            return MissiveNotice.STAYS_UP;
        }
        return Math.max(0L, postedAt + expiration - worldTime);
    }

    /** Sends the player the board's notices and why, and watches the board for them. */
    static void send(EntityPlayerMP player,
                     LostTalesTileEntityMissiveBoard board,
                     MissiveBoardStateReason reason) {
        LostTalesMissiveBoardStatePacket state = stateOf(board, reason);
        LostTalesNetworkHandler.CHANNEL.sendTo(state, player);
        MissiveBoardWatches.watch(player.getUniqueID(),
                state.getDimensionId(), state.getX(), state.getY(),
                state.getZ(), state.getFingerprint());
    }

    /** Answers a request: the board's notices and why, a refusal in the chat as well. */
    private static void answer(EntityPlayerMP player,
                               LostTalesTileEntityMissiveBoard board,
                               MissiveBoardStateReason reason) {
        if (reason.isSaidInChat()) {
            say(player, reason.getMessageKey());
        }
        send(player, board, reason);
    }

    private static void pop(EntityPlayerMP player,
                            LostTalesTileEntityMissiveBoard board) {
        player.worldObj.playSoundEffect(board.xCoord + 0.5D,
                board.yCoord + 0.5D, board.zCoord + 0.5D, "random.pop",
                0.45F, 1.25F);
    }

    /** A missive board's word to the player, said by the Server. */
    static void say(EntityPlayerMP player, String key) {
        if (player != null && key != null && key.length() > 0) {
            player.addChatMessage(new ChatComponentTranslation(key));
        }
    }
}
