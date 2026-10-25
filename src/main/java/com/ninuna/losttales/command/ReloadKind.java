package com.ninuna.losttales.command;

import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The kinds of content {@code /losttales reload} reads again (DATA-11),
 * each opened by its own system's capability (I82): quests and missive
 * templates by {@code quest.admin}, skills by {@code progression.admin},
 * map markers by {@code mapmarker.manage}, races, capes, starting kits and
 * lore characters by {@code character.admin}; {@code all} by
 * {@code server.config}. Each names its words and its count by a line of
 * the lang file.
 */
enum ReloadKind {
    QUESTS("quests", LostTalesCapability.QUEST_ADMIN,
            "chat.losttales.command.reload.kind.quests",
            "chat.losttales.command.reload.count.quests", null),
    SKILLS("skills", LostTalesCapability.PROGRESSION_ADMIN,
            "chat.losttales.command.reload.kind.skills",
            "chat.losttales.command.reload.count.skills", null),
    MARKERS("markers", LostTalesCapability.MAPMARKER_MANAGE,
            "chat.losttales.command.reload.kind.markers",
            "chat.losttales.command.reload.count.markers",
            "chat.losttales.command.reload.note.markers"),
    /** The races and, checked against them, the capes. */
    RACES("races", LostTalesCapability.CHARACTER_ADMIN,
            "chat.losttales.command.reload.kind.races",
            "chat.losttales.command.reload.count.races",
            "chat.losttales.command.reload.note.races"),
    CAPES("capes", LostTalesCapability.CHARACTER_ADMIN,
            "chat.losttales.command.reload.kind.capes",
            "chat.losttales.command.reload.count.capes", null),
    KITS("kits", LostTalesCapability.CHARACTER_ADMIN,
            "chat.losttales.command.reload.kind.kits",
            "chat.losttales.command.reload.count.kits", null),
    MISSIVES("missives", LostTalesCapability.QUEST_ADMIN,
            "chat.losttales.command.reload.kind.missives",
            "chat.losttales.command.reload.count.missives", null),
    LORE("lore", LostTalesCapability.CHARACTER_ADMIN,
            "chat.losttales.command.reload.kind.lore",
            "chat.losttales.command.reload.count.lore", null);

    /** The word that asks for every kind. */
    static final String ALL = "all";
    /** What {@link #ALL} needs: the server's own settings' capability. */
    static final LostTalesCapability ALL_CAPABILITY = LostTalesCapability.SERVER_CONFIG;
    /**
     * What {@link #ALL} reads, in the order the server reads them as it
     * starts, so each is checked against what it names: the capes come
     * with the races, the lore characters after them.
     */
    private static final List<ReloadKind> EVERY = Collections.unmodifiableList(Arrays.asList(
            QUESTS, SKILLS, RACES, LORE, MARKERS, KITS, MISSIVES));

    /** Asked of a sender: whether it holds a capability. */
    interface Holder {
        boolean holds(LostTalesCapability capability);
    }

    final String id;
    final LostTalesCapability capability;
    /** The lang key of what the kind is called in an answer: {@code the races and capes}. */
    final String wordsKey;
    /** The lang key of what a read of it holds, its numbers as arguments. */
    final String countKey;
    /** The lang key of a line said after a read of it; null for none. */
    final String noteKey;

    ReloadKind(String id, LostTalesCapability capability, String wordsKey, String countKey,
               String noteKey) {
        this.id = id;
        this.capability = capability;
        this.wordsKey = wordsKey;
        this.countKey = countKey;
        this.noteKey = noteKey;
    }

    /** The kinds {@code typed} asks for: one, or every kind for {@code all}; empty for none. */
    static List<ReloadKind> asked(String typed) {
        String word = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        if (ALL.equals(word)) {
            return EVERY;
        }
        for (ReloadKind kind : values()) {
            if (kind.id.equals(word)) {
                return Collections.singletonList(kind);
            }
        }
        return Collections.emptyList();
    }

    /** The capability {@code typed} needs: its kind's, {@link #ALL_CAPABILITY} for all; null for no kind. */
    static LostTalesCapability capabilityFor(String typed) {
        String word = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        if (ALL.equals(word)) {
            return ALL_CAPABILITY;
        }
        List<ReloadKind> kinds = asked(word);
        return kinds.isEmpty() ? null : kinds.get(0).capability;
    }

    /** Every word the command takes, each kind's then {@code all}. */
    static List<String> words() {
        List<String> words = new ArrayList<String>();
        for (ReloadKind kind : values()) {
            words.add(kind.id);
        }
        words.add(ALL);
        return words;
    }

    /** The words a sender holding what {@code holder} answers may type. */
    static List<String> wordsFor(Holder holder) {
        List<String> words = new ArrayList<String>();
        for (String word : words()) {
            if (holder.holds(capabilityFor(word))) {
                words.add(word);
            }
        }
        return words;
    }

    /** The lowest operator level that reads some kind again without any role. */
    static int lowestOperatorLevel() {
        int lowest = ALL_CAPABILITY.getRequiredOpLevel();
        for (ReloadKind kind : values()) {
            lowest = Math.min(lowest, kind.capability.getRequiredOpLevel());
        }
        return lowest;
    }
}
