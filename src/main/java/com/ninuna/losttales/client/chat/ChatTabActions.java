package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.TabSelection;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowGestures;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowTab;
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
 * — and the verbs that open a whisper, close a tab or a window, detach a
 * tab into a window of its own, or bring an open tab forward.
 */
public final class ChatTabActions {
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
        TabSelection.prune();
        if (TabSelection.windowId() != null
                && !TabSelection.isSelected(selected)) {
            TabSelection.clear();
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
        if (WindowLayout.showsPage(WindowLayout.windowOf(selected))) {
            ChatTab elsewhere = frontConversationElsewhere();
            if (elsewhere != null) {
                ClientChatChannelState.select(elsewhere);
                selected = ClientChatChannelState.getSelected();
                moved = true;
            }
        }
        if (!WindowLayout.showsPage(WindowLayout.windowOf(selected))) {
            WindowLayout.setActiveTab(selected);
        }
        if (!selected.equals(this.lastSelected)) {
            ChatTab previous = this.lastSelected;
            this.lastSelected = selected;
            // The window being typed in comes to the front, a completion
            // walked in the tab just left is over, and so is its read
            // unread divider: it was there to be seen, and it was.
            this.completion.dismissCompletion();
            ClientChatChannelViews.dismissSeenDivider(previous);
            Window selectedWindow = WindowLayout.windowOf(selected);
            if (selectedWindow != null && !moved) {
                WindowLayout.raise(selectedWindow.getId());
            }
            this.bar.updateInputBounds();
            swapDraft(previous, selected);

        }
        List<Window> windows = WindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            Window window = windows.get(index);
            ChatTab front = ChatTab.from(ChatFrame.activeTab(window,
                    ChatFrame.visibleTabs(window)));
            if (front != null) {
                ClientChatChannelViews.markViewed(front);
            }
        }
    }

    /**
     * The conversation in front of the window last brought forward among
     * those that show one; null while every window shows a page.
     */
    private static ChatTab frontConversationElsewhere() {
        List<Window> stacked = WindowLayout.stacked();
        for (int index = stacked.size() - 1; index >= 0; index--) {
            Window window = stacked.get(index);
            ChatTab front = ChatTab.from(ChatFrame.activeTab(window,
                    ChatFrame.visibleTabs(window)));
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
    public void selectWindow(Window window) {
        if (window == null) {
            return;
        }
        WindowTab front = ChatFrame.activeTab(window,
                ChatFrame.visibleTabs(window));
        if (front instanceof PageTab) {
            // A page takes no input: its window only comes forward.
            WindowLayout.raise(window.getId());
            return;
        }
        if (front != null && !front.equals(
                ClientChatChannelState.getSelected())) {
            selectChannel(front);
        }
    }

    public void selectChannel(ChatChannel channel) {
        selectChannel(ChatTab.of(channel));
    }

    /**
     * Makes a tab the one being typed into. A reply, and an edit, belong
     * to the tab they were started in; moving away from that tab
     * abandons them rather than carrying them along. The pickers close,
     * the field takes focus, and the mention candidates — shaped per
     * channel identity — are rebuilt.
     */
    public void selectChannel(WindowTab picked) {
        if (picked instanceof PageTab) {
            // A page is never typed into: it comes in front of its
            // window, and the input moves off it.
            WindowLayout.showPage((PageTab)picked);
            syncSelection();
            return;
        }
        ChatTab tab = ChatTab.from(picked);
        this.composer.onTabSelected(tab);
        ClientChatChannelState.select(tab);
        if (tab != null && tab.equals(ClientChatChannelState.getSelected())) {
            // The conversation picked comes in front of its window, over a
            // page there too; a page only moves the input when it is the
            // one brought forward.
            WindowLayout.setActiveTab(tab);
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
        Window current = WindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        ChatTab tab = ChatLayout.openWhisper(name, identity,
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
    void closeWindow(Window window) {
        if (window == null
                || !WindowLayout.closeWindow(window.getId())) {
            return;
        }
        TabSelection.prune();
        syncSelection();
    }

    /**
     * Closes one tab, a conversation through the chat and a page as any
     * window's tab; the group it was marked with ends with it.
     */
    void closeTab(WindowTab tab) {
        ChatTab conversation = ChatTab.from(tab);
        if (conversation != null ? ClientChatChannelState.close(conversation)
                : WindowLayout.close(tab)) {
            // What is left of the marks would be anchored on a tab that
            // has gone.
            TabSelection.clear();
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
        if (!TabSelection.isGroup()) {
            closeTab(ClientChatChannelState.getSelected());
            return;
        }
        List<WindowTab> marked = TabSelection.selectedIn(
                WindowLayout.window(TabSelection.windowId()));
        boolean closed = false;
        for (int index = 0; index < marked.size(); index++) {
            ChatTab conversation = ChatTab.from(marked.get(index));
            closed |= conversation != null
                    ? ClientChatChannelState.close(conversation)
                    : WindowLayout.close(marked.get(index));
        }
        if (closed) {
            TabSelection.clear();
            syncSelection();
        }
    }

    /**
     * The tab menu's way of giving a channel a window of its own: the
     * new window lands a little below its old row, kept on screen, and
     * the channel stays selected there.
     */
    void detachChannel(WindowTab channel, int screenWidth, int screenHeight) {
        Window window = WindowLayout.windowOf(channel);
        if (window == null) {
            return;
        }
        TabSelection.clear();
        ChatFrame frame = ChatFrame.of(window);
        WindowPlacement.Anchor anchor = WindowPlacement.constrainWindow(
                null, this.mc, frame.boxLeft,
                frame.tabRowBottom() + DETACH_DROP
                        + WindowPlacement.lineHeight(this.mc),
                screenWidth, screenHeight);
        Window detached = WindowLayout.detach(channel,
                WindowPlacement.windowPercentX(anchor.x, this.mc,
                        screenWidth),
                WindowPlacement.windowPercentY(null, anchor.baseline,
                        this.mc, screenHeight));
        if (detached != null) {
            ChatFrame.of(detached).beginAppearing();
            selectChannel(channel);
        }
    }

    /**
     * Brings a tab that is already open to the front of its own window
     * and moves the input there: what picking an open row in the search
     * panel does.
     */
    void jumpToTab(ChatTab tab) {
        Window window = tab == null ? null
                : WindowLayout.windowOf(tab);
        if (window == null) {
            return;
        }
        WindowLayout.raise(window.getId());
        selectChannel(tab);
    }
}
