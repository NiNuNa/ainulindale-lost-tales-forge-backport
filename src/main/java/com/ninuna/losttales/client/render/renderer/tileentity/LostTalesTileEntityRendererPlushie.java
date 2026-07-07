package com.ninuna.losttales.client.render.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.client.render.model.tileentity.LostTalesTileEntityModelPlushie;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.util.Color;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererPlushie extends GeoBlockRenderer<LostTalesTileEntityPlushie> {

    public LostTalesTileEntityRendererPlushie() {
        super(new LostTalesTileEntityModelPlushie());
    }

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof LostTalesTileEntityPlushie)) {
            return;
        }

        LostTalesTileEntityPlushie plushie = (LostTalesTileEntityPlushie) tile;
        ResourceLocation modelLocation = this.getGeoModelProvider().getModelLocation(plushie);
        if (modelLocation == null) {
            return;
        }

        GeoModel model = this.getGeoModelProvider().getModel(modelLocation);
        this.getGeoModelProvider().setLivingAnimations(plushie, this.getUniqueID(plushie));
        int light = 15;
        if (tile.getWorldObj() != null) {
            light = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        }

        int lx = light % 65536;
        int ly = light / 65536;
        if (tile.xCoord != 0 && tile.yCoord != 0 && tile.zCoord != 0) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)lx, (float)ly);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.0F, 0.01F, 0.0F);
        GlStateManager.translate(0.5, 0.0, 0.5);

        GlStateManager.rotate(plushie.getRenderRotation(tile.getBlockMetadata()), 0.0F, 1.0F, 0.0F);

        Minecraft.getMinecraft().renderEngine.bindTexture(this.getTextureLocation(plushie));
        Color renderColor = this.getRenderColor(plushie, partialTicks);
        this.render(model, plushie, partialTicks, (float)renderColor.getRed() / 255.0F, (float)renderColor.getGreen() / 255.0F, (float)renderColor.getBlue() / 255.0F, (float)renderColor.getAlpha() / 255.0F);
        GlStateManager.popMatrix();
    }
}
