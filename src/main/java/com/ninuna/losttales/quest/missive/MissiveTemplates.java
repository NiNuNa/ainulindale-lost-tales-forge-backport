package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.quest.QuestItemNames;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The missive templates the server's boards post notices from (QUEST-42):
 * the bundled ones, unless {@code bundledMissiveTemplates} leaves them
 * out, and a server's own in {@code config/losttales/missives/}, one of a
 * bundled template's id taking its place. A gather whose item the game
 * does not have is left out. Read as the server starts and on
 * {@code /losttales reload missives}, which reads
 * {@code bundledMissiveTemplates} again too; cleared as it starts and stops. With none left a board posts nothing, and the log says
 * so once.
 */
public final class MissiveTemplates {
    private static List<MissiveTemplate> templates = Collections.<MissiveTemplate>emptyList();
    private static boolean toldNone;

    private MissiveTemplates() {}

    /** Reads every template again; answers why each file left out was left out. */
    public static List<String> load() {
        return load(ProgressionFiles.CLASSPATH,
                ProgressionFiles.folder(MissiveTemplateFiles.FOLDER),
                LostTalesConfig.bundledMissiveTemplates, QuestItemNames.GAME);
    }

    /**
     * Reads the bundled files from {@code bundledSource} where
     * {@code bundled} lets them, and a server's own from {@code folder}.
     */
    public static synchronized List<String> load(DataFiles.Source bundledSource, File folder,
                                                 boolean bundled, QuestItemNames.Items items) {
        List<String> problems = new ArrayList<String>();
        List<MissiveTemplate> bundledTemplates = new ArrayList<MissiveTemplate>();
        if (bundled) {
            DataFiles.Result<MissiveTemplate> read = DataFiles.readBundled(bundledSource,
                    MissiveTemplateFiles.INDEX, MissiveTemplateFiles.LIST_KEY,
                    MissiveTemplateFiles.BUNDLED);
            note(problems, "Bundled missive template", read.problems);
            bundledTemplates.addAll(known(read.values, items, problems,
                    "Bundled missive template"));
        }
        DataFiles.Result<MissiveTemplate> own = DataFiles.readFolder(folder,
                MissiveTemplateFiles.SERVER);
        note(problems, "Server missive template", own.problems);
        List<MissiveTemplate> ownTemplates = known(own.values, items, problems,
                "Server missive template");
        set(DataFiles.merge(bundledTemplates, ownTemplates, MissiveTemplateFiles.SERVER));
        return problems;
    }

    /** Puts a set of templates in place, as a load does; for tests and the loader alike. */
    static synchronized void set(List<MissiveTemplate> loaded) {
        templates = Collections.unmodifiableList(new ArrayList<MissiveTemplate>(loaded));
        toldNone = false;
    }

    public static synchronized void clear() {
        templates = Collections.<MissiveTemplate>emptyList();
        toldNone = false;
    }

    /** Every template, the bundled ones in their index's order and a server's own after. */
    public static synchronized List<MissiveTemplate> templates() {
        return templates;
    }

    /**
     * A template for a new notice: a kill or a gather, each as likely
     * where there are both, then one of that kind, each as likely. Null
     * where there is none, which the log says once.
     */
    public static synchronized MissiveTemplate pick(Random random) {
        List<MissiveTemplate> kills = new ArrayList<MissiveTemplate>();
        List<MissiveTemplate> gathers = new ArrayList<MissiveTemplate>();
        for (MissiveTemplate template : templates) {
            (template.isKill() ? kills : gathers).add(template);
        }
        if (kills.isEmpty() && gathers.isEmpty()) {
            if (!toldNone) {
                toldNone = true;
                LostTalesLog.warning("No missive template is loaded, so missive boards"
                        + " post no notice; see config/losttales/missives/ and"
                        + " bundledMissiveTemplates");
            }
            return null;
        }
        List<MissiveTemplate> kind = kills.isEmpty() ? gathers : gathers.isEmpty() ? kills
                : random.nextInt(2) == 0 ? kills : gathers;
        return kind.get(random.nextInt(kind.size()));
    }

    private static List<MissiveTemplate> known(List<MissiveTemplate> read,
                                               QuestItemNames.Items items,
                                               List<String> problems, String what) {
        List<MissiveTemplate> known = new ArrayList<MissiveTemplate>();
        for (MissiveTemplate template : read) {
            List<String> lacking = MissiveTemplateFiles.itemProblems(template, items);
            if (lacking.isEmpty()) {
                known.add(template);
            } else {
                note(problems, what, lacking);
            }
        }
        return known;
    }

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }
}
