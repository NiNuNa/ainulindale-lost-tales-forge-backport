package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * An unseen voice: how a line heard nearby reads while the One Ring
 * conceals its speaker (CHAT-84, ACC-03). Nobody sees the speaker, so
 * the line wears no head, no name, no title and no card. Only the speaker
 * and staff holding {@code chat.moderate} read it as the speaker's own.
 *
 * <p>The line the server keeps is the speaker's, marked unseen; every
 * other reader is sent it masked, signed by {@link #SENDER_ID} with no
 * account and no character behind it, as the Narrator's lines are
 * ({@link ChatNarrator}). A reply's quote of such a line, and a forward
 * of it, are masked alike. Everything around the line is masked too, for
 * everyone but the speaker and staff: the speaker leaves the member list
 * of a conversation heard nearby, their typing there names the unseen
 * voice, so do their reactions to lines heard nearby, and the Server
 * Log's report and removal entries name the speaker to staff alone.</p>
 */
public final class ChatUnseenVoice {
    /**
     * The name a masked line is signed with; each game shows its own
     * words for it ({@link ChatNames#unseenVoice}).
     */
    public static final String NAME = "An unseen voice";
    /**
     * The skin id a masked line carries in a character's place: what
     * tells it apart everywhere it is drawn, and never a skin.
     */
    public static final String SKIN_ID = "losttales:unseen";
    /**
     * The sender id a masked line carries: nobody's account, so the line
     * names no one.
     */
    public static final UUID SENDER_ID = UUID.nameUUIDFromBytes(
            "losttales:unseen_voice".getBytes(Charset.forName("UTF-8")));

    private ChatUnseenVoice() {}

    /** The colour an unseen voice is named in: the chat's quiet tone. */
    public static int color() {
        return LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);
    }

    /** Whether a line's skin id marks it as an unseen voice's. */
    public static boolean isUnseenSkin(String skinId) {
        return SKIN_ID.equals(skinId);
    }

    /**
     * Whether a line said in {@code channel} reads as an unseen voice:
     * one heard nearby, said while its speaker is {@code concealed}, and
     * not the Narrator's, which names nobody already.
     */
    public static boolean applies(ChatChannel channel, boolean concealed,
                                  boolean narrator) {
        return concealed && !narrator && channel != null
                && channel.getRecipientRule() == ChatRecipientRule.PROXIMITY;
    }
}
