package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.SetBonusesInForce;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The coremod's way into vanilla's effect column (CONTENT-12):
 * {@code InventoryEffectRenderer.initGui} asks the player's potion
 * effects whether they are empty through {@link #noEffects}, so the
 * inventory moves over for the set bonuses in force exactly as it does
 * for a potion, before the screen places anything from its left edge.
 */
public final class SetBonusColumnHooks {
    private SetBonusColumnHooks() {}

    /** No potion effect and no set bonus in force: nothing for the column to show. */
    public static boolean noEffects(Collection effects) {
        return effects.isEmpty() && !bonusInForce();
    }

    private static boolean bonusInForce() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft == null ? null : minecraft.thePlayer;
        return player != null
                && SetBonusesInForce.any(Armour.worn(player), Armour.settings(player));
    }
}
