package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * Main-thread server authority for waystone settings. The block link and
 * repository revision are revalidated for every request, and every
 * answer goes back to the player as the waystone's state, saying why.
 *
 * <p>A player who may not see the waystone is told only that it is
 * private, in one Server line, and is sent no state (WAY-10). A player
 * whose page is open on a waystone they can no longer see is told so,
 * once, and the page closes on that line ({@link #checkSight}).</p>
 *
 * <p>A waystone is shared with characters and with fellowships of ours.
 * A character share holds for that character alone, whoever plays it,
 * never for its account's other characters. A fellowship is named among
 * the fellowships of the sharer's played character (WAY-09). A share whose
 * character or fellowship is gone for good counts for nothing: the page
 * is never sent it, and the next change to the waystone lets it go
 * ({@link #withoutLostShares}). A manager may hand a placed waystone to
 * another character (WAY-08).</p>
 *
 * <p>Destinations is a request too: the server checks the waystone as it
 * checks every other, opens the player's window to travel from it
 * ({@link LostTalesWaystoneVisits}), and answers, and the page opens the
 * map on that answer.</p>
 */
public final class LostTalesWaystoneSettingsService {
    private static final double MIN_DISCOVERY_RADIUS = 1.0D;
    /** The longest icon name a waystone's owner may save. */
    static final int MAX_ICON_LENGTH = 64;
    /** The one line a player who may not see the waystone is told. */
    public static final String PRIVATE_LINE = "chat.losttales.waystone.private";
    /** The line a player is told whose page is open on a waystone they no longer may see. */
    public static final String OUT_OF_SIGHT_LINE =
            "chat.losttales.waystone.out_of_sight";

    private LostTalesWaystoneSettingsService() {}

    /**
     * The player used a waystone. It passes the checks every request
     * passes — it stands where its record links it, within the player's
     * reach, holding the record's link token — and its state goes to the
     * player as an opening, which opens the waystone's page. A waystone
     * that fails them opens nothing, and one the player may not see says
     * it is private.
     */
    public static boolean open(
            EntityPlayerMP player, LostTalesTileEntityWaystone tile) {
        if (player == null || tile == null) {
            return false;
        }
        Context context = resolve(player, tile.xCoord, tile.yCoord,
                tile.zCoord, tile.getMarkerId());
        if (context == null || context.tile != tile) {
            return false;
        }
        if (!LostTalesMapMarkerVisibilityPolicy.canView(
                context.record, player)) {
            deny(player, PRIVATE_LINE);
            return false;
        }
        LostTalesWaystoneVisits.opened(player.getUniqueID(),
                context.record.getId(), context.world.provider.dimensionId,
                tile.xCoord, tile.yCoord, tile.zCoord);
        sendState(player, context.tile, context.record,
                LostTalesWaystoneStateReason.OPENED);
        return true;
    }

    public static void apply(
            EntityPlayerMP player,
            LostTalesWaystoneSettingsRequestPacket request) {
        if (player == null || request == null || request.isMalformed()) {
            return;
        }
        Context context = resolve(player, request.getX(), request.getY(),
                request.getZ(), request.getMarkerId());
        if (context == null) {
            deny(player, "chat.losttales.waystone.invalid");
            return;
        }
        // Before the revision: a stale request from a stranger must not
        // bring the waystone's state back either.
        if (!LostTalesMapMarkerVisibilityPolicy.canView(
                context.record, player)) {
            deny(player, PRIVATE_LINE);
            return;
        }
        if (request.getOperation()
                == LostTalesWaystoneSettingsOperation.DESTINATIONS) {
            destinations(player, context, System.currentTimeMillis());
            return;
        }
        if (context.record.getRevision() != request.getExpectedRevision()) {
            refuse(player, context, LostTalesWaystoneStateReason.STALE);
            return;
        }
        if (!LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                player, context.record, context.world,
                request.getX(), request.getY(), request.getZ(), true)) {
            refuse(player, context, LostTalesWaystoneStateReason.DENIED);
            return;
        }

        // Every change starts from the shares that still stand, so one
        // whose character or fellowship is gone goes with it.
        LostTalesMapMarkerRecord base = withoutLostShares(context.record,
                subjectsOf(context.world));
        Outcome outcome;
        switch (request.getOperation()) {
            case SAVE:
                outcome = applySettings(base,
                        request.getSettings(),
                        LostTalesWaystonePermissionPolicy.canMakePublic(
                                player));
                break;
            case SHARE_CHARACTER:
                outcome = applyCharacterSharing(
                        context.world, base,
                        request.getTargetName(), false);
                break;
            case UNSHARE_CHARACTER:
                outcome = applyCharacterSharing(
                        context.world, base,
                        request.getTargetName(), true);
                break;
            case SHARE_FELLOWSHIP:
                outcome = shareFellowship(base,
                        sharersFellowships(player),
                        request.getTargetName());
                break;
            case UNSHARE_FELLOWSHIP:
                outcome = unshareFellowship(base,
                        sharedFellowshipNames(context.world, base),
                        request.getTargetName());
                break;
            case HAND_OVER:
                outcome = handOver(base,
                        LostTalesWaystonePermissionPolicy.managesMarkers(
                                player),
                        namedCharacter(context.world,
                                request.getTargetName()));
                break;
            default:
                outcome = Outcome.refused(
                        LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        if (outcome.refusal != null) {
            refuse(player, context, outcome.refusal);
            return;
        }

        LostTalesMapMarkerRecord updated = outcome.updated;
        try {
            context.data.saveRecord(updated);
            context.tile.linkTo(updated);
        } catch (RuntimeException exception) {
            refuse(player, context,
                    LostTalesWaystoneStateReason.SAVE_FAILED);
            return;
        }
        LostTalesMapMarkerSyncManager.syncViewersOf(context.record, updated);
        sendState(player, context.tile, updated,
                LostTalesWaystoneStateReason.SAVED);
        player.addChatMessage(new ChatComponentTranslation(
                LostTalesWaystoneStateReason.SAVED.getMessageKey()));
    }

    /**
     * Destinations: a waystone with fast travel, from Middle-earth, opens
     * the player's window to travel from it at {@code nowMillis}, and the
     * answer tells the page to open the map. Anyone who may see the
     * waystone may travel from it; the revision is not asked, since
     * nothing on the waystone changes.
     */
    private static void destinations(EntityPlayerMP player, Context context,
                                     long nowMillis) {
        if (!LostTalesLotrWaystoneTravelAdapter.canTravelFrom(
                player, context.record)) {
            refuse(player, context,
                    LostTalesWaystoneStateReason.NO_FAST_TRAVEL);
            return;
        }
        LostTalesWaystoneVisits.destinations(player.getUniqueID(),
                context.record.getId(), context.world.provider.dimensionId,
                context.tile.xCoord, context.tile.yCoord,
                context.tile.zCoord, nowMillis);
        sendState(player, context.tile, context.record,
                LostTalesWaystoneStateReason.DESTINATIONS);
    }

    /**
     * Whether the player's page still shows a waystone they may see. One
     * whose page is open on a waystone they no longer may see — unshared,
     * made private, handed over, another character played, a fellowship
     * left — is told so, once, and their page closes on that line. A
     * player who walked away, or whose waystone is gone, is
     * forgotten: their page has closed by itself. Asked as the player is
     * sent their markers, which every such change brings about.
     */
    public static void checkSight(EntityPlayerMP player,
                                  LostTalesMapMarkerWorldData data) {
        if (player == null || data == null) {
            return;
        }
        LostTalesWaystoneVisits.Visit visit =
                LostTalesWaystoneVisits.get(player.getUniqueID());
        if (visit == null) {
            return;
        }
        LostTalesMapMarkerRecord record = data.getRecord(visit.getMarkerId());
        if (record == null || player.dimension != visit.getDimensionId()
                || player.getDistanceSq(visit.getX() + 0.5D,
                        visit.getY() + 0.5D, visit.getZ() + 0.5D)
                        > LostTalesTileEntityWaystone.REACH_SQ) {
            LostTalesWaystoneVisits.forget(player.getUniqueID());
            return;
        }
        if (!LostTalesMapMarkerVisibilityPolicy.canView(record, player)) {
            LostTalesWaystoneVisits.forget(player.getUniqueID());
            deny(player, OUT_OF_SIGHT_LINE);
        }
    }

    /**
     * The waystone's state for its page, with the owning character's name
     * and only the shares that still stand; nothing to a player who may not
     * see it, who could otherwise read a private waystone through a
     * request.
     */
    public static void sendState(
            EntityPlayerMP player,
            LostTalesTileEntityWaystone tile,
            LostTalesMapMarkerRecord record,
            LostTalesWaystoneStateReason reason) {
        if (player == null || tile == null || record == null
                || reason == null || player.worldObj == null
                || !tile.isUseableByPlayer(player)
                || !LostTalesMapMarkerVisibilityPolicy.canView(
                        record, player)) {
            return;
        }
        boolean manages =
                LostTalesWaystonePermissionPolicy.managesMarkers(player);
        boolean canEdit =
                LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                        player, record, player.worldObj,
                        tile.xCoord, tile.yCoord, tile.zCoord, false);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesWaystoneStatePacket(
                        player.dimension,
                        tile.xCoord, tile.yCoord, tile.zCoord,
                        withoutLostShares(record,
                                subjectsOf(player.worldObj)),
                        ownerName(player.worldObj, record),
                        canEdit, manages, reason),
                player);
    }

    /**
     * A request turned down: the chat says why, and the state as it
     * stands goes back to the page saying the same.
     */
    private static void refuse(
            EntityPlayerMP player, Context context,
            LostTalesWaystoneStateReason reason) {
        deny(player, reason.getMessageKey());
        sendState(player, context.tile, context.record, reason);
    }

    /**
     * The waystone at a place in the player's own world, linked to the
     * marker named and within the player's reach, its record linked back
     * to that very block by the same token; null where any of it fails.
     */
    private static Context resolve(
            EntityPlayerMP player, int x, int y, int z, String markerId) {
        World world = player.worldObj;
        if (world == null || world.isRemote || markerId == null
                || markerId.length() == 0
                || player.dimension != world.provider.dimensionId) {
            return null;
        }
        // Reach first: the position comes from the client, and reading a
        // tile entity there could load a chunk far from the player.
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D)
                > LostTalesTileEntityWaystone.REACH_SQ) {
            return null;
        }
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof LostTalesTileEntityWaystone)) {
            return null;
        }
        LostTalesTileEntityWaystone tile =
                (LostTalesTileEntityWaystone)raw;
        if (!tile.isUseableByPlayer(player)
                || !tile.isLinked()
                || !markerId.equals(tile.getMarkerId())) {
            return null;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(world);
        LostTalesMapMarkerRecord record = data.getRecord(markerId);
        if (record == null || !record.isLinkedTo(
                world.provider.dimensionId, x, y, z, tile.getLinkToken())) {
            return null;
        }
        return new Context(world, tile, data, record);
    }

    /**
     * The settings a Save asks for, checked and put on the record. Only
     * turning a waystone public needs {@code canMakePublic}: one public
     * already saves its other changes for its owner (WAY-13). A placed
     * waystone's discovery radius stops at
     * {@link LostTalesMapMarkerRecord#MAX_PLACED_DISCOVERY_RADIUS} (WAY-15).
     */
    static Outcome applySettings(
            LostTalesMapMarkerRecord record,
            LostTalesMapMarkerEditableSettings requested,
            boolean canMakePublic) {
        if (record == null || requested == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        String name = trim(requested.getName());
        String icon = trim(requested.getIconName());
        String color = normalizeColor(requested.getColorName());
        String category = trim(requested.getCategoryName());
        String description = trim(requested.getDescription());
        double compassRadius = requested.getCompassFadeInRadius();
        double discoveryRadius = requested.getDiscoveryRadius();
        LostTalesMapMarkerVisibility visibility =
                requested.getVisibility();
        // An empty name gives a waystone back to what it is called after;
        // one called after nothing must keep a name.
        if (name.length() == 0 && record.getNamedAfter().length() == 0
                || name.length()
                        > LostTalesMapMarkerRecord.MAX_NAME_LENGTH
                || !isIconName(icon)
                || color == null
                || category.length()
                        > LostTalesMapMarkerRecord.MAX_NAME_LENGTH
                || description.length()
                        > LostTalesMapMarkerRecord.MAX_TEXT_LENGTH
                || !validRadius(compassRadius, 0.0D,
                        LostTalesMapMarkerRecord.MAX_RADIUS)
                || !validRadius(discoveryRadius, MIN_DISCOVERY_RADIUS,
                        LostTalesMapMarkerRecord.maxDiscoveryRadius(
                                record.getSource()))
                || requested.getPriority()
                        < LostTalesMapMarkerDefinition.MIN_PRIORITY
                || requested.getPriority()
                        > LostTalesMapMarkerDefinition.MAX_PRIORITY
                || visibility == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        if (visibility == LostTalesMapMarkerVisibility.PUBLIC
                && record.getVisibility()
                        != LostTalesMapMarkerVisibility.PUBLIC
                && !canMakePublic) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.PUBLIC_DENIED);
        }
        LostTalesMapMarkerEditableSettings normalized =
                new LostTalesMapMarkerEditableSettings(
                        name, icon, color, category, description,
                        requested.hasFastTravel(),
                        compassRadius, discoveryRadius,
                        requested.isHiddenUntilDiscovered(),
                        requested.isDiscoverable(),
                        requested.requiresRegionUnlock(),
                        requested.getPriority(),
                        visibility);
        try {
            return Outcome.of(record.withEditableSettings(normalized));
        } catch (IllegalArgumentException exception) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
    }

    private static boolean validRadius(
            double value, double minimum, double maximum) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= minimum && value <= maximum;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Whether {@code icon} reads as an icon's name: up to
     * {@link #MAX_ICON_LENGTH} ASCII letters, digits, underscores and
     * hyphens, as every icon is called. The page offers no other, and a
     * marker's icon travels to everyone who can see it.
     */
    static boolean isIconName(String icon) {
        if (icon == null || icon.length() == 0
                || icon.length() > MAX_ICON_LENGTH) {
            return false;
        }
        for (int index = 0; index < icon.length(); index++) {
            char letter = icon.charAt(index);
            if (!(letter >= 'a' && letter <= 'z')
                    && !(letter >= 'A' && letter <= 'Z')
                    && !(letter >= '0' && letter <= '9')
                    && letter != '_' && letter != '-') {
                return false;
            }
        }
        return true;
    }

    /* ---- Sharing and handing over ---- */

    /** Which characters and fellowships still stand, as the shares are kept against. */
    interface Subjects {
        /** Whether a roster or a lore figure holds the character. */
        boolean hasCharacter(UUID characterId);

        /** Whether the fellowship has not ended. */
        boolean hasFellowship(UUID fellowshipId);
    }

    /**
     * The record without the shares whose character or fellowship is gone
     * for good: a character no roster and no lore figure holds, purged; a
     * fellowship that ended. Nobody could see the waystone through them,
     * and nobody could name them to unshare them. The revision stays, so a
     * page that read the record still matches it; the record itself is
     * the same where every share stands.
     */
    static LostTalesMapMarkerRecord withoutLostShares(
            LostTalesMapMarkerRecord record, Subjects subjects) {
        Set<UUID> characters = new LinkedHashSet<UUID>();
        for (UUID characterId : record.getSharedCharacterIds()) {
            if (subjects.hasCharacter(characterId)) {
                characters.add(characterId);
            }
        }
        Set<UUID> fellowships = new LinkedHashSet<UUID>();
        for (UUID fellowshipId : record.getSharedFellowshipIds()) {
            if (subjects.hasFellowship(fellowshipId)) {
                fellowships.add(fellowshipId);
            }
        }
        if (characters.size() == record.getSharedCharacterIds().size()
                && fellowships.size()
                        == record.getSharedFellowshipIds().size()) {
            return record;
        }
        return record.toBuilder()
                .sharedCharacterIds(characters)
                .sharedFellowshipIds(fellowships)
                .build();
    }

    /**
     * This world's characters, lore figures and fellowships as the shares
     * are kept against. A store that cannot be read, or reads only
     * another version's data, holds every subject, so no share is let go
     * on its word.
     */
    private static Subjects subjectsOf(World world) {
        CharacterWorldData characters = null;
        LoreCharacterOwnershipWorldData loreFigures = null;
        FellowshipWorldData fellowshipData = null;
        try {
            characters = CharacterStorage.get(world);
            loreFigures = LoreCharacterOwnershipStorage.get(world);
            if (characters.isReadOnlyForNewerVersion()
                    || loreFigures.isReadOnly()) {
                characters = null;
            }
        } catch (RuntimeException unreadable) {
            characters = null;
        }
        try {
            fellowshipData = FellowshipStorage.get(world);
            if (fellowshipData.isReadOnlyForNewerVersion()) {
                fellowshipData = null;
            }
        } catch (RuntimeException unreadable) {
            fellowshipData = null;
        }
        final CharacterWorldData rosters = characters;
        final LoreCharacterOwnershipWorldData lore = loreFigures;
        final FellowshipWorldData fellowships = fellowshipData;
        return new Subjects() {
            @Override
            public boolean hasCharacter(UUID characterId) {
                return rosters == null
                        || rosters.containsCharacter(characterId)
                        || lore.getRecordByCharacterId(characterId) != null;
            }

            @Override
            public boolean hasFellowship(UUID fellowshipId) {
                return fellowships == null
                        || fellowships.getFellowship(fellowshipId) != null;
            }
        };
    }

    private static Outcome applyCharacterSharing(
            World world,
            LostTalesMapMarkerRecord record,
            String characterName, boolean remove) {
        CharacterWorldData characters;
        try {
            characters = CharacterStorage.get(world);
        } catch (RuntimeException unreadable) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.CHARACTER_NOT_FOUND);
        }
        if (remove) {
            return unshareCharacter(record,
                    sharedNamed(characters, record, characterName));
        }
        RoleplayCharacter named = CharacterNames.named(
                characters.getRosters(), characterName);
        return shareCharacter(record,
                named == null ? null : named.getCharacterId());
    }

    /**
     * The waystone shared with the character found by name; one already
     * sharing it is refused before the limit is counted.
     */
    static Outcome shareCharacter(LostTalesMapMarkerRecord record,
                                  UUID characterId) {
        if (characterId == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.CHARACTER_NOT_FOUND);
        }
        if (record.getSharedCharacterIds().contains(characterId)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.ALREADY_SHARED);
        }
        if (record.getSharedCharacterIds().size()
                >= LostTalesMapMarkerRecord.MAX_SHARED_CHARACTERS) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.SHARE_LIMIT);
        }
        Set<UUID> shared =
                new LinkedHashSet<UUID>(record.getSharedCharacterIds());
        shared.add(characterId);
        return Outcome.of(record.withSharedCharacters(shared));
    }

    /** The waystone no longer shared with the character it is shared with; null names none. */
    static Outcome unshareCharacter(LostTalesMapMarkerRecord record,
                                    UUID sharedCharacterId) {
        if (sharedCharacterId == null || !record.getSharedCharacterIds()
                .contains(sharedCharacterId)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.NOT_SHARED);
        }
        Set<UUID> shared =
                new LinkedHashSet<UUID>(record.getSharedCharacterIds());
        shared.remove(sharedCharacterId);
        return Outcome.of(record.withSharedCharacters(shared));
    }

    /**
     * The waystone shared with the fellowship of the sharer's that goes by
     * the name, among {@code sharersFellowships} (id to name): the
     * fellowships the sharer's played character is a member of. One
     * already sharing it is refused before the limit is counted.
     */
    static Outcome shareFellowship(LostTalesMapMarkerRecord record,
                                   Map<UUID, String> sharersFellowships,
                                   String fellowshipName) {
        UUID fellowshipId = fellowshipNamed(sharersFellowships,
                fellowshipName);
        if (fellowshipId == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.FELLOWSHIP_NOT_FOUND);
        }
        if (record.getSharedFellowshipIds().contains(fellowshipId)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.ALREADY_SHARED);
        }
        if (record.getSharedFellowshipIds().size()
                >= LostTalesMapMarkerRecord.MAX_SHARED_FELLOWSHIPS) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.SHARE_LIMIT);
        }
        Set<UUID> shared = new LinkedHashSet<UUID>(
                record.getSharedFellowshipIds());
        shared.add(fellowshipId);
        return Outcome.of(record.withSharedFellowships(shared));
    }

    /**
     * The waystone no longer shared with the fellowship among its shares
     * that goes by the name; {@code sharedNames} names the shared
     * fellowships that still stand.
     */
    static Outcome unshareFellowship(LostTalesMapMarkerRecord record,
                                     Map<UUID, String> sharedNames,
                                     String fellowshipName) {
        UUID fellowshipId = fellowshipNamed(sharedNames, fellowshipName);
        if (fellowshipId == null || !record.getSharedFellowshipIds()
                .contains(fellowshipId)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.NOT_SHARED);
        }
        Set<UUID> shared = new LinkedHashSet<UUID>(
                record.getSharedFellowshipIds());
        shared.remove(fellowshipId);
        return Outcome.of(record.withSharedFellowships(shared));
    }

    /**
     * The placed waystone owned by the character found by name, handed
     * over by a manager. Its old owner keeps it only through a share, and
     * it stays called after whom it was (WAY-01).
     */
    static Outcome handOver(LostTalesMapMarkerRecord record,
                            boolean manages, UUID characterId) {
        if (!manages || record.getSource()
                != LostTalesMapMarkerSource.PLAYER_CREATED) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.HAND_OVER_DENIED);
        }
        if (characterId == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.CHARACTER_NOT_FOUND);
        }
        return Outcome.of(record.withOwner(characterId));
    }

    /** The fellowship among {@code fellowships} (id to name) that goes by the name, whatever its case; null for none. */
    static UUID fellowshipNamed(Map<UUID, String> fellowships,
                                String fellowshipName) {
        String wanted = trim(fellowshipName).toLowerCase(Locale.ROOT);
        if (fellowships == null || wanted.length() == 0) {
            return null;
        }
        for (Map.Entry<UUID, String> fellowship : fellowships.entrySet()) {
            if (fellowship.getKey() != null && fellowship.getValue() != null
                    && trim(fellowship.getValue()).toLowerCase(Locale.ROOT)
                            .equals(wanted)) {
                return fellowship.getKey();
            }
        }
        return null;
    }

    /** The fellowships of the player's played character, id to name; none without one. */
    private static Map<UUID, String> sharersFellowships(
            EntityPlayerMP player) {
        Map<UUID, String> named = new LinkedHashMap<UUID, String>();
        RoleplayCharacter played = CharacterActiveResolver.get(player);
        FellowshipWorldData data = fellowships(player.worldObj);
        if (played == null || data == null) {
            return named;
        }
        for (Fellowship fellowship
                : data.getFellowshipsForIdentity(played.getCharacterId())) {
            named.put(fellowship.getFellowshipId(), fellowship.getName());
        }
        return named;
    }

    /** The record's shared fellowships that still stand, id to name. */
    private static Map<UUID, String> sharedFellowshipNames(
            World world, LostTalesMapMarkerRecord record) {
        Map<UUID, String> named = new LinkedHashMap<UUID, String>();
        FellowshipWorldData data = fellowships(world);
        if (data == null) {
            return named;
        }
        for (UUID fellowshipId : record.getSharedFellowshipIds()) {
            Fellowship fellowship = data.getFellowship(fellowshipId);
            if (fellowship != null) {
                named.put(fellowshipId, fellowship.getName());
            }
        }
        return named;
    }

    private static FellowshipWorldData fellowships(World world) {
        try {
            return world == null ? null : FellowshipStorage.get(world);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** The one character in this world's rosters that goes by the name; null for none. */
    private static UUID namedCharacter(World world, String characterName) {
        try {
            RoleplayCharacter named = CharacterNames.named(
                    CharacterStorage.get(world).getRosters(), characterName);
            return named == null ? null : named.getCharacterId();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The owning character's name as its page reads it; empty for a
     * waystone nobody owns, an owner no roster holds, or a name past what
     * the state carries.
     */
    private static String ownerName(World world,
                                    LostTalesMapMarkerRecord record) {
        UUID owner = record.getOwnerCharacterId();
        if (owner == null) {
            return "";
        }
        try {
            RoleplayCharacter character =
                    CharacterStorage.get(world).findCharacter(owner);
            String name = character == null || character.getName() == null
                    ? "" : character.getName().trim();
            return name.getBytes(StandardCharsets.UTF_8).length
                    <= LostTalesWaystoneStatePacket.MAX_OWNER_NAME_BYTES
                    ? name : "";
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    /**
     * The character already shared with that goes by the name, deleted or
     * not, so one that is no longer played can still be taken off.
     */
    private static UUID sharedNamed(CharacterWorldData characters,
                                    LostTalesMapMarkerRecord record,
                                    String characterName) {
        for (UUID shared : record.getSharedCharacterIds()) {
            RoleplayCharacter character = characters.findCharacter(shared);
            if (character != null
                    && CharacterNames.same(characterName, character.getName())) {
                return shared;
            }
        }
        return null;
    }

    private static String normalizeColor(String value) {
        String color = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT)
                        .replace(' ', '_').replace('-', '_');
        if ("white".equals(color) || "red".equals(color)
                || "green".equals(color) || "blue".equals(color)
                || "yellow".equals(color) || "gold".equals(color)
                || "orange".equals(color) || "purple".equals(color)
                || "violet".equals(color) || "gray".equals(color)
                || "grey".equals(color) || "dark_gray".equals(color)
                || "dark_grey".equals(color) || "black".equals(color)) {
            return color;
        }
        String hex = color;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        if (hex.matches("[0-9a-f]{6}")) {
            return "#" + hex;
        }
        return null;
    }

    private static void deny(EntityPlayerMP player, String key) {
        if (player != null) {
            player.addChatMessage(new ChatComponentTranslation(key));
        }
    }

    /** What a request comes to: the record it makes, or why it was turned down. */
    static final class Outcome {
        private final LostTalesMapMarkerRecord updated;
        private final LostTalesWaystoneStateReason refusal;

        private Outcome(LostTalesMapMarkerRecord updated,
                        LostTalesWaystoneStateReason refusal) {
            this.updated = updated;
            this.refusal = refusal;
        }

        static Outcome of(LostTalesMapMarkerRecord updated) {
            return updated == null ? refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS)
                    : new Outcome(updated, null);
        }

        static Outcome refused(LostTalesWaystoneStateReason reason) {
            return new Outcome(null, reason);
        }

        /** The record the request makes; null where it was turned down. */
        LostTalesMapMarkerRecord updated() {
            return this.updated;
        }

        /** Why it was turned down; null where it passed. */
        LostTalesWaystoneStateReason refusal() {
            return this.refusal;
        }
    }

    private static final class Context {
        private final World world;
        private final LostTalesTileEntityWaystone tile;
        private final LostTalesMapMarkerWorldData data;
        private final LostTalesMapMarkerRecord record;

        private Context(
                World world, LostTalesTileEntityWaystone tile,
                LostTalesMapMarkerWorldData data,
                LostTalesMapMarkerRecord record) {
            this.world = world;
            this.tile = tile;
            this.data = data;
            this.record = record;
        }
    }
}
