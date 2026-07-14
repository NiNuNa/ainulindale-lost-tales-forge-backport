package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRDimension;
import lotr.common.LOTRPlayerData;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Public-API adapter for character-owned visited LOTR waypoint regions. */
public final class LotrFastTravelRegionStateAdapter {

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("52c09bfb-5020-41ea-b633-6b3dc6787b7d");
    private static final String TAG_REGIONS = "UnlockedFTRegions";
    private static final String TAG_NAME = "Name";
    private static final int MAX_REGIONS = 512;
    private static final int MAX_NAME_LENGTH = 128;

    public NBTTagCompound capture(EntityPlayerMP player) {
        NBTTagCompound state = extract(save(requireLiveData(player)));
        includeCurrentBiomeRegion(player, state);
        return normalize(state);
    }

    public NBTTagCompound createDefault() {
        return normalize(extract(save(new LOTRPlayerData(DETACHED_PLAYER_ID))));
    }

    public void validate(NBTTagCompound state) {
        if (state == null || state.func_150296_c().size() != 1
                || !state.hasKey(TAG_REGIONS, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                    "LOTR fast-travel region state is incomplete");
        }
        NBTTagList list = (NBTTagList)state.getTag(TAG_REGIONS);
        if (list.tagCount() > MAX_REGIONS
                || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "LOTR fast-travel region list is malformed or oversized");
        }
        Set<String> unique = new HashSet<String>();
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            if (entry.func_150296_c().size() != 1
                    || !entry.hasKey(TAG_NAME, Constants.NBT.TAG_STRING)) {
                throw new IllegalArgumentException(
                        "LOTR fast-travel region entry is malformed");
            }
            String name = entry.getString(TAG_NAME);
            if (name.length() == 0 || name.length() > MAX_NAME_LENGTH
                    || LOTRWaypoint.regionForName(name) == null
                    || !unique.add(name)) {
                throw new IllegalArgumentException(
                        "LOTR fast-travel region is unknown or duplicated");
            }
        }
        if (!normalize(state).equals(state)) {
            throw new IllegalArgumentException(
                    "LOTR fast-travel regions are not in canonical order");
        }
    }

    public void apply(EntityPlayerMP player, NBTTagCompound state) {
        validate(state);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR fast-travel regions cannot be applied during fast travel");
        }
        LOTRPlayerData data = requireLiveData(player);
        NBTTagCompound full = save(data);
        full.setTag(TAG_REGIONS, state.getTag(TAG_REGIONS).copy());
        data.load(full);
        data.markDirty();
        LOTRLevelData.saveData(player.getUniqueID());
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR region state requires a connected server player");
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
        NBTTagCompound state = new NBTTagCompound();
        state.setTag(TAG_REGIONS, full.hasKey(
                TAG_REGIONS, Constants.NBT.TAG_LIST)
                ? full.getTag(TAG_REGIONS).copy() : new NBTTagList());
        return state;
    }

    private static void includeCurrentBiomeRegion(
            EntityPlayerMP player, NBTTagCompound state) {
        if (player == null || player.worldObj == null || state == null) {
            return;
        }
        if (player.dimension != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        BiomeGenBase biome = player.worldObj.getBiomeGenForCoords(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posZ));
        LOTRWaypoint.Region region = biome instanceof LOTRBiome
                ? ((LOTRBiome)biome).getBiomeWaypoints() : null;
        if (region == null) {
            return;
        }
        NBTTagList list = state.getTagList(
                TAG_REGIONS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            if (region.name().equals(
                    list.getCompoundTagAt(index).getString(TAG_NAME))) {
                return;
            }
        }
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_NAME, region.name());
        list.appendTag(entry);
        state.setTag(TAG_REGIONS, list);
    }

    private static NBTTagCompound normalize(NBTTagCompound source) {
        NBTTagList original = source.getTagList(
                TAG_REGIONS, Constants.NBT.TAG_COMPOUND);
        ArrayList<NBTTagCompound> entries =
                new ArrayList<NBTTagCompound>(original.tagCount());
        for (int index = 0; index < original.tagCount(); index++) {
            entries.add((NBTTagCompound)
                    original.getCompoundTagAt(index).copy());
        }
        Collections.sort(entries, new Comparator<NBTTagCompound>() {
            @Override
            public int compare(NBTTagCompound left, NBTTagCompound right) {
                return left.getString(TAG_NAME).compareTo(
                        right.getString(TAG_NAME));
            }
        });
        NBTTagList sorted = new NBTTagList();
        for (NBTTagCompound entry : entries) {
            sorted.appendTag(entry);
        }
        NBTTagCompound normalized = new NBTTagCompound();
        normalized.setTag(TAG_REGIONS, sorted);
        return normalized;
    }
}
