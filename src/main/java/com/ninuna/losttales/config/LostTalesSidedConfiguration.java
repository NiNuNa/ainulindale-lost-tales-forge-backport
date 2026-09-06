package com.ninuna.losttales.config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

/**
 * One configuration over several files, told apart by category: the
 * client's options, the server's, and a file of its own for any category
 * given one (the roles, the channels). Every read and write Forge's
 * {@link Configuration} offers goes through {@link #getCategory}, so
 * routing that one call by the category's file is enough for the whole
 * option surface to read and write the right file, and the loader keeps
 * naming categories the way it always did.
 *
 * <p>A side without a file — the client's on a dedicated server — is a
 * configuration in memory only: reads answer with the defaults, and
 * saving writes nothing for it. The legacy single file is the case of
 * every category being one and the same file, loaded and saved once.</p>
 */
public final class LostTalesSidedConfiguration extends Configuration {

    private final Configuration client;
    private final Configuration server;
    /** Root category name, lower-cased, to the configuration holding it. */
    private final Map<String, Configuration> byCategory;
    /** Every distinct file-backed configuration, for loading and saving. */
    private final List<Configuration> onDisk;

    private LostTalesSidedConfiguration(Configuration client, Configuration server,
                                        Map<String, Configuration> byCategory,
                                        List<Configuration> onDisk) {
        super();
        this.client = client;
        this.server = server;
        this.byCategory = byCategory;
        this.onDisk = onDisk;
    }

    /**
     * Opens the client's and the server's files; every category not the
     * client's is the server's.
     */
    public static LostTalesSidedConfiguration open(File clientFile, File serverFile,
                                                  Set<String> clientCategories) {
        return open(clientFile, serverFile, clientCategories,
                new HashMap<String, File>());
    }

    /**
     * Opens every file. {@code clientFile} may be null for a side kept in
     * memory; {@code filesByCategory} names the categories with a file of
     * their own; a file named more than once is opened once.
     */
    public static LostTalesSidedConfiguration open(File clientFile, File serverFile,
                                                  Set<String> clientCategories,
                                                  Map<String, File> filesByCategory) {
        if (serverFile == null) {
            throw new IllegalArgumentException("serverFile is required");
        }
        Map<File, Configuration> opened = new HashMap<File, Configuration>();
        List<Configuration> onDisk = new ArrayList<Configuration>();
        Configuration server = openOnce(serverFile, opened, onDisk);
        Configuration client = clientFile == null ? new Configuration()
                : openOnce(clientFile, opened, onDisk);
        Map<String, Configuration> byCategory = new HashMap<String, Configuration>();
        for (String category : clientCategories) {
            byCategory.put(category.toLowerCase(Locale.ROOT), client);
        }
        if (filesByCategory != null) {
            for (Map.Entry<String, File> entry : filesByCategory.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    byCategory.put(entry.getKey().toLowerCase(Locale.ROOT),
                            openOnce(entry.getValue(), opened, onDisk));
                }
            }
        }
        return new LostTalesSidedConfiguration(client, server, byCategory, onDisk);
    }

    private static Configuration openOnce(File file, Map<File, Configuration> opened,
                                          List<Configuration> onDisk) {
        Configuration existing = opened.get(file);
        if (existing != null) {
            return existing;
        }
        Configuration configuration = new Configuration(file);
        opened.put(file, configuration);
        onDisk.add(configuration);
        return configuration;
    }

    /** The configuration a category lives in, by its root category name. */
    Configuration sideOf(String category) {
        String root = category == null ? "" : category;
        int split = root.indexOf(Configuration.CATEGORY_SPLITTER);
        if (split >= 0) {
            root = root.substring(0, split);
        }
        Configuration owner = this.byCategory.get(root.toLowerCase(Locale.ROOT));
        return owner == null ? this.server : owner;
    }

    /** The client's half, as the client's own screen edits it. */
    public Configuration getClientSide() {
        return this.client;
    }

    /** The server's main file, as the server's settings screen edits it. */
    public Configuration getServerSide() {
        return this.server;
    }

    @Override
    public ConfigCategory getCategory(String category) {
        return sideOf(category).getCategory(category);
    }

    @Override
    public boolean hasCategory(String category) {
        return sideOf(category).hasCategory(category);
    }

    @Override
    public boolean hasKey(String category, String key) {
        return sideOf(category).hasKey(category, key);
    }

    @Override
    public Set<String> getCategoryNames() {
        Set<String> names = new LinkedHashSet<String>(this.server.getCategoryNames());
        for (Configuration part : this.onDisk) {
            names.addAll(part.getCategoryNames());
        }
        if (!this.onDisk.contains(this.client)) {
            names.addAll(this.client.getCategoryNames());
        }
        return names;
    }

    @Override
    public void removeCategory(ConfigCategory category) {
        if (category != null) {
            sideOf(category.getQualifiedName()).removeCategory(category);
        }
    }

    @Override
    public Configuration setCategoryComment(String category, String comment) {
        sideOf(category).setCategoryComment(category, comment);
        return this;
    }

    @Override
    public void load() {
        for (Configuration part : this.onDisk) {
            part.load();
        }
    }

    @Override
    public void save() {
        for (Configuration part : this.onDisk) {
            part.save();
        }
    }

    @Override
    public boolean hasChanged() {
        for (Configuration part : this.onDisk) {
            if (part.hasChanged()) {
                return true;
            }
        }
        return false;
    }
}
