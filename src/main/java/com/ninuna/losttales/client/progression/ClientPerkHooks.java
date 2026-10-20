package com.ninuna.losttales.client.progression;

import com.ninuna.losttales.compat.lotr.LostTalesLotrPerkPatchHooks;
import com.ninuna.losttales.progression.perk.PerkShares;
import com.ninuna.losttales.progression.perk.Perks;
import com.ninuna.losttales.progression.skill.PerkEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

/**
 * Where the coremod's patches of LOTR's client call in, so what the
 * screen shows matches what the server does: Daggerwork's sooner
 * strikes in LOTR's attack timer, Fair Dealer's prices on LOTR's trade
 * screen.
 */
public final class ClientPerkHooks {
    private ClientPerkHooks() {}

    /** The ticks LOTR's client waits before the player's next strike. */
    public static int attackTime(int ticks) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return player == null ? ticks : LostTalesLotrPerkPatchHooks.attackTime(ticks, player);
    }

    /** A trade's price as LOTR's trade screen draws it. */
    public static int shownTradeCost(int cost, Slot slot) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return LostTalesLotrPerkPatchHooks.tradeCost(cost, player, slot);
    }

    /** What an offered stack sells for, as LOTR's trade screen adds it up. */
    public static int shownSellValue(int value) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return PerkShares.raise(value, Perks.of(player).total(PerkEffect.FAIR_DEALER));
    }
}
