package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.chat.server.ChatPresenceService;
import com.ninuna.losttales.chat.server.ChatServerStatus;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.util.LostTalesWords;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The bot's slash commands: what they are, as Discord is told for each
 * server the bot is in, and what they answer. {@code /online} lists who
 * is playing and as whom, {@code /who} describes one player or
 * character, {@code /server} says how the server is doing. {@code /link}
 * pairs the channel it is used in with a game channel by a code an
 * operator asked for in the game, and {@code /unlink} takes the channel's
 * link away; both are offered only to members who may manage webhooks,
 * and the bridge checks that permission again itself. The answers
 * are built on the server thread from the same identity resolution the
 * chat uses; they and the descriptions Discord shows are the lang file's
 * words in the server's language, put together here so the wording can
 * be checked without a server. A reply is ephemeral, so the channel
 * stays clean. They are answered only in a Discord channel linked to the
 * game: a Discord server that merely has the bot in it learns nothing of
 * who plays.
 */
public final class DiscordSlashCommands {

    public static final String ONLINE = "online";
    public static final String WHO = "who";
    public static final String SERVER = "server";
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    /** The option {@code /link} takes the code in. */
    public static final String CODE = "code";
    /** Manage Webhooks as Discord writes a permission: the bitfield in decimal. */
    private static final String MANAGE_WEBHOOKS_PERMISSION =
            String.valueOf(1L << DiscordJson.Interaction.MANAGE_WEBHOOKS);
    /** Discord's own bound on a message's content. */
    private static final int MAX_CONTENT_LENGTH = 2000;
    /** Discord's own bound on a command's or an option's description. */
    private static final int MAX_DESCRIPTION_LENGTH = 100;

    private DiscordSlashCommands() {}

    /** One online player as a command sees them. */
    public static final class Player {
        public final String account;
        /** The character being played; empty on the account. */
        public final String character;
        public final String race;
        public final String faction;

        public Player(String account, String character, String race, String faction) {
            this.account = account == null ? "" : account;
            this.character = character == null ? "" : character;
            this.race = race == null ? "" : race;
            this.faction = faction == null ? "" : faction;
        }
    }

    /** The command definitions Discord registers, as the API's JSON array. */
    public static String definitionsBody(LostTalesWords words) {
        JsonArray commands = new JsonArray();
        commands.add(command(ONLINE, description(words,
                "chat.losttales.discord.command.online")));
        JsonObject who = command(WHO, description(words,
                "chat.losttales.discord.command.who"));
        JsonObject name = new JsonObject();
        name.addProperty("type", Integer.valueOf(3));
        name.addProperty("name", "name");
        name.addProperty("description", description(words,
                "chat.losttales.discord.command.who.name"));
        name.addProperty("required", Boolean.TRUE);
        JsonArray options = new JsonArray();
        options.add(name);
        who.add("options", options);
        commands.add(who);
        commands.add(command(SERVER, description(words,
                "chat.losttales.discord.command.server")));
        JsonObject link = command(LINK, description(words,
                "chat.losttales.discord.command.link"));
        JsonObject code = new JsonObject();
        code.addProperty("type", Integer.valueOf(3));
        code.addProperty("name", CODE);
        code.addProperty("description", description(words,
                "chat.losttales.discord.command.link.code"));
        code.addProperty("required", Boolean.TRUE);
        JsonArray linkOptions = new JsonArray();
        linkOptions.add(code);
        link.add("options", linkOptions);
        link.addProperty("default_member_permissions", MANAGE_WEBHOOKS_PERMISSION);
        commands.add(link);
        JsonObject unlink = command(UNLINK, description(words,
                "chat.losttales.discord.command.unlink"));
        unlink.addProperty("default_member_permissions", MANAGE_WEBHOOKS_PERMISSION);
        commands.add(unlink);
        return commands.toString();
    }

    /**
     * A command's or an option's description, cut to what Discord takes:
     * one description past it and Discord refuses every command.
     */
    private static String description(LostTalesWords words, String key) {
        return cut(words.format(key), MAX_DESCRIPTION_LENGTH);
    }

    /** The answer in a Discord channel that is not linked to the game. */
    public static String notLinked(LostTalesWords words) {
        return words.format("chat.losttales.discord.not_linked");
    }

    /** {@code /link} used anywhere but a channel of a Discord server. */
    public static String linkNeedsServer(LostTalesWords words) {
        return words.format("chat.losttales.discord.link.needs_server");
    }

    /** {@code /link} or {@code /unlink} by a member who may not manage the channel's webhooks. */
    public static String linkNeedsPermission(LostTalesWords words) {
        return words.format("chat.losttales.discord.link.needs_permission");
    }

    /** The code is unknown or has run out, and where a new one comes from. */
    public static String linkUnknownCode(LostTalesWords words) {
        return sentences(words.format("chat.losttales.discord.link.unknown_code"),
                words.format("chat.losttales.discord.link.ask_in_game"));
    }

    /** Discord would not let the bot make the channel's webhook. */
    public static String linkBotNeedsPermission(LostTalesWords words) {
        return words.format("chat.losttales.discord.link.bot_needs_permission");
    }

    /** The game server could not save the link, or the unlink. */
    public static String linkNotSaved(LostTalesWords words) {
        return sentences(words.format("chat.losttales.discord.link.not_saved"),
                words.format("chat.losttales.discord.link.ask_again"));
    }

    /** This channel is linked to another game channel already. */
    public static String linkTaken(LostTalesWords words, String gameChannel) {
        return bound(sentences(linkAlready(words, gameChannel),
                words.format("chat.losttales.discord.link.one_channel")));
    }

    /** This channel is linked to the very game channel asked for. */
    public static String linkAlready(LostTalesWords words, String gameChannel) {
        return bound(words.format("chat.losttales.discord.link.already",
                escape(gameChannel)));
    }

    /** Discord would not make the webhook; {@code status} 0 for no answer. */
    public static String linkFailed(LostTalesWords words, int status) {
        return sentences(status > 0
                        ? words.format("chat.losttales.discord.link.failed",
                                Integer.valueOf(status))
                        : words.format("chat.losttales.discord.link.unreachable"),
                words.format("chat.losttales.discord.link.ask_again"));
    }

    /**
     * The link stands, and which way lines cross it; for a channel only
     * some players read in the game, that everyone who can see this
     * Discord channel reads it here.
     */
    public static String linked(LostTalesWords words, String gameChannel,
                                DiscordBridgeDirection direction,
                                boolean limitedInGame) {
        String name = escape(gameChannel);
        String crossing = direction == DiscordBridgeDirection.GAME_TO_DISCORD
                ? "chat.losttales.discord.link.to_discord"
                : direction == DiscordBridgeDirection.DISCORD_TO_GAME
                        ? "chat.losttales.discord.link.to_game"
                        : "chat.losttales.discord.link.both_ways";
        return bound(sentences(
                words.format("chat.losttales.discord.link.linked", name),
                words.format(crossing),
                limitedInGame
                        ? words.format("chat.losttales.discord.link.limited", name)
                        : ""));
    }

    /** The link is gone. */
    public static String unlinked(LostTalesWords words, String gameChannel) {
        return bound(words.format("chat.losttales.discord.link.unlinked",
                escape(gameChannel)));
    }

    private static JsonObject command(String name, String description) {
        JsonObject command = new JsonObject();
        command.addProperty("name", name);
        command.addProperty("description", description);
        command.addProperty("type", Integer.valueOf(1));
        return command;
    }

    /** The answer to a command, from the live server. Server thread. */
    public static String answer(LostTalesWords words, String command,
                                Map<String, String> options) {
        MinecraftServer server = MinecraftServer.getServer();
        List<Player> players = server == null ? Collections.<Player>emptyList()
                : onlinePlayers(words, server);
        String name = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (ONLINE.equals(name)) {
            return online(words, players);
        }
        if (WHO.equals(name)) {
            String wanted = options == null ? null : options.get("name");
            return who(words, players, wanted);
        }
        if (SERVER.equals(name)) {
            return serverStatus(ChatServerStatus.parts());
        }
        return "";
    }

    /** {@code **3 online:** Steve (as Aragorn), Alex, Bob (as Boromir)} */
    public static String online(LostTalesWords words, List<Player> players) {
        if (players.isEmpty()) {
            return words.format("chat.losttales.discord.online.nobody");
        }
        List<Player> sorted = new ArrayList<Player>(players);
        Collections.sort(sorted, new Comparator<Player>() {
            @Override
            public int compare(Player left, Player right) {
                return left.account.compareToIgnoreCase(right.account);
            }
        });
        StringBuilder list = new StringBuilder();
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                list.append(", ");
            }
            Player player = sorted.get(index);
            list.append(player.character.length() > 0
                    ? words.format("chat.losttales.discord.online.playing_as",
                            escape(player.account), escape(player.character))
                    : escape(player.account));
        }
        return bound(words.format("chat.losttales.discord.online.list",
                Integer.valueOf(sorted.size()), list.toString()));
    }

    /** The one player or character named, or that nobody online is. */
    public static String who(LostTalesWords words, List<Player> players,
                             String wanted) {
        String query = wanted == null ? "" : wanted.trim();
        if (query.length() == 0) {
            return words.format("chat.losttales.discord.who.ask");
        }
        for (Player player : players) {
            if (query.equalsIgnoreCase(player.account)
                    || (player.character.length() > 0
                            && query.equalsIgnoreCase(player.character))) {
                return describe(words, player);
            }
        }
        // The name is the asker's own words, as long as Discord lets an
        // option be, and escaping can double it.
        return bound(words.format("chat.losttales.discord.who.nobody",
                escape(query)));
    }

    private static String describe(LostTalesWords words, Player player) {
        if (player.character.length() == 0) {
            return bound(words.format("chat.losttales.discord.who.account",
                    escape(player.account)));
        }
        StringBuilder details = new StringBuilder();
        if (player.race.length() > 0) {
            details.append(escape(player.race));
        }
        if (player.faction.length() > 0) {
            if (details.length() > 0) {
                details.append(", ");
            }
            details.append(words.format("chat.losttales.discord.who.faction",
                    escape(player.faction)));
        }
        return bound(details.length() == 0
                ? words.format("chat.losttales.discord.who.character",
                        escape(player.character), escape(player.account))
                : words.format("chat.losttales.discord.who.character_of",
                        escape(player.character), escape(player.account),
                        details.toString()));
    }

    /**
     * {@code Lost Tales 0.1.3 • 3/20 players • play.example.org • 20 TPS •
     * up 2h 15m}: the mod and the server's status, as the topic and the
     * game's member lists say it ({@link ChatServerStatus}).
     */
    public static String serverStatus(List<String> status) {
        StringBuilder text = new StringBuilder(LostTalesMetaData.MOD_NAME)
                .append(" ").append(LostTalesMetaData.MOD_VERSION);
        for (String part : status) {
            text.append(" • ").append(escape(part));
        }
        return bound(text.toString());
    }

    private static List<Player> onlinePlayers(LostTalesWords words,
                                              MinecraftServer server) {
        List<Player> players = new ArrayList<Player>();
        if (server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return players;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            // An Invisible player is nobody Discord is told of.
            if (player == null || !ChatPresenceService.showsOnline(player)) {
                continue;
            }
            PlayableIdentityResolver.Resolution resolution =
                    PlayableIdentityResolver.resolve(player);
            RoleplayCharacter character = resolution.isAvailable()
                    ? resolution.getCharacter() : null;
            if (character == null) {
                players.add(new Player(player.getCommandSenderName(), "", "", ""));
                continue;
            }
            String faction = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(character.getFactionId());
            players.add(new Player(player.getCommandSenderName(), character.getName(),
                    raceName(words, character.getRaceId()),
                    faction == null ? "" : faction));
        }
        return players;
    }

    /**
     * A race by the name the game gives it: {@code losttales:half_troll}
     * reads as {@code Half-troll}. One the lang file does not name reads
     * as its id, {@code Half troll}.
     */
    static String raceName(LostTalesWords words, String raceId) {
        String path = raceId == null ? ""
                : raceId.substring(raceId.indexOf(':') + 1);
        if (path.length() == 0) {
            return "";
        }
        String key = "gui.losttales.character.race." + path;
        String named = words.format(key);
        if (!key.equals(named)) {
            return named;
        }
        String name = path.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Markdown and mentions rendered inert. */
    static String escape(String text) {
        return DiscordMessageSanitizer.escapeMarkdown(text == null ? "" : text);
    }

    /** Sentences one after another, a space between each two; an empty one is left out. */
    private static String sentences(String... said) {
        StringBuilder text = new StringBuilder();
        for (String sentence : said) {
            if (sentence != null && sentence.length() > 0) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(sentence);
            }
        }
        return text.toString();
    }

    /**
     * An answer cut to Discord's {@link #MAX_CONTENT_LENGTH} characters,
     * never inside a surrogate pair. Every answer the bridge sends goes
     * through here.
     */
    static String bound(String text) {
        return cut(text, MAX_CONTENT_LENGTH);
    }

    /** {@code text} cut to {@code max} characters, ending on "...", never inside a surrogate pair. */
    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = max - 3;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }
}
