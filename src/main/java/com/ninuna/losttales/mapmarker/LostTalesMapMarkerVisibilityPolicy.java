package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Visibility is evaluated by the server before snapshots or discovery. */
public final class LostTalesMapMarkerVisibilityPolicy {
    private LostTalesMapMarkerVisibilityPolicy() {}

    public static boolean canView(
            LostTalesMapMarkerRecord record, EntityPlayerMP player) {
        if (record == null || !record.isActive() || player == null) {
            return false;
        }
        return canView(record, player.getUniqueID(),
                LostTalesWaystonePermissionPolicy.isOperator(player));
    }

    public static boolean canView(
            LostTalesMapMarkerRecord record,
            UUID playerId, boolean operator) {
        if (record == null || !record.isActive()) {
            return false;
        }
        if (operator
                || playerId != null
                && playerId.equals(record.getOwnerPlayerId())) {
            return true;
        }
        if (record.getVisibility()
                == LostTalesMapMarkerVisibility.PUBLIC) {
            return true;
        }
        return record.getVisibility()
                == LostTalesMapMarkerVisibility.SHARED
                && playerId != null
                && record.getSharedPlayerIds().contains(playerId);
    }
}
