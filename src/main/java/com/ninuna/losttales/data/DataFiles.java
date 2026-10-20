package com.ninuna.losttales.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ninuna.losttales.util.LostTalesCloseables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mod's content as data files, bundled and a server's or a player's
 * own, read as the quest files are (MOD-17). The bundled ones are listed
 * in an index under the mod's assets and read from the classpath or a
 * resource pack; a server's own lie in a folder under
 * {@code config/losttales/}, a player's own under
 * {@code config/losttales/client/}, one JSON file each, sorted by name. A
 * file that cannot be read, takes an id an earlier file of its side has,
 * or raises any warning is left out, with a line for each reason; the
 * others still load. A folder's file with a bundled file's id takes that
 * file's place.
 */
public final class DataFiles {
    public static final int MAX_FILES = 256;
    public static final long MAX_FILE_BYTES = 64L * 1024L;

    private DataFiles() {}

    /** Where bundled files are read from. */
    public interface Source {
        /** The file at {@code path} under the mod's assets; null when there is none. */
        Reader open(String path) throws IOException;
    }

    /** One kind of file: how it is read, named and checked. */
    public interface Kind<T> {
        /** The thing a file writes, or null for a file that is none; {@code said} told what could not be read. */
        T parse(Reader reader, String file, List<String> said);

        String idOf(T value);

        /** Every warning the thing raises, beyond what its reading said. */
        List<String> warnings(T value);
    }

    /** What a read found: the things in order, and why each other file was left out. */
    public static final class Result<T> {
        public final List<T> values;
        public final List<String> problems;

        public Result(List<T> values, List<String> problems) {
            this.values = Collections.unmodifiableList(new ArrayList<T>(values));
            this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
        }
    }

    /** The bundled files {@code indexFile}'s {@code listKey} names, in its order. */
    public static <T> Result<T> readBundled(Source source, String indexFile,
                                            String listKey, Kind<T> kind) {
        List<String> problems = new ArrayList<String>();
        Map<String, T> byId = new LinkedHashMap<String, T>();
        for (String file : index(source, indexFile, listKey, problems)) {
            Reader reader = null;
            try {
                reader = source.open(file);
                if (reader == null) {
                    problems.add(file + " is named by the index but missing");
                    continue;
                }
                accept(kind, reader, file, byId, problems);
            } catch (IOException unreadable) {
                problems.add(file + " cannot be read: " + unreadable.getMessage());
            } catch (RuntimeException malformed) {
                problems.add(file + " cannot be read: " + malformed.getMessage());
            } finally {
                LostTalesCloseables.closeQuietly(reader);
            }
        }
        return new Result<T>(new ArrayList<T>(byId.values()), problems);
    }

    /** Every {@code .json} file directly in {@code folder}, sorted by name, the folder made if missing. */
    public static <T> Result<T> readFolder(File folder, Kind<T> kind) {
        List<String> problems = new ArrayList<String>();
        Map<String, T> byId = new LinkedHashMap<String, T>();
        if (folder == null) {
            return new Result<T>(new ArrayList<T>(), problems);
        }
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            problems.add("cannot make the folder " + folder.getPath());
            return new Result<T>(new ArrayList<T>(), problems);
        }
        File[] candidates = folder.listFiles();
        if (candidates == null) {
            problems.add("cannot list the folder " + folder.getPath());
            return new Result<T>(new ArrayList<T>(), problems);
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
            problems.add("the folder holds " + files.size() + " files; only the first "
                    + MAX_FILES + " by name are read");
            files = new ArrayList<File>(files.subList(0, MAX_FILES));
        }
        for (File file : files) {
            String name = file.getName();
            if (!isDirectChild(folder, file)) {
                problems.add(name + " lies outside the folder");
                continue;
            }
            if (file.length() > MAX_FILE_BYTES) {
                problems.add(name + " is larger than " + MAX_FILE_BYTES + " bytes");
                continue;
            }
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(file),
                        StandardCharsets.UTF_8);
                accept(kind, reader, name, byId, problems);
            } catch (IOException unreadable) {
                problems.add(name + " cannot be read: " + unreadable.getMessage());
            } catch (RuntimeException malformed) {
                problems.add(name + " cannot be read: " + malformed.getMessage());
            } finally {
                LostTalesCloseables.closeQuietly(reader);
            }
        }
        return new Result<T>(new ArrayList<T>(byId.values()), problems);
    }

    /** The bundled things with a server's own in place of those of the same id, and its new ones after. */
    public static <T> List<T> merge(List<T> bundled, List<T> server, Kind<T> kind) {
        Map<String, T> byId = new LinkedHashMap<String, T>();
        for (T value : bundled) {
            byId.put(kind.idOf(value), value);
        }
        for (T value : server) {
            byId.put(kind.idOf(value), value);
        }
        return new ArrayList<T>(byId.values());
    }

    private static <T> void accept(Kind<T> kind, Reader reader, String file,
                                   Map<String, T> byId, List<String> problems) {
        List<String> said = new ArrayList<String>();
        T value = kind.parse(reader, file, said);
        if (value == null) {
            problems.add(file + " is not a JSON object");
            return;
        }
        String id = kind.idOf(value);
        if (byId.containsKey(id)) {
            problems.add(file + ": the id " + id + " is an earlier file's");
            return;
        }
        List<String> warnings = new ArrayList<String>(said);
        warnings.addAll(kind.warnings(value));
        if (!warnings.isEmpty()) {
            for (String warning : warnings) {
                problems.add(file + ": " + warning);
            }
            return;
        }
        byId.put(id, value);
    }

    private static List<String> index(Source source, String indexFile,
                                      String listKey, List<String> problems) {
        Reader reader = null;
        try {
            reader = source.open(indexFile);
            if (reader == null) {
                problems.add(indexFile + " is missing, so no bundled file loads");
                return Collections.emptyList();
            }
            JsonElement root = new JsonParser().parse(reader);
            JsonElement list = root != null && root.isJsonObject()
                    ? root.getAsJsonObject().get(listKey) : null;
            if (list == null || !list.isJsonArray()) {
                problems.add(indexFile + " holds no \"" + listKey
                        + "\" list, so no bundled file loads");
                return Collections.emptyList();
            }
            List<String> files = new ArrayList<String>();
            JsonArray array = list.getAsJsonArray();
            for (JsonElement entry : array) {
                String file = entry != null && entry.isJsonPrimitive()
                        && entry.getAsJsonPrimitive().isString()
                        ? entry.getAsString().trim() : "";
                if (file.length() == 0) {
                    problems.add(indexFile + " names an empty file, so no bundled file loads");
                    return Collections.emptyList();
                }
                files.add(file);
            }
            return files;
        } catch (IOException unreadable) {
            problems.add(indexFile + " cannot be read, so no bundled file loads: "
                    + unreadable.getMessage());
        } catch (RuntimeException malformed) {
            problems.add(indexFile + " cannot be read, so no bundled file loads: "
                    + malformed.getMessage());
        } finally {
            LostTalesCloseables.closeQuietly(reader);
        }
        return Collections.emptyList();
    }

    private static boolean isDirectChild(File folder, File file) {
        try {
            return folder.getCanonicalFile().equals(file.getCanonicalFile().getParentFile());
        } catch (IOException unknowable) {
            return false;
        }
    }
}
