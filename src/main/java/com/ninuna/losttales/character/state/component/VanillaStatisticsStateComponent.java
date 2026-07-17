package com.ninuna.losttales.character.state.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.state.compat.VanillaStatisticsAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.IJsonSerializable;
import net.minecraft.util.TupleIntJsonSerializable;
import net.minecraftforge.common.util.Constants;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Complete server-side vanilla statistics and achievement state. */
public final class VanillaStatisticsStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_statistics";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_STAT_ID = "StatId";
    private static final String TAG_VALUE = "Value";
    private static final String TAG_PROGRESS = "Progress";

    private static final int MAX_ENTRIES = 32768;
    private static final int MAX_STAT_ID_CHARACTERS = 1024;
    private static final int PROGRESS_CHUNK_CHARACTERS = 12000;
    private static final int MAX_PROGRESS_CHUNKS_PER_STAT = 128;
    private static final int MAX_PROGRESS_CHARACTERS_PER_STAT = 1000000;
    private static final int MAX_TOTAL_PROGRESS_CHARACTERS = 4000000;

    /** Source-only keys that must be explicitly reset on the client after apply. */
    private final ConcurrentMap<UUID, Set<StatBase>> pendingClientResets =
            new ConcurrentHashMap<UUID, Set<StatBase>>();

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
        StatisticsFile statistics = requireStatistics(player);
        Map<StatBase, TupleIntJsonSerializable> live =
                VanillaStatisticsAccess.getStatisticsMap(statistics);
        if (live.size() > MAX_ENTRIES) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics contain too many entries: " + live.size());
        }

        ArrayList<EncodedStatistic> entries = new ArrayList<EncodedStatistic>();
        for (Map.Entry<StatBase, TupleIntJsonSerializable> entry : live.entrySet()) {
            entries.add(encode(entry.getKey(), entry.getValue()));
        }
        Collections.sort(entries, new Comparator<EncodedStatistic>() {
            @Override
            public int compare(EncodedStatistic left, EncodedStatistic right) {
                return left.statId.compareTo(right.statId);
            }
        });

        NBTTagList list = new NBTTagList();
        for (EncodedStatistic entry : entries) {
            list.appendTag(entry.toNbt());
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ENTRIES, list);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ENTRIES, new NBTTagList());
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        decode(state);
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        StatisticsFile statistics = requireStatistics(player);
        LinkedHashMap<StatBase, TupleIntJsonSerializable> replacement = decode(state);
        Map<StatBase, TupleIntJsonSerializable> live =
                VanillaStatisticsAccess.getStatisticsMap(statistics);

        HashSet<StatBase> previous = new HashSet<StatBase>(live.keySet());
        live.clear();
        live.putAll(replacement);
        rememberClientResets(player.getUniqueID(), previous);

        // Remove source-character dirty entries, then mark every target entry dirty.
        statistics.func_150878_c();
        statistics.func_150877_d();
        statistics.func_150883_b();
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null
                || player.getUniqueID() == null) {
            return;
        }
        UUID ownerId = player.getUniqueID();
        Set<StatBase> reset = this.pendingClientResets.get(ownerId);
        if (reset == null) {
            // Generic inventory/vitals synchronization must not consume the
            // vanilla statistics dirty set. A pending marker is created only
            // after this component has replaced the authoritative live map.
            return;
        }
        try {
            StatisticsFile statistics = requireStatistics(player);
            Map<StatBase, TupleIntJsonSerializable> live =
                    VanillaStatisticsAccess.getStatisticsMap(statistics);
            HashMap<StatBase, Integer> packetValues = new HashMap<StatBase, Integer>();

            for (StatBase stat : reset) {
                if (stat != null) {
                    packetValues.put(stat, Integer.valueOf(0));
                }
            }
            for (Map.Entry<StatBase, TupleIntJsonSerializable> entry : live.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    packetValues.put(entry.getKey(),
                            Integer.valueOf(entry.getValue().getIntegerValue()));
                }
            }

            player.playerNetServerHandler.sendPacket(
                    new S37PacketStatistics(packetValues));
            // apply() saved the replacement before creating this pending marker.
            // It is now safe to consume those target dirty entries.
            statistics.func_150878_c();
            this.pendingClientResets.remove(ownerId, reset);
        } catch (CharacterStateValidationException exception) {
            throw new IllegalStateException(
                    "Unable to synchronize vanilla statistics", exception);
        }
    }

    public void clearRuntimeState(UUID ownerId) {
        if (ownerId != null) {
            this.pendingClientResets.remove(ownerId);
        }
    }

    public void clearAllRuntimeState() {
        this.pendingClientResets.clear();
    }

    private static LinkedHashMap<StatBase, TupleIntJsonSerializable> decode(
            NBTTagCompound state) throws CharacterStateValidationException {
        requireVersion(state);
        if (!state.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics entry list is missing");
        }
        NBTBase rawEntries = state.getTag(TAG_ENTRIES);
        if (!(rawEntries instanceof NBTTagList)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics entry list has an invalid NBT type");
        }
        NBTTagList entries = (NBTTagList) rawEntries;
        if (entries.tagCount() > 0
                && entries.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics entry list has an invalid element type");
        }
        if (entries.tagCount() > MAX_ENTRIES) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics contain too many entries: "
                            + entries.tagCount());
        }

        LinkedHashMap<StatBase, TupleIntJsonSerializable> decoded =
                new LinkedHashMap<StatBase, TupleIntJsonSerializable>();
        HashSet<String> ids = new HashSet<String>();
        long totalProgressCharacters = 0L;
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            if (!entry.hasKey(TAG_STAT_ID, Constants.NBT.TAG_STRING)
                    || !entry.hasKey(TAG_VALUE, Constants.NBT.TAG_INT)) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic entry " + index + " is incomplete");
            }
            String statId = entry.getString(TAG_STAT_ID);
            if (statId.length() == 0 || statId.length() > MAX_STAT_ID_CHARACTERS
                    || !ids.add(statId)) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic identifier is invalid or duplicated: " + statId);
            }
            StatBase stat = StatList.func_151177_a(statId);
            if (stat == null) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic is not registered on this server: " + statId);
            }
            int value = entry.getInteger(TAG_VALUE);
            if (value < 0) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic has a negative value: " + statId);
            }

            TupleIntJsonSerializable tuple = new TupleIntJsonSerializable();
            tuple.setIntegerValue(value);
            if (entry.hasKey(TAG_PROGRESS)) {
                String progressJson = readChunkedString(entry, TAG_PROGRESS);
                totalProgressCharacters += (long) progressJson.length();
                if (totalProgressCharacters > MAX_TOTAL_PROGRESS_CHARACTERS) {
                    throw new CharacterStateValidationException(
                            "Vanilla statistic progress exceeds the expanded-size limit");
                }
                tuple.setJsonSerializableValue(
                        decodeProgress(stat, progressJson));
            }
            decoded.put(stat, tuple);
        }
        return decoded;
    }

    private static EncodedStatistic encode(
            StatBase stat, TupleIntJsonSerializable tuple)
            throws CharacterStateValidationException {
        if (stat == null || tuple == null || stat.statId == null
                || stat.statId.length() == 0
                || stat.statId.length() > MAX_STAT_ID_CHARACTERS) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics contain an invalid entry");
        }
        int value = tuple.getIntegerValue();
        if (value < 0) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic has a negative value: " + stat.statId);
        }

        String progress = null;
        IJsonSerializable serializable = tuple.getJsonSerializableValue();
        if (serializable != null) {
            Class<?> progressClass = stat.func_150954_l();
            if (progressClass == null || !progressClass.isInstance(serializable)) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress type is incompatible: " + stat.statId);
            }
            try {
                JsonElement element = serializable.getSerializableElement();
                if (element == null) {
                    throw new CharacterStateValidationException(
                            "Vanilla statistic progress is missing: " + stat.statId);
                }
                progress = element.toString();
            } catch (CharacterStateValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress could not be serialized: "
                                + stat.statId, exception);
            }
            if (progress.length() > MAX_PROGRESS_CHARACTERS_PER_STAT) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress is too large: " + stat.statId);
            }
        }
        return new EncodedStatistic(stat.statId, value, progress);
    }

    private static IJsonSerializable decodeProgress(StatBase stat, String json)
            throws CharacterStateValidationException {
        if (json == null || json.length() == 0
                || json.length() > MAX_PROGRESS_CHARACTERS_PER_STAT) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress is empty or too large: " + stat.statId);
        }
        Class<?> progressClass = stat.func_150954_l();
        if (progressClass == null
                || !IJsonSerializable.class.isAssignableFrom(progressClass)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic does not support progress: " + stat.statId);
        }
        try {
            Constructor<?> constructor = progressClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            IJsonSerializable progress = (IJsonSerializable) constructor.newInstance();
            JsonElement element = new JsonParser().parse(json);
            progress.func_152753_a(element);
            JsonElement canonical = progress.getSerializableElement();
            if (canonical == null) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress decoded to no data: " + stat.statId);
            }
            return progress;
        } catch (CharacterStateValidationException exception) {
            throw exception;
        } catch (StackOverflowError error) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress is nested too deeply: "
                            + stat.statId, error);
        } catch (Exception exception) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress could not be decoded: "
                            + stat.statId, exception);
        }
    }

    private void rememberClientResets(UUID ownerId, Set<StatBase> previous) {
        if (ownerId == null) {
            return;
        }
        Set<StatBase> existing = this.pendingClientResets.get(ownerId);
        HashSet<StatBase> combined = existing == null
                ? new HashSet<StatBase>() : new HashSet<StatBase>(existing);
        if (previous != null) {
            combined.addAll(previous);
        }
        // An empty set is still a required synchronization marker when the
        // source map was empty and the target map contains new statistics.
        this.pendingClientResets.put(ownerId, combined);
    }

    private static StatisticsFile requireStatistics(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics require a connected server player");
        }
        if (!VanillaStatisticsAccess.isAvailable()) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics are incompatible with this server build");
        }
        StatisticsFile statistics = player.func_147099_x();
        if (statistics == null) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics manager is unavailable");
        }
        return statistics;
    }

    private static void requireVersion(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported vanilla statistics component version " + version);
        }
    }

    private static void writeChunkedString(
            NBTTagCompound parent, String key, String value)
            throws CharacterStateValidationException {
        if (value == null) {
            return;
        }
        if (value.length() == 0 || value.length() > MAX_PROGRESS_CHARACTERS_PER_STAT) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress is empty or too large");
        }
        NBTTagList chunks = new NBTTagList();
        for (int offset = 0; offset < value.length(); offset += PROGRESS_CHUNK_CHARACTERS) {
            int end = Math.min(value.length(), offset + PROGRESS_CHUNK_CHARACTERS);
            chunks.appendTag(new NBTTagString(value.substring(offset, end)));
        }
        if (chunks.tagCount() > MAX_PROGRESS_CHUNKS_PER_STAT) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress contains too many chunks");
        }
        parent.setTag(key, chunks);
    }

    private static String readChunkedString(NBTTagCompound parent, String key)
            throws CharacterStateValidationException {
        if (!parent.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress has an invalid NBT type");
        }
        NBTBase rawChunks = parent.getTag(key);
        if (!(rawChunks instanceof NBTTagList)) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress has an invalid NBT type");
        }
        NBTTagList chunks = (NBTTagList) rawChunks;
        if (chunks.tagCount() == 0
                || chunks.func_150303_d() != Constants.NBT.TAG_STRING
                || chunks.tagCount() > MAX_PROGRESS_CHUNKS_PER_STAT) {
            throw new CharacterStateValidationException(
                    "Vanilla statistic progress chunks are invalid");
        }
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < chunks.tagCount(); index++) {
            String chunk = chunks.getStringTagAt(index);
            if (chunk.length() == 0 || chunk.length() > PROGRESS_CHUNK_CHARACTERS) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress chunk is invalid");
            }
            value.append(chunk);
            if (value.length() > MAX_PROGRESS_CHARACTERS_PER_STAT) {
                throw new CharacterStateValidationException(
                        "Vanilla statistic progress exceeds its size limit");
            }
        }
        return value.toString();
    }

    private static final class EncodedStatistic {
        private final String statId;
        private final int value;
        private final String progress;

        private EncodedStatistic(String statId, int value, String progress) {
            this.statId = statId;
            this.value = value;
            this.progress = progress;
        }

        private NBTTagCompound toNbt() throws CharacterStateValidationException {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(TAG_STAT_ID, this.statId);
            tag.setInteger(TAG_VALUE, this.value);
            if (this.progress != null) {
                writeChunkedString(tag, TAG_PROGRESS, this.progress);
            }
            return tag;
        }
    }
}
