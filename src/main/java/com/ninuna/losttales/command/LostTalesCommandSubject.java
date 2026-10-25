package com.ninuna.losttales.command;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.KnownAccounts;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerSelector;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Whom a command's name names, an account or a character, read alike by
 * every command that names a person (DATA-05): a role, a structure ban, a
 * mute, a quest, a marker, a skill, a character's repair. The name is
 * looked up as both, among everyone this world has seen, online or not;
 * one both answer to is refused until it is given as
 * {@code account:<name>} or {@code character:<name>}, and one nobody
 * answers to gets the same line in every command. Accounts and characters
 * come from the server's own records, the player list and the rosters,
 * never from the command's words alone and never by asking Mojang. A
 * vanilla selector ({@code @p}) picks among the players online.
 *
 * <p>A command that acts on a player in person, on the character they
 * play, asks {@link #inPerson}: the person found must be online, and a
 * character named must be the one played, each refusal one line of its
 * own. Every other command asks {@link #account} and works whether the
 * person is here or not.</p>
 */
final class LostTalesCommandSubject {
    /** What the lang key of each answer about a name begins with. */
    static final String SAY = "chat.losttales.command.subject.";
    static final String ACCOUNT_PREFIX = "account:";
    static final String CHARACTER_PREFIX = "character:";
    /** What a vanilla selector begins with: {@code @p}, {@code @r}. */
    static final String SELECTOR_PREFIX = "@";
    /** The answer when nobody answers to a name, the same in every command: {@code %s}, the name. */
    static final String UNKNOWN = SAY + "unknown";
    /** The answer when the person found is away and the command needs them here: {@code %s}, who. */
    static final String OFFLINE = SAY + "offline";

    /** The account named, or null. */
    final UUID account;
    /** The character named, or null. */
    final UUID character;
    /** Whose it is: the account named, or the character's owner. */
    final UUID owner;
    /** The name as the answers write it: {@code Aldric (character)}. */
    final IChatComponent label;
    /** The lang key of what went wrong, or null; and its arguments. */
    final String problem;
    final Object[] problemArguments;

    private LostTalesCommandSubject(UUID account, UUID character, UUID owner,
                                    IChatComponent label, String problem,
                                    Object... problemArguments) {
        this.account = account;
        this.character = character;
        this.owner = owner;
        this.label = label;
        this.problem = problem;
        this.problemArguments = problemArguments;
    }

    private static LostTalesCommandSubject found(UUID account, UUID character,
                                                 UUID owner, IChatComponent label) {
        return new LostTalesCommandSubject(account, character, owner, label, null);
    }

    private static LostTalesCommandSubject problem(String key, Object... arguments) {
        return new LostTalesCommandSubject(null, null, null, null, key, arguments);
    }

    /** The player online behind it: the account's, or the character's owner's; null while away. */
    EntityPlayerMP online() {
        return this.owner == null ? null : LostTalesServerPlayers.findOnline(this.owner);
    }

    /**
     * The account a command acts on, here or away: the one {@code typed}
     * names, a character's owner, or the sender when nothing is typed and
     * the sender is a player. A name that finds nobody, or two, is
     * answered with its line and gives null; so is no name from anyone
     * but a player.
     */
    static LostTalesCommandSubject account(ICommandSender sender, String typed) {
        if (typed == null || typed.trim().length() == 0) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP self = (EntityPlayerMP)sender;
                return found(self.getUniqueID(), null, self.getUniqueID(),
                        LostTalesCommandBase.words(SAY + "account",
                                self.getCommandSenderName()));
            }
            LostTalesCommandBase.say(sender, EnumChatFormatting.RED,
                    LostTalesCommandBase.PLAYER_REQUIRED);
            return null;
        }
        LostTalesCommandSubject subject = resolve(sender, typed);
        if (subject.problem != null) {
            LostTalesCommandBase.say(sender, EnumChatFormatting.RED, subject.problem,
                    subject.problemArguments);
            return null;
        }
        return subject;
    }

    /** The same for the name at {@code index} of the arguments, which may be left out. */
    static LostTalesCommandSubject account(ICommandSender sender, String[] args, int index) {
        return account(sender, args != null && args.length > index ? args[index] : null);
    }

    /**
     * The player a command acts on in person, as {@link #account} finds
     * them: they must be online, and a character named must be the one
     * they play. Each refusal is answered with its one line and gives null.
     */
    static EntityPlayerMP inPerson(ICommandSender sender, String typed) {
        LostTalesCommandSubject subject = account(sender, typed);
        if (subject == null) {
            return null;
        }
        EntityPlayerMP player = subject.online();
        if (player == null) {
            LostTalesCommandBase.say(sender, EnumChatFormatting.RED, OFFLINE,
                    subject.label);
            return null;
        }
        if (subject.character != null && !subject.character.equals(playedBy(player))) {
            LostTalesCommandBase.say(sender, EnumChatFormatting.RED, SAY + "not_played",
                    subject.label, player.getCommandSenderName());
            return null;
        }
        return player;
    }

    /** The same for the name at {@code index} of the arguments, which may be left out. */
    static EntityPlayerMP inPerson(ICommandSender sender, String[] args, int index) {
        return inPerson(sender, args != null && args.length > index ? args[index] : null);
    }

    /** The character the player plays; null for none or a roster that cannot be read. */
    private static UUID playedBy(EntityPlayerMP player) {
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            return roster == null ? null : roster.getActiveCharacterId();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** The account or character {@code typed} names, or the problem with it. */
    static LostTalesCommandSubject resolve(final ICommandSender sender, String typed) {
        String name = typed == null ? "" : typed.trim();
        if (name.startsWith(SELECTOR_PREFIX)) {
            return selected(sender, name);
        }
        return decide(name, new Lookup() {
            @Override
            public UUID account(String named) {
                return accountNamed(sender, named);
            }

            @Override
            public RoleplayCharacter character(String named) {
                return LostTalesCommandSubject.character(sender, named);
            }
        });
    }

    /** Where a name is looked up: the world's accounts and its rosters. */
    interface Lookup {
        /** The account an id or a name names; null for none. */
        UUID account(String name);

        /** The one character that goes by the name; null for none or two. */
        RoleplayCharacter character(String name);
    }

    /**
     * The rule itself, once the lookups are given: the prefixes, a name
     * both answer to, a name nobody answers to. Kept apart so it can be
     * checked without a world.
     */
    static LostTalesCommandSubject decide(String typed, Lookup lookup) {
        String name = typed == null ? "" : typed.trim();
        boolean accountOnly = false;
        boolean characterOnly = false;
        if (name.toLowerCase(Locale.ROOT).startsWith(ACCOUNT_PREFIX)) {
            accountOnly = true;
            name = name.substring(ACCOUNT_PREFIX.length()).trim();
        } else if (name.toLowerCase(Locale.ROOT).startsWith(CHARACTER_PREFIX)) {
            characterOnly = true;
            name = name.substring(CHARACTER_PREFIX.length()).trim();
        }
        if (name.length() == 0) {
            return problem(SAY + "empty");
        }
        UUID account = characterOnly ? null : lookup.account(name);
        RoleplayCharacter character = accountOnly ? null : lookup.character(name);
        if (account != null && character != null) {
            return problem(SAY + "both", name, ACCOUNT_PREFIX + name,
                    CHARACTER_PREFIX + name);
        }
        if (character != null) {
            return found(null, character.getCharacterId(), character.getOwnerId(),
                    LostTalesCommandBase.words(SAY + "character", character.getName()));
        }
        if (account != null) {
            return found(account, null, account,
                    LostTalesCommandBase.words(SAY + "account", name));
        }
        return problem(UNKNOWN, name);
    }

    /**
     * The character of that name on any roster, played or not, as a
     * waystone share finds one ({@link CharacterNames#named}); two
     * characters holding the name make it nobody's, since nothing can be
     * given to half a name. A store that cannot be read names nobody.
     */
    private static RoleplayCharacter character(ICommandSender sender, String name) {
        if (sender == null || sender.getEntityWorld() == null) {
            return null;
        }
        try {
            return CharacterNames.named(
                    CharacterStorage.get(sender.getEntityWorld()).getRosters(), name);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** The player a vanilla selector picks among those online, as an account; or why not. */
    private static LostTalesCommandSubject selected(ICommandSender sender, String selector) {
        EntityPlayerMP player;
        try {
            player = PlayerSelector.matchOnePlayer(sender, selector);
        } catch (RuntimeException unmatched) {
            player = null;
        }
        if (player == null) {
            return problem(UNKNOWN, selector);
        }
        return found(player.getUniqueID(), null, player.getUniqueID(),
                LostTalesCommandBase.words(SAY + "account", player.getCommandSenderName()));
    }

    /**
     * The account an id or a name names: a player online, or one this
     * world knows by that name. Mojang is never asked, so a name nobody
     * here has used names nobody.
     */
    private static UUID accountNamed(ICommandSender sender, String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException notAnId) {
            return KnownAccounts.find(
                    sender == null ? null : sender.getEntityWorld(), name);
        }
    }

    /** Every character name on every roster and every player online, for completion. */
    static String[] names(ICommandSender sender) {
        List<String> names = new ArrayList<String>();
        if (sender != null && sender.getEntityWorld() != null) {
            try {
                for (CharacterRoster roster
                        : CharacterStorage.get(sender.getEntityWorld()).getRosters()) {
                    if (roster == null) {
                        continue;
                    }
                    for (RoleplayCharacter character : roster.getCharacters()) {
                        if (character != null && character.getName().trim().length() > 0) {
                            names.add(character.getName());
                        }
                    }
                }
            } catch (RuntimeException unreadable) {
                // Nothing to complete from a store that cannot be read.
            }
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            names.addAll(Arrays.asList(server.getAllUsernames()));
        }
        return names.toArray(new String[names.size()]);
    }

    /** The words from {@code start} on, joined by spaces: a name may hold them. */
    static String joinFrom(String[] args, int start) {
        StringBuilder joined = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(args[index]);
        }
        return joined.toString();
    }
}
