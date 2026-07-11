package com.ninuna.losttales.character.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * Small, explicit migration pipeline for versioned character NBT records.
 *
 * Version zero represents an unversioned record using the current field names.
 * No released character format predates version one, but accepting version zero
 * provides a controlled path for development worlds and future migration tests.
 */
public final class CharacterDataMigrator {

    private static final String DATA_VERSION_TAG = "DataVersion";

    private CharacterDataMigrator() {}

    public static MigrationResult migrate(NBTTagCompound source, int currentVersion) {
        if (source == null) {
            return MigrationResult.invalid();
        }

        NBTTagCompound migrated = (NBTTagCompound) source.copy();
        int version = migrated.hasKey(DATA_VERSION_TAG, Constants.NBT.TAG_INT)
                ? migrated.getInteger(DATA_VERSION_TAG)
                : 0;

        if (version < 0) {
            return MigrationResult.invalid();
        }
        if (version > currentVersion) {
            return MigrationResult.unsupported(version);
        }

        boolean changed = false;
        while (version < currentVersion) {
            switch (version) {
                case 0:
                    version = 1;
                    migrated.setInteger(DATA_VERSION_TAG, version);
                    changed = true;
                    break;
                case 1:
                    // Character data version 2 adds SkinId. The NBT codec
                    // assigns its deterministic race/gender-compatible default.
                    version = 2;
                    migrated.setInteger(DATA_VERSION_TAG, version);
                    changed = true;
                    break;
                case 2:
                    // Character data version 3 removes the unspecified option
                    // and makes allowed gender values race-specific. The codec
                    // repairs the value and selected skin together.
                    version = 3;
                    migrated.setInteger(DATA_VERSION_TAG, version);
                    changed = true;
                    break;
                default:
                    return MigrationResult.unsupported(version);
            }
        }

        return MigrationResult.success(migrated, version, changed);
    }

    public static final class MigrationResult {
        private final NBTTagCompound tag;
        private final int version;
        private final boolean migrated;
        private final boolean supported;
        private final boolean valid;

        private MigrationResult(NBTTagCompound tag, int version, boolean migrated,
                                boolean supported, boolean valid) {
            this.tag = tag;
            this.version = version;
            this.migrated = migrated;
            this.supported = supported;
            this.valid = valid;
        }

        public static MigrationResult success(NBTTagCompound tag, int version, boolean migrated) {
            return new MigrationResult(tag, version, migrated, true, true);
        }

        public static MigrationResult unsupported(int version) {
            return new MigrationResult(null, version, false, false, true);
        }

        public static MigrationResult invalid() {
            return new MigrationResult(null, -1, false, false, false);
        }

        public NBTTagCompound getTag() {
            return this.tag;
        }

        public int getVersion() {
            return this.version;
        }

        public boolean wasMigrated() {
            return this.migrated;
        }

        public boolean isSupported() {
            return this.supported;
        }

        public boolean isValid() {
            return this.valid;
        }
    }
}
