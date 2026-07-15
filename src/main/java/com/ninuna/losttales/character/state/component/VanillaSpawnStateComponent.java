package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** All per-dimension bed and forced respawn locations owned by a character. */
public final class VanillaSpawnStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_spawns";

    private static final int VERSION = 1;
    private static final int MAX_DIMENSION_SPAWNS = 256;
    private static final int MAX_HORIZONTAL_COORDINATE = 30000000;
    private static final int MIN_Y = -4096;
    private static final int MAX_Y = 4096;

    private static final String TAG_VERSION = "Version";
    private static final String TAG_SPAWNS = "Spawns";
    private static final String TAG_DIMENSION = "Dim";
    private static final String TAG_X = "SpawnX";
    private static final String TAG_Y = "SpawnY";
    private static final String TAG_Z = "SpawnZ";
    private static final String TAG_FORCED = "SpawnForced";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Character spawn state requires a connected server player");
        }
        NBTTagCompound playerNbt = new NBTTagCompound();
        try {
            player.writeEntityToNBT(playerNbt);
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "Vanilla player spawn state could not be serialized", exception);
        }
        NBTTagCompound state = fromVanillaPlayerNbt(playerNbt);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_SPAWNS, new NBTTagList());
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || state.getInteger(TAG_VERSION) != VERSION
                || !state.hasKey(TAG_SPAWNS, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Spawn component version or list is missing");
        }
        Set<?> keys = state.func_150296_c();
        if (keys.size() != 2 || !keys.contains(TAG_VERSION)
                || !keys.contains(TAG_SPAWNS)) {
            throw new CharacterStateValidationException(
                    "Spawn component contains unsupported fields");
        }
        NBTBase rawSpawns = state.getTag(TAG_SPAWNS);
        if (!(rawSpawns instanceof NBTTagList)) {
            throw new CharacterStateValidationException(
                    "Spawn list has an invalid tag type");
        }
        NBTTagList spawns = (NBTTagList) rawSpawns;
        if (spawns.tagCount() > 0
                && spawns.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Spawn list has an invalid element type");
        }
        if (spawns.tagCount() > MAX_DIMENSION_SPAWNS) {
            throw new CharacterStateValidationException(
                    "Spawn list contains too many dimensions");
        }
        HashSet<Integer> dimensions = new HashSet<Integer>();
        int previousDimension = Integer.MIN_VALUE;
        for (int index = 0; index < spawns.tagCount(); index++) {
            NBTTagCompound entry = spawns.getCompoundTagAt(index);
            validateEntry(entry, index);
            int dimension = entry.getInteger(TAG_DIMENSION);
            if (!dimensions.add(Integer.valueOf(dimension))) {
                throw new CharacterStateValidationException(
                        "Spawn list contains duplicate dimension " + dimension);
            }
            if (index > 0 && dimension <= previousDimension) {
                throw new CharacterStateValidationException(
                        "Spawn list is not in canonical dimension order");
            }
            previousDimension = dimension;
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Character spawn state requires a connected server player");
        }
        validate(state);

        // Enumerate the live dimensions before mutating them. Forge exposes
        // individual entries publicly, but does not expose the backing map.
        NBTTagCompound live = capture(player);
        NBTTagList liveSpawns = live.getTagList(
                TAG_SPAWNS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < liveSpawns.tagCount(); index++) {
            int dimension = liveSpawns.getCompoundTagAt(index)
                    .getInteger(TAG_DIMENSION);
            player.setSpawnChunk(null, false, dimension);
        }

        NBTTagList targetSpawns = state.getTagList(
                TAG_SPAWNS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < targetSpawns.tagCount(); index++) {
            NBTTagCompound entry = targetSpawns.getCompoundTagAt(index);
            player.setSpawnChunk(new ChunkCoordinates(
                            entry.getInteger(TAG_X),
                            entry.getInteger(TAG_Y),
                            entry.getInteger(TAG_Z)),
                    entry.getBoolean(TAG_FORCED),
                    entry.getInteger(TAG_DIMENSION));
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        // Vanilla sends bed validity/respawn information when it is consumed.
    }

    /** Converts only Forge's public player-spawn NBT fields to canonical state. */
    NBTTagCompound fromVanillaPlayerNbt(NBTTagCompound playerNbt)
            throws CharacterStateValidationException {
        if (playerNbt == null) {
            throw new CharacterStateValidationException(
                    "Vanilla player NBT is missing");
        }
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        boolean hasAnyOverworldSpawnField = playerNbt.hasKey(TAG_X)
                || playerNbt.hasKey(TAG_Y) || playerNbt.hasKey(TAG_Z)
                || playerNbt.hasKey(TAG_FORCED);
        if (hasAnyOverworldSpawnField) {
            if (!playerNbt.hasKey(TAG_X, Constants.NBT.TAG_INT)
                    || !playerNbt.hasKey(TAG_Y, Constants.NBT.TAG_INT)
                    || !playerNbt.hasKey(TAG_Z, Constants.NBT.TAG_INT)
                    || !playerNbt.hasKey(TAG_FORCED, Constants.NBT.TAG_BYTE)) {
                throw new CharacterStateValidationException(
                        "Vanilla overworld spawn is incomplete");
            }
            requireCanonicalForcedFlag(playerNbt,
                    "Vanilla overworld spawn");
            entries.add(entry(0,
                    playerNbt.getInteger(TAG_X),
                    playerNbt.getInteger(TAG_Y),
                    playerNbt.getInteger(TAG_Z),
                    playerNbt.getBoolean(TAG_FORCED)));
        }

        if (playerNbt.hasKey(TAG_SPAWNS)
                && !playerNbt.hasKey(TAG_SPAWNS, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Vanilla dimension-spawn list is malformed");
        }
        NBTTagList vanillaSpawns = playerNbt.hasKey(TAG_SPAWNS)
                ? (NBTTagList) playerNbt.getTag(TAG_SPAWNS)
                : new NBTTagList();
        if (vanillaSpawns.tagCount() > 0
                && vanillaSpawns.func_150303_d()
                != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Vanilla dimension-spawn list has an invalid element type");
        }
        if (vanillaSpawns.tagCount() > MAX_DIMENSION_SPAWNS) {
            throw new CharacterStateValidationException(
                    "Vanilla dimension-spawn list is oversized");
        }
        for (int index = 0; index < vanillaSpawns.tagCount(); index++) {
            NBTTagCompound vanilla = vanillaSpawns.getCompoundTagAt(index);
            if (!vanilla.hasKey(TAG_DIMENSION, Constants.NBT.TAG_INT)
                    || !vanilla.hasKey(TAG_X, Constants.NBT.TAG_INT)
                    || !vanilla.hasKey(TAG_Y, Constants.NBT.TAG_INT)
                    || !vanilla.hasKey(TAG_Z, Constants.NBT.TAG_INT)
                    || !vanilla.hasKey(TAG_FORCED, Constants.NBT.TAG_BYTE)) {
                throw new CharacterStateValidationException(
                        "Vanilla dimension spawn " + index + " is incomplete");
            }
            requireCanonicalForcedFlag(vanilla,
                    "Vanilla dimension spawn " + index);
            entries.add(entry(
                    vanilla.getInteger(TAG_DIMENSION),
                    vanilla.getInteger(TAG_X),
                    vanilla.getInteger(TAG_Y),
                    vanilla.getInteger(TAG_Z),
                    vanilla.getBoolean(TAG_FORCED)));
        }

        Collections.sort(entries, new Comparator<NBTTagCompound>() {
            @Override
            public int compare(NBTTagCompound left, NBTTagCompound right) {
                int leftDimension = left.getInteger(TAG_DIMENSION);
                int rightDimension = right.getInteger(TAG_DIMENSION);
                return leftDimension < rightDimension ? -1
                        : leftDimension == rightDimension ? 0 : 1;
            }
        });
        NBTTagList spawns = new NBTTagList();
        for (NBTTagCompound spawn : entries) {
            spawns.appendTag(spawn);
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_SPAWNS, spawns);
        validate(state);
        return state;
    }

    private static NBTTagCompound entry(int dimension, int x, int y, int z,
                                        boolean forced) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(TAG_DIMENSION, dimension);
        entry.setInteger(TAG_X, x);
        entry.setInteger(TAG_Y, y);
        entry.setInteger(TAG_Z, z);
        entry.setBoolean(TAG_FORCED, forced);
        return entry;
    }

    private static void validateEntry(NBTTagCompound entry, int index)
            throws CharacterStateValidationException {
        Set<?> keys = entry.func_150296_c();
        if (keys.size() != 5
                || !entry.hasKey(TAG_DIMENSION, Constants.NBT.TAG_INT)
                || !entry.hasKey(TAG_X, Constants.NBT.TAG_INT)
                || !entry.hasKey(TAG_Y, Constants.NBT.TAG_INT)
                || !entry.hasKey(TAG_Z, Constants.NBT.TAG_INT)
                || !entry.hasKey(TAG_FORCED, Constants.NBT.TAG_BYTE)) {
            throw new CharacterStateValidationException(
                    "Spawn entry " + index + " is incomplete or unsupported");
        }
        int x = entry.getInteger(TAG_X);
        int y = entry.getInteger(TAG_Y);
        int z = entry.getInteger(TAG_Z);
        if (Math.abs((long) x) > MAX_HORIZONTAL_COORDINATE
                || Math.abs((long) z) > MAX_HORIZONTAL_COORDINATE
                || y < MIN_Y || y > MAX_Y) {
            throw new CharacterStateValidationException(
                    "Spawn entry " + index + " is outside the safe coordinate range");
        }
        byte forced = entry.getByte(TAG_FORCED);
        if (forced != 0 && forced != 1) {
            throw new CharacterStateValidationException(
                    "Spawn entry " + index + " has a non-canonical forced flag");
        }
    }

    private static void requireCanonicalForcedFlag(
            NBTTagCompound entry, String context)
            throws CharacterStateValidationException {
        byte forced = entry.getByte(TAG_FORCED);
        if (forced != 0 && forced != 1) {
            throw new CharacterStateValidationException(
                    context + " has a non-canonical forced flag");
        }
    }
}
