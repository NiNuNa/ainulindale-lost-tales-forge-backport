package com.ninuna.losttales.quest;

import com.ninuna.losttales.util.LostTalesCloseables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The quests a server writes for itself: one JSON file each, directly in
 * {@code config/losttales/quests/}, in the same form as the quests bundled
 * with the mod. They
 * are read when the server starts and again on
 * {@code /losttales quest reload}, and sent to every player, so a server
 * adds quests without a new build of the mod.
 *
 * <p>A file is left out, with a line in the log saying why, when it is too
 * large, cannot be read, takes an id a bundled quest or an earlier file
 * already has, takes a missive's id, or raises any warning the bundled
 * quests are checked for. The others still load.</p>
 */
public final class ServerQuestFiles {
    /** The folder under Forge's config directory. */
    public static final String DIRECTORY = "losttales/quests";
    public static final int MAX_FILES = 256;
    public static final long MAX_FILE_BYTES = 64L * 1024L;
    /** The id path missives are made under; no written quest may take it. */
    private static final String MISSIVE_PATH = "missive/";

    private static File directory;

    private ServerQuestFiles() {}

    /** Where the files are, from Forge's config directory; set once as the mod loads. */
    public static synchronized void configure(File modConfigurationDirectory) {
        directory = modConfigurationDirectory == null ? null
                : new File(modConfigurationDirectory, DIRECTORY);
    }

    public static synchronized File directory() {
        return directory;
    }

    /** What a read found: the quests it can use, and why it left each other file out. */
    public static final class Result {
        public final List<LostTalesQuestDefinition> quests;
        public final List<String> problems;

        Result(List<LostTalesQuestDefinition> quests, List<String> problems) {
            this.quests = Collections.unmodifiableList(quests);
            this.problems = Collections.unmodifiableList(problems);
        }
    }

    /**
     * Reads every {@code .json} file directly in {@code folder}, sorted by
     * name, the folder made if it is missing. {@code takenIds} are the
     * bundled quests' ids, which no file may take.
     */
    public static Result read(File folder, Collection<String> takenIds) {
        List<LostTalesQuestDefinition> quests =
                new ArrayList<LostTalesQuestDefinition>();
        List<String> problems = new ArrayList<String>();
        if (folder == null) {
            return new Result(quests, problems);
        }
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            problems.add("cannot make the folder " + folder.getPath());
            return new Result(quests, problems);
        }
        File[] candidates = folder.listFiles();
        if (candidates == null) {
            problems.add("cannot list the folder " + folder.getPath());
            return new Result(quests, problems);
        }
        List<File> files = new ArrayList<File>();
        for (File candidate : candidates) {
            if (candidate != null && candidate.isFile() && candidate.getName()
                    .toLowerCase(Locale.ROOT).endsWith(".json")) {
                files.add(candidate);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        if (files.size() > MAX_FILES) {
            problems.add("the folder holds " + files.size()
                    + " quest files; only the first " + MAX_FILES
                    + " by name are read");
            files = new ArrayList<File>(files.subList(0, MAX_FILES));
        }
        Set<String> ids = new HashSet<String>();
        if (takenIds != null) {
            ids.addAll(takenIds);
        }
        for (File file : files) {
            String name = file.getName();
            if (!isDirectChild(folder, file)) {
                problems.add(name + " lies outside the quest folder");
                continue;
            }
            if (file.length() > MAX_FILE_BYTES) {
                problems.add(name + " is larger than " + MAX_FILE_BYTES
                        + " bytes");
                continue;
            }
            LostTalesQuestDefinition quest = parse(file, problems);
            if (quest == null) {
                continue;
            }
            String problem = refusal(quest, ids);
            if (problem.length() > 0) {
                problems.add(name + ": " + problem);
                continue;
            }
            ids.add(quest.getId());
            quests.add(quest);
        }
        return new Result(quests, problems);
    }

    /**
     * Why a quest cannot load beside the ones already taken, in words;
     * empty when it can.
     */
    static String refusal(LostTalesQuestDefinition quest, Set<String> taken) {
        String id = quest.getId();
        if (taken.contains(id)) {
            return "the id " + id + " is already a quest's";
        }
        int colon = id.indexOf(':');
        String path = (colon >= 0 ? id.substring(colon + 1) : id)
                .toLowerCase(Locale.ROOT);
        if (path.startsWith(MISSIVE_PATH)) {
            return "the id " + id + " is a missive's";
        }
        if (quest.getStages().isEmpty()) {
            return "it has no stages";
        }
        List<String> warnings = LostTalesQuestDefinitionValidator
                .describeWarnings(Collections.singletonList(quest));
        return warnings.isEmpty() ? "" : warnings.get(0);
    }

    private static LostTalesQuestDefinition parse(File file,
                                                  List<String> problems) {
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file),
                    StandardCharsets.UTF_8);
            LostTalesQuestDefinition quest = LostTalesQuestDefinitionJsonParser
                    .parseQuest(reader, "quests/" + file.getName());
            if (quest == null) {
                problems.add(file.getName() + " is not a quest");
            }
            return quest;
        } catch (IOException unreadable) {
            problems.add(file.getName() + " cannot be read: "
                    + unreadable.getMessage());
            return null;
        } catch (RuntimeException malformed) {
            problems.add(file.getName() + " is not a quest: "
                    + malformed.getMessage());
            return null;
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
    }

    private static boolean isDirectChild(File folder, File file) {
        try {
            return folder.getCanonicalFile().equals(
                    file.getCanonicalFile().getParentFile());
        } catch (IOException unknowable) {
            return false;
        }
    }
}
