package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.storage.NbtTags;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.inventory.SlotFurnace;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

/**
 * The cook's mark (PROG-15): food a cook with a Cooking perk
 * takes from an oven, a furnace or a crafting grid carries their name,
 * and their own food stacks together. Hearty Meals and Feast work when
 * the cook eats what they cooked, Master Cook when anyone eats it.
 * Ale-wise lightens LOTR's drink as it is drunk.
 */
public final class PerkFood {
    /** The mark's tag on a stack of food. */
    public static final String MARK = "LostTalesCook";
    private static final String NAME = "Name";
    private static final String COOK = "Cook";
    private static final String MASTER = "Master";

    /** Marks the food waiting in an open oven's, furnace's or grid's output for its cook. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.openContainer == null
                || event.player.openContainer == event.player.inventoryContainer) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PerkIndex perks = Perks.of(player);
        if (!perks.has(PerkEffect.HEARTY_MEALS) && !perks.has(PerkEffect.FEAST)
                && !perks.has(PerkEffect.MASTER_COOK)) {
            return;
        }
        RoleplayCharacter cook = ProgressionService.playedCharacter(player);
        if (cook == null) {
            return;
        }
        for (Object entry : player.openContainer.inventorySlots) {
            if (!(entry instanceof SlotFurnace) && !(entry instanceof SlotCrafting)) {
                continue;
            }
            ItemStack food = ((Slot) entry).getStack();
            if (food != null && food.getItem() instanceof ItemFood && mark(food) == null) {
                NBTTagCompound mark = new NBTTagCompound();
                mark.setString(NAME, cook.getName());
                NbtTags.writeUuid(mark, COOK, cook.getCharacterId());
                double master = perks.total(PerkEffect.MASTER_COOK);
                if (master > 0.0D) {
                    mark.setDouble(MASTER, master);
                }
                if (!food.hasTagCompound()) {
                    food.setTagCompound(new NBTTagCompound());
                }
                food.getTagCompound().setTag(MARK, mark);
            }
        }
    }

    @SubscribeEvent
    public void onEaten(PlayerUseItemEvent.Finish event) {
        EntityPlayer player = event.entityPlayer;
        if (!(player instanceof EntityPlayerMP) || event.item == null) {
            return;
        }
        PerkIndex perks = Perks.of(player);
        if (LotrPerkHooks.isMug(event.item)) {
            double wise = Math.min(1.0D, perks.total(PerkEffect.ALE_WISE));
            PotionEffect drunk = player.getActivePotionEffect(Potion.confusion);
            if (wise > 0.0D && drunk != null) {
                player.removePotionEffect(Potion.confusion.id);
                int left = (int) Math.round(drunk.getDuration() * (1.0D - wise));
                if (left > 0) {
                    player.addPotionEffect(new PotionEffect(Potion.confusion.id, left,
                            drunk.getAmplifier()));
                }
            }
            return;
        }
        NBTTagCompound mark = mark(event.item);
        if (mark == null || !(event.item.getItem() instanceof ItemFood)) {
            return;
        }
        ItemFood food = (ItemFood) event.item.getItem();
        int hunger = food.func_150905_g(event.item);
        float saturation = food.func_150906_h(event.item);
        double more = mark.getDouble(MASTER);
        RoleplayCharacter eater = ProgressionService.playedCharacter((EntityPlayerMP) player);
        UUID cook = NbtTags.readUuid(mark, COOK);
        if (eater != null && eater.getCharacterId().equals(cook)) {
            more += perks.total(PerkEffect.HEARTY_MEALS);
            double feast = perks.total(PerkEffect.FEAST);
            if (feast > 0.0D) {
                player.addPotionEffect(new PotionEffect(Potion.regeneration.id,
                        (int) Math.round(feast * 20.0D), 0));
            }
        }
        int extra = (int) Math.round(hunger * more);
        if (extra > 0) {
            player.getFoodStats().addStats(extra, saturation);
        }
    }

    /** The cook's mark on a stack, or null. */
    public static NBTTagCompound mark(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(MARK, 10)
                ? stack.getTagCompound().getCompoundTag(MARK) : null;
    }

    /** The name a cook's mark carries, or null for unmarked food. */
    public static String cookName(ItemStack stack) {
        NBTTagCompound mark = mark(stack);
        return mark == null ? null : mark.getString(NAME);
    }
}
