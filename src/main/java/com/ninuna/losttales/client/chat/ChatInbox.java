package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.quest.ClientQuestNews;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.TabMark;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.gui.screen.fellowship.FellowshipPage;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.StatCollector;

/**
 * What is addressed to the player, as the Inbox lists it, as Discord's
 * inbox: the fellowship invitations waiting for the character played,
 * the lines that mentioned the player, the messages forwarded to them in a
 * whisper, and this session's quest news, each under its heading and the
 * newest first, narrowed by its field. A sub-window hung from the inbox
 * button every conversation's tool strip has beside its Notification
 * Settings; a press on a row goes where it names: a line in its
 * conversation, an invitation on the Fellowships page, a quest in the
 * journal. Each kind is read where it is kept; the inbox keeps nothing of
 * its own. The button counts what waits: invitations not yet answered,
 * mentions and forwards not yet read in their conversation, quest news
 * the inbox has not shown.
 */
final class ChatInbox {
    /** What a row's id starts with, for what a press on it goes to. */
    static final String INVITATION = "invitation:";
    static final String LINE = "line:";
    static final String QUEST = "quest:";
    /** The most rows of one kind listed, the newest first. */
    private static final int MAX_ROWS = 50;
    /** How long the button's count stands before it is counted again. */
    private static final long COUNT_NANOS = 250L * 1000000L;

    private static int counted;
    private static long countedAt;
    private static boolean everCounted;

    /** The Inbox's rows and what a press on one does, for the sub-window ({@link ChatSubWindows#INBOX}). */
    static final WindowMenus.Source SOURCE = new WindowMenus.Source() {
        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                    "gui.losttales.inbox.search"), null,
                    LostTalesUiSheet.SEARCH, MenuWindow.MAX_FILTER_LENGTH,
                    false);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.INBOX);
            menu.setRows(rows(menu.filter()));
            // Shown, the quest news has been seen.
            ClientQuestNews.markSeen();
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return WindowMenus.firstTyped(menu);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            goTo(entry.id);
            return false;
        }
    };

    private ChatInbox() {}

    /** Goes where a row names. */
    private static void goTo(String id) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null || id == null) {
            return;
        }
        if (id.startsWith(LINE)) {
            // The inbox numbers its rows itself: a line's is its line id.
            ChatScreenPart chat = screen.part(ChatScreenPart.class);
            if (chat != null) {
                chat.jumpFromInbox(Integer.parseInt(
                        id.substring(LINE.length())));
            }
        } else if (id.startsWith(INVITATION)) {
            screen.showOnPage(FellowshipPage.PAGE_ID,
                    id.substring(INVITATION.length()), null);
        } else if (id.startsWith(QUEST)) {
            screen.showOnPage(QuestJournalPage.PAGE_ID,
                    id.substring(QUEST.length()), null);
        }
    }

    /** The mark the inbox button wears: the tile counting what waits, nothing when nothing does. */
    static TabMark mark() {
        return TabMark.pings(waiting());
    }

    /**
     * How much waits, counted again at most four times a second: every
     * conversation's strip asks each frame.
     */
    static synchronized int waiting() {
        long now = System.nanoTime();
        if (everCounted && now - countedAt < COUNT_NANOS) {
            return counted;
        }
        int count = invitations().size() + ClientQuestNews.unseen();
        for (Integer line : newest(LostTalesChatPresentation.pingedLines())) {
            if (listed(line.intValue())
                    && ClientChatChannelViews.isUnread(line.intValue())) {
                count++;
            }
        }
        for (Integer line : newest(LostTalesChatPresentation.forwardedLines())) {
            if (listed(line.intValue())
                    && ClientChatChannelViews.isUnread(line.intValue())) {
                count++;
            }
        }
        counted = count;
        countedAt = now;
        everCounted = true;
        return count;
    }

    /**
     * The inbox's rows, each kind under its heading, the newest first,
     * those whose words hold {@code query} where it is not empty. Nothing
     * at all says so; nothing found is no rows.
     */
    static List<MenuWindow.Entry> rows(String query) {
        String words = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        section(rows, "gui.losttales.inbox.invitations",
                invitationRows(words));
        section(rows, "gui.losttales.inbox.mentions",
                lineRows(LostTalesChatPresentation.pingedLines(), false, words,
                        now));
        section(rows, "gui.losttales.inbox.forwards",
                lineRows(LostTalesChatPresentation.forwardedLines(), true,
                        words, now));
        section(rows, "gui.losttales.inbox.quest_news", questRows(words, now));
        if (rows.isEmpty() && words.length() == 0) {
            rows.add(MenuWindow.Entry.note(StatCollector.translateToLocal(
                    "gui.losttales.inbox.empty")));
        }
        return rows;
    }

    private static void section(List<MenuWindow.Entry> rows, String headingKey,
                                List<MenuWindow.Entry> section) {
        if (!section.isEmpty()) {
            rows.add(MenuWindow.Entry.header(
                    StatCollector.translateToLocal(headingKey)));
            rows.addAll(section);
        }
    }

    /** The invitations waiting for the character played, as the fellowship state holds them. */
    private static List<FellowshipInvitationSnapshot> invitations() {
        FellowshipStateSnapshot snapshot = ClientFellowshipStateCache.getSnapshot();
        List<FellowshipInvitationSnapshot> waiting =
                new ArrayList<FellowshipInvitationSnapshot>();
        if (snapshot == null) {
            return waiting;
        }
        long now = System.currentTimeMillis();
        for (FellowshipInvitationSnapshot invitation
                : snapshot.getIncomingInvitations()) {
            if (!invitation.isExpired(now)) {
                waiting.add(invitation);
            }
        }
        return waiting;
    }

    private static List<MenuWindow.Entry> invitationRows(String words) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        WindowPage page = WindowPages.tab(FellowshipPage.PAGE_ID);
        List<FellowshipInvitationSnapshot> waiting = invitations();
        Collections.reverse(waiting);
        for (FellowshipInvitationSnapshot invitation : waiting) {
            String label = StatCollector.translateToLocalFormatted(
                    "gui.losttales.inbox.invitation",
                    invitation.getInvitingCharacterName(),
                    invitation.getFellowshipName());
            if (holds(label, words)) {
                rows.add(new MenuWindow.Entry(INVITATION
                        + invitation.getInvitationId(), label, false, -1, page)
                        .withTip(StatCollector.translateToLocal(
                                "gui.losttales.inbox.open_invitation")));
            }
        }
        return rows;
    }

    /**
     * The lines that mentioned the player, or were forwarded to them
     * ({@code forwards}), still standing in their conversation, each
     * before its conversation's icon with what waits there.
     */
    private static List<MenuWindow.Entry> lineRows(List<Integer> lines,
                                                   boolean forwards,
                                                   String words, long now) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (Integer line : newest(lines)) {
            int chatLineId = line.intValue();
            if (!listed(chatLineId)) {
                continue;
            }
            ConversationPage tab = ClientChatChannelViews.tabOf(chatLineId);
            LostTalesChatMessagePacket packet = ClientChatMessages.get(
                    ClientChatMessageIds.messageIdOf(chatLineId)).packet;
            String name = plain(packet.getIdentityName());
            if (name.length() == 0) {
                name = plain(packet.getAccountName());
            }
            String label = forwards
                    ? StatCollector.translateToLocalFormatted(
                            "gui.losttales.inbox.forward", name,
                            plain(packet.getReply().getAuthor()))
                    : name + ": " + plain(packet.getMessage());
            if (holds(label, words)) {
                rows.add(new MenuWindow.Entry(LINE + chatLineId, label, false,
                        -1, tab)
                        .withValue(ChatTimestampFormatter.formatDrawnStamp(
                                packet.getTimestampMillis(), now))
                        .withTip(StatCollector.translateToLocal(
                                "gui.losttales.inbox.jump")));
            }
        }
        return rows;
    }

    private static List<MenuWindow.Entry> questRows(String words, long now) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        WindowPage journal = WindowPages.tab(QuestJournalPage.PAGE_ID);
        for (ClientQuestNews.News news : ClientQuestNews.all()) {
            if (holds(news.line, words)) {
                rows.add(new MenuWindow.Entry(QUEST + news.questId,
                        news.line, false, -1, journal)
                        .withValue(ChatTimestampFormatter.formatDrawnStamp(
                                news.timeMillis, now))
                        .withTip(StatCollector.translateToLocal(
                                "gui.losttales.inbox.open_quest")));
            }
        }
        return rows;
    }

    /**
     * Whether a line stands in the inbox: still filed in its conversation,
     * its message still kept, and said by somebody. The server's own lines
     * that name the player, a fellowship invitation's among them, are not
     * addressed to them by a person.
     */
    private static boolean listed(int chatLineId) {
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(
                ClientChatMessageIds.messageIdOf(chatLineId));
        return ClientChatChannelViews.tabOf(chatLineId) != null
                && remembered != null
                && !LostTalesChatMessagePacket.isSystemSender(
                        remembered.packet.getSenderId());
    }

    /** The newest of {@code lines}, at most as many as a section lists, the newest first. */
    private static List<Integer> newest(List<Integer> lines) {
        List<Integer> newest = new ArrayList<Integer>(Math.min(MAX_ROWS,
                lines.size()));
        for (int index = lines.size() - 1; index >= 0
                && newest.size() < MAX_ROWS; index--) {
            newest.add(lines.get(index));
        }
        return newest;
    }

    private static boolean holds(String label, String words) {
        return words.length() == 0
                || label.toLowerCase(Locale.ROOT).contains(words);
    }

    private static String plain(String text) {
        return text == null ? ""
                : LostTalesChatVisualStyle.removeColorCodes(text).trim();
    }
}
