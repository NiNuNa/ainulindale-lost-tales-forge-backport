package com.ninuna.losttales.quest.player;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * Additive migration pipeline for the live quest property compound. Character
 * snapshots reuse this codec after their one-time account-state import.
 */
final class LostTalesQuestDataMigrator {

    private static final String TAG_DATA_VERSION = "DataVersion";

    private LostTalesQuestDataMigrator() {}

    static MigrationResult migrate(NBTTagCompound source, int currentVersion) {
        if (source == null || currentVersion < 1) {
            return MigrationResult.invalid();
        }

        NBTTagCompound migrated = (NBTTagCompound) source.copy();
        int version = migrated.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? migrated.getInteger(TAG_DATA_VERSION)
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
                    // Version 1 formalizes the existing tag layout without
                    // renaming or deleting any legacy fields.
                    version = 1;
                    migrated.setInteger(TAG_DATA_VERSION, version);
                    changed = true;
                    break;
                default:
                    return MigrationResult.unsupported(version);
            }
        }
        return MigrationResult.success(migrated, version, changed);
    }

    static final class MigrationResult {
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

        static MigrationResult success(NBTTagCompound tag, int version, boolean migrated) {
            return new MigrationResult(tag, version, migrated, true, true);
        }

        static MigrationResult unsupported(int version) {
            return new MigrationResult(null, version, false, false, true);
        }

        static MigrationResult invalid() {
            return new MigrationResult(null, -1, false, false, false);
        }

        NBTTagCompound getTag() {
            return this.tag;
        }

        int getVersion() {
            return this.version;
        }

        boolean wasMigrated() {
            return this.migrated;
        }

        boolean isSupported() {
            return this.supported;
        }

        boolean isValid() {
            return this.valid;
        }
    }
}
