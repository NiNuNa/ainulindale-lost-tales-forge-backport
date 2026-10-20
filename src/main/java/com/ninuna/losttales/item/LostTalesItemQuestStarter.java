package com.ninuna.losttales.item;

import com.ninuna.losttales.quest.QuestStarters;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * A quest starter (QUEST-39): a sealed sheet naming its quest, written by
 * {@link QuestStarters}. Its name is built on each reader's side from what
 * it carries, as a missive letter's is, never kept as written. Using it is
 * read by the server's quest events, which start its quest through the
 * gate and use it up.
 */
public class LostTalesItemQuestStarter extends Item {
    public LostTalesItemQuestStarter() {
        this.setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String title = QuestStarters.titleOf(stack);
        return title.length() == 0 ? super.getItemStackDisplayName(stack)
                : StatCollector.translateToLocalFormatted("item.quest_starter.named", title);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list,
                               boolean advancedTooltips) {
        String questId = QuestStarters.questIdOf(stack);
        if (questId.length() == 0) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                    "item.quest_starter.blank"));
            return;
        }
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                "item.quest_starter.use"));
        if (advancedTooltips) {
            list.add(EnumChatFormatting.DARK_GRAY + questId);
        }
    }

    /** The game's own paper stands in until the starter has a picture of its own. */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("paper");
    }
}
