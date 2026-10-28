package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.sync.FellowshipMemberAvailability;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import net.minecraft.client.resources.I18n;

/**
 * The one word for where a member stands (FEL-32), wherever it shows: the
 * Fellowships page and the HUD both say Away, as the character played
 * instead, or Making a Character, the Tab list's word for a ghost
 * (CHAR-02). Nothing is said of a member who is here.
 */
public final class FellowshipMemberWords {

    private FellowshipMemberWords() {}

    /** The lang key of a presence; null for a member who is here. */
    public static String keyOf(FellowshipMemberPresence presence) {
        if (presence == FellowshipMemberPresence.AWAY) {
            return "gui.losttales.fellowship.away";
        }
        if (presence == FellowshipMemberPresence.ELSEWHERE) {
            return "gui.losttales.fellowship.playing_as";
        }
        if (presence == FellowshipMemberPresence.GHOST) {
            return "gui.losttales.fellowship.ghost";
        }
        return null;
    }

    /**
     * Where a member stands as the HUD's live status tells it: Away while
     * the account is offline; while it plays no character of this member,
     * as the member's state tells it, playing another or making one. Null
     * where neither says, as while the state has not caught up.
     */
    public static FellowshipMemberPresence presenceOf(
            FellowshipMemberAvailability availability,
            FellowshipMemberPresence presence) {
        if (availability == FellowshipMemberAvailability.OFFLINE) {
            return FellowshipMemberPresence.AWAY;
        }
        if (availability == FellowshipMemberAvailability.INACTIVE_CHARACTER
                && (presence == FellowshipMemberPresence.ELSEWHERE
                || presence == FellowshipMemberPresence.GHOST)) {
            return presence;
        }
        return null;
    }

    /** A presence as the reader's game words it; empty for a member who is here. */
    public static String of(FellowshipMemberPresence presence, String elsewhereName) {
        String key = keyOf(presence);
        if (key == null) {
            return "";
        }
        return presence == FellowshipMemberPresence.ELSEWHERE
                ? I18n.format(key, elsewhereName == null ? "" : elsewhereName)
                : I18n.format(key);
    }
}
