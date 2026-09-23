package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import net.minecraft.util.StatCollector;

/**
 * The mark a head wears for the presence of the identity it shows — the
 * account, or the one character the head is — at the head's bottom-right
 * corner the way a messenger's status dot sits on an avatar, each status
 * a shape as well as a colour, so it reads without the colour: the green
 * sphere for Online, a honey crescent for Away, a crimson disc barred
 * across for Do Not Disturb, and a muted ring for Offline, which is also
 * what everyone else sees of Invisible.
 *
 * <p>The head is <em>cut</em> at the mark's corner rather than painted
 * over: {@link #beginHeadCut} takes the mark's shape grown by a pixel up,
 * down, left and right away from every layer of the head while it is
 * drawn ({@link LostTalesUiCornerCut}), and the mark is drawn in it. What
 * is taken follows the round outline every mark shares, not its box: the
 * clear pixel runs along the sides the mark has ink on, and the head
 * keeps the pixels off its rounded corner. The mark stands two pixels
 * past the head's right edge and one below its bottom, a pixel higher
 * than it is far in.</p>
 *
 * <p>Every voice that can be online wears one ({@link #hasStatus}): a
 * player's account and characters, the server, which is online while it
 * runs, and a Discord member while the server follows their Discord
 * status. The mark standing for the server's or a member's head gives its
 * corner up as a head does. An NPC has no account and no presence, and
 * neither has the client, the Narrator or the Discord bridge itself; a
 * reply's quote wears the quoted head without one, as a messenger's reply
 * preview does, since a quote does not say which of the sender's
 * characters spoke.</p>
 */
public final class ChatPresenceMark {
    /** A mark's size on screen: the sheet's own, one texel to one pixel. */
    public static final int SIZE = 5;
    /** How far the mark's left edge stands short of the head's right edge. */
    public static final int INSET_X = 3;
    /** How far its top edge stands short of the head's bottom edge: one more. */
    public static final int INSET_Y = INSET_X + 1;
    /**
     * How far the mark stands past the head's own edges. A head that
     * wears one is laid out as this much wider and taller: the head and
     * its mark are one icon, so a name beside it keeps its clear space
     * from the mark rather than from the face.
     */
    public static final int OVERHANG_X = SIZE - INSET_X;
    public static final int OVERHANG_Y = SIZE - INSET_Y;
    /**
     * The outline every mark shares, and the ivory sphere with them, row
     * by row from its top: the column each row's ink starts at. Round:
     * its top and bottom rows are a pixel in from its sides
     * ({@code ChatPresenceMarkTest} reads the sheet to hold it).
     */
    static final int[] SPHERE_INK_LEFT = {1, 0, 0, 0, 1};

    private ChatPresenceMark() {}

    /** The cut a head drawn at {@code headX}, {@code headY}, {@code headSize} square gives its mark. */
    public static LostTalesUiCornerCut cutFor(float headX, float headY,
                                              float headSize) {
        return LostTalesUiCornerCut.around(headX + headSize - INSET_X,
                headY + headSize - INSET_Y, SPHERE_INK_LEFT);
    }

    /**
     * Opens the cut a head drawn at {@code headX}, {@code headY},
     * {@code headSize} square gives its mark. Always ended in a
     * {@code finally} with {@link #endHeadCut}.
     */
    public static void beginHeadCut(float headX, float headY,
                                    float headSize) {
        LostTalesCharacterHeadIconRenderer.beginCorner(
                cutFor(headX, headY, headSize));
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
     * Draws the mark on a head drawn at {@code headX}, {@code headY},
     * {@code headSize} square, with the chat's one shadow under it.
     */
    public static void draw(float headX, float headY, float headSize,
                            ChatPresence presence, int alpha) {
        if (alpha <= 0) {
            return;
        }
        markOf(presence).drawWithShadow(headX + headSize - INSET_X,
                headY + headSize - INSET_Y, alpha);
    }

    /**
     * The mark a presence wears: the muted ring for Offline, for
     * Invisible, which nobody else is ever shown, and for none at all.
     */
    public static LostTalesUiSheet markOf(ChatPresence presence) {
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
     * Whether a head wears a mark and gives up its corner: the head of
     * a voice with a status on its own line, and never the head a reply's
     * quote wears.
     */
    public static boolean wears(ChatHeadMarker.Data head) {
        return head != null && !head.quoted && hasStatus(head.senderId,
                head.npcIdentity, head.isNarrator());
    }

    /**
     * Whether a voice can be online at all: a player's account or
     * character, the server, and a Discord member while the server follows
     * Discord statuses — never an NPC, the client, the Narrator or the
     * Discord bridge itself.
     */
    public static boolean hasStatus(UUID senderId, boolean npc,
                                    boolean narrator) {
        if (senderId == null || npc || narrator
                || LostTalesChatMessagePacket.isClientSender(senderId)
                || LostTalesChatMessagePacket.DISCORD_SENDER_ID.equals(senderId)) {
            return false;
        }
        return !LostTalesChatMessagePacket.isDiscordSender(senderId)
                || ClientChatPresence.showsDiscordStatuses();
    }

    /**
     * What a voice with a status is doing: the server is online while it
     * runs, a Discord member as Discord says, and a player's identity as
     * the server says — the account on an account line, the character on
     * a character line, and Offline where a line does not say which
     * character spoke.
     */
    public static ChatPresence statusOf(UUID senderId, boolean accountIdentity,
                                        UUID characterId) {
        if (LostTalesChatMessagePacket.isServerSender(senderId)) {
            return ChatPresence.ONLINE;
        }
        if (!accountIdentity && characterId == null) {
            return ChatPresence.OFFLINE;
        }
        return ClientChatPresence.presenceOf(senderId, accountIdentity
                ? ChatPresenceIdentity.ACCOUNT
                : ChatPresenceIdentity.character(characterId));
    }

    /**
     * What the identity a line's head shows is doing: its account's
     * presence on an account line, its character's on a character line;
     * Offline where the line does not say which character spoke.
     */
    public static ChatPresence presenceOf(ChatHeadMarker.Data head) {
        return head == null ? ChatPresence.OFFLINE
                : statusOf(head.senderId, head.accountIdentity,
                        head.characterId);
    }

    /** What a card says of a presence; empty for Online, which is no news. */
    public static String label(ChatPresence presence) {
        return presence == null || presence == ChatPresence.ONLINE ? ""
                : StatCollector.translateToLocal(presence.labelKey());
    }
}
