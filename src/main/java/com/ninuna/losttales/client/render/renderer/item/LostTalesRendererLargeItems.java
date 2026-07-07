package com.ninuna.losttales.client.render.renderer.item;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.weapon.LostTalesItemPike;
import com.ninuna.losttales.item.weapon.LostTalesItemSpear;
import lotr.client.LOTRClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class LostTalesRendererLargeItems implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        switch(type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack itemstack, Object... data) {
        GL11.glPushMatrix();

        Item item = itemstack.getItem();
        Entity holder = (Entity)data[1];
        boolean isFirstPerson = holder == Minecraft.getMinecraft().thePlayer && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
        EntityLivingBase entityLiving;

        Tessellator tessellator = Tessellator.instance;
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);

        if (item instanceof LostTalesItemSpear && holder instanceof EntityPlayer && ((EntityPlayer)holder).getItemInUse() == itemstack) {
            GL11.glRotatef(260.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-1.0F, 0.0F, 0.0F);
        }

        if (item instanceof LostTalesItemPike && holder instanceof EntityLivingBase) {
            entityLiving = (EntityLivingBase)holder;
            if (entityLiving.getHeldItem() == itemstack && entityLiving.swingProgress <= 0.0F) {
                if (entityLiving.isSneaking()) {
                    if (isFirstPerson) {
                        GL11.glRotatef(270.0F, 0.0F, 0.0F, 1.0F);
                        GL11.glTranslatef(-1.0F, 0.0F, 0.0F);
                    } else {
                        GL11.glTranslatef(0.0F, -0.1F, 0.0F);
                        GL11.glRotatef(20.0F, 0.0F, 0.0F, 1.0F);
                    }
                } else if (!isFirstPerson) {
                    GL11.glTranslatef(0.0F, -0.3F, 0.0F);
                    GL11.glRotatef(40.0F, 0.0F, 0.0F, 1.0F);
                }
            }
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        IIcon icon = getIcon(item);

        if (item instanceof LostTalesItemPike) {
            GL11.glTranslatef(-(3 - 1.0F) / 2.0F, -(3 - 1.0F) / 2.0F, 0.0F);
            GL11.glScalef(3, 3, 1.0F);
        } else {
            GL11.glTranslatef(-(2 - 1.0F) / 2.0F, -(2 - 1.0F) / 2.0F, 0.0F);
            GL11.glScalef(2, 2, 1.0F);
        }

        ItemRenderer.renderItemIn2D(tessellator, icon.getMaxU(), icon.getMinV(), icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);

        if (itemstack.hasEffect(0)) {
            LOTRClientProxy.renderEnchantmentEffect();
        }

        GL11.glPopMatrix();
    }

    private IIcon getIcon(Item item) {
        for (ELostTalesItem largeItem : ELostTalesItem.values()) {
            if (largeItem.getItem().getUnlocalizedName().equals(item.getUnlocalizedName())) {
                return largeItem.getLargeIcon();
            }
        }
        return null;
    }
}