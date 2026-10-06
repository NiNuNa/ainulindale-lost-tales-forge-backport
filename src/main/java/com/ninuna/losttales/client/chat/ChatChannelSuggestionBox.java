package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * The list a typed {@code #} opens over the input: the channels the
 * prefix names, each with its icon and its {@code #Name} in its own
 * colour, exactly as the emoji and mention lists open on {@code :}
 * and {@code @}. Picking one writes the channel's code name into the
 * message ({@code #gondor} for the Faction tab read as Gondor), which
 * is drawn as a link to the channel.
 */
final class ChatChannelSuggestionBox extends ChatSuggestionBox {
    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 3;

    private List<ChatChannel> matches = Collections.emptyList();
    private ChatChannelSuggester.Query query;
    private int selectedIndex;
    private int dismissedAtIndex = -1;

    /** Recomputes the query and its matches against the channels offered. */
    void update(String text, int cursor, List<ChatChannel> channels) {
        ChatChannelSuggester.Query found =
                ChatChannelSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedAtIndex = -1;
            return;
        }
        if (found.hashIndex != this.dismissedAtIndex) {
            this.dismissedAtIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.hashIndex != found.hashIndex
                || !this.query.prefix.equals(found.prefix);
        this.query = found;
        if (changed) {
            this.matches = ChatChannelSuggester.matches(found.prefix,
                    channels, ClientChatChannelState.scopeKeyRead(
                            ChatChannel.FACTION,
                            ClientChatChannelState.getSelected()),
                    MAX_ROWS, LostTalesWords.LANG);
            if (this.selectedIndex >= this.matches.size()) {
                this.selectedIndex = 0;
            }
        }
    }

    @Override
    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.hashIndex != this.dismissedAtIndex;
    }

    ChatChannel getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatChannelSuggester.Query getQuery() {
        return this.query;
    }

    void moveSelection(int delta) {
        if (!isActive()) {
            return;
        }
        int size = this.matches.size();
        this.selectedIndex =
                ((this.selectedIndex + delta) % size + size) % size;
    }

    /** Hides the current query's list until a new query starts. */
    void dismiss() {
        if (this.query != null) {
            this.dismissedAtIndex = this.query.hashIndex;
        }
    }

    @Override
    int shownRows() {
        return this.matches.size();
    }

    /** The suggestion on a row, or null. */
    ChatChannel at(int row) {
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              PointerRegions regions, int screenHeight, int inputX,
              double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        if (hoveredRow >= 0) {
            this.selectedIndex = hoveredRow;
        }
        int top = drawFrame(font, regions, screenHeight, inputX,
                this.selectedIndex < this.matches.size()
                        ? this.selectedIndex : -1);
        for (int row = 0; row < this.matches.size(); row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            ChatChannel channel = this.matches.get(row);
            // The channel's emoji as its tab wears it, half Discord's
            // while it is linked, over the one shadow.
            ChatChannelIcons.drawChannelEmoji(minecraft, channel,
                    inputX + PADDING, rowTop + 1, ICON_SIZE, 255);
            LostTalesUiInk.drawText(font, label(channel),
                    inputX + PADDING + ICON_SIZE + ICON_GAP, rowTop + 2,
                    ClientChatChannelState.displayColor(channel), 255);
        }
    }

    /** What a row reads: the channel's shown name behind the hash, a faction's for the Faction tab. */
    static String label(ChatChannel channel) {
        return "#" + ClientChatChannelState.displayName(channel);
    }

    @Override
    int boxWidth(FontRenderer font) {
        int widest = 0;
        for (ChatChannel channel : this.matches) {
            widest = Math.max(widest, font.getStringWidth(label(channel)));
        }
        return PADDING + ICON_SIZE + ICON_GAP + widest + PADDING;
    }
}
