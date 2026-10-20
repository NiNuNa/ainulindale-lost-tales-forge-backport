package com.ninuna.losttales.progression;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.server.FellowshipService;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Who stands with a player in a fellowship (FEL-01): another player
 * playing a member of a fellowship the player's played character is in.
 * Read for Phalanx (PROG-06) and for deeds done together (PROG-11).
 */
public final class FellowshipCompanions {
    private FellowshipCompanions() {}

    /** Whether {@code other} plays a member of one of {@code player}'s fellowships. */
    public static boolean areCompanions(EntityPlayerMP player, EntityPlayerMP other) {
        if (player == null || other == null || player == other
                || player.getUniqueID().equals(other.getUniqueID())) {
            return false;
        }
        List<Fellowship> fellowships = FellowshipService.getInstance()
                .getFellowshipsForActiveIdentity(player);
        if (fellowships == null || fellowships.isEmpty()) {
            return false;
        }
        UUID identity = RoleplayCharacterIdentityHook.resolveGameplayId(other);
        if (identity == null) {
            return false;
        }
        for (Fellowship fellowship : fellowships) {
            if (fellowship.containsMember(identity)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a companion stands within {@code radius} blocks of the player. */
    public static boolean anyWithin(EntityPlayerMP player, double radius) {
        if (player == null || player.worldObj == null || !(radius > 0.0D)) {
            return false;
        }
        double reach = radius * radius;
        for (Object entry : player.worldObj.playerEntities) {
            if (entry instanceof EntityPlayerMP && entry != player
                    && ((EntityPlayer) entry).getDistanceSqToEntity(player) <= reach
                    && areCompanions(player, (EntityPlayerMP) entry)) {
                return true;
            }
        }
        return false;
    }
}
