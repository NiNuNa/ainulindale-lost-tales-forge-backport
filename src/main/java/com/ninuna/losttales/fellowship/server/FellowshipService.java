package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterIndex;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.compat.lotr.LotrFellowshipRules;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipGoHereMarkerStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipGoHereMarkerWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipMarkStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipMarkWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The one place fellowships change.
 *
 * <p>Public methods run on the logical server thread. Every changing
 * method is synchronized, so an invitation taken and a fellowship filling up
 * stay one step even when several requests arrive in the same tick. Every
 * change a player asks for names the fellowship and the revision it was made
 * against, and a fellowship changed since refuses it. A character may be in
 * several fellowships, and one account has one character in a fellowship at
 * most. LOTR's own rules for fellowships hold: its size setting, its switch
 * for making them, and how many one character may lead.</p>
 */
public final class FellowshipService {

    public static final long INVITATION_LIFETIME_MILLIS = 5L * 60L * 1000L;

    private static final int UUID_GENERATION_ATTEMPTS = 8;
    private static final FellowshipService INSTANCE = new FellowshipService();

    private final FellowshipInvitationCoordinator invitationCoordinator =
            new FellowshipInvitationCoordinator(this);
    private final LeadLimits leadLimits = new LeadLimits() {
        @Override
        public int of(FellowshipMember member) {
            return leadLimitOf(member);
        }
    };

    private FellowshipService() {}

    public static FellowshipService getInstance() {
        return INSTANCE;
    }

    /**
     * A new fellowship, with the character played as its leader. Refused
     * while the server lets no one make fellowships, while the character is
     * in as many as it can be or leads as many as LOTR lets it, and for a
     * name that is missing, not allowed, or one of its fellowships has.
     */
    public synchronized FellowshipOperationResult createFellowship(EntityPlayerMP player,
                                                                   String requestedName) {
        ActiveIdentityContext context = resolveActiveIdentity(player);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        if (fellowshipData == null) {
            return FellowshipOperationResult.failure(FellowshipErrorId.INTERNAL_ERROR, null);
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY, null);
        }
        if (!ensureFellowshipIntegrity(player.worldObj, fellowshipData,
                context.characterData)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }
        List<Fellowship> joined = fellowshipData.getFellowshipsForIdentity(
                context.gameplayId());
        FellowshipErrorId refusal = createRefusal(player, context.gameplayId(), joined);
        if (refusal != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(refusal, null);
        }
        String name = requestedName == null ? "" : requestedName.trim();
        FellowshipErrorId nameError = checkName(name, ChatProfanityCatalog.effective());
        if (nameError == FellowshipErrorId.NONE && hasFellowshipNamed(joined, name, null)) {
            nameError = FellowshipErrorId.NAME_IN_USE;
        }
        if (nameError != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(nameError, null);
        }

        UUID fellowshipId = createUniqueFellowshipId(fellowshipData);
        if (fellowshipId == null) {
            return FellowshipOperationResult.failure(FellowshipErrorId.INTERNAL_ERROR, null);
        }
        long now = System.currentTimeMillis();
        FellowshipMember leader = new FellowshipMember(context.gameplayId(),
                context.ownerId(), context.displayName, now, FellowshipColor.GREEN);
        Fellowship fellowship = Fellowship.createNew(fellowshipId, name, leader, now);
        try {
            fellowshipData.saveFellowship(fellowship);
            return FellowshipOperationResult.success(true, fellowship, leader);
        } catch (RuntimeException exception) {
            logFailure("create", player, exception);
            return FellowshipOperationResult.failure(FellowshipErrorId.INTERNAL_ERROR, null);
        }
    }

    /**
     * The character played leaves a fellowship. A leader with others in it
     * hands it on first, so it never loses its leader to a slip; alone,
     * leaving ends the fellowship.
     */
    public synchronized FellowshipOperationResult leaveFellowship(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, context.fellowship);
        }
        UUID leavingIdentityId = context.gameplayId();
        Fellowship fellowship = context.fellowship;
        FellowshipMember leaving = fellowship.getMember(leavingIdentityId);
        boolean leaderLeaving = fellowship.isLeader(leavingIdentityId);
        if (leaderLeaving && fellowship.getMemberCount() > 1) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.LEADER_MUST_HAND_OVER, fellowship);
        }
        invitationData.removeInvitationsSentBy(fellowship.getFellowshipId(),
                leavingIdentityId);
        if (fellowship.getMemberCount() == 1) {
            invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
            context.fellowshipData.removeFellowship(fellowship.getFellowshipId());
            forgetMarks(player.worldObj, fellowship.getFellowshipId());
            return FellowshipOperationResult.disbanded(fellowship, leaving);
        }
        fellowship.removeMember(leavingIdentityId);
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, leaving);
    }

    /**
     * The leader or a guide sends a member away, as in LOTR: never the
     * leader, and never themselves, who leave instead.
     */
    public synchronized FellowshipOperationResult removeMember(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID targetIdentityId) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.canManage(context.gameplayId())) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.NOT_LEADER_OR_GUIDE, fellowship);
        }
        FellowshipMember target = fellowship.getMember(targetIdentityId);
        if (target == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.TARGET_NOT_MEMBER, fellowship);
        }
        if (fellowship.isLeader(targetIdentityId)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.CANNOT_REMOVE_LEADER, fellowship);
        }
        if (targetIdentityId.equals(context.gameplayId())) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVALID_TARGET, fellowship);
        }
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, fellowship);
        }
        invitationData.removeInvitationsSentBy(fellowship.getFellowshipId(),
                targetIdentityId);
        fellowship.removeMember(targetIdentityId);
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, target);
    }

    /** The leader ends a fellowship; its invitations go with it. */
    public synchronized FellowshipOperationResult disbandFellowship(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.isLeader(context.gameplayId())) {
            return FellowshipOperationResult.failure(FellowshipErrorId.NOT_LEADER, fellowship);
        }
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, fellowship);
        }
        invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
        context.fellowshipData.removeFellowship(fellowship.getFellowshipId());
        forgetMarks(player.worldObj, fellowship.getFellowshipId());
        return FellowshipOperationResult.disbanded(fellowship, fellowship.getLeader());
    }

    /**
     * The leader makes another member the leader. Refused while that
     * member's character leads as many fellowships as LOTR lets it. The old
     * leader is no guide, so the invitations it sent are taken back.
     */
    public synchronized FellowshipOperationResult transferLeadership(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID targetIdentityId) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.isLeader(context.gameplayId())) {
            return FellowshipOperationResult.failure(FellowshipErrorId.NOT_LEADER, fellowship);
        }
        FellowshipMember target = fellowship.getMember(targetIdentityId);
        if (target == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.TARGET_NOT_MEMBER, fellowship);
        }
        if (fellowship.isLeader(targetIdentityId)) {
            return FellowshipOperationResult.success(false, fellowship, target);
        }
        if (countLed(context.fellowshipData.getFellowshipsForIdentity(targetIdentityId),
                targetIdentityId) >= leadLimitOf(target)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.LEAD_LIMIT_REACHED, fellowship);
        }
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, fellowship);
        }
        if (!fellowship.transferLeadership(targetIdentityId)) {
            return FellowshipOperationResult.success(false, fellowship, target);
        }
        invitationData.removeInvitationsSentBy(fellowship.getFellowshipId(),
                context.gameplayId());
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, target);
    }

    /** The leader makes a member a guide, or no guide. */
    public synchronized FellowshipOperationResult setGuide(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID targetIdentityId, boolean guide) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.isLeader(context.gameplayId())) {
            return FellowshipOperationResult.failure(FellowshipErrorId.NOT_LEADER, fellowship);
        }
        FellowshipMember target = fellowship.getMember(targetIdentityId);
        if (target == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.TARGET_NOT_MEMBER, fellowship);
        }
        if (fellowship.isLeader(targetIdentityId)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVALID_TARGET, fellowship);
        }
        if (!fellowship.setGuide(targetIdentityId, guide)) {
            return FellowshipOperationResult.success(false, fellowship, target);
        }
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, target);
    }

    /** A member picks the colour they wear in a fellowship. */
    public synchronized FellowshipOperationResult setMemberColor(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            FellowshipColor color) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        if (color == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVALID_COLOR, context.fellowship);
        }
        UUID identityId = context.gameplayId();
        Fellowship fellowship = context.fellowship;
        FellowshipMember member = fellowship.getMember(identityId);
        if (member.getColor() == color) {
            return FellowshipOperationResult.success(false, fellowship, member);
        }
        if (!fellowship.isColorAvailable(color, identityId)) {
            return FellowshipOperationResult.failure(FellowshipErrorId.COLOR_IN_USE, fellowship);
        }
        fellowship.changeMemberColor(identityId, color);
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship,
                fellowship.getMember(identityId));
    }

    /**
     * The leader gives the fellowship a new name, which must pass
     * {@link #checkName} and be the name of no other fellowship a member's
     * character is in.
     */
    public synchronized FellowshipOperationResult renameFellowship(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            String requestedName) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.isLeader(context.gameplayId())) {
            return FellowshipOperationResult.failure(FellowshipErrorId.NOT_LEADER, fellowship);
        }
        String name = requestedName == null ? "" : requestedName.trim();
        FellowshipErrorId nameError = checkName(name, ChatProfanityCatalog.effective());
        if (nameError == FellowshipErrorId.NONE
                && isNameTakenByMember(context.fellowshipData, fellowship, name)) {
            nameError = FellowshipErrorId.NAME_IN_USE;
        }
        if (nameError != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(nameError, fellowship);
        }
        FellowshipMember leader = fellowship.getLeader();
        if (!fellowship.rename(name)) {
            return FellowshipOperationResult.success(false, fellowship, leader);
        }
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, leader);
    }

    /**
     * The leader or a guide marks a place on the map for every member,
     * named: a meeting point, a target. A fellowship holds
     * {@link FellowshipMark#MAX_PER_FELLOWSHIP} at most, and the place must
     * lie in the world the player stands in. The name is held to the rules
     * a fellowship's own name keeps.
     */
    public synchronized FellowshipOperationResult placeMark(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            String requestedName, int dimensionId, double x, double z) {
        MarkContext context = resolveMarkContext(player, fellowshipId,
                expectedFellowshipRevision, dimensionId, x, z);
        if (context.errorId != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(context.errorId,
                    context.fellowship);
        }
        String name = requestedName == null ? "" : requestedName.trim();
        FellowshipErrorId nameError = checkMarkName(name,
                ChatProfanityCatalog.effective());
        if (nameError != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(nameError, context.fellowship);
        }
        if (context.marks.getMarks(fellowshipId).size()
                >= FellowshipMark.MAX_PER_FELLOWSHIP) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.TOO_MANY_MARKS, context.fellowship);
        }
        UUID markId = UUID.randomUUID();
        while (context.marks.getMark(markId) != null) {
            markId = UUID.randomUUID();
        }
        FellowshipMark placed = new FellowshipMark(markId, fellowshipId, name,
                context.placer, dimensionId, quantizeTrackingCoordinate(x),
                quantizeTrackingCoordinate(z), System.currentTimeMillis());
        context.marks.saveMark(placed);
        return FellowshipOperationResult.markChanged(context.fellowship,
                context.fellowship.getMember(context.placer), placed);
    }

    /** The leader or a guide moves one of the fellowship's marks, its name kept. */
    public synchronized FellowshipOperationResult moveMark(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID markId, int dimensionId, double x, double z) {
        MarkContext context = resolveMarkContext(player, fellowshipId,
                expectedFellowshipRevision, dimensionId, x, z);
        if (context.errorId != FellowshipErrorId.NONE) {
            return FellowshipOperationResult.failure(context.errorId,
                    context.fellowship);
        }
        FellowshipMark mark = context.marks.getMark(markId);
        if (mark == null || !mark.getFellowshipId().equals(fellowshipId)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARK_NOT_FOUND, context.fellowship);
        }
        FellowshipMark moved = mark.movedTo(dimensionId,
                quantizeTrackingCoordinate(x), quantizeTrackingCoordinate(z),
                context.placer, System.currentTimeMillis());
        context.marks.saveMark(moved);
        return FellowshipOperationResult.markChanged(context.fellowship,
                context.fellowship.getMember(context.placer), moved);
    }

    /** The leader or a guide takes one of the fellowship's marks away. */
    public synchronized FellowshipOperationResult removeMark(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID markId) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.canManage(context.gameplayId())) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.NOT_LEADER_OR_GUIDE, fellowship);
        }
        FellowshipMarkWorldData marks = getWritableMarkData(player.worldObj);
        if (marks == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARKER_STORAGE_READ_ONLY, fellowship);
        }
        FellowshipMark mark = marks.getMark(markId);
        if (mark == null || !mark.getFellowshipId().equals(fellowshipId)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARK_NOT_FOUND, fellowship);
        }
        marks.removeMark(markId);
        return FellowshipOperationResult.markChanged(fellowship,
                fellowship.getMember(context.gameplayId()), mark);
    }

    /**
     * What placing or moving a mark needs: the fellowship, with the player
     * its leader or a guide; a store that can be written; and a place in
     * the world the player stands in.
     */
    private MarkContext resolveMarkContext(EntityPlayerMP player, UUID fellowshipId,
                                           long expectedFellowshipRevision,
                                           int dimensionId, double x, double z) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return new MarkContext(context.errorId, context.fellowship, null, null);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.canManage(context.gameplayId())) {
            return new MarkContext(FellowshipErrorId.NOT_LEADER_OR_GUIDE, fellowship,
                    null, null);
        }
        if (dimensionId != player.dimension
                || !DimensionManager.isDimensionRegistered(dimensionId)
                || !FellowshipMark.isValidPosition(x, z)) {
            return new MarkContext(FellowshipErrorId.INVALID_MARKER_POSITION,
                    fellowship, null, null);
        }
        FellowshipMarkWorldData marks = getWritableMarkData(player.worldObj);
        if (marks == null) {
            return new MarkContext(FellowshipErrorId.MARKER_STORAGE_READ_ONLY,
                    fellowship, null, null);
        }
        return new MarkContext(FellowshipErrorId.NONE, fellowship, marks,
                context.gameplayId());
    }

    private static final class MarkContext {
        final FellowshipErrorId errorId;
        final Fellowship fellowship;
        final FellowshipMarkWorldData marks;
        final UUID placer;

        MarkContext(FellowshipErrorId errorId, Fellowship fellowship,
                    FellowshipMarkWorldData marks, UUID placer) {
            this.errorId = errorId;
            this.fellowship = fellowship;
            this.marks = marks;
            this.placer = placer;
        }
    }

    /** Why a mark's name is refused; {@link FellowshipErrorId#NONE} for one that stands. */
    static FellowshipErrorId checkMarkName(String name, ChatProfanityWords words) {
        if (name == null || name.length() == 0) {
            return FellowshipErrorId.NAME_MISSING;
        }
        if (name.length() > FellowshipMark.MAX_NAME_LENGTH) {
            return FellowshipErrorId.NAME_TOO_LONG;
        }
        if (!FellowshipMark.isValidName(name)
                || ChatProfanityFilter.hasListedWord(name, words)) {
            return FellowshipErrorId.NAME_NOT_ALLOWED;
        }
        return FellowshipErrorId.NONE;
    }

    /** A fellowship's marks; empty while the store cannot be read. */
    public synchronized List<FellowshipMark> marksOf(World world, UUID fellowshipId) {
        FellowshipMarkWorldData data = getMarkData(world);
        return data == null || data.isReadOnlyForNewerVersion()
                ? Collections.<FellowshipMark>emptyList()
                : data.getMarks(fellowshipId);
    }

    /** A fellowship that ends takes its marks with it. */
    private void forgetMarks(World world, UUID fellowshipId) {
        FellowshipMarkWorldData data = getWritableMarkData(world);
        if (data != null) {
            data.removeMarksOf(fellowshipId);
        }
    }

    /**
     * The leader or a guide gives the fellowship the item they hold as its
     * icon, as LOTR does; an empty hand takes the icon away. Only the item's
     * name and damage value are kept.
     */
    public synchronized FellowshipOperationResult setIcon(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (!fellowship.canManage(context.gameplayId())) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.NOT_LEADER_OR_GUIDE, fellowship);
        }
        FellowshipIcon icon = iconOf(player.getHeldItem());
        FellowshipMember member = fellowship.getMember(context.gameplayId());
        if (!fellowship.setIcon(icon)) {
            return FellowshipOperationResult.success(false, fellowship, member);
        }
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, member);
    }

    /** The icon an item in hand makes; null for an empty hand or an item with no registry name. */
    static FellowshipIcon iconOf(ItemStack held) {
        if (held == null || held.getItem() == null) {
            return null;
        }
        Object name = Item.itemRegistry.getNameForObject(held.getItem());
        int damage = Math.max(0, held.getItemDamage());
        return name != null && FellowshipIcon.isWellFormed(String.valueOf(name), damage)
                ? new FellowshipIcon(String.valueOf(name), damage) : null;
    }

    /**
     * Turns one of the fellowship's switches on or off: the map's for the
     * leader alone, the others for the leader and the guides, as in LOTR.
     */
    public synchronized FellowshipOperationResult setSwitch(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            FellowshipSwitch fellowshipSwitch, boolean on) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship fellowship = context.fellowship;
        if (fellowshipSwitch == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MALFORMED_REQUEST, fellowship);
        }
        UUID identityId = context.gameplayId();
        if (fellowshipSwitch.isLeaderOnly() ? !fellowship.isLeader(identityId)
                : !fellowship.canManage(identityId)) {
            return FellowshipOperationResult.failure(fellowshipSwitch.isLeaderOnly()
                    ? FellowshipErrorId.NOT_LEADER
                    : FellowshipErrorId.NOT_LEADER_OR_GUIDE, fellowship);
        }
        FellowshipMember member = fellowship.getMember(identityId);
        if (!fellowship.setSwitch(fellowshipSwitch, on)) {
            return FellowshipOperationResult.success(false, fellowship, member);
        }
        context.fellowshipData.saveFellowship(fellowship);
        return FellowshipOperationResult.success(true, fellowship, member);
    }

    /**
     * The character played travels with one of its fellowships: the one
     * the HUD, the compass, the go-here marker and shared quest credit
     * follow.
     */
    public synchronized FellowshipOperationResult setTravelling(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipOperationResult.failure(context.errorId, context.fellowship);
        }
        Fellowship before = context.fellowshipData.getTravellingFellowship(
                context.gameplayId());
        FellowshipMember member = context.fellowship.getMember(context.gameplayId());
        if (before != null && before.getFellowshipId().equals(fellowshipId)) {
            context.fellowshipData.setTravelling(context.gameplayId(), fellowshipId);
            return FellowshipOperationResult.success(false, context.fellowship, member);
        }
        context.fellowshipData.setTravelling(context.gameplayId(), fellowshipId);
        return FellowshipOperationResult.success(true, context.fellowship, member);
    }

    /**
     * Whether a trimmed name may be given to a fellowship: not empty, at
     * most {@link Fellowship#MAX_NAME_LENGTH} characters, no formatting codes
     * or control characters, and no word of the profanity list.
     */
    static FellowshipErrorId checkName(String name, ChatProfanityWords words) {
        if (name == null || name.length() == 0) {
            return FellowshipErrorId.NAME_MISSING;
        }
        if (name.codePointCount(0, name.length()) > Fellowship.MAX_NAME_LENGTH) {
            return FellowshipErrorId.NAME_TOO_LONG;
        }
        if (!Fellowship.isWellFormedName(name)
                || ChatProfanityFilter.hasListedWord(name, words)) {
            return FellowshipErrorId.NAME_NOT_ALLOWED;
        }
        return FellowshipErrorId.NONE;
    }

    /** Whether one of the fellowships, but the one passed by, has the name, whatever its case. */
    static boolean hasFellowshipNamed(List<Fellowship> fellowships, String name,
                                      UUID except) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Fellowship fellowship : fellowships) {
            if (!fellowship.getFellowshipId().equals(except)
                    && fellowship.getName().toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a member's character is in another fellowship of the name,
     * compared as {@link #hasFellowshipNamed} compares. A character is never
     * in two fellowships of one name, so its pages and conversations never
     * show two alike.
     */
    static boolean isNameTakenByMember(FellowshipWorldData data, Fellowship fellowship,
                                       String name) {
        for (FellowshipMember member : fellowship.getMembers()) {
            if (hasFellowshipNamed(data.getFellowshipsForIdentity(member.getIdentityId()),
                    name, fellowship.getFellowshipId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why an identity may not make a fellowship now: the server lets no one,
     * or it is in as many as it can be, or leads as many as LOTR lets it.
     * NONE when it may.
     */
    static FellowshipErrorId createRefusal(EntityPlayerMP player, UUID identityId,
                                           List<Fellowship> joined) {
        if (!LotrFellowshipRules.creationEnabled()) {
            return FellowshipErrorId.CREATION_DISABLED;
        }
        if (joined.size() >= Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
            return FellowshipErrorId.TOO_MANY_FELLOWSHIPS;
        }
        if (countLed(joined, identityId) >= LotrFellowshipRules.leadLimit(player)) {
            return FellowshipErrorId.LEAD_LIMIT_REACHED;
        }
        return FellowshipErrorId.NONE;
    }

    private static int countLed(List<Fellowship> fellowships, UUID identityId) {
        int led = 0;
        for (Fellowship fellowship : fellowships) {
            if (fellowship.isLeader(identityId)) {
                led++;
            }
        }
        return led;
    }

    /**
     * How many fellowships a member's character may lead. LOTR counts the
     * achievements of the character its account plays, so a character not
     * played now may lead the one fellowship every character may.
     */
    private int leadLimitOf(FellowshipMember member) {
        EntityPlayerMP player = LostTalesServerPlayers.findOnline(member.getOwnerId());
        if (player != null) {
            ActiveIdentityContext active = resolveActiveIdentity(player);
            if (active.isValid() && member.getIdentityId().equals(active.gameplayId())) {
                return LotrFellowshipRules.leadLimit(player);
            }
        }
        return LotrFellowshipRules.LEAST_LEAD_LIMIT;
    }

    /**
     * Who leads once the leader's character is deleted: the first joined of
     * the others who lead fewer fellowships than they may, else the first
     * joined of them. The limit only orders the choice, because a
     * fellowship must not end when nobody in it may lead one more. Null
     * while nobody else is in it.
     */
    static UUID successorOf(FellowshipWorldData data, Fellowship fellowship, UUID leaving,
                            LeadLimits limits) {
        UUID first = null;
        for (FellowshipMember member : fellowship.getMembers()) {
            UUID identityId = member.getIdentityId();
            if (identityId.equals(leaving)) {
                continue;
            }
            if (first == null) {
                first = identityId;
            }
            if (countLed(data.getFellowshipsForIdentity(identityId), identityId)
                    < limits.of(member)) {
                return identityId;
            }
        }
        return first;
    }

    /** How many fellowships each member's character may lead. */
    interface LeadLimits {
        int of(FellowshipMember member);
    }

    /** How many members make a fellowship full: LOTR's size setting, within our bounds. */
    public static int memberLimit() {
        return LotrFellowshipRules.memberLimit();
    }

    public synchronized FellowshipOperationResult setGoHereMarker(
            EntityPlayerMP player,
            boolean hasMarkerPosition, int markerDimensionId,
            double markerX, double markerZ) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return FellowshipOperationResult.failure(owner.errorId, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        Fellowship fellowship = fellowshipData == null
                ? null : fellowshipData.getTravellingFellowship(owner.ownerId);
        if (player.isDead || !player.isEntityAlive()
                || !hasMarkerPosition
                || markerDimensionId != player.dimension
                || !DimensionManager.isDimensionRegistered(markerDimensionId)
                || !FellowshipGoHereMarker.isValidCoordinates(
                markerX, player.posY, markerZ)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVALID_MARKER_POSITION, fellowship);
        }
        FellowshipGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARKER_STORAGE_READ_ONLY, fellowship);
        }

        UUID identityId = owner.ownerId;
        double x = quantizeTrackingCoordinate(markerX);
        double y = quantizeTrackingCoordinate(player.posY);
        double z = quantizeTrackingCoordinate(markerZ);
        FellowshipGoHereMarker previous = markerData.getMarker(identityId);
        FellowshipMember member = fellowship == null ? null : fellowship.getMember(identityId);
        if (previous != null
                && previous.getDimensionId() == markerDimensionId
                && Double.doubleToLongBits(previous.getX()) == Double.doubleToLongBits(x)
                && Double.doubleToLongBits(previous.getY()) == Double.doubleToLongBits(y)
                && Double.doubleToLongBits(previous.getZ()) == Double.doubleToLongBits(z)) {
            return FellowshipOperationResult.success(false, fellowship, member);
        }
        FellowshipGoHereMarker marker = new FellowshipGoHereMarker(
                fellowship == null ? null : fellowship.getFellowshipId(),
                identityId, markerDimensionId, x, y, z, System.currentTimeMillis());
        markerData.saveMarker(marker);
        return FellowshipOperationResult.success(true, fellowship, member);
    }

    public synchronized FellowshipOperationResult removeGoHereMarker(EntityPlayerMP player) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return FellowshipOperationResult.failure(owner.errorId, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        Fellowship fellowship = fellowshipData == null
                ? null : fellowshipData.getTravellingFellowship(owner.ownerId);
        FellowshipGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARKER_STORAGE_READ_ONLY, fellowship);
        }
        UUID identityId = owner.ownerId;
        FellowshipMember member = fellowship == null ? null : fellowship.getMember(identityId);
        if (markerData.getMarker(identityId) == null) {
            return FellowshipOperationResult.success(false, fellowship, member);
        }
        markerData.removeMarker(identityId);
        return FellowshipOperationResult.success(true, fellowship, member);
    }

    /** The leader or a guide invites a player online, as the character they play. */
    public synchronized FellowshipInvitationOperationResult invitePlayer(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID targetOwnerId) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipInvitationOperationResult.failure(
                    context.errorId, context.fellowship, null);
        }
        if (!context.fellowship.canManage(context.gameplayId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.NOT_LEADER_OR_GUIDE, context.fellowship, null);
        }
        return this.invitationCoordinator.invitePlayer(player, context.active,
                context.fellowshipData, context.fellowship, targetOwnerId);
    }

    public synchronized FellowshipInvitationOperationResult acceptInvitation(
            EntityPlayerMP player, UUID invitationId) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return FellowshipInvitationOperationResult.failure(active.errorId, null, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        if (fellowshipData == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INTERNAL_ERROR, null, null);
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY, null, null);
        }
        if (!ensureFellowshipIntegrity(player.worldObj, fellowshipData,
                active.characterData)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY, null, null);
        }
        return this.invitationCoordinator.acceptInvitation(player, active,
                fellowshipData, invitationId);
    }

    public synchronized FellowshipInvitationOperationResult declineInvitation(
            EntityPlayerMP player, UUID invitationId) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return FellowshipInvitationOperationResult.failure(active.errorId, null, null);
        }
        return this.invitationCoordinator.declineInvitation(player, active, invitationId);
    }

    /** The leader or a guide takes back an invitation the fellowship sent. */
    public synchronized FellowshipInvitationOperationResult cancelInvitation(
            EntityPlayerMP player, UUID fellowshipId, long expectedFellowshipRevision,
            UUID invitationId) {
        FellowshipContext context = resolveFellowshipContext(player, fellowshipId,
                expectedFellowshipRevision);
        if (!context.isValid()) {
            return FellowshipInvitationOperationResult.failure(
                    context.errorId, context.fellowship, null);
        }
        if (!context.fellowship.canManage(context.gameplayId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.NOT_LEADER_OR_GUIDE, context.fellowship, null);
        }
        return this.invitationCoordinator.cancelInvitation(player, context.fellowship,
                invitationId);
    }

    public synchronized FellowshipInvitationState getInvitationState(EntityPlayerMP player) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return FellowshipInvitationState.failure(active.errorId);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        if (fellowshipData == null) {
            return FellowshipInvitationState.failure(FellowshipErrorId.INTERNAL_ERROR);
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipInvitationState.failure(
                    FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY);
        }
        if (!ensureFellowshipIntegrity(player.worldObj, fellowshipData,
                active.characterData)) {
            return FellowshipInvitationState.failure(
                    FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY);
        }
        return this.invitationCoordinator.getInvitationState(player, active, fellowshipData);
    }

    /** The fellowships of the character a player plays; none while it cannot be told. */
    public synchronized List<Fellowship> getFellowshipsForActiveIdentity(EntityPlayerMP player) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        FellowshipWorldData data = active.isValid() ? getFellowshipData(player.worldObj) : null;
        return data == null || data.isReadOnlyForNewerVersion()
                ? new ArrayList<Fellowship>()
                : data.getFellowshipsForIdentity(active.gameplayId());
    }

    /** The fellowship the character a player plays travels with; null for none. */
    public synchronized Fellowship getTravellingFellowshipForActiveIdentity(
            EntityPlayerMP player) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        FellowshipWorldData data = active.isValid() ? getFellowshipData(player.worldObj) : null;
        return data == null || data.isReadOnlyForNewerVersion() ? null
                : data.getTravellingFellowship(active.gameplayId());
    }

    /**
     * Removes a character from every fellowship, with its invitations and
     * its go-here marker, before its record is deleted. A leader leaving so
     * is followed as {@link #successorOf} picks; a fellowship left empty
     * ends. Deletion is refused if a store cannot be updated safely.
     */
    public synchronized FellowshipOperationResult removeCharacterForDeletion(
            World world, RoleplayCharacter character) {
        if (world == null || world.isRemote || character == null) {
            return FellowshipOperationResult.failure(FellowshipErrorId.INVALID_PLAYER, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(world);
        CharacterWorldData characterData = getCharacterData(world);
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        FellowshipGoHereMarkerWorldData markerData = getWritableGoHereMarkerData(world);
        if (fellowshipData == null || characterData == null) {
            return FellowshipOperationResult.failure(FellowshipErrorId.INTERNAL_ERROR, null);
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY, null);
        }
        if (invitationData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, null);
        }
        if (markerData == null) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.MARKER_STORAGE_READ_ONLY, null);
        }
        if (!ensureFellowshipIntegrity(world, fellowshipData, characterData)) {
            return FellowshipOperationResult.failure(
                    FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }

        UUID characterId = character.getCharacterId();
        boolean changed = invitationData.removeInvitationsInvolvingIdentity(characterId) > 0;
        changed |= markerData.removeMarker(characterId) != null;
        Fellowship last = null;
        FellowshipMember removed = null;
        List<UUID> left = new ArrayList<UUID>();
        for (Fellowship fellowship : fellowshipData.getFellowshipsForIdentity(characterId)) {
            removed = fellowship.getMember(characterId);
            last = fellowship;
            changed = true;
            left.add(fellowship.getFellowshipId());
            if (fellowship.getMemberCount() == 1) {
                invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
                fellowshipData.removeFellowship(fellowship.getFellowshipId());
                continue;
            }
            if (fellowship.isLeader(characterId)) {
                fellowship.transferLeadership(successorOf(fellowshipData, fellowship,
                        characterId, this.leadLimits));
            }
            fellowship.removeMember(characterId);
            fellowshipData.saveFellowship(fellowship);
        }
        for (UUID fellowshipId : left) {
            FellowshipMirrors.reconcile(world, fellowshipId);
        }
        return FellowshipOperationResult.success(changed, last, removed);
    }

    /** Validates both persistent stores and removes stale invitations. */
    public synchronized boolean ensureIntegrity(World world) {
        FellowshipWorldData fellowshipData = getFellowshipData(world);
        CharacterWorldData characterData = getCharacterData(world);
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        FellowshipGoHereMarkerWorldData markerData = getWritableGoHereMarkerData(world);
        if (fellowshipData == null || characterData == null
                || invitationData == null || markerData == null) {
            return false;
        }
        if (!ensureFellowshipIntegrity(world, fellowshipData, characterData)) {
            return false;
        }
        this.invitationCoordinator.pruneInvalidInvitations(fellowshipData,
                invitationData, characterData, System.currentTimeMillis());
        pruneInvalidGoHereMarkers(characterData, markerData);
        return true;
    }

    /**
     * Counts what {@link #repairIntegrity} would remove, changing nothing.
     * Null while a store cannot be read or is read-only.
     */
    public synchronized FellowshipIntegrityReport inspectIntegrity(World world) {
        FellowshipWorldData fellowshipData = getFellowshipData(world);
        CharacterWorldData characterData = getCharacterData(world);
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        FellowshipGoHereMarkerWorldData markerData = getWritableGoHereMarkerData(world);
        if (fellowshipData == null || characterData == null
                || invitationData == null || markerData == null
                || fellowshipData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return null;
        }
        CharacterIndex index = characterData.characterIndex();
        int members = 0;
        for (Fellowship fellowship : fellowshipData.getFellowships()) {
            for (FellowshipMember member : fellowship.getMembers()) {
                if (memberRemovalReason(member, index) != null) {
                    members++;
                }
            }
        }
        int invitations = this.invitationCoordinator.countInvalidInvitations(
                fellowshipData, invitationData, characterData, System.currentTimeMillis());
        int markers = 0;
        for (FellowshipGoHereMarker marker : markerData.getMarkers()) {
            if (markerRemovalReason(marker, index) != null) {
                markers++;
            }
        }
        return new FellowshipIntegrityReport(members, invitations, markers);
    }

    /**
     * Removes what no longer stands from the fellowship stores: members, stale
     * invitations and go-here markers, the members checked again even when
     * they were checked since the world loaded. Returns what it removed;
     * null, having changed nothing, while a store cannot be written.
     */
    public synchronized FellowshipIntegrityReport repairIntegrity(World world) {
        FellowshipWorldData fellowshipData = getFellowshipData(world);
        CharacterWorldData characterData = getCharacterData(world);
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        FellowshipGoHereMarkerWorldData markerData = getWritableGoHereMarkerData(world);
        if (fellowshipData == null || characterData == null
                || invitationData == null || markerData == null
                || fellowshipData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return null;
        }
        int members = repairMemberReferences(fellowshipData, characterData);
        fellowshipData.markCharacterReferencesValidated();
        int invitations = this.invitationCoordinator.pruneInvalidInvitations(
                fellowshipData, invitationData, characterData, System.currentTimeMillis());
        int markers = pruneInvalidGoHereMarkers(characterData, markerData);
        FellowshipMirrors.reconcileAll(world);
        return new FellowshipIntegrityReport(members, invitations, markers);
    }

    /** Periodic expiration and referential-integrity cleanup. */
    public synchronized int pruneInvalidInvitations(World world) {
        FellowshipWorldData fellowshipData = getFellowshipData(world);
        CharacterWorldData characterData = getCharacterData(world);
        FellowshipInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        FellowshipGoHereMarkerWorldData markerData = getWritableGoHereMarkerData(world);
        if (fellowshipData == null || characterData == null
                || invitationData == null || markerData == null
                || !ensureFellowshipIntegrity(world, fellowshipData, characterData)) {
            return -1;
        }
        int removedInvitations = this.invitationCoordinator.pruneInvalidInvitations(
                fellowshipData, invitationData, characterData, System.currentTimeMillis());
        int removedMarkers = pruneInvalidGoHereMarkers(characterData, markerData);
        return removedInvitations + removedMarkers
                + pruneInvalidMarks(fellowshipData, getWritableMarkData(world));
    }

    boolean ensureFellowshipIntegrity(World world, FellowshipWorldData fellowshipData,
                                      CharacterWorldData characterData) {
        if (fellowshipData.areCharacterReferencesValidated()) {
            return true;
        }
        if (fellowshipData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return false;
        }
        repairMemberReferences(fellowshipData, characterData);
        fellowshipData.markCharacterReferencesValidated();
        return true;
    }

    /**
     * Quarantines every member that no longer stands, brings the names of
     * the others up to date, and mends a fellowship's leader or ends an empty
     * fellowship. Returns how many members it removed.
     */
    private int repairMemberReferences(FellowshipWorldData fellowshipData,
                                       CharacterWorldData characterData) {
        int removed = 0;
        CharacterIndex index = characterData.characterIndex();
        List<Fellowship> fellowships = new ArrayList<Fellowship>(fellowshipData.getFellowships());
        for (Fellowship fellowship : fellowships) {
            boolean changed = false;
            for (FellowshipMember member : new ArrayList<FellowshipMember>(fellowship.getMembers())) {
                UUID identityId = member.getIdentityId();
                String removalReason = memberRemovalReason(member, index);
                if (removalReason != null) {
                    fellowship.removeMember(identityId);
                    fellowshipData.quarantine(removalReason,
                            fellowship.getFellowshipId(), identityId);
                    changed = true;
                    removed++;
                    continue;
                }
                RoleplayCharacter character = index.find(identityId);
                String name = character != null ? character.getName()
                        : RoleplayCharacterIdentityHook.resolveGameplayName(identityId);
                if (name != null && name.length() > 0
                        && fellowship.refreshMemberIdentity(identityId,
                                member.getOwnerId(), name)) {
                    changed = true;
                }
            }
            if (fellowship.getMemberCount() == 0) {
                fellowshipData.removeFellowship(fellowship.getFellowshipId());
                continue;
            }
            if (fellowship.repairLeaderIfNecessary()) {
                changed = true;
            }
            if (changed) {
                fellowshipData.saveFellowship(fellowship);
            }
        }
        return removed;
    }

    /**
     * Why a member no longer stands: its id is held by two rosters, names
     * no character, or names a character of another account. Null while it
     * stands.
     */
    private static String memberRemovalReason(FellowshipMember member, CharacterIndex index) {
        UUID identityId = member.getIdentityId();
        if (index.isAmbiguous(identityId)) {
            return "ambiguous_character_uuid";
        }
        RoleplayCharacter character = index.find(identityId);
        if (character == null) {
            return "missing_character";
        }
        if (!character.getOwnerId().equals(member.getOwnerId())) {
            return "character_owner_mismatch";
        }
        return null;
    }

    /**
     * The fellowship a request names, for the character the player plays:
     * refused while the stores cannot be trusted, while the character is no
     * member of it, and once it has changed since the revision the request
     * was made against.
     */
    private FellowshipContext resolveFellowshipContext(EntityPlayerMP player,
                                                       UUID fellowshipId,
                                                       long expectedRevision) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return FellowshipContext.failure(active.errorId, null);
        }
        FellowshipWorldData fellowshipData = getFellowshipData(player.worldObj);
        if (fellowshipData == null) {
            return FellowshipContext.failure(FellowshipErrorId.INTERNAL_ERROR, null);
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipContext.failure(FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY, null);
        }
        if (!ensureFellowshipIntegrity(player.worldObj, fellowshipData, active.characterData)) {
            return FellowshipContext.failure(FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }
        Fellowship fellowship = fellowshipData.getFellowship(fellowshipId);
        if (fellowship == null || !fellowship.containsMember(active.gameplayId())) {
            return FellowshipContext.failure(FellowshipErrorId.NOT_IN_FELLOWSHIP, null);
        }
        if (expectedRevision < 0L) {
            return FellowshipContext.failure(FellowshipErrorId.INVALID_REVISION, fellowship);
        }
        if (fellowship.getRevision() != expectedRevision) {
            return FellowshipContext.failure(FellowshipErrorId.STALE_FELLOWSHIP_REVISION,
                    fellowship);
        }
        return FellowshipContext.success(active, fellowshipData, fellowship);
    }

    /**
     * The character the player is playing, as the fellowship system needs
     * it: the shared resolver's answer plus the fellowship's own integrity
     * checks, since a member is filed by its character's id and an id held
     * by two rosters or by another owner would file it under the wrong
     * person. A fellowship is a company of characters, so a player who has
     * not made one yet acts in none.
     */
    ActiveIdentityContext resolveActiveIdentity(EntityPlayerMP player) {
        PlayableIdentityResolver.Resolution resolution = PlayableIdentityResolver.resolve(player);
        if (!resolution.isAvailable()) {
            return ActiveIdentityContext.failure(fellowshipErrorOf(resolution.getError()));
        }
        CharacterWorldData data = resolution.getData();
        RoleplayCharacter character = resolution.getCharacter();
        if (character == null) {
            return ActiveIdentityContext.failure(FellowshipErrorId.CHARACTER_NOT_FOUND);
        }
        int matches = data.characterIndex().countOf(character.getCharacterId());
        if (matches == 0) {
            return ActiveIdentityContext.failure(FellowshipErrorId.CHARACTER_NOT_FOUND);
        }
        if (matches > 1) {
            return ActiveIdentityContext.failure(FellowshipErrorId.CHARACTER_ID_AMBIGUOUS);
        }
        if (!player.getUniqueID().equals(character.getOwnerId())) {
            return ActiveIdentityContext.failure(FellowshipErrorId.CHARACTER_NOT_FOUND);
        }
        return ActiveIdentityContext.success(data, resolution.getIdentity(),
                PlayableIdentityResolver.displayName(resolution, player));
    }

    /** The fellowship's word for the shared resolver's failure. */
    private static FellowshipErrorId fellowshipErrorOf(CharacterErrorId error) {
        if (error == CharacterErrorId.INVALID_PLAYER) {
            return FellowshipErrorId.INVALID_PLAYER;
        }
        if (error == CharacterErrorId.CLIENT_SIDE_REQUEST) {
            return FellowshipErrorId.CLIENT_SIDE_REQUEST;
        }
        if (error == CharacterErrorId.STORAGE_READ_ONLY) {
            return FellowshipErrorId.CHARACTER_STORAGE_READ_ONLY;
        }
        return FellowshipErrorId.INTERNAL_ERROR;
    }

    private UUID createUniqueFellowshipId(FellowshipWorldData data) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID fellowshipId = UUID.randomUUID();
            if (!data.containsFellowship(fellowshipId)) {
                return fellowshipId;
            }
        }
        return null;
    }

    FellowshipMarkWorldData getMarkData(World world) {
        try {
            return FellowshipMarkStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access fellowship mark storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    private FellowshipMarkWorldData getWritableMarkData(World world) {
        FellowshipMarkWorldData data = getMarkData(world);
        return data == null || data.isReadOnlyForNewerVersion() ? null : data;
    }

    /**
     * Takes away, into the quarantine, every mark whose fellowship ended
     * where nothing took its marks with it — a character deleted, a member
     * list mended — or whose world is gone. Answers how many went.
     */
    private static int pruneInvalidMarks(FellowshipWorldData fellowshipData,
                                         FellowshipMarkWorldData marks) {
        if (marks == null) {
            return 0;
        }
        int removed = 0;
        for (FellowshipMark mark : new ArrayList<FellowshipMark>(marks.getAllMarks())) {
            String reason = fellowshipData.getFellowship(mark.getFellowshipId()) == null
                    ? "missing_fellowship"
                    : !DimensionManager.isDimensionRegistered(mark.getDimensionId())
                            ? "unregistered_dimension" : null;
            if (reason != null) {
                marks.quarantine(reason, mark);
                marks.removeMark(mark.getMarkId());
                removed++;
            }
        }
        return removed;
    }

    FellowshipGoHereMarkerWorldData getGoHereMarkerData(World world) {
        try {
            return FellowshipGoHereMarkerStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access fellowship marker storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    private FellowshipGoHereMarkerWorldData getWritableGoHereMarkerData(World world) {
        FellowshipGoHereMarkerWorldData data = getGoHereMarkerData(world);
        return data == null || data.isReadOnlyForNewerVersion() ? null : data;
    }

    private int pruneInvalidGoHereMarkers(CharacterWorldData characterData,
                                          FellowshipGoHereMarkerWorldData markerData) {
        int removed = 0;
        CharacterIndex characters = characterData.characterIndex();
        for (FellowshipGoHereMarker marker
                : new ArrayList<FellowshipGoHereMarker>(markerData.getMarkers())) {
            String reason = markerRemovalReason(marker, characters);
            if (reason != null) {
                markerData.quarantine(reason, marker);
                markerData.removeMarker(marker.getOwnerIdentityId());
                removed++;
            }
        }
        return removed;
    }

    /**
     * Why a go-here marker no longer stands: its character is held by two
     * rosters or by nobody, or its dimension is gone. Null while it stands.
     */
    private static String markerRemovalReason(FellowshipGoHereMarker marker,
                                              CharacterIndex characters) {
        if (characters.isAmbiguous(marker.getOwnerIdentityId())) {
            return "ambiguous_owner_character";
        }
        if (characters.find(marker.getOwnerIdentityId()) == null) {
            return "missing_owner_character";
        }
        if (!DimensionManager.isDimensionRegistered(marker.getDimensionId())) {
            return "unregistered_dimension";
        }
        return null;
    }

    static double quantizeTrackingCoordinate(double value) {
        return Math.floor(value * 4.0D + 0.5D) / 4.0D;
    }

    FellowshipWorldData getFellowshipData(World world) {
        try {
            return FellowshipStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access fellowship storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    private CharacterWorldData getCharacterData(World world) {
        try {
            return CharacterStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access character storage for fellowship operation: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    void logFailure(String action, EntityPlayerMP player, RuntimeException exception) {
        FMLLog.warning("[%s] Fellowship %s failed for player %s: %s",
                LostTalesMetaData.MOD_ID, action,
                player == null ? "unknown" : player.getUniqueID(), exception.toString());
    }

    /**
     * Resolves who a personal marker belongs to: the character being
     * played. No character, unreadable storage, an ambiguous or stolen
     * character id are refusals.
     */
    PersonalMarkerContext resolvePersonalMarkerOwner(EntityPlayerMP player) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return PersonalMarkerContext.failure(active.errorId);
        }
        return PersonalMarkerContext.owned(active.identity.getCharacterId());
    }

    /** The character a personal marker is filed under. */
    static final class PersonalMarkerContext {
        final UUID ownerId;
        final FellowshipErrorId errorId;

        private PersonalMarkerContext(UUID ownerId, FellowshipErrorId errorId) {
            this.ownerId = ownerId;
            this.errorId = errorId;
        }

        private static PersonalMarkerContext owned(UUID ownerId) {
            return new PersonalMarkerContext(ownerId, FellowshipErrorId.NONE);
        }

        private static PersonalMarkerContext failure(FellowshipErrorId errorId) {
            return new PersonalMarkerContext(null, errorId == FellowshipErrorId.NONE
                    ? FellowshipErrorId.INTERNAL_ERROR : errorId);
        }

        boolean isValid() {
            return this.errorId == FellowshipErrorId.NONE && this.ownerId != null;
        }
    }

    /**
     * The character a player is acting as in the fellowship system.
     * Fellowships key members by its id.
     */
    static final class ActiveIdentityContext {
        final CharacterWorldData characterData;
        final PlayableIdentity identity;
        /** The name the character goes by. */
        final String displayName;
        final FellowshipErrorId errorId;

        private ActiveIdentityContext(CharacterWorldData characterData,
                                      PlayableIdentity identity, String displayName,
                                      FellowshipErrorId errorId) {
            this.characterData = characterData;
            this.identity = identity;
            this.displayName = displayName;
            this.errorId = errorId;
        }

        private static ActiveIdentityContext success(CharacterWorldData data,
                                                     PlayableIdentity identity,
                                                     String displayName) {
            return new ActiveIdentityContext(data, identity, displayName,
                    FellowshipErrorId.NONE);
        }

        private static ActiveIdentityContext failure(FellowshipErrorId errorId) {
            return new ActiveIdentityContext(null, null, "", errorId);
        }

        boolean isValid() {
            return this.errorId == FellowshipErrorId.NONE
                    && this.characterData != null && this.identity != null;
        }

        /** The id the fellowship system files this identity under. */
        UUID gameplayId() {
            return this.identity.getGameplayId();
        }

        UUID ownerId() {
            return this.identity.getOwnerId();
        }
    }

    private static final class FellowshipContext {
        private final ActiveIdentityContext active;
        private final FellowshipWorldData fellowshipData;
        private final Fellowship fellowship;
        private final FellowshipErrorId errorId;

        private FellowshipContext(ActiveIdentityContext active,
                                  FellowshipWorldData fellowshipData,
                                  Fellowship fellowship, FellowshipErrorId errorId) {
            this.active = active;
            this.fellowshipData = fellowshipData;
            this.fellowship = fellowship;
            this.errorId = errorId;
        }

        private static FellowshipContext success(ActiveIdentityContext active,
                                                 FellowshipWorldData data,
                                                 Fellowship fellowship) {
            return new FellowshipContext(active, data, fellowship, FellowshipErrorId.NONE);
        }

        private static FellowshipContext failure(FellowshipErrorId errorId,
                                                 Fellowship fellowship) {
            return new FellowshipContext(null, null, fellowship, errorId);
        }

        private boolean isValid() {
            return this.errorId == FellowshipErrorId.NONE
                    && this.active != null && this.active.isValid()
                    && this.fellowshipData != null && this.fellowship != null;
        }

        private UUID gameplayId() {
            return this.active.gameplayId();
        }
    }
}
