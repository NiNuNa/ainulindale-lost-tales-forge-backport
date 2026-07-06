package com.ninuna.losttales.rendering.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.rendering.model.tileentity.LostTalesTileEntityModelStatue;
import lotr.client.render.item.LOTRRenderBow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererStatue extends GeoBlockRenderer<LostTalesTileEntityStatue> {

    public LostTalesTileEntityRendererStatue() {
        super(new LostTalesTileEntityModelStatue());
    }

    @Override
    public void render(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (tileEntity.getBlockMetadata() != 4) {
            super.render(tileEntity, x, y, z, partialTicks);
        } else {
            LostTalesTileEntityStatue weaponRack = (LostTalesTileEntityStatue)tileEntity;
            GL11.glPushMatrix();
            GL11.glDisable(2884);
            GL11.glEnable(32826);
            GL11.glEnable(3008);
            GL11.glTranslatef((float)x + 0.5F, (float)y + 1.5F, (float)z + 0.5F);
            GL11.glScalef(-1.0F, -1.0F, 1.0F);
            ItemStack weaponItem = weaponRack.getWeaponItem();
            if (weaponItem != null) {
                float weaponScale = 0.625F;
                GL11.glScalef(weaponScale, weaponScale, weaponScale);
                GL11.glScalef(-1.0F, 1.0F, 1.0F);
                GL11.glTranslatef(0.0F, 0.52F, 0.0F);
                GL11.glRotatef(45.0F, 0.0F, 0.0F, 1.0F);
                GL11.glTranslatef(0.9375F, 0.0625F, 0.0F);
                GL11.glRotatef(-335.0F, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(-50.0F, 0.0F, 1.0F, 0.0F);
                GL11.glScalef(0.6666667F, 0.6666667F, 0.6666667F);
                GL11.glTranslatef(0.0F, 0.3F, 0.0F);
                RenderManager renderManager = RenderManager.instance;
                int passes = 1;
                if (weaponItem.getItem().requiresMultipleRenderPasses()) {
                    passes = weaponItem.getItem().getRenderPasses(weaponItem.getItemDamage());
                }

                LOTRRenderBow.renderingWeaponRack = true;

                for(int pass = 0; pass < passes; ++pass) {
                    int color = weaponItem.getItem().getColorFromItemStack(weaponItem, pass);
                    float r = (float)(color >> 16 & 255) / 255.0F;
                    float g = (float)(color >> 8 & 255) / 255.0F;
                    float b = (float)(color & 255) / 255.0F;
                    GL11.glColor4f(r, g, b, 1.0F);
                    renderManager.itemRenderer.renderItem(weaponRack.getEntityForRender(), weaponItem, 0, IItemRenderer.ItemRenderType.EQUIPPED);
                }
                LOTRRenderBow.renderingWeaponRack = false;
            }

            GL11.glEnable(2884);
            GL11.glDisable(32826);
            GL11.glPopMatrix();
            this.renderWeaponName(weaponRack, x + 0.5, y + 0.75, z + 0.5);
        }
    }

    private void renderWeaponName(LostTalesTileEntityStatue rack, double d, double d1, double d2) {
        MovingObjectPosition mop = Minecraft.getMinecraft().objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mop.blockX == rack.xCoord && mop.blockY == rack.yCoord && mop.blockZ == rack.zCoord) {
            ItemStack weaponItem = rack.getWeaponItem();
            if (Minecraft.isGuiEnabled() && weaponItem != null && weaponItem.hasDisplayName()) {
                RenderManager renderManager = RenderManager.instance;
                FontRenderer fontRenderer = this.func_147498_b();
                float f = 1.6F;
                float f1 = 0.016666668F * f;
                double dSq = renderManager.livingPlayer.getDistanceSq((double) rack.xCoord + 0.5, (double) rack.yCoord + 0.5, (double) rack.zCoord);
                float f2 = 64.0F;
                if (dSq < (double) (f2 * f2)) {
                    String name = weaponItem.getDisplayName();
                    GL11.glPushMatrix();
                    GL11.glTranslatef((float) d, (float) d1 + 0.5F, (float) d2);
                    GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                    GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
                    GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
                    GL11.glScalef(-f1, -f1, f1);
                    GL11.glDisable(2896);
                    GL11.glDepthMask(false);
                    GL11.glDisable(2929);
                    GL11.glEnable(3042);
                    OpenGlHelper.glBlendFunc(770, 771, 1, 0);
                    Tessellator tessellator = Tessellator.instance;
                    byte b0 = 0;
                    GL11.glDisable(3553);
                    tessellator.startDrawingQuads();
                    int j = fontRenderer.getStringWidth(name) / 2;
                    tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.25F);
                    tessellator.addVertex((double) (-j - 1), (double) (-1 + b0), 0.0);
                    tessellator.addVertex((double) (-j - 1), (double) (8 + b0), 0.0);
                    tessellator.addVertex((double) (j + 1), (double) (8 + b0), 0.0);
                    tessellator.addVertex((double) (j + 1), (double) (-1 + b0), 0.0);
                    tessellator.draw();
                    GL11.glEnable(3553);
                    fontRenderer.drawString(name, -fontRenderer.getStringWidth(name) / 2, b0, 553648127);
                    GL11.glEnable(2929);
                    GL11.glDepthMask(true);
                    fontRenderer.drawString(name, -fontRenderer.getStringWidth(name) / 2, b0, -1);
                    GL11.glEnable(2896);
                    GL11.glDisable(3042);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GL11.glPopMatrix();
                }
            }
        }
    }
}