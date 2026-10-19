package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Show Accounts is each player's own setting (CHAR-26): the account
 * behind a character trails its name, and while the setting is off the
 * run takes no room in any walk over the line; an account's own identity
 * never names itself twice.
 */
public final class ChatAccountAsideTest {

    @After
    public void showAccountsAgain() {
        LostTalesConfig.chatShowAccounts = true;
    }

    @Test
    public void aCharacterNamesItsAccountAfterItsName() {
        assertEquals(" (Steve)", ChatAccountAside.suffix("Aldric", "Steve", true));
        assertEquals(" (Steve)", ChatAccountAside.suffix(" Aldric ", " Steve ", true));
    }

    @Test
    public void anAccountOrAMissingHalfNamesNothing() {
        assertEquals("", ChatAccountAside.suffix("Steve", "Steve", false));
        assertEquals("", ChatAccountAside.suffix("Aldric", "Steve", false));
        assertEquals("", ChatAccountAside.suffix("steve", "Steve", true));
        assertEquals("", ChatAccountAside.suffix("Aldric", "", true));
        assertEquals("", ChatAccountAside.suffix("", "Steve", true));
        assertEquals("", ChatAccountAside.suffix(null, null, true));
    }

    @Test
    public void theAccountRunIsHiddenOnlyWhileTheSettingIsOff() {
        ChatComponentText run = ChatAccountMarker.apply(new ChatComponentText(" (Steve)"));
        assertTrue(ChatAccountMarker.isMarker(run));
        LostTalesConfig.chatShowAccounts = true;
        assertFalse(ChatPrefixMarker.isHidden(run, true));
        assertFalse(ChatPrefixMarker.isHidden(run, false));
        LostTalesConfig.chatShowAccounts = false;
        assertTrue(ChatPrefixMarker.isHidden(run, true));
        assertTrue(ChatPrefixMarker.isHidden(run, false));
        assertFalse("another run stays",
                ChatPrefixMarker.isHidden(new ChatComponentText("Aldric"), true));
    }
}
