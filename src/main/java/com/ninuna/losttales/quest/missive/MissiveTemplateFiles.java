package com.ninuna.losttales.quest.missive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.data.JsonReading;
import com.ninuna.losttales.progression.skill.SkillFileParser;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveMatcher;
import com.ninuna.losttales.quest.QuestItemNames;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and checks the missive template files (QUEST-42), bundled under
 * {@code assets/losttales/missives/} and a server's own in
 * {@code config/losttales/missives/}, one template each. A file with
 * anything said is left out. Both sides are held to one checker; only the
 * words differ: a bundled file names each title, issuer and flavour by its
 * {@code missive.losttales.*} line's id, a server's file writes them.
 *
 * <pre>
 * { "id": "losttales:zombies", "kind": "kill", "entity": "Zombie",
 *   "count": [3, 7], "experience": 10,
 *   "titles": ["trouble_on_the_road"], "issuers": ["local_watch"],
 *   "flavours": ["roads_open"] }
 * </pre>
 */
public final class MissiveTemplateFiles {
    public static final String INDEX = "missives/index.json";
    public static final String LIST_KEY = "missives";
    public static final String FOLDER = "losttales/missives";
    /** The most a notice may ask for, of creatures or of items. */
    public static final int MAX_COUNT = 256;
    /** The most experience one creature or item may pay. */
    public static final int MAX_EXPERIENCE = 1000;
    /** The most titles, issuers or flavour lines one template lists. */
    public static final int MAX_WORDS = 16;
    private static final int MAX_ID_LENGTH = 64;
    private static final Pattern TEMPLATE_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_./]+");
    static final Set<String> KEYS = JsonReading.keys("id", "kind", "entity", "group",
            "item", "count", "experience", "titles", "issuers", "flavours");

    private MissiveTemplateFiles() {}

    /** The bundled files: their words are word ids. */
    public static final DataFiles.Kind<MissiveTemplate> BUNDLED = kind(false);
    /** A server's own files: their words are written. */
    public static final DataFiles.Kind<MissiveTemplate> SERVER = kind(true);

    private static DataFiles.Kind<MissiveTemplate> kind(final boolean written) {
        return new DataFiles.Kind<MissiveTemplate>() {
            @Override
            public MissiveTemplate parse(Reader reader, String file, List<String> said) {
                return MissiveTemplateFiles.parse(reader, file, said, written);
            }

            @Override
            public String idOf(MissiveTemplate template) {
                return template.getId();
            }

            @Override
            public List<String> warnings(MissiveTemplate template) {
                return warningsOf(template);
            }
        };
    }

    /** The template a file writes; null for a file that is no JSON object. */
    public static MissiveTemplate parse(Reader reader, String file, List<String> said,
                                        boolean written) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        String fallbackId = SkillFileParser.idFromFile(file);
        String id = new JsonReading(said, "missive=" + fallbackId)
                .string(object, "id", fallbackId);
        JsonReading where = new JsonReading(said, "missive=" + id);
        where.unknownKeys(object, KEYS);
        String kind = where.string(object, "kind", "");
        String entity = where.string(object, "entity", "").trim();
        String group = where.string(object, "group", "").trim();
        String item = where.string(object, "item", "").trim();
        String targetParam;
        String target;
        if (LostTalesMissiveObjectiveData.TYPE_KILL.equals(kind)) {
            if (item.length() > 0) {
                where.say("names an 'item'; a kill names an 'entity' or a 'group'");
            }
            if (entity.length() > 0 == group.length() > 0) {
                where.say("names " + (entity.length() > 0 ? "both" : "neither")
                        + " an 'entity' and a 'group'; a kill names one of them");
            }
            targetParam = entity.length() > 0 ? "entity" : "group";
            target = entity.length() > 0 ? entity : group;
        } else if (LostTalesMissiveObjectiveData.TYPE_GATHER.equals(kind)) {
            if (entity.length() > 0 || group.length() > 0) {
                where.say("names an 'entity' or a 'group'; a gather names an 'item'");
            }
            if (item.length() == 0) {
                where.say("names no 'item' to gather");
            }
            targetParam = "item";
            target = item;
        } else {
            where.say("is of the kind '" + kind + "'; a missive is a kill or a gather");
            targetParam = "";
            target = "";
        }
        int[] count = where.wholes(object, "count");
        if (count.length != 2) {
            where.say("needs a 'count' of two whole numbers, the least and the most");
        }
        if (!object.has("experience")) {
            where.say("names no 'experience' for each creature or item");
        }
        if (!object.has("titles")) {
            where.say("lists no 'titles'");
        }
        // What is missing is said above; the stand-ins below raise nothing more.
        return new MissiveTemplate(id, kind, targetParam, target,
                count.length == 2 ? count[0] : 1, count.length == 2 ? count[1] : 1,
                where.whole(object, "experience", 0),
                words(where, object, "titles"), words(where, object, "issuers"),
                words(where, object, "flavours"), written);
    }

    /** A list of words, each trimmed; one listed but empty is said. */
    private static List<String> words(JsonReading where, JsonObject object, String key) {
        List<String> words = new ArrayList<String>();
        for (String word : where.strings(object, key)) {
            words.add(word.trim());
        }
        if (object.has(key) && words.isEmpty()) {
            where.say("lists nothing in '" + key + "'");
        }
        return words;
    }

    /** Every warning a template raises beyond what its reading said. */
    public static List<String> warningsOf(MissiveTemplate template) {
        List<String> warnings = new ArrayList<String>();
        if (template.getId().length() > MAX_ID_LENGTH
                || !TEMPLATE_ID.matcher(template.getId()).matches()) {
            warnings.add("the id '" + template.getId() + "' is not a namespace and a path of"
                    + " lower-case letters, digits, _ . and / within " + MAX_ID_LENGTH
                    + " characters");
        }
        if ("group".equals(template.getTargetParam()) && template.getTarget().length() > 0
                && !LostTalesQuestObjectiveMatcher.isGroup(template.getTarget())) {
            warnings.add("names the group '" + template.getTarget() + "'; the groups are"
                    + " living, player, hostile, animal, npc and enemy");
        }
        if (template.getTarget().length() > 0 && !MissiveWords.isTarget(template.getTarget())) {
            warnings.add("names '" + template.getTarget() + "', which is no creature kind,"
                    + " group or item id");
        } else if ("item".equals(template.getTargetParam()) && template.getTarget().length() > 0
                && template.getTarget().indexOf(':') <= 0) {
            warnings.add("names the item '" + template.getTarget() + "' without its"
                    + " namespace, as minecraft:coal");
        }
        if (template.getLeastCount() < 1 || template.getMostCount() > MAX_COUNT
                || template.getLeastCount() > template.getMostCount()) {
            warnings.add("counts " + template.getLeastCount() + " to " + template.getMostCount()
                    + "; a count runs from 1 to " + MAX_COUNT + ", the least first");
        }
        if (template.getExperience() < 0 || template.getExperience() > MAX_EXPERIENCE) {
            warnings.add("pays " + template.getExperience() + " experience for each; it is 0 to "
                    + MAX_EXPERIENCE);
        }
        checkWords(warnings, template, "titles", template.getTitles());
        checkWords(warnings, template, "issuers", template.getIssuers());
        checkWords(warnings, template, "flavours", template.getFlavours());
        return warnings;
    }

    private static void checkWords(List<String> warnings, MissiveTemplate template,
                                   String key, List<String> words) {
        if (words.size() > MAX_WORDS) {
            warnings.add("lists " + words.size() + " " + key + "; " + MAX_WORDS + " at most");
        }
        for (String word : words) {
            if (template.isWritten() ? !MissiveWords.isWrittenWords(word)
                    : !MissiveWords.isTemplateId(word)) {
                warnings.add(template.isWritten()
                        ? "lists in '" + key + "' words that are blank, hold a control"
                                + " character or pass " + MissiveWords.MAX_WRITTEN_BYTES + " bytes"
                        : "lists '" + word + "' in '" + key + "', which is no word id of"
                                + " lower-case letters, digits and _");
            }
        }
    }

    /**
     * A line for each template whose item the game does not have, each a
     * reason to leave it out; read once every mod's items are registered.
     */
    public static List<String> itemProblems(MissiveTemplate template,
                                            QuestItemNames.Items items) {
        List<String> problems = new ArrayList<String>();
        if (template != null && items != null && "item".equals(template.getTargetParam())
                && !items.has(template.getTarget())) {
            problems.add("missive=" + template.getId() + " names the item '"
                    + template.getTarget() + "', which the game does not have");
        }
        return problems;
    }
}
