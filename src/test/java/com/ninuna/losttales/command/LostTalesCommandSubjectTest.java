package com.ninuna.losttales.command;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.validation.CharacterNames;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One resolver for every command that names a person (DATA-05): a name
 * finds an account or a character of this world, a name both answer to
 * waits for {@code account:} or {@code character:}, a name nobody answers
 * to gets one line, and no command finds a person its own way.
 */
public final class LostTalesCommandSubjectTest {

    private static final UUID NILS = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BEREN_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID ALDRIC_OWNER =
            UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    private final Map<String, UUID> accounts = new HashMap<String, UUID>();
    private final List<RoleplayCharacter> characters = new ArrayList<RoleplayCharacter>();
    private final LostTalesCommandSubject.Lookup lookup = new LostTalesCommandSubject.Lookup() {
        @Override
        public UUID account(String name) {
            return accounts.get(name.toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public RoleplayCharacter character(String name) {
            for (RoleplayCharacter character : characters) {
                if (CharacterNames.same(name, character.getName())) {
                    return character;
                }
            }
            return null;
        }
    };

    @Test
    public void aNameFindsAnAccountOrACharacter() {
        this.accounts.put("nils", NILS);
        RoleplayCharacter aldric = character(ALDRIC_OWNER, "Aldric the Bold");
        this.characters.add(aldric);

        LostTalesCommandSubject account = LostTalesCommandSubject.decide("Nils", this.lookup);
        assertNull(account.problem);
        assertEquals(NILS, account.account);
        assertEquals(NILS, account.owner);
        assertNull(account.character);

        LostTalesCommandSubject character = LostTalesCommandSubject.decide(
                "AldrictheBold", this.lookup);
        assertNull("a name typed without its spaces is the same name", character.problem);
        assertEquals(aldric.getCharacterId(), character.character);
        assertEquals("a character stands for the account that owns it",
                ALDRIC_OWNER, character.owner);
        assertNull(character.account);
    }

    @Test
    public void aNameBothAnswerToWaitsForItsPrefix() {
        this.accounts.put("beren", BEREN_ACCOUNT);
        RoleplayCharacter beren = character(ALDRIC_OWNER, "Beren");
        this.characters.add(beren);

        LostTalesCommandSubject both = LostTalesCommandSubject.decide("Beren", this.lookup);
        assertEquals(LostTalesCommandSubject.SAY + "both", both.problem);
        assertArrayEquals(new Object[] {"Beren", "account:Beren", "character:Beren"},
                both.problemArguments);
        assertEquals(BEREN_ACCOUNT,
                LostTalesCommandSubject.decide("account:Beren", this.lookup).account);
        assertEquals(beren.getCharacterId(),
                LostTalesCommandSubject.decide("Character: Beren", this.lookup).character);
    }

    @Test
    public void aNameNobodyAnswersToGetsOneLine() {
        LostTalesCommandSubject nobody = LostTalesCommandSubject.decide("Gollum", this.lookup);
        assertEquals(LostTalesCommandSubject.UNKNOWN, nobody.problem);
        assertArrayEquals(new Object[] {"Gollum"}, nobody.problemArguments);
        assertEquals(LostTalesCommandSubject.UNKNOWN,
                LostTalesCommandSubject.decide("character:Gollum", this.lookup).problem);
        assertEquals(LostTalesCommandSubject.SAY + "empty",
                LostTalesCommandSubject.decide("account: ", this.lookup).problem);
    }

    /** A selector picks among the players online; one that picks nobody gets the same line. */
    @Test
    public void aSelectorThatPicksNobodyGetsTheSameLine() {
        LostTalesCommandSubject nobody = LostTalesCommandSubject.resolve(
                FakeCommandSender.operator("Ops"), "@p");
        assertEquals(LostTalesCommandSubject.UNKNOWN, nobody.problem);
        assertArrayEquals(new Object[] {"@p"}, nobody.problemArguments);
    }

    /** Only a player may leave the name out: anyone else is asked for one. */
    @Test
    public void noNameFromTheConsoleAsksForOne() {
        FakeCommandSender console = FakeCommandSender.operator("Server");
        assertNull(LostTalesCommandSubject.account(console, (String)null));
        assertNull(LostTalesCommandSubject.inPerson(console, new String[] {"show"}, 1));
        assertEquals(Arrays.asList(
                "Name an account or a character: only a player running the command may"
                        + " leave the name out.",
                "Name an account or a character: only a player running the command may"
                        + " leave the name out."), console.told());
    }

    /**
     * No command finds a person its own way: none asks vanilla for a
     * player by name or looks a name up among the players online.
     */
    @Test
    public void noCommandFindsAPersonItsOwnWay() throws IOException {
        File commands = new File("src/main/java/com/ninuna/losttales/command");
        File[] files = commands.listFiles();
        assertTrue("the command sources are where the build keeps them",
                files != null && files.length > 12);
        Pattern ownWay = Pattern.compile(
                "\\bgetPlayer\\s*\\(|func_152612_a\\s*\\(|findOnline\\s*\\(\\s*args"
                        + "|getPlayerForUsername\\s*\\(");
        List<String> problems = new ArrayList<String>();
        for (File file : files) {
            if (!file.getName().endsWith(".java")) {
                continue;
            }
            String[] lines = new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8).split("\n");
            for (int index = 0; index < lines.length; index++) {
                Matcher found = ownWay.matcher(lines[index]);
                if (found.find()) {
                    problems.add(file.getName() + ":" + (index + 1) + " " + lines[index].trim());
                }
            }
        }
        assertEquals(new ArrayList<String>(), problems);
    }

    private static RoleplayCharacter character(UUID owner, String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), owner)
                .slot(1).name(name).race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin("losttales:human_bree_masculine_0").age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .build();
    }
}
