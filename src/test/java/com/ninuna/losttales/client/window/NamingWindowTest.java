package com.ninuna.losttales.client.window;

import net.minecraft.client.gui.GuiTextField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A naming window starts with the name it is given, cut to its length,
 * counts the letters left, and asks its rule whether the trimmed name may
 * be kept.
 */
public final class NamingWindowTest {

    @Before
    public void plainFields() {
        WindowFields.install(new WindowFields.Kit() {
            @Override
            public GuiTextField make(int height, int limit, boolean showsEmoji) {
                GuiTextField field = new GuiTextField(null, 0, 0, 100, 12);
                field.setMaxStringLength(limit);
                return field;
            }

            @Override
            public void draw(GuiTextField field, int alpha) {
            }

            @Override
            public WindowFields.FieldList listFor(GuiTextField field, boolean showsEmoji) {
                return null;
            }
        });
    }

    @After
    public void reset() {
        WindowFields.install(null);
    }

    @Test
    public void theNameStartsCutToItsLengthAndCountsWhatIsLeft() {
        NamingWindow naming = new NamingWindow().ask("Rename View", "The Grey Company",
                8, NamingWindow.ANY, null);
        assertEquals("The Grey", naming.name());
        assertEquals(0, naming.lettersLeft());
        naming.ask("Rename View", " Rangers ", 24, NamingWindow.ANY, null);
        assertEquals("Rangers", naming.name());
        assertEquals(15, naming.lettersLeft());
    }

    @Test
    public void theRuleIsAskedAboutTheTrimmedName() {
        NamingWindow naming = new NamingWindow().ask("Create Fellowship", "  ", 32,
                new NamingWindow.Rule() {
                    @Override
                    public String refusal(String name) {
                        return name.isEmpty() ? "Give the fellowship a name." : "";
                    }
                }, null);
        assertEquals("Give the fellowship a name.", naming.refusal());
        assertEquals("", new NamingWindow().ask("Rename View", "", 24,
                NamingWindow.ANY, null).refusal());
    }

    @Test
    public void whatItIsAboutIsKept() {
        Object view = new Object();
        NamingWindow naming = new NamingWindow().ask("Rename View", "", 24,
                NamingWindow.ANY, null).about(view);
        assertEquals(view, naming.about());
    }
}
