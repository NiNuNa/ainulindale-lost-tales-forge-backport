package com.ninuna.losttales.config.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What became of an operator's changes: what was set, what was refused and why, what restarted. */
public final class ServerConfigApplyResult {

    /** One refused change and the reason. */
    public static final class Refusal {
        private final String name;
        private final String reason;

        public Refusal(String name, String reason) {
            this.name = name == null ? "" : name;
            this.reason = reason == null ? "" : reason;
        }

        public String getName() {
            return this.name;
        }

        public String getReason() {
            return this.reason;
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

    public List<String> getRestarted() {
        return this.restarted;
    }

    /** A line for the operator when nothing else needs saying; empty otherwise. */
    public String getMessage() {
        return this.message;
    }
}
