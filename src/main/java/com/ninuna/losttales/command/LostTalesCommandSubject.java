package com.ninuna.losttales.command;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.KnownAccounts;
import com.ninuna.losttales.character.storage.CharacterStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;

/**
 * Whom a command's name names, an account or a character, read alike by
 * every command that can reach either: a role, a structure ban. The name
 * is looked up as both; one both answer to is refused until it is given as
 * {@code account:<name>} or {@code character:<name>}. Accounts and
 * characters come from the server's own records, the player list and the
 * rosters, never from the command's words alone and never by asking
 * Mojang.
 */
final class LostTalesCommandSubject {
    /** What the lang key of each answer about a name begins with. */
    static final String SAY = "chat.losttales.command.subject.";
    static final String ACCOUNT_PREFIX = "account:";
    static final String CHARACTER_PREFIX = "character:";

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

    /** The account or character {@code typed} names, or the problem with it. */
    static LostTalesCommandSubject resolve(ICommandSender sender, String typed) {
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
        UUID account = characterOnly ? null : account(sender, name);
        RoleplayCharacter character = accountOnly ? null : character(sender, name);
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
        return problem(SAY + "unknown", name);
    }

    /**
     * The character of that name on any roster, or null; two rosters
     * holding the name make it nobody's, since nothing can be given to
     * half a name. A store that cannot be read names nobody.
     */
    private static RoleplayCharacter character(ICommandSender sender, String name) {
        if (sender == null || sender.getEntityWorld() == null) {
            return null;
        }
        RoleplayCharacter found = null;
        try {
            for (CharacterRoster roster
                    : CharacterStorage.get(sender.getEntityWorld()).getRosters()) {
                if (roster == null) {
                    continue;
                }
                for (RoleplayCharacter character : roster.getCharacters()) {
                    if (character == null || !name.equalsIgnoreCase(character.getName())) {
                        continue;
                    }
                    if (found != null) {
                        return null;
                    }
                    found = character;
                }
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return found;
    }

    /**
     * The account an id or a name names: a player online, or one this
     * world knows by that name. Mojang is never asked, so a name nobody
     * here has used names nobody.
     */
    private static UUID account(ICommandSender sender, String name) {
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
