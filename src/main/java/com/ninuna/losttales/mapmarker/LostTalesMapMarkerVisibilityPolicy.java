package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Who may see a marker, decided by the server before snapshots, discovery
 * and travel: an operator and the owner's account always, anyone while it
 * is public, and while it is shared the characters it is shared with and
 * the members of its fellowships. A share holds for the character played,
 * so an account sees it only while it plays that character.
 */
public final class LostTalesMapMarkerVisibilityPolicy {
    private LostTalesMapMarkerVisibilityPolicy() {}

    public static boolean canView(
            LostTalesMapMarkerRecord record, EntityPlayerMP player) {
        return record != null && player != null
                && canView(record, Viewer.of(player));
    }

    public static boolean canView(
            LostTalesMapMarkerRecord record, Viewer viewer) {
        return viewer != null && canView(record, viewer.accountId,
                viewer.characterId, viewer.operator, viewer.fellowshipIds);
    }

    static boolean canView(
            LostTalesMapMarkerRecord record,
            UUID accountId, UUID characterId, boolean operator,
            Collection<UUID> fellowshipIds) {
        if (record == null) {
            return false;
        }
        if (operator || accountId != null
                && accountId.equals(record.getOwnerPlayerId())) {
            return true;
        }
        if (record.getVisibility() == LostTalesMapMarkerVisibility.PUBLIC) {
            return true;
        }
        if (record.getVisibility() != LostTalesMapMarkerVisibility.SHARED) {
            return false;
        }
        if (characterId != null
                && record.getSharedCharacterIds().contains(characterId)) {
            return true;
        }
        if (fellowshipIds == null
                || record.getSharedFellowshipIds().isEmpty()) {
            return false;
        }
        for (UUID fellowshipId : fellowshipIds) {
            if (fellowshipId != null && record.getSharedFellowshipIds()
                    .contains(fellowshipId)) {
                return true;
            }
        }
        return false;
    }

    /** A player as the policy reads them, looked up once for many markers. */
    public static final class Viewer {
        private final UUID accountId;
        private final UUID characterId;
        private final boolean operator;
        private final Collection<UUID> fellowshipIds;

        private Viewer(UUID accountId, UUID characterId, boolean operator,
                       Collection<UUID> fellowshipIds) {
            this.accountId = accountId;
            this.characterId = characterId;
            this.operator = operator;
            this.fellowshipIds = fellowshipIds;
        }

        public static Viewer of(EntityPlayerMP player) {
            LOTRPlayerData lotrData = LOTRLevelData.getData(player);
            Collection<UUID> fellowshipIds = lotrData == null
                    ? Collections.<UUID>emptyList()
                    : lotrData.getFellowshipIDs();
            RoleplayCharacter played = CharacterActiveResolver.get(player);
            return new Viewer(player.getUniqueID(),
                    played == null ? null : played.getCharacterId(),
                    LostTalesWaystonePermissionPolicy.managesMarkers(player),
                    fellowshipIds);
        }
    }
}
