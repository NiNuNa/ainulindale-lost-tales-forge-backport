package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.NumberStepper;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.StatCollector;

/**
 * The waystone page's rows: Marker, Location, Rules and Sharing,
 * each a section of Settings' own rows — typed lines and numbers between
 * chevrons as Settings' own kinds, few-word options whose words open in a
 * pick as every page's do (WIN-36), switches as Settings words them, rows
 * that act, and quiet rows that only read — made afresh from the page's
 * draft whenever they are asked for, the page's search keeping what holds
 * its words. A waystone the player may not edit shows every row greyed,
 * saying why.
 */
final class WaystoneRows {
    private static final String LANG = "gui.losttales.waystone.";

    /* Rows of the page's own, by id. */
    static final String DISCOVERABLE = "discoverable";
    static final String HIDDEN = "hidden";
    static final String REGION = "region";
    static final String FAST_TRAVEL = "fast_travel";
    static final String SHARE = "share";
    static final String UNSHARE = "unshare";
    static final String HAND_OVER = "hand_over";
    /** What the Share with pick offers. */
    private static final String CHARACTER = "character";
    private static final String FELLOWSHIP = "fellowship";
    /** The longest name a share is sent for, in characters: past any character's and fellowship's name. */
    static final int MAX_SHARE_NAME = 32;

    /** What the rows read from, and what the typed rows write to: the page. */
    interface Host {
        /** The draft the rows edit; null before a waystone is open. */
        WaystoneDraft draft();

        boolean canEdit();

        /** Whether a request is on its way and its answer not back. */
        boolean isWaiting();

        String markerId();

        /** The characters it is shared with. */
        int sharedCharacters();

        int sharedFellowships();

        boolean sharesWithFellowship();

        /** Shares with a fellowship, or with a character; a change empties the name typed. */
        void setSharesWithFellowship(boolean fellowship);

        String shareTarget();

        void setShareTarget(String name);

        /** The lore LOTR gives the waystone's place, which an empty description shows; empty for none. */
        String nativeLore();

        /**
         * The name an unnamed waystone is called by, its placer's
         * ({@code Nils's Waystone}); empty for one that must keep a name.
         */
        String defaultName();

        /** The word for the waystone's kind, which an empty category reads. */
        String defaultCategory();

        /** What an empty description reads: the place's lore, else the words for a player's waystone. */
        String defaultDescription();

        /** The characters, or the fellowships, whose names hold {@code typed}. */
        List<String> offers(String typed);

        /** The owning character's name as the server sent it; empty for none it could name. */
        String ownerName();

        /** Whether a player placed the waystone, rather than the server building it. */
        boolean isPlayerPlaced();

        /** Whether the reader holds {@code mapmarker.manage}: Public and Hand Over are theirs. */
        boolean managesMarkers();

        /** Whether the page still shows the waystone its rows were made for. */
        boolean stands();

        /** The draft's icon in its colour, as the map draws it; null for none. */
        MenuWindow.Picture iconPicture();

        /** A marker colour as the map shows it. */
        int colorRgb(String color);
    }

    private final Host host;
    private final int iconWidth;
    final Settings.Line name;
    final Settings.Line category;
    final Settings.Line description;
    final Settings.Line target;
    final Settings.Numeric compassRadius;
    final Settings.Numeric discoveryRadius;
    final Pick icon;
    final Pick color;
    final Pick relevance;
    final Pick access;
    final Pick shareKind;
    /** The typed rows' and the picks' settings, by the id their rows carry. */
    private final Map<String, Settings.Setting> typed =
            new HashMap<String, Settings.Setting>();

    /** Rows for {@code host}, a marker's icon previewed {@code iconWidth} across. */
    WaystoneRows(Host host, int iconWidth) {
        this.host = host;
        this.iconWidth = iconWidth;
        // An empty name, category or description stands for the words of
        // the waystone's kind, which the field reads as its prompt: they
        // are shown, never saved.
        this.name = new Typed("waystone_name", LANG + "name", false,
                LostTalesMapMarkerRecord.MAX_NAME_LENGTH) {
            @Override
            protected boolean mayBeEmpty() {
                return WaystoneRows.this.host.defaultName().length() > 0;
            }

            @Override
            protected String prompt() {
                return orLabel(WaystoneRows.this.host.defaultName());
            }

            @Override
            protected String emptyValue() {
                return orNone(WaystoneRows.this.host.defaultName());
            }

            @Override
            protected String get() {
                WaystoneDraft draft = draft();
                return draft == null ? "" : draft.name();
            }

            @Override
            protected void set(String text) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setName(text);
                }
            }
        };
        this.category = new Typed("waystone_category", LANG + "category",
                true, LostTalesMapMarkerRecord.MAX_NAME_LENGTH) {
            @Override
            protected String prompt() {
                return orLabel(WaystoneRows.this.host.defaultCategory());
            }

            @Override
            protected String emptyValue() {
                return orNone(WaystoneRows.this.host.defaultCategory());
            }

            @Override
            protected String get() {
                WaystoneDraft draft = draft();
                return draft == null ? "" : draft.category();
            }

            @Override
            protected void set(String text) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setCategory(text);
                }
            }
        };
        // An empty description reads the lore LOTR gives the place, and
        // typing that lore as it reads keeps the description empty.
        this.description = new Typed("waystone_description",
                LANG + "description", true,
                LostTalesMapMarkerRecord.MAX_TEXT_LENGTH) {
            @Override
            protected String prompt() {
                return orLabel(WaystoneRows.this.host.defaultDescription());
            }

            @Override
            protected String emptyValue() {
                return orNone(WaystoneRows.this.host.defaultDescription());
            }

            @Override
            protected String get() {
                WaystoneDraft draft = draft();
                return draft == null ? "" : draft.description();
            }

            @Override
            protected void set(String text) {
                WaystoneDraft draft = draft();
                if (draft == null) {
                    return;
                }
                String lore = WaystoneRows.this.host.nativeLore();
                String typedText = text == null ? "" : text;
                draft.setDescription(lore.length() > 0
                        && typedText.trim().equals(lore.trim())
                        ? "" : typedText);
            }
        };
        this.target = new Typed("waystone_share_target",
                LANG + "share_target.character", true, MAX_SHARE_NAME) {
            @Override
            public String label() {
                return word(WaystoneRows.this.host.sharesWithFellowship()
                        ? "share_target.fellowship" : "share_target.character");
            }

            @Override
            protected String get() {
                return WaystoneRows.this.host.shareTarget();
            }

            @Override
            protected void set(String text) {
                WaystoneRows.this.host.setShareTarget(text);
            }

            @Override
            protected List<String> offers(String typedText) {
                return WaystoneRows.this.host.offers(typedText);
            }
        };
        this.compassRadius = new Radius("waystone_compass_radius",
                LANG + "compass_radius", 0.0D) {
            @Override
            protected double get() {
                WaystoneDraft draft = draft();
                return draft == null ? 0.0D : draft.compassRadius();
            }

            @Override
            protected void set(double value) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setCompassRadius(value);
                }
            }
        };
        this.discoveryRadius = new Radius("waystone_discovery_radius",
                LANG + "radius", 1.0D) {
            /** A placed waystone's radius stops where the discovery scan does (WAY-15). */
            @Override
            protected double[] bounds() {
                return new double[] {1.0D,
                        LostTalesMapMarkerRecord.maxDiscoveryRadius(
                                WaystoneRows.this.host.isPlayerPlaced()
                                        ? LostTalesMapMarkerSource.PLAYER_CREATED
                                        : LostTalesMapMarkerSource.CUSTOM_PRESET)};
            }

            @Override
            protected double get() {
                WaystoneDraft draft = draft();
                return draft == null ? 1.0D : draft.discoveryRadius();
            }

            @Override
            protected void set(double value) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setDiscoveryRadius(value);
                }
            }
        };
        this.icon = new Pick("waystone_icon", LANG + "icon") {
            @Override
            protected List<String> ids() {
                WaystoneDraft draft = draft();
                return draft == null ? Collections.<String>emptyList()
                        : draft.icons();
            }

            @Override
            protected String current() {
                WaystoneDraft draft = draft();
                return draft == null ? "" : draft.icon();
            }

            @Override
            protected void take(String id) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setIcon(id);
                }
            }

            @Override
            protected String word(String id) {
                return iconWord(id);
            }
        };
        this.color = new Pick("waystone_color", LANG + "color") {
            @Override
            protected List<String> ids() {
                WaystoneDraft draft = draft();
                return draft == null ? Collections.<String>emptyList()
                        : draft.colors();
            }

            @Override
            protected String current() {
                WaystoneDraft draft = draft();
                return draft == null ? "" : draft.color();
            }

            @Override
            protected void take(String id) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setColor(id);
                }
            }

            @Override
            protected String word(String id) {
                return colorWord(id);
            }
        };
        this.relevance = new Pick("waystone_relevance", LANG + "relevance") {
            @Override
            protected List<String> ids() {
                List<String> ids = new ArrayList<String>();
                for (LostTalesMapMarkerRelevance level
                        : LostTalesMapMarkerRelevance.values()) {
                    ids.add(level.getSerializedName());
                }
                return ids;
            }

            @Override
            protected String current() {
                WaystoneDraft draft = draft();
                return draft == null ? ""
                        : draft.relevance().getSerializedName();
            }

            @Override
            protected void take(String id) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setRelevance(
                            LostTalesMapMarkerRelevance.fromSerializedName(id));
                }
            }

            @Override
            protected String word(String id) {
                return WaystoneRows.word("relevance." + id.replace('-', '_'));
            }
        };
        // Access offers Private and Public; a waystone that is not public
        // reads Shared while it has a share (WAY-14).
        this.access = new Pick("waystone_access", LANG + "visibility") {
            @Override
            protected List<String> ids() {
                return Arrays.asList(
                        LostTalesMapMarkerVisibility.PRIVATE.getSerializedName(),
                        LostTalesMapMarkerVisibility.PUBLIC.getSerializedName());
            }

            @Override
            protected String current() {
                WaystoneDraft draft = draft();
                return (draft != null && draft.isPublic()
                        ? LostTalesMapMarkerVisibility.PUBLIC
                        : LostTalesMapMarkerVisibility.PRIVATE)
                        .getSerializedName();
            }

            @Override
            protected void take(String id) {
                WaystoneDraft draft = draft();
                if (draft != null) {
                    draft.setPublic(LostTalesMapMarkerVisibility.PUBLIC
                                    .getSerializedName().equals(id),
                            WaystoneRows.this.host.managesMarkers());
                }
            }

            @Override
            protected String word(String id) {
                return WaystoneRows.word("visibility." + id);
            }

            @Override
            public String value() {
                WaystoneDraft draft = draft();
                return WaystoneRows.word("visibility."
                        + LostTalesMapMarkerRecord.access(
                                draft != null && draft.isPublic(),
                                WaystoneRows.this.host.sharedCharacters() > 0
                                        || WaystoneRows.this.host
                                                .sharedFellowships() > 0)
                                .getSerializedName());
            }
        };
        this.shareKind = new Pick("waystone_share_kind", LANG + "share_with") {
            @Override
            protected List<String> ids() {
                return Arrays.asList(CHARACTER, FELLOWSHIP);
            }

            @Override
            protected String current() {
                return WaystoneRows.this.host.sharesWithFellowship()
                        ? FELLOWSHIP : CHARACTER;
            }

            @Override
            protected void take(String id) {
                WaystoneRows.this.host.setSharesWithFellowship(
                        FELLOWSHIP.equals(id));
            }

            @Override
            protected String word(String id) {
                return WaystoneRows.word("share_target." + id);
            }
        };
    }

    private WaystoneDraft draft() {
        return this.host.draft();
    }

    /** The setting a row stands for, a line, a number or a few-word pick; null for any other row. */
    Settings.Setting typedFor(String rowId) {
        return this.typed.get(rowId);
    }

    /**
     * Every row for the draft as it stands, under the four sections'
     * headers, with what the search's {@code words} keep of them; none
     * before a waystone is open.
     */
    List<MenuWindow.Entry> build(String words) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        WaystoneDraft draft = draft();
        if (draft == null) {
            return rows;
        }
        String readOnly = this.host.canEdit() ? "" : word("read_only");
        PageRows.addSection(rows, word("section.marker"),
                marker(draft, readOnly), words);
        PageRows.addSection(rows, word("section.location"),
                location(draft, readOnly), words);
        PageRows.addSection(rows, word("section.rules"),
                rules(draft, readOnly), words);
        PageRows.addSection(rows, word("section.sharing"),
                sharing(readOnly), words);
        return rows;
    }

    private List<MenuWindow.Entry> marker(WaystoneDraft draft,
                                          String readOnly) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(typedRow(this.name, readOnly));
        rows.add(pickRow(this.icon, readOnly)
                .withValuePicture(this.host.iconPicture(), this.iconWidth));
        rows.add(pickRow(this.color, readOnly)
                .withValueChip(this.host.colorRgb(draft.color())));
        rows.add(typedRow(this.category, readOnly));
        rows.add(typedRow(this.description, readOnly));
        return rows;
    }

    private List<MenuWindow.Entry> location(WaystoneDraft draft,
                                            String readOnly) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(typedRow(this.compassRadius, readOnly));
        rows.add(typedRow(this.discoveryRadius, readOnly));
        rows.add(pickRow(this.relevance, readOnly));
        return rows;
    }

    private List<MenuWindow.Entry> rules(WaystoneDraft draft,
                                         String readOnly) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(new MenuWindow.Entry(DISCOVERABLE, word("discoverable"))
                .withValue(Settings.onOff(draft.isDiscoverable()))
                .unavailable(readOnly));
        rows.add(new MenuWindow.Entry(HIDDEN, word("hidden"))
                .withValue(Settings.onOff(draft.isHidden()))
                .unavailable(first(readOnly, draft.isDiscoverable() ? ""
                        : word("why.hidden"))));
        rows.add(new MenuWindow.Entry(REGION, word("region"))
                .withValue(Settings.onOff(draft.requiresRegion()))
                .unavailable(readOnly));
        rows.add(new MenuWindow.Entry(FAST_TRAVEL, word("fast_travel"))
                .withValue(Settings.onOff(draft.hasFastTravel()))
                .unavailable(readOnly));
        rows.add(pickRow(this.access, first(readOnly, draft.isPublic()
                || draft.mayBePublic(this.host.managesMarkers())
                ? "" : word("why.public"))));
        rows.add(quiet(word("owner"), ownerWord()));
        rows.add(quiet(word("marker_id"), this.host.markerId()));
        return rows;
    }

    /**
     * Who owns the waystone, as its Owner row reads: the owning
     * character's name; for a generated waystone, no one; for a placed one
     * whose owner the server could not name, an unknown character.
     */
    private String ownerWord() {
        String owner = this.host.ownerName();
        if (owner != null && owner.trim().length() > 0) {
            return owner.trim();
        }
        return word(this.host.isPlayerPlaced() ? "owner.unknown"
                : "owner.none");
    }

    private List<MenuWindow.Entry> sharing(String readOnly) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(pickRow(this.shareKind, readOnly));
        rows.add(typedRow(this.target, readOnly));
        String why = first(readOnly,
                this.host.shareTarget().trim().length() == 0
                        ? word("why.no_target") : "",
                this.host.isWaiting() ? word("why.waiting") : "");
        rows.add(new MenuWindow.Entry(SHARE, word("share"))
                .unavailable(why));
        rows.add(new MenuWindow.Entry(UNSHARE, word("unshare"))
                .unavailable(why));
        if (this.host.managesMarkers()) {
            // Hand Over is a manager's alone, and names a character
            // (WAY-08); a generated waystone belongs to nobody.
            rows.add(new MenuWindow.Entry(HAND_OVER, word("hand_over"))
                    .unavailable(first(readOnly,
                            this.host.isPlayerPlaced() ? ""
                                    : word("why.hand_over_generated"),
                            this.host.sharesWithFellowship()
                                    ? word("why.hand_over_character") : "",
                            why)));
        }
        rows.add(quiet(word("shared.characters"),
                String.valueOf(this.host.sharedCharacters())));
        rows.add(quiet(word("shared.fellowships"),
                String.valueOf(this.host.sharedFellowships())));
        return rows;
    }

    /**
     * A typed setting's row as Settings makes it; greyed where the
     * waystone cannot be edited, a number's chevrons then flat.
     */
    private MenuWindow.Entry typedRow(Settings.Setting setting,
                                      String readOnly) {
        MenuWindow.Entry row = setting.row();
        this.typed.put(row.id, setting);
        if (readOnly.length() == 0) {
            return row;
        }
        if (setting instanceof Settings.Numeric) {
            NumberStepper stepper = ((Settings.Numeric)setting).stepper();
            row.withStepper(false, false, "", stepper.format(stepper.min),
                    stepper.format(stepper.max));
        }
        return row.unavailable(readOnly);
    }

    /**
     * A few-word option's row as Settings makes it, its words opening in
     * a pick once it is taken; greyed with {@code why} where it cannot be.
     */
    private MenuWindow.Entry pickRow(Pick setting, String why) {
        MenuWindow.Entry row = setting.row();
        this.typed.put(row.id, setting);
        return row.unavailable(why);
    }

    /** A quiet row that only reads: its name in the aside tone and its value, never taken. */
    private static MenuWindow.Entry quiet(String label, String value) {
        return MenuWindow.Entry.passive(label).withValue(value)
                .withLabelColor(WindowStyle.asideRgb());
    }

    /** An icon's name as the page words it; one it has no words for, as it is kept. */
    static String iconWord(String icon) {
        return known(LANG + "icon.", icon);
    }

    /** A colour's name as the page words it; a colour written as a number, as it is. */
    static String colorWord(String color) {
        return known(LANG + "color.", color);
    }

    private static String known(String prefix, String id) {
        String value = id == null ? "" : id.trim();
        String key = prefix + value.toLowerCase(Locale.ROOT);
        return value.length() > 0 && StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : value;
    }

    static String word(String key) {
        return StatCollector.translateToLocal(LANG + key);
    }

    /** The first reason given; empty while none is. */
    private static String first(String... reasons) {
        for (String reason : reasons) {
            if (reason != null && reason.length() > 0) {
                return reason;
            }
        }
        return "";
    }

    /* ---- The typed settings ---- */

    /** {@code words}, or the row's name where there are none. */
    private static String orLabelOr(String words, String label) {
        return words == null || words.trim().length() == 0 ? label : words;
    }

    /**
     * A line of the waystone's, typed into Settings' own field beside
     * the row: kept by the page until Save ({@link Settings.Store#NONE}),
     * and gone with the waystone the page shows.
     */
    private abstract class Typed extends Settings.Line {
        private final boolean mayBeEmpty;
        private final int maxLength;

        Typed(String key, String labelKey, boolean mayBeEmpty,
              int maxLength) {
            super(key, labelKey);
            this.mayBeEmpty = mayBeEmpty;
            this.maxLength = maxLength;
        }

        @Override
        protected int maxLength() {
            return this.maxLength;
        }

        @Override
        protected boolean mayBeEmpty() {
            return this.mayBeEmpty;
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return WaystoneRows.this.host.stands();
        }

        /** {@code words}, or the row's name where there are none: what the field reads empty. */
        String orLabel(String words) {
            return orLabelOr(words, label());
        }

        /** {@code words}, cut as a row reads words, or None where there are none: what the row reads empty. */
        String orNone(String words) {
            return words == null || words.trim().length() == 0
                    ? super.emptyValue() : shown(words.trim());
        }
    }

    /**
     * A few-word option of the waystone's: its words open in a pick named
     * after it, the one in force lit, and a word picked stands at once on
     * the page until Save (WIN-36). No word is marked as the default, as
     * the waystone's saved value is what it goes back to.
     */
    abstract class Pick extends Settings.Setting {
        Pick(String key, String labelKey) {
            super(key, labelKey);
        }

        /** The words' ids, in the order the pick lists them. */
        protected abstract List<String> ids();

        /** The id in force. */
        protected abstract String current();

        protected abstract void take(String id);

        /** An id as the pick and the row read it. */
        protected abstract String word(String id);

        @Override
        public String value() {
            return word(current());
        }

        @Override
        public List<String> words() {
            List<String> words = new ArrayList<String>();
            for (String id : ids()) {
                words.add(word(id));
            }
            return words;
        }

        @Override
        public int wordIndex() {
            return WaystoneDraft.indexOf(ids(), current());
        }

        @Override
        public void pickWord(int index) {
            List<String> ids = ids();
            if (index >= 0 && index < ids.size()) {
                take(ids.get(index));
            }
        }

        @Override
        public void restore() {}

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return WaystoneRows.this.host.stands();
        }
    }

    /**
     * A radius in whole blocks between its chevrons, a step at a time or
     * ten with Shift, from {@code min} to the largest a marker keeps; no
     * default, since the waystone's saved value is what it goes back to.
     */
    private abstract class Radius extends Settings.Numeric {
        private final double min;

        Radius(String key, String labelKey, double min) {
            super(key, labelKey, 1.0D, 0);
            this.min = min;
        }

        @Override
        protected double[] bounds() {
            return new double[] {this.min,
                    LostTalesMapMarkerRecord.MAX_RADIUS};
        }

        @Override
        protected double shippedNumber() {
            return Double.NaN;
        }

        @Override
        public Settings.Store store() {
            return Settings.Store.NONE;
        }

        @Override
        public boolean stands() {
            return WaystoneRows.this.host.stands();
        }
    }
}
