package com.ninuna.losttales.progression;

import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * A character's progression as NBT: the player's own save and the
 * character's snapshot both hold this compound. Strict: a compound of
 * another version, with a key it does not write, a missing key or a
 * value out of bounds is refused whole, never mended.
 */
public final class ProgressionStateNbt {
    public static final int VERSION = 1;

    static final String TAG_VERSION = "Version";
    static final String TAG_SKILLS = "Skills";
    static final String TAG_CHARACTER_LEVEL = "CharacterLevel";
    static final String TAG_CHARACTER_INTO = "CharacterInto";
    static final String TAG_PERKS = "Perks";
    static final String TAG_DEEDS = "Deeds";
    static final String TAG_DEED_COUNTERS = "DeedCounters";
    static final String TAG_TITLE = "Title";
    static final String TAG_RESETS = "Resets";
    static final String TAG_ID = "Id";
    static final String TAG_SKILL = "Skill";
    static final String TAG_PERK = "Perk";
    static final String TAG_LEVEL = "Level";
    static final String TAG_INTO = "Into";
    static final String TAG_RANK = "Rank";
    static final String TAG_COUNT = "Count";

    private ProgressionStateNbt() {}

    public static NBTTagCompound write(ProgressionState state) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_VERSION, VERSION);
        NBTTagList skills = new NBTTagList();
        for (Map.Entry<String, SkillStanding> skill : state.skills().entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_ID, skill.getKey());
            entry.setInteger(TAG_LEVEL, skill.getValue().getLevel());
            entry.setDouble(TAG_INTO, skill.getValue().getInto());
            skills.appendTag(entry);
        }
        tag.setTag(TAG_SKILLS, skills);
        tag.setInteger(TAG_CHARACTER_LEVEL, state.getCharacterLevel());
        tag.setLong(TAG_CHARACTER_INTO, state.getCharacterInto());
        NBTTagList perks = new NBTTagList();
        for (Map.Entry<String, Integer> perk : state.perks().entrySet()) {
            int slash = perk.getKey().indexOf('/');
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_SKILL, perk.getKey().substring(0, slash));
            entry.setString(TAG_PERK, perk.getKey().substring(slash + 1));
            entry.setInteger(TAG_RANK, perk.getValue().intValue());
            perks.appendTag(entry);
        }
        tag.setTag(TAG_PERKS, perks);
        NBTTagList deeds = new NBTTagList();
        for (String deed : state.deeds()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_ID, deed);
            deeds.appendTag(entry);
        }
        tag.setTag(TAG_DEEDS, deeds);
        NBTTagList counters = new NBTTagList();
        for (Map.Entry<String, Long> counter : state.deedCounters().entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_ID, counter.getKey());
            entry.setLong(TAG_COUNT, counter.getValue().longValue());
            counters.appendTag(entry);
        }
        tag.setTag(TAG_DEED_COUNTERS, counters);
        tag.setString(TAG_TITLE, state.getTitle());
        tag.setInteger(TAG_RESETS, state.getResets());
        return tag;
    }

    /** The state a compound holds; throws {@link IllegalArgumentException} for anything else. */
    public static ProgressionState read(NBTTagCompound tag) {
        if (tag == null) {
            throw new IllegalArgumentException("no progression");
        }
        requireKeys(tag, TAG_VERSION, TAG_SKILLS, TAG_CHARACTER_LEVEL,
                TAG_CHARACTER_INTO, TAG_PERKS, TAG_DEEDS, TAG_DEED_COUNTERS,
                TAG_TITLE, TAG_RESETS);
        if (!tag.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || tag.getInteger(TAG_VERSION) != VERSION) {
            throw new IllegalArgumentException("progression of another version");
        }
        ProgressionState state = new ProgressionState();
        for (NBTTagCompound entry : entries(tag, TAG_SKILLS, ProgressionState.MAX_SKILLS)) {
            requireKeys(entry, TAG_ID, TAG_LEVEL, TAG_INTO);
            String id = string(entry, TAG_ID);
            if (state.skills().containsKey(id)) {
                throw new IllegalArgumentException("a skill written twice");
            }
            state.setSkill(id, new SkillStanding(integer(entry, TAG_LEVEL),
                    decimal(entry, TAG_INTO)));
        }
        state.setCharacter(integer(tag, TAG_CHARACTER_LEVEL),
                whole(tag, TAG_CHARACTER_INTO));
        for (NBTTagCompound entry : entries(tag, TAG_PERKS, ProgressionState.MAX_PERKS)) {
            requireKeys(entry, TAG_SKILL, TAG_PERK, TAG_RANK);
            String skill = string(entry, TAG_SKILL);
            String perk = string(entry, TAG_PERK);
            int rank = integer(entry, TAG_RANK);
            if (skill.indexOf('/') >= 0 || rank < 1
                    || state.perkRank(skill, perk) != 0) {
                throw new IllegalArgumentException("a perk out of shape");
            }
            state.setPerkRank(skill, perk, rank);
        }
        for (NBTTagCompound entry : entries(tag, TAG_DEEDS, ProgressionState.MAX_DEEDS)) {
            requireKeys(entry, TAG_ID);
            if (!state.addDeed(string(entry, TAG_ID))) {
                throw new IllegalArgumentException("a deed written twice");
            }
        }
        for (NBTTagCompound entry : entries(tag, TAG_DEED_COUNTERS,
                ProgressionState.MAX_DEED_COUNTERS)) {
            requireKeys(entry, TAG_ID, TAG_COUNT);
            String id = string(entry, TAG_ID);
            long count = whole(entry, TAG_COUNT);
            if (count < 1L || state.deedCounter(id) != 0L || state.hasDeed(id)) {
                throw new IllegalArgumentException("a deed counter out of shape");
            }
            state.setDeedCounter(id, count);
        }
        if (!tag.hasKey(TAG_TITLE, Constants.NBT.TAG_STRING)) {
            throw new IllegalArgumentException("a title of the wrong kind");
        }
        state.setTitle(tag.getString(TAG_TITLE));
        state.setResets(integer(tag, TAG_RESETS));
        return state;
    }

    private static void requireKeys(NBTTagCompound tag, String... keys) {
        Set<?> present = tag.func_150296_c();
        if (present.size() != keys.length) {
            throw new IllegalArgumentException("progression keys out of shape");
        }
        for (String key : keys) {
            if (!present.contains(key)) {
                throw new IllegalArgumentException("progression lacks " + key);
            }
        }
    }

    private static NBTTagCompound[] entries(NBTTagCompound tag, String key, int max) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(key + " is not a list");
        }
        NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
        NBTBase raw = tag.getTag(key);
        int written = raw instanceof NBTTagList ? ((NBTTagList) raw).tagCount() : -1;
        if (written != list.tagCount() || list.tagCount() > max
                || list.tagCount() > 0 && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " out of shape");
        }
        NBTTagCompound[] entries = new NBTTagCompound[list.tagCount()];
        for (int index = 0; index < entries.length; index++) {
            entries[index] = list.getCompoundTagAt(index);
        }
        return entries;
    }

    private static String string(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            throw new IllegalArgumentException(key + " is not a text");
        }
        String value = tag.getString(key);
        if (!ProgressionState.fitsId(value)) {
            throw new IllegalArgumentException(key + " is empty or too long");
        }
        return value;
    }

    private static int integer(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new IllegalArgumentException(key + " is not a whole number");
        }
        return tag.getInteger(key);
    }

    private static long whole(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LONG)) {
            throw new IllegalArgumentException(key + " is not a long number");
        }
        long value = tag.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException(key + " is below 0");
        }
        return value;
    }

    private static double decimal(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_DOUBLE)) {
            throw new IllegalArgumentException(key + " is not a number");
        }
        return tag.getDouble(key);
    }
}
