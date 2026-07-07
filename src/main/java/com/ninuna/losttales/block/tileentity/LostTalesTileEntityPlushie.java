package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.util.LostTalesBlockRotationHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class LostTalesTileEntityPlushie extends TileEntity implements IAnimatable {
    private static final int CLIENT_EVENT_SQUEAK = 0;

    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean hasBeenClicked = false;
    private float rotation;
    private boolean hasStoredRotation;
    private boolean powered;

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<LostTalesTileEntityPlushie>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (hasBeenClicked) {
            hasBeenClicked = false;
            event.getController().setAnimation(new AnimationBuilder().addAnimation("squeak", ILoopType.EDefaultLoopTypes.PLAY_ONCE));
            event.getController().clearAnimationCache();
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    public void playSqueakAnimation() {
        this.hasBeenClicked = true;

        if (this.worldObj != null && !this.worldObj.isRemote && this.getBlockType() != null) {
            this.worldObj.addBlockEvent(this.xCoord, this.yCoord, this.zCoord, this.getBlockType(), CLIENT_EVENT_SQUEAK, 0);
        }
    }

    @Override
    public boolean receiveClientEvent(int eventId, int eventData) {
        if (eventId == CLIENT_EVENT_SQUEAK) {
            this.hasBeenClicked = true;
            return true;
        }
        return super.receiveClientEvent(eventId, eventData);
    }

    public void setRotation(float rotation) {
        this.rotation = LostTalesBlockRotationHelper.normalizeDegrees(rotation);
        this.hasStoredRotation = true;
        this.markDirtyAndSync();
    }

    public float getRotation() {
        return this.rotation;
    }

    public float getRenderRotation(int metadata) {
        return this.hasStoredRotation ? this.rotation : LostTalesBlockRotationHelper.getLegacyPlushieRenderRotation(metadata);
    }

    public boolean hasStoredRotation() {
        return this.hasStoredRotation;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            this.markDirty();
        }
    }

    private void markDirtyAndSync() {
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.hasStoredRotation = nbt.hasKey("rotation");
        this.rotation = this.hasStoredRotation ? LostTalesBlockRotationHelper.normalizeDegrees(nbt.getFloat("rotation")) : 0.0F;
        this.powered = nbt.getBoolean("powered");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.hasStoredRotation) {
            nbt.setFloat("rotation", this.rotation);
        }
        nbt.setBoolean("powered", this.powered);
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
