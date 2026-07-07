package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
/**
 * Legacy Forge companion to the modern map-marker command.
 *
 * The 1.21 branch can add/remove shared level markers with server attachments.
 * Minecraft 1.7.10 does not have that storage/networking model yet, so this
 * command focuses on the player-discovery state used by quest marker hints and
 * exposes a read-only catalog of bundled marker JSON for debugging.
 */
public class LostTalesCommandMapMarker extends LostTalesCommandBase {

    private final String commandPath;

    public LostTalesCommandMapMarker() {
        this(LostTalesMetaData.MOD_ID + "_mapmarker", LostTalesMetaData.MOD_ID + "_mapmarker");
    }

    public LostTalesCommandMapMarker(String commandName, String commandPath) {
        super(commandName);
        this.commandPath = commandPath;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return commandPrefix() + " <list|dynamic|known|discover|forget|track|untrack> [markerId] [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String action = args[0];
        if ("known".equalsIgnoreCase(action) || "catalog".equalsIgnoreCase(action)) {
            String filter = args.length > 1 ? args[1] : "";
            listKnownMarkers(sender, filter);
            return;
        }

        if ("list".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                listMarkers(sender, player);
            }
            return;
        }

        if ("dynamic".equalsIgnoreCase(action) || "dynamics".equalsIgnoreCase(action) || "runtime".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                String filter = args.length > 2 ? args[2] : "";
                listDynamicMarkers(sender, player, filter);
            }
            return;
        }

        if ("untrack".equalsIgnoreCase(action)) {
            EntityPlayerMP player = getTargetPlayer(sender, args, 1);
            if (player != null) {
                untrackMarker(sender, player);
            }
            return;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(args[1]);
        EntityPlayerMP player = getTargetPlayer(sender, args, 2);
        if (player == null) {
            return;
        }

        if ("discover".equalsIgnoreCase(action) || "reveal".equalsIgnoreCase(action)) {
            discoverMarker(sender, player, markerId);
        } else if ("forget".equalsIgnoreCase(action) || "hide".equalsIgnoreCase(action)) {
            forgetMarker(sender, player, markerId);
        } else if ("track".equalsIgnoreCase(action)) {
            trackMarker(sender, player, markerId);
        } else {
            sendUsage(sender);
        }
    }

    private void listKnownMarkers(ICommandSender sender, String filter) {
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<LostTalesMapMarkerDefinition> visible = new ArrayList<LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.getMarkers()) {
            if (marker == null) {
                continue;
            }
            if (normalizedFilter.length() == 0 || marker.getId().toLowerCase(Locale.ROOT).contains(normalizedFilter) || marker.getName().toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                visible.add(marker);
            }
        }

        if (visible.isEmpty()) {
            send(sender, EnumChatFormatting.YELLOW + "No bundled map markers found" + (normalizedFilter.length() > 0 ? " for filter: " + filter : "."));
            return;
        }

        send(sender, EnumChatFormatting.GOLD + "Bundled map markers (" + visible.size() + "):");
        int shown = 0;
        for (LostTalesMapMarkerDefinition marker : visible) {
            if (shown >= 12) {
                send(sender, EnumChatFormatting.GRAY + "...and " + (visible.size() - shown) + " more. Use a filter to narrow the list.");
                break;
            }
            String hidden = marker.isHiddenUntilDiscovered() ? EnumChatFormatting.DARK_GRAY + " hidden" : "";
            send(sender, EnumChatFormatting.GRAY + "- " + marker.getShortDescription() + hidden);
            shown++;
        }
    }

    private void listMarkers(ICommandSender sender, EntityPlayerMP player) {
        Set<String> markers = LostTalesQuestManager.getDiscoveredMarkerIds(player);
        String pinned = LostTalesQuestManager.getPinnedMapMarkerId(player);
        Map<String, LostTalesMapMarkerDefinition> dynamicMarkers = collectDynamicMarkerMap(player);

        send(sender, EnumChatFormatting.GOLD + "Map marker state for " + player.getCommandSenderName() + ":");
        send(sender, EnumChatFormatting.GRAY + "Tracked marker: " + (pinned.length() == 0 ? "none" : formatMarkerId(pinned, dynamicMarkers)));
        if (markers.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "Discovered markers: none");
        } else {
            send(sender, EnumChatFormatting.GRAY + "Discovered markers: " + joinFormatted(markers, dynamicMarkers));
        }
        if (!dynamicMarkers.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "Dynamic quest-giver markers: " + dynamicMarkers.size() + " (use " + commandPrefix() + " dynamic " + player.getCommandSenderName() + " for details)");
        }
    }

    private void listDynamicMarkers(ICommandSender sender, EntityPlayerMP player, String filter) {
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<LostTalesMapMarkerDefinition> visible = new ArrayList<LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker : LostTalesQuestManager.getDynamicMapMarkers(player)) {
            if (marker == null) {
                continue;
            }
            String id = marker.getId() == null ? "" : marker.getId();
            String name = marker.getName() == null ? "" : marker.getName();
            if (normalizedFilter.length() == 0 || id.toLowerCase(Locale.ROOT).contains(normalizedFilter) || name.toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                visible.add(marker);
            }
        }

        if (visible.isEmpty()) {
            send(sender, EnumChatFormatting.YELLOW + "No dynamic quest-giver markers found for " + player.getCommandSenderName() + (normalizedFilter.length() > 0 ? " with filter: " + filter : "."));
            return;
        }

        send(sender, EnumChatFormatting.GOLD + "Dynamic quest-giver markers for " + player.getCommandSenderName() + " (" + visible.size() + "):");
        int shown = 0;
        for (LostTalesMapMarkerDefinition marker : visible) {
            if (shown >= 12) {
                send(sender, EnumChatFormatting.GRAY + "...and " + (visible.size() - shown) + " more. Use a filter to narrow the list.");
                break;
            }
            send(sender, EnumChatFormatting.GRAY + "- " + marker.getShortDescription() + EnumChatFormatting.DARK_GRAY + " dynamic");
            shown++;
        }
    }

    private void discoverMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        warnIfUnknownMarker(sender, player, markerId);
        if (LostTalesQuestManager.revealMapMarker(player, markerId)) {
            send(sender, EnumChatFormatting.GREEN + "Discovered marker " + formatMarkerId(markerId) + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " already knows marker " + formatMarkerId(markerId) + " or marker discovery is disabled.");
        }
    }

    private void forgetMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        if (LostTalesQuestManager.forgetMapMarker(player, markerId)) {
            send(sender, EnumChatFormatting.GREEN + "Forgot marker " + formatMarkerId(markerId) + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " did not have marker " + formatMarkerId(markerId) + " discovered.");
        }
    }

    private void trackMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        warnIfUnknownMarker(sender, player, markerId);
        if (LostTalesQuestManager.pinMapMarker(player, markerId)) {
            send(sender, EnumChatFormatting.GREEN + "Tracking marker " + formatMarkerId(markerId) + " for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + "Could not track marker " + formatMarkerId(markerId) + ". It may not be discovered yet.");
        }
    }

    private void untrackMarker(ICommandSender sender, EntityPlayerMP player) {
        if (LostTalesQuestManager.unpinMapMarker(player)) {
            send(sender, EnumChatFormatting.GREEN + "Stopped tracking marker for " + player.getCommandSenderName() + ".");
        } else {
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " had no tracked marker.");
        }
    }

    private EntityPlayerMP getTargetPlayer(ICommandSender sender, String[] args, int playerArgIndex) {
        try {
            if (args.length > playerArgIndex) {
                return getPlayer(sender, args[playerArgIndex]);
            }
            if (sender instanceof EntityPlayerMP) {
                return (EntityPlayerMP) sender;
            }
            send(sender, EnumChatFormatting.RED + "Console must specify a player.");
            return null;
        } catch (Exception e) {
            String playerName = args.length > playerArgIndex ? args[playerArgIndex] : "";
            send(sender, EnumChatFormatting.RED + "Could not find player: " + playerName);
            return null;
        }
    }

    private void warnIfUnknownMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        if (markerId == null || markerId.length() == 0 || LostTalesMapMarkerCatalog.containsMarker(markerId)) {
            return;
        }
        LostTalesMapMarkerDefinition dynamic = null;
        if (player != null) {
            for (LostTalesMapMarkerDefinition marker : LostTalesQuestManager.getDynamicMapMarkers(player)) {
                if (marker != null && markerId.equals(marker.getId())) {
                    dynamic = marker;
                    break;
                }
            }
        }
        if (dynamic != null) {
            return;
        }
        send(sender, EnumChatFormatting.YELLOW + "Warning: " + markerId + " is not present in bundled map_marker JSON or " + player.getCommandSenderName() + "'s dynamic marker data. The ID will still be stored so resource-pack/server experiments are not blocked.");
    }

    private String formatMarkerId(String markerId) {
        return formatMarkerId(markerId, null);
    }

    private String formatMarkerId(String markerId, Map<String, LostTalesMapMarkerDefinition> dynamicMarkers) {
        if (dynamicMarkers != null) {
            LostTalesMapMarkerDefinition marker = dynamicMarkers.get(markerId);
            if (marker != null) {
                return marker.getId() + " (" + marker.getName() + ", dynamic @ " + Math.round(marker.getX()) + ", " + Math.round(marker.getY()) + ", " + Math.round(marker.getZ()) + ")";
            }
        }
        return LostTalesMapMarkerCatalog.getDisplayName(markerId);
    }

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.GRAY + getCommandUsage(sender));
        send(sender, EnumChatFormatting.GRAY + "Examples:");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " known");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " list <player>");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " dynamic <player> [filter]");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " discover losttales:quest_giver_nia <player>");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " track losttales:quest_giver_nia <player>");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " forget losttales:quest_giver_nia <player>");
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    private String joinFormatted(Collection<String> values, Map<String, LostTalesMapMarkerDefinition> dynamicMarkers) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(formatMarkerId(value, dynamicMarkers));
            first = false;
        }
        return builder.toString();
    }

    private Map<String, LostTalesMapMarkerDefinition> collectDynamicMarkerMap(EntityPlayerMP player) {
        Map<String, LostTalesMapMarkerDefinition> byId = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerDefinition marker : LostTalesQuestManager.getDynamicMapMarkers(player)) {
            if (marker != null && marker.getId() != null) {
                byId.put(marker.getId(), marker);
            }
        }
        return byId;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "dynamic", "known", "catalog", "discover", "reveal", "forget", "hide", "track", "untrack");
        }
        if (args.length == 2 && ("discover".equalsIgnoreCase(args[0]) || "reveal".equalsIgnoreCase(args[0]) || "forget".equalsIgnoreCase(args[0]) || "hide".equalsIgnoreCase(args[0]) || "track".equalsIgnoreCase(args[0]))) {
            List<String> ids = collectKnownMarkerIds();
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        return null;
    }

    private List<String> collectKnownMarkerIds() {
        Set<String> ids = new LinkedHashSet<String>();
        ids.addAll(LostTalesMapMarkerCatalog.getMarkerIds());
        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
            ids.addAll(LostTalesQuestMarkerHelper.collectQuestMarkerIds(quest));
        }
        return new ArrayList<String>(ids);
    }
}
