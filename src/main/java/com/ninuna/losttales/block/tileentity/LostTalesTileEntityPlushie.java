package com.ninuna.losttales.block.tileentity;

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

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
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
}