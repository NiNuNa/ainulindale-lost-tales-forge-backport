package com.ninuna.losttales.config;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Where the mod's files live under Forge's config directory: all of
 * them in one folder, {@code config/losttales/}. The main options, the
 * camera options, the camera presets, the lore characters and the
 * client's own stores share it, so an installation has one place to
 * look and one folder to copy.
 *
 * <p>Older builds wrote the two option files straight into
 * {@code config/}. The first time a file is asked for and is not in the
 * folder yet, the old one is moved there, so nobody's settings are lost
 * and nobody edits a file the mod no longer reads. A file already in the
 * folder is never overwritten by an old one.</p>
 */
public final class LostTalesConfigFiles {
    /** The folder's name under Forge's config directory. */
    public static final String FOLDER = LostTalesMetaData.MOD_ID;
    /** The main options file. */
    public static final String MAIN_OPTIONS = LostTalesMetaData.MOD_ID + ".cfg";
    /** The optional third-person camera's options file. */
    public static final String CAMERA_OPTIONS = LostTalesMetaData.MOD_ID + "-third-person.cfg";

    private LostTalesConfigFiles() {}

    /** The mod's folder under Forge's config directory, created if missing. */
    public static File directory(File modConfigDirectory) {
        if (modConfigDirectory == null) {
            throw new IllegalArgumentException("modConfigDirectory is required");
        }
        File folder = new File(modConfigDirectory, FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            log("Could not create the config folder " + folder.getAbsolutePath());
        }
        return folder;
    }

    /**
     * The file of that name in the mod's folder. An older build's copy
     * straight under {@code config/} is moved into the folder first when
     * the folder has none, and left alone when it does.
     */
    public static File file(File modConfigDirectory, String name) {
        File folder = directory(modConfigDirectory);
        File current = new File(folder, name);
        File legacy = new File(modConfigDirectory, name);
        if (!current.exists() && legacy.isFile()) {
            if (move(legacy, current)) {
                log("Moved " + legacy.getName() + " into " + folder.getPath());
            } else {
                log("Could not move " + legacy.getAbsolutePath() + " into "
                        + folder.getPath() + "; the mod reads and writes "
                        + current.getPath() + " from now on and leaves the old file "
                        + "where it is");
            }
        }
        return current;
    }

    /**
     * One line to the game log. The move runs during start-up, and in a
     * test there is no game log to write to; a log that cannot be written
     * never stops the file from being found.
     */
    private static void log(String message) {
        try {
            FMLLog.info("[%s] %s", LostTalesMetaData.MOD_ID, message);
        } catch (RuntimeException unavailable) {
            // No FML log outside the game; the move itself has happened.
        }
    }

    /** A rename, or a copy and delete where a rename is refused. */
    private static boolean move(File from, File to) {
        if (from.renameTo(to)) {
            return true;
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(from);
            output = new FileOutputStream(to);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.close();
            output = null;
            input.close();
            input = null;
            return from.delete() || to.isFile();
        } catch (IOException failed) {
            return false;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Nothing more can be done with a stream that will not close.
            }
        }
    }
}
