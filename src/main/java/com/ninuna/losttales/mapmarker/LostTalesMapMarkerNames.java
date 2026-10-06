package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.util.Locale;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * What a map marker is called, and how it is described, in each player's
 * language.
 *
 * <p>A bundled marker is named by a lang line found from its id: a LOTR
 * waypoint ({@code lotr:waypoint:bree}) by LOTR's own
 * {@code lotr.waypoint.BREE}, which LOTR ships in its lang files, and a
 * Lost Tales marker ({@code losttales:mossy_cave}) by
 * {@code lotr.waypoint.LOSTTALES_MOSSY_CAVE}, the same line LOTR reads for
 * the waypoint the marker registers. A Lost Tales marker's description is
 * that line's {@code .info}; LOTR describes its own waypoints.</p>
 *
 * <p>The precedence: words an operator gave a bundled marker in place of
 * the ones it ships with are theirs and never translated; otherwise the
 * lang line wins where one exists; otherwise the words the marker file or
 * the server gives it; otherwise its id. A Lost Tales marker's English
 * words live in the lang file alone, and the marker files leave them
 * out ({@link #englishName}).</p>
 */
public final class LostTalesMapMarkerNames {
    private static final String LOST_TALES_PREFIX = LostTalesMetaData.MOD_ID + ":";
    private static final String WAYPOINT_KEY_PREFIX = "lotr.waypoint.";
    private static final String WAYPOINT_CODE_PREFIX = "LOSTTALES_";
    private static final String DESCRIPTION_SUFFIX = ".info";
    private static final String CATEGORY_KEY_PREFIX = "gui.losttales.map_marker.category.";
    /** What a waystone a player placed says of itself while they leave it undescribed. */
    public static final String WAYSTONE_DESCRIPTION_KEY = "gui.losttales.waystone.default_description";

    private LostTalesMapMarkerNames() {}

    /**
     * The code of the LOTR waypoint a Lost Tales marker registers under:
     * {@code losttales:mossy_cave} is {@code LOSTTALES_MOSSY_CAVE}. Every
     * character but a letter or a digit becomes one underscore.
     */
    public static String waypointCode(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim().toUpperCase(Locale.ROOT);
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < normalized.length() - 1) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        StringBuilder builder = new StringBuilder(WAYPOINT_CODE_PREFIX);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        while (builder.indexOf("__") >= 0) {
            int index = builder.indexOf("__");
            builder.deleteCharAt(index);
        }
        if (builder.length() <= WAYPOINT_CODE_PREFIX.length()) {
            builder.append("MARKER");
        }
        return builder.toString();
    }

    /** The lang key that names a bundled marker; empty for any other. */
    public static String nameKey(String markerId) {
        String lotrCode = LostTalesMapMarkerIdResolver.resolveLotrWaypointId(markerId);
        if (lotrCode.length() > 0) {
            return WAYPOINT_KEY_PREFIX + lotrCode;
        }
        return isLostTalesPath(markerId)
                ? WAYPOINT_KEY_PREFIX + waypointCode(markerId) : "";
    }

    /** The lang key that describes a Lost Tales marker; empty for any other. */
    public static String descriptionKey(String markerId) {
        return isLostTalesPath(markerId)
                ? WAYPOINT_KEY_PREFIX + waypointCode(markerId) + DESCRIPTION_SUFFIX
                : "";
    }

    /**
     * Whether {@code name} is the one the bundled marker ships with, or
     * none: words an operator has not changed, which the lang line may
     * stand in for.
     */
    public static boolean isBundledName(String markerId, String name) {
        String given = trim(name);
        if (given.length() == 0) {
            return true;
        }
        LostTalesMapMarkerDefinition bundled = LostTalesMapMarkerCatalog.getMarker(markerId);
        return bundled != null && given.equals(trim(bundled.getName()));
    }

    /** The same for a description. */
    public static boolean isBundledDescription(String markerId, String description) {
        String given = trim(description);
        if (given.length() == 0) {
            return true;
        }
        LostTalesMapMarkerDefinition bundled = LostTalesMapMarkerCatalog.getMarker(markerId);
        return bundled != null && given.equals(trim(bundled.getDescription()));
    }

    /**
     * Whether a lang line names the marker: a bundled marker that keeps
     * the name it ships with. Each player reads such a name in their own
     * language, so a name typed for it in any language may stand for it.
     */
    public static boolean isNamedByLang(String markerId, String name) {
        return nameKey(markerId).length() > 0 && isBundledName(markerId, name);
    }

    /**
     * The line under {@code key} in the language of the side that asks,
     * or {@code given} where there is no such line.
     */
    public static String translated(String key, String given) {
        return key != null && key.length() > 0 && StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : given;
    }

    /**
     * A marker's name as this side reads it, following the precedence in
     * the class comment; empty where it has neither a line nor words.
     */
    public static String shownName(String markerId, String givenName) {
        String given = trim(givenName);
        return translated(isBundledName(markerId, given) ? nameKey(markerId) : "", given);
    }

    /**
     * A marker's name as a chat component for the server to send, which
     * each reader's game words: the lang line where one names the marker,
     * else its words, else its id.
     */
    public static IChatComponent component(String markerId, String givenName) {
        return component(markerId, givenName, "");
    }

    /**
     * The same for a marker that may have no name of its own: one called
     * after its placer, a creature or a block
     * ({@link LostTalesMapMarkerNamedAfter}) is named that way while its
     * name is empty.
     */
    public static IChatComponent component(String markerId, String givenName,
                                           String namedAfter) {
        String given = trim(givenName);
        if (given.length() == 0) {
            IChatComponent called = LostTalesMapMarkerNamedAfter.component(namedAfter);
            if (called != null) {
                return called;
            }
        }
        if (isNamedByLang(markerId, given)) {
            return new ChatComponentTranslation(nameKey(markerId));
        }
        return new ChatComponentText(given.length() > 0 ? given : trim(markerId));
    }

    /** The category word a marker of this kind is drawn with while it has none of its own. */
    public static String defaultCategoryKey(LostTalesMapMarkerSource source,
                                            boolean fastTravel) {
        if (source == LostTalesMapMarkerSource.PLAYER_CREATED) {
            return CATEGORY_KEY_PREFIX + "waystone";
        }
        return CATEGORY_KEY_PREFIX + (fastTravel ? "point_of_interest" : "map_marker");
    }

    /**
     * A marker's category as this side reads it: the words a player or an
     * operator gave it, else the word for its kind ({@code Waystone},
     * {@code Point of Interest}, {@code Map Marker}).
     */
    public static String shownCategory(String category,
                                       LostTalesMapMarkerSource source,
                                       boolean fastTravel) {
        String given = trim(category);
        return given.length() > 0 ? given : StatCollector.translateToLocal(
                defaultCategoryKey(source, fastTravel));
    }

    /**
     * The description every waystone a player placed has while they leave
     * it undescribed; empty for any other marker.
     */
    public static String defaultDescription(LostTalesMapMarkerSource source) {
        return source == LostTalesMapMarkerSource.PLAYER_CREATED
                ? StatCollector.translateToLocal(WAYSTONE_DESCRIPTION_KEY) : "";
    }

    /**
     * The English name a Lost Tales marker ships with, from the mod's
     * English lang file, for a marker file that leaves its name out;
     * empty where the file has no line.
     */
    public static String englishName(String markerId) {
        return english(isLostTalesPath(markerId) ? nameKey(markerId) : "");
    }

    /** The English description a Lost Tales marker ships with; empty where there is none. */
    public static String englishDescription(String markerId) {
        return english(descriptionKey(markerId));
    }

    private static String english(String key) {
        if (key.length() == 0) {
            return "";
        }
        String line = LostTalesLangFile.english().get(key);
        return line == null ? "" : line;
    }

    /** Whether the id is one of the mod's own markers: {@code losttales:} and a plain path. */
    private static boolean isLostTalesPath(String markerId) {
        String id = trim(markerId);
        return id.startsWith(LOST_TALES_PREFIX)
                && id.length() > LOST_TALES_PREFIX.length()
                && id.substring(LOST_TALES_PREFIX.length()).matches("[a-z0-9_]+");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
