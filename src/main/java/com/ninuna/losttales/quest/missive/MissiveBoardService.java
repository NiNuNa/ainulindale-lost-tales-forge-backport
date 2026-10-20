package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.compat.lotr.LotrContainerProtection;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.inventory.LostTalesQuickLootInventoryHelper;
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
 * player's world, the player's reach and sight, the board at the place
 * named, LOTR's banners and every other mod's protection, the slot's
 * range and the letter in it, as the page read it) and answered
 * with the board's notices and why. Whoever else watches the board is
 * sent the change by {@link MissiveBoardWatches}.
 */
public final class MissiveBoardService {
    private MissiveBoardService() {}

    /**
     * The player used a board: where they may use it, and LOTR's banners
     * let them at it, its notices go to them as an opening, which opens
     * the board's page, and they watch it.
     */
    public static boolean open(EntityPlayerMP player,
                               LostTalesTileEntityMissiveBoard board) {
        if (player == null || board == null
                || !board.isUseableByPlayer(player)) {
            return false;
        }
        if (!LotrContainerProtection.mayOpen(player, board.getWorldObj(),
                board.xCoord, board.yCoord, board.zCoord)) {
            say(player, "chat.losttales.missive.protected");
            return false;
        }
        send(player, board, MissiveBoardStateReason.OPENED);
        return true;
    }

    /** Accepts the notice in the board's {@code slot}: its quest starts, and the notice comes down. */
    public static void accept(EntityPlayerMP player, int dimensionId, int x,
                              int y, int z, int slot,
                              String expectedQuestId) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        if (slot < 0 || slot >= board.getSlotCount()) {
            answer(player, board, MissiveBoardStateReason.GONE);
            return;
        }
        ItemStack stack = board.getNotice(slot);
        MissiveBoardStateReason refusal =
                MissiveAcceptance.check(stack, expectedQuestId);
        if (refusal != null) {
            answer(player, board, refusal);
            return;
        }
        MissiveBoardStateReason outcome =
                MissiveAcceptance.start(player, stack);
        if (outcome == MissiveBoardStateReason.ACCEPTED) {
            board.removeNotice(slot);
            pop(player, board);
        }
        answer(player, board, outcome);
    }

    /**
     * Hands in, at the board, every missive of the player's that waits for
     * a board, its work done (QUEST-31): each pays as it ends; and what the
     * player carries for a running world quest's delivery handed in at a
     * board (QUEST-36).
     */
    public static void handIn(EntityPlayerMP player, int dimensionId, int x,
                              int y, int z) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        int handedIn = com.ninuna.losttales.quest.LostTalesQuestManager
                .handInAtBoard(player);
        if (com.ninuna.losttales.quest.world.WorldQuests.deliverAtBoard(player)) {
            handedIn++;
        }
        if (handedIn > 0) {
            pop(player, board);
        }
        answer(player, board, handedIn > 0 ? MissiveBoardStateReason.HANDED_IN
                : MissiveBoardStateReason.NOTHING_TO_HAND_IN);
    }

    /**
     * Takes the notice in the board's {@code slot} down into the player's
     * inventory. Its seal is not checked: taking moves the board's own
     * letter and starts nothing, and a letter without this world's seal
     * is refused where it would start a quest or go up on a board. A
     * letter whose seal fails can still be cleared off the board this way.
     */
    public static void take(EntityPlayerMP player, int dimensionId, int x,
                            int y, int z, int slot, String expectedQuestId) {
        LostTalesTileEntityMissiveBoard board =
                resolve(player, dimensionId, x, y, z);
        if (board == null) {
            return;
        }
        ItemStack stack = slot >= 0 && slot < board.getSlotCount()
                ? board.getNotice(slot) : null;
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
        board.removeNotice(slot);
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
     * now ({@link #refusal}). Where they may not, the chat says why and
     * nothing is sent: the page closes by itself once the player is away
     * from the board.
     */
    private static LostTalesTileEntityMissiveBoard resolve(
            final EntityPlayerMP player, final int dimensionId, final int x,
            final int y, final int z) {
        if (player == null || player.worldObj == null
                || player.worldObj.provider == null) {
            return null;
        }
        final World world = player.worldObj;
        final LostTalesTileEntityMissiveBoard[] found =
                new LostTalesTileEntityMissiveBoard[1];
        String refusal = refusal(new BoardSite() {
            @Override
            public boolean inPlayersWorld() {
                return world.provider.dimensionId == dimensionId;
            }

            @Override
            public boolean withinReach() {
                return LostTalesTileEntityMissiveBoard.isWithinReach(
                        player, x, y, z);
            }

            @Override
            public boolean inSight() {
                return world.blockExists(x, y, z)
                        && LostTalesQuickLootInventoryHelper.isInSight(
                                player, world, x, y, z);
            }

            @Override
            public boolean boardStands() {
                TileEntity tile = world.getTileEntity(x, y, z);
                found[0] = tile instanceof LostTalesTileEntityMissiveBoard
                        ? (LostTalesTileEntityMissiveBoard)tile : null;
                return found[0] != null;
            }

            @Override
            public boolean usable() {
                return found[0].isUseableByPlayer(player);
            }

            @Override
            public boolean bannerAllows() {
                return LotrContainerProtection.mayOpen(player, world, x, y, z);
            }

            @Override
            public boolean othersAllow() {
                return LostTalesQuickLootInventoryHelper.mayUseAsRightClick(
                        player, x, y, z, true);
            }
        });
        if (refusal != null) {
            say(player, refusal);
            return null;
        }
        return found[0];
    }

    /**
     * What a page's request asks of the world at the board, each question
     * asked only once the one before it holds: the distance before
     * anything at the place is read, so a place far off never has its
     * chunk loaded to be asked.
     */
    interface BoardSite {
        /** The board's world is the player's. */
        boolean inPlayersWorld();

        /** The player is within the board's reach. */
        boolean withinReach();

        /** Nothing solid stands between the player's eyes and the board. */
        boolean inSight();

        /** A missive board stands at the place. */
        boolean boardStands();

        /** The board lets this player use it. */
        boolean usable();

        /** LOTR's banners let this player at the board, as at a chest. */
        boolean bannerAllows();

        /**
         * Every other mod lets this player at the board, asked as a
         * right-click on it would ask, quietly: the question starts no
         * quest of this mod's.
         */
        boolean othersAllow();
    }

    /**
     * Why the player may not use the board a request names, as the chat
     * words it, or null where they may: as a chest is opened by quick
     * loot, the board must be in their world, within reach, in sight,
     * standing and usable, and open to them under LOTR's banner
     * protection and every other mod's.
     */
    static String refusal(BoardSite site) {
        if (!site.inPlayersWorld()) {
            return "chat.losttales.missive.board_gone";
        }
        if (!site.withinReach()) {
            return "chat.losttales.missive.too_far";
        }
        if (!site.inSight()) {
            return "chat.losttales.missive.out_of_sight";
        }
        if (!site.boardStands()) {
            return "chat.losttales.missive.board_gone";
        }
        if (!site.usable()) {
            return "chat.losttales.missive.too_far";
        }
        if (!site.bannerAllows() || !site.othersAllow()) {
            return "chat.losttales.missive.protected";
        }
        return null;
    }

    /** The board's notices, slot by slot, as its page shows them. */
    public static LostTalesMissiveBoardStatePacket stateOf(
            LostTalesTileEntityMissiveBoard board,
            MissiveBoardStateReason reason) {
        long worldTime = board.getWorldObj() == null ? 0L
                : board.getWorldObj().getTotalWorldTime();
        long expiration = LostTalesConfig.getMissiveBoardNoticeExpirationTicks();
        int slots = Math.min(board.getSlotCount(),
                LostTalesMissiveBoardStatePacket.MAX_NOTICES);
        List<MissiveNotice> notices = new ArrayList<MissiveNotice>();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = board.getNotice(slot);
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
