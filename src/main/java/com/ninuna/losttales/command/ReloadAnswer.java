package com.ninuna.losttales.command;

import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * How {@code /losttales reload} reads its kinds and what it answers
 * (DATA-11). Each kind is read and put in place by itself: one whose read
 * fails stays as it was and says why, and the others are still read. The
 * answer names each kind with what it holds now, then every reason a file
 * was left out, a warning a line, at most {@link #MOST_REASONS} in all; the
 * server's log keeps the rest.
 */
final class ReloadAnswer {
    /** What the lang key of each of the answer's lines begins with. */
    static final String SAY = "chat.losttales.command.reload.";
    /** The most reasons one answer says in the chat. */
    static final int MOST_REASONS = 64;

    private ReloadAnswer() {}

    /** Reads a kind again and, once it is in place, sends it where it goes. */
    interface Reader {
        /** Reads {@code kind} and puts it in place; its numbers and reasons, or a throw that left it as it was. */
        Outcome read(ReloadKind kind);

        /** Sends what was read to whoever holds a copy of it: the players online. */
        void spread(ReloadKind kind);
    }

    /** What one kind's read came to. */
    static final class Outcome {
        final ReloadKind kind;
        /** What it holds now, the count line's arguments. */
        final Object[] counts;
        /** Why each file was left out; empty for none. */
        final List<String> reasons;
        /** Why the read failed, the kind as it was; null for a read that held. */
        final String failure;
        /** Why what was read did not reach every player online; null for none. */
        String unsent;

        private Outcome(ReloadKind kind, Object[] counts, List<String> reasons, String failure) {
            this.kind = kind;
            this.counts = counts == null ? new Object[0] : counts;
            this.reasons = Collections.unmodifiableList(new ArrayList<String>(
                    reasons == null ? Collections.<String>emptyList() : reasons));
            this.failure = failure;
        }

        /** A read that held: what the kind holds now and why each file was left out. */
        static Outcome read(ReloadKind kind, List<String> reasons, Object... counts) {
            return new Outcome(kind, counts, reasons, null);
        }

        /** A read that failed, the kind as it was. */
        static Outcome failed(ReloadKind kind, String why) {
            return new Outcome(kind, null, null, why == null ? "" : why);
        }
    }

    /**
     * Reads each kind in turn: one that throws is answered as failed and
     * stays as it was, the next still read; what was read is then sent on,
     * and a failure of that is said beside it.
     */
    static List<Outcome> run(List<ReloadKind> kinds, Reader reader) {
        List<Outcome> outcomes = new ArrayList<Outcome>();
        for (ReloadKind kind : kinds) {
            Outcome outcome;
            try {
                outcome = reader.read(kind);
            } catch (RuntimeException failure) {
                LostTalesLog.warning("Reading the %s again failed; they stay as they were: %s",
                        kind.id, failure.toString());
                outcomes.add(Outcome.failed(kind, describe(failure)));
                continue;
            }
            try {
                reader.spread(kind);
            } catch (RuntimeException failure) {
                LostTalesLog.warning("The %s were read again but not sent to everyone: %s",
                        kind.id, failure.toString());
                outcome.unsent = describe(failure);
            }
            outcomes.add(outcome);
        }
        return outcomes;
    }

    /** The answer, line by line. */
    static List<IChatComponent> lines(List<Outcome> outcomes) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        int said = 0;
        for (Outcome outcome : outcomes) {
            IChatComponent kind = LostTalesCommandBase.words(outcome.kind.wordsKey);
            if (outcome.failure != null) {
                lines.add(LostTalesCommandBase.line(EnumChatFormatting.RED, SAY + "failed",
                        kind, outcome.failure));
                continue;
            }
            IChatComponent counts = LostTalesCommandBase.words(outcome.kind.countKey,
                    outcome.counts);
            if (outcome.reasons.isEmpty()) {
                lines.add(LostTalesCommandBase.line(EnumChatFormatting.GREEN, SAY + "done",
                        kind, counts));
            } else {
                lines.add(LostTalesCommandBase.line(EnumChatFormatting.YELLOW, SAY + "left_out",
                        kind, counts));
                int shown = Math.max(0, Math.min(outcome.reasons.size(), MOST_REASONS - said));
                for (int index = 0; index < shown; index++) {
                    lines.add(LostTalesCommandBase.line(EnumChatFormatting.GRAY,
                            SAY + "reason", outcome.reasons.get(index)));
                }
                said += shown;
                if (outcome.reasons.size() > shown) {
                    lines.add(LostTalesCommandBase.line(EnumChatFormatting.GRAY, SAY + "more",
                            Integer.valueOf(outcome.reasons.size() - shown)));
                }
            }
            if (outcome.unsent != null) {
                lines.add(LostTalesCommandBase.line(EnumChatFormatting.RED, SAY + "unsent",
                        outcome.unsent));
            }
            if (outcome.kind.noteKey != null) {
                lines.add(LostTalesCommandBase.line(EnumChatFormatting.GRAY,
                        outcome.kind.noteKey));
            }
        }
        return lines;
    }

    /** A failure as the answer names it: its message, else its kind. */
    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.trim().length() == 0
                ? failure.getClass().getSimpleName() : message.trim();
    }
}
