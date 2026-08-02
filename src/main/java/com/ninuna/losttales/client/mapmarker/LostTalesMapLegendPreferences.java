package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Client-owned persistent visibility choices for map legend categories. */
@SideOnly(Side.CLIENT)
final class LostTalesMapLegendPreferences {
    private static final int MAX_CATEGORY_ID_LENGTH = 64;
    private static final int MAX_HIDDEN_CATEGORIES = 64;

    private LostTalesMapLegendPreferences() {
    }

    static boolean isEnabled(String categoryId) {
        String normalized = normalize(categoryId);
        return normalized.length() == 0
                || !hiddenCategories().contains(normalized);
    }

    static boolean toggle(String categoryId) {
        String normalized = normalize(categoryId);
        if (normalized.length() == 0) {
            return true;
        }
        Set<String> hidden = hiddenCategories();
        boolean enabled;
        if (hidden.remove(normalized)) {
            enabled = true;
        } else {
            hidden.add(normalized);
            enabled = false;
        }
        LostTalesConfig.hiddenMapLegendCategories =
                hidden.toArray(new String[hidden.size()]);
        LostTalesConfig.save();
        return enabled;
    }

    private static Set<String> hiddenCategories() {
        LinkedHashSet<String> hidden = new LinkedHashSet<String>();
        String[] configured = LostTalesConfig.hiddenMapLegendCategories;
        if (configured == null) {
            return hidden;
        }
        for (String value : configured) {
            String normalized = normalize(value);
            if (normalized.length() > 0) {
                hidden.add(normalized);
            }
            if (hidden.size() >= MAX_HIDDEN_CATEGORIES) {
                break;
            }
        }
        return hidden;
    }

    static String normalize(String value) {
        String normalized = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0
                || normalized.length() > MAX_CATEGORY_ID_LENGTH) {
            return "";
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-'
                    && character != '.') {
                return "";
            }
        }
        return normalized;
    }
}
