package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Arrays;
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
 * a Discord channel id. Everything is cut to a line.
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

    private ChatCommandText() {}

    /** A command as typed, {@code /name arg arg}, described; empty for no command. */
    public static String describeTyped(String typed) {
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
        return describe(words[0], arguments);
    }

    /** A command by its name and arguments, described. */
    public static String describe(String commandName, String[] args) {
        String name = commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
        List<String> kept = new ArrayList<String>();
        String[] arguments = args == null ? new String[0] : args;
        if (name.equals("msg") || name.equals("tell") || name.equals("w")) {
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
}
