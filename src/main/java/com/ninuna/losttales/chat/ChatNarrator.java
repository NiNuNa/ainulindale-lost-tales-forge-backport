package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * The Narrator: the voice a storyteller speaks with in the roleplaying
 * channels, whispers included, while it is chosen. It rides on the
 * chat identity underneath — a line is still routed by that identity's
 * faction and fellowship and recorded under the account — and changes only
 * how the line is signed: the name Narrator, the parchment colour, a
 * mark where a head would stand, the words in italics, and no speech
 * bubble. Choosing it needs the {@code chat.narrate} capability, which
 * the server checks on every send.
 *
 * <p>The Narrator is anonymous. Every copy of its line but the
 * narrator's own is signed by {@link #SENDER_ID} and carries no account
 * and no character; who narrated is kept by the server and told only to
 * the readers of the Server Log.</p>
 */
public final class ChatNarrator {
    /**
     * The name a Narrator line is signed with; each game shows its own
     * word for it ({@link ChatNames#narrator}).
     */
    public static final String NAME = "Narrator";
    /**
     * The skin id a Narrator line carries in a character's place: what
     * tells such a line apart everywhere it is drawn, and never a skin.
     */
    public static final String SKIN_ID = "losttales:narrator";
    /**
     * The sender id a Narrator line carries to everyone but its narrator:
     * nobody's account, so the line names no one.
     */
    public static final UUID SENDER_ID = UUID.nameUUIDFromBytes(
            "losttales:narrator".getBytes(Charset.forName("UTF-8")));

    private ChatNarrator() {}

    /** The Narrator's colour: parchment, the storybook's page. */
    public static int color() {
        return LostTalesColors.rgb(LostTalesColors.PARCHMENT);
    }

    /** Whether a line's skin id marks it as the Narrator's. */
    public static boolean isNarratorSkin(String skinId) {
        return SKIN_ID.equals(skinId);
    }
}
