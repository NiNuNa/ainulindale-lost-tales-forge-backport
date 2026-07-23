package com.ninuna.losttales.inventory.container;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Slotless container used to keep Forge's server GUI validation active. */
public final class LostTalesContainerWaystone extends Container {
    private final LostTalesTileEntityWaystone waystone;

    public LostTalesContainerWaystone(
            LostTalesTileEntityWaystone waystone) {
        if (waystone == null) {
            throw new IllegalArgumentException(
                    "waystone must not be null");
        }
        this.waystone = waystone;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.waystone.isUseableByPlayer(player);
    }

    public LostTalesTileEntityWaystone getWaystone() {
        return this.waystone;
    }
}
