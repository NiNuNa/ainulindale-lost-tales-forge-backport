package com.ninuna.losttales.chat;

import java.util.ArrayList;
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

/**
 * The roles in force, with their bits and precedence, and — on the
 * server — who is assigned each: accounts, and characters. The Lost
 * Tales Team mark keeps bit 0 whatever the config says; every other
 * role, the operator role included, comes from the config and takes the
 * next bit in the order the config lists them. Precedence is by rank.
 * One catalogue is current per side: the server's from its config, a
 * client's from the last chat access packet.
 *
 * <p>Bits are the wire form of a set of roles for one session and are
 * never stored: assignments are kept by role id, so the config may be
 * reordered without disturbing anything saved.</p>
 */
public final class ChatRoleCatalog {

    public static final int MAX_ROLES = 32;
    private static final int FIRST_CONFIG_BIT = 1;

    private static volatile ChatRoleCatalog current = builtIn();
    /**
     * The server's own catalogue, members included. Kept apart from
     * {@link #current} because an integrated server shares the JVM with
     * its client, whose access packet installs a copy without members.
     */
    private static volatile ChatRoleCatalog server = builtIn();

    private final List<ChatAccountRole> roles;
    private final Map<String, ChatAccountRole> byId;
    private final Map<String, Set<UUID>> members;
    private final Map<String, Set<UUID>> characterMembers;
    private final int knownMask;

    private ChatRoleCatalog(List<ChatAccountRole> ordered, Map<String, Set<UUID>> members,
                            Map<String, Set<UUID>> characterMembers) {
        this.roles = Collections.unmodifiableList(new ArrayList<ChatAccountRole>(ordered));
        Map<String, ChatAccountRole> ids = new HashMap<String, ChatAccountRole>();
        int mask = 0;
        for (ChatAccountRole role : ordered) {
            ids.put(role.getId(), role);
            mask |= role.bit();
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.members = copyOf(members);
        this.characterMembers = copyOf(characterMembers);
        this.knownMask = mask;
    }

    private static Map<String, Set<UUID>> copyOf(Map<String, Set<UUID>> assignments) {
        Map<String, Set<UUID>> copied = new HashMap<String, Set<UUID>>();
        if (assignments != null) {
            for (Map.Entry<String, Set<UUID>> entry : assignments.entrySet()) {
                copied.put(entry.getKey(), Collections.unmodifiableSet(
                        new HashSet<UUID>(entry.getValue())));
            }
        }
        return Collections.unmodifiableMap(copied);
    }

    /** The catalogue in force on this side. */
    public static ChatRoleCatalog current() {
        return current;
    }

    /** What a client was told; presentation reads it. */
    public static void install(ChatRoleCatalog catalog) {
        current = catalog == null ? builtIn() : catalog;
    }

    /** What the server's config says; the resolver and the gates read it. */
    public static void installServer(ChatRoleCatalog catalog) {
        server = catalog == null ? builtIn() : catalog;
        current = server;
    }

    /** The catalogue the server resolves roles from. */
    public static ChatRoleCatalog server() {
        return server;
    }

    public static void resetToBuiltIn() {
        current = builtIn();
    }

    /** The ids and bits in force, for telling one catalogue from another. */
    public String signature() {
        StringBuilder signature = new StringBuilder();
        for (ChatAccountRole role : this.roles) {
            signature.append(role.getId()).append(':').append(role.getBitIndex()).append(';');
        }
        return signature.toString();
    }

    /**
     * The team mark alone: what stands before a config is read, and
     * what a client falls back to before its access packet arrives.
     * Every other role, the operator role included, is the file's.
     */
    public static ChatRoleCatalog builtIn() {
        return of(null, null, null);
    }

    /**
     * The team mark followed by the config roles, each given the next
     * bit in the order given, then ordered by rank. A role whose id is
     * the team mark's, or repeats another's, is dropped here — the
     * parser refuses it first — and roles past the bit budget are
     * dropped.
     */
    public static ChatRoleCatalog of(List<ChatAccountRole> configRoles,
                                     Map<String, Set<UUID>> members,
                                     Map<String, Set<UUID>> characterMembers) {
        List<ChatAccountRole> ordered = new ArrayList<ChatAccountRole>();
        ordered.add(ChatAccountRole.TEAM);
        Set<String> ids = new HashSet<String>();
        ids.add(ChatAccountRole.TEAM_ID);
        int bit = FIRST_CONFIG_BIT;
        for (ChatAccountRole role : configRoles == null
                ? Collections.<ChatAccountRole>emptyList() : configRoles) {
            if (role == null || role.getId().length() == 0 || !ids.add(role.getId())
                    || bit >= MAX_ROLES) {
                continue;
            }
            ordered.add(role.withBit(bit++));
        }
        return new ChatRoleCatalog(sortedByRank(ordered), members, characterMembers);
    }

    /** A catalogue as the wire describes it, bits already given. */
    public static ChatRoleCatalog fromWire(List<ChatAccountRole> roles) {
        List<ChatAccountRole> accepted = new ArrayList<ChatAccountRole>();
        Set<String> ids = new HashSet<String>();
        int bits = 0;
        for (ChatAccountRole role : roles) {
            if (role == null || role.isNone() || role.bit() == 0
                    || (bits & role.bit()) != 0 || !ids.add(role.getId())) {
                continue;
            }
            bits |= role.bit();
            accepted.add(role);
        }
        return new ChatRoleCatalog(sortedByRank(accepted), null, null);
    }

    private static List<ChatAccountRole> sortedByRank(List<ChatAccountRole> roles) {
        List<ChatAccountRole> sorted = new ArrayList<ChatAccountRole>(roles);
        Collections.sort(sorted, new Comparator<ChatAccountRole>() {
            @Override
            public int compare(ChatAccountRole left, ChatAccountRole right) {
                if (left.getRank() != right.getRank()) {
                    return left.getRank() < right.getRank() ? -1 : 1;
                }
                return left.getBitIndex() - right.getBitIndex();
            }
        });
        return sorted;
    }

    /** Every role, in precedence order. */
    public List<ChatAccountRole> roles() {
        return this.roles;
    }

    /** The role with that id, or null. */
    public ChatAccountRole byId(String id) {
        return id == null ? null : this.byId.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Every bit a role occupies. */
    public int knownMask() {
        return this.knownMask;
    }

    /** The accounts assigned the role by the config; empty for none. */
    public Set<UUID> membersOf(String roleId) {
        Set<UUID> assigned = roleId == null ? null
                : this.members.get(roleId.trim().toLowerCase(Locale.ROOT));
        return assigned == null ? Collections.<UUID>emptySet() : assigned;
    }

    /**
     * The characters assigned the role by the config, by character id;
     * empty for none. A character-scoped role is worn by that character
     * alone, never by the account's other identities, and grants nothing.
     */
    public Set<UUID> characterMembersOf(String roleId) {
        Set<UUID> assigned = roleId == null ? null
                : this.characterMembers.get(roleId.trim().toLowerCase(Locale.ROOT));
        return assigned == null ? Collections.<UUID>emptySet() : assigned;
    }

    /** Every account assignment, role id to accounts. */
    public Map<String, Set<UUID>> members() {
        return this.members;
    }

    /** Every character assignment, role id to character ids. */
    public Map<String, Set<UUID>> characterMembers() {
        return this.characterMembers;
    }

    /** The config roles only, in config (bit) order, for writing back. */
    public List<ChatAccountRole> configRoles() {
        List<ChatAccountRole> custom = new ArrayList<ChatAccountRole>();
        for (ChatAccountRole role : this.roles) {
            if (role.getBitIndex() >= FIRST_CONFIG_BIT) {
                custom.add(role);
            }
        }
        Collections.sort(custom, new Comparator<ChatAccountRole>() {
            @Override
            public int compare(ChatAccountRole left, ChatAccountRole right) {
                return left.getBitIndex() - right.getBitIndex();
            }
        });
        return custom;
    }

    /** A copy of the account assignments, for editing. */
    public Map<String, Set<UUID>> membersCopy() {
        Map<String, Set<UUID>> copy = new LinkedHashMap<String, Set<UUID>>();
        for (Map.Entry<String, Set<UUID>> entry : this.members.entrySet()) {
            copy.put(entry.getKey(), new HashSet<UUID>(entry.getValue()));
        }
        return copy;
    }
}
