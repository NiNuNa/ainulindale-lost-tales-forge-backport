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
 * category but the client's, each property with its type, bounds,
 * comment and valid values, and the secrets blanked. What the operator
 * screen and the commands read; nothing here touches a file.
 */
public final class ServerConfigSnapshot {

    /** Keys whose value never leaves the server. */
    public static final Set<String> SECRET_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    LostTalesConfig.CATEGORY_DISCORD + ".botToken")));
    /** Categories that belong to the client and are not the server's to offer. */
    public static final Set<String> CLIENT_CATEGORIES = LostTalesConfig.CLIENT_CATEGORIES;
    public static final int MAX_ENTRIES = 512;

    private ServerConfigSnapshot() {}

    /** The entries of every category not excluded, in category then key order. */
    public static List<ServerConfigEntry> fromConfiguration(Configuration config,
                                                            Set<String> excludedCategories,
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
                if (property == null || entries.size() >= MAX_ENTRIES) {
                    continue;
                }
                boolean secret = secretKeys != null
                        && secretKeys.contains(categoryName + "." + key);
                entries.add(entryOf(categoryName, key, property, secret));
            }
        }
        return entries;
    }

    static ServerConfigEntry entryOf(String category, String key, Property property,
                                     boolean secret) {
        List<String> values = secret ? Collections.singletonList("")
                : property.isList() ? Arrays.asList(property.getStringList())
                : Collections.singletonList(property.getString());
        List<String> defaults = secret ? Collections.singletonList("")
                : property.isList() ? Arrays.asList(property.getDefaults())
                : Collections.singletonList(property.getDefault());
        String[] valid = property.getValidValues();
        return new ServerConfigEntry(category, key, typeOf(property.getType()),
                property.isList(), values, defaults, property.getMinValue(),
                property.getMaxValue(), property.comment, property.getLanguageKey(),
                secret, valid == null ? null : Arrays.asList(valid));
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

    static Property.Type propertyTypeOf(ServerConfigEntry.Type type) {
        switch (type) {
            case INTEGER:
                return Property.Type.INTEGER;
            case BOOLEAN:
                return Property.Type.BOOLEAN;
            case DOUBLE:
                return Property.Type.DOUBLE;
            default:
                return Property.Type.STRING;
        }
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

    /**
     * A configuration holding these entries as properties, the shape the
     * Forge config screen edits; secrets stand empty. The client builds
     * one from the snapshot, and a file is never involved.
     */
    public static Configuration toConfiguration(List<ServerConfigEntry> entries) {
        Configuration config = new Configuration();
        for (ServerConfigEntry entry : entries) {
            Property.Type type = propertyTypeOf(entry.getType());
            Property property = entry.isList()
                    ? new Property(entry.getKey(), toArray(entry.getValues()), type)
                    : new Property(entry.getKey(), entry.getValue(), type);
            if (entry.isList()) {
                property.setDefaultValues(toArray(entry.getDefaults()));
            } else {
                property.setDefaultValue(entry.getDefault());
            }
            property.comment = entry.getComment();
            property.setLanguageKey(entry.getLangKey());
            applyBounds(property, entry);
            if (!entry.getValidValues().isEmpty()) {
                property.setValidValues(toArray(entry.getValidValues()));
            }
            config.getCategory(entry.getCategory()).put(entry.getKey(), property);
        }
        return config;
    }

    private static void applyBounds(Property property, ServerConfigEntry entry) {
        try {
            if (entry.getType() == ServerConfigEntry.Type.INTEGER) {
                if (entry.getMinValue().length() > 0) {
                    property.setMinValue(Integer.parseInt(entry.getMinValue()));
                }
                if (entry.getMaxValue().length() > 0) {
                    property.setMaxValue(Integer.parseInt(entry.getMaxValue()));
                }
            } else if (entry.getType() == ServerConfigEntry.Type.DOUBLE) {
                if (entry.getMinValue().length() > 0) {
                    property.setMinValue(Double.parseDouble(entry.getMinValue()));
                }
                if (entry.getMaxValue().length() > 0) {
                    property.setMaxValue(Double.parseDouble(entry.getMaxValue()));
                }
            }
        } catch (NumberFormatException ignored) {
            // A bound the snapshot could not name leaves the property unbounded.
        }
    }

    private static String[] toArray(List<String> values) {
        return values.toArray(new String[values.size()]);
    }
}
