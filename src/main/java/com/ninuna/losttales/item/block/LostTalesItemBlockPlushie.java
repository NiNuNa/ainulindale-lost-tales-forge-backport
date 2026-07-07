package com.ninuna.losttales.item.block;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public class LostTalesItemBlockPlushie extends LostTalesItemBlockBase {
    private IIcon authoredItemIcon;

    public LostTalesItemBlockPlushie(Block block) {
        super(block);
        this.setMaxStackSize(1);
    }

    @Override
    public EnumRarity getRarity(ItemStack itemStack) {
        return ((LostTalesBlockPlushie)this.field_150939_a).getRarity();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        String blockName = getBlockTextureName();
        if (hasAuthoredItemIcon(blockName)) {
            this.authoredItemIcon = iconRegister.registerIcon(LostTalesMetaData.MOD_ID + ":" + blockName);
            this.itemIcon = this.authoredItemIcon;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int metadata) {
        if (this.authoredItemIcon != null) {
            return this.authoredItemIcon;
        }
        return this.field_150939_a.getIcon(2, metadata);
    }

    private String getBlockTextureName() {
        String unlocalizedName = this.field_150939_a.getUnlocalizedName();
        return unlocalizedName != null && unlocalizedName.startsWith("tile.") ? unlocalizedName.substring(5) : unlocalizedName;
    }

    private boolean hasAuthoredItemIcon(String blockName) {
        return "plushie_bear".equals(blockName) || "plushie_fox".equals(blockName);
    }
}
