package com.ninuna.losttales.command;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

/**
 * The chat roles, live: list them, assign one to an account and take it
 * away, create, restyle and delete config roles. Every change is written
 * to the config through the same service the settings screen uses, so
 * the file, the screen and the running server agree, and the chat
 * access of everyone online follows. The Lost Tales Team mark is shown
 * and refused by every verb: it belongs to the code.
 */
public final class LostTalesCommandRole extends LostTalesCommandBase {

    private static final String ROLES_KEY = "roles";
    private static final String MEMBERS_KEY = "roleMembers";

    public LostTalesCommandRole() {
        super("role");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales role <list|assign|unassign|create|edit|delete> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
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
                    .append(' ').append(role.getDisplayTag())
                    .append(String.format(" #%06X", role.getColor()))
                    .append(role.isMentionable() ? " mentionable" : " worn only")
                    .append(" rank ").append(role.getRank());
            if (role.isLocked()) {
                line.append(EnumChatFormatting.DARK_GRAY).append(" (the code's; not editable)");
            } else {
                for (ChatRoleSource source : role.getSources()) {
                    line.append(' ').append(source.toConfigOption());
                }
                int members = catalog.membersOf(role.getId()).size();
                if (members > 0) {
                    line.append(" members:").append(members);
                }
            }
            LostTalesCommandConfig.send(sender, line.toString());
        }
    }

    private void assign(ICommandSender sender, String[] args, boolean grant) {
        if (args.length < 3) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role "
                    + (grant ? "assign" : "unassign") + " <role> <player>");
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
        UUID account = resolveAccount(args[2]);
        if (account == null) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "No account known as " + args[2] + ".");
            return;
        }
        Set<UUID> members = new HashSet<UUID>(ChatRoleCatalog.server().membersOf(role.getId()));
        boolean changed = grant ? members.add(account) : members.remove(account);
        if (!changed) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + args[2]
                    + (grant ? " already holds " : " does not hold ") + role.getId() + ".");
            return;
        }
        List<String> entries = ChatRoleConfig.withMembers(
                LostTalesConfig.chatRoleMembers, role.getId(), members);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.apply(
                java.util.Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_CHAT, MEMBERS_KEY, true, entries))));
    }

    /**
     * {@code create <id> [option ...]} and {@code edit <id> <option ...>}
     * take the options of a config entry ({@code name:Text tag:[Text]
     * color:RRGGBB mention:true rank:15 op:1 faction:GONDOR@gondor.knight
     * desc:Text}), space-separated; an edit keeps whatever it does not name.
     */
    private void define(ICommandSender sender, String[] args, boolean create) {
        if (args.length < 2 || (!create && args.length < 3)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY + "/losttales role "
                    + (create ? "create" : "edit") + " <id> [name:<text>] [tag:<[Text]>] "
                    + "[color:<RRGGBB>] [mention:<true|false>] [rank:<n>] [op:<level>] "
                    + "[faction:<FACTION>@<rank>] [desc:<text>]");
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
        if (role == null || (ChatAccountRole.OPERATOR_ID.equals(id) && role.isLocked())) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The entry could not be read; nothing was changed.");
            return;
        }
        List<String> entries = ChatRoleConfig.upsertRole(LostTalesConfig.chatRoles, role);
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.apply(
                java.util.Collections.singletonList(new ServerConfigChange(
                        LostTalesConfig.CATEGORY_CHAT, ROLES_KEY, true, entries))));
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
        if (role.isLocked() || ChatAccountRole.OPERATOR_ID.equals(id)) {
            LostTalesCommandConfig.send(sender, EnumChatFormatting.RED
                    + "The built-in roles cannot be deleted; an edit restyles the operator role.");
            return;
        }
        List<ServerConfigChange> changes = new ArrayList<ServerConfigChange>();
        changes.add(new ServerConfigChange(LostTalesConfig.CATEGORY_CHAT, ROLES_KEY, true,
                ChatRoleConfig.removeKey(LostTalesConfig.chatRoles, id)));
        changes.add(new ServerConfigChange(LostTalesConfig.CATEGORY_CHAT, MEMBERS_KEY, true,
                ChatRoleConfig.removeKey(LostTalesConfig.chatRoleMembers, id)));
        LostTalesCommandConfig.report(sender, LostTalesServerConfigService.apply(changes));
    }

    /** The entry with one option replaced, or appended; a repeatable one is added. */
    static String replaceOption(String entry, String name, String value) {
        String lower = name.toLowerCase(Locale.ROOT);
        int equals = entry.indexOf('=');
        String key = equals < 0 ? entry : entry.substring(0, equals);
        String rest = equals < 0 ? "" : entry.substring(equals + 1);
        List<String> parts = new ArrayList<String>();
        boolean replaced = false;
        boolean repeatable = "op".equals(lower) || "faction".equals(lower);
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
                + "/losttales role assign <role> <player>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role unassign <role> <player>");
        LostTalesCommandConfig.send(sender, EnumChatFormatting.GRAY
                + "/losttales role create <id> [name:<text>] [tag:<[Text]>] [color:<RRGGBB>] "
                + "[mention:<true|false>] [rank:<n>] [op:<level>] [faction:<FACTION>@<rank>] "
                + "[desc:<text>]");
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
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                return getListOfStringsMatchingLastWord(args, server.getAllUsernames());
            }
        }
        return null;
    }

    /** Every member id of the catalogue, for tests and listings. */
    static Map<String, Set<UUID>> membersOf(ChatRoleCatalog catalog) {
        return catalog.members();
    }

    static List<String> asList(String[] values) {
        return Arrays.asList(values);
    }
}
