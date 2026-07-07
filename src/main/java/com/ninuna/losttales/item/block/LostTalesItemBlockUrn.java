package com.ninuna.losttales.item.block;

import net.minecraft.block.Block;

/**
 * Item form for urn blocks. The modern branch stacks urn items to 16; this keeps
 * that gameplay expectation without changing the 1.7.10 block registry names.
 */
public class LostTalesItemBlockUrn extends LostTalesItemBlockBase {

    public LostTalesItemBlockUrn(Block block) {
        super(block);
        this.setMaxStackSize(16);
    }
}
