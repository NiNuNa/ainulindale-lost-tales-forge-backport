package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveGenerator;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.MissiveBoardWatches;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

/**
 * A missive board's notices: up to nine missive letters, which the board
 * refills and takes down by itself on the server, by the server's
 * {@code missives} settings as they stand now. Its page reads them
 * through {@link com.ninuna.losttales.quest.missive.MissiveBoardService},
 * which accepts, takes and pins for a player standing within
 * {@link #REACH_SQ}; every change here tells the players watching it. The
 * board's model shows none of its notices, so no client is sent them
 * with the block.
 *
 * <p>The board is no inventory: hoppers, pipes, quick loot and other
 * mods' automation never reach its notices, so a letter goes up only
 * through the board's own refill or a sealed Pin, and comes down only
 * through Accept, Take, expiry or the board being broken.</p>
 */
public class LostTalesTileEntityMissiveBoard extends TileEntity {
    /** The slots a board's notices stand in. */
    public static final int INVENTORY_SIZE = 9;
    /**
     * How near a player stands to use the board, squared, measured to the
     * block's middle: eight blocks. The board's page closes once its
     * player is further than this.
     */
    public static final double REACH_SQ = 64.0D;
    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 1200L;

    private final ItemStack[] notices = new ItemStack[INVENTORY_SIZE];
    private long lastGenerationWorldTime;
    private long nextGenerationWorldTime;
    private int generationSequence;
    private long nextExpirationCheckWorldTime;

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) return;

        long worldTime = this.worldObj.getTotalWorldTime();
        this.expireOldMissives(worldTime);
        if (!LostTalesConfig.enableDynamicMissiveBoards) {
            return;
        }
        if (this.nextGenerationWorldTime <= 0L) {
            this.nextGenerationWorldTime = worldTime;
        }
        if (worldTime >= this.nextGenerationWorldTime) {
            this.generateScheduledMissives(worldTime);
        }
    }

    public int countAvailableMissives() {
        int count = 0;
        for (int slot = 0; slot < this.notices.length; slot++) {
            ItemStack stack = this.notices[slot];
            if (this.isMissiveLetter(stack)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasRoomForMissive() {
        return this.getFirstEmptySlot() >= 0 && this.countAvailableMissives() < getMaxAvailableMissives();
    }

    public boolean addMissive(ItemStack stack) {
        if (!this.isMissiveLetter(stack) || !this.hasRoomForMissive()) return false;

        int slot = this.getFirstEmptySlot();
        if (slot < 0) return false;

        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        this.notices[slot] = copy;
        this.markDirtyAndSync();
        return true;
    }

    private void generateScheduledMissives(long worldTime) {
        int available = this.countAvailableMissives();
        int most = getMaxAvailableMissives();
        if (available >= most || this.getFirstEmptySlot() < 0) {
            this.markGenerationAttempt(worldTime);
            return;
        }

        int room = Math.min(most - available, this.getEmptySlotCount());
        int configuredMinBatch = Math.max(1, Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMinGeneratedPerCycle));
        int configuredMaxBatch = Math.max(configuredMinBatch, Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMaxGeneratedPerCycle));
        int maxBatch = Math.min(configuredMaxBatch, room);
        int minBatch = Math.min(configuredMinBatch, maxBatch);
        if (maxBatch <= 0) {
            this.markGenerationAttempt(worldTime);
            return;
        }

        int toGenerate = minBatch;
        if (available < getMinAvailableMissives()) {
            // Refill boards below the desired floor more eagerly, but still only
            // in small batches so missives do not all regenerate at once.
            toGenerate = maxBatch;
        } else if (maxBatch > minBatch) {
            toGenerate += this.worldObj.rand.nextInt(maxBatch - minBatch + 1);
        }

        int generated = 0;
        int attemptsRemaining = toGenerate * 3;
        while (generated < toGenerate && attemptsRemaining-- > 0 && this.hasRoomForMissive()) {
            ItemStack stack = LostTalesMissiveGenerator.createRandomMissiveLetter(
                    this.worldObj,
                    this.createBoardKey(),
                    worldTime,
                    this.generationSequence++,
                    this.worldObj.rand
            );
            if (stack != null && this.addMissive(stack)) {
                generated++;
            }
        }

        this.markGenerationAttempt(worldTime);
    }

    private void expireOldMissives(long worldTime) {
        long expirationTicks = LostTalesConfig.getMissiveBoardNoticeExpirationTicks();
        if (expirationTicks <= 0L) {
            return;
        }
        if (this.nextExpirationCheckWorldTime > 0L && worldTime < this.nextExpirationCheckWorldTime) {
            return;
        }
        this.nextExpirationCheckWorldTime = worldTime + EXPIRATION_CHECK_INTERVAL_TICKS;

        boolean changed = false;
        for (int slot = 0; slot < this.notices.length; slot++) {
            ItemStack stack = this.notices[slot];
            if (!this.isMissiveLetter(stack)) {
                continue;
            }
            LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
            if (missive == null || !missive.isValid()) {
                continue;
            }
            long generatedAt = missive.getGenerationWorldTime();
            if (generatedAt > 0L && worldTime >= generatedAt && worldTime - generatedAt >= expirationTicks) {
                this.notices[slot] = null;
                changed = true;
            }
        }

        if (changed) {
            this.markDirtyAndSync();
        }
    }

    private int getEmptySlotCount() {
        int count = 0;
        for (int slot = 0; slot < this.notices.length; slot++) {
            if (this.notices[slot] == null) {
                count++;
            }
        }
        return count;
    }

    private String createBoardKey() {
        int dimension = this.worldObj == null || this.worldObj.provider == null ? 0 : this.worldObj.provider.dimensionId;
        return "dim" + dimension + "_" + this.xCoord + "_" + this.yCoord + "_" + this.zCoord;
    }

    public int getFirstEmptySlot() {
        for (int slot = 0; slot < this.notices.length; slot++) {
            if (this.notices[slot] == null) {
                return slot;
            }
        }
        return -1;
    }

    public void markGenerationAttempt(long worldTime) {
        this.lastGenerationWorldTime = worldTime;
        this.scheduleNextGeneration(worldTime);
        this.markDirtyAndSync();
    }

    public void scheduleNextGeneration(long currentWorldTime) {
        this.nextGenerationWorldTime = currentWorldTime + getGenerationIntervalTicks();
        this.markDirtyAndSync();
    }

    /** The most notices a board holds, as the server's settings say now, within its nine slots. */
    public static int getMaxAvailableMissives() {
        return Math.max(getMinAvailableMissives(),
                Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMaxAvailable));
    }

    /** The fewest notices a board refills towards at once, as the server's settings say now. */
    static int getMinAvailableMissives() {
        return Math.max(0, Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMinAvailable));
    }

    /** The world ticks between two refills, as the server's settings say now; a minute at the least. */
    static long getGenerationIntervalTicks() {
        return Math.max(1200L, (long) LostTalesConfig.missiveBoardGenerationIntervalTicks);
    }

    private boolean isMissiveLetter(ItemStack stack) {
        return stack != null && stack.getItem() == ELostTalesItem.MISSIVE_LETTER.getItem();
    }

    /** Saves the change and tells the players watching the board's page; nothing goes to the block's clients. */
    private void markDirtyAndSync() {
        this.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            MissiveBoardWatches.markChanged();
        }
    }

    /** How many slots the board's notices stand in. */
    public int getSlotCount() {
        return this.notices.length;
    }

    /** The letter in the slot; null for an empty slot or one out of range. */
    public ItemStack getNotice(int slot) {
        return slot >= 0 && slot < this.notices.length ? this.notices[slot] : null;
    }

    /** Takes the notice in the slot down; an empty slot or one out of range stays as it is. */
    public void removeNotice(int slot) {
        if (slot < 0 || slot >= this.notices.length || this.notices[slot] == null) return;

        this.notices[slot] = null;
        this.markDirtyAndSync();
    }

    /** The board still stands here, and the player is within {@link #REACH_SQ} of it. */
    public boolean isUseableByPlayer(EntityPlayer player) {
        return player != null && this.worldObj != null
                && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
                && this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord)
                        == ELostTalesBlock.MISSIVE_BOARD.getBlock()
                && isWithinReach(player, this.xCoord, this.yCoord, this.zCoord);
    }

    /** Whether the player is within {@link #REACH_SQ} of the middle of the block at {@code x}/{@code y}/{@code z}. */
    public static boolean isWithinReach(EntityPlayer player, int x, int y, int z) {
        return player != null && player.getDistanceSq(
                x + 0.5D, y + 0.5D, z + 0.5D) <= REACH_SQ;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        this.lastGenerationWorldTime = nbt.getLong("LastGenerationWorldTime");
        this.nextGenerationWorldTime = nbt.getLong("NextGenerationWorldTime");
        this.generationSequence = Math.max(0, nbt.getInteger("GenerationSequence"));
        this.nextExpirationCheckWorldTime = nbt.getLong("NextExpirationCheckWorldTime");

        for (int slot = 0; slot < this.notices.length; slot++) {
            this.notices[slot] = null;
        }

        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.notices.length) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(itemTag);
                if (this.isMissiveLetter(stack)) {
                    this.notices[slot] = stack;
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setLong("LastGenerationWorldTime", this.lastGenerationWorldTime);
        nbt.setLong("NextGenerationWorldTime", this.nextGenerationWorldTime);
        nbt.setInteger("GenerationSequence", this.generationSequence);
        nbt.setLong("NextExpirationCheckWorldTime", this.nextExpirationCheckWorldTime);

        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < this.notices.length; slot++) {
            ItemStack stack = this.notices[slot];
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) slot);
                stack.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        nbt.setTag("Items", list);
    }
}
