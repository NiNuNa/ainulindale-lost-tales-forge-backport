package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.network.packet.ChatPacketFixtures;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A game names the member list's groups, the roles and the LOTR titles in
 * its player's language from the keys the server sends: a faction by
 * LOTR's name, a role the mod ships by its lang line, a title by LOTR's
 * line. A name an operator wrote and a Discord server's name read as they
 * were written. A mention reaches a role by the name this game shows it
 * by, or by its id, which reads the same in every language.
 */
public final class ChatRolesAndTitlesInThisGameTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String OPERATOR_KEY = "chat.losttales.role.operator";
    private static final String UNALIGNED_KEY = "lotr.faction.UNALIGNED.name";
    private static final String FARMER_KEY = "lotr.title.farmer";
    private static final List<String> KEYS = Arrays.asList(OPERATOR_KEY,
            UNALIGNED_KEY, "chat.losttales.server.name",
            "gui.losttales.chat.members.online",
            "gui.losttales.chat.members.heading",
            "chat.losttales.title.epithet");

    private ChatRoleCatalog catalog;

    @Before
    public void readInAnotherLanguage() {
        this.catalog = ChatRoleConfig.parse(new String[] {
                ChatRoleConfig.DEFAULT_OPERATOR_ENTRY,
                "moderator=name:Moderators;mention:true;rank:15",
        }, null, ChatRoleConfig.SILENT);
        ChatRoleCatalog.install(this.catalog);
        inject(OPERATOR_KEY + "=Betreiber\n"
                + UNALIGNED_KEY + "=Ungebunden\n"
                + FARMER_KEY + "=Bauer\n"
                + "chat.losttales.server.name=Serveur\n"
                + "gui.losttales.chat.members.online=En ligne\n"
                + "gui.losttales.chat.members.heading=%s - %s\n"
                + "chat.losttales.title.epithet=%s %s\n");
    }

    /** The English lines of the keys read here: the mod's, and LOTR's for the title. */
    @After
    public void readInEnglishAgain() throws IOException {
        StringBuilder english = new StringBuilder(FARMER_KEY + "=Farmer\n");
        InputStream in = ChatRolesAndTitlesInThisGameTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && KEYS.contains(line.substring(0, equals))) {
                    english.append(line).append('\n');
                }
            }
        } finally {
            in.close();
        }
        inject(english.toString());
        ChatRoleCatalog.resetToBuiltIn();
        ClientChatChannelState.clear();
    }

    /**
     * The seeded operator role is named by this game's line; a role an
     * operator named reads as written, in every language, and one named
     * nothing by its id. A line the server sends names a shipped role by
     * its key, for each reader's game to name.
     */
    @Test
    public void aShippedRoleIsNamedInThisGamesLanguage() {
        ChatAccountRole operator = this.catalog.byId("operator");
        ChatAccountRole moderator = this.catalog.byId("moderator");
        assertEquals("Betreiber", operator.getDisplayName());
        assertEquals("Moderators", moderator.getDisplayName());
        assertEquals(Arrays.asList("Betreiber", "operator"), operator.mentionNames());
        assertEquals(Arrays.asList("Moderators", "moderator"), moderator.mentionNames());
        IChatComponent said = operator.nameComponent();
        assertTrue(said instanceof ChatComponentTranslation);
        assertEquals(OPERATOR_KEY, ((ChatComponentTranslation)said).getKey());
        assertEquals("Moderators", moderator.nameComponent().getUnformattedText());
    }

    /**
     * A mention reaches a role by the name this game shows it by and by
     * its id, whatever their case: {@code @Betreiber} here, {@code
     * @operator} — and so {@code @Operator} — in every game.
     */
    @Test
    public void aMentionReachesARoleByItsNameHereOrByItsId() {
        ChatAccountRole operator = this.catalog.byId("operator");
        ChatAccountRole moderator = this.catalog.byId("moderator");
        assertSame(operator, ChatMentionColors.roleFor("Betreiber"));
        assertSame(operator, ChatMentionColors.roleFor("operator"));
        assertSame(operator, ChatMentionColors.roleFor("Operator"));
        assertSame(moderator, ChatMentionColors.roleFor("Moderators"));
        assertSame(moderator, ChatMentionColors.roleFor("MODERATOR"));
        assertNull(ChatMentionColors.roleFor("Mods"));
        assertTrue(operator.answersTo(" operator "));
        assertFalse(operator.answersTo(""));
    }

    /** The {@code @} list shows a role by this game's name and finds it by its id too. */
    @Test
    public void theAtListShowsARoleByItsNameHereAndFindsItByItsId() {
        List<ChatMentionCandidate> candidates =
                ChatInputCompletion.mentionCandidatesFor(UUID.randomUUID(),
                        "Nils", "Aldric", null,
                        Collections.<LostTalesChatMembersPacket.Member>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String, CharacterAppearance>emptyMap());
        ChatMentionCandidate operator = null;
        for (ChatMentionCandidate candidate : candidates) {
            if ("role:operator".equals(candidate.getKey())) {
                operator = candidate;
            }
        }
        assertEquals("Betreiber", operator.getDisplayName());
        assertTrue(operator.isRole());
        assertTrue(operator.matches("betr"));
        assertTrue(operator.matches("oper"));
        List<ChatInputMentions.Found> found = ChatInputMentions.find(
                "@operator and @Betreiber", candidates);
        assertEquals(2, found.size());
        assertSame(operator, found.get(0).candidate);
        assertSame(operator, found.get(1).candidate);
    }

    /**
     * Each group is headed in this game's words from its key: the Server,
     * Online, a faction by LOTR's name, a role by its name here; a Discord
     * server by the name Discord gives it.
     */
    @Test
    public void groupsAreHeadedInThisGamesLanguage() {
        assertEquals("Serveur - 1", ChatMemberList.headingOf(
                member("Server", LostTalesChatMembersPacket.SERVER_GROUP, "", ""), 1));
        assertEquals("En ligne - 2", ChatMemberList.headingOf(
                member("Alex", "", "", ""), 2));
        assertEquals("Ungebunden - 3", ChatMemberList.headingOf(member("Aldric",
                LotrCharacterAdapter.UNALIGNED_FACTION_ID, "", ""), 3));
        assertEquals("Betreiber - 1", ChatMemberList.headingOf(
                member("Steve", "operator", "", ""), 1));
        assertEquals("Moderators - 1", ChatMemberList.headingOf(
                member("Alex", "moderator", "", ""), 1));
        assertEquals("Arda - 4", ChatMemberList.headingOf(member("Sam",
                LostTalesChatMembersPacket.DISCORD_GROUP_PREFIX + "100", "Arda", ""), 4));
        // A role this game was not told of is headed by its id.
        assertEquals("herald", ChatMemberList.groupNameOf(
                member("Bob", "herald", "", "")));
    }

    /**
     * A member's title is LOTR's line in this game's words, after their
     * faction's people while they stand under their faction; an absent
     * member's alone.
     */
    @Test
    public void aMembersTitleIsReadInThisGamesLanguage() {
        LostTalesChatMembersPacket.Member here = member("Aldric",
                LotrCharacterAdapter.UNALIGNED_FACTION_ID, "", FARMER_KEY);
        assertEquals("Ungebunden Bauer", ChatMemberList.epithetOf(here));
        LostTalesChatMembersPacket.Member away = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "aldric", UUID.randomUUID(), "Aldric", 0, "",
                FARMER_KEY, 0, "", "", 0, false);
        assertEquals("Bauer", ChatMemberList.epithetOf(away));
        assertEquals("", ChatMemberList.epithetOf(member("Alex", "", "", "")));
    }

    /**
     * A line's title reads in this game's words too; a Discord member's
     * Discord server reads as Discord names it, whatever it looks like.
     */
    @Test
    public void aLinesTitleIsReadInThisGamesLanguage() {
        LostTalesChatMessagePacket titled = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aldric", "Aldric123", "Good harvest.")
                .title(FARMER_KEY).faction(LotrCharacterAdapter.UNALIGNED_FACTION_ID)
                .build();
        assertEquals("Ungebunden Bauer", LostTalesChatPresentation.epithetOf(titled));
        LostTalesChatMessagePacket discord = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Sam", "Sam", "Hello.")
                .sender(LostTalesChatMessagePacket.discordSenderId("100"))
                .title(FARMER_KEY).build();
        assertEquals(FARMER_KEY, LostTalesChatPresentation.epithetOf(discord));
    }

    private static LostTalesChatMembersPacket.Member member(String name,
                                                           String groupKey,
                                                           String groupName,
                                                           String title) {
        return new LostTalesChatMembersPacket.Member(UUID.randomUUID(),
                name.toLowerCase(), null, name, 0xFFFFFF, "", title, 0xFFFFFF,
                groupKey, groupName, 0, true);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
