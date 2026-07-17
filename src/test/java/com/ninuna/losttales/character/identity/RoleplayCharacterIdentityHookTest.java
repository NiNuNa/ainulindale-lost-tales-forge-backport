package com.ninuna.losttales.character.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

public final class RoleplayCharacterIdentityHookTest {
    @Test
    public void deathComponentReplacesVictimAndPlayerKillerNames() {
        ChatComponentText victim = new ChatComponentText("Player284");
        ChatComponentText killer = new ChatComponentText("Player901");
        killer.appendSibling(new ChatComponentText("!"));
        ChatComponentTranslation source = new ChatComponentTranslation(
                "death.attack.player", victim, killer);
        Map<String, String> replacements =
                new LinkedHashMap<String, String>();
        replacements.put("Player284", "Dorwinion Court");
        replacements.put("Player901", "Nimrodel");

        IChatComponent result =
                RoleplayCharacterIdentityHook.replaceAccountNames(
                        source, replacements);

        assertNotSame(source, result);
        Object[] arguments =
                ((ChatComponentTranslation)result).getFormatArgs();
        assertEquals("Dorwinion Court",
                ((ChatComponentText)arguments[0])
                        .getChatComponentText_TextValue());
        ChatComponentText replacedKiller =
                (ChatComponentText)arguments[1];
        assertEquals("Nimrodel",
                replacedKiller.getChatComponentText_TextValue());
        assertEquals("!", ((ChatComponentText)replacedKiller
                .getSiblings().get(0))
                .getChatComponentText_TextValue());
    }

    @Test
    public void unrelatedTextIsPreserved() {
        ChatComponentText source =
                new ChatComponentText("Player2840");
        Map<String, String> replacements =
                new LinkedHashMap<String, String>();
        replacements.put("Player284", "Dorwinion Court");

        IChatComponent result =
                RoleplayCharacterIdentityHook.replaceAccountNames(
                        source, replacements);

        assertEquals("Player2840",
                ((ChatComponentText)result)
                        .getChatComponentText_TextValue());
    }
}
