package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import java.util.EnumSet;

/**
 * What the waystone page holds between the server's state and Save: each
 * setting the page edits, as the player has set it, over the settings it
 * was read from. It says which of them changed, which is what lights
 * Save; keeps the player's edits when newer settings arrive; and makes
 * the settings Save sends. The waystone's place, structure and link are
 * the settings' own and are never edited here.
 */
public final class WaystoneDraft {
    /** Every setting the page edits. */
    public enum Field {
        NAME, ICON, COLOR, CATEGORY, DESCRIPTION, COMPASS_RADIUS,
        DISCOVERY_RADIUS, RELEVANCE, DISCOVERABLE, HIDDEN, REGION,
        FAST_TRAVEL, VISIBILITY
    }

    /** The icons a waystone's marker is given from, in the order a step walks them. */
    public static final String[] ICONS = {
        "quest", "hostile", "undiscovered", "point_of_interest",
        "personal", "shack", "graveyard", "forest", "mountains",
        "port", "big_port", "bridge", "small_bridge", "camp"
    };

    /** The colours a waystone's marker is given from, in the order a step walks them. */
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
    private LostTalesMapMarkerVisibility visibility;

    public WaystoneDraft(LostTalesMapMarkerEditableSettings base) {
        reset(base);
    }

    /** The settings it was read from, or last moved onto. */
    public LostTalesMapMarkerEditableSettings base() {
        return this.base;
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
        this.visibility = settings.getVisibility();
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
        LostTalesMapMarkerVisibility keptVisibility = this.visibility;
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
            this.visibility = keptVisibility;
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
        if (this.visibility != from.getVisibility()) {
            fields.add(Field.VISIBILITY);
        }
        return fields;
    }

    /** The settings Save sends: the player's, on the waystone's own place, structure and link. */
    public LostTalesMapMarkerEditableSettings toSettings() {
        LostTalesMapMarkerEditableSettings from = this.base;
        return new LostTalesMapMarkerEditableSettings(
                this.name, this.icon, this.color, this.category,
                this.description, this.fastTravel,
                from.getDimensionId(), from.getX(), from.getY(),
                from.getZ(), this.compassRadius, this.discoveryRadius,
                this.hidden, this.discoverable, this.region,
                from.hasWaystone(), from.getWaystoneStructureType(),
                this.priority, this.visibility);
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
    public LostTalesMapMarkerVisibility visibility() {
        return this.visibility;
    }

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

    /** The next icon, or with {@code back} the one before; one off the list steps onto it. */
    public void stepIcon(boolean back) {
        this.icon = step(ICONS, this.icon, back);
    }

    /** The next colour, or with {@code back} the one before. */
    public void stepColor(boolean back) {
        this.color = step(COLORS, this.color, back);
    }

    /** The next relevance up, or with {@code back} down, round from either end. */
    public void stepRelevance(boolean back) {
        LostTalesMapMarkerRelevance[] all =
                LostTalesMapMarkerRelevance.values();
        this.priority = all[next(relevance().ordinal(), all.length, back)]
                .getRank();
    }

    /**
     * The next access: private, shared, and public only where the player
     * may make a waystone public; one standing public for a player who may
     * not steps back into the other two.
     */
    public void stepVisibility(boolean back, boolean canMakePublic) {
        LostTalesMapMarkerVisibility[] all = canMakePublic
                ? LostTalesMapMarkerVisibility.values()
                : new LostTalesMapMarkerVisibility[] {
                        LostTalesMapMarkerVisibility.PRIVATE,
                        LostTalesMapMarkerVisibility.SHARED};
        int index = -1;
        for (int at = 0; at < all.length; at++) {
            if (all[at] == this.visibility) {
                index = at;
            }
        }
        this.visibility = all[next(index, all.length, back)];
    }

    /** The word after {@code current} in {@code words}, or before it; a word off the list steps onto the first or the last. */
    static String step(String[] words, String current, boolean back) {
        int index = -1;
        for (int at = 0; at < words.length; at++) {
            if (words[at].equalsIgnoreCase(current == null ? ""
                    : current.trim())) {
                index = at;
            }
        }
        return words[next(index, words.length, back)];
    }

    /** The index a step lands on among {@code count}, round from either end; from none, the first or the last. */
    private static int next(int index, int count, boolean back) {
        if (index < 0) {
            return back ? count - 1 : 0;
        }
        return ((back ? index - 1 : index + 1) % count + count) % count;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
