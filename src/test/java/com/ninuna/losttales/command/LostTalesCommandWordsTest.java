package com.ninuna.losttales.command;

import com.ninuna.losttales.util.EnglishWords;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every word a {@code /losttales} sub-command answers with is a line of the
 * lang file, so each operator reads it in their own game's language. This
 * reads every source file of the command package, comments left out, and
 * fails, naming the file and the line, on:
 *
 * <ul>
 * <li>a lang key with no English line: a whole {@code "chat.losttales..."}
 * literal, or a file's {@code SAY + "..."};</li>
 * <li>a sentence in a text component: two words in a row in a literal
 * given to {@code new ChatComponentText(...)};</li>
 * <li>a colour glued to text ({@code EnumChatFormatting.RED + ...}), the
 * shape a sentence written in the code takes;</li>
 * <li>a usage line that is not a command line: it starts with {@code "/"},
 * the command's prefix or its usage;</li>
 * <li>a command exception thrown with words in place of a key.</li>
 * </ul>
 *
 * <p>And the other way: every {@code chat.losttales.command.} line of the
 * lang file is one some code names.</p>
 */
public final class LostTalesCommandWordsTest {

    private static final String SOURCE_ROOT = "src/main/java";
    private static final String COMMANDS = "com/ninuna/losttales/command";
    private static final String COMMAND_WORDS = "chat.losttales.command.";
    /** Fewer files than this means the walk found the wrong place. */
    private static final int FEWEST_COMMAND_FILES = 12;

    private static final Pattern WHOLE_KEY = Pattern.compile(
            "\"(chat\\.losttales\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\"");
    private static final Pattern SAY_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+SAY\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern SAY_PART = Pattern.compile(
            "\\bSAY\\s*\\+\\s*\"([^\"]*)\"(?!\\s*\\+)");
    private static final Pattern TEXT_COMPONENT = Pattern.compile(
            "new\\s+(?:[\\w.]+\\.)?ChatComponentText\\s*\\(");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TWO_WORDS = Pattern.compile("[A-Za-z]{2,}\\s+[A-Za-z]{2,}");
    private static final Pattern GLUED_COLOUR = Pattern.compile(
            "EnumChatFormatting\\.[A-Z_]+\\s*\\+|\\+\\s*EnumChatFormatting\\.[A-Z_]+");
    private static final Pattern USAGE_CALL = Pattern.compile("\\busage\\(\\s*\\w+\\s*,\\s*");
    private static final Pattern THROWN = Pattern.compile(
            "new\\s+(?:CommandException|WrongUsageException|SyntaxErrorException"
                    + "|NumberInvalidException|PlayerNotFoundException)\\s*\\(\\s*\"([^\"]*)\"");
    private static final Pattern KEY_SHAPE = Pattern.compile("[a-z0-9_.]+");

    @Test
    public void everyKeyACommandNamesHasAnEnglishLine() throws IOException {
        List<String> problems = new ArrayList<String>();
        for (Source source : commandSources()) {
            for (Named named : source.keys()) {
                if (!EnglishWords.INSTANCE.has(named.key)) {
                    problems.add(source.where(named.at) + " names " + named.key
                            + ", which has no English line");
                }
            }
        }
        assertEquals("every key a command names is in en_US.lang",
                new ArrayList<String>(), problems);
    }

    @Test
    public void everyCommandLineOfTheLangFileIsNamed() throws IOException {
        Set<String> named = new TreeSet<String>();
        for (Source source : commandSources()) {
            for (Named key : source.keys()) {
                named.add(key.key);
            }
        }
        List<File> everything = new ArrayList<File>();
        collect(sourceRoot(), everything);
        for (File file : everything) {
            Matcher whole = WHOLE_KEY.matcher(Source.withoutComments(read(file)));
            while (whole.find()) {
                named.add(whole.group(1));
            }
        }
        List<String> unnamed = new ArrayList<String>();
        for (String key : langKeys()) {
            if (key.startsWith(COMMAND_WORDS) && !named.contains(key)) {
                unnamed.add(key);
            }
        }
        assertEquals("every command line of en_US.lang is named by the code",
                new ArrayList<String>(), unnamed);
    }

    @Test
    public void noCommandSendsWordsOfItsOwn() throws IOException {
        List<String> problems = new ArrayList<String>();
        for (Source source : commandSources()) {
            Matcher text = TEXT_COMPONENT.matcher(source.code);
            while (text.find()) {
                String argument = source.argumentAfter(text.end());
                Matcher literal = LITERAL.matcher(argument);
                while (literal.find()) {
                    if (TWO_WORDS.matcher(literal.group(1)).find()) {
                        problems.add(source.where(text.start()) + " sends the words \""
                                + literal.group(1) + "\" as they are");
                    }
                }
            }
            Matcher glued = GLUED_COLOUR.matcher(source.code);
            while (glued.find()) {
                problems.add(source.where(glued.start()) + " glues a colour to text");
            }
            Matcher thrown = THROWN.matcher(source.code);
            while (thrown.find()) {
                if (!KEY_SHAPE.matcher(thrown.group(1)).matches()) {
                    problems.add(source.where(thrown.start()) + " throws the words \""
                            + thrown.group(1) + "\" in place of a key");
                }
            }
        }
        assertEquals("every answer is a lang key", new ArrayList<String>(), problems);
    }

    @Test
    public void usageLinesAreCommandLines() throws IOException {
        List<String> problems = new ArrayList<String>();
        int usages = 0;
        for (Source source : commandSources()) {
            Matcher call = USAGE_CALL.matcher(source.code);
            while (call.find()) {
                usages++;
                String rest = source.code.substring(call.end());
                if (!rest.startsWith("\"/") && !rest.startsWith("commandPrefix()")
                        && !rest.startsWith("getCommandUsage(")) {
                    problems.add(source.where(call.start())
                            + " sends a usage line that is not a command line");
                }
            }
        }
        assertTrue("the usage lines were found", usages > 0);
        assertEquals("a usage line is the command's syntax", new ArrayList<String>(), problems);
    }

    /** A lang key named at an offset of a source file. */
    private static final class Named {
        final String key;
        final int at;

        Named(String key, int at) {
            this.key = key;
            this.at = at;
        }
    }

    /** One source file, its comments blanked out so offsets keep their lines. */
    private static final class Source {
        final String name;
        final String code;

        Source(String name, String text) {
            this.name = name;
            this.code = withoutComments(text);
        }

        /** Every whole key and every {@code SAY + "..."} the file names. */
        List<Named> keys() {
            List<Named> keys = new ArrayList<Named>();
            Matcher whole = WHOLE_KEY.matcher(this.code);
            while (whole.find()) {
                keys.add(new Named(whole.group(1), whole.start()));
            }
            Matcher constant = SAY_CONSTANT.matcher(this.code);
            String say = constant.find() ? constant.group(1) : null;
            Matcher part = SAY_PART.matcher(this.code);
            while (part.find()) {
                if (say == null) {
                    keys.add(new Named("(no SAY constant) " + part.group(1), part.start()));
                } else if (!part.group(1).endsWith(".")) {
                    keys.add(new Named(say + part.group(1), part.start()));
                }
            }
            return keys;
        }

        /** The text of the call's argument list from {@code from}, to its closing bracket. */
        String argumentAfter(int from) {
            int depth = 1;
            boolean inString = false;
            for (int index = from; index < this.code.length(); index++) {
                char c = this.code.charAt(index);
                if (inString) {
                    if (c == '\\') {
                        index++;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else if (c == '"') {
                    inString = true;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    return this.code.substring(from, index);
                }
            }
            return this.code.substring(from);
        }

        String where(int offset) {
            int line = 1;
            for (int index = 0; index < offset && index < this.code.length(); index++) {
                if (this.code.charAt(index) == '\n') {
                    line++;
                }
            }
            return this.name + ":" + line;
        }

        /** The code with every comment's characters but its line breaks made spaces. */
        static String withoutComments(String text) {
            StringBuilder code = new StringBuilder(text.length());
            int index = 0;
            while (index < text.length()) {
                char c = text.charAt(index);
                char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
                if (c == '/' && next == '/') {
                    while (index < text.length() && text.charAt(index) != '\n') {
                        code.append(' ');
                        index++;
                    }
                } else if (c == '/' && next == '*') {
                    int end = text.indexOf("*/", index + 2);
                    end = end < 0 ? text.length() : end + 2;
                    for (; index < end; index++) {
                        code.append(text.charAt(index) == '\n' ? '\n' : ' ');
                    }
                } else if (c == '"' || c == '\'') {
                    code.append(c);
                    index++;
                    while (index < text.length() && text.charAt(index) != c) {
                        if (text.charAt(index) == '\\' && index + 1 < text.length()) {
                            code.append(text.charAt(index));
                            index++;
                        }
                        code.append(text.charAt(index));
                        index++;
                    }
                    if (index < text.length()) {
                        code.append(c);
                        index++;
                    }
                } else {
                    code.append(c);
                    index++;
                }
            }
            return code.toString();
        }
    }

    private static List<Source> commandSources() throws IOException {
        File directory = new File(sourceRoot(), COMMANDS);
        File[] files = directory.listFiles();
        List<Source> sources = new ArrayList<Source>();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".java")) {
                    sources.add(new Source(file.getName(), read(file)));
                }
            }
        }
        assertTrue("the command sources were found", sources.size() >= FEWEST_COMMAND_FILES);
        return sources;
    }

    /** Every key of the English lang file, in its order. */
    private static List<String> langKeys() throws IOException {
        InputStream in = LostTalesCommandWordsTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        assertTrue("the English lang file is on the test classpath", in != null);
        List<String> keys = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#")) {
                    keys.add(line.substring(0, equals));
                }
            }
        } finally {
            reader.close();
        }
        return keys;
    }

    /**
     * {@code src/main/java} under the working directory, as Gradle runs
     * tests, or under the nearest folder above it that has one.
     */
    private static File sourceRoot() throws IOException {
        File directory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (directory != null) {
            File candidate = new File(directory, SOURCE_ROOT);
            if (new File(candidate, COMMANDS).isDirectory()) {
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
}
