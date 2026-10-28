package com.ninuna.losttales.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The small text files the client keeps its own choices in, read and
 * written a line at a time in UTF-8. A read takes no line longer than
 * {@link #MAX_LINE_CHARACTERS} and no more than
 * {@link #MAX_FILE_CHARACTERS} of a file, so a file grown or written by
 * hand past what the game writes cannot hold the game up. A write that
 * fails never breaks the game: it is logged, naming the file and the
 * error, and not logged again for that file until a write of it has
 * succeeded, so a folder that cannot be written does not fill the log.
 */
public final class LostTalesTextFiles {
    /** The longest line read; a longer one is left out whole. */
    public static final int MAX_LINE_CHARACTERS = 16384;
    /** The most of a file read; the lines past it are left out. */
    public static final int MAX_FILE_CHARACTERS = 1024 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /**
     * The files whose failed write has been logged and not written since,
     * by absolute path. It only keeps the log quiet, so it lasts as long
     * as the game does.
     */
    private static final Set<String> FAILING = new HashSet<String>();

    private LostTalesTextFiles() {}

    /**
     * The file's lines, at most {@code maxLines} of them, within the
     * bounds every read keeps ({@link #linesOf}); null when
     * there is no such file or it cannot be read.
     */
    public static List<String> readLines(File file, int maxLines) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            return linesOf(reader, maxLines);
        } catch (IOException unreadable) {
            return null;
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
    }

    /**
     * The lines {@code reader} holds, at most {@code maxLines} of them,
     * each ended by a line feed, a carriage return or both: a line longer
     * than {@link #MAX_LINE_CHARACTERS} is left out whole, and reading
     * stops after {@link #MAX_FILE_CHARACTERS}. No line is held whole
     * before its length is known.
     */
    static List<String> linesOf(Reader reader, int maxLines) throws IOException {
        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        boolean tooLong = false;
        boolean afterReturn = false;
        long read = 0L;
        int next;
        while (lines.size() < maxLines && read < MAX_FILE_CHARACTERS
                && (next = reader.read()) >= 0) {
            read++;
            char letter = (char)next;
            if (letter == '\n' && afterReturn) {
                afterReturn = false;
                continue;
            }
            afterReturn = letter == '\r';
            if (letter == '\n' || letter == '\r') {
                if (!tooLong) {
                    lines.add(line.toString());
                }
                line.setLength(0);
                tooLong = false;
            } else if (!tooLong) {
                if (line.length() < MAX_LINE_CHARACTERS) {
                    line.append(letter);
                } else {
                    tooLong = true;
                    line.setLength(0);
                }
            }
        }
        if (lines.size() < maxLines && read < MAX_FILE_CHARACTERS
                && !tooLong && line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Writes the lines over the file, each ended by a newline and a null
     * one as an empty line, making its folder first. A line break inside
     * a line is written as a space, so one entry stays one line whatever
     * words a server put in it. Answers whether the file was written; a
     * failure is reported by {@link #writeFailed}.
     */
    public static boolean writeLines(File file, Iterable<String> lines) {
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            writeFailed(file, "its folder could not be made");
            return false;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                    new FileOutputStream(file), UTF_8);
            for (String line : lines) {
                writer.write(line == null ? ""
                        : line.replace('\n', ' ').replace('\r', ' '));
                writer.write('\n');
            }
            // Closed here, so bytes that fail to reach the disk as it
            // closes count as a failed write.
            writer.close();
        } catch (IOException unwritable) {
            writeFailed(file, unwritable.toString());
            return false;
        } finally {
            LostTalesCloseables.closeQuietly(writer);
        }
        writeSucceeded(file);
        return true;
    }

    /**
     * Notes that {@code file} could not be written and logs why, unless
     * that has been logged since its last successful write. Answers
     * whether it was logged.
     */
    public static synchronized boolean writeFailed(File file, String reason) {
        if (file == null || !FAILING.add(file.getAbsolutePath())) {
            return false;
        }
        LostTalesLog.warning("Could not write %s (%s); check that the file "
                + "and its folder can be written. This is not logged again "
                + "until a write of it succeeds.", file.getAbsolutePath(),
                reason);
        return true;
    }

    /** Notes that {@code file} was written, so its next failure is logged again. */
    public static synchronized void writeSucceeded(File file) {
        if (file != null) {
            FAILING.remove(file.getAbsolutePath());
        }
    }
}
