package com.ninuna.losttales.world.biome;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The biome classes are the mod's own code (CONTENT-13): no source under
 * {@code world/biome} carries a decompiler's marks, its numbered locals
 * ({@code var10000}) or its header comments.
 */
public final class BiomeSourcesTest {

    private static final String BIOME_SOURCES = "src/main/java/com/ninuna/losttales/world/biome";
    private static final Pattern DECOMPILER_MARK = Pattern.compile(
            "\\bvar\\d+\\b|\\$FF:|(?i)decompiled (?:by|with)");

    @Test
    public void noBiomeSourceCarriesADecompilersMarks() throws IOException {
        List<File> files = new ArrayList<File>();
        collect(biomeSources(), files);
        assertTrue("No biome sources were found, so this test proved nothing", files.size() >= 4);
        List<String> found = new ArrayList<String>();
        for (File file : files) {
            String[] lines = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = DECOMPILER_MARK.matcher(lines[i]);
                if (matcher.find()) {
                    found.add(file.getName() + ":" + (i + 1) + " " + matcher.group());
                }
            }
        }
        assertTrue("Decompiler marks in the biome sources: " + found, found.isEmpty());
    }

    private static File biomeSources() throws IOException {
        File directory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (directory != null) {
            File candidate = new File(directory, BIOME_SOURCES);
            if (candidate.isDirectory()) {
                return candidate;
            }
            directory = directory.getParentFile();
        }
        throw new IOException("No " + BIOME_SOURCES + " at or above the working "
                + "directory; run this test from the repository");
    }

    private static void collect(File directory, List<File> files) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collect(entry, files);
            } else if (entry.getName().endsWith(".java")) {
                files.add(entry);
            }
        }
    }
}
