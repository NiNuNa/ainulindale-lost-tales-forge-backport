package com.ninuna.losttales.progression.deed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.progression.SkillCurve;
import com.ninuna.losttales.progression.CharacterLevelRule;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.progression.skill.SkillFileParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the deed files (PROG-11), bundled under
 * {@code assets/losttales/deeds/} and a server's own in
 * {@code config/losttales/deeds/}. A file with anything said is left out.
 */
public final class DeedFiles {
    public static final String INDEX = "deeds/index.json";
    public static final String FOLDER = "losttales/deeds";
    public static final int MAX_PLACES = 32;
    public static final int MAX_LIST = 64;
    public static final int MAX_COUNT = 100000;
    public static final double MAX_RADIUS = 1024.0D;
    public static final double DEFAULT_RADIUS = 64.0D;
    public static final int MAX_ALIGNMENT = 1000;
    private static final Pattern DEED_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    static final Set<String> KEYS = JsonReading.keys("id", "category", "icon", "kind",
            "places", "radius", "creatures", "named", "quests", "count", "skill", "level",
            "together", "alignment", "title", "name", "description");

    private DeedFiles() {}

    public static final DataFiles.Kind<DeedDefinition> KIND = new DataFiles.Kind<DeedDefinition>() {
        @Override
        public DeedDefinition parse(Reader reader, String file, List<String> said) {
            return DeedFiles.parse(reader, file, said);
        }

        @Override
        public String idOf(DeedDefinition deed) {
            return deed.getId();
        }

        @Override
        public List<String> warnings(DeedDefinition deed) {
            return DeedFiles.warningsOf(deed);
        }
    };

    public static DeedDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String fallbackId = SkillFileParser.idFromFile(file);
        String id = new JsonReading(said, "deed=" + fallbackId).string(object, "id", fallbackId);
        JsonReading where = new JsonReading(said, "deed=" + id);
        where.unknownKeys(object, KEYS);
        String word = where.string(object, "kind", "");
        DeedDefinition.Kind kind = DeedDefinition.Kind.fromWord(word);
        if (kind == null) {
            where.say("is of the kind '" + word + "', which no deed has");
            kind = DeedDefinition.Kind.QUESTS;
        }
        return new DeedDefinition(id, where.string(object, "category", ""),
                where.string(object, "icon", ""), kind, where.strings(object, "places"),
                where.number(object, "radius", DEFAULT_RADIUS), where.strings(object, "creatures"),
                where.bool(object, "named", false), where.strings(object, "quests"),
                where.whole(object, "count", 1), where.string(object, "skill", ""),
                where.whole(object, "level", 0),
                where.bool(object, "together", kind == DeedDefinition.Kind.KILL
                        || kind == DeedDefinition.Kind.WORLD_QUEST),
                where.string(object, "alignment", ""), where.bool(object, "title", false),
                where.string(object, "name", ""), where.string(object, "description", ""));
    }

    public static List<String> warningsOf(DeedDefinition deed) {
        List<String> warnings = new ArrayList<String>();
        if (deed.getId().length() > 64 || !DEED_ID.matcher(deed.getId()).matches()) {
            warnings.add("the id '" + deed.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits, _ and . within 64 characters");
        }
        if (DeedCategory.fromWord(deed.getCategory()) == null) {
            warnings.add("is in the category '" + deed.getCategory()
                    + "'; it is growth, journeys, foes or tales");
        }
        if (deed.getIcon().length() > 0 && deed.getIcon().indexOf(':') <= 0) {
            warnings.add("the icon '" + deed.getIcon() + "' is no item name");
        }
        if (deed.getCount() < 1 || deed.getCount() > MAX_COUNT) {
            warnings.add("counts " + deed.getCount() + "; a count runs from 1 to " + MAX_COUNT);
        }
        if (deed.getPlaces().size() > MAX_PLACES || deed.getCreatures().size() > MAX_LIST
                || deed.getQuests().size() > MAX_LIST) {
            warnings.add("lists more than it may: " + MAX_PLACES + " places, " + MAX_LIST
                    + " creatures or quests");
        }
        switch (deed.getKind()) {
            case PLACES:
                if (deed.getPlaces().isEmpty()) {
                    warnings.add("lists no places");
                }
                for (String place : deed.getPlaces()) {
                    if (place.indexOf(':') <= 0) {
                        warnings.add("lists the place '" + place
                                + "', which is neither lotr:WAYPOINT nor a marker's id");
                    }
                }
                if (deed.getRadius() < 1.0D || deed.getRadius() > MAX_RADIUS) {
                    warnings.add("reaches places within " + deed.getRadius()
                            + " blocks; it is 1 to " + MAX_RADIUS);
                }
                break;
            case KILL:
                if (deed.getCreatures().isEmpty() && !deed.isNamed()) {
                    warnings.add("lists no creatures and asks for no named one");
                }
                break;
            case SKILL_LEVEL:
                if (deed.getSkill().length() == 0) {
                    warnings.add("names no skill; write a skill's id or 'any'");
                }
                if (deed.getLevel() < SkillCurve.FIRST_LEVEL + 1
                        || deed.getLevel() > SkillCurve.TOP_LEVEL) {
                    warnings.add("asks for skill level " + deed.getLevel()
                            + "; it is 2 to " + SkillCurve.TOP_LEVEL);
                }
                break;
            case CHARACTER_LEVEL:
                if (deed.getLevel() < 2 || deed.getLevel() > CharacterLevelRule.MAX_LEVEL) {
                    warnings.add("asks for character level " + deed.getLevel()
                            + "; it is 2 to " + CharacterLevelRule.MAX_LEVEL);
                }
                break;
            default:
                break;
        }
        if (deed.getAlignment().length() > 0) {
            for (String part : deed.getAlignment().split(",")) {
                int star = part.lastIndexOf('*');
                String amount = star < 0 ? "" : part.substring(star + 1).trim();
                if (star <= 0 || !amount.matches("[0-9]{1,4}")
                        || Integer.parseInt(amount) < 1 || Integer.parseInt(amount) > MAX_ALIGNMENT) {
                    warnings.add("gives the alignment '" + part.trim() + "'; write faction*amount,"
                            + " an amount of 1 to " + MAX_ALIGNMENT);
                }
            }
        }
        if (deed.getName().length() > 64 || deed.getDescription().length() > 512) {
            warnings.add("its name or description is too long");
        }
        return warnings;
    }
}
