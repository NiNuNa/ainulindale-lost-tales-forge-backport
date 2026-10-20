package com.ninuna.losttales.progression.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a skill file's JSON (PROG-01). It only reads what is written;
 * {@link SkillFileValidator} says what is missing or wrong in what it
 * read, and this says what it could not read at all: a key no skill file
 * has, and a value of a shape its key never takes.
 */
public final class SkillFileParser {
    static final Set<String> SKILL_KEYS = JsonReading.keys("id", "icon", "order",
            "name", "description", "counts", "perks");
    static final Set<String> COUNT_KEYS = JsonReading.keys("act", "per",
            "weapons", "worth", "ingredients");
    static final Set<String> PERK_KEYS = JsonReading.keys("id", "levels",
            "needs", "effect", "amount", "params", "with", "enabled", "name",
            "description");

    private SkillFileParser() {}

    /**
     * The skill a file writes, or null for a file that is no JSON object.
     * {@code said} is told each thing the file writes that nothing reads.
     */
    public static SkillDefinition parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String fallbackId = idFromFile(file);
        JsonReading top = new JsonReading(said, "skill=" + fallbackId);
        String id = top.string(object, "id", fallbackId);
        JsonReading skill = new JsonReading(said, "skill=" + id);
        skill.unknownKeys(object, SKILL_KEYS);
        String icon = skill.string(object, "icon", "");
        int order = skill.whole(object, "order", 0);
        String name = skill.string(object, "name", "");
        String description = skill.string(object, "description", "");
        List<SkillCount> counts = new ArrayList<SkillCount>();
        int index = 0;
        for (JsonObject countObject : skill.objects(object, "counts")) {
            counts.add(parseCount(countObject, skill.in("count=" + (++index))));
        }
        List<PerkDefinition> perks = new ArrayList<PerkDefinition>();
        for (JsonObject perkObject : skill.objects(object, "perks")) {
            perks.add(parsePerk(perkObject, skill));
        }
        return new SkillDefinition(id, icon, order, name, description, counts, perks);
    }

    /** {@code skills/archery.json} reads as {@code losttales:archery}. */
    public static String idFromFile(String file) {
        String path = file == null ? "" : file.trim().replace('\\', '/');
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return LostTalesMetaData.MOD_ID + ":" + path;
    }

    private static SkillCount parseCount(JsonObject object, JsonReading where) {
        where.unknownKeys(object, COUNT_KEYS);
        String word = where.string(object, "act", "");
        SkillAct act = SkillAct.fromWord(word);
        if (act == null) {
            where.say("counts '" + word + "', which is no act");
            act = SkillAct.HIT;
        }
        double per = where.number(object, "per", act.getDefaultPer());
        List<String> weapons = where.strings(object, "weapons");
        WorthTable worth = new WorthTable(where.numbers(object, "worth"));
        WorthTable ingredients = new WorthTable(where.numbers(object, "ingredients"));
        return new SkillCount(act, per, weapons, worth, ingredients);
    }

    private static PerkDefinition parsePerk(JsonObject object, JsonReading skill) {
        String id = skill.string(object, "id", "");
        JsonReading where = skill.in("perk=" + id);
        where.unknownKeys(object, PERK_KEYS);
        int[] levels = where.wholes(object, "levels");
        String needs = where.string(object, "needs", "");
        String needsPerk = needs;
        int needsRank = needs.length() == 0 ? 0 : 1;
        int colon = needs.lastIndexOf(':');
        if (colon > 0) {
            needsPerk = needs.substring(0, colon);
            try {
                needsRank = Integer.parseInt(needs.substring(colon + 1));
            } catch (NumberFormatException notARank) {
                where.say("needs '" + needs + "', whose rank is no whole number");
                needsRank = 1;
            }
        }
        String effect = where.string(object, "effect", "");
        double amount = where.number(object, "amount", 0.0D);
        Map<String, Double> params = new LinkedHashMap<String, Double>(
                where.numbers(object, "params"));
        List<String> with = where.strings(object, "with");
        boolean enabled = where.bool(object, "enabled", true);
        String name = where.string(object, "name", "");
        String description = where.string(object, "description", "");
        return new PerkDefinition(id, levels, needsPerk, needsRank, effect,
                amount, params, with, enabled, name, description);
    }
}
