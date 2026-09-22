package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;

/**
 * What a channel's icon says in its corner about what waits there: the
 * crimson tile counting the pings not yet read, or else the white sphere
 * for anything else not yet read, or nothing. In a conversation with one
 * person every line not yet read is a ping, as a messenger's direct
 * messages are, so a whisper's tab counts them all on the tile. The tile
 * goes to nine; past nine it stays the nine.
 *
 * <p>It stands where a head's status sphere stands, two pixels past the
 * icon's right edge and one below its bottom, and the icon gives it its
 * corner as a head gives its sphere one: the mark's shape grown by a
 * pixel is cut out of the icon ({@link LostTalesUiCornerCut}). On a
 * whisper tab it takes the corner from the partner's status sphere while
 * anything is unread.</p>
 */
final class ChatIconMark {
    /** The tile's outline, row by row from its top: a plain rectangle. */
    static final int[] TILE_INK_LEFT = {0, 0, 0, 0, 0, 0, 0};
    /** Nothing waiting. */
    static final ChatIconMark NONE = new ChatIconMark(0, false);
    /** Something unread that pinged nobody. */
    static final ChatIconMark UNREAD = new ChatIconMark(0, true);

    private final int pings;
    private final boolean unread;

    private ChatIconMark(int pings, boolean unread) {
        this.pings = pings;
        this.unread = unread;
    }

    /** The tile for {@code count} pings; nothing for none. */
    static ChatIconMark pings(int count) {
        return count > 0 ? new ChatIconMark(count, true) : NONE;
    }

    /**
     * The mark a tab's icon wears: its pings on the tile — every unread
     * line, for a conversation with one person — else the sphere while
     * anything is unread.
     */
    static ChatIconMark of(ChatTab tab) {
        if (tab == null) {
            return NONE;
        }
        if (tab.isWhisper()) {
            return pings(ClientChatChannelViews.unreadCount(tab));
        }
        int pings = ClientChatChannelViews.unreadPingCount(tab);
        if (pings > 0) {
            return pings(pings);
        }
        return ClientChatChannelViews.unreadOtherCount(tab) > 0 ? UNREAD
                : NONE;
    }

    /** The mark for several tabs together: the sum of their pings, else the sphere if any has something unread. */
    static ChatIconMark combined(Iterable<ChatTab> tabs) {
        int pings = 0;
        boolean unread = false;
        for (ChatTab tab : tabs) {
            ChatIconMark mark = of(tab);
            pings += mark.pings;
            unread |= mark.unread;
        }
        return pings > 0 ? pings(pings) : unread ? UNREAD : NONE;
    }

    boolean isNone() {
        return !this.unread;
    }

    /** How many pings the tile counts; zero for the sphere and for nothing. */
    int pingCount() {
        return this.pings;
    }

    /** The mark's sprite: the tile for its count, or the white sphere. */
    LostTalesUiSheet sprite() {
        return this.pings > 0 ? LostTalesUiSheet.countTile(this.pings)
                : LostTalesUiSheet.PRESENCE_SELECTED;
    }

    /** The mark's outline, row by row, as {@link LostTalesUiCornerCut#around} reads one. */
    int[] inkLeft() {
        return this.pings > 0 ? TILE_INK_LEFT : ChatPresenceMark.SPHERE_INK_LEFT;
    }

    /** Where the mark's left edge stands on an icon drawn {@code iconSize} wide from {@code iconX}. */
    float markX(float iconX, float iconSize) {
        return iconX + iconSize + ChatPresenceMark.OVERHANG_X
                - sprite().getWidth();
    }

    /** Where the mark's top stands on an icon drawn {@code iconSize} tall from {@code iconY}. */
    float markY(float iconY, float iconSize) {
        return iconY + iconSize + ChatPresenceMark.OVERHANG_Y
                - sprite().getHeight();
    }

    /** The corner an icon drawn at {@code iconX}, {@code iconY}, {@code iconSize} square gives the mark. */
    LostTalesUiCornerCut cut(float iconX, float iconY, float iconSize) {
        return isNone() ? LostTalesUiCornerCut.NONE
                : LostTalesUiCornerCut.around(markX(iconX, iconSize),
                        markY(iconY, iconSize), inkLeft());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatIconMark)) {
            return false;
        }
        ChatIconMark mark = (ChatIconMark)other;
        return this.pings == mark.pings && this.unread == mark.unread;
    }

    @Override
    public int hashCode() {
        return this.pings * 2 + (this.unread ? 1 : 0);
    }

    /** The mark in the corner of an icon drawn at {@code iconX}, {@code iconY}, over the one shadow. */
    void draw(float iconX, float iconY, float iconSize, int alpha) {
        if (!isNone()) {
            sprite().drawWithShadow(markX(iconX, iconSize),
                    markY(iconY, iconSize), alpha);
        }
    }
}
