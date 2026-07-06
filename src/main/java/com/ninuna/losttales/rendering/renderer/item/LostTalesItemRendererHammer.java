package com.ninuna.losttales.rendering.renderer.item;

import com.ninuna.losttales.item.weapon.LostTalesItemWarhammer;
import com.ninuna.losttales.rendering.model.item.LostTalesItemModelTestHammer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.renderers.geo.GeoItemRenderer;

public class LostTalesItemRendererHammer extends GeoItemRenderer<LostTalesItemWarhammer> {

    public LostTalesItemRendererHammer() {
        super(new LostTalesItemModelTestHammer());
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack itemStack, Object... var3) {
        GL11.glPushMatrix();
        GL11.glScalef(2.5F, 2.5F, 2.5F);
        GL11.glRotated(45, 0,1,0);
        GL11.glRotated(-65, 1,0,0);
        GL11.glTranslated(-0.5,-1.3,0);
        this.render((LostTalesItemWarhammer) itemStack.getItem(), itemStack);
        GL11.glPopMatrix();
    }

    @Override
    public boolean handleRenderType(ItemStack itemStack, ItemRenderType type) {
        switch(type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                return true;
            default:
                return false;
        }
    }
}