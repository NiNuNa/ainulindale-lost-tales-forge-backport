package com.ninuna.losttales.chat;

import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The config form of the roles, their members and the channel gates,
 * read and written.
 *
 * <p>{@code roles.definitions} in {@code server/roles.cfg}, one role per
 * entry:</p>
 * <pre>
 * operator=name:Operator;tag:[Operator];color:A94B54;mention:true;rank:10;op:2
 * moderator=name:Moderator;tag:[Mod];color:A94B54;mention:true;rank:15;op:1;faction:GONDOR@gondor.knight;grant:chat.moderate;desc:Keeps the peace.
 * </pre>
 * Options are optional and case-insensitive; a role without a name is
 * named by its id, one without a tag wears its name in brackets. The
 * operator entry is seeded into a fresh file ({@link #DEFAULT_OPERATOR_ENTRY})
 * and is a role like any other from then on: restyle it, regrant it,
 * or delete it. The team entry is refused: the team mark is the code's
 * alone. {@code op:<level>}, {@code faction:<FACTION>@<rank>} and
 * {@code grant:<capability>} may repeat; a grant naming no {@link
 * LostTalesCapability} is skipped with a warning.
 *
 * <p>{@code roles.members}, one role per entry, accounts by id and
 * characters by {@code character:} and their id:
 * {@code moderator=<uuid>,<uuid>,character:<uuid>}. A character-scoped
 * assignment is worn by that character alone and grants nothing.</p>
 *
 * <p>{@code channels.gates} in {@code server/channels.cfg}, one channel
 * per entry: {@code admin=read:operator,moderator;send:operator}; a
 * side left out or set to {@code any} is open, and a side naming a role
 * that does not exist is closed to everyone, with a warning.</p>
 *
 * <p>A line starting with {@code #} is a comment. Every problem is
 * reported through {@link Warnings} and the entry is skipped, never the
 * whole list.</p>
 */
public final class ChatRoleConfig {

    /** Where the parser reports what it could not use. */
    public interface Warnings {
        void warn(String message);
    }

    public static final Warnings SILENT = new Warnings() {
        @Override
        public void warn(String message) {
        }
    };

    private static final Pattern ROLE_ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final int DEFAULT_CUSTOM_RANK = 20;
    /** The prefix a member entry gives a character id rather than an account id. */
    public static final String CHARACTER_MEMBER_PREFIX = "character:";
    /**
     * The operator role a fresh file starts with: held by op level 2,
     * crimson, mentionable, rank 10, no grants. Text only — the code
     * knows no operator role — and the file's to change once written.
     */
    public static final String DEFAULT_OPERATOR_ENTRY =
            "operator=name:Operator;tag:[Operator];color:A94B54;mention:true;rank:10;op:2"
            + ";desc:Runs the server day to day.";
    /** The gate a fresh file starts with: the Operator channel for the operator role. */
    public static final String DEFAULT_ADMIN_GATE = ChatChannel.ADMIN.getId()
            + "=read:operator;send:operator";

    private ChatRoleConfig() {}

    /** The catalogue the entries describe, members included. */
    public static ChatRoleCatalog parse(String[] roleEntries, String[] memberEntries,
                                        Warnings warnings) {
        Warnings out = warnings == null ? SILENT : warnings;
        List<ChatAccountRole> custom = new ArrayList<ChatAccountRole>();
        Set<String> seen = new HashSet<String>();
        for (String entry : roleEntries == null ? new String[0] : roleEntries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            String id = keyOf(entry);
            if (!ROLE_ID.matcher(id).matches()) {
                out.warn("Chat role entry '" + entry + "' has no usable id "
                        + "(letters, digits and underscores, up to 32); skipped");
                continue;
            }
            if (ChatAccountRole.TEAM_ID.equals(id)) {
                out.warn("Chat role entry '" + entry + "' names the Lost Tales Team "
                        + "mark, which no config edits; skipped");
                continue;
            }
            if (!seen.add(id)) {
                out.warn("Chat role '" + id + "' is listed twice; the later entry is skipped");
                continue;
            }
            Map<String, List<String>> options = optionsOf(entry);
            ChatAccountRole role = roleOf(id, options, out);
            if (role == null) {
                continue;
            }
            custom.add(role);
        }
        if (custom.size() > ChatRoleCatalog.MAX_ROLES - 1) {
            out.warn("More than " + (ChatRoleCatalog.MAX_ROLES - 1)
                    + " chat roles are configured; the rest are dropped");
        }
        ChatRoleCatalog withoutMembers = ChatRoleCatalog.of(custom, null, null);
        Map<String, Set<UUID>> accounts = new LinkedHashMap<String, Set<UUID>>();
        Map<String, Set<UUID>> characters = new LinkedHashMap<String, Set<UUID>>();
        parseMembers(memberEntries, withoutMembers, out, accounts, characters);
        return ChatRoleCatalog.of(custom, accounts, characters);
    }

    private static ChatAccountRole roleOf(String id, Map<String, List<String>> options,
                                          Warnings out) {
        String name = first(options, "name");
        String tag = first(options, "tag");
        String description = first(options, "desc");
        int color = LostTalesColorsDefault.ROLE;
        String colorOption = first(options, "color");
        if (colorOption.length() > 0) {
            if (!COLOR.matcher(colorOption).matches()) {
                out.warn("Chat role '" + id + "' has colour '" + colorOption
                        + "', not six hex digits; the default is used");
            } else {
                color = Integer.parseInt(colorOption.replace("#", ""), 16);
            }
        }
        boolean mentionable = !"false".equalsIgnoreCase(first(options, "mention"));
        int rank = DEFAULT_CUSTOM_RANK;
        String rankOption = first(options, "rank");
        if (rankOption.length() > 0) {
            try {
                rank = Math.max(1, Math.min(1000, Integer.parseInt(rankOption.trim())));
            } catch (NumberFormatException malformed) {
                out.warn("Chat role '" + id + "' has rank '" + rankOption
                        + "', not a number; the default is used");
            }
        }
        List<ChatRoleSource> sources = new ArrayList<ChatRoleSource>();
        for (String level : all(options, "op")) {
            try {
                sources.add(ChatRoleSource.opLevel(Integer.parseInt(level.trim())));
            } catch (NumberFormatException malformed) {
                out.warn("Chat role '" + id + "' has op level '" + level
                        + "', not a number; that source is skipped");
            }
        }
        for (String faction : all(options, "faction")) {
            int at = faction.indexOf('@');
            if (at <= 0 || at == faction.length() - 1) {
                out.warn("Chat role '" + id + "' has faction source '" + faction
                        + "', expected <FACTION>@<rank>; that source is skipped");
                continue;
            }
            sources.add(ChatRoleSource.factionRank(faction.substring(0, at),
                    faction.substring(at + 1)));
        }
        Set<LostTalesCapability> grants = EnumSet.noneOf(LostTalesCapability.class);
        for (String grant : all(options, "grant")) {
            LostTalesCapability capability = LostTalesCapability.byId(grant);
            if (capability == null) {
                out.warn("Chat role '" + id + "' grants '" + grant
                        + "', which is not a capability; that grant is skipped");
                continue;
            }
            grants.add(capability);
        }
        return ChatAccountRole.custom(id, name.length() == 0 ? id : name, tag, description,
                color, mentionable, rank, sources, grants);
    }

    private static void parseMembers(String[] entries, ChatRoleCatalog catalog,
                                     Warnings out, Map<String, Set<UUID>> accounts,
                                     Map<String, Set<UUID>> characters) {
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            String id = keyOf(entry);
            ChatAccountRole role = catalog.byId(id);
            if (role == null) {
                out.warn("Role members entry '" + entry + "' names no configured "
                        + "role; skipped");
                continue;
            }
            if (role.isLocked()) {
                out.warn("Role members entry '" + entry + "' names the Lost Tales "
                        + "Team mark, which is never assigned; skipped");
                continue;
            }
            for (String value : valueOf(entry).split(",")) {
                String trimmed = value.trim();
                if (trimmed.length() == 0) {
                    continue;
                }
                boolean character = trimmed.toLowerCase(Locale.ROOT)
                        .startsWith(CHARACTER_MEMBER_PREFIX);
                String raw = character
                        ? trimmed.substring(CHARACTER_MEMBER_PREFIX.length()).trim() : trimmed;
                try {
                    UUID member = UUID.fromString(raw);
                    Map<String, Set<UUID>> into = character ? characters : accounts;
                    Set<UUID> assigned = into.get(id);
                    if (assigned == null) {
                        assigned = new HashSet<UUID>();
                        into.put(id, assigned);
                    }
                    assigned.add(member);
                } catch (IllegalArgumentException malformed) {
                    out.warn("Role '" + id + "' member '" + trimmed + "' is not "
                            + (character ? "a character UUID" : "an account UUID")
                            + "; skipped");
                }
            }
        }
    }

    /** The gates the entries describe, over the roles of {@code catalog}. */
    public static ChatChannelGates parseGates(String[] entries, ChatRoleCatalog catalog,
                                              Warnings warnings) {
        Warnings out = warnings == null ? SILENT : warnings;
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new LinkedHashMap<ChatChannel, ChatChannelGates.Gate>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            ChatChannel channel = ChatChannel.fromId(keyOf(entry));
            if (channel == null) {
                out.warn("Channel gate entry '" + entry + "' names no channel; skipped");
                continue;
            }
            Map<String, List<String>> options = optionsOf(entry);
            Set<String> read = roleIds(first(options, "read"), catalog, entry, out);
            Set<String> send = roleIds(first(options, "send"), catalog, entry, out);
            gates.put(channel, new ChatChannelGates.Gate(read, send,
                    read == null, send == null));
        }
        return ChatChannelGates.of(gates);
    }

    /**
     * The role ids one side of a gate names; empty for an open side, and
     * null for a side that is closed: {@code none}, or a role the
     * catalogue does not have. A misspelt role must not open a staff
     * channel to everyone, so the side is refused rather than widened.
     */
    private static Set<String> roleIds(String option, ChatRoleCatalog catalog, String entry,
                                       Warnings out) {
        Set<String> ids = new HashSet<String>();
        String trimmed = option.trim();
        if (trimmed.length() == 0 || "any".equalsIgnoreCase(trimmed)) {
            return ids;
        }
        if ("none".equalsIgnoreCase(trimmed)) {
            return null;
        }
        for (String value : trimmed.split(",")) {
            String id = value.trim().toLowerCase(Locale.ROOT);
            if (id.length() == 0) {
                continue;
            }
            if (catalog.byId(id) == null) {
                out.warn("Channel gate entry '" + entry + "' names role '" + id
                        + "', which is not configured; that side of the gate is closed "
                        + "to everyone until the entry is fixed");
                return null;
            }
            ids.add(id);
        }
        return ids;
    }

    /* ---- writing back ---- */

    /** The entry a role is written as. */
    public static String formatRole(ChatAccountRole role) {
        StringBuilder entry = new StringBuilder(role.getId());
        entry.append("=name:").append(role.getName().length() == 0
                ? role.getDisplayName() : role.getName());
        if (role.getTag().length() > 0) {
            entry.append(";tag:").append(role.getTag());
        }
        entry.append(";color:").append(String.format("%06X", role.getColor()));
        entry.append(";mention:").append(role.isMentionable());
        entry.append(";rank:").append(role.getRank());
        for (ChatRoleSource source : role.getSources()) {
            entry.append(';').append(source.toConfigOption());
        }
        for (LostTalesCapability capability : role.getGrants()) {
            entry.append(";grant:").append(capability.getId());
        }
        if (role.getDescription().length() > 0) {
            entry.append(";desc:").append(role.getDescription());
        }
        return entry.toString();
    }

    /** The role entries with {@code role}'s replaced or appended. */
    public static List<String> upsertRole(String[] entries, ChatAccountRole role) {
        List<String> result = new ArrayList<String>();
        boolean replaced = false;
        for (String entry : entries == null ? new String[0] : entries) {
            if (!replaced && !isBlankOrComment(entry) && role.getId().equals(keyOf(entry))) {
                result.add(formatRole(role));
                replaced = true;
            } else {
                result.add(entry);
            }
        }
        if (!replaced) {
            result.add(formatRole(role));
        }
        return result;
    }

    /** The entries without the key's; role entries and member entries alike. */
    public static List<String> removeKey(String[] entries, String key) {
        List<String> result = new ArrayList<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry) || !key.equalsIgnoreCase(keyOf(entry))) {
                result.add(entry);
            }
        }
        return result;
    }

    /** The member entries with the role's accounts replaced, or removed when empty. */
    public static List<String> withMembers(String[] entries, String roleId, Set<UUID> members) {
        return withMembers(entries, roleId, members, Collections.<UUID>emptySet());
    }

    /**
     * The member entries with the role's accounts and characters
     * replaced, or the entry removed when both are empty. Accounts are
     * written first, then characters behind {@link #CHARACTER_MEMBER_PREFIX},
     * each group sorted so a rewrite is stable.
     */
    public static List<String> withMembers(String[] entries, String roleId, Set<UUID> members,
                                           Set<UUID> characterMembers) {
        List<String> result = removeKey(entries, roleId);
        List<String> ids = new ArrayList<String>();
        List<String> accounts = new ArrayList<String>();
        for (UUID member : members == null ? Collections.<UUID>emptySet() : members) {
            accounts.add(member.toString());
        }
        Collections.sort(accounts);
        ids.addAll(accounts);
        List<String> characters = new ArrayList<String>();
        for (UUID member : characterMembers == null
                ? Collections.<UUID>emptySet() : characterMembers) {
            characters.add(CHARACTER_MEMBER_PREFIX + member);
        }
        Collections.sort(characters);
        ids.addAll(characters);
        if (!ids.isEmpty()) {
            StringBuilder entry = new StringBuilder(roleId).append('=');
            for (int index = 0; index < ids.size(); index++) {
                if (index > 0) {
                    entry.append(',');
                }
                entry.append(ids.get(index));
            }
            result.add(entry.toString());
        }
        return result;
    }

    /* ---- entry grammar ---- */

    static boolean isBlankOrComment(String entry) {
        return entry == null || entry.trim().length() == 0 || entry.trim().startsWith("#");
    }

    /** Everything before the first '=', lower-cased. */
    static String keyOf(String entry) {
        int equals = entry.indexOf('=');
        return (equals < 0 ? entry : entry.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
    }

    static String valueOf(String entry) {
        int equals = entry.indexOf('=');
        return equals < 0 ? "" : entry.substring(equals + 1);
    }

    /** The {@code name:value} options after the '=', split on ';'. */
    static Map<String, List<String>> optionsOf(String entry) {
        Map<String, List<String>> options = new LinkedHashMap<String, List<String>>();
        for (String part : valueOf(entry).split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            List<String> values = options.get(name);
            if (values == null) {
                values = new ArrayList<String>();
                options.put(name, values);
            }
            values.add(part.substring(colon + 1).trim());
        }
        return options;
    }

    private static String first(Map<String, List<String>> options, String name) {
        List<String> values = options.get(name);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private static List<String> all(Map<String, List<String>> options, String name) {
        List<String> values = options.get(name);
        return values == null ? new ArrayList<String>() : values;
    }

    /** The colour a role wears when the config names none. */
    private static final class LostTalesColorsDefault {
        static final int ROLE = com.ninuna.losttales.gui.style.LostTalesColors.rgb(
                com.ninuna.losttales.gui.style.LostTalesColors.HONEY);
    }
}
