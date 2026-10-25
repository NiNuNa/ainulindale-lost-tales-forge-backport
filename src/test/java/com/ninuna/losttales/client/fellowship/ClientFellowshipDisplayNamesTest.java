package com.ninuna.losttales.client.fellowship;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The invite field's offers and the invitations on the fellowship page
 * name a character's account after it only while the reader's Show
 * Accounts is on (FEL-31); the server always sends it.
 */
public final class ClientFellowshipDisplayNamesTest {

    @Test
    public void theAccountStandsAfterTheNameWhileShown() {
        assertEquals("Aldric (Steve)",
                ClientFellowshipDisplayNames.named("Aldric", "Steve", true));
        assertEquals("Aldric", ClientFellowshipDisplayNames.named("Aldric", "Steve", false));
    }

    @Test
    public void anAccountUnknownOrTheCharactersOwnNameIsLeftOut() {
        assertEquals("Aldric", ClientFellowshipDisplayNames.named("Aldric", "", true));
        assertEquals("Steve", ClientFellowshipDisplayNames.named("Steve", "steve", true));
        assertEquals("", ClientFellowshipDisplayNames.named(null, "Steve", false));
    }
}
