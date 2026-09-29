package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiscordMessageSanitizerTest {

    @Test
    public void discordMarkupIsSpelledOutAndLinesAreFlattened() {
        Map<String, String> names = new HashMap<String, String>();
        names.put("1234", "Frodo");
        assertEquals("hey @Frodo and @user,\nsee #channel :smile: @role",
                DiscordMessageSanitizer.inbound(
                        "hey <@1234> and <@!99>,\nsee <#55> <a:smile:7> <@&8>",
                        names));
        assertEquals("no codes here",
                DiscordMessageSanitizer.inbound("no §ccodes here",
                        Collections.<String, String>emptyMap()));
        assertEquals("", DiscordMessageSanitizer.inbound("  \n\t ", null));
        assertEquals("", DiscordMessageSanitizer.inbound(null, null));
    }

    /** Discord's block markup folds into the inline marks the chat reads. */
    @Test
    public void discordBlockMarkupFoldsIntoTheSharedInlineMarks() {
        assertEquals("**bold** *it* __u__ ~~s~~ ||sp|| `c`",
                DiscordMessageSanitizer.normalizeMarkdown(
                        "**bold** *it* __u__ ~~s~~ ||sp|| `c`"));
        assertEquals("*it* and snake_case_name and a_b",
                DiscordMessageSanitizer.normalizeMarkdown(
                        "_it_ and snake_case_name and a_b"));
        assertEquals("run `System.out.println('x');` now",
                DiscordMessageSanitizer.normalizeMarkdown(
                        "run ```java\nSystem.out.println(`x`);\n``` now"));
        assertEquals("Title\nquoted\nsmall\nmore",
                DiscordMessageSanitizer.normalizeMarkdown(
                        "# Title\n> quoted\n-# small\n>>> more"));
        assertEquals("", DiscordMessageSanitizer.normalizeMarkdown("``````"));
        assertEquals("", DiscordMessageSanitizer.normalizeMarkdown(null));
        // Through the whole inbound path the line is one the chat
        // accepts, Discord's lines its paragraphs.
        assertEquals("Title\nquoted\n`code`\n*it*",
                DiscordMessageSanitizer.inbound(
                        "# Title\n> quoted\n```\ncode\n```\n_it_", null));
    }

    @Test
    public void longMessagesAreCutToTheChatLimit() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 300; index++) {
            text.append("word ");
        }
        String cut = DiscordMessageSanitizer.inbound(text.toString(), null);
        assertTrue(cut.length() <= ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(cut.endsWith("..."));
        assertTrue(ChatMessageValidator.isValid(cut));
    }

    @Test
    public void unicodeEmojiBecomeCanonicalShortcodes() {
        assertEquals("hi :flushed: there",
                DiscordMessageSanitizer.inbound(
                        "hi 😳 there", null));
        // Adjacent emoji, and the heart with and without its selector.
        assertEquals(":joy::slight_smile:",
                DiscordMessageSanitizer.inbound(
                        "😂🙂", null));
        assertEquals(":heart: :heart:", DiscordMessageSanitizer.inbound(
                "❤️ ❤", null));
    }

    @Test
    public void aliasesResolveAndUnknownEmojiKeepTheirNames() {
        // A literal alias shortcode, and a custom emoji named by one.
        assertEquals("well :flushed: then",
                DiscordMessageSanitizer.inbound(
                        "well :flushed_face: then", null));
        assertEquals(":laughing:", DiscordMessageSanitizer.inbound(
                "<:Satisfied:12345>", null));
        // An emoji the registry does not carry reads as its Discord name,
        // never as broken glyphs: a ZWJ sequence whole, even where its
        // base is known, so it never becomes the wrong emoji, and a flag
        // as one name.
        assertEquals("look :robot: here", DiscordMessageSanitizer.inbound(
                "look 🤖 here", null));
        assertEquals("so :face_with_spiral_eyes: dizzy",
                DiscordMessageSanitizer.inbound("so 😵‍💫 dizzy", null));
        assertEquals("from :flag_de:", DiscordMessageSanitizer.inbound(
                "from 🇩🇪", null));
        // A skin tone goes with the emoji the registry has.
        assertEquals(":index_pointing_at_the_viewer:",
                DiscordMessageSanitizer.inbound("🫵🏽", null));
        // Signs the chat's font draws stay as they are.
        assertEquals("© 2026", DiscordMessageSanitizer.inbound("© 2026", null));
        assertEquals(":pepe:", DiscordMessageSanitizer.inbound("<:pepe:12345>", null));
    }

    @Test
    public void outboundTurnsCanonicalShortcodesIntoUnicode() {
        assertEquals("hi 😳 there 😂",
                DiscordMessageSanitizer.outbound("hi :flushed: there :joy:"));
        // The mod's own sprites and everything unknown stay as typed.
        assertEquals("a :discord: b :console: c :nope: :sm",
                DiscordMessageSanitizer.outbound(
                        "a :discord: b :console: c :nope: :sm"));
        assertEquals("", DiscordMessageSanitizer.outbound(null));
        assertEquals("plain", DiscordMessageSanitizer.outbound("plain"));
    }

    /**
     * What Discord would draw and the game does not is broken by an
     * invisible space, so a post reads on Discord as its line read in the
     * game and no link hides where it leads; the marks both sides read
     * alike cross unchanged.
     */
    @Test
    public void markupOnlyDiscordDrawsIsBroken() {
        char b = DiscordMentions.BREAK;
        assertEquals("[free gift]" + b + "(https://evil.example/login)",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup(
                        "[free gift](https://evil.example/login)"));
        assertEquals("a [b [c]]" + b + "(<https://x.example>) d",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup(
                        "a [b [c]](<https://x.example>) d"));
        assertEquals("even in code: `[a]" + b + "(b)`",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup("even in code: `[a](b)`"));
        // Headings, subtext and quotes at a line's start.
        assertEquals(b + "# Title", DiscordMessageSanitizer.breakDiscordOnlyMarkup("# Title"));
        assertEquals(b + "## Two", DiscordMessageSanitizer.breakDiscordOnlyMarkup("## Two"));
        assertEquals(b + "### Three",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup("### Three"));
        assertEquals(b + "-# small", DiscordMessageSanitizer.breakDiscordOnlyMarkup("-# small"));
        assertEquals(b + "> quoted", DiscordMessageSanitizer.breakDiscordOnlyMarkup("> quoted"));
        assertEquals(b + ">>> the rest",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup(">>> the rest"));
        // List markers where Discord would draw a list, and only there.
        assertEquals(b + "- item", DiscordMessageSanitizer.breakDiscordOnlyMarkup("- item"));
        assertEquals(b + "* item", DiscordMessageSanitizer.breakDiscordOnlyMarkup("* item"));
        assertEquals(b + "+ item", DiscordMessageSanitizer.breakDiscordOnlyMarkup("+ item"));
        assertEquals(b + "12. item", DiscordMessageSanitizer.breakDiscordOnlyMarkup("12. item"));
        // After a line break and after leading spaces too.
        assertEquals("first\n" + b + "# Title\n  " + b + "- nested",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup("first\n# Title\n  - nested"));
        // A no-break space counts as a space on either side of a mark.
        assertEquals(" " + b + "# Title",
                DiscordMessageSanitizer.breakDiscordOnlyMarkup(" # Title"));
        // What Discord draws no differently is left as it is.
        for (String same : new String[] {
                "#### four", "#ooc/12 and #hashtag", "-5 degrees", "3.14 is pi",
                ">_<", "a # b > c - d 1. e * f", "*italic* first", "**bold** first",
                "**b** *i* __u__ ~~s~~ ||sp|| `c`", "[just brackets] (and parentheses)"}) {
            assertEquals(same, DiscordMessageSanitizer.breakDiscordOnlyMarkup(same));
        }
        assertEquals("", DiscordMessageSanitizer.breakDiscordOnlyMarkup(""));
        assertEquals("", DiscordMessageSanitizer.breakDiscordOnlyMarkup(null));
    }

    /**
     * A player's line can never pass for the bridge's own reply header:
     * its subtext mark is broken, so it reads at full size as typed.
     */
    @Test
    public void aLineCannotImitateTheBridgesReplyHeader() {
        String header = DiscordMessageSanitizer.replyHeader("Aldric", "hi", false, "");
        String typed = header.substring(0, header.length() - 1);
        assertEquals(DiscordMentions.BREAK + typed,
                DiscordMessageSanitizer.breakDiscordOnlyMarkup(typed));
    }

    /** A forward says where its message was said, by the channel's code name alone, and who said it. */
    @Test
    public void aForwardHeaderNamesItsPlaceAndItsAuthor() {
        assertEquals("-# ↪ Forwarded from \\#ooc · **Aldric**\n",
                DiscordMessageSanitizer.forwardHeader("Aldric", "#ooc/1234"));
        assertEquals("-# ↪ Forwarded from \\#gondor · **x\\_y**\n",
                DiscordMessageSanitizer.forwardHeader("x_y", "#gondor/9"));
    }

    @Test
    public void replyHeadersQuoteInSubtextAndLinkWhenTheyCan() {
        assertEquals("-# ↩ [**Aldric** — meet me at the gate](https://discord"
                + ".com/channels/9/8/30)\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "meet me at the gate", false,
                        "https://discord.com/channels/9/8/30"));
        assertEquals("-# ↩ **Aldric** — meet me at the gate\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "meet me at the gate", false, ""));
        // A quote with markdown in it reads as the text it is, brackets
        // included, so it cannot break out of the masked link.
        assertEquals("-# ↩ [**x\\_y** — a \\[b\\]\\(c\\) \\*d\\*](url)\n",
                DiscordMessageSanitizer.replyHeader("x_y",
                        "a [b](c) *d*", false, "url"));
        // An emoji in the quote goes as the emoji Discord renders.
        assertEquals("-# ↩ **Aldric** — hi 😳\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "hi :flushed:", false, null));
        assertEquals("-# ↩ **Aldric**\n",
                DiscordMessageSanitizer.replyHeader("Aldric", "", false, ""));
    }


    /** An action posts in italics under the speaker's name, and quotes in italics too (C2). */
    @Test
    public void actionsPostInItalics() {
        assertEquals("*draws his sword.*",
                DiscordMessageSanitizer.outboundAction("draws his sword."));
        assertEquals("*waves 😳*", DiscordMessageSanitizer.outboundAction(" waves :flushed: "));
        assertEquals("", DiscordMessageSanitizer.outboundAction("  "));
        assertEquals("a last backslash cannot open the closing mark",
                "*leans on the wall\\\\*", DiscordMessageSanitizer.outboundAction(
                        "leans on the wall\\"));
        assertEquals("-# ↩ **Aldric** *draws his sword.*\n",
                DiscordMessageSanitizer.replyHeader("Aldric", "draws his sword.",
                        true, ""));
    }

    @Test
    public void markdownEscapingCoversTheLinkBrackets() {
        assertEquals("\\[a\\]\\(b\\) \\*c\\* \\@d \\#e \\`f\\` \\|g\\| \\>h",
                DiscordMessageSanitizer.escapeMarkdown(
                        "[a](b) *c* @d #e `f` |g| >h"));
        assertEquals("", DiscordMessageSanitizer.escapeMarkdown(null));
        assertEquals("plain", DiscordMessageSanitizer.escapeMarkdown(
                " plain "));
    }

    @Test
    public void namesAreBoundedAndCleaned() {
        assertEquals("Sam Gamgee",
                DiscordMessageSanitizer.inboundName(" Sam\n§lGamgee "));
        assertEquals("", DiscordMessageSanitizer.inboundName(null));
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 50; index++) {
            name.append('x');
        }
        assertEquals(32, DiscordMessageSanitizer.inboundName(
                name.toString()).length());
    }

    /** A name is an account name in the chat, so it fits the account's 64 bytes. */
    @Test
    public void aNameFitsTheBytesAnAccountNameTakes() throws Exception {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 32; index++) {
            name.append('月');
        }
        String kept = DiscordMessageSanitizer.inboundName(name.toString());
        assertEquals(21, kept.length());
        assertEquals(63, kept.getBytes("UTF-8").length);
    }

    @Test
    public void filesStickersAndForwardsArriveInWords() {
        String link = "https://discord.com/channels/1/2/3";
        assertEquals("look *[Sticker: Wave]* *map.png* " + link,
                DiscordMessageSanitizer.inboundWithAttachments("look", "",
                        java.util.Collections.singletonList("Wave"),
                        java.util.Collections.singletonList("map.png"), link));
        assertEquals("*[Forwarded]* the gate is open",
                DiscordMessageSanitizer.inboundWithAttachments("", "the gate is open",
                        java.util.Collections.<String>emptyList(),
                        java.util.Collections.<String>emptyList(), link));
        // No file, no link; a name loses every mark it could smuggle in.
        assertEquals("*evillink.png*",
                DiscordMessageSanitizer.inboundWithAttachments("",
                        "", java.util.Collections.<String>emptyList(),
                        java.util.Collections.singletonList("**evil**[link].png"), null));
    }

    @Test
    public void theWordsGiveWayBeforeTheFilesDo() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 1500; index++) {
            long_.append('a');
        }
        String text = DiscordMessageSanitizer.inboundWithAttachments(long_.toString(), "",
                java.util.Collections.<String>emptyList(),
                java.util.Collections.singletonList("map.png"),
                "https://discord.com/channels/1/2/3");
        assertTrue(text.length() <= com.ninuna.losttales.chat.ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(text.endsWith("*map.png* https://discord.com/channels/1/2/3"));
    }

    /**
     * Paragraphs cross both ways: a game message's are Discord's lines, an
     * action's each in italics; a Discord message's lines are paragraphs,
     * eight at most, a block of code one span.
     */
    @Test
    public void paragraphsCrossBothWays() {
        assertEquals("*Aldric bows.*\n*He waits.*",
                DiscordMessageSanitizer.outboundAction("Aldric bows.\nHe waits."));
        assertEquals("one\ntwo", DiscordMessageSanitizer.outbound("one\ntwo"));
        assertEquals("a\nb\nc\nd\ne\nf\ng\nh i",
                DiscordMessageSanitizer.inbound("a\n\nb\nc\nd\ne\nf\ng\nh\ni", null));
        assertEquals("see `x = 1; y = 2`", DiscordMessageSanitizer.inbound(
                "see ```\nx = 1;\ny = 2\n```", null));
    }

    /**
     * A long turn naming many members can pass Discord's 2,000 once each
     * name is its id: the post is cut short of it, never inside an id.
     */
    @Test
    public void aPostIsCutToWhatDiscordTakes() {
        assertEquals("short", DiscordMessageSanitizer.fitted("short"));
        StringBuilder post = new StringBuilder();
        while (post.length() < 1990) {
            post.append('a');
        }
        post.append(" <@123456789012345678> the end");
        String cut = DiscordMessageSanitizer.fitted(post.toString());
        assertTrue(cut.length() <= DiscordMessageSanitizer.MAX_POST_CHARACTERS);
        assertTrue(cut.endsWith(" ..."));
        assertTrue(cut.indexOf('<') < 0);
    }
}
