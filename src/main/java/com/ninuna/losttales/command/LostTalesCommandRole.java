package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNames;
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
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The chat roles, live: list them, assign one to an account or to a
 * character — named alike, whichever it is — and take it away, create,
 * restyle and delete roles,
 * their grants included. Every change is written to the roles file
 * through the same service the settings screen uses, so the file, the
 * screen and the running server agree, and the chat access of everyone
 * online follows. The Lost Tales Team mark is shown and refused by every
 * verb: it belongs to the code. The operator role is a role like any
 * other here; deleting it closes every gate that names it. A role's name
 * is the roles file's and reads as written; the config parser's warnings
 * are the server log's and read in its words.
 */
public final class LostTalesCommandRole extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.role.";

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
            say(sender, EnumChatFormatting.RED, SAY + "side_only");
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

    /**
     * A line for each role: its id, then in white its name — a shipped
     * role's in the operator's own language — colour, whether it can be
     * mentioned and its rank, its sources and grants as the roles file
     * writes them, and how many hold it.
     */
    private void list(ICommandSender sender) {
        ChatRoleCatalog catalog = ChatRoleCatalog.server();
        for (ChatAccountRole role : catalog.roles()) {
            IChatComponent details = line(EnumChatFormatting.WHITE, SAY + "list.details",
                    role.nameComponent(), String.format("%06X", role.getColor()),
                    words(role.isMentionable() ? SAY + "list.mentionable"
                            : SAY + "list.worn_only"),
                    Integer.valueOf(role.getRank()));
            if (role.isLocked()) {
                details.appendSibling(new ChatComponentText(" "));
                details.appendSibling(line(EnumChatFormatting.DARK_GRAY, SAY + "list.locked"));
            } else {
                for (ChatRoleSource source : role.getSources()) {
                    details.appendSibling(new ChatComponentText(" " + source.toConfigOption()));
                }
                for (String granted : role.getGrants()) {
                    details.appendSibling(new ChatComponentText(" grant:" + granted));
                    if (!LostTalesPermissionCatalog.current().isKnown(granted)) {
                        details.appendSibling(line(EnumChatFormatting.DARK_GRAY,
                                SAY + "list.allows_nothing"));
                    }
                }
                int members = catalog.membersOf(role.getId()).size();
                if (members > 0) {
                    details.appendSibling(new ChatComponentText(" "));
                    details.appendSibling(words(SAY + "list.accounts",
                            Integer.valueOf(members)));
                }
                int characters = catalog.characterMembersOf(role.getId()).size();
                if (characters > 0) {
                    details.appendSibling(new ChatComponentText(" "));
                    details.appendSibling(words(SAY + "list.characters",
                            Integer.valueOf(characters)));
                }
            }
            say(sender, EnumChatFormatting.GRAY, SAY + "list.role", role.getId(), details);
        }
    }

    /**
     * {@code assign|unassign <role> <player|character>}: the name is an
     * account's or a character's ({@link LostTalesCommandSubject}). An
     * account holds the role as every identity it plays and gains its
     * grants; a character alone wears it and nothing is granted.
     */
    private void assign(ICommandSender sender, String[] args, boolean grant) {
        if (args.length < 3) {
            usage(sender, "/losttales role " + (grant ? "assign" : "unassign")
                    + " <role> <player|character>");
            return;
        }
        ChatAccountRole role = ChatRoleCatalog.server().byId(args[1]);
        if (role == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_role", args[1]);
            return;
        }
        if (role.isLocked()) {
            say(sender, EnumChatFormatting.RED, SAY + "team.assign");
            return;
        }
        String withheld = grant ? withheldGrant(sender, role) : null;
        if (withheld != null) {
            say(sender, EnumChatFormatting.RED, SAY + "withheld.assign", role.getId(), withheld);
            return;
        }
        LostTalesCommandSubject subject = LostTalesCommandSubject.resolve(sender,
                LostTalesCommandSubject.joinFrom(args, 2));
        if (subject.problem != null) {
            say(sender, EnumChatFormatting.RED, subject.problem, subject.problemArguments);
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
            say(sender, EnumChatFormatting.GRAY, grant ? SAY + "assign.already"
                    : SAY + "unassign.not_held", subject.label, role.getId());
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

    /**
     * {@code create <id> [option ...]} and {@code edit <id> <option ...>}
     * take the options of a config entry ({@code name:Text colour:RRGGBB
     * mention:true rank:15 op:1 faction:GONDOR@gondor.knight
     * grant:chat.moderate icon:emoji:bee desc:Text}), space-separated;
     * an edit keeps
     * whatever it does not name. {@code op:}, {@code faction:} and
     * {@code grant:} with nothing after the colon clear that kind.
     */
    private void define(ICommandSender sender, String[] args, boolean create) {
        if (args.length < 2 || (!create && args.length < 3)) {
            usage(sender, "/losttales role " + (create ? "create" : "edit")
                    + " <id> [name:<text>] "
                    + "[colour:<RRGGBB>] [mention:<true|false>] [rank:<n>] [op:<level>] "
                    + "[faction:<FACTION>@<rank>] [grant:<capability>] "
                    + "[icon:<emoji:name|item:id>] [desc:<text>]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        ChatAccountRole existing = ChatRoleCatalog.server().byId(id);
        if (ChatAccountRole.TEAM_ID.equals(id) || (existing != null && existing.isLocked())) {
            say(sender, EnumChatFormatting.RED, SAY + "team.edit");
            return;
        }
        if (create && existing != null) {
            say(sender, EnumChatFormatting.RED, SAY + "exists", id);
            return;
        }
        if (!create && existing == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_role", id);
            return;
        }
        String entry = existing == null ? id + "=" : ChatRoleConfig.formatRole(existing);
        StringBuilder merged = new StringBuilder(entry);
        for (int index = 2; index < args.length; index++) {
            String option = args[index];
            int colon = option.indexOf(':');
            if (colon <= 0) {
                say(sender, EnumChatFormatting.RED, SAY + "not_an_option", option);
                return;
            }
            merged = new StringBuilder(replaceOption(merged.toString(),
                    option.substring(0, colon), option.substring(colon + 1)));
        }
        List<String> warnings = new ArrayList<String>();
        ChatRoleCatalog parsed = ChatRoleConfig.parse(new String[] {merged.toString()}, null,
                collecting(warnings));
        for (String warning : warnings) {
            // The roles file's own warning, as the server log reads it.
            IChatComponent line = new ChatComponentText(warning);
            line.getChatStyle().setColor(EnumChatFormatting.YELLOW);
            sender.addChatMessage(line);
        }
        ChatAccountRole role = parsed.byId(id);
        if (role == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unreadable");
            return;
        }
        String withheld = withheldGrant(sender, role);
        if (withheld != null) {
            say(sender, EnumChatFormatting.RED, SAY + "withheld.define", id, withheld);
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
            usage(sender, "/losttales role delete <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        ChatAccountRole role = ChatRoleCatalog.server().byId(id);
        if (role == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_role", id);
            return;
        }
        if (role.isLocked()) {
            say(sender, EnumChatFormatting.RED, SAY + "team.delete");
            return;
        }
        for (ChatChannel channel : ChatChannel.values()) {
            ChatChannelGates.Gate gate = ChatChannelGates.current().gateOf(channel);
            if (gate.getReadRoles().contains(id) || gate.getSendRoles().contains(id)) {
                say(sender, EnumChatFormatting.YELLOW, SAY + "delete.gate",
                        ChatNames.channelComponent(channel), id);
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

    private static ChatRoleConfig.Warnings collecting(final List<String> into) {
        return new ChatRoleConfig.Warnings() {
            @Override
            public void warn(String message) {
                into.add(message);
            }
        };
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        usage(sender, "/losttales role list");
        usage(sender, "/losttales role assign <role> <player|character>");
        usage(sender, "/losttales role unassign <role> <player|character>");
        usage(sender, "/losttales role create <id> [name:<text>] [colour:<RRGGBB>] "
                + "[mention:<true|false>] [rank:<n>] [op:<level>] [faction:<FACTION>@<rank>] "
                + "[grant:<capability>] [icon:<emoji:name|item:id>] [desc:<text>]");
        say(sender, EnumChatFormatting.GRAY, SAY + "capabilities", capabilityIds());
        usage(sender, "/losttales role edit <id> <option ...>");
        usage(sender, "/losttales role delete <id>");
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
            return getListOfStringsMatchingLastWord(args,
                    LostTalesCommandSubject.names(sender));
        }
        return null;
    }
}
