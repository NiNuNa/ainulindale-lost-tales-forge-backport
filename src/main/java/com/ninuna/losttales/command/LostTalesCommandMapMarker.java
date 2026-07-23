package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerReseedService;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneGenerationState;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import com.ninuna.losttales.world.waystone.LostTalesWaystonePlacementResult;
import com.ninuna.losttales.world.waystone.LostTalesWaystonePlacementService;
import cpw.mods.fml.common.FMLLog;
/**
 * Legacy Forge companion to the modern map-marker command.
 *
 * Operator tools for player discovery state, bundled marker inspection, and
 * retrying failed waystone generation.
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
        return commandPrefix() + " <list|dynamic|known|discover|forget|track|untrack|retry|reseed> [markerId|all] [player]";
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

        if ("retry".equalsIgnoreCase(action)) {
            if (args.length < 2) {
                sendUsage(sender);
            } else {
                retryWaystoneGeneration(sender,
                        LostTalesQuestMarkerHelper.normalizeMarkerId(
                                args[1]));
            }
            return;
        }

        if ("reseed".equalsIgnoreCase(action)
                || "regenerate".equalsIgnoreCase(action)) {
            if (args.length < 2) {
                sendUsage(sender);
            } else {
                reseedMarkers(sender, args[1]);
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
            send(sender, EnumChatFormatting.YELLOW + player.getCommandSenderName() + " already knows marker " + formatMarkerId(markerId) + ".");
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

    private void retryWaystoneGeneration(
            ICommandSender sender, String markerId) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer overworld = server == null
                ? null : server.worldServerForDimension(0);
        if (overworld == null) {
            send(sender, EnumChatFormatting.RED
                    + "The marker repository is not available.");
            return;
        }
        LostTalesMapMarkerWorldData data;
        try {
            data = LostTalesMapMarkerStorage.get(overworld);
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED
                    + "The marker repository could not be opened.");
            return;
        }
        LostTalesMapMarkerRecord record = data.getRecord(markerId);
        if (record == null) {
            send(sender, EnumChatFormatting.RED
                    + "Unknown marker: " + markerId);
            return;
        }
        if (!record.hasWaystone()
                || record.isLinked()
                || (record.getGenerationState()
                        != LostTalesWaystoneGenerationState
                                .FAILED_OR_BLOCKED
                    && record.getGenerationState()
                        != LostTalesWaystoneGenerationState
                                .NOT_ATTEMPTED)) {
            send(sender, EnumChatFormatting.YELLOW
                    + "Marker " + markerId
                    + " is not an unlinked waystone awaiting generation.");
            return;
        }
        WorldServer world = server.worldServerForDimension(
                record.getDimensionId());
        if (world == null) {
            send(sender, EnumChatFormatting.RED
                    + "Dimension " + record.getDimensionId()
                    + " is not loaded.");
            return;
        }
        int chunkX = floor(record.getX()) >> 4;
        int chunkZ = floor(record.getZ()) >> 4;
        if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
            send(sender, EnumChatFormatting.YELLOW
                    + "The target chunk is not loaded. Visit the marker area, then retry; no chunk was force-loaded.");
            return;
        }
        LostTalesMapMarkerRecord retry =
                record.withGenerationState(
                        LostTalesWaystoneGenerationState.NOT_ATTEMPTED,
                        "operator_retry");
        data.saveRecord(retry);
        LostTalesWaystonePlacementResult result =
                LostTalesWaystonePlacementService.attempt(world, retry);
        send(sender, (result.getStatus()
                == LostTalesWaystonePlacementResult.Status.SUCCESS
                        ? EnumChatFormatting.GREEN
                        : EnumChatFormatting.YELLOW)
                + "Waystone retry for " + markerId + ": "
                + result.getStatus().name().toLowerCase(Locale.ROOT)
                + " (" + result.getReason() + ").");
    }

    private static int floor(double value) {
        int truncated = (int)value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private void reseedMarkers(
            ICommandSender sender, String requestedId) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer overworld = server == null
                ? null : server.worldServerForDimension(0);
        if (overworld == null) {
            send(sender, EnumChatFormatting.RED
                    + "The marker repository is not available.");
            return;
        }

        LostTalesMapMarkerCatalog.reloadFromClasspath();
        Collection<LostTalesMapMarkerDefinition> definitions;
        if ("all".equalsIgnoreCase(requestedId)) {
            definitions = LostTalesMapMarkerCatalog.getMarkers();
        } else {
            String markerId =
                    LostTalesQuestMarkerHelper.normalizeMarkerId(
                            requestedId);
            LostTalesMapMarkerDefinition definition =
                    LostTalesMapMarkerCatalog.getMarker(markerId);
            if (definition == null) {
                send(sender, EnumChatFormatting.RED
                        + "Unknown bundled marker: " + markerId);
                return;
            }
            definitions = java.util.Collections.singleton(definition);
        }

        LostTalesMapMarkerWorldData data;
        try {
            data = LostTalesMapMarkerStorage.get(overworld);
        } catch (RuntimeException exception) {
            send(sender, EnumChatFormatting.RED
                    + "The marker repository could not be opened.");
            return;
        }
        if (data.isReadOnlyForNewerVersion()) {
            send(sender, EnumChatFormatting.RED
                    + "The marker repository is read-only because it uses data version "
                    + data.getUnsupportedDataVersion() + ".");
            return;
        }

        int reseeded = 0;
        int linked = 0;
        int placed = 0;
        int deferred = 0;
        int blocked = 0;
        String firstFailure = "";
        for (LostTalesMapMarkerDefinition definition : definitions) {
            try {
                LostTalesMapMarkerRecord record =
                        LostTalesMapMarkerReseedService.reseed(
                                data, definition);
                reseeded++;
                if (record.isLinked()) {
                    linked++;
                    continue;
                }
                if (!record.hasWaystone()) {
                    continue;
                }
                WorldServer world = server.worldServerForDimension(
                        record.getDimensionId());
                int chunkX = floor(record.getX()) >> 4;
                int chunkZ = floor(record.getZ()) >> 4;
                if (world == null
                        || !world.getChunkProvider().chunkExists(
                                chunkX, chunkZ)) {
                    deferred++;
                    continue;
                }
                LostTalesWaystonePlacementResult result =
                        LostTalesWaystonePlacementService.attempt(
                                world, record);
                if (result.getStatus()
                        == LostTalesWaystonePlacementResult.Status.SUCCESS) {
                    placed++;
                } else if (result.getStatus()
                        == LostTalesWaystonePlacementResult.Status.DEFERRED) {
                    deferred++;
                } else {
                    blocked++;
                }
            } catch (RuntimeException exception) {
                blocked++;
                if (firstFailure.length() == 0) {
                    firstFailure = definition.getId() + ": "
                            + exception.getMessage();
                }
                FMLLog.warning(
                        "[%s] Could not reseed bundled marker %s: %s",
                        LostTalesMetaData.MOD_ID, definition.getId(),
                        exception.getMessage());
            }
        }
        LostTalesMapMarkerSyncManager.syncAll();
        send(sender, (blocked == 0
                ? EnumChatFormatting.GREEN
                : EnumChatFormatting.YELLOW)
                + "Reseeded " + reseeded + " bundled marker(s) from JSON"
                + "; preserved " + linked + " linked waystone(s)"
                + ", placed " + placed + ", deferred " + deferred
                + ", blocked " + blocked + ".");
        if (linked > 0) {
            send(sender, EnumChatFormatting.GRAY
                    + "Linked waystones kept their live block coordinates and link tokens.");
        }
        if (firstFailure.length() > 0) {
            send(sender, EnumChatFormatting.RED
                    + "First reseed failure: " + firstFailure);
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
        send(sender, EnumChatFormatting.YELLOW + "Warning: " + markerId + " is not present in bundled map_markers JSON or " + player.getCommandSenderName() + "'s dynamic marker data. The ID will still be stored so resource-pack/server experiments are not blocked.");
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
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " retry losttales:marker_id");
        send(sender, EnumChatFormatting.GRAY + commandPrefix() + " reseed <markerId|all>");
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
            return getListOfStringsMatchingLastWord(args, "list", "dynamic", "known", "catalog", "discover", "reveal", "forget", "hide", "track", "untrack", "retry", "reseed", "regenerate");
        }
        if (args.length == 2 && ("discover".equalsIgnoreCase(args[0]) || "reveal".equalsIgnoreCase(args[0]) || "forget".equalsIgnoreCase(args[0]) || "hide".equalsIgnoreCase(args[0]) || "track".equalsIgnoreCase(args[0]) || "retry".equalsIgnoreCase(args[0]) || "reseed".equalsIgnoreCase(args[0]) || "regenerate".equalsIgnoreCase(args[0]))) {
            List<String> ids = collectKnownMarkerIds();
            if ("reseed".equalsIgnoreCase(args[0])
                    || "regenerate".equalsIgnoreCase(args[0])) {
                ids.add(0, "all");
            }
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
