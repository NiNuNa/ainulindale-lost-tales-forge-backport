package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Visibility is evaluated by the server before snapshots or discovery. */
public final class LostTalesMapMarkerVisibilityPolicy {
    private LostTalesMapMarkerVisibilityPolicy() {}

    public static boolean canView(
            LostTalesMapMarkerRecord record, EntityPlayerMP player) {
        if (record == null || player == null) {
            return false;
        }
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        Collection<UUID> fellowshipIds = lotrData == null
                ? Collections.<UUID>emptyList()
                : lotrData.getFellowshipIDs();
        return canView(record, player.getUniqueID(),
                LostTalesWaystonePermissionPolicy.isOperator(player),
                fellowshipIds);
    }

    public static boolean canView(
            LostTalesMapMarkerRecord record,
            UUID playerId, boolean operator) {
        if (record == null) {
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

    static boolean canView(
            LostTalesMapMarkerRecord record,
            UUID playerId, boolean operator,
            Collection<UUID> fellowshipIds) {
        if (canView(record, playerId, operator)) {
            return true;
        }
        if (record == null || record.getVisibility()
                != LostTalesMapMarkerVisibility.SHARED
                || fellowshipIds == null
                || fellowshipIds.isEmpty()
                || record.getSharedFellowshipIds().isEmpty()) {
            return false;
        }
        for (UUID fellowshipId : fellowshipIds) {
            if (fellowshipId != null
                    && record.getSharedFellowshipIds()
                            .contains(fellowshipId)) {
                return true;
            }
        }
        return false;
    }
}
