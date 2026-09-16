package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;

/**
 * The Narrator: the voice a storyteller speaks with in the roleplaying
 * channels, whispers included, while it is chosen. It rides on the
 * chat identity underneath — a line is still routed by that identity's
 * faction and party and recorded under the account — and changes only
 * how the line is signed: the name Narrator, the parchment colour, a
 * mark where a head would stand, the words in italics, and no speech
 * bubble. Choosing it needs the {@code chat.narrate} capability, which
 * the server checks on every send.
 */
public final class ChatNarrator {
    public static final String NAME = "Narrator";
    /**
     * The skin id a Narrator line carries in a character's place: what
     * tells such a line apart everywhere it is drawn, and never a skin.
     */
    public static final String SKIN_ID = "losttales:narrator";

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
