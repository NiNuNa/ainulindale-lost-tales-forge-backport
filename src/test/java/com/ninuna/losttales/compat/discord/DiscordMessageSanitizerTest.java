package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatTranslatedWords;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesWords;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringTranslate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiscordMessageSanitizerTest {

    @Test
    public void discordMarkupIsSpelledOutAndLinesAreFlattened() {
        Map<String, String> names = new HashMap<String, String>();
        names.put("1234", "Frodo");
        assertEquals("hey @Frodo and @user,\nsee #channel :smile: @role",
                inbound(
                        "hey <@1234> and <@!99>,\nsee <#55> <a:smile:7> <@&8>",
                        names));
        assertEquals("no codes here",
                inbound("no §ccodes here",
                        Collections.<String, String>emptyMap()));
        assertEquals("", inbound("  \n\t ", null));
        assertEquals("", inbound(null, null));
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
                inbound(
                        "# Title\n> quoted\n```\ncode\n```\n_it_", null));
    }

    @Test
    public void longMessagesAreCutToTheChatLimit() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 300; index++) {
            text.append("word ");
        }
        String cut = inbound(text.toString(), null);
        assertTrue(cut.length() <= ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(cut.endsWith("..."));
        assertTrue(ChatMessageValidator.isValid(cut));
    }

    @Test
    public void unicodeEmojiBecomeCanonicalShortcodes() {
        assertEquals("hi :flushed: there",
                inbound(
                        "hi 😳 there", null));
        // Adjacent emoji, and the heart with and without its selector.
        assertEquals(":joy::slight_smile:",
                inbound(
                        "😂🙂", null));
        assertEquals(":heart: :heart:", inbound(
                "❤️ ❤", null));
    }

    @Test
    public void aliasesResolveAndUnknownEmojiKeepTheirNames() {
        // A literal alias shortcode, and a custom emoji named by one.
        assertEquals("well :flushed: then",
                inbound(
                        "well :flushed_face: then", null));
        assertEquals(":laughing:", inbound(
                "<:Satisfied:12345>", null));
        // An emoji the registry does not carry reads as its Discord name,
        // never as broken glyphs: a ZWJ sequence whole, even where its
        // base is known, so it never becomes the wrong emoji, and a flag
        // as one name.
        assertEquals("look :robot: here", inbound(
                "look 🤖 here", null));
        assertEquals("so :face_with_spiral_eyes: dizzy",
                inbound("so 😵‍💫 dizzy", null));
        assertEquals("from :flag_de:", inbound(
                "from 🇩🇪", null));
        // A skin tone goes with the emoji the registry has.
        assertEquals(":index_pointing_at_the_viewer:",
                inbound("🫵🏽", null));
        // Signs the chat's font draws stay as they are.
        assertEquals("© 2026", inbound("© 2026", null));
        assertEquals(":pepe:", inbound("<:pepe:12345>", null));
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
                DiscordMessageSanitizer.forwardHeader(EnglishWords.INSTANCE,
                        "Aldric", "#ooc/1234"));
        assertEquals("-# ↪ Forwarded from \\#gondor · **x\\_y**\n",
                DiscordMessageSanitizer.forwardHeader(EnglishWords.INSTANCE,
                        "x_y", "#gondor/9"));
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


    /**
     * An action posts as the Narrator tells it in the game: the action
     * mark shown as it is, then the sentence its speaker's name opens, in
     * italics (C25); a quote of one is in italics too (C2).
     */
    @Test
    public void actionsPostAsTheNarratorTellsThem() {
        assertEquals("\\* *Aldric draws his sword.*",
                DiscordMessageSanitizer.outboundAction("Aldric", "draws his sword."));
        assertEquals("\\* *Aldric waves 😳*",
                DiscordMessageSanitizer.outboundAction("Aldric", " waves :flushed: "));
        assertEquals("", DiscordMessageSanitizer.outboundAction("Aldric", "  "));
        assertEquals("no name leaves the words alone in the sentence",
                "\\* *draws his sword.*",
                DiscordMessageSanitizer.outboundAction("", "draws his sword."));
        assertEquals("a last backslash cannot open the closing mark",
                "\\* *Aldric leans on the wall\\\\*",
                DiscordMessageSanitizer.outboundAction("Aldric",
                        "leans on the wall\\"));
        assertEquals("the name reads as it is written",
                "\\* *Bilbo\\_Baggins bows.*",
                DiscordMessageSanitizer.outboundAction("Bilbo_Baggins", "bows."));
        assertEquals("-# ↩ **Aldric** *draws his sword.*\n",
                DiscordMessageSanitizer.replyHeader("Aldric", "draws his sword.",
                        true, ""));
    }

    /**
     * The escaped mark makes no list item of the post and the bridge's
     * own breaking leaves it whole, while a code typed in the sentence is
     * still broken as any line's is.
     */
    @Test
    public void anActionsMarkCrossesAsItIs() {
        String action = DiscordMessageSanitizer.outboundAction("Aldric",
                "bows to @everyone.");
        assertEquals(action, DiscordMessageSanitizer.breakDiscordOnlyMarkup(action));
        DiscordMentions.Post post = DiscordMentions.rewrite(action,
                Collections.<com.ninuna.losttales.chat.ChatNamedPlayer>emptyList(),
                Collections.<String>emptySet());
        assertEquals("\\* *Aldric bows to @" + DiscordMentions.BREAK
                + "everyone.*", post.content);
        assertTrue(post.pinged.isEmpty());
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
                attached("look", "", Collections.singletonList("Wave"),
                        Collections.singletonList("map.png"), link).getText());
        assertEquals("*[Forwarded]* the gate is open",
                attached("", "the gate is open", Collections.<String>emptyList(),
                        Collections.<String>emptyList(), link).getText());
        // No file, no link; a name loses every mark it could smuggle in.
        assertEquals("*evillink.png*",
                attached("", "", Collections.<String>emptyList(),
                        Collections.singletonList("**evil**[link].png"), null).getText());
    }

    @Test
    public void theWordsGiveWayBeforeTheFilesDo() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 1500; index++) {
            long_.append('a');
        }
        String text = attached(long_.toString(), "",
                Collections.<String>emptyList(),
                Collections.singletonList("map.png"),
                "https://discord.com/channels/1/2/3").getText();
        assertTrue(text.length() <= com.ninuna.losttales.chat.ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(text.endsWith("*map.png* https://discord.com/channels/1/2/3"));
    }

    /**
     * A forward's and a sticker's marks are the lang file's: the text has
     * them in the server's words, and the pieces keep them as marks, in
     * order, joining to the text exactly. Files, the link and the
     * member's own words are plain.
     */
    @Test
    public void theMarksArePiecesEachGameTranslates() {
        String link = "https://discord.com/channels/1/2/3";
        DiscordInboundLine line = attached("look", "",
                Arrays.asList("Wave", "Dance"), Collections.singletonList("map.png"), link);
        assertEquals("look *[Sticker: Wave]* *[Sticker: Dance]* *map.png* " + link,
                line.getText());
        assertTrue(line.hasMarks());
        List<DiscordInboundLine.Piece> pieces = line.getPieces();
        assertEquals(5, pieces.size());
        assertPlain("look ", pieces.get(0));
        assertMark(DiscordMessageSanitizer.STICKER, "*[Sticker: Wave]*", pieces.get(1));
        assertEquals(Collections.singletonList("Wave"), pieces.get(1).arguments);
        assertPlain(" ", pieces.get(2));
        assertMark(DiscordMessageSanitizer.STICKER, "*[Sticker: Dance]*", pieces.get(3));
        assertPlain(" *map.png* " + link, pieces.get(4));
        assertJoinsInEnglish(line);

        DiscordInboundLine forward = attached("", "the gate is open",
                Collections.<String>emptyList(), Collections.<String>emptyList(), link);
        assertEquals(2, forward.getPieces().size());
        assertMark(DiscordMessageSanitizer.FORWARDED, "*[Forwarded]*",
                forward.getPieces().get(0));
        assertTrue(forward.getPieces().get(0).arguments.isEmpty());
        assertPlain(" the gate is open", forward.getPieces().get(1));
        assertJoinsInEnglish(forward);

        // A member's own words win over a forward's, and no mark is made.
        DiscordInboundLine said = attached("mine", "theirs",
                Collections.<String>emptyList(), Collections.<String>emptyList(), link);
        assertEquals("mine", said.getText());
        assertFalse(said.hasMarks());
    }

    /** A line without a mark has nothing to translate: no body travels with it. */
    @Test
    public void aLineWithoutAMarkHasNoBody() {
        DiscordInboundLine plain = attached("look", "", Collections.<String>emptyList(),
                Collections.singletonList("map.png"), "https://discord.com/channels/1/2/3");
        assertFalse(plain.hasMarks());
        assertEquals(1, plain.getPieces().size());
        assertEquals("", DiscordInboundBody.jsonOf(plain));
        assertEquals("", DiscordInboundBody.jsonOf(DiscordInboundLine.EMPTY));
        assertEquals("", DiscordInboundBody.jsonOf(null));
        assertEquals("", attached("", "", Collections.<String>emptyList(),
                Collections.<String>emptyList(), null).getText());
    }

    /**
     * The words are cut on the server's words, and the pieces the same
     * way: a mark the cut leaves whole stays a mark, the tail's stickers
     * among them, and the text still joins from the pieces.
     */
    @Test
    public void theCutKeepsThePiecesInStepWithTheText() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 1500; index++) {
            long_.append('a');
        }
        DiscordInboundLine forward = attached("", long_.toString(),
                Collections.singletonList("Wave"), Collections.<String>emptyList(), null);
        String text = forward.getText();
        assertTrue(text.length() <= ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(text.startsWith("*[Forwarded]* aaa"));
        assertTrue(text.endsWith("aaa... *[Sticker: Wave]*"));
        List<DiscordInboundLine.Piece> pieces = forward.getPieces();
        assertMark(DiscordMessageSanitizer.FORWARDED, "*[Forwarded]*", pieces.get(0));
        assertMark(DiscordMessageSanitizer.STICKER, "*[Sticker: Wave]*",
                pieces.get(pieces.size() - 1));
        assertJoinsInEnglish(forward);
    }

    /** A mark the cut runs through keeps the part before the cut as plain words. */
    @Test
    public void aMarkTheCutRunsThroughBecomesPlainWords() {
        List<DiscordInboundLine.Piece> pieces = Arrays.asList(
                DiscordInboundLine.Piece.plain("  a "),
                DiscordInboundLine.Piece.mark(EnglishWords.INSTANCE,
                        DiscordMessageSanitizer.STICKER, "Wave"),
                DiscordInboundLine.Piece.plain(" b  "));
        List<DiscordInboundLine.Piece> cut = DiscordInboundLine.slice(pieces, 0, 9);
        assertEquals("  a *[Sti", DiscordInboundLine.textOf(cut));
        assertFalse(cut.get(cut.size() - 1).isMark());
        DiscordInboundLine trimmed = new DiscordInboundLine(DiscordInboundLine.trim(pieces));
        assertEquals("a *[Sticker: Wave]* b", trimmed.getText());
        assertEquals("  a *[Sticker: Wave]* b  ".trim(), trimmed.getText());
        assertTrue(trimmed.getPieces().get(1).isMark());
    }

    /**
     * The body is the pieces as one chat component: plain text and the
     * marks' translations in order, read back by a client as translated
     * words and nothing else.
     */
    @Test
    public void theBodyIsTheMarksAsTranslatedWords() {
        DiscordInboundLine line = attached("look", "", Collections.singletonList("Wave"),
                Collections.<String>emptyList(), null);
        String json = DiscordInboundBody.jsonOf(line);
        assertTrue(json, json.length() > 0);
        IChatComponent read = IChatComponent.Serializer.func_150699_a(json);
        assertTrue(ChatTranslatedWords.isWordsComponent(read));
        assertEquals(2, read.getSiblings().size());
        assertEquals("look ", ((IChatComponent)read.getSiblings().get(0))
                .getUnformattedTextForChat());
        ChatComponentTranslation sticker =
                (ChatComponentTranslation)read.getSiblings().get(1);
        assertEquals(DiscordMessageSanitizer.STICKER, sticker.getKey());
        assertEquals("Wave", sticker.getFormatArgs()[0]);
    }

    /** Read back in the server's language, the body says the line's text exactly. */
    @Test
    public void theBodyReadInTheServersLanguageIsTheText() throws Exception {
        // The two marks' English lines alone: other tests read every other
        // key untranslated.
        BufferedReader lang = new BufferedReader(new InputStreamReader(
                DiscordMessageSanitizerTest.class.getResourceAsStream(
                        "/assets/losttales/lang/en_US.lang"), "UTF-8"));
        StringBuilder marks = new StringBuilder();
        try {
            String line;
            while ((line = lang.readLine()) != null) {
                if (line.startsWith(DiscordMessageSanitizer.FORWARDED + "=")
                        || line.startsWith(DiscordMessageSanitizer.STICKER + "=")) {
                    marks.append(line).append('\n');
                }
            }
        } finally {
            lang.close();
        }
        StringTranslate.inject(new ByteArrayInputStream(
                marks.toString().getBytes("UTF-8")));
        DiscordInboundLine forward = attached("", "the gate is open",
                Arrays.asList("Wave", "Dance"), Collections.singletonList("map.png"),
                "https://discord.com/channels/1/2/3");
        IChatComponent read = IChatComponent.Serializer.func_150699_a(
                DiscordInboundBody.jsonOf(forward));
        assertEquals(forward.getText(), read.getUnformattedText());
    }

    /** A mention whose name the message does not give reads in the server's words. */
    @Test
    public void unnamedMentionsReadInTheServersWords() {
        LostTalesWords german = new LostTalesWords() {
            @Override
            public String format(String key, Object... arguments) {
                return DiscordMessageSanitizer.UNKNOWN_USER.equals(key) ? "Nutzer"
                        : DiscordMessageSanitizer.UNKNOWN_ROLE.equals(key) ? "Rolle"
                        : "Kanal";
            }
        };
        assertEquals("@Nutzer @Rolle #Kanal",
                DiscordMessageSanitizer.inbound("<@1> <@&2> <#3>", null, null, german));
    }

    /**
     * A role's and a channel's mention read as the names their own server
     * gives them, in bold right behind the sign, so neither reads as a
     * mention or a game channel's link; one the bridge does not know
     * there reads as the server's words.
     */
    @Test
    public void rolesAndChannelsReadByTheirNames() {
        DiscordMessageSanitizer.Places shire = places("Moderators", "general");
        assertEquals("ask @**Moderators** in #**general**",
                DiscordMessageSanitizer.inbound("ask <@&8> in <#55>", null, shire,
                        EnglishWords.INSTANCE));
        assertEquals("ask @role in #channel",
                DiscordMessageSanitizer.inbound("ask <@&9> in <#56>", null, shire,
                        EnglishWords.INSTANCE));
        assertEquals("ask @role in #channel",
                DiscordMessageSanitizer.inbound("ask <@&8> in <#55>", null,
                        DiscordMessageSanitizer.Places.NONE, EnglishWords.INSTANCE));
        assertEquals("@role #channel",
                DiscordMessageSanitizer.inbound("<@&8> <#55>", null,
                        places("  ", "**"), EnglishWords.INSTANCE));
        // A member's mention stays their name, and a name put in is never
        // read as a mention again.
        Map<String, String> names = new HashMap<String, String>();
        names.put("1", "<#55>");
        assertEquals("@<#55> #**general**", DiscordMessageSanitizer.inbound(
                "<@1> <#55>", names, shire, EnglishWords.INSTANCE));
        // Inside the bold, the chat's marks still pair.
        assertEquals("**hi @**Moderators** all**",
                DiscordMessageSanitizer.inbound("**hi <@&8> all**", null, shire,
                        EnglishWords.INSTANCE));
    }

    /** A hostile name is plain words: no mark, mention, link, token or code survives in it. */
    @Test
    public void aPlacesNameHoldsNothingButWords() {
        assertEquals("Mods", DiscordMessageSanitizer.placeName("**Mods**"));
        assertEquals("everyone", DiscordMessageSanitizer.placeName("@everyone"));
        assertEquals("ix", DiscordMessageSanitizer.placeName("[i:x]"));
        assertEquals("general", DiscordMessageSanitizer.placeName("#general"));
        assertEquals("Red", DiscordMessageSanitizer.placeName("§cRed"));
        assertEquals("no web address", "https//evil.example",
                DiscordMessageSanitizer.placeName("https://evil.example"));
        assertEquals("skull", DiscordMessageSanitizer.placeName(":skull:"));
        assertEquals("&1", DiscordMessageSanitizer.placeName("<@&1>"));
        assertEquals("Mod Team", DiscordMessageSanitizer.placeName(
                " Mod ​‮ Team\n"));
        assertEquals("", DiscordMessageSanitizer.placeName("**__~~||``"));
        assertEquals("", DiscordMessageSanitizer.placeName(null));
        StringBuilder long200 = new StringBuilder();
        for (int index = 0; index < 200; index++) {
            long200.append(index % 10 == 9 ? ' ' : 'x');
        }
        String cut = DiscordMessageSanitizer.placeName(long200.toString());
        assertTrue(cut, cut.length() <= DiscordMessageSanitizer.MAX_PLACE_NAME);
        assertEquals(cut, cut.trim());
        String said = DiscordMessageSanitizer.inbound("<@&8>", null,
                places("@**x** [i:Sword] #ooc/123 <@&9> §k" + long200, ""),
                EnglishWords.INSTANCE);
        assertEquals("@**x iSword ooc/123 &9 xxxxxxxxx xx**", said);
        assertEquals(said, 2, said.split("\\*\\*", -1).length - 1);
        assertEquals(said, 1, said.length() - said.replace("@", "").length());
        assertFalse(said, said.indexOf('#') >= 0 || said.indexOf('[') >= 0
                || said.indexOf('<') >= 0 || said.indexOf(':') >= 0
                || said.indexOf('§') >= 0);
        assertTrue(ChatMessageValidator.isValid(said));
    }

    private static DiscordMessageSanitizer.Places places(final String role,
                                                         final String channel) {
        return new DiscordMessageSanitizer.Places() {
            @Override
            public String roleName(String roleId) {
                return "8".equals(roleId) ? role : "";
            }

            @Override
            public String channelName(String channelId) {
                return "55".equals(channelId) ? channel : "";
            }
        };
    }

    private static String inbound(String content, Map<String, String> names) {
        return DiscordMessageSanitizer.inbound(content, names, null,
                EnglishWords.INSTANCE);
    }

    private static DiscordInboundLine attached(String said, String forwarded,
                                               List<String> stickers, List<String> files,
                                               String link) {
        return DiscordMessageSanitizer.inboundWithAttachments(EnglishWords.INSTANCE,
                said, forwarded, stickers, files, link);
    }

    private static void assertPlain(String words, DiscordInboundLine.Piece piece) {
        assertFalse(piece.isMark());
        assertEquals(words, piece.words);
    }

    private static void assertMark(String key, String words, DiscordInboundLine.Piece piece) {
        assertTrue(piece.isMark());
        assertEquals(key, piece.key);
        assertEquals(words, piece.words);
    }

    /** Each piece read again in English joins to the line's text. */
    private static void assertJoinsInEnglish(DiscordInboundLine line) {
        StringBuilder joined = new StringBuilder();
        for (DiscordInboundLine.Piece piece : line.getPieces()) {
            joined.append(piece.isMark() ? EnglishWords.INSTANCE.format(piece.key,
                    piece.arguments.toArray()) : piece.words);
        }
        assertEquals(line.getText(), joined.toString());
    }

    /**
     * Paragraphs cross both ways: a game message's are Discord's lines, an
     * action's each in italics, the mark and the name on the first; a
     * Discord message's lines are paragraphs, eight at most, a block of
     * code one span.
     */
    @Test
    public void paragraphsCrossBothWays() {
        assertEquals("\\* *Aldric bows.*\n*He waits.*",
                DiscordMessageSanitizer.outboundAction("Aldric", "bows.\nHe waits."));
        assertEquals("one\ntwo", DiscordMessageSanitizer.outbound("one\ntwo"));
        assertEquals("a\nb\nc\nd\ne\nf\ng\nh i",
                inbound("a\n\nb\nc\nd\ne\nf\ng\nh\ni", null));
        assertEquals("see `x = 1; y = 2`", inbound(
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
