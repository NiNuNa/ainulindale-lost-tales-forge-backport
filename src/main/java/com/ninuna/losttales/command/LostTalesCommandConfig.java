package com.ninuna.losttales.command;

import com.ninuna.losttales.config.LostTalesConfigWords;
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
import net.minecraft.util.IChatComponent;

/**
 * The server's config from the console or an operator's chat: list the
 * categories and keys, read one, set one, or reload the file and restart
 * what its categories own. Every change goes through the same service
 * the settings screen uses, so the file, the screen and the running
 * server never disagree. The service's own words (why a change is
 * refused, what restarted) and each option's tip are lang keys too,
 * read in the sender's language like the command's.
 */
public final class LostTalesCommandConfig extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.config.";

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
            say(sender, EnumChatFormatting.RED, SAY + "side_only");
            return;
        }
        String action = args[0];
        if ("reload".equalsIgnoreCase(action)) {
            List<String> restarted = LostTalesServerConfigService.reloadAll();
            if (restarted.isEmpty()) {
                say(sender, EnumChatFormatting.GREEN, SAY + "reloaded");
            } else {
                say(sender, EnumChatFormatting.GREEN, SAY + "reloaded.restarted",
                        restartedWords(restarted));
            }
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
            say(sender, EnumChatFormatting.GRAY, SAY + "categories",
                    join(new ArrayList<String>(categories)));
            return;
        }
        int shown = 0;
        for (ServerConfigEntry entry : entries) {
            if (entry.getCategory().equalsIgnoreCase(category)) {
                say(sender, EnumChatFormatting.GRAY, SAY + "entry", entry.getKey(),
                        shownValue(entry));
                shown++;
            }
        }
        if (shown == 0) {
            say(sender, EnumChatFormatting.RED, SAY + "no_category", category);
        }
    }

    private void get(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/losttales config get <category> <key>");
            return;
        }
        ServerConfigEntry entry = ServerConfigSnapshot.find(
                LostTalesServerConfigService.snapshot(), args[1], args[2]);
        if (entry == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_key", args[1], args[2]);
            return;
        }
        say(sender, EnumChatFormatting.GRAY, SAY + "entry", entry.qualifiedName(),
                shownValue(entry));
        // What the option does, its tip line: the words of the file's
        // comment, in the reader's language. A key the mod does not
        // define, left in the file, has none.
        String tip = LostTalesConfigWords.tipKey(entry.getCategory(), entry.getKey());
        if (LostTalesConfigWords.hasEnglish(tip)) {
            say(sender, EnumChatFormatting.DARK_GRAY, tip);
        }
    }

    private void set(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/losttales config set <category> <key> <value> [more list items...]");
            return;
        }
        ServerConfigEntry entry = ServerConfigSnapshot.find(
                LostTalesServerConfigService.snapshot(), args[1], args[2]);
        if (entry == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_key", args[1], args[2]);
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

    /**
     * What the config service made of a change, for the role and Discord
     * commands too: its message, what it applied and restarted, and each
     * refusal with its reason.
     */
    static void report(ICommandSender sender, ServerConfigApplyResult result) {
        if (result.getMessage().length() > 0) {
            say(sender, EnumChatFormatting.RED, result.getMessage());
        }
        if (!result.getApplied().isEmpty()) {
            if (result.getRestarted().isEmpty()) {
                say(sender, EnumChatFormatting.GREEN, SAY + "applied",
                        join(result.getApplied()));
            } else {
                say(sender, EnumChatFormatting.GREEN, SAY + "applied.restarted",
                        join(result.getApplied()), restartedWords(result.getRestarted()));
            }
        }
        for (ServerConfigApplyResult.Refusal refusal : result.getRefused()) {
            say(sender, EnumChatFormatting.RED, SAY + "refused", refusal.getName(),
                    words(refusal.getReasonKey(), refusal.getReasonArguments().toArray()));
        }
    }

    /** The names of what restarted, joined by commas, each in the reader's words. */
    private static IChatComponent restartedWords(List<String> keys) {
        IChatComponent joined = new ChatComponentText("");
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                joined.appendSibling(new ChatComponentText(", "));
            }
            joined.appendSibling(words(keys.get(index)));
        }
        return joined;
    }

    /** An entry's value in white; a secret never shows. */
    private static IChatComponent shownValue(ServerConfigEntry entry) {
        IChatComponent value = entry.isSecret() ? words(SAY + "secret")
                : new ChatComponentText(entry.isList() ? entry.getValues().toString()
                        : entry.getValue());
        value.getChatStyle().setColor(EnumChatFormatting.WHITE);
        return value;
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
        usage(sender, getCommandUsage(sender));
        usage(sender, "/losttales config list [category]");
        usage(sender, "/losttales config get <category> <key>");
        usage(sender, "/losttales config set <category> <key> <value> [more list items...]");
        usage(sender, "/losttales config reload");
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
