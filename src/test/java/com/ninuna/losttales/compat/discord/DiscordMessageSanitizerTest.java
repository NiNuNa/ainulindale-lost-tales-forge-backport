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
        assertEquals("hey @Frodo and @user, see #channel :smile: @role",
                DiscordMessageSanitizer.inbound(
                        "hey <@1234> and <@!99>,\nsee <#55> <a:smile:7> <@&8>",
                        names));
        assertEquals("no codes here",
                DiscordMessageSanitizer.inbound("no §ccodes here",
                        Collections.<String, String>emptyMap()));
        assertEquals("", DiscordMessageSanitizer.inbound("  \n\t ", null));
        assertEquals("", DiscordMessageSanitizer.inbound(null, null));
    }

    @Test
    public void longMessagesAreCutToTheChatLimit() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 80; index++) {
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
    public void aliasesResolveAndUnknownEmojiAreDropped() {
        // A literal alias shortcode, and a custom emoji named by one.
        assertEquals("well :flushed: then",
                DiscordMessageSanitizer.inbound(
                        "well :flushed_face: then", null));
        assertEquals(":laughing:", DiscordMessageSanitizer.inbound(
                "<:Satisfied:12345>", null));
        // An emoji the registry does not carry is dropped, not shown as
        // broken glyphs — a ZWJ sequence whole, even where its base is
        // known, so it never becomes the wrong emoji.
        assertEquals("look here", DiscordMessageSanitizer.inbound(
                "look 🤖 here", null));
        assertEquals("so dizzy", DiscordMessageSanitizer.inbound(
                "so 😵‍💫 dizzy", null));
    }

    @Test
    public void readOnlyLinesArePostedUnderTheChannelTag() {
        assertEquals("[Global] Aragorn",
                DiscordMessageSanitizer.channelTaggedName("Global", "Aragorn"));
        assertEquals("[OOC] Steve",
                DiscordMessageSanitizer.channelTaggedName(" OOC ", " Steve "));
        assertEquals("[Global] ",
                DiscordMessageSanitizer.channelTaggedName("Global", null));
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            name.append('a');
        }
        String tagged = DiscordMessageSanitizer.channelTaggedName("Global",
                name.toString());
        assertEquals(DiscordMessageSanitizer.MAX_WEBHOOK_NAME_LENGTH,
                tagged.length());
        assertTrue(tagged.startsWith("[Global] aaaa"));
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

    @Test
    public void replyHeadersQuoteInSubtextAndLinkWhenTheyCan() {
        assertEquals("-# ↩ [**Aldric** — meet me at the gate](https://discord"
                + ".com/channels/9/8/30)\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "meet me at the gate",
                        "https://discord.com/channels/9/8/30"));
        assertEquals("-# ↩ **Aldric** — meet me at the gate\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "meet me at the gate", ""));
        // A quote with markdown in it reads as the text it is, brackets
        // included, so it cannot break out of the masked link.
        assertEquals("-# ↩ [**x\\_y** — a \\[b\\]\\(c\\) \\*d\\*](url)\n",
                DiscordMessageSanitizer.replyHeader("x_y",
                        "a [b](c) *d*", "url"));
        // An emoji in the quote goes as the emoji Discord renders.
        assertEquals("-# ↩ **Aldric** — hi 😳\n",
                DiscordMessageSanitizer.replyHeader("Aldric",
                        "hi :flushed:", null));
        assertEquals("-# ↩ **Aldric**\n",
                DiscordMessageSanitizer.replyHeader("Aldric", "", ""));
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
}
