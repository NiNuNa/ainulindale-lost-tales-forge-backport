package com.ninuna.losttales.chat.server;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.IPlayerFileData;

/**
 * Who a conversation is shown to, as a window's member list lists them:
 * every identity that may read it now, those here and those who are not.
 *
 * <p>Those here are exactly the players a line said there by the asker
 * would reach now ({@link ChatChannelPolicy#route}), each as the identity
 * the channel shows them as, while that identity shows itself. An
 * in-character channel shows the character they speak as, in its
 * faction's colour, with the LOTR title their lines carry, grouped by that
 * faction; an out-of-character one shows the account, in the colour of
 * its highest role, grouped by that role, the rest together.</p>
 *
 * <p>Every other identity that may read the channel is absent, and the
 * absent stand together after everyone here, by name. On an
 * in-character channel that is every other character of the world's
 * rosters that may read it — every character of the faction for Faction,
 * the party's members for Party — including a player's characters other
 * than the one they speak as; on an out-of-character one, every other
 * account the world has made a roster for. An account the server would
 * not let in, banned or off an enforced whitelist, is nobody's member. A
 * player whose presence is Invisible stands among the absent, as Discord
 * shows them, in their own list as in everyone else's. Proximity has no
 * absent members but the asker: its members are whoever is near. Whether
 * an absent account may read a gated channel is asked of the server's own
 * lists ({@link ChatAbsentReader}), so a role only a LOTR faction rank
 * gives counts while its holder plays.</p>
 *
 * <p>A conversation that is no room has members all the same: the
 * player's own console is the player alone, and a whisper is its two
 * people ({@link #answerForWhisper}).</p>
 *
 * <p>Who is here is read afresh for every answer. Who may be absent from a
 * room is read from the rosters at most once every
 * {@link #ABSENT_REFRESH_MILLIS} for each conversation and kept until
 * then: static state, cleared with the server's other chat stores.</p>
 */
public final class ChatMemberDirectory {
    /** The absent members' group; the client names it. */
    public static final String ABSENT_GROUP = "";
    /** How long the identities that may be absent from a conversation are kept before they are read again. */
    static final long ABSENT_REFRESH_MILLIS = 15000L;

    private static final Map<String, Absentees> ABSENT = new HashMap<String, Absentees>();

    /** A conversation's members as one answer lists them, and how many more absent ones it leaves out. */
    public static final class Answer {
        public static final Answer NONE = new Answer(
                Collections.<LostTalesChatMembersPacket.Member>emptyList(), 0);

        public final List<LostTalesChatMembersPacket.Member> members;
        public final int unlisted;

        Answer(List<LostTalesChatMembersPacket.Member> members, int unlisted) {
            this.members = Collections.unmodifiableList(members);
            this.unlisted = unlisted;
        }
    }

    /** One identity that may be absent, by the key an identity here is known by. */
    static final class Absentee {
        final String key;
        final LostTalesChatMembersPacket.Member member;

        Absentee(String key, LostTalesChatMembersPacket.Member member) {
            this.key = key;
            this.member = member;
        }
    }

    /** A conversation's absentees as last read from the rosters. */
    private static final class Absentees {
        final long readAt;
        final List<Absentee> list;

        Absentees(long readAt, List<Absentee> list) {
            this.readAt = readAt;
            this.list = list;
        }
    }

    private ChatMemberDirectory() {}

    /**
     * The members of {@code channel}'s conversation as {@code viewer}
     * reads it, grouped and ordered as the list stands them, at most
     * {@link LostTalesChatMembersPacket#MAX_MEMBERS}: the viewer alone in
     * a private console, and none for a channel the viewer may not read,
     * or for the whisper channel, whose conversations are asked for by
     * their two people ({@link #answerForWhisper}).
     */
    public static Answer answerFor(EntityPlayerMP viewer, ChatChannel channel) {
        if (viewer == null || channel == null || channel == ChatChannel.WHISPER
                || !ChatChannelPolicy.canRead(viewer, channel,
                        ChatIdentitySelection.roles(viewer))) {
            return Answer.NONE;
        }
        boolean inCharacter = ChatRolePresentation.isInCharacter(channel);
        if (channel.getRecipientRule() == ChatRecipientRule.SELF) {
            RoleplayCharacter speaking = inCharacter
                    ? ChatIdentitySelection.character(viewer) : null;
            List<LostTalesChatMembersPacket.Member> alone =
                    new ArrayList<LostTalesChatMembersPacket.Member>(1);
            alone.add(isShown(viewer, speaking)
                    ? present(viewer, speaking, channel, inCharacter)
                    : absentAs(viewer, speaking, channel).member);
            return new Answer(alone, 0);
        }
        Party party = channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP
                ? ChatIdentitySelection.party(viewer) : null;
        if (channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP
                && party == null) {
            return Answer.NONE;
        }
        String factionId = ChatChannelPolicy.factionOf(
                ChatIdentitySelection.character(viewer));
        List<LostTalesChatMembersPacket.Member> present =
                new ArrayList<LostTalesChatMembersPacket.Member>();
        Set<String> presentKeys = new HashSet<String>();
        List<EntityPlayerMP> reached = new ArrayList<EntityPlayerMP>(
                ChatChannelPolicy.route(viewer, channel, party, factionId)
                        .recipients);
        if (!reached.contains(viewer)) {
            reached.add(viewer);
        }
        for (EntityPlayerMP member : reached) {
            if (member == null || member.getUniqueID() == null) {
                continue;
            }
            RoleplayCharacter character = inCharacter
                    ? ChatIdentitySelection.character(member) : null;
            if (!isShown(member, character)) {
                continue;
            }
            present.add(present(member, character, channel, inCharacter));
            presentKeys.add(keyOf(member, character));
        }
        List<Absentee> absent = absenteesOf(viewer, channel, party, factionId,
                inCharacter);
        RoleplayCharacter viewerAs = inCharacter
                ? ChatIdentitySelection.character(viewer) : null;
        if (!presentKeys.contains(keyOf(viewer, viewerAs))) {
            absent = with(absent, absentAs(viewer, viewerAs, channel));
        }
        return assemble(present, absent, presentKeys,
                LostTalesChatMembersPacket.MAX_MEMBERS);
    }

    /**
     * The two people of a whisper as {@code viewer} reads it: the viewer as
     * the identity the conversation is held as — {@code heldCharacterId},
     * one of their own characters, or the account for null or for a
     * character that is not theirs — and, where {@code partnerAccount}
     * names one, the other party as the identity the conversation is with:
     * the character {@code partnerCharacterId} names, else the one wearing
     * {@code partnerIdentity}, else their account. Each is here while they
     * speak as that identity and show themselves, and absent otherwise. The
     * other party is looked up in the server's own records by account
     * name — the players here, then the world's rosters — and one nobody
     * knows is left out. Nothing here is cached.
     */
    public static Answer answerForWhisper(EntityPlayerMP viewer,
                                          String partnerAccount,
                                          String partnerIdentity,
                                          UUID partnerCharacterId,
                                          UUID heldCharacterId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (viewer == null || server == null
                || server.getConfigurationManager() == null) {
            return Answer.NONE;
        }
        ChatChannel channel = ChatChannel.WHISPER;
        List<LostTalesChatMembersPacket.Member> present =
                new ArrayList<LostTalesChatMembersPacket.Member>();
        List<Absentee> absent = new ArrayList<Absentee>();
        Set<String> presentKeys = new HashSet<String>();
        CharacterRoster ownRoster = rosterOf(viewer, viewer.getUniqueID());
        RoleplayCharacter held = ownRoster == null || heldCharacterId == null
                ? null : ownRoster.getCharacter(heldCharacterId);
        if (isHere(viewer, held)) {
            present.add(present(viewer, held, channel, true));
            presentKeys.add(keyOf(viewer, held));
        } else {
            absent.add(absentAs(viewer, held, channel));
        }
        String named = partnerAccount == null ? "" : partnerAccount.trim();
        if (named.length() > 0) {
            EntityPlayerMP online = server.getConfigurationManager()
                    .func_152612_a(named);
            UUID owner = online != null ? online.getUniqueID()
                    : ownerNamed(server, viewer, named);
            CharacterRoster roster = owner == null ? null : rosterOf(viewer, owner);
            RoleplayCharacter character = identityIn(roster, partnerCharacterId,
                    partnerIdentity, named);
            if (online != null && online != viewer && isHere(online, character)) {
                present.add(present(online, character, channel, true));
                presentKeys.add(keyOf(online, character));
            } else if (owner != null && !owner.equals(viewer.getUniqueID())) {
                String account = online != null ? accountOf(online)
                        : accountName(server, owner, roster);
                absent.add(character != null
                        ? new Absentee(characterKey(character.getCharacterId()),
                                characterMember(owner, account.length() > 0
                                        ? account : named, character))
                        : new Absentee(accountKey(owner), accountMember(channel,
                                owner, account.length() > 0 ? account : named, 0)));
            }
        }
        Collections.sort(absent, BY_NAME);
        return assemble(present, absent, presentKeys,
                LostTalesChatMembersPacket.MAX_MEMBERS);
    }

    /**
     * The character of {@code roster} a whisper is with: the one the
     * client named by id, else the one wearing the conversation's
     * {@code identityName}; null for a conversation with the account
     * {@code accountName}, and where the roster has neither.
     */
    static RoleplayCharacter identityIn(CharacterRoster roster, UUID characterId,
                                        String identityName, String accountName) {
        if (roster == null) {
            return null;
        }
        RoleplayCharacter byId = characterId == null ? null
                : roster.getCharacter(characterId);
        if (byId != null) {
            return byId;
        }
        String wanted = identityName == null ? "" : identityName.trim();
        if (wanted.length() == 0 || wanted.equalsIgnoreCase(accountName)) {
            return null;
        }
        for (RoleplayCharacter character : roster.getCharacters()) {
            if (character != null && character.getName() != null
                    && character.getName().trim().equalsIgnoreCase(wanted)) {
                return character;
            }
        }
        return null;
    }

    /**
     * One answer from its parts: those here in the list's order, then the
     * absentees — already in theirs — who are not here, until {@code most}
     * members are listed; the absent left over are counted instead.
     */
    static Answer assemble(List<LostTalesChatMembersPacket.Member> present,
                           List<Absentee> absent, Set<String> presentKeys,
                           int most) {
        List<LostTalesChatMembersPacket.Member> listed =
                new ArrayList<LostTalesChatMembersPacket.Member>(present);
        Collections.sort(listed, LostTalesChatMembersPacket.ORDER);
        if (listed.size() > most) {
            listed = new ArrayList<LostTalesChatMembersPacket.Member>(
                    listed.subList(0, most));
        }
        int unlisted = 0;
        for (Absentee absentee : absent) {
            if (presentKeys.contains(absentee.key)) {
                continue;
            }
            if (listed.size() < most) {
                listed.add(absentee.member);
            } else {
                unlisted++;
            }
        }
        return new Answer(listed, unlisted);
    }

    /**
     * {@code absent} with {@code one} among them in name order, unless an
     * absentee of the same identity is there already.
     */
    static List<Absentee> with(List<Absentee> absent, Absentee one) {
        for (Absentee absentee : absent) {
            if (absentee.key.equals(one.key)) {
                return absent;
            }
        }
        List<Absentee> joined = new ArrayList<Absentee>(absent.size() + 1);
        joined.addAll(absent);
        joined.add(one);
        Collections.sort(joined, BY_NAME);
        return joined;
    }

    /** Forgets every conversation's absentees; with the server's other chat stores. */
    public static void clear() {
        ABSENT.clear();
    }

    /**
     * The identities that may be absent from the conversation, by name: a
     * party's members, read afresh; nobody for Proximity; for every other
     * channel the rosters' identities that may read it, kept for a while.
     */
    private static List<Absentee> absenteesOf(EntityPlayerMP viewer,
                                              ChatChannel channel, Party party,
                                              String factionId,
                                              boolean inCharacter) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || viewer.worldObj == null) {
            return Collections.emptyList();
        }
        if (channel.getAccess() == ChatChannelAccess.PARTY_MEMBERSHIP) {
            return partyAbsentees(server, viewer, party);
        }
        if (channel.getRecipientRule() == ChatRecipientRule.PROXIMITY) {
            return Collections.emptyList();
        }
        boolean byFaction = channel.getRecipientRule() == ChatRecipientRule.FACTION;
        String key = channel.getId() + '|' + (byFaction ? factionId : "");
        long now = System.currentTimeMillis();
        Absentees kept = ABSENT.get(key);
        if (kept == null || now < kept.readAt
                || now - kept.readAt >= ABSENT_REFRESH_MILLIS) {
            kept = new Absentees(now, readAbsentees(server, viewer, channel,
                    byFaction ? factionId : null, inCharacter));
            ABSENT.put(key, kept);
        }
        return kept.list;
    }

    /**
     * Every identity that has been in the world and may read the channel:
     * each character of the world's rosters, of {@code factionId} where
     * one is named, on an in-character channel; on an out-of-character one
     * each account the world keeps a roster or a player file for, so an
     * operator who never made a character still stands among the
     * operators. An account the server knows no name for is left out.
     */
    private static List<Absentee> readAbsentees(MinecraftServer server,
                                                EntityPlayerMP viewer,
                                                ChatChannel channel,
                                                String factionId,
                                                boolean inCharacter) {
        Collection<CharacterRoster> rosters;
        try {
            rosters = CharacterStorage.get(viewer.worldObj).getRosters();
        } catch (RuntimeException unreadable) {
            return Collections.emptyList();
        }
        Map<UUID, CharacterRoster> known =
                new LinkedHashMap<UUID, CharacterRoster>();
        for (CharacterRoster roster : rosters) {
            if (roster != null && roster.getOwnerId() != null) {
                known.put(roster.getOwnerId(), roster);
            }
        }
        for (UUID account : playerFileIds(viewer.worldObj)) {
            if (!known.containsKey(account)) {
                known.put(account, null);
            }
        }
        ChatChannelGates gates = ChatChannelGates.current();
        Set<UUID> operatorVoices = ChatHistory.authorsIn(ChatChannel.ADMIN);
        List<Absentee> absent = new ArrayList<Absentee>();
        for (Map.Entry<UUID, CharacterRoster> entry : known.entrySet()) {
            UUID owner = entry.getKey();
            CharacterRoster roster = entry.getValue();
            String account = accountName(server, owner, roster);
            if (account.length() == 0) {
                continue;
            }
            ChatAbsentReader reader = new ChatAbsentReader(server,
                    new GameProfile(owner, account),
                    operatorVoices.contains(owner));
            if (!reader.mayJoin()) {
                continue;
            }
            if (!inCharacter) {
                if (ChatChannelPolicy.canRead(reader, channel,
                        reader.accountRoles(), gates)) {
                    absent.add(new Absentee(accountKey(owner),
                            accountMember(channel, owner, account,
                                    reader.accountRoles())));
                }
                continue;
            }
            if (roster == null) {
                continue;
            }
            for (RoleplayCharacter character : roster.getCharacters()) {
                UUID characterId = character == null ? null
                        : character.getCharacterId();
                if (characterId == null || (factionId != null
                        && !factionId.equals(ChatChannelPolicy.factionOf(character)))
                        || !ChatChannelPolicy.canRead(reader, channel,
                                reader.rolesAs(characterId), gates)) {
                    continue;
                }
                absent.add(new Absentee(characterKey(characterId),
                        characterMember(owner, account, character)));
            }
        }
        Collections.sort(absent, BY_NAME);
        return absent;
    }

    /**
     * Every account the world keeps a player file for, by its id: everyone
     * who has played in it, whether or not they ever made a character.
     * None where the files cannot be listed.
     */
    private static List<UUID> playerFileIds(World world) {
        List<UUID> ids = new ArrayList<UUID>();
        try {
            IPlayerFileData files = world == null ? null
                    : world.getSaveHandler().getSaveHandler();
            String[] names = files == null ? null : files.getAvailablePlayerDat();
            if (names == null) {
                return ids;
            }
            for (String name : names) {
                try {
                    ids.add(UUID.fromString(name));
                } catch (IllegalArgumentException notAPlayerFile) {
                    // A temporary file beside the players', or one a tool
                    // left there: nobody's.
                }
            }
        } catch (RuntimeException unreadable) {
            ids.clear();
        }
        return ids;
    }

    /**
     * A party's members as absentees, read from their rosters: a member
     * whose roster cannot be read keeps the name the party knows them by.
     */
    private static List<Absentee> partyAbsentees(MinecraftServer server,
                                                 EntityPlayerMP viewer,
                                                 Party party) {
        List<Absentee> absent = new ArrayList<Absentee>();
        if (party == null) {
            return absent;
        }
        for (PartyMember member : party.getMembers()) {
            UUID owner = member == null ? null : member.getOwnerId();
            UUID characterId = member == null ? null : member.getCharacterId();
            if (owner == null || characterId == null) {
                continue;
            }
            CharacterRoster roster = rosterOf(viewer, owner);
            String account = accountName(server, owner, roster);
            if (account.length() == 0 || !new ChatAbsentReader(server,
                    new GameProfile(owner, account), false).mayJoin()) {
                continue;
            }
            RoleplayCharacter character = roster == null ? null
                    : roster.getCharacter(characterId);
            if (character != null) {
                absent.add(new Absentee(characterKey(characterId),
                        characterMember(owner, account, character)));
            } else if (member.getCharacterName() != null
                    && member.getCharacterName().trim().length() > 0) {
                int color = ChatRolePresentation.unassignedColor();
                absent.add(new Absentee(characterKey(characterId),
                        new LostTalesChatMembersPacket.Member(owner, account,
                                characterId, member.getCharacterName().trim(),
                                color, "", "", color, ABSENT_GROUP, "", 0,
                                false)));
            }
        }
        Collections.sort(absent, BY_NAME);
        return absent;
    }

    /**
     * Whether a player here is here as {@code character} — the character
     * they speak as — or, for null, as their account, and shows that
     * identity to others.
     */
    private static boolean isHere(EntityPlayerMP player,
                                  RoleplayCharacter character) {
        if (character == null) {
            return isShown(player, null);
        }
        RoleplayCharacter speaking = ChatIdentitySelection.character(player);
        return speaking != null && character.getCharacterId() != null
                && character.getCharacterId().equals(speaking.getCharacterId())
                && isShown(player, character);
    }

    /** Whether the identity — {@code character}, or the account for null — shows itself to others. */
    private static boolean isShown(EntityPlayerMP player,
                                   RoleplayCharacter character) {
        return ChatPresenceService.isShown(player.getUniqueID(),
                character == null ? ChatPresenceIdentity.ACCOUNT
                        : ChatPresenceIdentity.character(character.getCharacterId()));
    }

    /** A player here as an absent identity: {@code character}, or the account for null. */
    private static Absentee absentAs(EntityPlayerMP player,
                                     RoleplayCharacter character,
                                     ChatChannel channel) {
        String account = accountOf(player);
        if (character != null) {
            return new Absentee(characterKey(character.getCharacterId()),
                    characterMember(player.getUniqueID(), account, character));
        }
        return new Absentee(accountKey(player.getUniqueID()),
                accountMember(channel, player.getUniqueID(), account,
                        ChatAccountRoleResolver.resolve(player)));
    }

    /** A member who is here, as the channel shows them. */
    private static LostTalesChatMembersPacket.Member present(
            EntityPlayerMP member, RoleplayCharacter character,
            ChatChannel channel, boolean inCharacter) {
        String account = accountOf(member);
        int roles = ChatAccountRoleResolver.resolve(member,
                character == null ? null : character.getCharacterId());
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(member, character);
        int nameColor = ChatRolePresentation.nameColor(channel,
                ChatRolePresentation.rolesShown(channel, roles),
                character == null, presentation.nameColor);
        String name = character == null ? account
                : PlayableIdentity.displayName(character, account);
        if (inCharacter) {
            String factionId = ChatChannelPolicy.factionOf(character);
            return new LostTalesChatMembersPacket.Member(member.getUniqueID(),
                    account, character == null ? null : character.getCharacterId(),
                    name, nameColor, character == null ? ""
                            : character.getSkinId(),
                    presentation.title, presentation.titleColor, factionId,
                    factionName(factionId), 0, true);
        }
        ChatAccountRole role = ChatAccountRole.primary(
                ChatRolePresentation.rolesShown(channel, roles));
        return new LostTalesChatMembersPacket.Member(member.getUniqueID(),
                account, null, name, nameColor, "", "", nameColor,
                role.isNone() ? "" : role.getId(),
                role.isNone() ? "" : role.getDisplayName(),
                role.isNone() ? 0 : rolePlace(role), true);
    }

    /** An absent character, in its faction's colour; the title is read from a player here only. */
    private static LostTalesChatMembersPacket.Member characterMember(
            UUID owner, String account, RoleplayCharacter character) {
        int color = LotrFactionColors.forFactionId(character.getStartingFactionId(),
                ChatRolePresentation.unassignedColor());
        return new LostTalesChatMembersPacket.Member(owner, account,
                character.getCharacterId(),
                PlayableIdentity.displayName(character, account), color,
                character.getSkinId(), "", color, ABSENT_GROUP, "", 0, false);
    }

    /** An absent account, in the colour of its highest role the channel shows. */
    private static LostTalesChatMembersPacket.Member accountMember(
            ChatChannel channel, UUID owner, String account, int roles) {
        int color = ChatRolePresentation.nameColor(channel,
                ChatRolePresentation.rolesShown(channel, roles), true, 0);
        return new LostTalesChatMembersPacket.Member(owner, account, null,
                account, color, "", "", color, ABSENT_GROUP, "", 0, false);
    }

    /** A faction's name as the list heads its group: its own, not its people's. */
    private static String factionName(String factionId) {
        try {
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(factionId);
            String plain = ChatFormattingCodes.stripSectionCodes(name).trim();
            return plain.length() > 0 ? plain : factionId;
        } catch (RuntimeException unavailable) {
            return factionId;
        } catch (LinkageError unavailable) {
            return factionId;
        }
    }

    /** A role's place among the roles, highest first, which the groups keep. */
    private static int rolePlace(ChatAccountRole role) {
        List<ChatAccountRole> all = ChatAccountRole.all();
        int place = all.indexOf(role);
        return place < 0 ? all.size() : place + 1;
    }

    private static String accountOf(EntityPlayerMP player) {
        return player.getGameProfile() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
    }

    /** An account's roster, or null when it has none or the store cannot be read. */
    private static CharacterRoster rosterOf(EntityPlayerMP viewer, UUID owner) {
        if (viewer.worldObj == null || owner == null) {
            return null;
        }
        try {
            return CharacterStorage.get(viewer.worldObj).getRoster(owner);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The account the server knows by {@code name} while its player is not
     * here: the roster owner whose last known name it is, or null.
     */
    private static UUID ownerNamed(MinecraftServer server, EntityPlayerMP viewer,
                                   String name) {
        if (viewer.worldObj == null) {
            return null;
        }
        try {
            for (CharacterRoster roster : CharacterStorage.get(viewer.worldObj)
                    .getRosters()) {
                UUID owner = roster == null ? null : roster.getOwnerId();
                if (owner != null
                        && name.equalsIgnoreCase(accountName(server, owner, roster))) {
                    return owner;
                }
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return null;
    }

    /**
     * An account's name while its player is not here: the one the server
     * last saw it log in with, else the name its default character was
     * given on its first visit, which was the account's; empty when
     * neither is known. Asked of the server's own memory, never of Mojang.
     */
    private static String accountName(MinecraftServer server, UUID account,
                                      CharacterRoster roster) {
        GameProfile profile = null;
        try {
            profile = server.func_152358_ax() == null ? null
                    : server.func_152358_ax().func_152652_a(account);
        } catch (RuntimeException unavailable) {
            profile = null;
        }
        if (profile != null && profile.getName() != null
                && profile.getName().trim().length() > 0) {
            return profile.getName().trim();
        }
        RoleplayCharacter first = roster == null ? null : roster.getDefaultCharacter();
        return first == null || first.getName() == null ? ""
                : first.getName().trim();
    }

    /** The key an identity is known by here: its character, or the account for null. */
    private static String keyOf(EntityPlayerMP player, RoleplayCharacter character) {
        return character == null ? accountKey(player.getUniqueID())
                : characterKey(character.getCharacterId());
    }

    private static String accountKey(UUID account) {
        return "a:" + account;
    }

    private static String characterKey(UUID characterId) {
        return "c:" + characterId;
    }

    /** The absent's order: by name, whatever its case. */
    private static final Comparator<Absentee> BY_NAME = new Comparator<Absentee>() {
        @Override
        public int compare(Absentee one, Absentee other) {
            int byName = one.member.getName().toLowerCase(Locale.ROOT).compareTo(
                    other.member.getName().toLowerCase(Locale.ROOT));
            return byName != 0 ? byName : one.key.compareTo(other.key);
        }
    };
}
