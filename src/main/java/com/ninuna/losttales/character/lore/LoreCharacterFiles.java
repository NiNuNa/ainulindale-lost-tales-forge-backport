package com.ninuna.losttales.character.lore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the lore character files (CHAR-43), bundled under
 * {@code assets/losttales/lore_characters/} and a server's own in
 * {@code config/losttales/lore_characters/}. A file writes {@code id},
 * {@code name}, the {@code faction} the character starts in (CHAR-44),
 * and its look as {@code race}, {@code body_type} and {@code skin}; a file
 * with none of the three is listed but cannot be claimed. It may write a
 * {@code description}, an {@code age} (18 otherwise) and a whole
 * {@code profile}. A file with anything said is left out.
 */
public final class LoreCharacterFiles {
    public static final String INDEX = "lore_characters/index.json";
    public static final String LIST_KEY = "characters";
    public static final String FOLDER = "losttales/lore_characters";
    /** A glance's emoji is a chat emoji's name; well past the longest. */
    public static final int MAX_EMOJI_NAME_LENGTH = 64;

    /** The ids the lore stores keep ({@code LoreCharacterOwnershipRecord}). */
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern RACE_BODY_OR_FACTION = Pattern.compile("[a-z0-9_]+:[a-z0-9_]+");
    static final Set<String> KEYS = JsonReading.keys("id", "name", "description", "age",
            "race", "body_type", "skin", "faction", "profile");
    static final Set<String> PROFILE_KEYS = JsonReading.keys("appearance", "personality",
            "history", "facts", "glances");
    static final Set<String> GLANCE_KEYS = JsonReading.keys("emoji", "title", "line");

    /** What the game has, for the checks a lore file's names need. */
    public interface Game {
        /** The race of that id the server plays with; null for none, or a stand-in. */
        CharacterRaceDefinition race(String raceId);

        /** Whether a character of that race and body type may wear the skin. */
        boolean skinFits(String skinId, String raceId, String bodyTypeId);

        /** Whether LOTR can be asked about factions; when it cannot, factions go unchecked. */
        boolean knowsFactions();

        /** The faction of that id; null when LOTR has none. */
        CharacterFactionDefinition faction(String factionId);
    }

    /** The server's races, its skins and LOTR's factions, as the running game has them. */
    public static final Game GAME = new Game() {
        @Override
        public CharacterRaceDefinition race(String raceId) {
            CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
            return race == null || race.isStandIn() ? null : race;
        }

        @Override
        public boolean skinFits(String skinId, String raceId, String bodyTypeId) {
            return CharacterSkinRegistry.isCompatible(skinId, raceId, bodyTypeId);
        }

        @Override
        public boolean knowsFactions() {
            return LotrCharacterAdapter.getInstance().isAvailable();
        }

        @Override
        public CharacterFactionDefinition faction(String factionId) {
            return LotrCharacterAdapter.getInstance().resolve(factionId);
        }
    };

    private LoreCharacterFiles() {}

    /** The lore files, each checked against what {@code game} has. */
    public static DataFiles.Kind<LoreCharacterDefinition> kind(final Game game) {
        return new DataFiles.Kind<LoreCharacterDefinition>() {
            @Override
            public LoreCharacterDefinition parse(Reader reader, String file, List<String> said) {
                return LoreCharacterFiles.parse(reader, file, said);
            }

            @Override
            public String idOf(LoreCharacterDefinition definition) {
                return definition.getId();
            }

            @Override
            public List<String> warnings(LoreCharacterDefinition definition) {
                return warningsOf(definition, game);
            }
        };
    }

    public static LoreCharacterDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        // A missing id or name reads as empty, which the checks then name.
        String id = new JsonReading(said, file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "lore=" + (id.length() == 0 ? file : id));
        where.unknownKeys(object, KEYS);
        String description = where.string(object, "description", "").trim();
        String race = where.string(object, "race", "");
        String bodyType = where.string(object, "body_type", "");
        String skin = where.string(object, "skin", "");
        LoreCharacterDefinition.Appearance look = race.length() == 0
                && bodyType.length() == 0 && skin.length() == 0 ? null
                : new LoreCharacterDefinition.Appearance(race, bodyType, skin);
        return new LoreCharacterDefinition(id, where.string(object, "name", "").trim(),
                description, where.whole(object, "age", LoreCharacterDefinition.DEFAULT_AGE),
                look, where.string(object, "faction", ""),
                profile(where, object, description));
    }

    /** Every warning the lore character raises: its shape, then what {@code game} lacks. */
    public static List<String> warningsOf(LoreCharacterDefinition definition, Game game) {
        List<String> warnings = new ArrayList<String>();
        String id = definition.getId();
        if (id.length() == 0) {
            warnings.add("names no id");
        } else if (id.length() > LoreCharacterDefinition.MAX_ID_LENGTH
                || !ID.matcher(id).matches()) {
            warnings.add("the id '" + id + "' is not a namespace and a path of lower-case"
                    + " letters, digits, _, -, . and / within "
                    + LoreCharacterDefinition.MAX_ID_LENGTH + " characters");
        }
        nameWarnings(definition.getName(), warnings);
        String description = definition.getDescription();
        if (description.length() > LoreCharacterDefinition.MAX_DESCRIPTION_LENGTH) {
            warnings.add("its description is longer than "
                    + LoreCharacterDefinition.MAX_DESCRIPTION_LENGTH + " characters");
        } else if (!printable(description)) {
            warnings.add("its description holds a formatting code or a control character");
        }
        if (definition.getAge() < CharacterValidator.MIN_AGE
                || definition.getAge() > CharacterValidator.MAX_AGE) {
            warnings.add("is " + definition.getAge() + " years old; an age runs from "
                    + CharacterValidator.MIN_AGE + " to " + CharacterValidator.MAX_AGE);
        }
        CharacterRaceDefinition race = lookWarnings(definition.getAppearance(), game, warnings);
        factionWarnings(definition.getFactionId(), definition.getAppearance(), race, game,
                warnings);
        return warnings;
    }

    /**
     * Whether a race may start in a faction by the race's own file, its
     * faction categories and factions, whatever the server's allow and
     * deny lists say: those are asked as the figure is claimed.
     */
    static boolean raceTakes(CharacterRaceDefinition race, CharacterFactionDefinition faction) {
        return race != null && faction != null && race.isCompatibleWith(
                new CharacterFactionDefinition(faction.getId(), true, faction.getCategories()));
    }

    private static void nameWarnings(String name, List<String> warnings) {
        if (name.length() == 0) {
            warnings.add("names no name");
            return;
        }
        if (name.length() > LoreCharacterDefinition.MAX_NAME_LENGTH) {
            warnings.add("its name is longer than " + LoreCharacterDefinition.MAX_NAME_LENGTH
                    + " characters");
        } else if (name.getBytes(StandardCharsets.UTF_8).length
                > LoreCharacterDefinition.MAX_NAME_BYTES) {
            warnings.add("its name takes more than " + LoreCharacterDefinition.MAX_NAME_BYTES
                    + " bytes of UTF-8");
        }
        if (!printable(name) || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            warnings.add("its name holds a formatting code or a control character");
        }
        if (CharacterNames.key(name).length() == 0) {
            warnings.add("its name holds no letter or digit");
        }
    }

    /** The look's warnings; answers its race when the server has it. */
    private static CharacterRaceDefinition lookWarnings(LoreCharacterDefinition.Appearance look,
                                                        Game game, List<String> warnings) {
        if (look == null) {
            return null;
        }
        String said = " a lore character that can be claimed names its race, body type and skin";
        if (look.getRaceId().length() == 0) {
            warnings.add("names no race;" + said);
        }
        if (look.getBodyTypeId().length() == 0) {
            warnings.add("names no body type;" + said);
        }
        if (look.getSkinId().length() == 0) {
            warnings.add("names no skin;" + said);
        }
        String[][] pieces = {{"race", look.getRaceId(), "losttales:human"},
                {"body type", look.getBodyTypeId(), "losttales:masculine"},
                {"skin", look.getSkinId(), "losttales:human_bree_masculine_0"}};
        boolean written = true;
        for (String[] piece : pieces) {
            if (piece[1].length() > 0 && !RACE_BODY_OR_FACTION.matcher(piece[1]).matches()) {
                warnings.add("the " + piece[0] + " '" + piece[1] + "' is not written as an id,"
                        + " like " + piece[2]);
                written = false;
            }
        }
        if (look.getRaceId().length() == 0 || !written) {
            return null;
        }
        CharacterRaceDefinition race = game.race(look.getRaceId());
        if (race == null) {
            warnings.add("is of the race " + look.getRaceId()
                    + ", which the server does not have");
            return null;
        }
        if (look.getBodyTypeId().length() > 0 && !race.isBodyTypeAllowed(look.getBodyTypeId())) {
            warnings.add("has the body type " + look.getBodyTypeId() + ", which the race "
                    + race.getId() + " does not");
        } else if (look.getBodyTypeId().length() > 0 && look.getSkinId().length() > 0
                && !game.skinFits(look.getSkinId(), race.getId(), look.getBodyTypeId())) {
            warnings.add("wears the skin " + look.getSkinId() + ", which the race "
                    + race.getId() + " does not offer for the body type "
                    + look.getBodyTypeId());
        }
        return race;
    }

    private static void factionWarnings(String factionId, LoreCharacterDefinition.Appearance look,
                                        CharacterRaceDefinition race, Game game,
                                        List<String> warnings) {
        if (factionId.length() == 0) {
            warnings.add("names no faction; a lore character names the faction it starts in,"
                    + " like lotr:gondor");
            return;
        }
        if (!RACE_BODY_OR_FACTION.matcher(factionId).matches()) {
            warnings.add("the faction '" + factionId + "' is not written as a faction's id,"
                    + " like lotr:gondor");
            return;
        }
        if (!game.knowsFactions()) {
            return;
        }
        CharacterFactionDefinition faction = game.faction(factionId);
        if (faction == null) {
            warnings.add("starts in the faction " + factionId + ", which LOTR does not have");
        } else if (look != null && race != null && !raceTakes(race, faction)) {
            warnings.add("starts in the faction " + factionId + ", which the race "
                    + race.getId() + " may not start in");
        }
    }

    /**
     * The profile the file gives, every part optional, held to the bounds
     * a player's profile is and stored as one is: {@code appearance} and
     * {@code personality} (512 characters each) and {@code history}
     * (2,048), paragraphs kept, {@code facts} by name (24 characters
     * each), and up to five {@code glances}, each the {@code emoji} of the
     * chat's by its name, a {@code title} and an optional {@code line}.
     * The History is the description where the profile gives none.
     */
    private static CharacterProfile profile(JsonReading where, JsonObject object,
                                            String description) {
        CharacterProfile profile = CharacterProfile.EMPTY;
        JsonReading in = where.in("profile");
        JsonObject block = block(where, object, "profile");
        if (block != null) {
            in.unknownKeys(block, PROFILE_KEYS);
            for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
                profile = profile.withSection(section, in.string(block, section.getId(), ""));
            }
            JsonObject facts = block(in, block, "facts");
            if (facts != null) {
                for (Map.Entry<String, JsonElement> fact : facts.entrySet()) {
                    CharacterProfile.Fact known = fact(fact.getKey());
                    if (known == null) {
                        in.say("writes the fact '" + fact.getKey() + "', which nothing reads");
                        continue;
                    }
                    profile = profile.withFact(known, in.in("fact").string(facts,
                            fact.getKey(), ""));
                }
            }
            List<CharacterProfile.Glance> glances = new ArrayList<CharacterProfile.Glance>();
            int index = 0;
            for (JsonObject entry : in.objects(block, "glances")) {
                JsonReading glance = in.in("glance " + (++index));
                glance.unknownKeys(entry, GLANCE_KEYS);
                glances.add(new CharacterProfile.Glance(glance.string(entry, "emoji", ""),
                        glance.string(entry, "title", ""), glance.string(entry, "line", "")));
            }
            if (glances.size() > CharacterProfile.MAX_GLANCES) {
                in.say("holds " + glances.size() + " glances; a profile holds at most "
                        + CharacterProfile.MAX_GLANCES);
            }
            profile = profile.withGlances(glances);
        }
        if (profile.section(CharacterProfile.Section.HISTORY).trim().length() == 0) {
            profile = profile.withSection(CharacterProfile.Section.HISTORY, description);
        }
        CharacterProfile stored = CharacterValidator.normalizeProfile(profile);
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            if (!CharacterValidator.isValidSection(section, stored.section(section))) {
                in.say("holds a " + section.getId() + " longer than " + section.getMaxLength()
                        + " characters, or with a character a profile may not hold");
            }
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            if (!CharacterValidator.isValidLine(stored.fact(fact),
                    CharacterProfile.MAX_FACT_LENGTH)) {
                in.say("holds the fact " + fact.getId() + " longer than "
                        + CharacterProfile.MAX_FACT_LENGTH
                        + " characters, or on more than one line");
            }
        }
        for (int glance = 0; glance < stored.glances().size(); glance++) {
            CharacterProfile.Glance read = stored.glances().get(glance);
            if (read.getEmoji().length() > MAX_EMOJI_NAME_LENGTH
                    || !CharacterValidator.isValidGlance(read)) {
                in.say("holds glance " + (glance + 1) + " without one of the chat's emoji by"
                        + " its name or a title, or with a title past "
                        + CharacterProfile.MAX_GLANCE_TITLE_LENGTH + " characters or a line past "
                        + CharacterProfile.MAX_GLANCE_LINE_LENGTH);
            }
        }
        return stored;
    }

    /** The block of names under {@code key}; null, and said, when it is something else. */
    private static JsonObject block(JsonReading where, JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            where.say("writes '" + key + "' as something other than a block of names");
            return null;
        }
        return element.getAsJsonObject();
    }

    private static CharacterProfile.Fact fact(String id) {
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            if (fact.getId().equals(id)) {
                return fact;
            }
        }
        return null;
    }

    /** Text with no formatting code and no control character but a line break or a tab. */
    private static boolean printable(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '§' || character < 32 && character != '\n'
                    && character != '\r' && character != '\t') {
                return false;
            }
        }
        return true;
    }
}
