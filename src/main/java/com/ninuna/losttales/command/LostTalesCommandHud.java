package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.permission.LostTalesCapability;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
/**
 * Small config command for HUD placement presets and percent-based offsets.
 *
 * These values are the same legacy Forge config values used by the client HUD
 * renderers. The command is useful in an integrated server, but cannot alter a
 * remote client's configuration from a dedicated server. Presets and HUD
 * elements are named by their config ids.
 */
public class LostTalesCommandHud extends LostTalesCommandBase {
    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.hud.";

    private final String commandPath;

    public LostTalesCommandHud() {
        this(LostTalesMetaData.MOD_ID + "_hud", LostTalesMetaData.MOD_ID + "_hud");
    }

    public LostTalesCommandHud(String commandName, String commandPath) {
        super(commandName);
        this.commandPath = commandPath;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return commandPrefix() + " <status|preset|set|move|toggle> [args]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.HUD_ADMIN;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
            say(sender, EnumChatFormatting.RED, SAY + "dedicated");
            say(sender, EnumChatFormatting.GRAY, SAY + "dedicated.tip");
            return;
        }

        if (args.length == 0 || "status".equalsIgnoreCase(args[0]) || "show".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return;
        }

        String action = args[0];
        if ("preset".equalsIgnoreCase(action)) {
            applyPreset(sender, args);
        } else if ("set".equalsIgnoreCase(action)) {
            setOffset(sender, args);
        } else if ("move".equalsIgnoreCase(action)) {
            moveOffset(sender, args);
        } else if ("toggle".equalsIgnoreCase(action)) {
            toggle(sender, args);
        } else {
            sendUsage(sender);
        }
    }

    private void applyPreset(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, EnumChatFormatting.RED, SAY + "usage",
                    commandPrefix() + " preset <custom|default|lotr-safe|compact|minimal>");
            return;
        }
        if (LostTalesConfig.applyHudPreset(args[1])) {
            say(sender, EnumChatFormatting.GREEN, SAY + "preset.applied", args[1]);
            sendStatus(sender);
        } else {
            say(sender, EnumChatFormatting.RED, SAY + "preset.unknown", args[1]);
        }
    }

    private void setOffset(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            say(sender, EnumChatFormatting.RED, SAY + "usage", commandPrefix()
                    + " set <compass|fellowship|quickloot|quest|notifications> <xPercent> <yPercent>");
            return;
        }
        String element = LostTalesConfig.normalizeHudElement(args[1]);
        if (element.length() == 0) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown_element", args[1]);
            return;
        }
        Integer x = parseInt(sender, args[2], "xPercent");
        Integer y = parseInt(sender, args[3], "yPercent");
        if (x == null || y == null) {
            return;
        }
        LostTalesConfig.setHudOffset(element, x.intValue(), y.intValue());
        say(sender, EnumChatFormatting.GREEN, SAY + "set", element, formatOffset(element));
    }

    private void moveOffset(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            say(sender, EnumChatFormatting.RED, SAY + "usage", commandPrefix()
                    + " move <compass|fellowship|quickloot|quest|notifications> <dxPercent> <dyPercent>");
            return;
        }
        String element = LostTalesConfig.normalizeHudElement(args[1]);
        if (element.length() == 0) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown_element", args[1]);
            return;
        }
        Integer dx = parseInt(sender, args[2], "dxPercent");
        Integer dy = parseInt(sender, args[3], "dyPercent");
        if (dx == null || dy == null) {
            return;
        }
        LostTalesConfig.moveHudOffset(element, dx.intValue(), dy.intValue());
        say(sender, EnumChatFormatting.GREEN, SAY + "moved", element, formatOffset(element));
    }

    private void toggle(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, EnumChatFormatting.RED, SAY + "usage",
                    commandPrefix() + " toggle <hud|compass|quickloot|quest|worldmarkers>");
            return;
        }

        String key = args[1].toLowerCase();
        if ("hud".equals(key) || "all".equals(key)) {
            LostTalesConfig.toggleLostTalesHud();
            say(sender, EnumChatFormatting.GREEN, SAY + "toggled",
                    onOff(LostTalesConfig.showLostTalesHud));
            return;
        }
        if ("compass".equals(key)) {
            LostTalesConfig.showCompassHud = !LostTalesConfig.showCompassHud;
        } else if ("quickloot".equals(key) || "loot".equals(key)) {
            LostTalesConfig.showQuickLootHud = !LostTalesConfig.showQuickLootHud;
        } else if ("quest".equals(key) || "quests".equals(key)) {
            LostTalesConfig.showQuestHud = !LostTalesConfig.showQuestHud;
        } else if ("worldmarkers".equals(key) || "world".equals(key)) {
            LostTalesConfig.showWorldQuestMarkers = !LostTalesConfig.showWorldQuestMarkers;
        } else {
            say(sender, EnumChatFormatting.RED, SAY + "unknown_toggle", args[1]);
            return;
        }

        LostTalesConfig.save();
        sendStatus(sender);
    }

    private Integer parseInt(ICommandSender sender, String value, String name) {
        try {
            return Integer.valueOf(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            say(sender, EnumChatFormatting.RED, SAY + "not_whole", name, value);
            return null;
        }
    }

    private void sendStatus(ICommandSender sender) {
        say(sender, EnumChatFormatting.GOLD, SAY + "status.header");
        say(sender, EnumChatFormatting.GRAY, SAY + "status.shown",
                onOff(LostTalesConfig.showLostTalesHud),
                onOff(LostTalesConfig.showCompassHud),
                onOff(LostTalesConfig.showQuickLootHud),
                onOff(LostTalesConfig.showQuestHud),
                onOff(LostTalesConfig.showWorldQuestMarkers));
        say(sender, EnumChatFormatting.GRAY, SAY + "status.placed",
                LostTalesConfig.hudPlacementPreset, formatOffset("compass"),
                formatOffset("fellowship"), formatOffset("quickloot"), formatOffset("quest"));
        say(sender, EnumChatFormatting.GRAY, SAY + "status.notifications",
                formatOffset("notifications"));
        say(sender, EnumChatFormatting.DARK_GRAY, SAY + "status.tip");
    }

    /** An element's offset as {@code x,y} percent, or the word for unknown. */
    private Object formatOffset(String element) {
        if ("compass".equals(element)) {
            return formatOffset(LostTalesConfig.compassHudOffsetX,
                    LostTalesConfig.compassHudOffsetY);
        }
        if ("quickloot".equals(element)) {
            return formatOffset(LostTalesConfig.quickLootHudOffsetX,
                    LostTalesConfig.quickLootHudOffsetY);
        }
        if ("fellowship".equals(element)) {
            return formatOffset(LostTalesConfig.fellowshipHudOffsetX,
                    LostTalesConfig.fellowshipHudOffsetY);
        }
        if ("quest".equals(element)) {
            return formatOffset(LostTalesConfig.questHudOffsetX,
                    LostTalesConfig.questHudOffsetY);
        }
        if ("notifications".equals(element)) {
            return formatOffset(LostTalesConfig.notificationHudOffsetX,
                    LostTalesConfig.notificationHudOffsetY);
        }
        return words(SAY + "unknown_offset");
    }

    private String formatOffset(double x, double y) {
        return formatPercent(x) + "," + formatPercent(y);
    }

    private String formatPercent(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** On in green or off in red, in the reader's words. */
    private static IChatComponent onOff(boolean value) {
        return value ? line(EnumChatFormatting.GREEN, SAY + "on")
                : line(EnumChatFormatting.RED, SAY + "off");
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        usage(sender, commandPrefix() + " status");
        usage(sender, commandPrefix() + " preset lotr-safe");
        usage(sender, commandPrefix() + " set compass 50 12");
        usage(sender, commandPrefix() + " move quickloot -5 3");
        usage(sender, commandPrefix() + " toggle worldmarkers");
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "show", "preset", "set", "move", "toggle");
        }
        if (args.length == 2 && "preset".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "custom", "default", "lotr-safe", "compact", "minimal");
        }
        if (args.length == 2 && ("set".equalsIgnoreCase(args[0]) || "move".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args,
                    "compass", "fellowship", "quickloot", "quest", "notifications");
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "hud", "compass", "quickloot", "quest", "worldmarkers");
        }
        return null;
    }
}
