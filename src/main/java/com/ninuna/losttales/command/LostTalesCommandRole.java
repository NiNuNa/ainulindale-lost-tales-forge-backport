package com.ninuna.losttales.command;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigSnapshot;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.permission.LostTalesPermissions;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

/**
 * The chat roles, live: list them, assign one to an account or to a
 * character — named alike, whichever it is — and take it away, create,
 * restyle and delete roles,
 * their grants included. Every change is written to the roles file
 * through the same service the settings screen uses, so the file, the
 * screen and the running server agree, and the chat access of everyone
 * online follows. The Lost Tales Team mark is shown and refused by every
 * verb: it belongs to the code. The operator role is a role like any
 * other here; deleting it closes every gate that names it.
 */
public final class LostTalesCommandRole extends LostTalesCommandBase {

    private static final String ROLES_KEY = "definitions";
    private static final String MEMBERS_KEY = "members";

    public LostTalesCommandRole() {
        super("role");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales role <list|assign|unassign|create|edit|delete> ...";
    }


    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.ROLES_MANAGE;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "Chat roles are the logical server's to change.");
            return;
        }
        String action = args[0];
        if ("list".equalsIgnoreCase(action)) {
            list(sender);
        } else if ("assign".equalsIgnoreCase(action)) {
            assign(sender, args, true);
        } else if ("unassign".equalsIgnoreCase(action)) {
            assign(sender, args, false);
        } else if ("create".equalsIgnoreCase(action) || "edit".equalsIgnoreCase(action)) {
            define(sender, args, "create".equalsIgnoreCase(action));
        } else if ("delete".equalsIgnoreCase(action)) {
            delete(sender, args);
        } else {
            sendUsage(sender);
        }
    }

    private void list(ICommandSender sender) {
        ChatRoleCatalog catalog = ChatRoleCatalog.server();
        for (ChatAccountRole role : catalog.roles()) {
            StringBuilder line = new StringBuilder();
            line.append(EnumChatFormatting.GRAY).append(role.getId()).append(" = ")
                    .append(EnumChatFormatting.WHITE).append(role.getDisplayName())
                    .append(String.format(" #%06X", role.getColor()))
                    .append(role.isMentionable() ? " mentionable" : " worn only")
                    .append(" rank ").append(role.getRank());
            if (role.isLocked()) {
                line.append(EnumChatFormatting.DARK_GRAY).append(" (the code's; not editable)");
            } else {
                for (ChatRoleSource source : role.getSources()) {
                    line.append(' ').append(source.toConfigOption());
                }
                for (String granted : role.getGrants()) {
                    line.append(" grant:").append(granted);
                    if (!LostTalesPermissionCatalog.current().isKnown(granted)) {
                        line.append(EnumChatFormatting.DARK_GRAY)
                                .append("(allows nothing)")
                                .append(EnumChatFormatting.GRAY);
                    }
                }
                int members = catalog.membersOf(role.getId()).size();
                if (members > 0) {
                    line.append(" accounts:").append(members);
                }
                int characters = catalog.characterMembersOf(role.getId()).size();
                if (characters > 0) {
                    line.append(" characters:").append(characters);
                }
            }
            LostTalesCommandConfig.send(sender, line.toString());
        }
    }

    /**
     * {@code assign|unassign <role> <player|character>}: the name is an
     * account's (online, by id, or known to the server's profile cache)
     * or a character's, on any roster, and is looked up as both. An
     * account holds the role as every identity it plays and gains its
     * grants; a character alone wears it and nothing is granted. A name
     * both an account and a character answer to is refused until it is
     * given as {@code account:<name>} or {@code character:<name>}.
     */
    private void assign(ICommandSender sender, String[] args, boolean grant) {
        if (args.length < 3) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role "
                    + (grant ? "assign" : "unassign") + " <role> <player|character>");
            return;
        }
        ChatAccountRole role = ChatRoleCatalog.server().byId(args[1]);
        if (role == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "No chat role " + args[1] + ".");
            return;
        }
        if (role.isLocked()) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The Lost Tales Team mark is never assigned; it belongs to the code.");
            return;
        }
        String withheld = grant ? withheldGrant(sender, role) : null;
        if (withheld != null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The role " + role.getId() + " grants " + withheld
                    + ", which you do not hold; handing it on is not yours to do.");
            return;
        }
        Subject subject = resolveSubject(sender, joinFrom(args, 2));
        if (subject.problem != null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED + subject.problem);
            return;
        }
        Set<UUID> accounts = new HashSet<UUID>(
                ChatRoleCatalog.server().membersOf(role.getId()));
        Set<UUID> characters = new HashSet<UUID>(
                ChatRoleCatalog.server().characterMembersOf(role.getId()));
        boolean changed = subject.character != null
                ? (grant ? characters.add(subject.character)
                        : characters.remove(subject.character))
                : (grant ? accounts.add(subject.account)
                        : accounts.remove(subject.account));
        if (!changed) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + subject.label
                    + (grant ? " already holds " : " does not hold ") + role.getId() + ".");
            return;
        }
        List<String> entries = ChatRoleConfig.withMembers(
                LostTalesConfig.chatRoleMembers, role.getId(), accounts, characters);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.applyOwned(
                java.util.Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_ROLES, MEMBERS_KEY, true, entries)),
                ServerConfigSnapshot.AUTHORIZATION_CATEGORIES,
                java.util.Collections.<String>emptySet()));
    }

    /** Whom a name names: one account, or one character, or a problem to report. */
    private static final class Subject {
        final UUID account;
        final UUID character;
        final String label;
        final String problem;

        Subject(UUID account, UUID character, String label, String problem) {
            this.account = account;
            this.character = character;
            this.label = label;
            this.problem = problem;
        }
    }

    private static final String ACCOUNT_PREFIX = "account:";
    private static final String CHARACTER_PREFIX = "character:";

    /**
     * The account or character the name stands for, read from the
     * server's own records — the player list, the profile cache and the
     * character rosters — never from the command's words alone.
     */
    private static Subject resolveSubject(ICommandSender sender, String typed) {
        String name = typed == null ? "" : typed.trim();
        boolean accountOnly = false;
        boolean characterOnly = false;
        if (name.toLowerCase(Locale.ROOT).startsWith(ACCOUNT_PREFIX)) {
            accountOnly = true;
            name = name.substring(ACCOUNT_PREFIX.length()).trim();
        } else if (name.toLowerCase(Locale.ROOT).startsWith(CHARACTER_PREFIX)) {
            characterOnly = true;
            name = name.substring(CHARACTER_PREFIX.length()).trim();
        }
        if (name.length() == 0) {
            return new Subject(null, null, "", "Name an account or a character.");
        }
        UUID account = characterOnly ? null : resolveAccount(name);
        RoleplayCharacter character = accountOnly ? null
                : resolveCharacter(sender, name);
        if (account != null && character != null) {
            return new Subject(null, null, name, "Both an account and a character are named "
                    + name + "; say " + ACCOUNT_PREFIX + name + " or " + CHARACTER_PREFIX
                    + name + ".");
        }
        if (character != null) {
            return new Subject(null, character.getCharacterId(),
                    character.getName() + " (character)", null);
        }
        if (account != null) {
            return new Subject(account, null, name + " (account)", null);
        }
        return new Subject(null, null, name, "No account or character known as " + name + ".");
    }

    /**
     * The character of that name on any roster, or null; two rosters
     * holding the name make it nobody's, since a role cannot be given
     * to half a name. Read from the server's store; a store that cannot
     * be read names nobody.
     */
    private static RoleplayCharacter resolveCharacter(ICommandSender sender, String name) {
        if (sender == null || sender.getEntityWorld() == null) {
            return null;
        }
        RoleplayCharacter found = null;
        try {
            for (CharacterRoster roster
                    : CharacterStorage.get(sender.getEntityWorld()).getRosters()) {
                if (roster == null) {
                    continue;
                }
                for (RoleplayCharacter character : roster.getCharacters()) {
                    if (character == null || !name.equalsIgnoreCase(character.getName())) {
                        continue;
                    }
                    if (found != null) {
                        return null;
                    }
                    found = character;
                }
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return found;
    }

    /** Every character name on every roster, for completion. */
    private static List<String> characterNames(ICommandSender sender) {
        List<String> names = new ArrayList<String>();
        if (sender == null || sender.getEntityWorld() == null) {
            return names;
        }
        try {
            for (CharacterRoster roster
                    : CharacterStorage.get(sender.getEntityWorld()).getRosters()) {
                if (roster == null) {
                    continue;
                }
                for (RoleplayCharacter character : roster.getCharacters()) {
                    if (character != null && character.getName().trim().length() > 0) {
                        names.add(character.getName());
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            // Nothing to complete from a store that cannot be read.
        }
        return names;
    }

    private static String joinFrom(String[] args, int start) {
        StringBuilder joined = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(args[index]);
        }
        return joined.toString();
    }

    /**
     * {@code create <id> [option ...]} and {@code edit <id> <option ...>}
     * take the options of a config entry ({@code name:Text color:RRGGBB
     * mention:true rank:15 op:1 faction:GONDOR@gondor.knight
     * grant:chat.moderate icon:emoji:bee desc:Text}), space-separated;
     * an edit keeps
     * whatever it does not name. {@code op:}, {@code faction:} and
     * {@code grant:} with nothing after the colon clear that kind.
     */
    private void define(ICommandSender sender, String[] args, boolean create) {
        if (args.length < 2 || (!create && args.length < 3)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role "
                    + (create ? "create" : "edit") + " <id> [name:<text>] "
                    + "[color:<RRGGBB>] [mention:<true|false>] [rank:<n>] [op:<level>] "
                    + "[faction:<FACTION>@<rank>] [grant:<capability>] "
                    + "[icon:<emoji:name|item:id>] [desc:<text>]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        ChatAccountRole existing = ChatRoleCatalog.server().byId(id);
        if (ChatAccountRole.TEAM_ID.equals(id) || (existing != null && existing.isLocked())) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The Lost Tales Team mark belongs to the code and is not edited here.");
            return;
        }
        if (create && existing != null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "A role " + id + " already exists; use edit.");
            return;
        }
        if (!create && existing == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED + "No chat role " + id + ".");
            return;
        }
        String entry = existing == null ? id + "=" : ChatRoleConfig.formatRole(existing);
        StringBuilder merged = new StringBuilder(entry);
        for (int index = 2; index < args.length; index++) {
            String option = args[index];
            int colon = option.indexOf(':');
            if (colon <= 0) {
                LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                        + "Option " + option + " is not name:value.");
                return;
            }
            merged = new StringBuilder(replaceOption(merged.toString(),
                    option.substring(0, colon), option.substring(colon + 1)));
        }
        List<String> warnings = new ArrayList<String>();
        ChatRoleCatalog parsed = ChatRoleConfig.parse(new String[] {merged.toString()}, null,
                collecting(warnings));
        for (String warning : warnings) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.YELLOW + warning);
        }
        ChatAccountRole role = parsed.byId(id);
        if (role == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The entry could not be read; nothing was changed.");
            return;
        }
        String withheld = withheldGrant(sender, role);
        if (withheld != null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The role " + id + " would grant " + withheld
                    + ", which you do not hold; nothing was changed.");
            return;
        }
        List<String> entries = ChatRoleConfig.upsertRole(LostTalesConfig.chatRoles, role);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.applyOwned(
                java.util.Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_ROLES, ROLES_KEY, true, entries)),
                ServerConfigSnapshot.AUTHORIZATION_CATEGORIES,
                java.util.Collections.<String>emptySet()));
    }

    private void delete(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                    + "/losttales role delete <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        ChatAccountRole role = ChatRoleCatalog.server().byId(id);
        if (role == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED + "No chat role " + id + ".");
            return;
        }
        if (role.isLocked()) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The Lost Tales Team mark belongs to the code and cannot be deleted.");
            return;
        }
        for (ChatChannel channel : ChatChannel.values()) {
            ChatChannelGates.Gate gate = ChatChannelGates.current().gateOf(channel);
            if (gate.getReadRoles().contains(id) || gate.getSendRoles().contains(id)) {
                LostTalesCommandConfig.send(sender, EnumChatFormatting.YELLOW
                        + "The " + channel.getDisplayName() + " channel's gate names " + id
                        + "; that side of the gate is closed to everyone until the gate is "
                        + "changed in channels.cfg.");
            }
        }
        List<ServerConfigChange> changes = new ArrayList<ServerConfigChange>();
        changes.add(new ServerConfigChange(LostTalesConfig.CATEGORY_ROLES, ROLES_KEY, true,
                ChatRoleConfig.removeKey(LostTalesConfig.chatRoles, id)));
        changes.add(new ServerConfigChange(LostTalesConfig.CATEGORY_ROLES, MEMBERS_KEY, true,
                ChatRoleConfig.removeKey(LostTalesConfig.chatRoleMembers, id)));
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.applyOwned(changes,
                ServerConfigSnapshot.AUTHORIZATION_CATEGORIES,
                java.util.Collections.<String>emptySet()));
    }

    /**
     * The first capability the role grants that {@code sender} does not
     * hold, or null when it grants nothing beyond them. Managing roles
     * is not a way around the capabilities: a role may only be written
     * or handed on by someone who could already do everything it
     * allows, so nobody grants themselves — or a friend — what they
     * were not given. An operator holds every capability by level, so
     * this refuses an operator nothing.
     */
    static String withheldGrant(ICommandSender sender, ChatAccountRole role) {
        LostTalesPermissionCatalog permissions = LostTalesPermissionCatalog.current();
        for (String granted : role.getGrants()) {
            for (LostTalesCapability capability : LostTalesCapability.all()) {
                if (permissions.reaches(granted, capability)
                        && !LostTalesPermissions.has(sender, capability)) {
                    return capability.getId();
                }
            }
        }
        return null;
    }

    /** The entry with one option replaced, or appended; a repeatable one is added. */
    static String replaceOption(String entry, String name, String value) {
        String lower = name.toLowerCase(Locale.ROOT);
        int equals = entry.indexOf('=');
        String key = equals < 0 ? entry : entry.substring(0, equals);
        String rest = equals < 0 ? "" : entry.substring(equals + 1);
        List<String> parts = new ArrayList<String>();
        boolean replaced = false;
        boolean repeatable = "op".equals(lower) || "faction".equals(lower)
                || "grant".equals(lower);
        for (String part : rest.split(";")) {
            if (part.trim().length() == 0) {
                continue;
            }
            int colon = part.indexOf(':');
            String partName = colon <= 0 ? "" : part.substring(0, colon).trim()
                    .toLowerCase(Locale.ROOT);
            if (!repeatable && partName.equals(lower)) {
                if (!replaced) {
                    parts.add(lower + ":" + value);
                    replaced = true;
                }
                continue;
            }
            if (repeatable && partName.equals(lower) && value.length() == 0) {
                // op: or faction: with nothing after it clears that kind.
                continue;
            }
            parts.add(part);
        }
        if (!replaced && value.length() > 0) {
            parts.add(lower + ":" + value);
        }
        StringBuilder joined = new StringBuilder(key).append('=');
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                joined.append(';');
            }
            joined.append(parts.get(index));
        }
        return joined.toString();
    }

    /** Every permission and capability a grant may name, for the usage. */
    private static String capabilityIds() {
        StringBuilder ids = new StringBuilder();
        for (String permission : LostTalesPermissionCatalog.current().ids()) {
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(permission);
        }
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(capability.getId());
        }
        return ids.toString();
    }

    private static UUID resolveAccount(String name) {
        EntityPlayerMP online = LostTalesServerPlayers.findOnline(name);
        if (online != null) {
            return online.getUniqueID();
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException notAnId) {
            // A name: the server's profile cache may know it.
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.func_152358_ax() == null) {
            return null;
        }
        GameProfile profile = server.func_152358_ax().func_152655_a(name);
        return profile == null ? null : profile.getId();
    }

    private static ChatRoleConfig.Warnings collecting(final List<String> into) {
        return new ChatRoleConfig.Warnings() {
            @Override
            public void warn(String message) {
                into.add(message);
            }
        };
    }

    private void sendUsage(ICommandSender sender) {
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role list");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role assign <role> <player|character>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role unassign <role> <player|character>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role create <id> [name:<text>] [color:<RRGGBB>] "
                + "[mention:<true|false>] [rank:<n>] [op:<level>] [faction:<FACTION>@<rank>] "
                + "[grant:<capability>] [icon:<emoji:name|item:id>] [desc:<text>]");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "  capabilities: " + capabilityIds());
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role edit <id> <option ...>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role delete <id>");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    "list", "assign", "unassign", "create", "edit", "delete");
        }
        if (args.length == 2 && !"create".equalsIgnoreCase(args[0])
                && !"list".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (ChatAccountRole role : ChatRoleCatalog.server().roles()) {
                if (!role.isLocked()) {
                    ids.add(role.getId());
                }
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        if (args.length == 3 && ("assign".equalsIgnoreCase(args[0])
                || "unassign".equalsIgnoreCase(args[0]))) {
            List<String> names = characterNames(sender);
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                names.addAll(java.util.Arrays.asList(server.getAllUsernames()));
            }
            return getListOfStringsMatchingLastWord(args,
                    names.toArray(new String[names.size()]));
        }
        return null;
    }
}
