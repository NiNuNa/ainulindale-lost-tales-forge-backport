package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.skill.WeaponKind;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The perks a player's played character took, on either side, as far as
 * the armour the player wears lets them work (PROG-17): the server reads
 * its saved progression, the client what the server sent of its own
 * player. Every other player reads as none on a client.
 */
public final class Perks {
    private Perks() {}

    public static PerkIndex of(EntityPlayer player) {
        if (player == null || player.worldObj == null) {
            return PerkIndex.EMPTY;
        }
        PerkIndex taken;
        if (player.worldObj.isRemote) {
            taken = LostTalesMod.proxy == null ? PerkIndex.EMPTY
                    : LostTalesMod.proxy.clientPerkIndex(player);
        } else {
            ProgressionPlayerData data = ProgressionPlayerData.get(player);
            taken = data == null ? PerkIndex.EMPTY : data.perkIndex();
        }
        if (taken == null) {
            return PerkIndex.EMPTY;
        }
        return taken.hasArmourPerks() ? taken.armoured(Armour.worn(player)) : taken;
    }

    /** A weapon or tool as a perk's list may name it: its kind, or its item name. */
    public static PerkIndex.WeaponMatch weapon(final ItemStack stack) {
        final WeaponKind kind = LotrWeaponKinds.kindOf(stack);
        return new PerkIndex.WeaponMatch() {
            @Override
            public boolean matches(List<String> names) {
                return LotrWeaponKinds.names(names, kind, stack);
            }
        };
    }

    /** A weapon known only by its kind, as a projectile shows it. */
    public static PerkIndex.WeaponMatch kind(final WeaponKind kind, final ItemStack stack) {
        return new PerkIndex.WeaponMatch() {
            @Override
            public boolean matches(List<String> names) {
                return LotrWeaponKinds.names(names, kind, stack);
            }
        };
    }
}
