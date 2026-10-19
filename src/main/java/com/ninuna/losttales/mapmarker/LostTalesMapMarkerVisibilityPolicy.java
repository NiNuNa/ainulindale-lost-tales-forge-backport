package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Who may see a marker, decided by the server before snapshots, discovery
 * and travel: a manager always, the character that owns it, anyone while
 * it is public, and while it is shared the characters it is shared with
 * and the members of its fellowships. Everything follows the character
 * played: the owner's account sees its waystone only while it plays the
 * owning character (WAY-08), and a fellowship share holds while the
 * character played is a member of that fellowship (WAY-09).
 */
public final class LostTalesMapMarkerVisibilityPolicy {
    private static boolean warnedUnreadable;

    private LostTalesMapMarkerVisibilityPolicy() {}

    public static boolean canView(
            LostTalesMapMarkerRecord record, EntityPlayerMP player) {
        return record != null && player != null
                && canView(record, Viewer.of(player));
    }

    public static boolean canView(
            LostTalesMapMarkerRecord record, Viewer viewer) {
        return viewer != null && canView(record, viewer.characterId,
                viewer.manager, viewer.fellowshipIds);
    }

    static boolean canView(
            LostTalesMapMarkerRecord record, UUID characterId,
            boolean manager, Collection<UUID> fellowshipIds) {
        if (record == null) {
            return false;
        }
        if (manager || characterId != null
                && characterId.equals(record.getOwnerCharacterId())) {
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

    /**
     * The ids of the fellowships of ours the player's played character is
     * a member of; none without a played character, and none where the
     * fellowship store cannot be read, so a share then shows nothing.
     */
    public static Set<UUID> fellowshipIdsOf(EntityPlayerMP player) {
        RoleplayCharacter played = player == null ? null
                : CharacterActiveResolver.get(player);
        return played == null ? Collections.<UUID>emptySet()
                : fellowshipIdsOf(player, played.getCharacterId());
    }

    private static Set<UUID> fellowshipIdsOf(EntityPlayerMP player,
                                             UUID characterId) {
        if (characterId == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return Collections.<UUID>emptySet();
        }
        try {
            LinkedHashSet<UUID> ids = new LinkedHashSet<UUID>();
            for (Fellowship fellowship : FellowshipStorage.get(
                    player.worldObj).getFellowshipsForIdentity(characterId)) {
                ids.add(fellowship.getFellowshipId());
            }
            return Collections.unmodifiableSet(ids);
        } catch (RuntimeException unreadable) {
            warnUnreadable(unreadable);
            return Collections.<UUID>emptySet();
        }
    }

    /** Said once: an unreadable store would otherwise be logged on every snapshot. */
    private static synchronized void warnUnreadable(RuntimeException cause) {
        if (warnedUnreadable) {
            return;
        }
        warnedUnreadable = true;
        LostTalesLog.warning("The fellowship store could not be read, so no waystone shared with a fellowship shows: %s",
                cause.toString());
    }

    /** A player as the policy reads them, looked up once for many markers. */
    public static final class Viewer {
        private final UUID characterId;
        private final boolean manager;
        private final Collection<UUID> fellowshipIds;

        private Viewer(UUID characterId, boolean manager,
                       Collection<UUID> fellowshipIds) {
            this.characterId = characterId;
            this.manager = manager;
            this.fellowshipIds = fellowshipIds == null
                    ? Collections.<UUID>emptySet() : fellowshipIds;
        }

        public static Viewer of(EntityPlayerMP player) {
            RoleplayCharacter played = CharacterActiveResolver.get(player);
            UUID characterId = played == null ? null
                    : played.getCharacterId();
            return new Viewer(characterId,
                    LostTalesWaystonePermissionPolicy.managesMarkers(player),
                    fellowshipIdsOf(player, characterId));
        }

        /** A viewer as the tests make one: the character played, a manager or not, its fellowships. */
        static Viewer of(UUID characterId, boolean manager,
                         Collection<UUID> fellowshipIds) {
            return new Viewer(characterId, manager, fellowshipIds);
        }

        /** Whether the viewer holds {@code mapmarker.manage}, which sees every marker. */
        public boolean isManager() {
            return this.manager;
        }
    }
}
