package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.Locale;
import java.util.UUID;

/** Persistent server-authoritative record for one roleplaying character. */
public class RoleplayCharacter {

    public static final int CURRENT_DATA_VERSION = 11;
    public static final boolean DEFAULT_SHOW_MINECRAFT_CAPE = true;
    public static final String DEFAULT_COSMETIC_CAPE_ID = CharacterCapeCatalog.NONE_ID;
    /** The longest LOTR title a record keeps. */
    public static final int MAX_LOTR_TITLE_LENGTH = 64;

    private final UUID characterId;
    private final UUID ownerId;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final CharacterProfile profile;
    private final int age;
    private final String startingFactionId;
    private final String startingWaypointId;
    private final boolean unconventionalSettings;
    private final long creationTimestamp;

    private boolean showMinecraftCape;
    /** The cape file's id; empty for none. */
    private String cosmeticCapeId;
    /** The body its race stood on when it was last known; it stands on it while the race is gone. */
    private CharacterBody body;
    /** The faction LOTR says the character is pledged to; empty while it has no pledge. */
    private String pledgedFactionId;
    /** When the character's faction became its faction: its making, or its latest pledge. */
    private long factionSince;
    /** The LOTR title the character wore when it was last played, by its lang key; empty for none. */
    private String lotrTitle;

    /** A builder for a character with these ids; everything else defaults. */
    public static Builder builder(UUID characterId, UUID ownerId) {
        return new Builder(characterId, ownerId);
    }

    /** A builder holding every field of {@code source}, to change some. */
    public static Builder builder(RoleplayCharacter source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return new Builder(source.characterId, source.ownerId)
                .slot(source.slotIndex).name(source.name).race(source.raceId)
                .gender(source.genderId).skin(source.skinId).age(source.age)
                .startingFaction(source.startingFactionId)
                .pledgedFaction(source.pledgedFactionId)
                .factionSince(source.factionSince)
                .lotrTitle(source.lotrTitle)
                .createdAt(source.creationTimestamp)
                .minecraftCapeVisible(source.showMinecraftCape)
                .cosmeticCape(source.cosmeticCapeId)
                .body(source.body)
                .startingWaypoint(source.startingWaypointId)
                .unconventionalSettings(source.unconventionalSettings)
                .profile(source.profile)
                .bodyType(source.bodyTypeId)
                .chestType(source.chestTypeId);
    }

    private RoleplayCharacter(Builder builder) {
        this.characterId = builder.characterId;
        this.ownerId = builder.ownerId;
        this.slotIndex = builder.slotIndex;
        this.name = builder.name;
        this.raceId = builder.raceId;
        this.genderId = builder.genderId;
        this.skinId = builder.skinId;
        this.bodyTypeId = CharacterBodyTypeRegistry.contains(builder.bodyTypeId)
                ? LostTalesIdentifiers.normalize(builder.bodyTypeId)
                : CharacterBodyTypeRegistry.defaultFor(builder.genderId);
        this.chestTypeId = CharacterChestTypeRegistry.contains(builder.chestTypeId)
                ? LostTalesIdentifiers.normalize(builder.chestTypeId)
                : CharacterChestTypeRegistry.defaultFor(builder.genderId);
        this.profile = builder.profile;
        this.age = builder.age;
        this.startingFactionId = builder.startingFactionId;
        this.startingWaypointId = builder.startingWaypointId;
        this.unconventionalSettings = builder.unconventionalSettings;
        this.pledgedFactionId = normalizeFactionId(builder.pledgedFactionId);
        this.creationTimestamp = Math.max(0L, builder.creationTimestamp);
        // A faction is never the character's before the character is: a
        // figure claimed again counts from its claim.
        this.factionSince = Math.max(builder.factionSince,
                this.creationTimestamp);
        this.lotrTitle = normalizeTitle(builder.lotrTitle);
        this.showMinecraftCape = builder.showMinecraftCape;
        this.cosmeticCapeId = builder.cosmeticCapeId;
        this.body = builder.body != null ? builder.body : bodyOf(builder.raceId);
    }

    /** The body a race stands on, Human's for a race the list does not hold. */
    private static CharacterBody bodyOf(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        return race == null ? CharacterBody.HUMAN : race.getBody();
    }

    /**
     * Every field a character holds, with the default a character made
     * without it gets: the body and chest types follow the sex, the capes
     * are the catalogue's defaults, and there is no pledge and no title.
     */
    public static final class Builder {
        private final UUID characterId;
        private UUID ownerId;
        private int slotIndex;
        private String name = "";
        private String raceId = "";
        private String genderId = "";
        private String skinId = "";
        private int age;
        private String startingFactionId = "";
        private String pledgedFactionId = "";
        private long creationTimestamp;
        private long factionSince;
        private String lotrTitle = "";
        private boolean showMinecraftCape = DEFAULT_SHOW_MINECRAFT_CAPE;
        private String cosmeticCapeId = DEFAULT_COSMETIC_CAPE_ID;
        private CharacterBody body;
        private String startingWaypointId = "";
        private boolean unconventionalSettings;
        private CharacterProfile profile = CharacterProfile.EMPTY;
        private String bodyTypeId;
        private String chestTypeId;

        private Builder(UUID characterId, UUID ownerId) {
            if (characterId == null) {
                throw new IllegalArgumentException("characterId must not be null");
            }
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId must not be null");
            }
            this.characterId = characterId;
            this.ownerId = ownerId;
        }

        /** The account the character belongs to, when it changes hands. */
        public Builder owner(UUID ownerId) {
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId must not be null");
            }
            this.ownerId = ownerId;
            return this;
        }

        public Builder slot(int slotIndex) {
            this.slotIndex = slotIndex;
            return this;
        }

        public Builder name(String name) {
            this.name = name == null ? "" : name;
            return this;
        }

        public Builder race(String raceId) {
            this.raceId = raceId == null ? "" : raceId;
            return this;
        }

        public Builder gender(String genderId) {
            this.genderId = genderId == null ? "" : genderId;
            return this;
        }

        public Builder skin(String skinId) {
            this.skinId = skinId == null ? "" : skinId;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder startingFaction(String startingFactionId) {
            this.startingFactionId = startingFactionId == null
                    ? "" : startingFactionId;
            return this;
        }

        /** The faction LOTR says the character is pledged to; null or empty for none. */
        public Builder pledgedFaction(String pledgedFactionId) {
            this.pledgedFactionId = pledgedFactionId == null
                    ? "" : pledgedFactionId;
            return this;
        }

        /** When the faction became the character's; 0, or a time before its making, for its making. */
        public Builder factionSince(long factionSince) {
            this.factionSince = factionSince;
            return this;
        }

        /** The LOTR title the character last wore; null or empty for none. */
        public Builder lotrTitle(String lotrTitle) {
            this.lotrTitle = lotrTitle == null ? "" : lotrTitle;
            return this;
        }

        public Builder createdAt(long creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            return this;
        }

        public Builder minecraftCapeVisible(boolean showMinecraftCape) {
            this.showMinecraftCape = showMinecraftCape;
            return this;
        }

        /** The cape file's id; null or empty for none. */
        public Builder cosmeticCape(String cosmeticCapeId) {
            this.cosmeticCapeId = cosmeticCapeId == null ? "" : cosmeticCapeId;
            return this;
        }

        /** The body the race stands on; null is the race's own, Human's for a race not known. */
        public Builder body(CharacterBody body) {
            this.body = body;
            return this;
        }

        public Builder startingWaypoint(String startingWaypointId) {
            this.startingWaypointId = startingWaypointId == null
                    ? "" : startingWaypointId;
            return this;
        }

        public Builder unconventionalSettings(boolean unconventionalSettings) {
            this.unconventionalSettings = unconventionalSettings;
            return this;
        }

        /** What the character says about itself; null is nothing. */
        public Builder profile(CharacterProfile profile) {
            this.profile = profile == null ? CharacterProfile.EMPTY : profile;
            return this;
        }

        /** Null, or an id the registry does not hold, is the sex's default. */
        public Builder bodyType(String bodyTypeId) {
            this.bodyTypeId = bodyTypeId;
            return this;
        }

        /** Null, or an id the registry does not hold, is the sex's default. */
        public Builder chestType(String chestTypeId) {
            this.chestTypeId = chestTypeId;
            return this;
        }

        public RoleplayCharacter build() {
            return new RoleplayCharacter(this);
        }
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public String getName() {
        return this.name;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getGenderId() {
        return this.genderId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    /** Arm width of the body, one of {@link CharacterBodyTypeRegistry}; stored apart from sex. */
    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    /** Chest shape and size, one of {@link CharacterChestTypeRegistry}; stored apart from sex. */
    public String getChestTypeId() {
        return this.chestTypeId;
    }

    /** What the character says about itself: its About texts, facts and glances. */
    public CharacterProfile getProfile() {
        return this.profile;
    }

    public int getAge() {
        return this.age;
    }

    /** The faction the character chose when it was made. */
    public String getStartingFactionId() {
        return this.startingFactionId;
    }

    /** The faction LOTR says the character is pledged to; empty while it has no pledge. */
    public String getPledgedFactionId() {
        return this.pledgedFactionId;
    }

    /**
     * The character's faction: its LOTR pledge while it has one, else its
     * starting faction. Its chat colour, its Faction Chat and the people
     * its title names all follow this.
     */
    public String getFactionId() {
        return factionOf(this.pledgedFactionId, this.startingFactionId);
    }

    /** The pledge when there is one, else the starting faction. */
    public static String factionOf(String pledgedFactionId,
                                   String startingFactionId) {
        String pledged = normalizeFactionId(pledgedFactionId);
        if (pledged.length() > 0) {
            return pledged;
        }
        return startingFactionId == null ? "" : startingFactionId;
    }

    /**
     * Keeps the pledge LOTR reports for this character, read from the
     * player data of the one being played. Answers whether it changed.
     */
    public boolean setPledgedFactionId(String pledgedFactionId, long now) {
        String normalized = normalizeFactionId(pledgedFactionId);
        if (normalized.equals(this.pledgedFactionId)) {
            return false;
        }
        String before = getFactionId();
        this.pledgedFactionId = normalized;
        if (!before.equals(getFactionId())) {
            // A faction's past is its own: the character reads it from
            // the moment it joined, never from before, nor from before
            // the character was made.
            this.factionSince = Math.max(this.creationTimestamp, now);
        }
        return true;
    }

    /**
     * When the character's faction became its faction: its making for
     * the starting faction, the pledge for a pledged one. Its Faction
     * history starts here.
     */
    public long getFactionSince() {
        return this.factionSince;
    }

    private static String normalizeFactionId(String factionId) {
        return factionId == null ? ""
                : factionId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The LOTR title the character wore when it was last played, as the
     * lang key LOTR names it by ({@code lotr.title.farmer}): each game
     * shows it after the name in its own language. Empty for none. The
     * title of the character being played is LOTR's live one; this is
     * what the others show.
     */
    public String getLotrTitle() {
        return this.lotrTitle;
    }

    /**
     * Keeps the title LOTR reports for this character, by its lang key,
     * read from the player data of the one being played. Answers whether
     * it changed.
     */
    public boolean setLotrTitle(String lotrTitle) {
        String normalized = normalizeTitle(lotrTitle);
        if (normalized.equals(this.lotrTitle)) {
            return false;
        }
        this.lotrTitle = normalized;
        return true;
    }

    /**
     * A title as a record keeps it: without colour codes or control
     * characters, trimmed, cut to {@link #MAX_LOTR_TITLE_LENGTH}.
     */
    public static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(title.length());
        for (int index = 0; index < title.length(); index++) {
            char c = title.charAt(index);
            if (c == '\u00a7') {
                index++;
            } else if (!Character.isISOControl(c)) {
                kept.append(c);
            }
        }
        String trimmed = kept.toString().trim();
        return trimmed.length() > MAX_LOTR_TITLE_LENGTH
                ? trimmed.substring(0, MAX_LOTR_TITLE_LENGTH).trim() : trimmed;
    }

    public String getStartingWaypointId() {
        return this.startingWaypointId;
    }

    public boolean hasUnconventionalSettings() {
        return this.unconventionalSettings;
    }

    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    /** The cape file's id; empty for none. */
    public String getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }

    /** Called only after server-side catalog and eligibility validation. */
    public boolean setCapeSettings(boolean showMinecraftCape, String cosmeticCapeId) {
        String capeId = cosmeticCapeId == null ? "" : cosmeticCapeId;
        boolean changed = this.showMinecraftCape != showMinecraftCape
                || !this.cosmeticCapeId.equals(capeId);
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = capeId;
        return changed;
    }

    /** The body the character's race stood on when it was last known (CHAR-38). */
    public CharacterBody getBody() {
        return this.body;
    }

    /** Remembers the body its race stands on now; answers whether it changed. */
    public boolean rememberBody(CharacterBody body) {
        if (body == null || body == this.body) {
            return false;
        }
        this.body = body;
        return true;
    }

    public long getCreationTimestamp() {
        return this.creationTimestamp;
    }
}
