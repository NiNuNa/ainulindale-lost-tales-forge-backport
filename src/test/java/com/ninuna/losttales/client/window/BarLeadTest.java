package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import net.minecraft.client.Minecraft;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The tab and identity buttons every bar starts with: the tab button
 * holds the tab's icon and whole name while there is room, gives its name
 * up pixel by pixel down to the icon alone, and keeps a name whole where
 * it has no icon; the identity button stands a button gap after it.
 */
public final class BarLeadTest {
    /** Six pixels a letter: enough to lay a bar out without a font. */
    private static final WindowBar.Measure SIX = new WindowBar.Measure() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }
    };
    private static final int WIDE = LostTalesUiFramedButton.WIDE_INSET;

    private static int wholeFrame(String name) {
        return 2 * WIDE + TabIcons.SIZE + TabIcons.GAP + name.length() * 6 - 1;
    }

    @Test
    public void theWholePairHoldsTheIconTheNameAndTheIdentityButton() {
        Tab journal = new Tab("Journal", true);
        assertEquals(wholeFrame("Journal") + BarLead.BUTTON_GAP
                + BarLead.IDENTITY_SIZE, BarLead.wholeWidth(journal, SIX));
        BarLead.Fit fit = BarLead.fit(journal, 3,
                BarLead.wholeWidth(journal, SIX), SIX);
        assertEquals(3, fit.frameLeft);
        assertEquals(3 + wholeFrame("Journal"), fit.frameRight);
        assertEquals("Journal", fit.label);
        assertEquals(fit.labelWidth, fit.labelRoom);
        assertEquals(3 + WIDE, fit.iconLeft);
        assertEquals(fit.iconLeft + TabIcons.SIZE + TabIcons.GAP,
                fit.labelLeft);
        assertEquals(fit.frameRight + BarLead.BUTTON_GAP, fit.identityLeft);
        assertEquals(fit.identityLeft + BarLead.IDENTITY_SIZE, fit.right);
    }

    @Test
    public void eachPixelShortCutsTheNameByOneDownToTheIconAlone() {
        Tab journal = new Tab("Journal", true);
        int whole = BarLead.wholeWidth(journal, SIX);
        BarLead.Fit cut = BarLead.fit(journal, 0, whole - 5, SIX);
        assertEquals(wholeFrame("Journal") - 5, cut.frameRight);
        assertEquals(cut.frameRight - 2 * WIDE - TabIcons.SIZE - TabIcons.GAP,
                cut.labelRoom);
        BarLead.Fit least = BarLead.fit(journal, 0, 0, SIX);
        assertEquals(2 * WIDE + TabIcons.SIZE, least.frameRight);
        assertEquals(0, least.labelRoom);
        assertEquals(BarLead.leastWidth(journal, SIX), least.right);
    }

    @Test
    public void aTabWithoutAnIconKeepsItsNameWhole() {
        Tab bare = new Tab("Server Log", false);
        assertEquals(BarLead.wholeWidth(bare, SIX),
                BarLead.leastWidth(bare, SIX));
        BarLead.Fit fit = BarLead.fit(bare, 0, 10, SIX);
        assertEquals(-1, fit.iconLeft);
        assertEquals(fit.labelWidth, fit.labelRoom);
        assertEquals(WIDE, fit.labelLeft);
    }

    @Test
    public void eachButtonAnswersOnItsFrameAlone() {
        Tab journal = new Tab("Journal", true);
        BarLead.Fit fit = BarLead.fit(journal, 0, 1000, SIX);
        int top = 10;
        assertEquals(true, fit.onTab(fit.frameLeft, top, top));
        assertEquals(false, fit.onTab(fit.frameRight, top, top));
        assertEquals(false, fit.onTab(fit.frameLeft, top - 1, top));
        assertEquals(true, fit.onIdentity(fit.identityLeft, top, top));
        assertEquals(false, fit.onIdentity(fit.frameRight, top, top));
        assertEquals(false, fit.onIdentity(fit.right, top, top));
    }

    /** A tab with a name, with or without an icon. */
    static final class Tab extends WindowPage {
        private final String name;
        private final boolean icon;

        Tab(String name, boolean icon) {
            this.name = name;
            this.icon = icon;
        }

        @Override
        public String id() {
            return "test:" + this.name;
        }

        @Override
        public String title() {
            return this.name;
        }

        @Override
        public int tone() {
            return 0;
        }

        @Override
        public boolean hasIcon() {
            return this.icon;
        }

        @Override
        public void drawIcon(Minecraft minecraft, float x, float y, int alpha,
                             TabMark mark) {
        }

        @Override
        public boolean isKeptInLayout() {
            return false;
        }

        @Override
        public PageCategory category() {
            return PageCategory.CHANNELS;
        }
    }
}
