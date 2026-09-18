package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import net.minecraft.util.StatCollector;

/**
 * The sphere a head wears for the presence of the identity it shows —
 * the account, or the one character the head is — at the head's
 * bottom-right corner the way a messenger's status dot sits on an
 * avatar: green for Online, honey for Away, crimson for Do Not Disturb,
 * and the muted sphere for Offline, which is also what everyone else
 * sees of Invisible.
 *
 * <p>The head is <em>cut</em> at the sphere's corner rather than painted
 * over: {@link #beginHeadCut} takes the sphere grown by
 * {@link #CUT_MARGIN} on every side away from every layer of the head
 * while it is drawn, and the sphere is drawn in it. What is taken is the
 * sphere's shape grown, not its box: the clear pixel runs along the
 * sides the sphere has ink on, and the corner it points at, where its
 * artwork is clear, keeps its head pixel. The sphere stands two pixels
 * past the head's right edge and one below its bottom, a pixel higher
 * than it is far in.</p>
 *
 * <p>Only a player's own head on their own line wears one. An NPC has no
 * account and no presence, and neither has a line from the server, the
 * client or the Discord bridge; a reply's quote wears the quoted head
 * without one, as a messenger's reply preview does, since a quote does
 * not say which of the sender's characters spoke.</p>
 */
public final class ChatPresenceMark {
    /** The sphere's size on screen: the sheet's own, one texel to one pixel. */
    public static final int SIZE = 5;
    /** How far the sphere's left edge stands short of the head's right edge. */
    public static final int INSET_X = 3;
    /** How far its top edge stands short of the head's bottom edge: one more. */
    public static final int INSET_Y = INSET_X + 1;
    /**
     * How far the sphere stands past the head's own edges. A head that
     * wears one is laid out as this much wider and taller: the head and
     * its sphere are one icon, so a name beside it keeps its clear space
     * from the sphere rather than from the face.
     */
    public static final int OVERHANG_X = SIZE - INSET_X;
    public static final int OVERHANG_Y = SIZE - INSET_Y;
    /**
     * How far past the sphere the head is cut, on every side: one clear
     * pixel, so the two never touch. Only the top and left of it fall on
     * the head at all — the sphere's other two sides already stand past
     * the head's edges — and the corner they meet at keeps its pixel,
     * since the sphere is round and has no ink of its own there.
     */
    public static final int CUT_MARGIN = 1;

    private ChatPresenceMark() {}

    /**
     * Opens the cut a head drawn at {@code headX}, {@code headY},
     * {@code headSize} square gives its sphere. Always ended in a
     * {@code finally} with {@link #endHeadCut}.
     */
    public static void beginHeadCut(float headX, float headY,
                                    float headSize) {
        float sphereX = headX + headSize - INSET_X;
        float sphereY = headY + headSize - INSET_Y;
        // The column left of the sphere goes with it, and so does the
        // row above it — but not the pixel where those two meet, which
        // the sphere's round corner never reaches.
        LostTalesCharacterHeadIconRenderer.beginCorner(
                sphereX - CUT_MARGIN, sphereY,
                sphereX, sphereY - CUT_MARGIN);
    }

    /**
     * Opens the cut for the shadow of a head drawn at {@code headX},
     * {@code headY}: the shadow is the head a pixel down and to the
     * right, so its cut is carried with it, and the edge the head is cut
     * along casts its shadow into the cut as every other edge of the
     * head casts one past itself. Ended like the head's own.
     */
    public static void beginShadowCut(float headX, float headY,
                                      float headSize) {
        beginHeadCut(headX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                headY + LostTalesChatVisualStyle.SHADOW_OFFSET, headSize);
    }

    /** Ends the cut, whether the head drew or not. */
    public static void endHeadCut() {
        LostTalesCharacterHeadIconRenderer.endCorner();
    }

    /**
     * Draws the sphere on a head drawn at {@code headX}, {@code headY},
     * {@code headSize} square, with the chat's one shadow under it.
     */
    public static void draw(float headX, float headY, float headSize,
                            ChatPresence presence, int alpha) {
        if (alpha <= 0) {
            return;
        }
        sphereOf(presence).drawWithShadow(headX + headSize - INSET_X,
                headY + headSize - INSET_Y, alpha);
    }

    /**
     * The sphere a presence wears: the muted one for Offline, for
     * Invisible, which nobody else is ever shown, and for none at all.
     */
    public static LostTalesUiSheet sphereOf(ChatPresence presence) {
        if (presence == ChatPresence.ONLINE) {
            return LostTalesUiSheet.PRESENCE_ONLINE;
        }
        if (presence == ChatPresence.AWAY) {
            return LostTalesUiSheet.PRESENCE_AWAY;
        }
        return presence == ChatPresence.DO_NOT_DISTURB
                ? LostTalesUiSheet.PRESENCE_BUSY
                : LostTalesUiSheet.PRESENCE_OFFLINE;
    }

    /**
     * Whether a head wears a sphere and gives up its corner: a player's
     * own head on their own line — not an NPC's, not a mark standing in
     * for a head, not one of the synthetic senders a line from the
     * server, the client or the Discord bridge carries, and not the head
     * a reply's quote wears.
     */
    public static boolean wears(ChatHeadMarker.Data head) {
        return head != null && !head.npcIdentity && !head.quoted
                && head.mark() == null && head.senderId != null
                && !LostTalesChatMessagePacket.isSystemSender(head.senderId)
                && !LostTalesChatMessagePacket.isDiscordSender(head.senderId);
    }

    /**
     * What the identity a line's head shows is doing: its account's
     * presence on an account line, its character's on a character line;
     * Offline where the line does not say which character spoke.
     */
    public static ChatPresence presenceOf(ChatHeadMarker.Data head) {
        if (head == null || (!head.accountIdentity && head.characterId == null)) {
            return ChatPresence.OFFLINE;
        }
        return ClientChatPresence.presenceOf(head.senderId, head.accountIdentity
                ? ChatPresenceIdentity.ACCOUNT
                : ChatPresenceIdentity.character(head.characterId));
    }

    /** What a card says of a presence; empty for Online, which is no news. */
    public static String label(ChatPresence presence) {
        return presence == null || presence == ChatPresence.ONLINE ? ""
                : StatCollector.translateToLocal(presence.labelKey());
    }
}
