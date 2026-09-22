package com.ninuna.losttales.command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** An edit names options one at a time and keeps the rest of the entry. */
public final class LostTalesCommandRoleTest {

    @Test
    public void anOptionIsReplacedInPlaceOrAppended() {
        String entry = "moderator=name:Moderator;color:A94B54";
        assertEquals("moderator=name:Staff;color:A94B54",
                LostTalesCommandRole.replaceOption(entry, "NAME", "Staff"));
        assertEquals("moderator=name:Moderator;color:A94B54;rank:5",
                LostTalesCommandRole.replaceOption(entry, "rank", "5"));
        assertEquals("moderator=name:Moderator;color:A94B54;op:1",
                LostTalesCommandRole.replaceOption(entry, "op", "1"));
    }

    @Test
    public void aRepeatableOptionAddsAndAnEmptyOneClearsItsKind() {
        String entry = "moderator=name:Moderator;op:1;faction:GONDOR@gondor.knight";
        assertEquals("moderator=name:Moderator;op:1;faction:GONDOR@gondor.knight;op:2",
                LostTalesCommandRole.replaceOption(entry, "op", "2"));
        assertEquals("moderator=name:Moderator;faction:GONDOR@gondor.knight",
                LostTalesCommandRole.replaceOption(entry, "op", ""));
        assertEquals("moderator=name:Moderator;op:1",
                LostTalesCommandRole.replaceOption(entry, "faction", ""));
        assertEquals("builder=name:Builder",
                LostTalesCommandRole.replaceOption("builder=", "name", "Builder"));
    }
}
