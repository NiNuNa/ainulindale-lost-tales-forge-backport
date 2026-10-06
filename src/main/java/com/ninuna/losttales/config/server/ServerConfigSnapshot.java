package com.ninuna.losttales.config.server;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The server-side keys of a Forge configuration as entries: every
 * category but the client's, each property with its type, bounds and
 * valid values, and the secrets blanked. What the Server Settings page
 * and the commands read; nothing here touches a file.
 */
public final class ServerConfigSnapshot {

    /** Keys whose value never leaves the server. */
    public static final Set<String> SECRET_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    LostTalesConfig.CATEGORY_DISCORD + ".botToken")));
    /** Categories that belong to the client and are not the server's to offer. */
    public static final Set<String> CLIENT_CATEGORIES = LostTalesConfig.CLIENT_CATEGORIES;
    /**
     * Categories that decide what a player is allowed to do: the roles and
     * the permissions they reach, and the gates the channels ask for. They
     * are edited by {@code /losttales role}, which asks for
     * {@link com.ninuna.losttales.permission.LostTalesCapability#ROLES_MANAGE},
     * and are kept out of the general settings surface so that
     * {@code server.config} cannot be used to grant itself anything else.
     */
    public static final Set<String> AUTHORIZATION_CATEGORIES =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    LostTalesConfig.CATEGORY_ROLES,
                    LostTalesConfig.CATEGORY_CHANNELS)));
    /** Every category the general settings surface leaves alone. */
    public static final Set<String> EXCLUDED_CATEGORIES = excluded();
    /**
     * Keys a command of their own writes and the general settings
     * surface leaves alone, as {@code category.key} in lower case: the
     * Discord links, which hold webhook addresses — as good as passwords
     * to their Discord channels — and are made with
     * {@code /losttales discord link}.
     */
    public static final Set<String> COMMAND_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    LostTalesConfig.CATEGORY_DISCORD + ".channelbindings")));
    public static final int MAX_ENTRIES = 512;

    private static Set<String> excluded() {
        Set<String> names = new HashSet<String>();
        for (String name : CLIENT_CATEGORIES) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
        for (String name : AUTHORIZATION_CATEGORIES) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(names);
    }

    private ServerConfigSnapshot() {}

    /**
     * The entries of every category not excluded, in category then key
     * order, leaving out the {@code excludedKeys} too, each named
     * {@code category.key} in lower case.
     */
    public static List<ServerConfigEntry> fromConfiguration(Configuration config,
                                                            Set<String> excludedCategories,
                                                            Set<String> excludedKeys,
                                                            Set<String> secretKeys) {
        List<ServerConfigEntry> entries = new ArrayList<ServerConfigEntry>();
        if (config == null) {
            return entries;
        }
        for (String categoryName : new TreeSet<String>(config.getCategoryNames())) {
            if (excludedCategories != null && excludedCategories.contains(
                    categoryName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            ConfigCategory category = config.getCategory(categoryName);
            for (String key : new TreeSet<String>(category.getValues().keySet())) {
                Property property = category.get(key);
                if (property == null || entries.size() >= MAX_ENTRIES
                        || (excludedKeys != null && excludedKeys.contains(
                                (categoryName + "." + key).toLowerCase(Locale.ROOT)))) {
                    continue;
                }
                boolean secret = secretKeys != null
                        && secretKeys.contains(categoryName + "." + key);
                entries.add(entryOf(categoryName, key, property, secret));
            }
        }
        return entries;
    }

    /**
     * One property as an entry. A secret keeps its value at home: it
     * leaves as one blank value while it holds one and as none while it
     * does not, so a page can say whether it is set.
     */
    static ServerConfigEntry entryOf(String category, String key, Property property,
                                     boolean secret) {
        List<String> values = secret ? (property.getString().length() == 0
                        ? Collections.<String>emptyList() : Collections.singletonList(""))
                : property.isList() ? Arrays.asList(property.getStringList())
                : Collections.singletonList(property.getString());
        List<String> defaults = secret ? Collections.singletonList("")
                : property.isList() ? Arrays.asList(property.getDefaults())
                : Collections.singletonList(property.getDefault());
        String[] valid = property.getValidValues();
        return new ServerConfigEntry(category, key, typeOf(property.getType()),
                property.isList(), values, defaults, property.getMinValue(),
                property.getMaxValue(), secret,
                valid == null ? null : Arrays.asList(valid));
    }

    static ServerConfigEntry.Type typeOf(Property.Type type) {
        if (type == Property.Type.INTEGER) {
            return ServerConfigEntry.Type.INTEGER;
        }
        if (type == Property.Type.BOOLEAN) {
            return ServerConfigEntry.Type.BOOLEAN;
        }
        if (type == Property.Type.DOUBLE) {
            return ServerConfigEntry.Type.DOUBLE;
        }
        return ServerConfigEntry.Type.STRING;
    }

    /** The entry named, or null. */
    public static ServerConfigEntry find(List<ServerConfigEntry> entries,
                                         String category, String key) {
        if (entries == null || category == null || key == null) {
            return null;
        }
        for (ServerConfigEntry entry : entries) {
            if (entry.getCategory().equalsIgnoreCase(category)
                    && entry.getKey().equalsIgnoreCase(key)) {
                return entry;
            }
        }
        return null;
    }
}
