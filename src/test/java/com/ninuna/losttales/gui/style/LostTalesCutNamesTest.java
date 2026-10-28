package com.ninuna.losttales.gui.style;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * No name the interface draws is cut with dots (LOOK-37): a cut name
 * fades into its end through {@link LostTalesUiCutName}. No piece of the
 * style layer keeps a dotted cut, and no source under the client's and the GUI's
 * packages writes dots into what it draws, but for the few
 * {@link #ALLOWED} words that are not names, each with its reason; an
 * allowance that no longer matches fails too.
 *
 * <p>The scan reads {@code src/main/java} under the working directory, as
 * Gradle runs tests.</p>
 */
public final class LostTalesCutNamesTest {

    private static final String SOURCE_ROOT = "src/main/java";
    /** The packages whose code draws what the player reads. */
    private static final String[] DRAWING_PACKAGES = {
            "com/ninuna/losttales/client", "com/ninuna/losttales/gui"};
    /** Fewer files than this means the walk found the wrong place. */
    private static final int FEWEST_EXPECTED = 200;
    /** The style layer's package, the shared pieces every screen draws with. */
    private static final String STYLE_PACKAGE = "com/ninuna/losttales/gui/style";
    /** Fewer pieces than this means the style walk found the wrong place. */
    private static final int FEWEST_STYLE_PIECES = 20;

    /** Files that write dots into words that are not names, and why. */
    private static final String[][] ALLOWED = {
            {"Settings.java",
                    "a typed setting's value, read from its end"},
            {"MotionCodec.java", "a log line, never drawn"},
    };

    /** No piece of the style layer, any class under {@code gui/style}, keeps a method that cuts words. */
    @Test
    public void theStyleLayerKeepsNoDottedCut() throws IOException,
            ClassNotFoundException {
        File style = new File(sourceRoot(), STYLE_PACKAGE);
        File[] sources = style.listFiles();
        assertTrue("no sources under " + style, sources != null);
        int classes = 0;
        for (File source : sources) {
            String name = source.getName();
            if (!name.endsWith(".java")) {
                continue;
            }
            Class<?> piece = Class.forName(STYLE_PACKAGE.replace('/', '.') + "."
                            + name.substring(0, name.length() - ".java".length()),
                    false, getClass().getClassLoader());
            classes++;
            for (Method method : piece.getDeclaredMethods()) {
                assertTrue(piece.getSimpleName() + "." + method.getName()
                                + " cuts words; a cut name fades through"
                                + " LostTalesUiCutName",
                        !method.getName().toLowerCase().contains("trim"));
            }
        }
        assertTrue("found only " + classes + " classes under " + style,
                classes >= FEWEST_STYLE_PIECES);
    }

    @Test
    public void noDrawnNameIsCutWithDots() throws IOException {
        File root = sourceRoot();
        List<File> files = new ArrayList<File>();
        for (String drawing : DRAWING_PACKAGES) {
            collect(new File(root, drawing), files);
        }
        assertTrue("found only " + files.size() + " sources under "
                + root, files.size() >= FEWEST_EXPECTED);
        List<String> found = new ArrayList<String>();
        Set<String> used = new HashSet<String>();
        for (File file : files) {
            String source = new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (int line : dottedLines(source)) {
                String name = file.getName();
                if (allowed(name)) {
                    used.add(name);
                } else {
                    found.add(name + ":" + line);
                }
            }
        }
        assertEquals("words cut with dots; cut names fade through"
                + " LostTalesUiCutName", new ArrayList<String>(), found);
        for (String[] allowance : ALLOWED) {
            assertTrue(allowance[0] + " no longer writes dots; drop its"
                    + " allowance", used.contains(allowance[0]));
        }
    }

    /** The scan itself: dots beside words are a cut; a glyph's pattern, a comment or a character is not. */
    @Test
    public void theScanTellsACutFromAPattern() {
        assertTrue(isDotted("..."));
        assertTrue(isDotted("Loading..."));
        assertTrue(isDotted("\\u2026"));
        assertTrue(!isDotted("....#"));
        assertTrue(!isDotted("#...#"));
        assertTrue(!isDotted("a.b.c"));
        String source = "// a cut \"...\" in a comment\n"
                + "char end = '\\u2026';\n"
                + "/* \"...\"\n */ String pattern = \".#...\";\n"
                + "String cut = text + \"...\";\n";
        List<Integer> expected = new ArrayList<Integer>();
        expected.add(Integer.valueOf(5));
        assertEquals(expected, dottedLines(source));
    }

    /**
     * The lines holding a string literal that is three dots or an
     * ellipsis on its own, or that a cut ends or starts with: comments
     * and character literals are not read, and a glyph's pattern of dots
     * and marks is no cut.
     */
    static List<Integer> dottedLines(String source) {
        List<Integer> lines = new ArrayList<Integer>();
        int line = 1;
        int index = 0;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '\n') {
                line++;
                index++;
            } else if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                index = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                end = end < 0 ? source.length() : end + 2;
                line += count(source, index, end);
                index = end;
            } else if (c == '\'') {
                int end = index + 1;
                while (end < source.length() && source.charAt(end) != '\''
                        && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                index = Math.min(source.length(), end + 1);
            } else if (c == '"') {
                int end = index + 1;
                while (end < source.length() && source.charAt(end) != '"'
                        && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                String literal = source.substring(index + 1,
                        Math.min(source.length(), end));
                if (isDotted(literal)) {
                    lines.add(Integer.valueOf(line));
                }
                index = Math.min(source.length(), end + 1);
            } else {
                index++;
            }
        }
        return lines;
    }

    /** Whether a literal's words carry a cut's dots: an ellipsis, or three dots beside words. */
    static boolean isDotted(String literal) {
        if (literal.contains("\\u2026") || literal.indexOf('…') >= 0) {
            return true;
        }
        if (!literal.contains("...")) {
            return false;
        }
        // A glyph's pattern is dots and marks alone.
        for (int index = 0; index < literal.length(); index++) {
            char c = literal.charAt(index);
            if (c != '.' && c != '#') {
                return true;
            }
        }
        return literal.equals("...");
    }

    private static int count(String source, int from, int to) {
        int lines = 0;
        for (int index = from; index < to; index++) {
            if (source.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static boolean allowed(String fileName) {
        for (String[] allowance : ALLOWED) {
            if (allowance[0].equals(fileName)) {
                return true;
            }
        }
        return false;
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
}
