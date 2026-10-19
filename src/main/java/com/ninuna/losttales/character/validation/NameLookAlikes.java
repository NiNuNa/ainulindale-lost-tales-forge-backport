package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.util.LostTalesLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The letters and digits that look like others, from Unicode's table of
 * confusables (UTS #39), cut to what a name key holds: each reads as the
 * letters it is listed with, lower case and without accents, and an
 * apostrophe-like letter as nothing. Read once from the bundled list; a
 * list that is missing or unreadable folds no letter, and says so once in
 * the log.
 */
final class NameLookAlikes {
    static final String RESOURCE = "assets/losttales/names/look_alikes.txt";
    /** Bounds for the bundled list: about 2,100 lines of a few code points each. */
    private static final int MAX_ENTRIES = 8192;
    private static final int MAX_TARGET_CODE_POINTS = 16;

    private NameLookAlikes() {}

    /** The letters {@code codePoint} reads as, or null where it reads as itself. */
    static String of(int codePoint) {
        return Holder.TABLE.get(Integer.valueOf(codePoint));
    }

    /**
     * Reads a list: a line is a source code point and the code points it
     * reads as, in hexadecimal; a line starting with {@code #} is a
     * comment, and a line out of shape is skipped.
     */
    static Map<Integer, String> read(BufferedReader reader) throws IOException {
        Map<Integer, String> table = new HashMap<Integer, String>();
        String line;
        while (table.size() < MAX_ENTRIES && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            String[] fields = line.split(" +");
            if (fields.length > MAX_TARGET_CODE_POINTS + 1) {
                continue;
            }
            try {
                int source = Integer.parseInt(fields[0], 16);
                StringBuilder target = new StringBuilder();
                for (int index = 1; index < fields.length; index++) {
                    int codePoint = Integer.parseInt(fields[index], 16);
                    if (!Character.isValidCodePoint(codePoint)) {
                        target = null;
                        break;
                    }
                    target.appendCodePoint(codePoint);
                }
                if (target != null && Character.isValidCodePoint(source)) {
                    table.put(Integer.valueOf(source), target.toString());
                }
            } catch (NumberFormatException outOfShape) {
                // Skipped: the rest of the list still reads.
            }
        }
        return table;
    }

    private static Map<Integer, String> load() {
        InputStream input = NameLookAlikes.class.getClassLoader()
                .getResourceAsStream(RESOURCE);
        if (input == null) {
            LostTalesLog.warning("The look-alike letter list %s is missing; character names"
                    + " are compared without folding look-alike letters", RESOURCE);
            return Collections.emptyMap();
        }
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, Charset.forName("UTF-8")));
            try {
                return Collections.unmodifiableMap(read(reader));
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            LostTalesLog.warning("The look-alike letter list %s could not be read; character"
                    + " names are compared without folding look-alike letters: %s", RESOURCE, e);
        } catch (RuntimeException e) {
            LostTalesLog.warning("The look-alike letter list %s could not be read; character"
                    + " names are compared without folding look-alike letters: %s", RESOURCE, e);
        }
        return Collections.emptyMap();
    }

    private static final class Holder {
        static final Map<Integer, String> TABLE = load();
    }
}
