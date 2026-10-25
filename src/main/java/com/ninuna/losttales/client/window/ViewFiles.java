package com.ninuna.losttales.client.window;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/**
 * Where the view files lie and how they are read (WIN-49): the mod's own
 * under {@code assets/losttales/views/}, listed in its {@code index.json}
 * and read through the resource manager, so a resource pack or a modpack
 * may change them; a player's own in {@code config/losttales/client/views/},
 * one file each, where a file of a view the mod's files name takes that
 * file's place. They are read again with the resources, F3+T included. A
 * server never sends one. A file with any warning is left out, with a line
 * in the log; a view no file names opens with no window.
 */
public final class ViewFiles {
    /** The mod's index of its view files, under its assets. */
    public static final String INDEX = "views/index.json";
    /** The index's list of files. */
    public static final String LIST_KEY = "views";
    /** The player's own files, a folder under the client folder. */
    public static final String FOLDER = "views";

    private static File folder;

    /** Reads every view file again whenever the resources are, F3+T included. */
    public static final IResourceManagerReloadListener RELOADER =
            new IResourceManagerReloadListener() {
                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    reload(manager);
                }
            };

    /** What the game has for a view file to name: its pages and the systems' sets. */
    static final ViewFile.Known GAME = new ViewFile.Known() {
        @Override
        public boolean isPage(String id) {
            return WindowPages.byId(id) != null;
        }

        @Override
        public boolean opensByGame(String id) {
            WindowPages.Page page = WindowPages.byId(id);
            return page != null && page.opensByGame();
        }

        @Override
        public boolean isSet(String name) {
            return ViewDefaults.isSet(name);
        }
    };

    /** What a file may name before the game has registered its pages and sets: anything. */
    static final ViewFile.Known ANYTHING = new ViewFile.Known() {
        @Override
        public boolean isPage(String id) {
            return true;
        }

        @Override
        public boolean opensByGame(String id) {
            return false;
        }

        @Override
        public boolean isSet(String name) {
            return true;
        }
    };

    /** The mod's own files as the classpath holds them, with no resource pack over them. */
    static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = ViewFiles.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID
                            + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };

    private ViewFiles() {}

    /** Where the player's own files lie: the client folder's {@code views}. */
    public static synchronized void initialize(File clientFolder) {
        folder = clientFolder == null ? null : new File(clientFolder, FOLDER);
    }

    /** View files, checked against what {@code known} says the game has. */
    static DataFiles.Kind<ViewFile> kind(final ViewFile.Known known) {
        return new DataFiles.Kind<ViewFile>() {
            @Override
            public ViewFile parse(Reader reader, String file, List<String> said) {
                return ViewFile.parse(reader, file, said);
            }

            @Override
            public String idOf(ViewFile value) {
                return value.view;
            }

            @Override
            public List<String> warnings(ViewFile value) {
                return ViewFile.warningsOf(value, known);
            }
        };
    }

    /** The mod's files as the resource packs give them: the top pack's of each. */
    static DataFiles.Source packs(final IResourceManager manager) {
        return new DataFiles.Source() {
            @Override
            public Reader open(String path) throws IOException {
                try {
                    return new InputStreamReader(manager.getResource(
                            new ResourceLocation(LostTalesMetaData.MOD_ID, path))
                            .getInputStream(), StandardCharsets.UTF_8);
                } catch (FileNotFoundException missing) {
                    return null;
                }
            }
        };
    }

    /**
     * Reads the mod's files through {@code manager} (the classpath's
     * without one) and the player's own, and has the views open with them
     * from now on.
     */
    public static void reload(IResourceManager manager) {
        DataFiles.Kind<ViewFile> kind = kind(GAME);
        DataFiles.Result<ViewFile> bundled = DataFiles.readBundled(
                manager == null ? CLASSPATH : packs(manager), INDEX, LIST_KEY,
                kind);
        File own;
        synchronized (ViewFiles.class) {
            own = folder;
        }
        DataFiles.Result<ViewFile> mine = DataFiles.readFolder(own, kind);
        note("The mod's view file", bundled.problems);
        note("Your view file", mine.problems);
        ViewDefaults.use(DataFiles.merge(bundled.values, mine.values, kind));
    }

    /**
     * The mod's own files as its jar holds them, checked for their shape
     * alone: what the views open with until the resources are first read.
     */
    static List<ViewFile> classpathFiles() {
        DataFiles.Result<ViewFile> read = DataFiles.readBundled(CLASSPATH,
                INDEX, LIST_KEY, kind(ANYTHING));
        note("The mod's view file", read.problems);
        return read.values;
    }

    private static void note(String what, List<String> problems) {
        for (String problem : problems) {
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }
}
