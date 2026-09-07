package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.config.LostTalesConfigFiles;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Where an account's default-character template is kept: one file per
 * Minecraft account under {@code config/losttales/client/templates/}.
 *
 * <p>Per account rather than per installation, so a machine two people
 * share keeps a template each and one person with two accounts keeps one
 * for each. An installation that cannot say which account it is signed in
 * as reads and writes nothing rather than sharing somebody else's.</p>
 *
 * <p>A line is {@code key=value}; anything else is skipped, and a key
 * this build does not know is kept as it was so a file written by a later
 * one is not thinned out by an earlier one. Nothing here is validated
 * beyond its length: a template is what the creation form opens with, and
 * every server decides for itself what it will accept.</p>
 */
public final class CharacterTemplateStore {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** A file longer than this is not a template. */
    static final int MAX_LINES = 64;
    /** A stored value is bounded by the longest field a template holds. */
    static final int MAX_VALUE_LENGTH = CharacterValidator.MAX_DESCRIPTION_LENGTH;

    private static final String KEY_NAME = "name";
    private static final String KEY_RACE = "race";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_SKIN = "skin";
    private static final String KEY_BODY = "body";
    private static final String KEY_CHEST = "chest";
    private static final String KEY_FACTION = "faction";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_AGE = "age";
    private static final String KEY_UNCONVENTIONAL = "unconventional";

    /** The folder under the client's own, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHARACTER_TEMPLATES;

    private static File templateFolder;

    private CharacterTemplateStore() {}

    /** Told where the client's config folder is, once, as it starts. */
    public static synchronized void initialize(File clientDirectory) {
        templateFolder = clientDirectory == null
                ? null : new File(clientDirectory, FOLDER);
    }

    /**
     * The template this account last saved, or an empty one when it has
     * none, when the account is unknown, or when the file cannot be read.
     * A template is a convenience; failing to read one is never an error
     * the player has to deal with.
     */
    public static synchronized CharacterTemplate load(UUID accountId) {
        File file = fileFor(accountId);
        if (file == null || !file.isFile()) {
            return CharacterTemplate.EMPTY;
        }
        Map<String, String> values = read(file);
        if (values.isEmpty()) {
            return CharacterTemplate.EMPTY;
        }
        return new CharacterTemplate(
                values.get(KEY_NAME), values.get(KEY_RACE),
                values.get(KEY_GENDER), values.get(KEY_SKIN),
                values.get(KEY_BODY), values.get(KEY_CHEST),
                values.get(KEY_FACTION), values.get(KEY_DESCRIPTION),
                parseAge(values.get(KEY_AGE)),
                Boolean.parseBoolean(values.get(KEY_UNCONVENTIONAL)));
    }

    /**
     * Remembers this template for the account. Answers whether it was
     * written; a template that cannot be saved costs the player nothing
     * but the convenience, so nothing above this has to handle it.
     */
    public static synchronized boolean save(UUID accountId,
                                            CharacterTemplate template) {
        File file = fileFor(accountId);
        if (file == null || template == null) {
            return false;
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put(KEY_NAME, template.getName());
        values.put(KEY_RACE, template.getRaceId());
        values.put(KEY_GENDER, template.getGenderId());
        values.put(KEY_SKIN, template.getSkinId());
        values.put(KEY_BODY, template.getBodyTypeId());
        values.put(KEY_CHEST, template.getChestTypeId());
        values.put(KEY_FACTION, template.getStartingFactionId());
        values.put(KEY_DESCRIPTION, template.getDescription());
        values.put(KEY_AGE, String.valueOf(template.getAge()));
        values.put(KEY_UNCONVENTIONAL,
                String.valueOf(template.hasUnconventionalSettings()));
        // Anything a later build wrote and this one does not know is put
        // back as it was, so saving here does not thin out that file.
        if (file.isFile()) {
            for (Map.Entry<String, String> stored : read(file).entrySet()) {
                if (!values.containsKey(stored.getKey())) {
                    values.put(stored.getKey(), stored.getValue());
                }
            }
        }
        List<String> lines = new ArrayList<String>(values.size());
        for (Map.Entry<String, String> value : values.entrySet()) {
            lines.add(line(value.getKey(), value.getValue()));
        }
        return write(file, lines);
    }

    /** Forgets this account's template. */
    public static synchronized boolean clear(UUID accountId) {
        File file = fileFor(accountId);
        return file != null && file.isFile() && file.delete();
    }

    /** Whether this account has a template worth opening a form from. */
    public static synchronized boolean has(UUID accountId) {
        return !load(accountId).isEmpty();
    }

    static File fileFor(UUID accountId) {
        if (templateFolder == null || accountId == null) {
            return null;
        }
        return new File(templateFolder,
                accountId.toString().toLowerCase(Locale.ROOT) + ".txt");
    }

    private static Map<String, String> read(File file) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            String line;
            int read = 0;
            while ((line = reader.readLine()) != null && read < MAX_LINES) {
                read++;
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim()
                        .toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (key.length() == 0 || value.length() > MAX_VALUE_LENGTH) {
                    continue;
                }
                values.put(key, value);
            }
        } catch (IOException unreadable) {
            return new LinkedHashMap<String, String>();
        } finally {
            closeQuietly(reader);
        }
        return values;
    }

    /**
     * Writes beside the file and moves it into place, so a template is
     * never half-written: an interrupted save leaves the previous one.
     */
    private static boolean write(File file, List<String> lines) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        File temporary = new File(parent, "." + file.getName() + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                    new FileOutputStream(temporary), UTF_8);
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
            writer.flush();
        } catch (IOException unwritable) {
            closeQuietly(writer);
            temporary.delete();
            return false;
        } finally {
            closeQuietly(writer);
        }
        if (file.isFile() && !file.delete()) {
            temporary.delete();
            return false;
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            return false;
        }
        return true;
    }

    private static String line(String key, String value) {
        String stored = value == null ? "" : value.trim();
        if (stored.length() > MAX_VALUE_LENGTH) {
            stored = stored.substring(0, MAX_VALUE_LENGTH);
        }
        // A newline in a value would read back as another entry.
        return key + "=" + stored.replace('\n', ' ').replace('\r', ' ');
    }

    private static int parseAge(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Losing a template write must never break the client.
            }
        }
    }
}
