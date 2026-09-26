package com.ninuna.losttales.storage;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * NBT reads and writes the mod's stores and codecs share. A UUID is kept
 * as two longs, under its key followed by {@code Most} and by
 * {@code Least}; every save the mod writes uses that layout.
 */
public final class NbtTags {
    private static final String MOST = "Most";
    private static final String LEAST = "Least";

    private NbtTags() {}

    /** Writes the id under {@code key}; a null id writes nothing. */
    public static void writeUuid(NBTTagCompound tag, String key, UUID id) {
        if (tag == null || id == null) {
            return;
        }
        tag.setLong(key + MOST, id.getMostSignificantBits());
        tag.setLong(key + LEAST, id.getLeastSignificantBits());
    }

    /** The id under {@code key}; null unless both halves are there as longs. */
    public static UUID readUuid(NBTTagCompound tag, String key) {
        if (tag == null || key == null) {
            return null;
        }
        String most = key + MOST;
        String least = key + LEAST;
        if (!tag.hasKey(most, Constants.NBT.TAG_LONG)
                || !tag.hasKey(least, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(most), tag.getLong(least));
    }

    /** Puts a copy of every tag of {@code source} into {@code destination}. */
    public static void copyContents(NBTTagCompound source,
                                    NBTTagCompound destination) {
        Set<?> keys = source.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }

    /**
     * Whether the list under {@code key} is absent, or holds compounds and
     * nothing else, {@code maximum} at most. A list of anything else reads
     * back as empty, which would lose what it holds on the next write.
     */
    public static boolean hasCompoundListWithinLimit(NBTTagCompound owner,
                                                     String key, int maximum) {
        if (owner == null) {
            return false;
        }
        if (!owner.hasKey(key)) {
            return true;
        }
        NBTBase raw = owner.getTag(key);
        if (!(raw instanceof NBTTagList)) {
            return false;
        }
        NBTTagList list = (NBTTagList) raw;
        return (list.tagCount() == 0
                || list.func_150303_d() == Constants.NBT.TAG_COMPOUND)
                && list.tagCount() <= maximum;
    }

    /**
     * Whether the string under {@code key} is at most {@code maximum}
     * characters long. A required one must be there and not empty; one
     * that is not required may be absent.
     */
    public static boolean hasReasonableString(NBTTagCompound owner,
                                              String key, int maximum,
                                              boolean required) {
        if (owner == null || !owner.hasKey(key)) {
            return !required;
        }
        if (!owner.hasKey(key, Constants.NBT.TAG_STRING)) {
            return false;
        }
        String value = owner.getString(key);
        return (!required || value.length() > 0)
                && value.length() <= maximum;
    }
}
