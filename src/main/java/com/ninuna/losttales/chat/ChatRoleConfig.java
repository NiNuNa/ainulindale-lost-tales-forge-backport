package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    /** What a granted id and a permission id may be made of. */
    private static final Pattern GRANT_ID = Pattern.compile("[a-z0-9_.]{1,64}");
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
        return parse(roleEntries, memberEntries, LostTalesPermissionCatalog.empty(),
                warnings);
    }

    /**
     * The catalogue the entries describe, read against the permissions
     * in force so a grant naming neither a permission nor a capability
     * can be reported as it is read.
     */
    public static ChatRoleCatalog parse(String[] roleEntries, String[] memberEntries,
                                        LostTalesPermissionCatalog permissions,
                                        Warnings warnings) {
        Warnings out = warnings == null ? SILENT : warnings;
        LostTalesPermissionCatalog defined = permissions == null
                ? LostTalesPermissionCatalog.empty() : permissions;
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
            custom.add(roleOf(id, optionsOf(entry), defined, out));
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
                                          LostTalesPermissionCatalog permissions,
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
        Set<String> grants = new LinkedHashSet<String>();
        for (String grant : all(options, "grant")) {
            String granted = grant == null ? "" : grant.trim().toLowerCase(Locale.ROOT);
            if (granted.length() == 0) {
                continue;
            }
            if (!GRANT_ID.matcher(granted).matches()) {
                out.warn("Chat role '" + id + "' grants '" + grant + "', which is not a "
                        + "usable id (letters, digits, dots and underscores, up to 64); "
                        + "that grant is skipped");
                continue;
            }
            if (!permissions.isKnown(granted)) {
                out.warn("Chat role '" + id + "' grants '" + granted + "', which names "
                        + "no permission and no capability; it is kept and allows "
                        + "nothing until a permission of that id is defined");
            }
            grants.add(granted);
        }
        warnIfGrantsFollowAFaction(id, sources, grants, out);
        return ChatAccountRole.custom(id, name.length() == 0 ? id : name, tag, description,
                color, mentionable, rank, sources, grants);
    }

    /**
     * Says so when a role is both earned by a LOTR faction rank and
     * grants a capability. A faction rank is the played character's, so
     * the role comes and goes with the character being played, while
     * what it allows is the account's everywhere else — a capability
     * held only while one character is played is rarely what the entry
     * means. The entry is kept as written; this only tells the operator.
     */
    private static void warnIfGrantsFollowAFaction(String id, List<ChatRoleSource> sources,
                                                   Set<String> grants,
                                                   Warnings out) {
        if (grants.isEmpty()) {
            return;
        }
        for (ChatRoleSource source : sources) {
            if (source.getKind() == ChatRoleSource.Kind.FACTION_RANK) {
                out.warn("Chat role '" + id + "' is earned by a faction rank, which the "
                        + "played character holds, and grants a capability besides; that "
                        + "capability comes and goes as the account switches character");
                return;
            }
        }
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

    /**
     * The permissions the entries describe:
     * <pre>
     * keeper=capability:chat.moderate;capability:chat.console.read;desc:Keeps the peace.
     * </pre>
     * {@code capability:} may repeat and names a capability the code
     * registers; one naming none is skipped with a warning and the rest
     * of the permission stands. A permission that ends up naming no
     * capability at all is kept exactly as written and reaches nothing —
     * a name with nothing behind it must never read as a name with
     * everything behind it. Ids are permanent, and a role grants them.
     */
    public static LostTalesPermissionCatalog parsePermissions(String[] entries,
                                                              Warnings warnings) {
        Warnings out = warnings == null ? SILENT : warnings;
        Map<String, Set<String>> capabilities = new LinkedHashMap<String, Set<String>>();
        Map<String, String> descriptions = new LinkedHashMap<String, String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            String id = keyOf(entry);
            if (!GRANT_ID.matcher(id).matches()) {
                out.warn("Permission entry '" + entry + "' has no usable id (letters, "
                        + "digits, dots and underscores, up to 64); skipped");
                continue;
            }
            if (capabilities.containsKey(id)) {
                out.warn("Permission '" + id + "' is listed twice; the later entry is "
                        + "skipped");
                continue;
            }
            if (capabilities.size() >= LostTalesPermissionCatalog.MAX_PERMISSIONS) {
                out.warn("More than " + LostTalesPermissionCatalog.MAX_PERMISSIONS
                        + " permissions are configured; the rest are dropped");
                break;
            }
            Map<String, List<String>> options = optionsOf(entry);
            Set<String> named = new LinkedHashSet<String>();
            for (String capability : all(options, "capability")) {
                LostTalesCapability known = LostTalesCapability.byId(capability);
                if (known == null) {
                    out.warn("Permission '" + id + "' names capability '" + capability
                            + "', which the code does not have; that one is skipped");
                    continue;
                }
                named.add(known.getId());
            }
            if (named.isEmpty()) {
                out.warn("Permission '" + id + "' names no capability the code has; it "
                        + "is kept and allows nothing");
            }
            capabilities.put(id, named);
            String description = first(options, "desc");
            if (description.length() > 0) {
                descriptions.put(id, description);
            }
        }
        return LostTalesPermissionCatalog.of(capabilities, descriptions);
    }

    /** The entry a permission is written as. */
    public static String formatPermission(String id, Set<String> capabilityIds,
                                          String description) {
        StringBuilder entry = new StringBuilder(id == null ? ""
                : id.trim().toLowerCase(Locale.ROOT));
        entry.append('=');
        boolean first = true;
        for (String capability : capabilityIds == null
                ? Collections.<String>emptySet() : capabilityIds) {
            entry.append(first ? "" : ";").append("capability:").append(capability);
            first = false;
        }
        if (description != null && description.trim().length() > 0) {
            entry.append(first ? "" : ";").append("desc:").append(description.trim());
        }
        return entry.toString();
    }

    /**
     * The channels the entries define, registered beside the built-in
     * ones:
     * <pre>
     * trade=name:Trade;rule:global;colour:C9A227;bridge:true
     * </pre>
     * The key is the channel's id and is permanent: packets, the layout
     * file and the gates all name a channel by it. {@code rule} is how
     * the server routes it — {@code global}, {@code proximity} or
     * {@code operators}; the rules that need something the config cannot
     * describe (a party, a faction, a whisper, a private console) are
     * refused. {@code ooc} makes it an out-of-character channel, which
     * is what decides whether roles are tagged on its lines.
     *
     * <p>An entry naming a built-in channel's id is refused rather than
     * replacing it: the code's own channels are not a config's to
     * redefine. Every problem is reported and that entry alone skipped.</p>
     */
    public static List<ChatChannelDescriptor> parseChannelDefinitions(
            String[] entries, Warnings out) {
        Warnings warnings = out == null ? SILENT : out;
        List<ChatChannelDescriptor> defined =
                new ArrayList<ChatChannelDescriptor>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            String id = keyOf(entry).toLowerCase(Locale.ROOT);
            if (id.length() == 0 || !isChannelId(id)) {
                warnings.warn("Channel entry '" + entry + "' names no usable "
                        + "channel id; skipped");
                continue;
            }
            if (ChatChannel.fromId(id) != null) {
                warnings.warn("Channel '" + id + "' is one this build "
                        + "already has and is not a config's to define; skipped");
                continue;
            }
            if (!seen.add(id)) {
                warnings.warn("Channel '" + id + "' is defined twice; the "
                        + "second was skipped");
                continue;
            }
            Map<String, List<String>> options = optionsOf(entry);
            ChatRecipientRule rule = definedRule(first(options, "rule"));
            if (rule == null) {
                warnings.warn("Channel '" + id + "' names no routing a config "
                        + "can describe (global, proximity or operators); skipped");
                continue;
            }
            if (defined.size() >= ChatChannel.MAX_DEFINED_CHANNELS) {
                warnings.warn("Channel '" + id + "' is past the "
                        + ChatChannel.MAX_DEFINED_CHANNELS + " a server may "
                        + "define; it and any after it were skipped");
                break;
            }
            String name = first(options, "name");
            if (name == null || name.trim().length() == 0) {
                name = id;
            }
            String shown = ChatChannelDescriptor.clipDisplayName(name);
            if (!shown.equals(name.trim())) {
                warnings.warn("Channel '" + id + "' is named longer than the "
                        + ChatChannelDescriptor.MAX_DISPLAY_NAME_LENGTH
                        + " characters a name may be shown as; it reads '"
                        + shown + "'");
            }
            boolean outOfCharacter = Boolean.parseBoolean(first(options, "ooc"));
            defined.add(new ChatChannelDescriptor(id, shown,
                    outOfCharacter ? ChatPresentationMode.OUT_OF_CHARACTER
                            : ChatPresentationMode.IN_CHARACTER,
                    rule, ChatChannelAccess.NONE,
                    definedColour(first(options, "colour"), id, warnings),
                    Boolean.parseBoolean(first(options, "bridge")),
                    ChatChannelScope.NONE));
        }
        return defined;
    }

    /**
     * The routing a config may ask for. The rules left out each need
     * something only the game can supply — a party, a faction, the two
     * parties of a whisper, one player's own console — so a channel
     * defined by data cannot name them.
     */
    private static ChatRecipientRule definedRule(String rule) {
        String named = rule == null ? "" : rule.trim().toLowerCase(Locale.ROOT);
        if ("global".equals(named) || named.length() == 0) {
            return ChatRecipientRule.GLOBAL;
        }
        if ("proximity".equals(named)) {
            return ChatRecipientRule.PROXIMITY;
        }
        if ("operators".equals(named)) {
            return ChatRecipientRule.OPERATORS;
        }
        return null;
    }

    /** A channel id is a short word the wire and the layout file can carry. */
    private static boolean isChannelId(String id) {
        if (id.length() == 0 || id.length() > 16) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            if ((character < 'a' || character > 'z')
                    && (character < '0' || character > '9')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    /** The colour the entry names, or the palette's own for one it does not. */
    private static int definedColour(String colour, String id, Warnings out) {
        String named = colour == null ? "" : colour.trim();
        if (named.length() == 0) {
            return LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        }
        try {
            return Integer.parseInt(named, 16) & 0xFFFFFF;
        } catch (NumberFormatException notAColour) {
            out.warn("Channel '" + id + "' names '" + named + "', which is no "
                    + "colour; the default is used");
            return LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        }
    }

    /**
     * The icons a config puts on channels, one per line as
     * {@code <channel>=<icon>}, where the icon is {@code emoji:<name>} or
     * {@code item:<id>[@<damage>]}; read once every channel is in force,
     * so a channel the same file defines may be given one. An entry
     * naming a channel not in force, an icon that reads as none, an emoji
     * this build does not have, or a channel named twice is reported and
     * skipped. An item is left to the client that draws it, which may
     * lack the mod it comes from and shows the channel's own emoji then.
     */
    public static Map<String, ChatChannelIconSpec> parseChannelIcons(
            String[] entries, Warnings warnings) {
        Map<String, ChatChannelIconSpec> icons =
                new LinkedHashMap<String, ChatChannelIconSpec>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (isBlankOrComment(entry)) {
                continue;
            }
            String id = keyOf(entry);
            ChatChannel channel = ChatChannel.fromId(id);
            if (channel == null) {
                warnings.warn("Channel icon entry '" + entry + "' names no "
                        + "channel in force; skipped");
                continue;
            }
            if (icons.containsKey(channel.getId())) {
                warnings.warn("Channel '" + channel.getId() + "' is given an "
                        + "icon twice; the second was skipped");
                continue;
            }
            if (icons.size() >= ChatChannelIconCatalog.MAX_ICONS) {
                warnings.warn("Channel '" + channel.getId() + "' is past the "
                        + ChatChannelIconCatalog.MAX_ICONS + " icons a server "
                        + "may send; it and any after it were skipped");
                break;
            }
            String text = valueOf(entry).trim();
            ChatChannelIconSpec icon = ChatChannelIconSpec.parse(text);
            if (icon == null) {
                warnings.warn("Channel '" + channel.getId() + "' names '" + text
                        + "', which is no icon: emoji:<name> or "
                        + "item:<id>[@<damage>]; skipped");
                continue;
            }
            if (icon.getKind() == ChatChannelIconSpec.Kind.EMOJI
                    && ChatEmoji.fromName(icon.getName()) == null) {
                warnings.warn("Channel '" + channel.getId() + "' names the "
                        + "emoji '" + icon.getName() + "', which this build "
                        + "does not have; skipped");
                continue;
            }
            icons.put(channel.getId(), icon);
        }
        return icons;
    }

    /**
     * The gate entries with the Operator channel's put back when no
     * entry names that channel at all. A staff channel with no gate is
     * open to everyone, and a line can go missing by a slip of the
     * editor as easily as on purpose, so a missing line is treated as a
     * slip: the seeded gate returns and the file is told so. An entry
     * that names the channel is a decision and stands as written —
     * {@code admin=read:any;send:any} opens the channel on purpose.
     */
    public static String[] withRequiredGates(String[] entries, Warnings warnings) {
        String[] kept = entries == null ? new String[0] : entries;
        for (String entry : kept) {
            if (!isBlankOrComment(entry)
                    && ChatChannel.fromId(keyOf(entry)) == ChatChannel.ADMIN) {
                return kept;
            }
        }
        (warnings == null ? SILENT : warnings).warn("The Operator channel has no "
                + "gate entry in channels.cfg; the seeded one has been put back so "
                + "the channel stays the operators' own. To open it on purpose, "
                + "keep the line and set its sides to any.");
        String[] reseeded = new String[kept.length + 1];
        System.arraycopy(kept, 0, reseeded, 0, kept.length);
        reseeded[kept.length] = DEFAULT_ADMIN_GATE;
        return reseeded;
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
        for (String granted : role.getGrants()) {
            entry.append(";grant:").append(granted);
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
