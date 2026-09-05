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
 * server — the accounts assigned to each. The two built-ins keep their
 * bits whatever the config says (the team mark bit 0, the operator
 * bit 1, the wire layout every older client reads); config roles take
 * the bits after them in the order the config lists them. Precedence is
 * by rank, the built-ins' ranks fixed. One catalogue is current per
 * side: the server's from its config, a client's from the last chat
 * access packet.
 */
public final class ChatRoleCatalog {

    public static final int MAX_ROLES = 32;
    private static final int FIRST_CUSTOM_BIT = 2;

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
    private final int knownMask;

    private ChatRoleCatalog(List<ChatAccountRole> ordered, Map<String, Set<UUID>> members) {
        this.roles = Collections.unmodifiableList(new ArrayList<ChatAccountRole>(ordered));
        Map<String, ChatAccountRole> ids = new HashMap<String, ChatAccountRole>();
        int mask = 0;
        for (ChatAccountRole role : ordered) {
            ids.put(role.getId(), role);
            mask |= role.bit();
        }
        this.byId = Collections.unmodifiableMap(ids);
        Map<String, Set<UUID>> copied = new HashMap<String, Set<UUID>>();
        if (members != null) {
            for (Map.Entry<String, Set<UUID>> entry : members.entrySet()) {
                copied.put(entry.getKey(), Collections.unmodifiableSet(
                        new HashSet<UUID>(entry.getValue())));
            }
        }
        this.members = Collections.unmodifiableMap(copied);
        this.knownMask = mask;
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

    /** The two built-ins and nothing else. */
    public static ChatRoleCatalog builtIn() {
        return of(Collections.<ChatAccountRole>emptyList(), null, null);
    }

    /**
     * The built-ins followed by the config roles, each given the next bit
     * in the order given, then ordered by rank. A config role whose id is
     * a built-in's is dropped here — the parser refuses it first — and
     * roles past the bit budget are dropped. {@code operatorLook}, when
     * given, restyles the operator role.
     */
    public static ChatRoleCatalog of(List<ChatAccountRole> customRoles,
                                     ChatAccountRole operatorLook,
                                     Map<String, Set<UUID>> members) {
        List<ChatAccountRole> ordered = new ArrayList<ChatAccountRole>();
        ordered.add(ChatAccountRole.TEAM);
        ordered.add(operatorLook == null ? ChatAccountRole.OPERATOR
                : operatorLook.withBit(ChatAccountRole.OPERATOR.getBitIndex()));
        Set<String> ids = new HashSet<String>();
        ids.add(ChatAccountRole.TEAM_ID);
        ids.add(ChatAccountRole.OPERATOR_ID);
        int bit = FIRST_CUSTOM_BIT;
        for (ChatAccountRole role : customRoles == null
                ? Collections.<ChatAccountRole>emptyList() : customRoles) {
            if (role == null || role.getId().length() == 0 || !ids.add(role.getId())
                    || bit >= MAX_ROLES) {
                continue;
            }
            ordered.add(role.withBit(bit++));
        }
        return new ChatRoleCatalog(sortedByRank(ordered), members);
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
        return new ChatRoleCatalog(sortedByRank(accepted), null);
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

    /** Every assignment, role id to accounts. */
    public Map<String, Set<UUID>> members() {
        return this.members;
    }

    /** The config roles only, in config (bit) order, for writing back. */
    public List<ChatAccountRole> customRoles() {
        List<ChatAccountRole> custom = new ArrayList<ChatAccountRole>();
        for (ChatAccountRole role : this.roles) {
            if (role.getBitIndex() >= FIRST_CUSTOM_BIT) {
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

    /** The operator role as it stands, restyled or not. */
    public ChatAccountRole operator() {
        ChatAccountRole role = this.byId.get(ChatAccountRole.OPERATOR_ID);
        return role == null ? ChatAccountRole.OPERATOR : role;
    }

    /** A copy of the assignments, for editing. */
    public Map<String, Set<UUID>> membersCopy() {
        Map<String, Set<UUID>> copy = new LinkedHashMap<String, Set<UUID>>();
        for (Map.Entry<String, Set<UUID>> entry : this.members.entrySet()) {
            copy.put(entry.getKey(), new HashSet<UUID>(entry.getValue()));
        }
        return copy;
    }
}
