package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.storage.NbtTags;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

/**
 * The block's link to its marker record: the marker id and the link token,
 * nothing else; the world repository owns the record. The block sends its
 * clients nothing, so a private waystone's name never reaches a game that
 * only loads its chunk (WAY-10).
 */
public final class LostTalesTileEntityWaystone
        extends TileEntity implements IAnimatable {
    /**
     * How near a player stands to use the waystone, squared, measured to
     * the block's middle: eight blocks. The waystone's page closes once
     * its player is further than this.
     */
    public static final double REACH_SQ = 64.0D;
    private final AnimationFactory factory = new AnimationFactory(this);

    private String markerId = "";
    private UUID linkToken;
    private boolean reconciled;

    @Override
    public void registerControllers(AnimationData data) {}

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Override
    public void updateEntity() {
        if (this.reconciled || this.worldObj == null
                || this.worldObj.isRemote) {
            return;
        }
        this.reconciled = true;
        if (!isLinked()) {
            return;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(this.worldObj);
        LostTalesMapMarkerRecord record = data.findByLinkedPosition(
                this.worldObj.provider.dimensionId,
                this.xCoord, this.yCoord, this.zCoord);
        if (record == null
                || !this.markerId.equals(record.getId())
                || !this.linkToken.equals(record.getLinkToken())) {
            clearLink();
        }
    }

    public void linkTo(LostTalesMapMarkerRecord record) {
        if (record == null || !record.isLinked()
                || record.getLinkToken() == null) {
            throw new IllegalArgumentException(
                    "waystone record must have a live link");
        }
        this.markerId = record.getId();
        this.linkToken = record.getLinkToken();
        this.reconciled = true;
        markDirty();
    }

    public void clearLink() {
        this.markerId = "";
        this.linkToken = null;
        markDirty();
    }

    public boolean isLinked() {
        return this.markerId.length() > 0 && this.linkToken != null;
    }

    public String getMarkerId() { return this.markerId; }
    public UUID getLinkToken() { return this.linkToken; }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return player != null && this.worldObj != null
                && this.worldObj.getTileEntity(
                        this.xCoord, this.yCoord, this.zCoord) == this
                && this.worldObj.getBlock(
                        this.xCoord, this.yCoord, this.zCoord)
                        == ELostTalesBlock.WAYSTONE.getBlock()
                && player.getDistanceSq(
                        this.xCoord + 0.5D,
                        this.yCoord + 0.5D,
                        this.zCoord + 0.5D) <= REACH_SQ;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("MarkerId", this.markerId);
        NbtTags.writeUuid(tag, "LinkToken", this.linkToken);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.markerId = bounded(tag.getString("MarkerId"), 256, "");
        this.linkToken = NbtTags.readUuid(tag, "LinkToken");
        if (this.markerId.length() == 0 || this.linkToken == null) {
            this.markerId = "";
            this.linkToken = null;
        }
        this.reconciled = false;
    }

    private static String bounded(
            String value, int maximumLength, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() == 0
                || normalized.length() > maximumLength) {
            return fallback;
        }
        return normalized;
    }
}
