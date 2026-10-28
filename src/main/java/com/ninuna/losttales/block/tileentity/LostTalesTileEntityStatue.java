package com.ninuna.losttales.block.tileentity;

import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

/** The watch stone's block entity: its GeckoLib model and nothing saved. */
public class LostTalesTileEntityStatue extends TileEntity implements IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);

    @Override
    public void registerControllers(AnimationData data) {}

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }
}
