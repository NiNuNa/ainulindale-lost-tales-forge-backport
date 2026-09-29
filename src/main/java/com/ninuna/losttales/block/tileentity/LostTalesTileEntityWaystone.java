package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.storage.NbtTags;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

/** Minimal local link/presentation state; the world repository owns the record. */
public final class LostTalesTileEntityWaystone
        extends TileEntity implements IAnimatable {
    public static final int CURRENT_DATA_VERSION = 1;
    /**
     * How near a player stands to use the waystone, squared, measured to
     * the block's middle: eight blocks. The waystone's page closes once
     * its player is further than this.
     */
    public static final double REACH_SQ = 64.0D;
    private final AnimationFactory factory = new AnimationFactory(this);

    private String markerId = "";
    private UUID linkToken;
    private UUID ownerPlayerId;
    private String displayName = "Waystone";
    private LostTalesMapMarkerVisibility visibility =
            LostTalesMapMarkerVisibility.PRIVATE;
    private long markerRevision;
    private int sharedPlayerCount;
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
            return;
        }
        applyPresentation(record);
    }

    public void linkTo(LostTalesMapMarkerRecord record) {
        if (record == null || !record.isLinked()
                || record.getLinkToken() == null) {
            throw new IllegalArgumentException(
                    "waystone record must have a live link");
        }
        this.markerId = record.getId();
        this.linkToken = record.getLinkToken();
        this.ownerPlayerId = record.getOwnerPlayerId();
        applyPresentation(record);
        this.reconciled = true;
        markDirtyAndSync();
    }

    public void clearLink() {
        this.markerId = "";
        this.linkToken = null;
        this.ownerPlayerId = null;
        this.displayName = "Waystone";
        this.visibility = LostTalesMapMarkerVisibility.PRIVATE;
        this.markerRevision = 0L;
        this.sharedPlayerCount = 0;
        markDirtyAndSync();
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
        tag.setInteger("DataVersion", CURRENT_DATA_VERSION);
        tag.setString("MarkerId", this.markerId);
        NbtTags.writeUuid(tag, "LinkToken", this.linkToken);
        NbtTags.writeUuid(tag, "Owner", this.ownerPlayerId);
        tag.setString("DisplayName", this.displayName);
        tag.setString("Visibility",
                this.visibility.getSerializedName());
        tag.setLong("MarkerRevision", this.markerRevision);
        tag.setInteger("SharedPlayerCount", this.sharedPlayerCount);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.markerId = bounded(tag.getString("MarkerId"), 256, "");
        this.linkToken = NbtTags.readUuid(tag, "LinkToken");
        this.ownerPlayerId = NbtTags.readUuid(tag, "Owner");
        this.displayName = bounded(
                tag.getString("DisplayName"), 256, "Waystone");
        this.visibility =
                LostTalesMapMarkerVisibility.forSerializedName(
                        tag.getString("Visibility"),
                        LostTalesMapMarkerVisibility.PRIVATE);
        this.markerRevision = Math.max(
                0L, tag.getLong("MarkerRevision"));
        this.sharedPlayerCount = Math.max(
                0, tag.getInteger("SharedPlayerCount"));
        if (this.markerId.length() == 0 || this.linkToken == null) {
            this.markerId = "";
            this.linkToken = null;
        }
        this.reconciled = false;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MarkerId", this.markerId);
        tag.setString("DisplayName", this.displayName);
        tag.setString("Visibility",
                this.visibility.getSerializedName());
        tag.setLong("MarkerRevision", this.markerRevision);
        tag.setInteger("SharedPlayerCount", this.sharedPlayerCount);
        return new S35PacketUpdateTileEntity(
                this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(
            NetworkManager manager, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.markerId = bounded(tag.getString("MarkerId"), 256, "");
        this.displayName = bounded(
                tag.getString("DisplayName"), 256, "Waystone");
        this.visibility =
                LostTalesMapMarkerVisibility.forSerializedName(
                        tag.getString("Visibility"),
                        LostTalesMapMarkerVisibility.PRIVATE);
        this.markerRevision = Math.max(
                0L, tag.getLong("MarkerRevision"));
        this.sharedPlayerCount = Math.max(
                0, tag.getInteger("SharedPlayerCount"));
    }

    private void applyPresentation(LostTalesMapMarkerRecord record) {
        this.displayName = record.getName();
        this.visibility = record.getVisibility();
        this.markerRevision = record.getRevision();
        this.sharedPlayerCount = record.getSharedPlayerIds().size();
    }

    private void markDirtyAndSync() {
        markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(
                    this.xCoord, this.yCoord, this.zCoord);
        }
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
