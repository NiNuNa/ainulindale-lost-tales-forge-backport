package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.TwoWindowLayout;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Each copy of a page keeps its own search words in its well until they
 * are cleared, and finds them again as the field comes back to it; the
 * field types for one copy at a time, and leaving a well keeps the words
 * (WIN-25). A copy opening anew starts with none, and leaving the world
 * forgets them all.
 */
public final class WindowSearchTest {
    private static final String PAGE = "search_copies_page";

    @BeforeClass
    public static void registerPage() {
        if (WindowPages.byId(PAGE) == null) {
            WindowPages.register(PAGE, "gui.test.search.page",
                    new ItemStack(Items.book), null, PageCategory.QUEST_JOURNAL,
                    new WindowPages.Factory() {
                        @Override
                        public PageContent create() {
                            return new EmptyPage();
                        }
                    });
        }
    }

    @Before
    public void reset() {
        WindowSearch.forgetAll();
        TwoWindowLayout.reset();
        WindowView.clear();
    }

    @After
    public void cleanUp() {
        WindowSearch.forgetAll();
        WindowView.clear();
        TwoWindowLayout.reset();
    }

    @Test
    public void eachCopyKeepsItsOwnWords() {
        WindowPage first = WindowLayout.openCopy(WindowPages.tab(PAGE), null);
        WindowPage second = WindowLayout.duplicate(first);
        assertNotNull(second);
        WindowSearch.open("w1", first);
        WindowSearch.setQuery("ring");
        assertEquals("ring", WindowSearch.query());
        WindowSearch.open("w2", second);
        assertTrue(WindowSearch.typesFor(second));
        assertFalse(WindowSearch.typesFor(first));
        assertEquals("the other copy's well starts empty", "",
                WindowSearch.query());
        WindowSearch.setQuery("orc");
        assertEquals("ring", WindowSearch.wordsOf(first));
        assertEquals("orc", WindowSearch.wordsOf(second));
    }

    @Test
    public void leavingTheWellKeepsTheWordsUntilTheyAreCleared() {
        WindowPage copy = WindowLayout.openCopy(WindowPages.tab(PAGE), null);
        WindowSearch.open("w1", copy);
        WindowSearch.setQuery("ring");
        int before = WindowSearch.generation(copy.id());
        WindowSearch.release();
        assertFalse(WindowSearch.isOpen());
        assertEquals("the words stay with the copy", "ring",
                WindowSearch.wordsOf(copy));
        WindowSearch.open("w1", copy);
        assertEquals("and come back with the field", "ring",
                WindowSearch.query());
        WindowSearch.setQuery("");
        assertEquals("", WindowSearch.wordsOf(copy));
        assertEquals("deleting the words is no clearing", before,
                WindowSearch.generation(copy.id()));
        WindowSearch.setQuery("orc");
        WindowSearch.clear(copy.id());
        assertEquals("", WindowSearch.wordsOf(copy));
        assertNotEquals("what the words found goes with them", before,
                WindowSearch.generation(copy.id()));
    }

    @Test
    public void theFieldLeavesAWellThatIsNotTypedIn() {
        WindowPage copy = WindowLayout.openCopy(WindowPages.tab(PAGE), null);
        WindowSearch.open("w1", copy);
        WindowSearch.releaseUnless("w1");
        assertTrue(WindowSearch.isOpenOn("w1"));
        WindowSearch.releaseUnless("w2");
        assertFalse(WindowSearch.isOpen());
        assertEquals("", WindowSearch.query());
    }

    @Test
    public void theWellHoldsTheWordsOfTheCopyInFrontOfItsWindow() {
        WindowPage first = WindowLayout.openCopy(WindowPages.tab(PAGE), null);
        // The page's first window opened locked, as a category's first does.
        Unlocking.all();
        Window window = WindowLayout.windowOf(first);
        WindowSearch.open(window.getId(), first);
        WindowSearch.setQuery("ring");
        assertSame(first, WindowSearch.frontOf(window.getId()));
        assertEquals("ring", WindowSearch.wordsIn(window.getId()));
        WindowPage second = WindowLayout.duplicate(first);
        assertSame("a copy coming forward has its own well",
                window, WindowLayout.windowOf(second));
        assertEquals("", WindowSearch.wordsIn(window.getId()));
        WindowLayout.setActiveTab(first);
        assertEquals("going back finds the words again", "ring",
                WindowSearch.wordsIn(window.getId()));
    }

    @Test
    public void aCopyOpeningAnewAndALeftWorldStartWithNone() {
        WindowPage first = WindowLayout.openCopy(WindowPages.tab(PAGE), null);
        WindowPage second = WindowLayout.duplicate(first);
        WindowSearch.open("w1", second);
        WindowSearch.setQuery("orc");
        Unlocking.all();
        assertTrue(WindowLayout.close(second));
        WindowPage again = WindowLayout.duplicate(first);
        assertEquals("the same number opens again", second, again);
        assertEquals("with none of the words before", "",
                WindowSearch.wordsOf(again));

        WindowSearch.open("w1", first);
        WindowSearch.setQuery("ring");
        int epoch = WindowSearch.epoch();
        WindowSearch.forgetAll();
        assertEquals("", WindowSearch.wordsOf(first));
        assertFalse(WindowSearch.isOpen());
        assertNotEquals(epoch, WindowSearch.epoch());
    }

    private static final class EmptyPage extends PageContent {
        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
