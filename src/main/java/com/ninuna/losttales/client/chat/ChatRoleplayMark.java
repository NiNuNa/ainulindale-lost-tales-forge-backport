package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;

/**
 * The mark of a role-play status beside a name, on a card and in a member
 * list (P4 a): a small diamond — solid for In Character, hollow for Out
 * of Character, holed for Looking for a Scene — each in a colour of its
 * own, casting the one shadow. Drawn from its own pixels, standing in
 * until the marks' artwork is painted. An identity whose status is its
 * own default — a character in character, the account out of it — shows
 * no mark, so the lists stay quiet until somebody says otherwise.
 */
public final class ChatRoleplayMark {
    /** A mark's box. */
    public static final int SIZE = 5;
    /** Between a name and its mark. */
    public static final int GAP = 3;

    private static final String[] IN_CHARACTER = {
            "..#..",
            ".###.",
            "#####",
            ".###.",
            "..#.."};
    private static final String[] OUT_OF_CHARACTER = {
            "..#..",
            ".#.#.",
            "#...#",
            ".#.#.",
            "..#.."};
    private static final String[] LOOKING_FOR_SCENE = {
            "..#..",
            ".###.",
            "##.##",
            ".###.",
            "..#.."};

    private ChatRoleplayMark() {}

    /**
     * The status an identity of an account wears a mark for: the one the
     * server says it shows, where that is not its default; null for none,
     * and for the Server and Discord members, who are not in the role-play.
     */
    static ChatRoleplayStatus markedFor(UUID account,
                                        ChatPresenceIdentity identity) {
        if (account == null || identity == null
                || LostTalesChatMessagePacket.isSystemSender(account)
                || LostTalesChatMessagePacket.isDiscordSender(account)) {
            return null;
        }
        ChatRoleplayStatus status = ClientChatPresence.roleplayOf(account,
                identity);
        return status == null || status == ChatRoleplayStatus.defaultFor(
                identity) ? null : status;
    }

    /** Draws the status's mark from {@code (x, y)}, over its shadow, at {@code alpha}. */
    public static void draw(ChatRoleplayStatus status, float x, float y,
                            int alpha) {
        if (status == null || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        String[] rows = rowsOf(status);
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawPixels(rows, x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        drawPixels(rows, x, y, LostTalesUiInk.argb(rgbOf(status), alpha));
        LostTalesUiInk.beginContent();
    }

    private static String[] rowsOf(ChatRoleplayStatus status) {
        switch (status) {
            case IN_CHARACTER:
                return IN_CHARACTER;
            case LOOKING_FOR_SCENE:
                return LOOKING_FOR_SCENE;
            default:
                return OUT_OF_CHARACTER;
        }
    }

    /** Each status's colour: seafoam in character, honey looking for a scene, rose beige out of character. */
    static int rgbOf(ChatRoleplayStatus status) {
        switch (status) {
            case IN_CHARACTER:
                return LostTalesColors.rgb(LostTalesColors.SEAFOAM);
            case LOOKING_FOR_SCENE:
                return LostTalesColors.rgb(LostTalesColors.HONEY);
            default:
                return LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
        }
    }

    /** The glyph's pixels in one colour, each run of a row one quad, so no pixel is laid down twice. */
    private static void drawPixels(String[] rows, float x, float y, int argb) {
        for (int row = 0; row < rows.length; row++) {
            String pixels = rows[row];
            int column = 0;
            while (column < pixels.length()) {
                if (pixels.charAt(column) != '#') {
                    column++;
                    continue;
                }
                int start = column;
                while (column < pixels.length()
                        && pixels.charAt(column) == '#') {
                    column++;
                }
                LostTalesUiInk.fillRect(x + start, y + row, x + column,
                        y + row + 1.0F, argb);
            }
        }
    }
}
