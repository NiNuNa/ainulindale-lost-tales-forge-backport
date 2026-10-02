package com.ninuna.losttales.gui.style;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Every colour the mod's code draws with is one of the palette's entries,
 * at whatever alpha. This reads every source file under
 * {@code src/main/java}, takes each hex literal of six or eight digits
 * ({@code 0xRRGGBB}, {@code 0xAARRGGBB}) and fails, naming the file, the
 * line and the nearest entry, on one whose red, green and blue are not a
 * palette entry's. The palette is read from {@link LostTalesColors}.
 *
 * <p>The code around a literal says when it is not a colour: beside a bit
 * operator it is a mask or a shift, a {@code long} is a key or a hash, and
 * in a multiplication, on its own or inside the bracketed sum it stands
 * in, it is a hashing constant. Comments, strings and characters are not
 * read. The few colours left that the mod's interface does not draw (the
 * world's, or another mod's recognised and swapped) are {@link #ALLOWED},
 * each with its reason, and an entry that no longer matches fails too.</p>
 */
public final class LostTalesPaletteUseTest {

    private static final String SOURCE_ROOT = "src/main/java";
    /** Fewer files than this means the walk found the wrong place. */
    private static final int FEWEST_EXPECTED = 200;

    private static final Allowed[] ALLOWED = {
            new Allowed("ELostTalesEntity.java",
                    "spawn-egg colours, which vanilla draws on the eggs",
                    "0x8A5A38", "0xB45A32", "0x4F5A33", "0xC89B5A"),
            new Allowed("ELostTalesStructure.java",
                    "a structure spawner's colours, which LOTR draws on its item",
                    "0x8A5A38", "0xE3B04B"),
            new Allowed("LostTalesEntityOdaneMan.java",
                    "leather dyes the Odane wear in the world",
                    "0x6B3F2A", "0x8A5A38", "0x9A6B3F", "0x4F5A33"),
            new Allowed("ELostTalesBiome.java",
                    "each biome's key in LOTR's map image, one colour per biome",
                    "0x78A85A", "0x627E4B"),
            new Allowed("LostTalesBiomeGenOdaneIsland.java",
                    "grass and leaves in the world, tinted by vanilla",
                    "0x6FA557", "0x5C8F46"),
            new Allowed("LostTalesBiomeGenOdaneMountains.java",
                    "grass and leaves in the world, tinted by vanilla",
                    "0x607F47", "0x526E3B"),
            new Allowed("LostTalesMapOverlay.java",
                    "the overlay image's keys for the Odane biomes; never drawn",
                    "0xB8563F", "0x7C2F35"),
            new Allowed("LostTalesMapTerrainRenderer.java",
                    "land with no map colour, among the world's own map colours",
                    "0x777777"),
            new Allowed("LostTalesChatPresentation.java",
                    "vanilla's formatting colours, compared to pick a code; never drawn",
                    "0x0000AA", "0x00AA00", "0x00AAAA", "0xAA0000", "0xAA00AA",
                    "0xFFAA00", "0xAAAAAA", "0x555555", "0x5555FF", "0x55FF55",
                    "0x55FFFF", "0xFF5555", "0xFF55FF", "0xFFFF55", "0xFFFFFF"),
            new Allowed("LostTalesMainMenuTextStyle.java",
                    "vanilla's splash yellow, recognised to keep its emphasis",
                    "0xFFFF00"),
            new Allowed("LostTalesLotrMapLabelStyle.java",
                    "LOTR's white for region names, recognised to swap for ivory",
                    "0x00FFFFFF"),
            new Allowed("LostTalesClassTransformer.java",
                    "LOTR's white in its bytecode, recognised to route it to ivory",
                    "0x00FFFFFF"),
            new Allowed("LostTalesUiInk.java",
                    "white as a texture's tint, which leaves the art as painted",
                    "0xFFFFFF")};

    private static final Pattern HEX_LITERAL = Pattern.compile(
            "(?<![\\w.])0[xX]([0-9a-fA-F_]+)([lL]?)(?![\\w.])");
    private static final Pattern BIT_OPERATOR_BEFORE = Pattern.compile(
            "(?:(?<!&)&|(?<!\\|)\\||\\^|~|<<|>>)=?$");
    private static final Pattern BIT_OPERATOR_AFTER = Pattern.compile(
            "^(?:&(?!&)|\\|(?!\\|)|\\^|<<|>>)");

    @Test
    public void everyColourTheCodeDrawsWithIsAPaletteEntry() throws IOException {
        Set<Integer> palette = paletteRgb();
        File root = sourceRoot();
        List<File> files = new ArrayList<File>();
        collect(root, files);
        if (files.size() < FEWEST_EXPECTED) {
            throw new AssertionError("Only " + files.size() + " source files were "
                    + "read under " + root + ", so this test proved nothing");
        }
        List<String> offPalette = new ArrayList<String>();
        Set<String> allowanceUsed = new HashSet<String>();
        for (File file : files) {
            String path = root.toURI().relativize(file.toURI()).getPath();
            scan(path, file.getName(), read(file), palette, offPalette, allowanceUsed);
        }
        for (Allowed allowed : ALLOWED) {
            for (String literal : allowed.literals) {
                if (!allowanceUsed.contains(allowed.key(literal))) {
                    offPalette.add("allowed " + literal + " in " + allowed.file
                            + " matches nothing any more; take it off the list");
                }
            }
        }
        if (!offPalette.isEmpty()) {
            StringBuilder message = new StringBuilder(offPalette.size()
                    + " colour(s) off the palette; use a LostTalesColors entry"
                    + " (withAlpha for the alpha):");
            for (String line : offPalette) {
                message.append("\n  ").append(line);
            }
            throw new AssertionError(message.toString());
        }
    }

    /** The detection rules on their own, so a change to them is seen. */
    @Test
    public void theCodeAroundALiteralSaysWhetherItIsAColour() {
        Set<Integer> palette = paletteRgb();
        List<String> found = new ArrayList<String>();
        String source = "class A {\n"
                + "  int a = 0x123456;\n"
                + "  int b = 0x80123456 ;\n"
                + "  int c = d & 0x123456;\n"
                + "  int e = 0xFF000000 | f;\n"
                + "  int g = h << 0x123456;\n"
                + "  long i = 0x12345678L;\n"
                + "  int j = k * 0x123456;\n"
                + "  int l = (m + 0x123456) * 0x654321;\n"
                + "  int n = call(0x123456) * 2;\n"
                + "  // 0x123456 in a comment\n"
                + "  String o = \"0x123456\";\n"
                + "  int p = 0x2D1E2F;\n"
                + "  int q = 0xBC2D1E2F;\n"
                + "  boolean r = s > 0x123456;\n"
                + "}\n";
        scan("A.java", "A.java", source, palette, found, new HashSet<String>());
        List<String> lines = new ArrayList<String>();
        for (String entry : found) {
            lines.add(entry.substring(0, entry.indexOf(' ')));
        }
        assertEquals("[A.java:2, A.java:3, A.java:10, A.java:15]", lines.toString());
    }

    private static Set<Integer> paletteRgb() {
        String[] names = LostTalesColors.paletteNames();
        assertEquals("the palette has 32 entries", 32, names.length);
        Set<Integer> rgb = new HashSet<Integer>();
        for (String name : names) {
            rgb.add(Integer.valueOf(LostTalesColors.rgb(
                    LostTalesColors.paletteColor(name, 0))));
        }
        assertEquals("no two palette entries share a colour", 32, rgb.size());
        return rgb;
    }

    private static void scan(String path, String fileName, String source,
                             Set<Integer> palette, List<String> offPalette,
                             Set<String> allowanceUsed) {
        String code = codeOnly(source);
        Matcher matcher = HEX_LITERAL.matcher(code);
        while (matcher.find()) {
            String digits = matcher.group(1).replace("_", "");
            if (digits.length() != 6 && digits.length() != 8) {
                continue;
            }
            if (matcher.group(2).length() > 0
                    || besideBitOperator(code, matcher.start(), matcher.end())
                    || inMultiplication(code, matcher.start(), matcher.end())) {
                continue;
            }
            int rgb = (int)(Long.parseLong(digits, 16) & 0xFFFFFFL);
            if (palette.contains(Integer.valueOf(rgb))) {
                continue;
            }
            String literal = "0x" + digits.toUpperCase(Locale.ROOT);
            Allowed allowed = allowance(fileName, literal);
            if (allowed != null) {
                allowanceUsed.add(allowed.key(literal));
                continue;
            }
            offPalette.add(path + ":" + lineOf(code, matcher.start()) + " "
                    + matcher.group() + " (nearest "
                    + LostTalesColors.nearestName(rgb) + ")");
        }
    }

    private static boolean besideBitOperator(String code, int start, int end) {
        return BIT_OPERATOR_BEFORE.matcher(before(code, start)).find()
                || BIT_OPERATOR_AFTER.matcher(after(code, end)).find();
    }

    /**
     * A factor of a multiplication: the literal itself, or the bracketed
     * sum it stands in, such as a hash's {@code (y + 0x165667B1) * k}. A
     * call's brackets do not count; its result is no sum.
     */
    private static boolean inMultiplication(String code, int start, int end) {
        if (isMultiplication(before(code, start), after(code, end))) {
            return true;
        }
        int open = enclosingOpen(code, start);
        int close = enclosingClose(code, end);
        if (open < 0 || close < 0) {
            return false;
        }
        String ahead = before(code, open);
        if (ahead.length() > 0
                && Character.isJavaIdentifierPart(ahead.charAt(ahead.length() - 1))) {
            return false;
        }
        return isMultiplication(ahead, after(code, close + 1));
    }

    private static boolean isMultiplication(String before, String after) {
        return before.endsWith("*") || before.endsWith("*=") || after.startsWith("*");
    }

    private static int enclosingOpen(String code, int from) {
        int depth = 0;
        for (int index = from - 1; index >= 0; index--) {
            char c = code.charAt(index);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth == 0) {
                    return index;
                }
                depth--;
            } else if (c == ';' || c == '{' || c == '}') {
                return -1;
            }
        }
        return -1;
    }

    private static int enclosingClose(String code, int from) {
        int depth = 0;
        for (int index = from; index < code.length(); index++) {
            char c = code.charAt(index);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return index;
                }
                depth--;
            } else if (c == ';' || c == '{' || c == '}') {
                return -1;
            }
        }
        return -1;
    }

    /** The code before {@code index}, without the white space next to it. */
    private static String before(String code, int index) {
        int end = index;
        while (end > 0 && Character.isWhitespace(code.charAt(end - 1))) {
            end--;
        }
        return code.substring(Math.max(0, end - 4), end);
    }

    /** The code from {@code index}, without the white space next to it. */
    private static String after(String code, int index) {
        int start = index;
        while (start < code.length() && Character.isWhitespace(code.charAt(start))) {
            start++;
        }
        return code.substring(start, Math.min(code.length(), start + 4));
    }

    /**
     * The source with comments, strings and characters blanked out, every
     * line break kept, so offsets and line numbers still match.
     */
    private static String codeOnly(String source) {
        StringBuilder code = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                end = end < 0 ? source.length() : end;
                blank(code, source, index, end);
                index = end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                end = end < 0 ? source.length() : end + 2;
                blank(code, source, index, end);
                index = end;
            } else if (c == '"' || c == '\'') {
                int end = index + 1;
                while (end < source.length() && source.charAt(end) != c
                        && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(source.length(), end + 1);
                code.append(c);
                blank(code, source, index + 1, end);
                index = end;
            } else {
                code.append(c);
                index++;
            }
        }
        return code.toString();
    }

    private static void blank(StringBuilder code, String source, int start, int end) {
        for (int index = start; index < end; index++) {
            code.append(source.charAt(index) == '\n' ? '\n' : ' ');
        }
    }

    private static int lineOf(String code, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (code.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Allowed allowance(String fileName, String literal) {
        for (Allowed allowed : ALLOWED) {
            if (allowed.file.equals(fileName)) {
                for (String each : allowed.literals) {
                    if (each.equalsIgnoreCase(literal)) {
                        return allowed;
                    }
                }
            }
        }
        return null;
    }

    /**
     * {@code src/main/java} under the working directory, as Gradle runs
     * tests, or under the nearest folder above it that has one.
     */
    private static File sourceRoot() throws IOException {
        File directory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (directory != null) {
            File candidate = new File(directory, SOURCE_ROOT);
            if (new File(candidate, "com/ninuna/losttales").isDirectory()) {
                return candidate;
            }
            directory = directory.getParentFile();
        }
        throw new IOException("No " + SOURCE_ROOT + " at or above the working "
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

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /** Literals in one file that are colours, but not the interface's. */
    private static final class Allowed {
        final String file;
        final String reason;
        final String[] literals;

        Allowed(String file, String reason, String... literals) {
            this.file = file;
            this.reason = reason;
            this.literals = literals;
        }

        String key(String literal) {
            return this.file + " 0x" + literal.substring(2).toUpperCase(Locale.ROOT);
        }
    }
}
