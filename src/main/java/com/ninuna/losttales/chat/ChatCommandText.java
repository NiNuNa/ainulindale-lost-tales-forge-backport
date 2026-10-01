package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How a command reads wherever it is shown: {@code /name arg arg}, with
 * what must not be repeated left out. The Server Console writes its
 * entries with it, and the chat shows a typed command's echo with it, so
 * a reply quoting the echo carries no secret either. A private message
 * keeps only whom it went to; a config change keeps the category and key
 * but not the value, since a value may be a secret; a Discord binding
 * keeps its channel and direction but never a webhook address or a
 * channel id. Everything is cut to a line.
 */
public final class ChatCommandText {
    /** The longest a described command gets. */
    public static final int MAX_LENGTH = 256;
    /** Three full stops rather than an ellipsis: the chat's font has no glyph for one. */
    private static final String ELIDED = "...";

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
                && arguments[0].equalsIgnoreCase("config")
                && arguments[1].equalsIgnoreCase("set")) {
            for (int index = 0; index < arguments.length && index < 4; index++) {
                kept.add(arguments[index]);
            }
            if (arguments.length > 4) {
                kept.add(ELIDED);
            }
        } else if (name.equals("losttales") && arguments.length > 0
                && arguments[0].equalsIgnoreCase("discord")) {
            for (String argument : arguments) {
                int equals = argument.indexOf('=');
                String option = equals < 0 ? "" : argument.substring(0, equals)
                        .toLowerCase(Locale.ROOT);
                kept.add(option.equals("webhook") || option.equals("channel")
                        ? option + "=" + ELIDED : argument);
            }
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
