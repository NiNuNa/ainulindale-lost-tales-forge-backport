package com.ninuna.losttales.config;

import java.util.Map;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * What a config file cannot hold about an option: its default, the
 * comment written above it, its bounds and its words. A configuration
 * read from a file knows only what each option is set to — Forge takes
 * the value it reads as the default as well — so a screen built on one
 * can only restore what was saved last, and a value written back through
 * {@code Configuration.get(category, key, value)} takes its default and
 * its comment with it. The options' own definitions are read once,
 * against no file, while every field still holds the value the mod
 * ships; {@link #apply} dresses any configuration in them and leaves
 * what each option is set to alone.
 */
public final class LostTalesConfigDefinitions {
    private LostTalesConfigDefinitions() {}

    /**
     * Gives every option of {@code target} that {@code definitions}
     * holds — the same category, key, type and shape — the default,
     * comment, bounds and words it is defined with. What each is set to
     * is untouched, and so is an option the definitions do not hold or
     * hold in another shape, which its own load converts.
     */
    public static void apply(Configuration definitions, Configuration target) {
        if (definitions == null || target == null) {
            return;
        }
        for (String name : definitions.getCategoryNames()) {
            if (!target.hasCategory(name)) {
                continue;
            }
            ConfigCategory defined = definitions.getCategory(name);
            ConfigCategory held = target.getCategory(name);
            for (Map.Entry<String, Property> entry : defined.getValues().entrySet()) {
                Property property = held.get(entry.getKey());
                if (property != null) {
                    dress(property, entry.getValue());
                }
            }
        }
    }

    private static void dress(Property property, Property definition) {
        if (property.getType() != definition.getType()
                || property.isList() != definition.isList()) {
            return;
        }
        if (definition.isList()) {
            property.setDefaultValues(definition.getDefaults());
        } else {
            property.setDefaultValue(definition.getDefault());
        }
        if (definition.comment != null && definition.comment.length() > 0) {
            property.comment = definition.comment;
        }
        String[] words = definition.getValidValues();
        if (words != null && words.length > 0) {
            property.setValidValues(words);
        }
        try {
            if (definition.getType() == Property.Type.INTEGER) {
                property.setMinValue(Integer.parseInt(definition.getMinValue()));
                property.setMaxValue(Integer.parseInt(definition.getMaxValue()));
            } else if (definition.getType() == Property.Type.DOUBLE) {
                property.setMinValue(Double.parseDouble(definition.getMinValue()));
                property.setMaxValue(Double.parseDouble(definition.getMaxValue()));
            }
        } catch (NumberFormatException unstated) {
            // A bound the definition cannot state leaves the option unbounded.
        }
    }
}
