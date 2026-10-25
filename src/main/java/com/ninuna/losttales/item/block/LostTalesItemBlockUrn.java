package com.ninuna.losttales.item.block;

import com.ninuna.losttales.block.custom.LostTalesBlockUrnBase;
import com.ninuna.losttales.block.vessel.VesselContents;
import com.ninuna.losttales.block.vessel.VesselItems;
import com.ninuna.losttales.block.vessel.VesselRules;
import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * A vessel as an item (CONTENT-09): empty ones stack to 16; one holding
 * items stands alone in its slot and lists what it holds in its tooltip.
 */
public class LostTalesItemBlockUrn extends LostTalesItemBlockBase {

    public LostTalesItemBlockUrn(Block block) {
        super(block);
        this.setMaxStackSize(VesselRules.EMPTY_STACK_LIMIT);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return VesselRules.stackLimit(stack != null
                && VesselContents.holdsItems(stack.getTagCompound()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        if (itemStack != null && this.field_150939_a instanceof LostTalesBlockUrnBase
                && VesselContents.holdsItems(itemStack.getTagCompound())) {
            LostTalesClientUtil.addVesselContents(list, VesselItems.contents(itemStack,
                    ((LostTalesBlockUrnBase) this.field_150939_a).getInventorySlots()));
        }
        super.addInformation(itemStack, player, list, advancedTooltips);
    }
}
