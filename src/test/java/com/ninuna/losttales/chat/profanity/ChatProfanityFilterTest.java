package com.ninuna.losttales.chat.profanity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The filter replaces listed words whole, catches stretched letters and
 * the common endings, keeps the word's case, and leaves everything that
 * is not a word alone.
 */
public final class ChatProfanityFilterTest {

    private static final ChatProfanityWords WORDS = ChatProfanityWords.parse(
            new String[] {"fuck=flip", "shit=poop", "ass=bum", "bitch=witch",
                    "piss=whizz", "pussy=kitty", "spic=spice", "damn=dang"},
            ChatProfanityWords.MAX_WORDS, null);

    private static String silly(String text) {
        return ChatProfanityFilter.filter(text, ChatProfanityMode.SILLY, WORDS);
    }

    private static String stars(String text) {
        return ChatProfanityFilter.filter(text, ChatProfanityMode.STARS, WORDS);
    }

    @Test
    public void listedWordsReadAsTheirSillyStandIns() {
        assertEquals("flip this poop", silly("fuck this shit"));
        assertEquals("what a bum", silly("what a ass"));
    }

    @Test
    public void wordsAreMatchedWholeOnly() {
        assertEquals("Scunthorpe assassin bass class hello",
                silly("Scunthorpe assassin bass class hello"));
        assertEquals("as is a word", silly("as is a word"));
        assertEquals("passion", silly("passion"));
    }

    @Test
    public void stretchedLettersAreCaught() {
        assertEquals("flip", silly("fuuuuck"));
        assertEquals("poop", silly("shiiit"));
        assertEquals("bum", silly("asssss"));
        assertEquals("flipping", silly("fuckinggg"));
    }

    @Test
    public void theCommonEndingsAreCarriedOverInEnglishSpelling() {
        assertEquals("flipping flipped flipper flippers flippin",
                silly("fucking fucked fucker fuckers fuckin"));
        assertEquals("poops poopy poophead", silly("shits shitty shithead"));
        assertEquals("witches bumhole bums", silly("bitches asshole asses"));
        assertEquals("whizzing whizzed", silly("pissing pissed"));
        assertEquals("kitties kittier kittiest", silly("pussies pussier pussiest"));
        assertEquals("spiced spicing", silly("spiced spicing"));
        assertEquals("flipface flipfaces", silly("fuckface fuckfaces"));
    }

    @Test
    public void theWordsCaseIsKept() {
        assertEquals("Flip", silly("Fuck"));
        assertEquals("FLIP", silly("FUCK"));
        assertEquals("FLIPPING", silly("FUCKING"));
        assertEquals("flip", silly("fUcK"));
        assertEquals("Poop happens.", silly("Shit happens."));
    }

    @Test
    public void starsKeepTheFirstLetterAndTheLength() {
        assertEquals("f*** this s***", stars("fuck this shit"));
        assertEquals("F******", stars("Fucking"));
        assertEquals("a*****", stars("asssss"));
        assertEquals("Scunthorpe", stars("Scunthorpe"));
    }

    @Test
    public void offAndAnEmptyListLeaveTheTextAlone() {
        String text = "fuck this shit";
        assertSame(text, ChatProfanityFilter.filter(text, ChatProfanityMode.OFF, WORDS));
        assertSame(text, ChatProfanityFilter.filter(text, ChatProfanityMode.SILLY,
                ChatProfanityWords.NONE));
        assertSame(text, ChatProfanityFilter.filter(text, ChatProfanityMode.SILLY, null));
        assertEquals("", ChatProfanityFilter.filter(null, ChatProfanityMode.SILLY, WORDS));
    }

    @Test
    public void punctuationDigitsAndApostrophesBoundWords() {
        assertEquals("flip, poop! bum? flippin' poop123",
                silly("fuck, shit! ass? fuckin' shit123"));
        assertEquals("f*ck stays", silly("f*ck stays"));
    }

    @Test
    public void aMessageKeepsItsTokensEmojisLinksMentionsChannelsAndCode() {
        String message = "shit [i:Iron Sword] :smile: https://example.com/fuck "
                + "@fuck #fuck `fuck` fuck";
        assertEquals("poop [i:Iron Sword] :smile: https://example.com/fuck "
                + "@fuck #fuck `fuck` flip",
                ChatProfanityFilter.filterMessage(message, ChatProfanityMode.SILLY, WORDS));
        assertEquals("a lone ` tick flip",
                ChatProfanityFilter.filterMessage("a lone ` tick fuck",
                        ChatProfanityMode.SILLY, WORDS));
        assertEquals("flip", ChatProfanityFilter.filterMessage("fuck",
                ChatProfanityMode.SILLY, WORDS));
    }

    @Test
    public void aListedWordIsFoundWhereverItStands() {
        assertTrue(ChatProfanityFilter.hasListedWord("Aldric the Fucker", WORDS));
        assertTrue(ChatProfanityFilter.hasListedWord("SHITTY", WORDS));
        assertFalse(ChatProfanityFilter.hasListedWord("Aldric of Bree", WORDS));
        assertFalse(ChatProfanityFilter.hasListedWord("", WORDS));
        assertFalse(ChatProfanityFilter.hasListedWord(null, WORDS));
        assertFalse(ChatProfanityFilter.hasListedWord("fuck", ChatProfanityWords.NONE));
    }

    @Test
    public void aLaterListsStandInWinsForAWordBothName() {
        List<String> warnings = new ArrayList<String>();
        ChatProfanityWords server = ChatProfanityWords.parse(
                new String[] {"fuck=fudge", "bugger=badger"},
                ChatProfanityWords.MAX_WORDS, warnings);
        ChatProfanityWords merged = WORDS.plus(server);
        assertTrue(warnings.isEmpty());
        assertEquals("fudge the badger and the poop",
                ChatProfanityFilter.filter("fuck the bugger and the shit",
                        ChatProfanityMode.SILLY, merged));
        assertEquals(WORDS.size() + 1, merged.size());
    }

    @Test
    public void theEndingSpellingRulesReadAsEnglish() {
        assertEquals("flipping", ChatProfanityFilter.withEnding("flip", "ing"));
        assertEquals("pooping", ChatProfanityFilter.withEnding("poop", "ing"));
        assertEquals("witches", ChatProfanityFilter.withEnding("witch", "s"));
        assertEquals("bums", ChatProfanityFilter.withEnding("bum", "es"));
        assertEquals("kitties", ChatProfanityFilter.withEnding("kitty", "ies"));
        assertEquals("spiced", ChatProfanityFilter.withEnding("spice", "ed"));
        assertEquals("cruddy", ChatProfanityFilter.withEnding("crud", "y"));
        assertEquals("duckhead", ChatProfanityFilter.withEnding("duck", "head"));
        assertEquals("rascals", ChatProfanityFilter.withEnding("rascal", "s"));
        assertEquals("rooster", ChatProfanityFilter.withEnding("rooster", ""));
    }
}
