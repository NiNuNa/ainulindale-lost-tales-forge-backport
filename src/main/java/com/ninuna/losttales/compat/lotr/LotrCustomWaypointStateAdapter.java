package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import lotr.common.network.LOTRPacketHandler;
import lotr.common.world.map.LOTRCustomWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Isolates the character-owned subset of LOTR custom-waypoint data.
 *
 * <p>Received shared waypoints and the viewer's shared-waypoint preferences are
 * deliberately excluded. They belong to the account/fellowship relationship,
 * not to one roleplaying character.</p>
 */
public final class LotrCustomWaypointStateAdapter {

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static final String TAG_CUSTOM_WAYPOINTS = "CustomWaypoints";
    private static final String TAG_CUSTOM_USES = "CWPUses";
    private static final String TAG_NEXT_CUSTOM_ID = "NextCWPID";

    private static final String TAG_NAME = "Name";
    private static final String TAG_MAP_X = "XMap";
    private static final String TAG_MAP_Y = "YMap";
    private static final String TAG_X = "XCoord";
    private static final String TAG_Y = "YCoord";
    private static final String TAG_Z = "ZCoord";
    private static final String TAG_ID = "ID";
    private static final String TAG_SHARED_FELLOWSHIPS = "SharedFellowships";
    private static final String TAG_CUSTOM_ID = "CustomID";
    private static final String TAG_COUNT = "Count";

    private static final int FIRST_CUSTOM_ID = 20000;
    private static final int MAX_WAYPOINTS = 512;
    private static final int MAX_USE_COUNTS = 16384;
    private static final int MAX_SHARED_FELLOWSHIPS = 256;
    private static final int MAX_NAME_LENGTH = 256;
    private static final double MAX_ABSOLUTE_MAP_COORDINATE = 100000000.0D;
    private static final int MAX_ABSOLUTE_WORLD_COORDINATE = 30000000;

    private static final Set<String> ROOT_KEYS = setOf(
            TAG_CUSTOM_WAYPOINTS, TAG_CUSTOM_USES, TAG_NEXT_CUSTOM_ID);
    private static final Set<String> WAYPOINT_REQUIRED_KEYS = setOf(
            TAG_NAME, TAG_MAP_X, TAG_MAP_Y, TAG_X, TAG_Y, TAG_Z, TAG_ID);
    private static final Set<String> WAYPOINT_KEYS = setOf(
            TAG_NAME, TAG_MAP_X, TAG_MAP_Y, TAG_X, TAG_Y, TAG_Z, TAG_ID,
            TAG_SHARED_FELLOWSHIPS);
    private static final Set<String> USE_KEYS = setOf(TAG_CUSTOM_ID, TAG_COUNT);

    private final ConcurrentMap<UUID, PendingSynchronization> pendingSync =
            new ConcurrentHashMap<UUID, PendingSynchronization>();

    public NBTTagCompound capture(EntityPlayerMP player) {
        LOTRPlayerData data = requireLiveData(player);
        try {
            return normalize(extract(save(data)));
        } catch (LinkageError error) {
            throw incompatible("Unable to capture LOTR custom waypoints", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to capture LOTR custom waypoints", exception);
        }
    }

    public NBTTagCompound createDefault() {
        try {
            return normalize(extract(save(
                    new LOTRPlayerData(DETACHED_PLAYER_ID))));
        } catch (LinkageError error) {
            throw incompatible("Unable to create default LOTR custom waypoints", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to create default LOTR custom waypoints", exception);
        }
    }

    public void validate(NBTTagCompound state) {
        if (state == null) {
            throw new IllegalArgumentException("LOTR custom-waypoint state is missing");
        }
        validateKeys(state, ROOT_KEYS, ROOT_KEYS,
                "LOTR custom-waypoint root");
        NBTTagList waypoints = requireCompoundList(
                state, TAG_CUSTOM_WAYPOINTS, MAX_WAYPOINTS);
        NBTTagList uses = requireCompoundList(
                state, TAG_CUSTOM_USES, MAX_USE_COUNTS);
        requireInteger(state, TAG_NEXT_CUSTOM_ID);

        HashSet<Integer> waypointIds = new HashSet<Integer>();
        int greatestId = FIRST_CUSTOM_ID - 1;
        for (int index = 0; index < waypoints.tagCount(); index++) {
            NBTTagCompound waypoint = waypoints.getCompoundTagAt(index);
            validateKeys(waypoint, WAYPOINT_REQUIRED_KEYS, WAYPOINT_KEYS,
                    "LOTR custom waypoint");
            String name = requireString(waypoint, TAG_NAME, MAX_NAME_LENGTH);
            String validatedName = LOTRCustomWaypoint.validateCustomName(name);
            if (validatedName == null || !name.equals(validatedName)) {
                throw new IllegalArgumentException(
                        "LOTR custom waypoint name is blank or non-canonical");
            }
            requireFiniteDouble(waypoint, TAG_MAP_X,
                    MAX_ABSOLUTE_MAP_COORDINATE);
            requireFiniteDouble(waypoint, TAG_MAP_Y,
                    MAX_ABSOLUTE_MAP_COORDINATE);
            int x = requireInteger(waypoint, TAG_X);
            int y = requireInteger(waypoint, TAG_Y);
            int z = requireInteger(waypoint, TAG_Z);
            if (Math.abs((long)x) > MAX_ABSOLUTE_WORLD_COORDINATE
                    || Math.abs((long)z) > MAX_ABSOLUTE_WORLD_COORDINATE
                    || y < -1 || y > 4096) {
                throw new IllegalArgumentException(
                        "LOTR custom waypoint coordinates are outside supported bounds");
            }
            int id = requireCustomId(waypoint, TAG_ID);
            if (!waypointIds.add(Integer.valueOf(id))) {
                throw new IllegalArgumentException(
                        "LOTR custom waypoint ID is duplicated");
            }
            greatestId = Math.max(greatestId, id);
            validateFellowships(waypoint);
        }

        HashSet<Integer> useIds = new HashSet<Integer>();
        for (int index = 0; index < uses.tagCount(); index++) {
            NBTTagCompound use = uses.getCompoundTagAt(index);
            validateKeys(use, USE_KEYS, USE_KEYS,
                    "LOTR custom-waypoint use count");
            int id = requireCustomId(use, TAG_CUSTOM_ID);
            int count = requireInteger(use, TAG_COUNT);
            if (count < 0 || !useIds.add(Integer.valueOf(id))) {
                throw new IllegalArgumentException(
                        "LOTR custom-waypoint use count is negative or duplicated");
            }
            greatestId = Math.max(greatestId, id);
        }

        int nextId = state.getInteger(TAG_NEXT_CUSTOM_ID);
        if (nextId < FIRST_CUSTOM_ID || nextId <= greatestId) {
            throw new IllegalArgumentException(
                    "LOTR next custom-waypoint ID is invalid");
        }
        if (!normalize(state).equals(state)) {
            throw new IllegalArgumentException(
                    "LOTR custom-waypoint state is not in canonical order");
        }
    }

    /** Replaces only owned waypoint fields and leaves shared/account fields intact. */
    public void apply(EntityPlayerMP player, NBTTagCompound state) {
        validate(state);
        LOTRPlayerData data = requireLiveData(player);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR custom waypoints cannot be applied during fast travel");
        }
        try {
            List<LOTRCustomWaypoint> previous = copyWaypoints(
                    data.getCustomWaypoints());
            NBTTagCompound full = save(data);
            overlay(full, state);
            data.load(full);
            List<LOTRCustomWaypoint> current = copyWaypoints(
                    data.getCustomWaypoints());
            this.pendingSync.put(player.getUniqueID(),
                    new PendingSynchronization(previous, current));
            data.markDirty();
            LOTRLevelData.saveData(player.getUniqueID());
        } catch (LinkageError error) {
            throw incompatible("Unable to apply LOTR custom waypoints", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to apply LOTR custom waypoints", exception);
        }
    }

    /**
     * Clears source-only client entries and refreshes fellowship recipients.
     * The following full LOTR player-data synchronization creates the current
     * character's own waypoints on the switching client.
     */
    public void synchronize(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        PendingSynchronization pending = this.pendingSync.remove(
                player.getUniqueID());
        if (pending == null) {
            return;
        }
        for (LOTRCustomWaypoint waypoint : pending.previous) {
            try {
                LOTRPacketHandler.networkWrapper.sendTo(
                        waypoint.getClientDeletePacket(), player);
            } catch (RuntimeException exception) {
                warn(player.getUniqueID(), "delete stale client waypoint",
                        exception);
            }
        }
        synchronizeFellowshipRecipients(
                player.getUniqueID(), pending.previous, pending.current);
    }

    public void clearRuntimeState(UUID ownerId) {
        if (ownerId != null) {
            this.pendingSync.remove(ownerId);
        }
    }

    public void clearAllRuntimeState() {
        this.pendingSync.clear();
    }

    private static void synchronizeFellowshipRecipients(
            UUID ownerId,
            List<LOTRCustomWaypoint> previous,
            List<LOTRCustomWaypoint> current) {
        for (LOTRCustomWaypoint waypoint : previous) {
            LOTRCustomWaypoint shared = waypoint.createCopyOfShared(ownerId);
            for (UUID recipientId : shared.getPlayersInAllSharedFellowships()) {
                if (isOnline(recipientId)) {
                    try {
                        LOTRLevelData.getData(recipientId)
                                .removeSharedCustomWaypoint(shared);
                    } catch (RuntimeException exception) {
                        warn(ownerId, "remove stale fellowship waypoint",
                                exception);
                    }
                }
            }
        }

        for (LOTRCustomWaypoint waypoint : current) {
            LOTRCustomWaypoint shared = waypoint.createCopyOfShared(ownerId);
            for (UUID recipientId : shared.getPlayersInAllSharedFellowships()) {
                if (isOnline(recipientId)) {
                    try {
                        LOTRLevelData.getData(recipientId)
                                .addOrUpdateSharedCustomWaypoint(shared);
                    } catch (RuntimeException exception) {
                        warn(ownerId, "publish fellowship waypoint", exception);
                    }
                }
            }
        }

        LinkedHashSet<UUID> fellowshipIds = new LinkedHashSet<UUID>();
        collectFellowshipIds(previous, fellowshipIds);
        collectFellowshipIds(current, fellowshipIds);
        for (UUID fellowshipId : fellowshipIds) {
            LOTRFellowship fellowship =
                    LOTRFellowshipData.getActiveFellowship(fellowshipId);
            if (fellowship == null) {
                continue;
            }
            boolean sharesCurrent = false;
            for (LOTRCustomWaypoint waypoint : current) {
                if (waypoint.hasSharedFellowship(fellowshipId)) {
                    sharesCurrent = true;
                    break;
                }
            }
            fellowship.markIsWaypointSharer(ownerId, sharesCurrent);
        }
    }

    private static void collectFellowshipIds(
            List<LOTRCustomWaypoint> waypoints, Set<UUID> destination) {
        for (LOTRCustomWaypoint waypoint : waypoints) {
            destination.addAll(waypoint.getSharedFellowshipIDs());
        }
    }

    private static boolean isOnline(UUID playerId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return false;
        }
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP
                    && playerId.equals(((EntityPlayerMP)entry).getUniqueID())) {
                return true;
            }
        }
        return false;
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR custom waypoints require a connected server player");
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                throw new IllegalStateException("LOTR player data is unavailable");
            }
            return data;
        } catch (LinkageError error) {
            throw incompatible("LOTR player-data API is incompatible", error);
        }
    }

    private static NBTTagCompound save(LOTRPlayerData data) {
        NBTTagCompound full = new NBTTagCompound();
        data.save(full);
        return full;
    }

    private static NBTTagCompound extract(NBTTagCompound full) {
        NBTTagCompound state = new NBTTagCompound();
        for (String key : ROOT_KEYS) {
            if (!full.hasKey(key)) {
                throw new IllegalArgumentException(
                        "LOTR custom-waypoint data is missing " + key);
            }
            state.setTag(key, full.getTag(key).copy());
        }
        return state;
    }

    private static void overlay(NBTTagCompound full, NBTTagCompound state) {
        for (String key : ROOT_KEYS) {
            full.removeTag(key);
            full.setTag(key, state.getTag(key).copy());
        }
    }

    private static NBTTagCompound normalize(NBTTagCompound source) {
        NBTTagCompound normalized = (NBTTagCompound)source.copy();
        if (normalized.hasKey(TAG_CUSTOM_WAYPOINTS, Constants.NBT.TAG_LIST)) {
            ArrayList<NBTTagCompound> entries = copyCompounds(
                    normalized.getTagList(
                            TAG_CUSTOM_WAYPOINTS, Constants.NBT.TAG_COMPOUND));
            for (NBTTagCompound entry : entries) {
                sortStringList(entry, TAG_SHARED_FELLOWSHIPS);
            }
            sortByInteger(entries, TAG_ID);
            normalized.setTag(TAG_CUSTOM_WAYPOINTS, toList(entries));
        }
        if (normalized.hasKey(TAG_CUSTOM_USES, Constants.NBT.TAG_LIST)) {
            ArrayList<NBTTagCompound> entries = copyCompounds(
                    normalized.getTagList(
                            TAG_CUSTOM_USES, Constants.NBT.TAG_COMPOUND));
            sortByInteger(entries, TAG_CUSTOM_ID);
            normalized.setTag(TAG_CUSTOM_USES, toList(entries));
        }
        return normalized;
    }

    private static void sortStringList(NBTTagCompound root, String key) {
        if (!root.hasKey(key, Constants.NBT.TAG_LIST)) {
            return;
        }
        NBTTagList original = root.getTagList(key, Constants.NBT.TAG_STRING);
        ArrayList<String> values = new ArrayList<String>();
        for (int index = 0; index < original.tagCount(); index++) {
            values.add(original.getStringTagAt(index));
        }
        Collections.sort(values);
        NBTTagList sorted = new NBTTagList();
        for (String value : values) {
            sorted.appendTag(new NBTTagString(value));
        }
        root.setTag(key, sorted);
    }

    private static void sortByInteger(
            List<NBTTagCompound> entries, final String key) {
        Collections.sort(entries, new Comparator<NBTTagCompound>() {
            @Override
            public int compare(NBTTagCompound left, NBTTagCompound right) {
                int leftValue = left.getInteger(key);
                int rightValue = right.getInteger(key);
                return leftValue < rightValue ? -1
                        : leftValue == rightValue ? 0 : 1;
            }
        });
    }

    private static ArrayList<NBTTagCompound> copyCompounds(NBTTagList list) {
        ArrayList<NBTTagCompound> copy = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < list.tagCount(); index++) {
            copy.add((NBTTagCompound)list.getCompoundTagAt(index).copy());
        }
        return copy;
    }

    private static NBTTagList toList(List<NBTTagCompound> compounds) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound compound : compounds) {
            list.appendTag(compound);
        }
        return list;
    }

    private static List<LOTRCustomWaypoint> copyWaypoints(
            List<LOTRCustomWaypoint> source) {
        return Collections.unmodifiableList(
                new ArrayList<LOTRCustomWaypoint>(source));
    }

    private static NBTTagList requireCompoundList(
            NBTTagCompound root, String key, int maximum) {
        if (!root.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(key + " must be an NBT list");
        }
        NBTTagList list = (NBTTagList)root.getTag(key);
        if (list.tagCount() > maximum
                || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " is oversized or malformed");
        }
        return list;
    }

    private static void validateFellowships(NBTTagCompound waypoint) {
        if (!waypoint.hasKey(TAG_SHARED_FELLOWSHIPS)) {
            return;
        }
        if (!waypoint.hasKey(TAG_SHARED_FELLOWSHIPS, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                    "SharedFellowships must be an NBT list");
        }
        NBTTagList list = (NBTTagList)waypoint.getTag(TAG_SHARED_FELLOWSHIPS);
        if (list.tagCount() > MAX_SHARED_FELLOWSHIPS
                || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_STRING) {
            throw new IllegalArgumentException(
                    "SharedFellowships is oversized or malformed");
        }
        HashSet<UUID> unique = new HashSet<UUID>();
        for (int index = 0; index < list.tagCount(); index++) {
            UUID id;
            try {
                id = UUID.fromString(list.getStringTagAt(index));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "SharedFellowships contains an invalid UUID", exception);
            }
            if (!unique.add(id)) {
                throw new IllegalArgumentException(
                        "SharedFellowships contains a duplicate UUID");
            }
        }
    }

    private static void validateKeys(NBTTagCompound compound,
                                     Set<String> required,
                                     Set<String> allowed,
                                     String description) {
        Set<?> keys = compound.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)
                    || !allowed.contains((String)keyObject)) {
                throw new IllegalArgumentException(
                        description + " contains an unsupported field");
            }
        }
        for (String requiredKey : required) {
            if (!keys.contains(requiredKey)) {
                throw new IllegalArgumentException(
                        description + " is missing " + requiredKey);
            }
        }
    }

    private static String requireString(
            NBTTagCompound compound, String key, int maximumLength) {
        if (!compound.hasKey(key, Constants.NBT.TAG_STRING)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = compound.getString(key);
        if (value.length() == 0 || value.length() > maximumLength) {
            throw new IllegalArgumentException(key + " has an invalid length");
        }
        return value;
    }

    private static int requireInteger(NBTTagCompound compound, String key) {
        if (!compound.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return compound.getInteger(key);
    }

    private static int requireCustomId(NBTTagCompound compound, String key) {
        int id = requireInteger(compound, key);
        if (id < FIRST_CUSTOM_ID) {
            throw new IllegalArgumentException(key + " is not a custom-waypoint ID");
        }
        return id;
    }

    private static void requireFiniteDouble(
            NBTTagCompound compound, String key, double maximumAbsoluteValue) {
        if (!compound.hasKey(key, Constants.NBT.TAG_DOUBLE)) {
            throw new IllegalArgumentException(key + " must be a double");
        }
        double value = compound.getDouble(key);
        if (Double.isNaN(value) || Double.isInfinite(value)
                || Math.abs(value) > maximumAbsoluteValue) {
            throw new IllegalArgumentException(key + " is outside supported bounds");
        }
    }

    private static Set<String> setOf(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
    }

    private static void warn(UUID ownerId, String operation,
                             RuntimeException exception) {
        FMLLog.warning("[%s] Unable to %s for LOTR custom waypoints owned by %s: %s",
                LostTalesMetaData.MOD_ID, operation, ownerId,
                exception.toString());
    }

    private static IllegalStateException incompatible(
            String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    private static final class PendingSynchronization {
        private final List<LOTRCustomWaypoint> previous;
        private final List<LOTRCustomWaypoint> current;

        private PendingSynchronization(List<LOTRCustomWaypoint> previous,
                                       List<LOTRCustomWaypoint> current) {
            this.previous = previous;
            this.current = current;
        }
    }
}
