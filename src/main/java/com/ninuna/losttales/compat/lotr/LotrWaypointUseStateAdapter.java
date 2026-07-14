package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Public-API adapter for per-character native LOTR waypoint use counts. */
public final class LotrWaypointUseStateAdapter {

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    private static final String TAG_USES = "WPUses";
    private static final String TAG_WAYPOINT = "WPName";
    private static final String TAG_COUNT = "Count";
    private static final int MAX_USES = 1024;
    private static final int MAX_NAME_LENGTH = 128;

    public NBTTagCompound capture(EntityPlayerMP player) {
        return normalize(extract(save(requireLiveData(player))));
    }

    public NBTTagCompound createDefault() {
        return normalize(extract(save(
                new LOTRPlayerData(DETACHED_PLAYER_ID))));
    }

    public void validate(NBTTagCompound state) {
        if (state == null || state.func_150296_c().size() != 1
                || !state.hasKey(TAG_USES, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                    "LOTR waypoint-use state is incomplete or unsupported");
        }
        NBTTagList uses = (NBTTagList)state.getTag(TAG_USES);
        if (uses.tagCount() > MAX_USES || uses.tagCount() > 0
                && uses.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "LOTR waypoint-use state is oversized or malformed");
        }
        HashSet<String> unique = new HashSet<String>();
        for (int index = 0; index < uses.tagCount(); index++) {
            NBTTagCompound entry = uses.getCompoundTagAt(index);
            Set<?> keys = entry.func_150296_c();
            if (keys.size() != 2 || !keys.contains(TAG_WAYPOINT)
                    || !keys.contains(TAG_COUNT)
                    || !entry.hasKey(TAG_WAYPOINT, Constants.NBT.TAG_STRING)
                    || !entry.hasKey(TAG_COUNT, Constants.NBT.TAG_INT)) {
                throw new IllegalArgumentException(
                        "LOTR waypoint-use entry is incomplete or unsupported");
            }
            String waypointName = entry.getString(TAG_WAYPOINT);
            if (waypointName.length() == 0
                    || waypointName.length() > MAX_NAME_LENGTH
                    || LOTRWaypoint.waypointForName(waypointName) == null
                    || !unique.add(waypointName)
                    || entry.getInteger(TAG_COUNT) < 0) {
                throw new IllegalArgumentException(
                        "LOTR waypoint-use entry is invalid or duplicated");
            }
        }
        if (!normalize(state).equals(state)) {
            throw new IllegalArgumentException(
                    "LOTR waypoint-use state is not in canonical order");
        }
    }

    public void apply(EntityPlayerMP player, NBTTagCompound state) {
        validate(state);
        LOTRPlayerData data = requireLiveData(player);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR waypoint uses cannot be applied during fast travel");
        }
        NBTTagCompound full = save(data);
        full.removeTag(TAG_USES);
        full.setTag(TAG_USES, state.getTag(TAG_USES).copy());
        data.load(full);
        data.markDirty();
        LOTRLevelData.saveData(player.getUniqueID());
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR waypoint uses require a connected server player");
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data == null) {
            throw new IllegalStateException("LOTR player data is unavailable");
        }
        return data;
    }

    private static NBTTagCompound save(LOTRPlayerData data) {
        NBTTagCompound full = new NBTTagCompound();
        data.save(full);
        return full;
    }

    private static NBTTagCompound extract(NBTTagCompound full) {
        if (!full.hasKey(TAG_USES, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                    "LOTR player data is missing waypoint-use counts");
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setTag(TAG_USES, full.getTag(TAG_USES).copy());
        return state;
    }

    private static NBTTagCompound normalize(NBTTagCompound source) {
        NBTTagCompound normalized = (NBTTagCompound)source.copy();
        if (!normalized.hasKey(TAG_USES, Constants.NBT.TAG_LIST)) {
            return normalized;
        }
        NBTTagList original = normalized.getTagList(
                TAG_USES, Constants.NBT.TAG_COMPOUND);
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < original.tagCount(); index++) {
            entries.add((NBTTagCompound)original.getCompoundTagAt(index).copy());
        }
        Collections.sort(entries, new Comparator<NBTTagCompound>() {
            @Override
            public int compare(NBTTagCompound left, NBTTagCompound right) {
                return left.getString(TAG_WAYPOINT).compareTo(
                        right.getString(TAG_WAYPOINT));
            }
        });
        NBTTagList sorted = new NBTTagList();
        for (NBTTagCompound entry : entries) {
            sorted.appendTag(entry);
        }
        normalized.setTag(TAG_USES, sorted);
        return normalized;
    }
}
