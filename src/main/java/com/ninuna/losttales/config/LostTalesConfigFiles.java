package com.ninuna.losttales.config;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.File;

/**
 * Where the mod's files live under Forge's config directory, split by
 * the side that reads them:
 *
 * <pre>
 * config/losttales/
 *   client/   client.cfg, third-person.cfg, camera_presets/, chat/, map_markers.txt,
 *             windows/&lt;account uuid&gt;.txt, templates/&lt;account uuid&gt;.txt
 *   server/   server.cfg, roles.cfg, channels.cfg
 *   lore_characters/   content both sides read; not configuration
 * </pre>
 *
 * <p>The client folder holds what one installation decides for itself
 * and nothing a server would read; the server folder holds what the
 * server is authoritative for, the Discord secrets included, and is
 * never read by a client joining somebody else's server. A dedicated
 * server never creates the client folder.</p>
 */
public final class LostTalesConfigFiles {
    /** The folder's name under Forge's config directory. */
    public static final String FOLDER = LostTalesMetaData.MOD_ID;
    /** The client's folder inside it. */
    public static final String CLIENT_FOLDER = "client";
    /** The server's folder inside it. */
    public static final String SERVER_FOLDER = "server";

    /** The client's options file, in the client folder. */
    public static final String CLIENT_OPTIONS = "client.cfg";
    /** The server's options file, in the server folder. */
    public static final String SERVER_OPTIONS = "server.cfg";
    /** The chat roles and their assignments, in the server folder. */
    public static final String ROLES_OPTIONS = "roles.cfg";
    /** The chat channels' gates and definitions, in the server folder. */
    public static final String CHANNELS_OPTIONS = "channels.cfg";
    /** The optional third-person camera's options file, in the client folder. */
    public static final String CAMERA_OPTIONS = "third-person.cfg";
    /** The editable camera presets, a folder under the client folder. */
    public static final String CAMERA_PRESETS = "camera_presets";
    /**
     * The window layouts, a folder under the client folder holding
     * one file per Minecraft account: windows are the account's, so two
     * people sharing a machine keep their own arrangement.
     */
    public static final String WINDOW_LAYOUTS = "windows";
    /** Emoji favourites and use counts, under the client folder. */
    public static final String CHAT_EMOJIS = "chat/emojis.txt";
    /** The ignore list, under the client folder. */
    public static final String CHAT_IGNORES = "chat/ignores.txt";
    /** This installation's own profanity words, under the client folder; never written. */
    public static final String CHAT_PROFANITY = "chat/profanity.txt";
    /**
     * How far each account has read each conversation on each server, a
     * folder under the client folder holding one file per account.
     */
    public static final String CHAT_READ_MARKS = "chat/read_marks";
    /**
     * The status each account last chose for each of its identities on
     * each server, a folder under the client folder holding one file per
     * account.
     */
    public static final String CHAT_PRESENCE = "chat/statuses";
    /** Map marker favourites and recent destinations, under the client folder. */
    public static final String MAP_MARKERS = "map_markers.txt";
    /**
     * The default-character templates, a folder under the client folder
     * holding one file per Minecraft account. A template is what a new
     * world's default character is filled in from, so it is the account's
     * and not the installation's: two people sharing a machine keep their
     * own, and one person with two accounts keeps one for each.
     */
    public static final String CHARACTER_TEMPLATES = "templates";

    private LostTalesConfigFiles() {}

    /** The mod's folder under Forge's config directory, created if missing. */
    public static File directory(File modConfigDirectory) {
        if (modConfigDirectory == null) {
            throw new IllegalArgumentException("modConfigDirectory is required");
        }
        return ensured(new File(modConfigDirectory, FOLDER));
    }

    /** The client's folder, created if missing. */
    public static File clientDirectory(File modConfigDirectory) {
        return ensured(new File(directory(modConfigDirectory), CLIENT_FOLDER));
    }

    /** The server's folder, created if missing. */
    public static File serverDirectory(File modConfigDirectory) {
        return ensured(new File(directory(modConfigDirectory), SERVER_FOLDER));
    }

    /** A file of the client's, by its path under the client folder. */
    public static File clientFile(File modConfigDirectory, String name) {
        return new File(clientDirectory(modConfigDirectory), name);
    }

    /** A file of the server's, by its path under the server folder. */
    public static File serverFile(File modConfigDirectory, String name) {
        return new File(serverDirectory(modConfigDirectory), name);
    }

    /** The client's options file. */
    public static File clientOptions(File modConfigDirectory) {
        return clientFile(modConfigDirectory, CLIENT_OPTIONS);
    }

    /** The server's options file. */
    public static File serverOptions(File modConfigDirectory) {
        return serverFile(modConfigDirectory, SERVER_OPTIONS);
    }

    /** The server's roles file. */
    public static File rolesOptions(File modConfigDirectory) {
        return serverFile(modConfigDirectory, ROLES_OPTIONS);
    }

    /** The server's channels file. */
    public static File channelsOptions(File modConfigDirectory) {
        return serverFile(modConfigDirectory, CHANNELS_OPTIONS);
    }

    /** A file of that name straight in the mod's folder. */
    public static File file(File modConfigDirectory, String name) {
        return new File(directory(modConfigDirectory), name);
    }

    private static File ensured(File folder) {
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            try {
                FMLLog.info("[%s] Could not create the config folder %s",
                        LostTalesMetaData.MOD_ID, folder.getAbsolutePath());
            } catch (RuntimeException unavailable) {
                // No FML log outside the game.
            }
        }
        return folder;
    }
}
