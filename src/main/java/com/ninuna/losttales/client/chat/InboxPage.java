package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.quest.ClientQuestNews;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.screen.fellowship.FellowshipPage;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The Inbox: what is addressed to the player, one page of it, as
 * Discord's inbox ({@link ChatInbox}). Fellowship invitations first,
 * then mentions, forwards and quest news, each under its heading and the
 * newest first, narrowed by the tool strip's search. A press goes where
 * the row names: a line in its conversation, an invitation on the
 * Fellowships page, a quest in the journal. Every conversation's strip
 * opens it, and the button there counts what waits.
 */
public final class InboxPage extends PageContent {
    public static final String PAGE_ID = "inbox";
    public static final ItemStack ICON = new ItemStack(Blocks.chest);

    private final PageRows list;
    /** What the tool strip's search holds; empty for everything. */
    private String query = "";

    public InboxPage() {
        this.list = new PageRows(new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part,
                             boolean back, LostTalesUiHitBox row) {
                goTo(entry.id);
            }
        });
    }

    /** Goes where a row names. */
    private static void goTo(String id) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null || id == null) {
            return;
        }
        if (id.startsWith(ChatInbox.LINE)) {
            // The inbox numbers its rows itself: a line's is its line id.
            ChatScreenPart chat = screen.part(ChatScreenPart.class);
            if (chat != null) {
                chat.jumpFromInbox(Integer.parseInt(
                        id.substring(ChatInbox.LINE.length())));
            }
        } else if (id.startsWith(ChatInbox.INVITATION)) {
            screen.showOnPage(FellowshipPage.PAGE_ID,
                    id.substring(ChatInbox.INVITATION.length()), null);
        } else if (id.startsWith(ChatInbox.QUEST)) {
            screen.showOnPage(QuestJournalPage.PAGE_ID,
                    id.substring(ChatInbox.QUEST.length()), null);
        }
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        List<MenuWindow.Entry> rows = ChatInbox.rows(this.query);
        this.list.setRows(rows);
        LostTalesUiHitBox column = PageRows.column(box);
        this.list.draw(minecraft, column, clipX + (column.left - box.left),
                clipY + (column.top - box.top), pointerX, pointerY, alpha);
        // Shown on the screen, the quest news has been seen; drawn pinned
        // while playing, it has not been looked at.
        if (WindowScreen.current() != null) {
            ClientQuestNews.markSeen();
        }
    }

    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.list.acts(PageRows.column(box), x, y);
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        return this.list.tipAt(PageRows.column(box), x, y);
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        return this.list.press(PageRows.column(box), x, y, button);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || !PageRows.column(box).contains(x, y)) {
            return false;
        }
        this.list.scroll(lines);
        return true;
    }

    /* ---- The window's strip ---- */

    /** The mention line's coral: what is addressed to the player. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.CORAL);
    }

    /** The keys the Inbox answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.inbox",
                PageKeys.pageKey(PAGE_ID, "go", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    @Override
    public String searchPrompt() {
        return StatCollector.translateToLocal("gui.losttales.inbox.search");
    }

    @Override
    public void search(String words) {
        String next = words == null ? "" : words.trim();
        if (!next.equals(this.query)) {
            this.query = next;
            this.list.toTop();
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1
                : PageRows.found(ChatInbox.rows(this.query));
    }
}
