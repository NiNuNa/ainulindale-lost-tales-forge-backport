package com.ninuna.losttales.command;

import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import com.ninuna.losttales.config.server.ServerConfigSnapshot;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * The server's config from the console or an operator's chat: list the
 * categories and keys, read one, set one, or reload the file and restart
 * what its categories own. Every change goes through the same service
 * the settings screen uses, so the file, the screen and the running
 * server never disagree.
 */
public final class LostTalesCommandConfig extends LostTalesCommandBase {

    public LostTalesCommandConfig() {
        super("config");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales config <list|get|set|reload> ...";
    }


    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.SERVER_CONFIG;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return;
        }
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) {
            send(sender, EnumChatFormatting.RED
                    + "The server config can only be changed on the logical server.");
            return;
        }
        String action = args[0];
        if ("reload".equalsIgnoreCase(action)) {
            List<String> restarted = LostTalesServerConfigService.reloadAll();
            send(sender, EnumChatFormatting.GREEN + "Config reloaded"
                    + (restarted.isEmpty() ? "." : "; restarted " + join(restarted) + "."));
        } else if ("list".equalsIgnoreCase(action)) {
            list(sender, args.length > 1 ? args[1] : null);
        } else if ("get".equalsIgnoreCase(action)) {
            get(sender, args);
        } else if ("set".equalsIgnoreCase(action)) {
            set(sender, args);
        } else {
            sendUsage(sender);
        }
    }

    private void list(ICommandSender sender, String category) {
        List<ServerConfigEntry> entries = LostTalesServerConfigService.snapshot();
        if (category == null) {
            TreeSet<String> categories = new TreeSet<String>();
            for (ServerConfigEntry entry : entries) {
                categories.add(entry.getCategory());
            }
            send(sender, EnumChatFormatting.GRAY + "Categories: " + join(
                    new ArrayList<String>(categories)));
            return;
        }
        int shown = 0;
        for (ServerConfigEntry entry : entries) {
            if (entry.getCategory().equalsIgnoreCase(category)) {
                send(sender, EnumChatFormatting.GRAY + entry.getKey() + " = "
                        + EnumChatFormatting.WHITE + shownValue(entry));
                shown++;
            }
        }
        if (shown == 0) {
            send(sender, EnumChatFormatting.RED + "No server category named " + category + ".");
        }
    }

    private void get(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, EnumChatFormatting.GRAY + "/losttales config get <category> <key>");
            return;
        }
        ServerConfigEntry entry = ServerConfigSnapshot.find(
                LostTalesServerConfigService.snapshot(), args[1], args[2]);
        if (entry == null) {
            send(sender, EnumChatFormatting.RED + "No server key " + args[1] + "." + args[2] + ".");
            return;
        }
        send(sender, EnumChatFormatting.GRAY + entry.qualifiedName() + " = "
                + EnumChatFormatting.WHITE + shownValue(entry));
        if (entry.getComment().length() > 0) {
            send(sender, EnumChatFormatting.DARK_GRAY + entry.getComment());
        }
    }

    private void set(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, EnumChatFormatting.GRAY
                    + "/losttales config set <category> <key> <value> [more list items...]");
            return;
        }
        ServerConfigEntry entry = ServerConfigSnapshot.find(
                LostTalesServerConfigService.snapshot(), args[1], args[2]);
        if (entry == null) {
            send(sender, EnumChatFormatting.RED + "No server key " + args[1] + "." + args[2] + ".");
            return;
        }
        List<String> values = Arrays.asList(args).subList(3, args.length);
        ServerConfigChange change = entry.isList()
                ? new ServerConfigChange(entry.getCategory(), entry.getKey(), true, values)
                : new ServerConfigChange(entry.getCategory(), entry.getKey(),
                        joinWith(values, " "));
        report(sender, LostTalesServerConfigService.apply(
                java.util.Collections.singletonList(change)));
    }

    static void report(ICommandSender sender, ServerConfigApplyResult result) {
        if (result.getMessage().length() > 0) {
            send(sender, EnumChatFormatting.RED + result.getMessage());
        }
        if (!result.getApplied().isEmpty()) {
            send(sender, EnumChatFormatting.GREEN + "Applied " + join(result.getApplied())
                    + (result.getRestarted().isEmpty() ? "."
                            : "; restarted " + join(result.getRestarted()) + "."));
        }
        for (ServerConfigApplyResult.Refusal refusal : result.getRefused()) {
            send(sender, EnumChatFormatting.RED + "Refused " + refusal.getName() + ": "
                    + refusal.getReason());
        }
    }

    private static String shownValue(ServerConfigEntry entry) {
        if (entry.isSecret()) {
            return "(secret)";
        }
        return entry.isList() ? entry.getValues().toString() : entry.getValue();
    }

    private static String join(List<String> values) {
        return joinWith(values, ", ");
    }

    private static String joinWith(List<String> values, String separator) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) {
                joined.append(separator);
            }
            joined.append(value);
        }
        return joined.toString();
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        send(sender, EnumChatFormatting.GRAY + "/losttales config list [category]");
        send(sender, EnumChatFormatting.GRAY + "/losttales config get <category> <key>");
        send(sender, EnumChatFormatting.GRAY
                + "/losttales config set <category> <key> <value> [more list items...]");
        send(sender, EnumChatFormatting.GRAY + "/losttales config reload");
    }

    static void send(ICommandSender sender, String message) {
        if (sender != null) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "get", "set", "reload");
        }
        if (args.length == 2 && !"reload".equalsIgnoreCase(args[0])) {
            TreeSet<String> categories = new TreeSet<String>();
            for (ServerConfigEntry entry : LostTalesServerConfigService.snapshot()) {
                categories.add(entry.getCategory());
            }
            return getListOfStringsMatchingLastWord(args,
                    categories.toArray(new String[categories.size()]));
        }
        if (args.length == 3 && ("get".equalsIgnoreCase(args[0])
                || "set".equalsIgnoreCase(args[0]))) {
            List<String> keys = new ArrayList<String>();
            for (ServerConfigEntry entry : LostTalesServerConfigService.snapshot()) {
                if (entry.getCategory().equalsIgnoreCase(args[1])) {
                    keys.add(entry.getKey());
                }
            }
            return getListOfStringsMatchingLastWord(args, keys.toArray(new String[keys.size()]));
        }
        return null;
    }
}
