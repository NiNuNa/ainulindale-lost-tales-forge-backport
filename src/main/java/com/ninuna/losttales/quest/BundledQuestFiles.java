package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.util.LostTalesCloseables;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quest files bundled with the mod, read the same way on the server
 * and on the client: {@code quests/index.json} lists them, and each is
 * parsed in the index's order. A file that is missing or cannot be read,
 * an index that cannot be read, a file whose id is too long for the
 * {@link LostTalesQuestIds} bound, and a file whose id an earlier file has
 * are left out with a line saying why; the others still load. The server
 * reads the files from its classpath, the client from its resources.
 *
 * <p>Each quest read is worded by the lang lines its id names
 * ({@link LostTalesQuestWords#bundled}); the files hold no words of their
 * own, and an id that cannot name lang lines is left out.</p>
 */
public final class BundledQuestFiles {
    public static final String INDEX_FILE = "quests/index.json";

    private BundledQuestFiles() {}

    /** Where the files are read from. */
    public interface Source {
        /**
         * The file at {@code path} under the mod's assets, or under
         * another domain's for {@code domain:path}; null when there is none.
         */
        Reader open(String path) throws IOException;
    }

    /** What a read found: the quests in the index's order, and why each other file was left out. */
    public static final class Result {
        public final List<LostTalesQuestDefinition> quests;
        public final List<String> problems;

        Result(List<LostTalesQuestDefinition> quests, List<String> problems) {
            this.quests = Collections.unmodifiableList(quests);
            this.problems = Collections.unmodifiableList(problems);
        }
    }

    public static Result read(Source source) {
        List<String> problems = new ArrayList<String>();
        Map<String, LostTalesQuestDefinition> byId =
                new LinkedHashMap<String, LostTalesQuestDefinition>();
        Map<String, String> fileOf = new LinkedHashMap<String, String>();
        for (String file : index(source, problems)) {
            LostTalesQuestDefinition quest = parse(source, file, problems);
            if (quest == null) {
                continue;
            }
            if (!LostTalesQuestIds.fits(quest.getId())) {
                problems.add(file + ": its id is longer than "
                        + LostTalesQuestIds.MAX_BYTES + " bytes");
                continue;
            }
            if (!LostTalesQuestWords.isKeySafe(quest.getId())) {
                problems.add(file + ": its id " + quest.getId()
                        + " cannot name lang lines; it holds only lower-case"
                        + " letters, digits and _ . / :");
                continue;
            }
            String earlier = fileOf.get(quest.getId());
            if (earlier != null) {
                problems.add(file + ": the id " + quest.getId()
                        + " is already " + earlier + "'s");
                continue;
            }
            fileOf.put(quest.getId(), file);
            byId.put(quest.getId(), LostTalesQuestWords.bundled(quest,
                    LostTalesLangFile.english()));
        }
        return new Result(new ArrayList<LostTalesQuestDefinition>(
                byId.values()), problems);
    }

    /**
     * Where a bundled file lies on the classpath: {@code assets/<domain>/<path>},
     * the mod's own domain for a path that names none.
     */
    public static String classpathPath(String questFile) {
        String normalized = LostTalesQuestDefinitionJsonParser.normalizeQuestFile(questFile);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return "assets/" + normalized.substring(0, colon) + "/"
                    + normalized.substring(colon + 1);
        }
        return "assets/" + LostTalesMetaData.MOD_ID + "/" + normalized;
    }

    private static List<String> index(Source source, List<String> problems) {
        Reader reader = null;
        try {
            reader = source.open(INDEX_FILE);
            if (reader == null) {
                problems.add(INDEX_FILE + " is missing, so no bundled quest loads");
                return Collections.emptyList();
            }
            return LostTalesQuestDefinitionJsonParser.parseQuestIndex(reader);
        } catch (IOException unreadable) {
            problems.add(INDEX_FILE + " cannot be read, so no bundled quest loads: "
                    + unreadable.getMessage());
        } catch (RuntimeException malformed) {
            problems.add(INDEX_FILE + " cannot be read, so no bundled quest loads: "
                    + malformed.getMessage());
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
        return Collections.emptyList();
    }

    private static LostTalesQuestDefinition parse(Source source, String file,
                                                  List<String> problems) {
        Reader reader = null;
        try {
            reader = source.open(file);
            if (reader == null) {
                problems.add(file + " is named by the index but missing");
                return null;
            }
            LostTalesQuestDefinition quest =
                    LostTalesQuestDefinitionJsonParser.parseQuest(reader, file);
            if (quest == null) {
                problems.add(file + " is not a quest");
            }
            return quest;
        } catch (IOException unreadable) {
            problems.add(file + " cannot be read: " + unreadable.getMessage());
        } catch (RuntimeException malformed) {
            problems.add(file + " is not a quest: " + malformed.getMessage());
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
        return null;
    }
}
