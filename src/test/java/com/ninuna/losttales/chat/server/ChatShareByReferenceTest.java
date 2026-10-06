package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A shared item is the stack in the slot the sender's client named. The
 * token's name is the label the sender's own game showed, in its own
 * language; the server never compares it with a name in its language, so
 * a player whose game reads German shares as surely as one in English.
 */
public final class ChatShareByReferenceTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NAME_KEY = "item.losttalesShareTestStick.name";

    @After
    public void readInEnglishAgain() {
        inject(NAME_KEY + "=Stick\n");
    }

    @Test
    public void anItemIsSharedByItsSlotWhateverItsLabelSays() {
        inject(NAME_KEY + "=Stock\n");
        Item stick = new Item().setUnlocalizedName("losttalesShareTestStick");
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack stack = new ItemStack(stick, 3);
        inventory.setInventorySlotContents(3, stack);
        assertEquals("Stock", stack.getDisplayName());

        ChatShareTokenParser.Token typed = ChatShareTokenParser.parse(
                "schau [i:Stock]").get(0);
        assertEquals(ChatShareKind.ITEM, typed.kind);
        // The label never decides: the slot the client named does.
        assertSame(stack, LostTalesChatService.itemInSlot(inventory,
                ChatShareReference.item(3)));
        assertNull("an empty slot shares nothing",
                LostTalesChatService.itemInSlot(inventory,
                        ChatShareReference.item(4)));
        assertNull("a token the client could not resolve shares nothing",
                LostTalesChatService.itemInSlot(inventory,
                        ChatShareReference.unresolved(ChatShareKind.ITEM)));
        assertNull("a marker's reference names no slot",
                LostTalesChatService.itemInSlot(inventory,
                        ChatShareReference.marker("losttales:bree")));
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
