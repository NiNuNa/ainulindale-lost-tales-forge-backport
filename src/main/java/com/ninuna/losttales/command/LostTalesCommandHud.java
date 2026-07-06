package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/**
 * Small config command for HUD placement presets and percent-based offsets.
 *
 * These values are the same legacy Forge config values used by the client HUD
 * renderers. In singleplayer/integrated-server testing this gives quick feedback;
 * dedicated-server admins should still treat the HUD values as client config.
 */
public class LostTalesCommandHud extends LostTalesCommandBase {
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
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
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
            send(sender, EnumChatFormatting.RED + "Usage: " + commandPrefix() + " preset <custom|default|lotr-safe|compact|minimal>");
            return;
        }
        if (LostTalesConfig.applyHudPreset(args[1])) {
            send(sender, EnumChatFormatting.GREEN + "Applied HUD preset: " + args[1]);
            sendStatus(sender);
        } else {
            send(sender, EnumChatFormatting.RED + "Unknown HUD preset: " + args[1]);
        }
    }

    private void setOffset(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, EnumChatFormatting.RED + "Usage: " + commandPrefix() + " set <compass|quickloot|quest> <xPercent> <yPercent>");
            return;
        }
        String element = LostTalesConfig.normalizeHudElement(args[1]);
        if (element.length() == 0) {
            send(sender, EnumChatFormatting.RED + "Unknown HUD element: " + args[1]);
            return;
        }
        Integer x = parseInt(sender, args[2], "xPercent");
        Integer y = parseInt(sender, args[3], "yPercent");
        if (x == null || y == null) {
            return;
        }
        LostTalesConfig.setHudOffset(element, x.intValue(), y.intValue());
        send(sender, EnumChatFormatting.GREEN + "Set " + element + " HUD offset to " + formatOffset(element) + ".");
    }

    private void moveOffset(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, EnumChatFormatting.RED + "Usage: " + commandPrefix() + " move <compass|quickloot|quest> <dxPercent> <dyPercent>");
            return;
        }
        String element = LostTalesConfig.normalizeHudElement(args[1]);
        if (element.length() == 0) {
            send(sender, EnumChatFormatting.RED + "Unknown HUD element: " + args[1]);
            return;
        }
        Integer dx = parseInt(sender, args[2], "dxPercent");
        Integer dy = parseInt(sender, args[3], "dyPercent");
        if (dx == null || dy == null) {
            return;
        }
        LostTalesConfig.moveHudOffset(element, dx.intValue(), dy.intValue());
        send(sender, EnumChatFormatting.GREEN + "Moved " + element + " HUD offset to " + formatOffset(element) + ".");
    }

    private void toggle(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, EnumChatFormatting.RED + "Usage: " + commandPrefix() + " toggle <hud|compass|quickloot|quest|worldmarkers>");
            return;
        }

        String key = args[1].toLowerCase();
        if ("hud".equals(key) || "all".equals(key)) {
            LostTalesConfig.toggleLostTalesHud();
            send(sender, EnumChatFormatting.GREEN + "Lost Tales HUD is now " + onOff(LostTalesConfig.showLostTalesHud) + ".");
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
            send(sender, EnumChatFormatting.RED + "Unknown HUD toggle: " + args[1]);
            return;
        }

        LostTalesConfig.save();
        sendStatus(sender);
    }

    private Integer parseInt(ICommandSender sender, String value, String name) {
        try {
            return Integer.valueOf(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            send(sender, EnumChatFormatting.RED + name + " must be a whole number: " + value);
            return null;
        }
    }

    private void sendStatus(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "Lost Tales HUD config:");
        send(sender, EnumChatFormatting.GRAY + "Master: " + onOff(LostTalesConfig.showLostTalesHud)
                + ", compass: " + onOff(LostTalesConfig.showCompassHud)
                + ", quick loot: " + onOff(LostTalesConfig.showQuickLootHud)
                + ", quest: " + onOff(LostTalesConfig.showQuestHud)
                + ", world markers: " + onOff(LostTalesConfig.showWorldQuestMarkers));
        send(sender, EnumChatFormatting.GRAY + "Preset: " + LostTalesConfig.hudPlacementPreset
                + ", compass " + formatOffset("compass")
                + ", quick loot " + formatOffset("quickloot")
                + ", quest " + formatOffset("quest"));
        send(sender, EnumChatFormatting.DARK_GRAY + "Tip: use Shift+H client-side to open the HUD placement preview.");
    }

    private String formatOffset(String element) {
        if ("compass".equals(element)) {
            return LostTalesConfig.compassHudOffsetX + "," + LostTalesConfig.compassHudOffsetY;
        }
        if ("quickloot".equals(element)) {
            return LostTalesConfig.quickLootHudOffsetX + "," + LostTalesConfig.quickLootHudOffsetY;
        }
        if ("quest".equals(element)) {
            return LostTalesConfig.questHudOffsetX + "," + LostTalesConfig.questHudOffsetY;
        }
        return "unknown";
    }

    private String onOff(boolean value) {
        return value ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF";
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " status");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " preset lotr-safe");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " set compass 50 12");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " move quickloot -5 3");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " toggle worldmarkers");
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
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
            return getListOfStringsMatchingLastWord(args, "compass", "quickloot", "quest");
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "hud", "compass", "quickloot", "quest", "worldmarkers");
        }
        return null;
    }
}
