package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * What the waystone page holds between the server's state and Save: each
 * setting the page edits, as the player has set it, over the settings it
 * was read from. It says which of them changed, which is what lights
 * Save; keeps the player's edits when newer settings arrive; and makes
 * the settings Save sends. Access is public or not: a waystone that is
 * not public reads as shared or private by its shares alone (WAY-14), so
 * the draft keeps only whether it is public.
 */
public final class WaystoneDraft {
    /** Every setting the page edits. */
    public enum Field {
        NAME, ICON, COLOR, CATEGORY, DESCRIPTION, COMPASS_RADIUS,
        DISCOVERY_RADIUS, RELEVANCE, DISCOVERABLE, HIDDEN, REGION,
        FAST_TRAVEL, VISIBILITY
    }

    /** The icons a waystone's marker is given from, in the order its pick lists them. */
    public static final String[] ICONS = {
        "quest", "hostile", "undiscovered", "point_of_interest",
        "personal", "shack", "graveyard", "forest", "mountains",
        "port", "big_port", "bridge", "small_bridge", "camp"
    };

    /** The colours a waystone's marker is given from, in the order its pick lists them. */
    public static final String[] COLORS = {
        "white", "red", "green", "blue", "yellow", "gold",
        "orange", "purple", "violet", "gray", "dark_gray", "black"
    };

    private LostTalesMapMarkerEditableSettings base;
    private String name;
    private String icon;
    private String color;
    private String category;
    private String description;
    private double compassRadius;
    private double discoveryRadius;
    private int priority;
    private boolean discoverable;
    private boolean hidden;
    private boolean region;
    private boolean fastTravel;
    private boolean publicAccess;

    public WaystoneDraft(LostTalesMapMarkerEditableSettings base) {
        reset(base);
    }

    /** Starts over from {@code settings}: every edit goes. */
    public void reset(LostTalesMapMarkerEditableSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("a draft needs settings");
        }
        this.base = settings;
        this.name = settings.getName();
        this.icon = settings.getIconName();
        this.color = settings.getColorName();
        this.category = settings.getCategoryName();
        this.description = settings.getDescription();
        this.compassRadius = settings.getCompassFadeInRadius();
        this.discoveryRadius = settings.getDiscoveryRadius();
        this.priority = settings.getPriority();
        this.discoverable = settings.isDiscoverable();
        this.hidden = settings.isHiddenUntilDiscovered();
        this.region = settings.requiresRegionUnlock();
        this.fastTravel = settings.hasFastTravel();
        this.publicAccess = isPublic(settings);
    }

    /**
     * Moves the draft onto {@code newer}: each setting still as it stood
     * in {@code reference} takes the newer value, and each the player
     * changed from it keeps the player's. A waystone kept hidden stays
     * discoverable.
     */
    public void rebase(LostTalesMapMarkerEditableSettings reference,
                       LostTalesMapMarkerEditableSettings newer) {
        if (reference == null || newer == null) {
            throw new IllegalArgumentException("a rebase needs settings");
        }
        EnumSet<Field> edited = differences(reference);
        String keptName = this.name;
        String keptIcon = this.icon;
        String keptColor = this.color;
        String keptCategory = this.category;
        String keptDescription = this.description;
        double keptCompass = this.compassRadius;
        double keptDiscovery = this.discoveryRadius;
        int keptPriority = this.priority;
        boolean keptDiscoverable = this.discoverable;
        boolean keptHidden = this.hidden;
        boolean keptRegion = this.region;
        boolean keptFastTravel = this.fastTravel;
        boolean keptPublic = this.publicAccess;
        reset(newer);
        if (edited.contains(Field.NAME)) {
            this.name = keptName;
        }
        if (edited.contains(Field.ICON)) {
            this.icon = keptIcon;
        }
        if (edited.contains(Field.COLOR)) {
            this.color = keptColor;
        }
        if (edited.contains(Field.CATEGORY)) {
            this.category = keptCategory;
        }
        if (edited.contains(Field.DESCRIPTION)) {
            this.description = keptDescription;
        }
        if (edited.contains(Field.COMPASS_RADIUS)) {
            this.compassRadius = keptCompass;
        }
        if (edited.contains(Field.DISCOVERY_RADIUS)) {
            this.discoveryRadius = keptDiscovery;
        }
        if (edited.contains(Field.RELEVANCE)) {
            this.priority = keptPriority;
        }
        if (edited.contains(Field.DISCOVERABLE)) {
            this.discoverable = keptDiscoverable;
        }
        if (edited.contains(Field.HIDDEN)) {
            this.hidden = keptHidden;
        }
        if (edited.contains(Field.REGION)) {
            this.region = keptRegion;
        }
        if (edited.contains(Field.FAST_TRAVEL)) {
            this.fastTravel = keptFastTravel;
        }
        if (edited.contains(Field.VISIBILITY)) {
            this.publicAccess = keptPublic;
        }
        if (!this.discoverable) {
            this.hidden = false;
        }
    }

    /** The settings the player changed from the ones it was read from: what lights Save. */
    public EnumSet<Field> changed() {
        return differences(this.base);
    }

    public boolean isChanged() {
        return !changed().isEmpty();
    }

    private EnumSet<Field> differences(
            LostTalesMapMarkerEditableSettings from) {
        EnumSet<Field> fields = EnumSet.noneOf(Field.class);
        if (!this.name.equals(from.getName())) {
            fields.add(Field.NAME);
        }
        if (!this.icon.equals(from.getIconName())) {
            fields.add(Field.ICON);
        }
        if (!this.color.equals(from.getColorName())) {
            fields.add(Field.COLOR);
        }
        if (!this.category.equals(from.getCategoryName())) {
            fields.add(Field.CATEGORY);
        }
        if (!this.description.equals(from.getDescription())) {
            fields.add(Field.DESCRIPTION);
        }
        if (Double.compare(this.compassRadius,
                from.getCompassFadeInRadius()) != 0) {
            fields.add(Field.COMPASS_RADIUS);
        }
        if (Double.compare(this.discoveryRadius,
                from.getDiscoveryRadius()) != 0) {
            fields.add(Field.DISCOVERY_RADIUS);
        }
        if (this.priority != from.getPriority()) {
            fields.add(Field.RELEVANCE);
        }
        if (this.discoverable != from.isDiscoverable()) {
            fields.add(Field.DISCOVERABLE);
        }
        if (this.hidden != from.isHiddenUntilDiscovered()) {
            fields.add(Field.HIDDEN);
        }
        if (this.region != from.requiresRegionUnlock()) {
            fields.add(Field.REGION);
        }
        if (this.fastTravel != from.hasFastTravel()) {
            fields.add(Field.FAST_TRAVEL);
        }
        if (this.publicAccess != isPublic(from)) {
            fields.add(Field.VISIBILITY);
        }
        return fields;
    }

    /** The settings Save sends: every one as the player has set it. */
    public LostTalesMapMarkerEditableSettings toSettings() {
        return new LostTalesMapMarkerEditableSettings(
                this.name, this.icon, this.color, this.category,
                this.description, this.fastTravel,
                this.compassRadius, this.discoveryRadius,
                this.hidden, this.discoverable, this.region,
                this.priority, this.publicAccess
                        ? LostTalesMapMarkerVisibility.PUBLIC
                        : LostTalesMapMarkerVisibility.PRIVATE);
    }

    /* ---- The settings ---- */

    public String name() { return this.name; }
    public String icon() { return this.icon; }
    public String color() { return this.color; }
    public String category() { return this.category; }
    public String description() { return this.description; }
    public double compassRadius() { return this.compassRadius; }
    public double discoveryRadius() { return this.discoveryRadius; }
    public LostTalesMapMarkerRelevance relevance() {
        return LostTalesMapMarkerRelevance.fromRank(this.priority);
    }
    public boolean isDiscoverable() { return this.discoverable; }
    public boolean isHidden() { return this.hidden; }
    public boolean requiresRegion() { return this.region; }
    public boolean hasFastTravel() { return this.fastTravel; }
    /** Whether the waystone is to be public; one that is not reads as shared or private by its shares. */
    public boolean isPublic() { return this.publicAccess; }

    public void setName(String name) {
        this.name = text(name);
    }

    public void setCategory(String category) {
        this.category = text(category);
    }

    public void setDescription(String description) {
        this.description = text(description);
    }

    public void setCompassRadius(double radius) {
        this.compassRadius = radius;
    }

    public void setDiscoveryRadius(double radius) {
        this.discoveryRadius = radius;
    }

    /** A waystone that cannot be discovered is never hidden until it is. */
    public void setDiscoverable(boolean on) {
        this.discoverable = on;
        if (!on) {
            this.hidden = false;
        }
    }

    /** Hidden until discovered, which only a discoverable waystone can be. */
    public void setHidden(boolean on) {
        this.hidden = on && this.discoverable;
    }

    public void setRequiresRegion(boolean on) {
        this.region = on;
    }

    public void setFastTravel(boolean on) {
        this.fastTravel = on;
    }

    /**
     * The icons the icon's pick offers: the fourteen, after the icon the
     * waystone was read with where it is none of them, so picking another
     * never loses it.
     */
    public List<String> icons() {
        return offered(ICONS, this.base.getIconName());
    }

    /** The colours the colour's pick offers: the twelve, after the waystone's own where it is none of them. */
    public List<String> colors() {
        return offered(COLORS, this.base.getColorName());
    }

    public void setIcon(String icon) {
        this.icon = text(icon);
    }

    public void setColor(String color) {
        this.color = text(color);
    }

    /** The level picked; picking the level the waystone reads keeps its exact rank. */
    public void setRelevance(LostTalesMapMarkerRelevance level) {
        if (level != null && level != relevance()) {
            this.priority = level.getRank();
        }
    }

    /**
     * Public or private, as picked. Only a player who manages markers
     * makes a waystone public; one that is public already may be picked
     * public again whoever edits it, since saving it public again needs
     * nothing.
     */
    public void setPublic(boolean on, boolean managesMarkers) {
        if (!on) {
            this.publicAccess = false;
        } else if (mayBePublic(managesMarkers)) {
            this.publicAccess = true;
        }
    }

    /** Whether the draft may be public: for a manager, or a waystone saved public already. */
    public boolean mayBePublic(boolean managesMarkers) {
        return managesMarkers || isPublic(this.base);
    }

    private static boolean isPublic(
            LostTalesMapMarkerEditableSettings settings) {
        return settings.getVisibility() == LostTalesMapMarkerVisibility.PUBLIC;
    }

    /** {@code words}, after {@code own} where it is none of them. */
    private static List<String> offered(String[] words, String own) {
        List<String> offered = new ArrayList<String>(words.length + 1);
        String value = own == null ? "" : own.trim();
        if (value.length() > 0 && indexOf(Arrays.asList(words), value) < 0) {
            offered.add(value);
        }
        offered.addAll(Arrays.asList(words));
        return offered;
    }

    /** Where {@code word} stands among {@code words}, whatever its case; -1 for nowhere. */
    static int indexOf(List<String> words, String word) {
        String wanted = word == null ? "" : word.trim();
        for (int at = 0; at < words.size(); at++) {
            if (words.get(at).equalsIgnoreCase(wanted)) {
                return at;
            }
        }
        return -1;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
