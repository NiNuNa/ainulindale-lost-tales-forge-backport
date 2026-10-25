package com.ninuna.losttales.character.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.progression.SkillCurve;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the race files (CHAR-38), bundled under
 * {@code assets/losttales/races/} and a server's own in
 * {@code config/losttales/races/}. A file with anything said is left out.
 * The bounds a server's race is held to are what the hitbox, the eyes, the
 * camera and the attributes are known to bear.
 */
public final class CharacterRaceFiles {
    public static final String INDEX = "races/index.json";
    public static final String LIST_KEY = "races";
    public static final String FOLDER = "losttales/races";

    /** Narrower than a block with room to spare, so every one-wide gap is passed. */
    public static final float MIN_WIDTH = 0.4F;
    public static final float MAX_WIDTH = 0.8F;
    /** Under two blocks, so every two-high gap is passed and a respawn always fits. */
    public static final float MIN_HEIGHT = 0.9F;
    public static final float MAX_HEIGHT = 1.95F;
    public static final double MIN_HEALTH = 2.0D;
    public static final double MAX_HEALTH = 40.0D;
    public static final double MIN_SPEED = 0.5D;
    public static final double MAX_SPEED = 1.5D;
    public static final double MAX_ATTACK = 10.0D;
    public static final int MAX_SKIN_ENTRIES = 32;
    public static final int MAX_SKINS_PER_ENTRY = 64;
    public static final int MAX_FACTIONS = 32;
    public static final int MAX_STARTING_SKILLS = 16;
    public static final int MAX_NAME = 32;
    public static final int MAX_TEXTURE = 128;

    /** The LOTR people a race may take its health, speed and attack from. */
    public static final List<String> REPRESENTATIVES = Collections.unmodifiableList(
            Arrays.asList("lotr:bree_man", "lotr:high_elf", "lotr:dwarf",
                    "lotr:hobbit", "lotr:mordor_orc", "lotr:uruk_hai"));
    /** Where a picture lies that every player has: LOTR's, or the mod's own. */
    public static final String LOTR_TEXTURES = "lotr:";
    public static final String OWN_TEXTURES = "losttales:textures/skins/";

    private static final Pattern RACE_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_]+");
    private static final Pattern GROUP = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern FACTION_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_]+:[A-Za-z0-9_./\\-]+");
    static final Set<String> KEYS = JsonReading.keys("id", "name", "body", "values_from",
            "body_types", "size", "health", "speed", "attack", "faction_categories", "factions",
            "skins", "skills");
    static final Set<String> SKIN_KEYS = JsonReading.keys("group", "name", "body_type",
            "texture", "count");
    static final Set<String> SIZE_KEYS = JsonReading.keys("width", "height");

    private CharacterRaceFiles() {}

    /**
     * The kind as the server reads it: each race's starting skills checked
     * against {@code skillIds}, the skills that loaded; null leaves them
     * unchecked, as where the skills are not known.
     */
    public static DataFiles.Kind<CharacterRaceDefinition> kind(final Collection<String> skillIds) {
        final Set<String> skills = skillIds == null ? null : new HashSet<String>(skillIds);
        return new DataFiles.Kind<CharacterRaceDefinition>() {
            @Override
            public CharacterRaceDefinition parse(Reader reader, String file, List<String> said) {
                return CharacterRaceFiles.parse(reader, file, said);
            }

            @Override
            public String idOf(CharacterRaceDefinition race) {
                return race.getId();
            }

            @Override
            public List<String> warnings(CharacterRaceDefinition race) {
                return warningsOf(race, skills);
            }
        };
    }

    public static CharacterRaceDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String id = new JsonReading(said, file).string(object, "id", "");
        JsonReading where = new JsonReading(said, "race=" + (id.length() == 0 ? file : id));
        where.unknownKeys(object, KEYS);
        if (id.length() == 0) {
            where.say("names no id");
        }
        String bodyWord = where.string(object, "body", "");
        CharacterBody body = CharacterBody.fromWord(bodyWord);
        if (body == null) {
            where.say("stands on the body '" + bodyWord
                    + "'; it is human, elf, dwarf, hobbit, orc or uruk");
        }
        Map<String, Double> size = where.numbers(object, "size");
        for (String key : size.keySet()) {
            if (!SIZE_KEYS.contains(key)) {
                where.say("writes 'size." + key + "', which nothing reads");
            }
        }
        if (!size.containsKey("width") || !size.containsKey("height")) {
            where.say("gives no size.width and size.height");
        }
        double width = size.containsKey("width") ? size.get("width").doubleValue() : 0.0D;
        double height = size.containsKey("height") ? size.get("height").doubleValue() : 0.0D;
        double health = required(where, object, "health");
        double speed = required(where, object, "speed");
        double attack = required(where, object, "attack");

        Set<CharacterFactionCategory> categories =
                EnumSet.noneOf(CharacterFactionCategory.class);
        for (String word : where.strings(object, "faction_categories")) {
            CharacterFactionCategory category = categoryOf(word);
            if (category == null) {
                where.say("starts in the faction category '" + word
                        + "'; it is human, elf, dwarf or orc");
            } else {
                categories.add(category);
            }
        }
        Set<String> factions = new LinkedHashSet<String>(where.strings(object, "factions"));
        Set<String> bodyTypes = new LinkedHashSet<String>(where.strings(object, "body_types"));

        List<CharacterRaceSkins> skins = new ArrayList<CharacterRaceSkins>();
        int index = 0;
        for (JsonObject entry : where.objects(object, "skins")) {
            JsonReading skin = where.in("skins[" + index++ + "]");
            skin.unknownKeys(entry, SKIN_KEYS);
            skins.add(new CharacterRaceSkins(skin.string(entry, "group", ""),
                    skin.string(entry, "name", ""), skin.string(entry, "body_type", ""),
                    skin.string(entry, "texture", ""), skin.whole(entry, "count", 0)));
        }

        Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Double> skill : where.numbers(object, "skills").entrySet()) {
            double level = skill.getValue().doubleValue();
            if (level != Math.rint(level) || level < SkillCurve.FIRST_LEVEL
                    || level > SkillCurve.TOP_LEVEL) {
                where.say("starts " + skill.getKey() + " at " + level
                        + "; a skill starts at a whole level from "
                        + SkillCurve.FIRST_LEVEL + " to " + SkillCurve.TOP_LEVEL);
                continue;
            }
            skills.put(skill.getKey(), Integer.valueOf((int) level));
        }

        return new CharacterRaceDefinition(id, where.string(object, "name", ""), body,
                where.string(object, "values_from", ""), categories, factions, bodyTypes,
                (float) width, (float) height, health, speed, attack, skins, skills, "");
    }

    /** Every warning a race raises; {@code skillIds} null leaves its skills unchecked. */
    public static List<String> warningsOf(CharacterRaceDefinition race, Set<String> skillIds) {
        List<String> warnings = new ArrayList<String>();
        if (race.getId().length() > 64 || !RACE_ID.matcher(race.getId()).matches()) {
            warnings.add("the id '" + race.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits and _ within 64 characters");
        }
        if (race.getName().length() > MAX_NAME) {
            warnings.add("its name is longer than " + MAX_NAME + " characters");
        }
        String valuesFrom = race.getLotrRaceAssociation();
        if (valuesFrom.length() > 0 && !REPRESENTATIVES.contains(valuesFrom)) {
            warnings.add("takes its values from '" + valuesFrom + "'; it is one of "
                    + REPRESENTATIVES);
        }
        if (!within(race.getWidth(), MIN_WIDTH, MAX_WIDTH)
                || !within(race.getHeight(), MIN_HEIGHT, MAX_HEIGHT)) {
            warnings.add("is " + race.getWidth() + " wide and " + race.getHeight()
                    + " high; a race is " + MIN_WIDTH + " to " + MAX_WIDTH + " wide and "
                    + MIN_HEIGHT + " to " + MAX_HEIGHT + " high");
        }
        if (!within(race.getMaxHealth(), MIN_HEALTH, MAX_HEALTH)) {
            warnings.add("has health " + race.getMaxHealth() + "; it is " + MIN_HEALTH
                    + " to " + MAX_HEALTH);
        }
        if (!within(race.getMovementSpeedMultiplier(), MIN_SPEED, MAX_SPEED)) {
            warnings.add("has speed " + race.getMovementSpeedMultiplier() + "; it is "
                    + MIN_SPEED + " to " + MAX_SPEED);
        }
        if (!within(race.getAttackDamage(), 0.0D, MAX_ATTACK)) {
            warnings.add("has attack " + race.getAttackDamage() + "; it is 0 to " + MAX_ATTACK);
        }
        if (race.getAllowedBodyTypeIds().isEmpty()) {
            warnings.add("names no sexes");
        }
        for (String bodyType : race.getAllowedBodyTypeIds()) {
            if (!CharacterBodyTypeRegistry.contains(bodyType) || !bodyType.equals(bodyType.toLowerCase(Locale.ROOT))) {
                warnings.add("names the sex '" + bodyType + "', which is none the game has");
            }
        }
        if (race.getAllowedFactionCategories().isEmpty()
                && race.getAllowedFactionIds().isEmpty()) {
            warnings.add("names no faction categories and no factions, so nobody could start as it");
        }
        if (race.getAllowedFactionIds().size() > MAX_FACTIONS) {
            warnings.add("names more than " + MAX_FACTIONS + " factions");
        }
        for (String faction : race.getAllowedFactionIds()) {
            if (!FACTION_ID.matcher(faction).matches()) {
                warnings.add("names the faction '" + faction + "', which is no faction's id");
            }
        }
        warnSkins(race, warnings);
        if (race.getStartingSkills().size() > MAX_STARTING_SKILLS) {
            warnings.add("starts more than " + MAX_STARTING_SKILLS + " skills higher");
        }
        if (skillIds != null) {
            for (String skill : race.getStartingSkills().keySet()) {
                if (!skillIds.contains(skill)) {
                    warnings.add("starts " + skill + ", which is no skill that loaded");
                }
            }
        }
        return warnings;
    }

    private static void warnSkins(CharacterRaceDefinition race, List<String> warnings) {
        if (race.getSkins().size() > MAX_SKIN_ENTRIES) {
            warnings.add("lists more than " + MAX_SKIN_ENTRIES + " skin entries");
        }
        Set<String> seen = new HashSet<String>();
        Map<String, String> names = new LinkedHashMap<String, String>();
        int index = 0;
        for (CharacterRaceSkins skins : race.getSkins()) {
            String where = "skins[" + index++ + "]";
            if (!GROUP.matcher(skins.getGroup()).matches()
                    || CharacterSkinRegistry.ACCOUNT_SKIN_GROUP.equals(skins.getGroup())) {
                warnings.add(where + " is in the group '" + skins.getGroup()
                        + "'; a group is up to 32 lower-case letters, digits and _, not 'account'");
            }
            if (skins.getBodyTypeId().length() > 0 && !race.isBodyTypeAllowed(skins.getBodyTypeId())) {
                warnings.add(where + " is painted for '" + skins.getBodyTypeId()
                        + "', which is no sex the race has");
            }
            if (!seen.add(skins.getGroup() + "|" + skins.getBodyTypeId())) {
                warnings.add(where + " lists the group " + skins.getGroup()
                        + " for the same sex twice");
            }
            String texture = skins.getTexture();
            if (texture.length() > MAX_TEXTURE || !TEXTURE.matcher(texture).matches()
                    || !(texture.startsWith(LOTR_TEXTURES) || texture.startsWith(OWN_TEXTURES))
                    || texture.contains("..")) {
                warnings.add(where + " draws from '" + texture + "'; a skin folder is LOTR's ("
                        + LOTR_TEXTURES + "...) or the mod's own (" + OWN_TEXTURES
                        + "...), which every player has");
            }
            if (skins.getCount() < 1 || skins.getCount() > MAX_SKINS_PER_ENTRY) {
                warnings.add(where + " holds " + skins.getCount() + " pictures; it is 1 to "
                        + MAX_SKINS_PER_ENTRY);
            }
            if (skins.getName().length() > MAX_NAME) {
                warnings.add(where + " has a name longer than " + MAX_NAME + " characters");
            }
            String earlier = names.get(skins.getGroup());
            if (earlier != null && !earlier.equals(skins.getName())) {
                warnings.add(where + " names the group " + skins.getGroup()
                        + " otherwise than an earlier entry");
            }
            names.put(skins.getGroup(), skins.getName());
        }
    }

    private static double required(JsonReading where, JsonObject object, String key) {
        if (!object.has(key)) {
            where.say("gives no " + key);
            return 0.0D;
        }
        return where.number(object, key, 0.0D);
    }

    private static CharacterFactionCategory categoryOf(String word) {
        for (CharacterFactionCategory category : CharacterFactionCategory.values()) {
            if (category.name().toLowerCase(Locale.ROOT).equals(word)) {
                return category;
            }
        }
        return null;
    }

    private static boolean within(double value, double minimum, double maximum) {
        return !Double.isNaN(value) && value >= minimum && value <= maximum;
    }
}
