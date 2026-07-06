package com.ninuna.losttales.rendering.model.tileentity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class LostTalesTileEntityModelUrn extends AnimatedGeoModel<LostTalesTileEntityUrn> {

    @Override
    public ResourceLocation getModelLocation(LostTalesTileEntityUrn tileEntityUrn) {
        String modelName = this.getModelName(tileEntityUrn);
        return modelName == null ? null : new ResourceLocation(LostTalesMetaData.MOD_ID, "geo/" + modelName + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(LostTalesTileEntityUrn tileEntityUrn) {
        String modelName = this.getModelName(tileEntityUrn);
        return modelName == null ? null : new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/models/" + modelName + ".png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(LostTalesTileEntityUrn tileEntityUrn) {
        return new ResourceLocation(LostTalesMetaData.MOD_ID, this.isLoutrophoros(tileEntityUrn.getBlockType()) ? "animations/urn_tall.animation.json" : "animations/urn.animation.json");
    }

    private String getModelName(LostTalesTileEntityUrn tileEntityUrn) {
        Block block = tileEntityUrn.getBlockType();
        if (this.isUrn(block)) {
            return tileEntityUrn.isSealed() ? "urn_sealed" : "urn";
        }
        if (this.isAmphora(block)) {
            return tileEntityUrn.isSealed() ? "urn_amphora_sealed" : "urn_amphora";
        }
        if (this.isLoutrophoros(block)) {
            return tileEntityUrn.isSealed() ? "urn_loutrophoros_sealed" : "urn_loutrophoros";
        }
        return null;
    }

    private boolean isUrn(Block block) {
        return block != null && block.getUnlocalizedName().equals(ELostTalesBlock.URN.getBlock().getUnlocalizedName());
    }

    private boolean isAmphora(Block block) {
        return block != null && (block.getUnlocalizedName().equals(ELostTalesBlock.URN_AMPHORA.getBlock().getUnlocalizedName())
                || block.getUnlocalizedName().equals(ELostTalesBlock.URN_AMPHORA_ANCIENT.getBlock().getUnlocalizedName()));
    }

    private boolean isLoutrophoros(Block block) {
        return block != null && (block.getUnlocalizedName().equals(ELostTalesBlock.URN_LOUTROPHOROS.getBlock().getUnlocalizedName())
                || block.getUnlocalizedName().equals(ELostTalesBlock.URN_LOUTROPHOROS_ANCIENT.getBlock().getUnlocalizedName()));
    }
}
