package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

/**
 * The selection and the tab and window verbs every other part of the
 * chat screen needs: the one place that changes the selected tab and
 * keeps the screen consistent afterwards — the window being typed in
 * comes to the front, drafts change hands with the tab, a completion
 * walked in the tab just left is over, a reply aimed at it is put down
 * — and the verbs that open a whisper, close a tab or a window, lock a
 * window to its neighbour, detach a tab into a window of its own, or
 * bring an open tab forward.
 */
final class ChatTabActions {
    /** Distance from another window's edge at which a drag snaps and links. */
    static final int LINK_SNAP = 6;
    /** Where a detached window's row lands: a little below its old one. */
    private static final int DETACH_DROP = 40;

    private final ChatInputBar bar;
    private final ChatInputCompletion completion;
    private final ChatComposer composer;
    private Minecraft mc;
    private GuiTextField field;
    private ChatTab lastSelected;

    ChatTabActions(ChatInputBar bar, ChatInputCompletion completion,
                   ChatComposer composer) {
        this.bar = bar;
        this.completion = completion;
        this.composer = composer;
    }

    /**
     * Takes the field the screen just built; called from {@code initGui},
     * which also runs on every resize. The selection as it stands is
     * what the next sync compares against, so a rebuild changes nothing.
     */
    void bind(Minecraft mc, GuiTextField field) {
        this.mc = mc;
        this.field = field;
        this.lastSelected = ClientChatChannelState.getSelected();
    }

    /**
     * Text belongs to the tab it was typed in: what the field holds goes
     * back to the tab just left, and the tab coming to the front brings
     * its own unsent text, if any. A walk through the tab's sent lines
     * ends here, so the line it left in the field is what goes back.
     */
    private void swapDraft(ChatTab previous, ChatTab selected) {
        if (this.field == null) {
            return;
        }
        ClientChatChannelState.endSentBrowse();
        if (previous != null) {
            ClientChatChannelState.setDraft(previous, this.field.getText());
        }
        this.field.setText(ClientChatChannelState.getDraft(selected));
        this.field.setCursorPositionEnd();
    }

    /**
     * Reacts to any selection change, whatever path caused it: the
     * selected channel comes to the front of its window, and every
     * window's front tab counts as read while the screen is open.
     */
    void syncSelection() {
        ChatTab selected = ClientChatChannelState.getSelected();
        // A set of marks is anchored on the tab being typed in and
        // always holds it: a tab that has gone is forgotten, and moving
        // the input off the set — to another tab, or another window —
        // ends it.
        ChatTabSelection.prune();
        if (ChatTabSelection.windowId() != null
                && !ChatTabSelection.isSelected(selected)) {
            ChatTabSelection.clear();
        }
        // The selected tab is always the front tab of its window, whether
        // the selection just changed or the layout came back from its
        // file with another tab in front; setting what is already set
        // changes nothing.
        // A page brought in front of the tab typed in sends the input to
        // the conversation in front of the window last brought forward,
        // which stays where it stands; with none, the input waits behind
        // the page with no bar.
        boolean moved = false;
        if (ChatWindowLayout.showsPage(ChatWindowLayout.windowOf(selected))) {
            ChatTab elsewhere = frontConversationElsewhere();
            if (elsewhere != null) {
                ClientChatChannelState.select(elsewhere);
                selected = ClientChatChannelState.getSelected();
                moved = true;
            }
        }
        if (!ChatWindowLayout.showsPage(ChatWindowLayout.windowOf(selected))) {
            ChatWindowLayout.setActiveTab(selected);
        }
        if (!selected.equals(this.lastSelected)) {
            ChatTab previous = this.lastSelected;
            this.lastSelected = selected;
            // The window being typed in comes to the front, a completion
            // walked in the tab just left is over, and so is its read
            // unread divider: it was there to be seen, and it was.
            this.completion.dismissCompletion();
            ClientChatChannelViews.dismissSeenDivider(previous);
            ChatWindow selectedWindow = ChatWindowLayout.windowOf(selected);
            if (selectedWindow != null && !moved) {
                ChatWindowLayout.raise(selectedWindow.getId());
            }
            this.bar.updateInputBounds();
            swapDraft(previous, selected);

        }
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            ChatWindow window = windows.get(index);
            ChatTab front = ChatWindowFrame.activeTab(window,
                    ChatWindowFrame.visibleTabs(window));
            if (front != null && !front.isPage()) {
                ClientChatChannelViews.markViewed(front);
            }
        }
    }

    /**
     * The conversation in front of the window last brought forward among
     * those that show one; null while every window shows a page.
     */
    private static ChatTab frontConversationElsewhere() {
        List<ChatWindow> stacked = ChatWindowLayout.stacked();
        for (int index = stacked.size() - 1; index >= 0; index--) {
            ChatWindow window = stacked.get(index);
            ChatTab front = ChatWindowFrame.activeTab(window,
                    ChatWindowFrame.visibleTabs(window));
            if (front != null && ClientChatChannelState.isSelectable(front)) {
                return front;
            }
        }
        return null;
    }

    /**
     * Moves the input to a window: its front tab becomes the selected
     * one, so what is typed goes where the player just clicked. Does
     * nothing for the window that already has it.
     */
    void selectWindow(ChatWindow window) {
        if (window == null) {
            return;
        }
        ChatTab front = ChatWindowFrame.activeTab(window,
                ChatWindowFrame.visibleTabs(window));
        if (front != null && front.isPage()) {
            // A page takes no input: its window only comes forward.
            ChatWindowLayout.raise(window.getId());
            return;
        }
        if (front != null && !front.equals(
                ClientChatChannelState.getSelected())) {
            selectChannel(front);
        }
    }

    void selectChannel(ChatChannel channel) {
        selectChannel(ChatTab.of(channel));
    }

    /**
     * Makes a tab the one being typed into. A reply, and an edit, belong
     * to the tab they were started in; moving away from that tab
     * abandons them rather than carrying them along. The pickers close,
     * the field takes focus, and the mention candidates — shaped per
     * channel identity — are rebuilt.
     */
    void selectChannel(ChatTab tab) {
        if (tab != null && tab.isPage()) {
            // A page is never typed into: it comes in front of its
            // window, and the input moves off it.
            ChatWindowLayout.showPage(tab);
            syncSelection();
            return;
        }
        this.composer.onTabSelected(tab);
        ClientChatChannelState.select(tab);
        if (tab != null && tab.equals(ClientChatChannelState.getSelected())) {
            // The conversation picked comes in front of its window, over a
            // page there too; a page only moves the input when it is the
            // one brought forward.
            ChatWindowLayout.setActiveTab(tab);
        }
        syncSelection();
        this.field.setFocused(true);
        this.completion.invalidateMentionCandidates();
    }

    /**
     * Opens (and selects) the conversation a name names: with the
     * account when the name is theirs, and with the person as they are
     * playing when it is the name on their lines. A name nobody online
     * answers to opens the account conversation, which is what the
     * server refuses if it is nobody's — the notice is the server's to
     * give, not a guess made here.
     */
    ChatTab openWhisperTab(String account) {
        String[] played = ClientChatChannelState.playedBy(account);
        return played == null ? openWhisperTab(account, "")
                : openWhisperTab(played[0], played[1]);
    }

    /** As above with one identity of that account; empty is its own. */
    ChatTab openWhisperTab(String account, String identity) {
        String name = account == null ? "" : account.trim();
        if (name.length() == 0) {
            return null;
        }
        if (this.mc.thePlayer != null && name.equalsIgnoreCase(
                this.mc.thePlayer.getCommandSenderName())) {
            this.bar.showNotice(StatCollector.translateToLocal(
                    "chat.losttales.whisper.self"));
            return null;
        }
        ChatWindow current = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatTab tab = ChatWindowLayout.openWhisper(name, identity,
                current == null ? null : current.getId());
        if (tab != null) {
            selectChannel(tab);
        }
        return tab;
    }

    /**
     * Closes a whole window: its tabs leave it and the window goes. The
     * channels behind them keep receiving, so nothing is lost — they are
     * offered back by the {@code +} control and by the empty state.
     */
    void closeWindow(ChatWindow window) {
        if (window == null
                || !ChatWindowLayout.closeWindow(window.getId())) {
            return;
        }
        ChatTabSelection.prune();
        syncSelection();
    }

    /**
     * Lets a window fill a part of the screen, or gives it back the
     * place and size it had, which filling the screen leaves as they
     * were. The window comes to the front either way, since it is the
     * one being looked at.
     */
    void setWindowFill(ChatWindow window, ChatWindow.ScreenFill fill) {
        if (window == null) {
            return;
        }
        ChatWindowLayout.raise(window.getId());
        ChatWindowLayout.setFill(window.getId(), fill, true);
    }

    /**
     * Locks or unlocks a window, and with it whether it is stuck to a
     * neighbour: a window locked while it touches another sticks to it
     * and moves with it from then on, and unlocking lets go again. The
     * locked window is the one that follows, since a locked window is
     * the one that cannot be dragged.
     */
    void setWindowLocked(ChatWindow window, boolean locked) {
        if (window == null) {
            return;
        }
        ChatWindowLayout.setLocked(window.getId(), locked);
        if (!locked) {
            ChatWindowLayout.unlink(window.getId());
            return;
        }
        ChatWindowFrame frame = ChatWindowFrame.find(window.getId());
        if (frame == null || !frame.drawn) {
            return;
        }
        ChatWindow neighbour = touchingWindow(frame);
        if (neighbour != null
                // The window it touches may already be stuck to this one;
                // two windows never hold each other.
                && !window.getId().equals(neighbour.getLinkTarget())) {
            ChatWindowLayout.link(window.getId(), neighbour.getId(),
                    touchingSide(frame, neighbour));
        }
    }

    /** The window this one is resting against, or null. */
    private static ChatWindow touchingWindow(ChatWindowFrame frame) {
        List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            ChatWindowFrame other = frames.get(index);
            if (other == frame) {
                continue;
            }
            ChatWindow candidate = ChatWindowLayout.window(other.windowId);
            // A window filling the screen has no edge of its own to be
            // stuck to.
            if (candidate != null
                    && candidate.getFill() == ChatWindow.ScreenFill.NONE
                    && touchingSide(frame, candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Which of a neighbour's edges this window is resting against, or
     * null when it is against none of them. The same margin a snap uses,
     * so a window that showed the touch highlight is one that sticks.
     */
    private static ChatWindow.LinkSide touchingSide(ChatWindowFrame frame,
                                                    ChatWindow neighbour) {
        ChatWindowFrame other = ChatWindowFrame.find(neighbour.getId());
        if (other == null || !other.drawn) {
            return null;
        }
        return touchingSide(frame, other);
    }

    /** As above between two drawn frames. */
    static ChatWindow.LinkSide touchingSide(ChatWindowFrame frame,
                                            ChatWindowFrame other) {
        int margin = ChatWindowPlacement.WINDOW_GAP;
        boolean overlapsColumn = frame.boxLeft < other.boxRight + margin
                && frame.boxRight + margin > other.boxLeft;
        boolean overlapsRow = frame.boxTop < other.boxBottom + margin
                && frame.boxBottom + margin > other.boxTop;
        if (overlapsColumn) {
            if (Math.abs(other.boxTop - margin - frame.boxBottom)
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.ABOVE;
            }
            if (Math.abs(frame.boxTop - (other.boxBottom + margin))
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.BELOW;
            }
        }
        if (overlapsRow) {
            if (Math.abs(frame.boxRight + margin - other.boxLeft)
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.LEFT;
            }
            if (Math.abs(frame.boxLeft - (other.boxRight + margin))
                    <= LINK_SNAP) {
                return ChatWindow.LinkSide.RIGHT;
            }
        }
        return null;
    }

    /** Closes one tab; the group it was marked with ends with it. */
    void closeChannel(ChatTab channel) {
        if (ClientChatChannelState.close(channel)) {
            // What is left of the marks would be anchored on a tab that
            // has gone.
            ChatTabSelection.clear();
            syncSelection();
        }
    }

    /**
     * What Ctrl+W closes: the marked tabs while more than one is marked,
     * and the tab being typed in otherwise. The marks always hold the
     * tab in front, so a group closes that one with the rest. A locked
     * window refuses its tabs either way, so a group holding nothing
     * closable closes nothing.
     */
    void closeMarkedOrActiveTabs() {
        if (!ChatTabSelection.isGroup()) {
            closeChannel(ClientChatChannelState.getSelected());
            return;
        }
        List<ChatTab> marked = ChatTabSelection.selectedIn(
                ChatWindowLayout.window(ChatTabSelection.windowId()));
        boolean closed = false;
        for (int index = 0; index < marked.size(); index++) {
            closed |= ClientChatChannelState.close(marked.get(index));
        }
        if (closed) {
            ChatTabSelection.clear();
            syncSelection();
        }
    }

    /**
     * The tab menu's way of giving a channel a window of its own: the
     * new window lands a little below its old row, kept on screen, and
     * the channel stays selected there.
     */
    void detachChannel(ChatTab channel, int screenWidth, int screenHeight) {
        ChatWindow window = ChatWindowLayout.windowOf(channel);
        if (window == null) {
            return;
        }
        ChatTabSelection.clear();
        ChatWindowFrame frame = ChatWindowFrame.of(window);
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                null, this.mc, frame.boxLeft,
                frame.tabRowBottom() + DETACH_DROP
                        + ChatWindowPlacement.lineHeight(this.mc),
                screenWidth, screenHeight);
        ChatWindow detached = ChatWindowLayout.detach(channel,
                ChatWindowPlacement.windowPercentX(anchor.x, this.mc,
                        screenWidth),
                ChatWindowPlacement.windowPercentY(null, anchor.baseline,
                        this.mc, screenHeight));
        if (detached != null) {
            ChatWindowFrame.of(detached).beginAppearing();
            selectChannel(channel);
        }
    }

    /**
     * Brings a tab that is already open to the front of its own window
     * and moves the input there: what picking an open row in the search
     * panel does.
     */
    void jumpToTab(ChatTab tab) {
        ChatWindow window = tab == null ? null
                : ChatWindowLayout.windowOf(tab);
        if (window == null) {
            return;
        }
        ChatWindowLayout.raise(window.getId());
        selectChannel(tab);
    }
}
