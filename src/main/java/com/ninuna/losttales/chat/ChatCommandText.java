package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How a command reads wherever it is shown: {@code /name arg arg}, with
 * what must not be repeated left out. The Server Log writes its
 * entries with it, and the chat shows a typed command's echo with it, so
 * a reply quoting the echo carries no secret either. A private message
 * keeps only whom it went to; a config change ({@code /losttales config set} or its
 * alias {@code cfg}) keeps the category and key but not the value, since a
 * value may be a secret; a Discord unlink keeps its game channel but never
 * a Discord channel id. Another mod's login, password or private message
 * keeps only its name ({@link #SECRET_NAMES}), and so does every command the
 * server lists as private in its config ({@link #parseNames}). Everything is
 * cut to a line.
 */
public final class ChatCommandText {
    /** The longest a described command gets. */
    public static final int MAX_LENGTH = 256;
    /** Three full stops rather than an ellipsis: the chat's font has no glyph for one. */
    private static final String ELIDED = "...";
    /**
     * Every name {@code /losttales config} answers to: a value set under any
     * of them is masked. {@code ChatCommandTextTest} holds this to the
     * sub-command's own names.
     */
    public static final List<String> CONFIG_NAMES =
            Collections.unmodifiableList(Arrays.asList("config", "cfg"));
    /**
     * Commands other mods add whose words are never repeated, only their
     * name, matched by any name a command answers to: an offline server's
     * login and password commands, and private messages and their replies.
     */
    public static final List<String> SECRET_NAMES =
            Collections.unmodifiableList(Arrays.asList(
                    "login", "l", "log", "register", "reg", "unregister",
                    "unreg", "changepassword", "changepw", "cp", "password",
                    "passwd", "pass", "auth", "2fa", "totp", "r", "reply",
                    "pm", "m", "message", "whisper", "mail"));
    /** The most names the server's list of private commands holds. */
    public static final int MAX_LISTED_NAMES = 64;
    /** The longest name on that list. */
    public static final int MAX_NAME_LENGTH = 32;

    /** The server's own list of private commands, from its config; empty until read. */
    private static volatile List<String> serverNames = Collections.emptyList();

    private ChatCommandText() {}

    /** The server's list of private commands, written by its config. */
    public static void installServerNames(List<String> names) {
        serverNames = names == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(names));
    }

    /** The server's list of private commands. */
    public static List<String> serverNames() {
        return serverNames;
    }

    public static void resetServerNames() {
        serverNames = Collections.emptyList();
    }

    /**
     * The config's lines as command names: trimmed, a leading slash taken
     * off, in lower case. A line that is no name, or too long, is reported
     * and skipped; a repeat or a name already on {@link #SECRET_NAMES} is
     * skipped quietly; past {@link #MAX_LISTED_NAMES} the rest are reported.
     */
    public static List<String> parseNames(String[] lines, List<String> warnings) {
        List<String> names = new ArrayList<String>();
        if (lines == null) {
            return names;
        }
        for (String line : lines) {
            String name = line == null ? "" : line.trim();
            if (name.startsWith("/")) {
                name = name.substring(1).trim();
            }
            name = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            if (!isValidName(name)) {
                warn(warnings, "Private command '" + line + "' is not a command's name "
                        + "(no spaces or formatting, up to " + MAX_NAME_LENGTH
                        + " letters); skipped");
            } else if (names.size() >= MAX_LISTED_NAMES) {
                warn(warnings, "Private command '" + line + "' is past the "
                        + MAX_LISTED_NAMES + " the list holds; skipped");
            } else if (!names.contains(name) && !SECRET_NAMES.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Whether {@code name} may stand on the list: a command's name, in lower case. */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || !name.equals(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char each = name.charAt(index);
            if (Character.isWhitespace(each) || Character.isISOControl(each)
                    || each == '\u00a7' || each == '/') {
                return false;
            }
        }
        return true;
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings != null) {
            warnings.add(message);
        }
    }

    /**
     * A command as typed, {@code /name arg arg}, described, {@code listed}
     * being the server's private commands; empty for no command.
     */
    public static String describeTyped(String typed, Collection<String> listed) {
        String line = typed == null ? "" : typed.trim();
        if (line.startsWith("/")) {
            line = line.substring(1).trim();
        }
        if (line.isEmpty()) {
            return "";
        }
        String[] words = line.split("\\s+");
        String[] arguments = new String[words.length - 1];
        System.arraycopy(words, 1, arguments, 0, arguments.length);
        return describe(words[0], null, arguments, listed);
    }

    /**
     * A command by its name, the other names it answers to, and its
     * arguments, described: a command known by any name in
     * {@link #SECRET_NAMES} or in {@code listed}, the server's private
     * commands, keeps only its name.
     */
    public static String describe(String commandName, List<String> aliases,
                                  String[] args, Collection<String> listed) {
        String name = commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
        List<String> kept = new ArrayList<String>();
        String[] arguments = args == null ? new String[0] : args;
        if (isSecret(name, aliases, listed)) {
            if (arguments.length > 0) {
                kept.add(ELIDED);
            }
        } else if (name.equals("msg") || name.equals("tell") || name.equals("w")) {
            if (arguments.length > 0) {
                kept.add(arguments[0]);
            }
            if (arguments.length > 1) {
                kept.add(ELIDED);
            }
        } else if (name.equals("losttales") && arguments.length > 1
                && CONFIG_NAMES.contains(arguments[0].toLowerCase(Locale.ROOT))
                && arguments[1].equalsIgnoreCase("set")) {
            for (int index = 0; index < arguments.length && index < 4; index++) {
                kept.add(arguments[index]);
            }
            if (arguments.length > 4) {
                kept.add(ELIDED);
            }
        } else if (name.equals("losttales") && arguments.length > 3
                && arguments[0].equalsIgnoreCase("discord")
                && arguments[1].equalsIgnoreCase("unlink")) {
            for (int index = 0; index < 3; index++) {
                kept.add(arguments[index]);
            }
            kept.add(ELIDED);
        } else {
            Collections.addAll(kept, arguments);
        }
        StringBuilder line = new StringBuilder("/").append(name);
        for (String argument : kept) {
            line.append(' ').append(argument);
        }
        if (line.length() > MAX_LENGTH) {
            line.setLength(MAX_LENGTH - ELIDED.length());
            line.append(ELIDED);
        }
        return line.toString();
    }

    /** Whether a command known by {@code name} or any of {@code aliases} keeps its words to itself. */
    private static boolean isSecret(String name, List<String> aliases,
                                    Collection<String> listed) {
        if (isSecretName(name, listed)) {
            return true;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && isSecretName(
                        alias.trim().toLowerCase(Locale.ROOT), listed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSecretName(String name, Collection<String> listed) {
        return SECRET_NAMES.contains(name) || (listed != null && listed.contains(name));
    }
}
