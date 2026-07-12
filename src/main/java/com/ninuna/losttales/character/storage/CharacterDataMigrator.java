package com.ninuna.losttales.character.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * Explicit migration entry points for each character persistence schema.
 *
 * Keeping the schema type in the method name prevents a future root, roster,
 * or progression version increase from accidentally executing character-only
 * migration steps.
 */
public final class CharacterDataMigrator {

    private static final String DATA_VERSION_TAG = "DataVersion";

    private CharacterDataMigrator() {}

    public static MigrationResult migrateRoot(NBTTagCompound source, int currentVersion) {
        return migrate(source, currentVersion, Schema.ROOT);
    }

    public static MigrationResult migrateRoster(NBTTagCompound source, int currentVersion) {
        return migrate(source, currentVersion, Schema.ROSTER);
    }

    public static MigrationResult migrateCharacter(NBTTagCompound source, int currentVersion) {
        return migrate(source, currentVersion, Schema.CHARACTER);
    }

    public static MigrationResult migrateProgression(NBTTagCompound source, int currentVersion) {
        return migrate(source, currentVersion, Schema.PROGRESSION);
    }

    private static MigrationResult migrate(NBTTagCompound source, int currentVersion, Schema schema) {
        if (source == null || currentVersion < 1) {
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
            int nextVersion = nextVersion(schema, version);
            if (nextVersion <= version || nextVersion > currentVersion) {
                return MigrationResult.unsupported(version);
            }
            version = nextVersion;
            migrated.setInteger(DATA_VERSION_TAG, version);
            changed = true;
        }

        return MigrationResult.success(migrated, version, changed);
    }

    private static int nextVersion(Schema schema, int version) {
        switch (schema) {
            case ROOT:
                // Root v1 introduced the explicit root DataVersion.
                // Root v2 adds the additive Quarantine container.
                if (version == 0) {
                    return 1;
                }
                if (version == 1) {
                    return 2;
                }
                break;
            case ROSTER:
                if (version == 0) {
                    return 1;
                }
                break;
            case CHARACTER:
                if (version == 0) {
                    return 1;
                }
                if (version == 1) {
                    // Character v2 adds SkinId. The codec assigns a
                    // deterministic race/gender-compatible default.
                    return 2;
                }
                if (version == 2) {
                    // Character v3 removes the unspecified option and makes
                    // allowed gender values race-specific. The codec repairs
                    // the selected gender and skin together.
                    return 3;
                }
                if (version == 3) {
                    // Character v4 adds normal-cape visibility and a stable
                    // numeric LOTR cosmetic-cape selection. Missing values are
                    // filled by the codec with backward-compatible defaults.
                    return 4;
                }
                break;
            case PROGRESSION:
                if (version == 0) {
                    return 1;
                }
                break;
            default:
                break;
        }
        return -1;
    }

    private enum Schema {
        ROOT,
        ROSTER,
        CHARACTER,
        PROGRESSION
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
