package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.client.window.PageSearch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Motion Lab's list, as the page draws it and the pointer hits it:
 * every motion under the heading of its family, the families in the
 * order their files are read and each family's motions in its file's
 * order. The well's words keep the motions whose id or about text holds
 * every one of them, and a family keeps its heading only while it keeps
 * a motion.
 *
 * <p>Free of Minecraft: the page hands it the motions in force, and a
 * test can ask it the same questions the page does.</p>
 */
final class MotionLabList {
    /** What a line is. */
    enum Kind { FAMILY, MOTION }

    /** One line: a family's heading, or a motion under it. */
    static final class Line {
        final Kind kind;
        final String family;
        /** The motion's id; empty on a heading. */
        final String id;

        private Line(Kind kind, String family, String id) {
            this.kind = kind;
            this.family = family;
            this.id = id;
        }

        boolean isMotion() {
            return this.kind == Kind.MOTION;
        }
    }

    /** Where the list reads the motions from: the ones in force in the game, fixed ones in a test. */
    interface Source {
        /** Every motion's id, in file order. */
        List<String> ids();

        /** The family a motion's file is; empty for none. */
        String family(String id);

        /** What the motion is for. */
        String about(String id);
    }

    private MotionLabList() {}

    /**
     * The lines for {@code families}, in that order, the search held. A
     * motion of a family not named is left out, as is a family with no
     * motion left.
     */
    static List<Line> of(List<String> families, Source source, String query) {
        List<Line> lines = new ArrayList<Line>();
        if (families == null || source == null) {
            return lines;
        }
        PageSearch search = PageSearch.of(query);
        List<String> ids = source.ids();
        for (String family : families) {
            boolean headed = false;
            for (String id : ids) {
                if (!family.equals(source.family(id))
                        || !search.matches(id, source.about(id))) {
                    continue;
                }
                if (!headed) {
                    lines.add(new Line(Kind.FAMILY, family, ""));
                    headed = true;
                }
                lines.add(new Line(Kind.MOTION, family, id));
            }
        }
        return lines;
    }

    /** The motions among the lines, in order. */
    static List<String> motions(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>();
        for (Line line : lines) {
            if (line.isMotion()) {
                ids.add(line.id);
            }
        }
        return ids;
    }

    /** Whether {@code id} is one of the motions among the lines. */
    static boolean holds(List<Line> lines, String id) {
        return id != null && motions(lines).contains(id);
    }

    /**
     * The motion {@code step} places after {@code from} among the lines,
     * held at the first and the last; the first where {@code from} is not
     * among them, and null where no motion is.
     */
    static String step(List<Line> lines, String from, int step) {
        List<String> ids = motions(lines);
        if (ids.isEmpty()) {
            return null;
        }
        int at = ids.indexOf(from);
        if (at < 0) {
            return ids.get(0);
        }
        return ids.get(Math.max(0, Math.min(ids.size() - 1, at + step)));
    }

    /** A motion's name in the list: its id after the family, which the heading above it already says. */
    static String shortName(String id) {
        if (id == null) {
            return "";
        }
        int dot = id.indexOf('.');
        return dot < 0 || dot == id.length() - 1 ? id : id.substring(dot + 1);
    }
}
