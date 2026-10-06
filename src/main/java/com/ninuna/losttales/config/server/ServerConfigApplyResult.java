package com.ninuna.losttales.config.server;

import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What became of an operator's changes: what was set, what was refused and
 * why, what restarted. Every word in it is a lang key under {@link #WORDS},
 * with its arguments, so the command and the Server Settings page each
 * show it in their reader's language.
 */
public final class ServerConfigApplyResult {

    /** What the lang key of every word of a result begins with. */
    public static final String WORDS = "losttales.config.";
    /** The answer when the server holds no config file. */
    public static final String NO_FILE = WORDS + "no_file";
    /** What a restart of the Discord bridge is called. */
    public static final String RESTARTED_DISCORD = WORDS + "restarted.discord_bridge";
    /** What sending the chat access to everyone again is called. */
    public static final String RESTARTED_CHAT_ACCESS = WORDS + "restarted.chat_access";

    /** One refused change and the reason, a lang key and its arguments. */
    public static final class Refusal {
        private final String name;
        private final String reasonKey;
        private final List<String> reasonArguments;

        public Refusal(String name, String reasonKey, String... reasonArguments) {
            this.name = name == null ? "" : name;
            this.reasonKey = reasonKey == null ? "" : reasonKey;
            List<String> arguments = new ArrayList<String>();
            if (reasonArguments != null) {
                for (String argument : reasonArguments) {
                    arguments.add(argument == null ? "" : argument);
                }
            }
            this.reasonArguments = Collections.unmodifiableList(arguments);
        }

        public String getName() {
            return this.name;
        }

        /** The lang key of the reason, under {@link #WORDS}. */
        public String getReasonKey() {
            return this.reasonKey;
        }

        /** What the reason's {@code %s} are filled with: numbers, values, never words. */
        public List<String> getReasonArguments() {
            return this.reasonArguments;
        }

        /** The reason in {@code words}. */
        public String reason(LostTalesWords words) {
            return words.format(this.reasonKey, this.reasonArguments.toArray());
        }
    }

    private final List<String> applied;
    private final List<Refusal> refused;
    private final List<String> restarted;
    private final String message;

    public ServerConfigApplyResult(List<String> applied, List<Refusal> refused,
                                   List<String> restarted, String message) {
        this.applied = Collections.unmodifiableList(new ArrayList<String>(
                applied == null ? Collections.<String>emptyList() : applied));
        this.refused = Collections.unmodifiableList(new ArrayList<Refusal>(
                refused == null ? Collections.<Refusal>emptyList() : refused));
        this.restarted = Collections.unmodifiableList(new ArrayList<String>(
                restarted == null ? Collections.<String>emptyList() : restarted));
        this.message = message == null ? "" : message;
    }

    public static ServerConfigApplyResult refusedOutright(String message) {
        return new ServerConfigApplyResult(null, null, null, message);
    }

    public List<String> getApplied() {
        return this.applied;
    }

    public List<Refusal> getRefused() {
        return this.refused;
    }

    /** What restarted, each the lang key of its name ({@link #RESTARTED_DISCORD}). */
    public List<String> getRestarted() {
        return this.restarted;
    }

    /**
     * The lang key of a line for the operator when nothing else needs
     * saying ({@link #NO_FILE}); empty otherwise.
     */
    public String getMessage() {
        return this.message;
    }

    /** The names of what restarted in {@code words}, joined by commas. */
    public String restartedIn(LostTalesWords words) {
        StringBuilder joined = new StringBuilder();
        for (String key : this.restarted) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(words.format(key));
        }
        return joined.toString();
    }
}
