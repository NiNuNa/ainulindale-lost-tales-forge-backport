package com.ninuna.losttales.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The small text files the client keeps its own choices in, read and
 * written a line at a time in UTF-8. A write that fails never breaks the
 * game: it is logged, naming the file and the error, and not logged again
 * for that file until a write of it has succeeded, so a folder that
 * cannot be written does not fill the log.
 */
public final class LostTalesTextFiles {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /**
     * The files whose failed write has been logged and not written since,
     * by absolute path. It only keeps the log quiet, so it lasts as long
     * as the game does.
     */
    private static final Set<String> FAILING = new HashSet<String>();

    private LostTalesTextFiles() {}

    /**
     * The file's lines, at most {@code maxLines} of them; null when there
     * is no such file or it cannot be read.
     */
    public static List<String> readLines(File file, int maxLines) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            List<String> lines = new ArrayList<String>();
            String line;
            while (lines.size() < maxLines
                    && (line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException unreadable) {
            return null;
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
    }

    /**
     * Writes the lines over the file, each ended by a newline and a null
     * one as an empty line, making its folder first. Answers whether the
     * file was written; a failure is reported by {@link #writeFailed}.
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
                writer.write(line == null ? "" : line);
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
