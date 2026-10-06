package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
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
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import com.ninuna.losttales.world.waystone.LostTalesWaystonePlacementResult;
import com.ninuna.losttales.world.waystone.LostTalesWaystonePlacementService;
import com.ninuna.losttales.permission.LostTalesCapability;
import cpw.mods.fml.common.FMLLog;
/**
 * Legacy Forge companion to the modern map-marker command.
 *
 * Operator tools for player discovery state, bundled marker inspection, and
 * retrying failed waystone generation. Markers are named by their ids and
 * the names their JSON gives them; a placement's reason is its code.
 */
public class LostTalesCommandMapMarker extends LostTalesCommandBase {

    /** What the lang key of each of the command's answers begins with. */
    static final String SAY = "chat.losttales.command.mapmarker.";

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
    public LostTalesCapability getCapability() {
        return LostTalesCapability.MAPMARKER_MANAGE;
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
            if (normalizedFilter.length() > 0) {
                say(sender, EnumChatFormatting.YELLOW, SAY + "known.none.filter", filter);
            } else {
                say(sender, EnumChatFormatting.YELLOW, SAY + "known.none");
            }
            return;
        }

        say(sender, EnumChatFormatting.GOLD, SAY + "known.header",
                Integer.valueOf(visible.size()));
        int shown = 0;
        for (LostTalesMapMarkerDefinition marker : visible) {
            if (shown >= 12) {
                say(sender, EnumChatFormatting.GRAY, SAY + "more",
                        Integer.valueOf(visible.size() - shown));
                break;
            }
            if (marker.isHiddenUntilDiscovered()) {
                say(sender, EnumChatFormatting.GRAY, SAY + "entry.marked", describe(marker),
                        line(EnumChatFormatting.DARK_GRAY, SAY + "hidden"));
            } else {
                say(sender, EnumChatFormatting.GRAY, SAY + "entry", describe(marker));
            }
            shown++;
        }
    }

    /** A marker's id, name, dimension, place and discovery radius. */
    private static IChatComponent describe(LostTalesMapMarkerDefinition marker) {
        return words(SAY + "marker", marker.getId(),
                LostTalesMapMarkerNames.component(marker.getId(), marker.getName(),
                        marker.getNamedAfter()),
                Integer.valueOf(marker.getDimensionId()), coordinate(marker.getX()),
                coordinate(marker.getY()), coordinate(marker.getZ()),
                coordinate(marker.getDiscoveryRadius()));
    }

    /** A coordinate as a whole number where it is one. */
    private static String coordinate(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01D ? String.valueOf(rounded) : String.valueOf(value);
    }

    private void listMarkers(ICommandSender sender, EntityPlayerMP player) {
        Set<String> markers = LostTalesQuestManager.getDiscoveredMarkerIds(player);
        String pinned = LostTalesQuestManager.getPinnedMapMarkerId(player);
        Map<String, LostTalesMapMarkerDefinition> dynamicMarkers = collectDynamicMarkerMap(player);

        say(sender, EnumChatFormatting.GOLD, SAY + "list.header", player.getCommandSenderName());
        say(sender, EnumChatFormatting.GRAY, SAY + "list.tracked", pinned.length() == 0
                ? words(SAY + "none") : formatMarkerId(pinned, dynamicMarkers));
        say(sender, EnumChatFormatting.GRAY, SAY + "list.discovered", markers.isEmpty()
                ? words(SAY + "none") : joinFormatted(markers, dynamicMarkers));
        if (!dynamicMarkers.isEmpty()) {
            say(sender, EnumChatFormatting.GRAY, SAY + "list.dynamic",
                    Integer.valueOf(dynamicMarkers.size()), commandPrefix(),
                    player.getCommandSenderName());
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
            String namedAfter = LostTalesMapMarkerNamedAfter.subject(marker.getNamedAfter());
            if (normalizedFilter.length() == 0 || id.toLowerCase(Locale.ROOT).contains(normalizedFilter) || name.toLowerCase(Locale.ROOT).contains(normalizedFilter)
                    || namedAfter.toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                visible.add(marker);
            }
        }

        if (visible.isEmpty()) {
            if (normalizedFilter.length() > 0) {
                say(sender, EnumChatFormatting.YELLOW, SAY + "dynamic.none.filter",
                        player.getCommandSenderName(), filter);
            } else {
                say(sender, EnumChatFormatting.YELLOW, SAY + "dynamic.none",
                        player.getCommandSenderName());
            }
            return;
        }

        say(sender, EnumChatFormatting.GOLD, SAY + "dynamic.header",
                player.getCommandSenderName(), Integer.valueOf(visible.size()));
        int shown = 0;
        for (LostTalesMapMarkerDefinition marker : visible) {
            if (shown >= 12) {
                say(sender, EnumChatFormatting.GRAY, SAY + "more",
                        Integer.valueOf(visible.size() - shown));
                break;
            }
            say(sender, EnumChatFormatting.GRAY, SAY + "entry.marked", describe(marker),
                    line(EnumChatFormatting.DARK_GRAY, SAY + "dynamic"));
            shown++;
        }
    }

    private void discoverMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        warnIfUnknownMarker(sender, player, markerId);
        if (LostTalesQuestManager.revealMapMarker(player, markerId)) {
            say(sender, EnumChatFormatting.GREEN, SAY + "discover.done",
                    formatMarkerId(markerId), player.getCommandSenderName());
        } else {
            say(sender, EnumChatFormatting.YELLOW, SAY + "discover.known",
                    player.getCommandSenderName(), formatMarkerId(markerId));
        }
    }

    private void forgetMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        if (LostTalesQuestManager.forgetMapMarker(player, markerId)) {
            say(sender, EnumChatFormatting.GREEN, SAY + "forget.done",
                    formatMarkerId(markerId), player.getCommandSenderName());
        } else {
            say(sender, EnumChatFormatting.YELLOW, SAY + "forget.unknown",
                    player.getCommandSenderName(), formatMarkerId(markerId));
        }
    }

    private void trackMarker(ICommandSender sender, EntityPlayerMP player, String markerId) {
        warnIfUnknownMarker(sender, player, markerId);
        if (LostTalesQuestManager.pinMapMarker(player, markerId)) {
            say(sender, EnumChatFormatting.GREEN, SAY + "track.done",
                    formatMarkerId(markerId), player.getCommandSenderName());
        } else {
            say(sender, EnumChatFormatting.YELLOW, SAY + "track.failed",
                    formatMarkerId(markerId));
        }
    }

    private void untrackMarker(ICommandSender sender, EntityPlayerMP player) {
        if (LostTalesQuestManager.unpinMapMarker(player)) {
            say(sender, EnumChatFormatting.GREEN, SAY + "untrack.done",
                    player.getCommandSenderName());
        } else {
            say(sender, EnumChatFormatting.YELLOW, SAY + "untrack.nothing",
                    player.getCommandSenderName());
        }
    }

    private void retryWaystoneGeneration(
            ICommandSender sender, String markerId) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer overworld = server == null
                ? null : server.worldServerForDimension(0);
        if (overworld == null) {
            say(sender, EnumChatFormatting.RED, SAY + "repository.unavailable");
            return;
        }
        LostTalesMapMarkerWorldData data;
        try {
            data = LostTalesMapMarkerStorage.get(overworld);
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "repository.unopened");
            return;
        }
        LostTalesMapMarkerRecord record = data.getRecord(markerId);
        if (record == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown", markerId);
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
            say(sender, EnumChatFormatting.YELLOW, SAY + "retry.not_waiting", markerId);
            return;
        }
        WorldServer world = server.worldServerForDimension(
                record.getDimensionId());
        if (world == null) {
            say(sender, EnumChatFormatting.RED, SAY + "retry.dimension",
                    Integer.valueOf(record.getDimensionId()));
            return;
        }
        int chunkX = floor(record.getX()) >> 4;
        int chunkZ = floor(record.getZ()) >> 4;
        if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
            say(sender, EnumChatFormatting.YELLOW, SAY + "retry.chunk");
            return;
        }
        LostTalesMapMarkerRecord retry =
                record.withGenerationState(
                        LostTalesWaystoneGenerationState.NOT_ATTEMPTED,
                        "operator_retry");
        data.saveRecord(retry);
        LostTalesWaystonePlacementResult result =
                LostTalesWaystonePlacementService.attempt(world, retry);
        LostTalesWaystonePlacementResult.Status status = result.getStatus();
        say(sender, status == LostTalesWaystonePlacementResult.Status.SUCCESS
                        ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW,
                SAY + "retry.result", markerId, words(statusKey(status)), result.getReason());
    }

    /** The lang key of the word a placement's status is named by. */
    private static String statusKey(LostTalesWaystonePlacementResult.Status status) {
        return status == LostTalesWaystonePlacementResult.Status.SUCCESS ? SAY + "status.success"
                : status == LostTalesWaystonePlacementResult.Status.DEFERRED
                        ? SAY + "status.deferred" : SAY + "status.blocked";
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
            say(sender, EnumChatFormatting.RED, SAY + "repository.unavailable");
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
                say(sender, EnumChatFormatting.RED, SAY + "unknown_bundled", markerId);
                return;
            }
            definitions = java.util.Collections.singleton(definition);
        }

        LostTalesMapMarkerWorldData data;
        try {
            data = LostTalesMapMarkerStorage.get(overworld);
        } catch (RuntimeException exception) {
            say(sender, EnumChatFormatting.RED, SAY + "repository.unopened");
            return;
        }
        if (data.isReadOnlyForNewerVersion()) {
            say(sender, EnumChatFormatting.RED, SAY + "repository.read_only",
                    Integer.valueOf(data.getUnsupportedDataVersion()));
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
        say(sender, blocked == 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW,
                SAY + "reseed.done", Integer.valueOf(reseeded), Integer.valueOf(linked),
                Integer.valueOf(placed), Integer.valueOf(deferred), Integer.valueOf(blocked));
        if (linked > 0) {
            say(sender, EnumChatFormatting.GRAY, SAY + "reseed.linked");
        }
        if (firstFailure.length() > 0) {
            say(sender, EnumChatFormatting.RED, SAY + "reseed.failure", firstFailure);
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
            say(sender, EnumChatFormatting.RED, PLAYER_REQUIRED);
            return null;
        } catch (Exception e) {
            String playerName = args.length > playerArgIndex ? args[playerArgIndex] : "";
            say(sender, EnumChatFormatting.RED, SAY + "no_player", playerName);
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
        say(sender, EnumChatFormatting.YELLOW, SAY + "unknown_warning", markerId,
                player.getCommandSenderName());
    }

    private Object formatMarkerId(String markerId) {
        return formatMarkerId(markerId, null);
    }

    /** A marker by its id and name; a dynamic one with where it stands. */
    private Object formatMarkerId(String markerId, Map<String, LostTalesMapMarkerDefinition> dynamicMarkers) {
        if (dynamicMarkers != null) {
            LostTalesMapMarkerDefinition marker = dynamicMarkers.get(markerId);
            if (marker != null) {
                return words(SAY + "marker.dynamic", marker.getId(),
                        LostTalesMapMarkerNames.component(marker.getId(), marker.getName(),
                        marker.getNamedAfter()),
                        Long.valueOf(Math.round(marker.getX())),
                        Long.valueOf(Math.round(marker.getY())),
                        Long.valueOf(Math.round(marker.getZ())));
            }
        }
        return LostTalesMapMarkerCatalog.getDisplayName(markerId);
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, getCommandUsage(sender));
        say(sender, EnumChatFormatting.GRAY, SAY + "examples");
        usage(sender, commandPrefix() + " known");
        usage(sender, commandPrefix() + " list <player>");
        usage(sender, commandPrefix() + " dynamic <player> [filter]");
        usage(sender, commandPrefix() + " discover losttales:quest_giver_nia <player>");
        usage(sender, commandPrefix() + " track losttales:quest_giver_nia <player>");
        usage(sender, commandPrefix() + " forget losttales:quest_giver_nia <player>");
        usage(sender, commandPrefix() + " retry losttales:marker_id");
        usage(sender, commandPrefix() + " reseed <markerId|all>");
    }

    private String commandPrefix() {
        return "/" + commandPath;
    }

    /** The markers, comma-separated, each as {@link #formatMarkerId} names it. */
    private IChatComponent joinFormatted(Collection<String> values, Map<String, LostTalesMapMarkerDefinition> dynamicMarkers) {
        IChatComponent joined = new ChatComponentText("");
        for (String value : values) {
            if (!joined.getSiblings().isEmpty()) {
                joined.appendSibling(new ChatComponentText(", "));
            }
            Object marker = formatMarkerId(value, dynamicMarkers);
            joined.appendSibling(marker instanceof IChatComponent ? (IChatComponent)marker
                    : new ChatComponentText(String.valueOf(marker)));
        }
        return joined;
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
