package com.ninuna.losttales.client.chat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.gui.GuiChat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * The chat key opens the Lost Tales chat only when vanilla's chat screen
 * gives up the text it opened with. The test classpath carries MCP names,
 * so this covers the development name; the release name is pinned
 * against the mapping tables by VanillaMemberNamesTest.
 */
public final class LostTalesChatClientHandlerTest {

    @Test
    public void theOpeningTextResolvesAsGuiChatsOwnStringField() {
        Field field = LostTalesChatClientHandler.resolveDefaultInputField();
        assertNotNull("GuiChat's opening text must resolve", field);
        assertEquals(GuiChat.class, field.getDeclaringClass());
        assertEquals(String.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
    }

    @Test
    public void theResolvedFieldHoldsTheTextTheScreenOpenedWith()
            throws IllegalAccessException {
        Field field = LostTalesChatClientHandler.resolveDefaultInputField();
        assertNotNull(field);
        assertEquals("/", field.get(new GuiChat("/")));
        assertEquals("", field.get(new GuiChat()));
    }
}
