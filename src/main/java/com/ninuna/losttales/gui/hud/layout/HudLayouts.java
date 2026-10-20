package com.ninuna.losttales.gui.hud.layout;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/**
 * The HUD layouts this client knows (MAP-11): the bundled files, read
 * through the resources so a resource pack may change them, with the
 * player's own from {@code config/losttales/client/hud_layouts/} in place
 * of those of the same id and theirs after. Read again whenever the
 * resources are, F3+T included; a server never sends one. What loaded is
 * handed to the client file's options ({@link LostTalesConfig#useHudLayouts}),
 * which put the panels where the chosen layout has them.
 */
@SideOnly(Side.CLIENT)
public final class HudLayouts {
    private static volatile List<HudLayout> layouts = Collections.<HudLayout>emptyList();
    private static volatile boolean loaded;
    private static File folder;

    /** Reads every layout again whenever the resources are. */
    public static final IResourceManagerReloadListener RELOADER =
            new IResourceManagerReloadListener() {
                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    reload(manager);
                }
            };

    private HudLayouts() {}

    /** Where the player's own layouts are kept: under the client folder. */
    public static synchronized void initialize(File clientFolder) {
        folder = clientFolder == null ? null
                : new File(clientFolder, LostTalesConfigFiles.HUD_LAYOUTS);
    }

    /** Reads the bundled layouts and the player's own again; every file left out is logged. */
    public static synchronized void reload(IResourceManager manager) {
        DataFiles.Result<HudLayout> read = HudLayoutFiles.read(source(manager), folder);
        for (String problem : read.problems) {
            LostTalesLog.warning("HUD layout left out: %s", problem);
        }
        layouts = Collections.unmodifiableList(new ArrayList<HudLayout>(read.values));
        loaded = true;
        LostTalesConfig.useHudLayouts(HudLayoutFiles.placesById(read.values));
    }

    /** Every layout that loaded, the bundled first in their index's order. */
    public static List<HudLayout> all() {
        ensureLoaded();
        return layouts;
    }

    /** The layout of that id; null for custom or one that did not load. */
    public static HudLayout get(String id) {
        for (HudLayout layout : all()) {
            if (layout.getId().equals(id)) {
                return layout;
            }
        }
        return null;
    }

    /** A layout's name in the reader's language: as its file writes it, else its lang line. */
    public static String name(String id) {
        HudLayout layout = get(id);
        return layout != null && layout.getName().length() > 0 ? layout.getName()
                : StatCollector.translateToLocal(HudLayoutFiles.NAME_KEY_PREFIX + id);
    }

    /** What a layout is for: as its file writes it, else its lang line; empty for neither. */
    public static String description(String id) {
        HudLayout layout = get(id);
        if (layout != null && layout.getDescription().length() > 0) {
            return layout.getDescription();
        }
        String key = HudLayoutFiles.DESCRIPTION_KEY_PREFIX + id;
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : "";
    }

    private static void ensureLoaded() {
        if (!loaded) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.getResourceManager() != null) {
                reload(minecraft.getResourceManager());
            }
        }
    }

    /** The bundled files as the resources hold them, a resource pack's over the mod's own. */
    private static DataFiles.Source source(final IResourceManager manager) {
        return new DataFiles.Source() {
            @Override
            public Reader open(String path) throws IOException {
                if (manager == null) {
                    return null;
                }
                try {
                    IResource resource = manager.getResource(
                            new ResourceLocation(LostTalesMetaData.MOD_ID, path));
                    return new InputStreamReader(resource.getInputStream(),
                            StandardCharsets.UTF_8);
                } catch (FileNotFoundException missing) {
                    return null;
                }
            }
        };
    }
}
