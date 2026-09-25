package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * A channel icon is written as an emoji's name or an item's id, and
 * carried as the same text; text that names nothing is refused rather
 * than guessed at.
 */
public final class ChatChannelIconSpecTest {

    @Test
    public void anEmojiIsNamedWithOrWithoutItsPrefix() {
        ChatChannelIconSpec prefixed = ChatChannelIconSpec.parse("emoji:joy");
        ChatChannelIconSpec bare = ChatChannelIconSpec.parse(" Joy ");
        assertNotNull(prefixed);
        assertEquals(ChatChannelIconSpec.Kind.EMOJI, prefixed.getKind());
        assertEquals("joy", prefixed.getName());
        assertEquals(0, prefixed.getMeta());
        assertEquals(prefixed, bare);
        assertEquals("emoji:joy", bare.toText());
    }

    @Test
    public void anItemKeepsItsIdAndDamage() {
        ChatChannelIconSpec sword =
                ChatChannelIconSpec.parse("item:minecraft:iron_sword");
        assertNotNull(sword);
        assertEquals(ChatChannelIconSpec.Kind.ITEM, sword.getKind());
        assertEquals("minecraft:iron_sword", sword.getName());
        assertEquals(0, sword.getMeta());
        assertEquals("item:minecraft:iron_sword", sword.toText());

        ChatChannelIconSpec wool = ChatChannelIconSpec.parse("Item:minecraft:wool@14");
        assertNotNull(wool);
        assertEquals("minecraft:wool", wool.getName());
        assertEquals(14, wool.getMeta());
        assertEquals("item:minecraft:wool@14", wool.toText());
        // A modded id keeps its case: registries tell ids apart by it.
        assertEquals("lotr:item.gondorSword",
                ChatChannelIconSpec.parse("item:lotr:item.gondorSword").getName());
    }

    @Test
    public void aChannelIsNamedByItsCodeName() {
        ChatChannelIconSpec operator = ChatChannelIconSpec.parse(" Channel:Operator ");
        assertNotNull(operator);
        assertEquals(ChatChannelIconSpec.Kind.CHANNEL, operator.getKind());
        assertEquals("operator", operator.getName());
        assertEquals("channel:operator", operator.toText());
        assertEquals(null, ChatChannelIconSpec.parse("channel:"));
        assertEquals(null, ChatChannelIconSpec.parse("channel:out of character"));
    }

    @Test
    public void theTextParsesBackToItself() {
        for (String text : new String[] {"emoji:slight_smile",
                "item:minecraft:diamond", "item:minecraft:wool@7",
                "channel:client_console"}) {
            ChatChannelIconSpec icon = ChatChannelIconSpec.parse(text);
            assertNotNull(text, icon);
            assertEquals(text, icon.toText());
            assertEquals(icon, ChatChannelIconSpec.parse(icon.toText()));
            assertEquals(icon.hashCode(),
                    ChatChannelIconSpec.parse(icon.toText()).hashCode());
        }
    }

    @Test
    public void textThatNamesNothingIsRefused() {
        assertNull(ChatChannelIconSpec.parse(null));
        assertNull(ChatChannelIconSpec.parse(""));
        assertNull(ChatChannelIconSpec.parse("   "));
        assertNull(ChatChannelIconSpec.parse("item:"));
        assertNull(ChatChannelIconSpec.parse("item::sword"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:wool@"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:wool@-1"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:wool@99999"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:wool@red"));
        assertNull(ChatChannelIconSpec.parse("item:minecraft:iron sword"));
        assertNull(ChatChannelIconSpec.parse("emoji:"));
        assertNull(ChatChannelIconSpec.parse("emoji:slight smile"));
        assertNull(ChatChannelIconSpec.parse(":joy:"));
        StringBuilder long_ = new StringBuilder("item:minecraft:");
        while (long_.length() <= ChatChannelIconSpec.MAX_TEXT_LENGTH) {
            long_.append('x');
        }
        assertNull(ChatChannelIconSpec.parse(long_.toString()));
    }
}
