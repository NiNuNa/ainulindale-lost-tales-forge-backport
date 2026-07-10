package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveGenerator;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * Server-authoritative inventory and generation foundation for dynamic missive boards.
 *
 * The board stores generated missive letter stacks and slowly refills itself on
 * the server. Quest acceptance is intentionally still deferred to a later
 * server-validated stage.
 */
public class LostTalesTileEntityMissiveBoard extends TileEntity implements IInventory {
    public static final int INVENTORY_SIZE = 9;
    public static final int DEFAULT_MIN_AVAILABLE_MISSIVES = 5;
    public static final int DEFAULT_MAX_AVAILABLE_MISSIVES = 9;
    private static final int LEGACY_DEFAULT_MAX_AVAILABLE_MISSIVES = 8;
    public static final long DEFAULT_GENERATION_INTERVAL_TICKS = 36000L;
    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 1200L;

    private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];
    private long lastGenerationWorldTime;
    private long nextGenerationWorldTime;
    private long generationIntervalTicks = DEFAULT_GENERATION_INTERVAL_TICKS;
    private int minAvailableMissives = DEFAULT_MIN_AVAILABLE_MISSIVES;
    private int maxAvailableMissives = DEFAULT_MAX_AVAILABLE_MISSIVES;
    private int generationSequence;
    private long nextExpirationCheckWorldTime;

    public LostTalesTileEntityMissiveBoard() {
        this.applyConfiguredDefaults();
    }

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
        for (int slot = 0; slot < this.inventory.length; slot++) {
            ItemStack stack = this.inventory[slot];
            if (this.isMissiveLetter(stack)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasRoomForMissive() {
        return this.getFirstEmptySlot() >= 0 && this.countAvailableMissives() < this.maxAvailableMissives;
    }

    public boolean addMissive(ItemStack stack) {
        if (!this.isItemValidForSlot(0, stack) || !this.hasRoomForMissive()) return false;

        int slot = this.getFirstEmptySlot();
        if (slot < 0) return false;

        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        this.inventory[slot] = copy;
        this.markDirtyAndSync();
        return true;
    }

    private void generateScheduledMissives(long worldTime) {
        int available = this.countAvailableMissives();
        if (available >= this.maxAvailableMissives || this.getFirstEmptySlot() < 0) {
            this.markGenerationAttempt(worldTime);
            return;
        }

        int room = Math.min(this.maxAvailableMissives - available, this.getEmptySlotCount());
        int configuredMinBatch = Math.max(1, Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMinGeneratedPerCycle));
        int configuredMaxBatch = Math.max(configuredMinBatch, Math.min(INVENTORY_SIZE, LostTalesConfig.missiveBoardMaxGeneratedPerCycle));
        int maxBatch = Math.min(configuredMaxBatch, room);
        int minBatch = Math.min(configuredMinBatch, maxBatch);
        if (maxBatch <= 0) {
            this.markGenerationAttempt(worldTime);
            return;
        }

        int toGenerate = minBatch;
        if (available < this.minAvailableMissives) {
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
        for (int slot = 0; slot < this.inventory.length; slot++) {
            ItemStack stack = this.inventory[slot];
            if (!this.isMissiveLetter(stack)) {
                continue;
            }
            LostTalesMissiveData missive = LostTalesMissiveNbt.readFromItemStack(stack);
            if (missive == null || !missive.isValid()) {
                continue;
            }
            long generatedAt = missive.getGenerationWorldTime();
            if (generatedAt > 0L && worldTime >= generatedAt && worldTime - generatedAt >= expirationTicks) {
                this.inventory[slot] = null;
                changed = true;
            }
        }

        if (changed) {
            this.markDirtyAndSync();
        }
    }

    private int getEmptySlotCount() {
        int count = 0;
        for (int slot = 0; slot < this.inventory.length; slot++) {
            if (this.inventory[slot] == null) {
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
        for (int slot = 0; slot < this.inventory.length; slot++) {
            if (this.inventory[slot] == null) {
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
        long interval = this.generationIntervalTicks > 0L ? this.generationIntervalTicks : getConfiguredGenerationIntervalTicks();
        this.nextGenerationWorldTime = currentWorldTime + interval;
        this.markDirtyAndSync();
    }

    public long getLastGenerationWorldTime() {
        return this.lastGenerationWorldTime;
    }

    public long getNextGenerationWorldTime() {
        return this.nextGenerationWorldTime;
    }

    public long getGenerationIntervalTicks() {
        return this.generationIntervalTicks;
    }

    public void setGenerationIntervalTicks(long generationIntervalTicks) {
        this.generationIntervalTicks = generationIntervalTicks > 0L ? generationIntervalTicks : getConfiguredGenerationIntervalTicks();
        this.markDirtyAndSync();
    }

    public int getMinAvailableMissives() {
        return this.minAvailableMissives;
    }

    public int getMaxAvailableMissives() {
        return this.maxAvailableMissives;
    }

    public void setMissiveRange(int minAvailableMissives, int maxAvailableMissives) {
        this.applyMissiveRange(minAvailableMissives, maxAvailableMissives);
        this.markDirtyAndSync();
    }

    private void applyConfiguredDefaults() {
        this.generationIntervalTicks = getConfiguredGenerationIntervalTicks();
        this.applyMissiveRange(LostTalesConfig.missiveBoardMinAvailable, LostTalesConfig.missiveBoardMaxAvailable);
    }

    private static long getConfiguredGenerationIntervalTicks() {
        return Math.max(1200L, (long) LostTalesConfig.missiveBoardGenerationIntervalTicks);
    }

    private void applyMissiveRange(int minAvailableMissives, int maxAvailableMissives) {
        if (minAvailableMissives < 0) minAvailableMissives = 0;
        if (minAvailableMissives > INVENTORY_SIZE) minAvailableMissives = INVENTORY_SIZE;
        if (maxAvailableMissives < minAvailableMissives) maxAvailableMissives = minAvailableMissives;
        if (maxAvailableMissives > INVENTORY_SIZE) maxAvailableMissives = INVENTORY_SIZE;

        this.minAvailableMissives = minAvailableMissives;
        this.maxAvailableMissives = maxAvailableMissives;
    }

    private boolean isMissiveLetter(ItemStack stack) {
        return stack != null && stack.getItem() == ELostTalesItem.MISSIVE_LETTER.getItem();
    }

    private void markDirtyAndSync() {
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public int getSizeInventory() {
        return this.inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.inventory.length ? this.inventory[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        if (slot < 0 || slot >= this.inventory.length || this.inventory[slot] == null) return null;

        ItemStack stack = this.inventory[slot];
        if (stack.stackSize <= count) {
            this.inventory[slot] = null;
            this.markDirtyAndSync();
            return stack;
        }

        ItemStack result = stack.splitStack(count);
        if (stack.stackSize <= 0) {
            this.inventory[slot] = null;
        }
        this.markDirtyAndSync();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= this.inventory.length) return null;
        ItemStack stack = this.inventory[slot];
        this.inventory[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.inventory.length) return;

        if (stack != null) {
            if (!this.isItemValidForSlot(slot, stack)) return;
            stack.stackSize = Math.min(stack.stackSize, this.getInventoryStackLimit());
        }
        this.inventory[slot] = stack;
        this.markDirtyAndSync();
    }

    @Override
    public String getInventoryName() {
        return "container.losttales.missive_board";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.worldObj != null
                && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
                && player.getDistanceSq((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D, (double) this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return this.isMissiveLetter(stack);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        this.lastGenerationWorldTime = nbt.getLong("LastGenerationWorldTime");
        this.nextGenerationWorldTime = nbt.getLong("NextGenerationWorldTime");
        this.applyConfiguredDefaults();
        this.generationIntervalTicks = nbt.hasKey("GenerationIntervalTicks") ? nbt.getLong("GenerationIntervalTicks") : this.generationIntervalTicks;
        if (this.generationIntervalTicks <= 0L) {
            this.generationIntervalTicks = getConfiguredGenerationIntervalTicks();
        }
        this.minAvailableMissives = nbt.hasKey("MinAvailableMissives") ? nbt.getInteger("MinAvailableMissives") : this.minAvailableMissives;
        this.maxAvailableMissives = nbt.hasKey("MaxAvailableMissives") ? nbt.getInteger("MaxAvailableMissives") : this.maxAvailableMissives;
        if (this.maxAvailableMissives == LEGACY_DEFAULT_MAX_AVAILABLE_MISSIVES && INVENTORY_SIZE > LEGACY_DEFAULT_MAX_AVAILABLE_MISSIVES) {
            this.maxAvailableMissives = Math.max(this.maxAvailableMissives, LostTalesConfig.missiveBoardMaxAvailable);
        }
        this.generationSequence = Math.max(0, nbt.getInteger("GenerationSequence"));
        this.nextExpirationCheckWorldTime = nbt.getLong("NextExpirationCheckWorldTime");
        this.applyMissiveRange(this.minAvailableMissives, this.maxAvailableMissives);

        for (int slot = 0; slot < this.inventory.length; slot++) {
            this.inventory[slot] = null;
        }

        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.inventory.length) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(itemTag);
                if (this.isItemValidForSlot(slot, stack)) {
                    this.inventory[slot] = stack;
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setLong("LastGenerationWorldTime", this.lastGenerationWorldTime);
        nbt.setLong("NextGenerationWorldTime", this.nextGenerationWorldTime);
        nbt.setLong("GenerationIntervalTicks", this.generationIntervalTicks);
        nbt.setInteger("MinAvailableMissives", this.minAvailableMissives);
        nbt.setInteger("MaxAvailableMissives", this.maxAvailableMissives);
        nbt.setInteger("GenerationSequence", this.generationSequence);
        nbt.setLong("NextExpirationCheckWorldTime", this.nextExpirationCheckWorldTime);

        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < this.inventory.length; slot++) {
            ItemStack stack = this.inventory[slot];
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) slot);
                stack.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        nbt.setTag("Items", list);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }
}
