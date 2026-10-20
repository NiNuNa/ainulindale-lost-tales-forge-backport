package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.progression.count.CountEvents;
import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.perk.PerkShares;
import com.ninuna.losttales.progression.perk.Perks;
import com.ninuna.losttales.progression.skill.PerkEffect;
import lotr.common.inventory.LOTRContainerAnvil;
import lotr.common.inventory.LOTRContainerTrade;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;

/**
 * Where the coremod's patches of LOTR call in for the perks that change
 * what LOTR keeps itself (Nils, 2026-10-19): Friend of the Wise's larger
 * alignment gains, Daggerwork's sooner strikes, Fair Dealer's prices,
 * and Repair and Master Smith at LOTR's anvil, which a repair taken
 * there also counts toward Smithing through. Every hook hands back what
 * LOTR gave it when the player took no such perk.
 */
public final class LostTalesLotrPerkPatchHooks {
    private LostTalesLotrPerkPatchHooks() {}

    /** A gain LOTR is about to add to one faction's alignment. */
    public static float alignmentGain(float gain, EntityPlayer player) {
        return PerkShares.gain(gain, Perks.of(player).total(PerkEffect.ALIGNMENT_GAIN));
    }

    /** The ticks LOTR blocks a player's next strike for, on the server. */
    public static int attackTime(int ticks, NetHandlerPlayServer handler) {
        EntityPlayerMP player = handler == null ? null : handler.playerEntity;
        return player == null ? ticks : attackTime(ticks, player);
    }

    /** The ticks the player's next strike waits for, on either side. */
    public static int attackTime(int ticks, EntityPlayer player) {
        double share = Perks.of(player).total(PerkEffect.DAGGER_WORK,
                Perks.weapon(player.getHeldItem()));
        return PerkShares.shorter(ticks, share);
    }

    /**
     * A trade's price as LOTR checks, takes and shows it: lower to buy,
     * higher for what the trader buys.
     */
    public static int tradeCost(int cost, EntityPlayer player, Slot slot) {
        double share = Perks.of(player).total(PerkEffect.FAIR_DEALER);
        if (!(share > 0.0D) || player == null || slot == null) {
            return cost;
        }
        boolean buying = player.openContainer instanceof LOTRContainerTrade
                && slot.inventory == ((LOTRContainerTrade) player.openContainer).tradeInvBuy;
        return buying ? PerkShares.lower(cost, share) : PerkShares.raise(cost, share);
    }

    /** What one offered stack sells for, as LOTR pays it. */
    public static int sellValue(int value, EntityPlayer player) {
        return PerkShares.raise(value, Perks.of(player).total(PerkEffect.FAIR_DEALER));
    }

    /**
     * LOTR's anvil has worked out its output and price: a repair restores
     * more for Master Smith and costs less for Repair, on both sides, so
     * the screen shows what the server takes.
     */
    public static void anvilOutput(LOTRContainerAnvil anvil) {
        if (anvil == null || anvil.thePlayer == null) {
            return;
        }
        ItemStack input = anvil.invInput.getStackInSlot(0);
        ItemStack output = anvil.invOutput.getStackInSlot(0);
        if (!isRepair(input, output)) {
            return;
        }
        PerkIndex perks = Perks.of(anvil.thePlayer);
        double more = perks.total(PerkEffect.REPAIR_MORE);
        if (more > 0.0D) {
            int restored = input.getItemDamage() - output.getItemDamage();
            output.setItemDamage(Math.max(0, output.getItemDamage()
                    - (int) Math.round(restored * more)));
        }
        anvil.materialCost = PerkShares.lower(anvil.materialCost,
                perks.total(PerkEffect.REPAIR_COST));
    }

    /** A repair taken from LOTR's anvil counts toward Smithing, by the material it took. */
    public static void anvilTaken(EntityPlayer player, ItemStack taken) {
        if (!(player instanceof EntityPlayerMP)
                || !(player.openContainer instanceof LOTRContainerAnvil)) {
            return;
        }
        LOTRContainerAnvil anvil = (LOTRContainerAnvil) player.openContainer;
        if (isRepair(anvil.invInput.getStackInSlot(0), taken) && anvil.materialCost > 0) {
            CountEvents.repaired((EntityPlayerMP) player, taken, anvil.materialCost);
        }
    }

    private static boolean isRepair(ItemStack input, ItemStack output) {
        return input != null && output != null && input.getItem() == output.getItem()
                && input.isItemStackDamageable()
                && output.getItemDamage() < input.getItemDamage();
    }
}
