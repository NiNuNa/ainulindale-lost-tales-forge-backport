package com.ninuna.losttales.progression.deed;

/**
 * A deed as the Skills page shows it (PROG-13): what it is called and
 * asks, how far it goes, and whether it brings a LOTR title. What counts
 * toward it stays with the server.
 */
public final class DeedSummary {
    private final String id;
    private final String category;
    private final String icon;
    private final DeedDefinition.Kind kind;
    private final long target;
    private final boolean title;
    private final String name;
    private final String description;

    public DeedSummary(String id, String category, String icon, DeedDefinition.Kind kind,
                       long target, boolean title, String name, String description) {
        this.id = id == null ? "" : id;
        this.category = category == null ? "" : category;
        this.icon = icon == null ? "" : icon;
        this.kind = kind;
        this.target = target;
        this.title = title;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
    }

    public static DeedSummary of(DeedDefinition deed) {
        return new DeedSummary(deed.getId(), deed.getCategory(), deed.getIcon(), deed.getKind(),
                deed.target(), deed.hasTitle(), deed.getName(), deed.getDescription());
    }

    public String getId() {
        return this.id;
    }

    public String getCategory() {
        return this.category;
    }

    public String getIcon() {
        return this.icon;
    }

    public DeedDefinition.Kind getKind() {
        return this.kind;
    }

    public long getTarget() {
        return this.target;
    }

    public boolean hasTitle() {
        return this.title;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }
}
