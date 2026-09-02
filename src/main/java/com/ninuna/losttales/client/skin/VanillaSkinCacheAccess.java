package com.ninuna.losttales.client.skin;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Finds vanilla's on-disk skin cache, {@code <assets>/skins/<xx>/<hash>},
 * where the skin manager stores each download as the raw file it received.
 * Reading it saves a second download of the same image. The assets directory
 * is a private field of {@link Minecraft}; it is resolved once by name and
 * checked for its type, and an incompatible build simply yields no cache.
 */
final class VanillaSkinCacheAccess {

    private static final String CACHE_DIRECTORY = "skins";
    private static final Field ASSETS_DIRECTORY_FIELD = resolveAssetsDirectoryField();

    private VanillaSkinCacheAccess() {}

    /** Vanilla's cached copy of the skin with this hash, or null when unknown. */
    static File cachedCopy(Minecraft minecraft, String hash) {
        File assets = assetsDirectory(minecraft);
        if (assets == null || hash == null || hash.length() < 2) {
            return null;
        }
        return new File(new File(new File(assets, CACHE_DIRECTORY),
                hash.substring(0, 2)), hash);
    }

    static boolean isAvailable() {
        return ASSETS_DIRECTORY_FIELD != null;
    }

    private static File assetsDirectory(Minecraft minecraft) {
        if (minecraft == null || ASSETS_DIRECTORY_FIELD == null) {
            return null;
        }
        try {
            Object value = ASSETS_DIRECTORY_FIELD.get(minecraft);
            return value instanceof File ? (File)value : null;
        } catch (IllegalAccessException failure) {
            return null;
        }
    }

    private static Field resolveAssetsDirectoryField() {
        Field field = findNamedField("fileAssets");
        if (field == null) {
            field = findNamedField("field_110446_Y");
        }
        return field;
    }

    /**
     * Minecraft holds several directory fields, so only the named one is
     * accepted, and only when it is an instance field of type File.
     */
    private static Field findNamedField(String name) {
        try {
            Field field = Minecraft.class.getDeclaredField(name);
            if (Modifier.isStatic(field.getModifiers())
                    || field.getType() != File.class) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            return null;
        } catch (SecurityException denied) {
            return null;
        }
    }
}
