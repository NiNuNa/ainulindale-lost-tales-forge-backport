package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.PageCategory;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.gui.screen.fellowship.FellowshipPage;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Each page's Inbox lists its own kind: a conversation's, the journal's, the Fellowships page's. */
public final class ChatInboxTest {

    @Test
    public void eachPageListsItsOwnKind() {
        register(QuestJournalPage.PAGE_ID, PageCategory.QUEST_JOURNAL);
        register(FellowshipPage.PAGE_ID, PageCategory.FELLOWSHIPS);
        assertEquals(ChatInbox.Kind.CONVERSATIONS,
                ChatInbox.kindOf(ConversationPage.of(ChatChannel.GLOBAL)));
        assertEquals(ChatInbox.Kind.CONVERSATIONS, ChatInbox.kindOf(null));
        assertEquals(ChatInbox.Kind.QUEST_NEWS,
                ChatInbox.kindOf(WindowPages.tab(QuestJournalPage.PAGE_ID)));
        assertEquals(ChatInbox.Kind.INVITATIONS,
                ChatInbox.kindOf(WindowPages.tab(FellowshipPage.PAGE_ID)));
    }

    private static void register(String id, PageCategory category) {
        if (WindowPages.byId(id) == null) {
            WindowPages.register(id, "gui.test.inbox." + id, new ItemStack(Items.paper),
                    null, category, new WindowPages.Factory() {
                        @Override
                        public PageContent create() {
                            return new EmptyPage();
                        }
                    });
        }
    }

    private static final class EmptyPage extends PageContent {
        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
