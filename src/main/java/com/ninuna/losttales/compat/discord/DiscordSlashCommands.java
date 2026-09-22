package com.ninuna.losttales.compat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
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
 * chat uses, and formatted here so the wording can be checked without
 * a server; a reply is ephemeral, so the channel stays clean. They are
 * answered only in a Discord channel linked to the game: a Discord
 * server that merely has the bot in it learns nothing of who plays.
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

    public static final String LINK_NEEDS_SERVER =
            "Use this in a channel of your Discord server.";
    public static final String LINK_NEEDS_PERMISSION =
            "You need the Manage Webhooks permission in this channel.";
    public static final String LINK_UNKNOWN_CODE =
            "That code is unknown or has run out. Ask for a new one in the game"
                    + " with `/losttales discord link <channel>`.";
    public static final String LINK_BOT_NEEDS_PERMISSION =
            "I need the Manage Webhooks permission in this channel to link it.";
    public static final String LINK_NOT_SAVED =
            "The link could not be saved on the game server. Ask for a new code.";
    /** Discord's own bound on a message's content. */
    private static final int MAX_CONTENT_LENGTH = 2000;
    /** The answer in a Discord channel that is not linked to the game. */
    public static final String NOT_LINKED =
            "This channel is not linked to the game.";

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
    public static String definitionsBody() {
        JsonArray commands = new JsonArray();
        commands.add(command(ONLINE, "Who is playing on the server right now"));
        JsonObject who = command(WHO, "About a player or a character on the server");
        JsonObject name = new JsonObject();
        name.addProperty("type", Integer.valueOf(3));
        name.addProperty("name", "name");
        name.addProperty("description", "An account or character name");
        name.addProperty("required", Boolean.TRUE);
        JsonArray options = new JsonArray();
        options.add(name);
        who.add("options", options);
        commands.add(who);
        commands.add(command(SERVER, "How the server is doing"));
        JsonObject link = command(LINK, "Link this channel to a game channel");
        JsonObject code = new JsonObject();
        code.addProperty("type", Integer.valueOf(3));
        code.addProperty("name", CODE);
        code.addProperty("description", "The code the game gave you");
        code.addProperty("required", Boolean.TRUE);
        JsonArray linkOptions = new JsonArray();
        linkOptions.add(code);
        link.add("options", linkOptions);
        link.addProperty("default_member_permissions", MANAGE_WEBHOOKS_PERMISSION);
        commands.add(link);
        JsonObject unlink = command(UNLINK, "Take this channel's link to the game away");
        unlink.addProperty("default_member_permissions", MANAGE_WEBHOOKS_PERMISSION);
        commands.add(unlink);
        return commands.toString();
    }

    /** This channel is linked to another game channel already. */
    public static String linkTaken(String gameChannel) {
        return bound("This channel is already linked to **" + escape(gameChannel)
                + "**. A Discord channel holds one game channel: use `/unlink` first.");
    }

    /** This channel is linked to the very game channel asked for. */
    public static String linkAlready(String gameChannel) {
        return bound("This channel is already linked to **" + escape(gameChannel) + "**.");
    }

    /** Discord would not make the webhook; {@code status} 0 for no answer. */
    public static String linkFailed(int status) {
        return status > 0
                ? "Discord did not make the webhook (HTTP " + status + "). Ask for a new code."
                : "Discord could not be reached. Ask for a new code.";
    }

    /** The link stands, and which way lines cross it. */
    public static String linked(String gameChannel, DiscordBridgeDirection direction) {
        String crossing = direction == DiscordBridgeDirection.GAME_TO_DISCORD
                ? "Lines from the game come here."
                : direction == DiscordBridgeDirection.DISCORD_TO_GAME
                        ? "Messages here go to the game."
                        : "Messages cross both ways.";
        return bound("Linked to **" + escape(gameChannel) + "**. " + crossing);
    }

    /** The link is gone. */
    public static String unlinked(String gameChannel) {
        return bound("Unlinked from **" + escape(gameChannel) + "**.");
    }

    private static JsonObject command(String name, String description) {
        JsonObject command = new JsonObject();
        command.addProperty("name", name);
        command.addProperty("description", description);
        command.addProperty("type", Integer.valueOf(1));
        return command;
    }

    /** The answer to a command, from the live server. Server thread. */
    public static String answer(String command, Map<String, String> options,
                                long serverStartedMillis) {
        MinecraftServer server = MinecraftServer.getServer();
        List<Player> players = server == null ? Collections.<Player>emptyList()
                : onlinePlayers(server);
        String name = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (ONLINE.equals(name)) {
            return online(players);
        }
        if (WHO.equals(name)) {
            String wanted = options == null ? null : options.get("name");
            return who(players, wanted);
        }
        if (SERVER.equals(name)) {
            return serverStatus(players.size(), server == null ? 0 : server.getMaxPlayers(),
                    serverStartedMillis, System.currentTimeMillis());
        }
        return "";
    }

    /** {@code **3 online:** Steve (as Aragorn), Alex, Bob (as Boromir)} */
    public static String online(List<Player> players) {
        if (players.isEmpty()) {
            return "**Nobody is online.**";
        }
        List<Player> sorted = new ArrayList<Player>(players);
        Collections.sort(sorted, new Comparator<Player>() {
            @Override
            public int compare(Player left, Player right) {
                return left.account.compareToIgnoreCase(right.account);
            }
        });
        StringBuilder text = new StringBuilder("**").append(sorted.size())
                .append(sorted.size() == 1 ? " online:** " : " online:** ");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            Player player = sorted.get(index);
            text.append(escape(player.account));
            if (player.character.length() > 0) {
                text.append(" (as ").append(escape(player.character)).append(')');
            }
        }
        return bound(text.toString());
    }

    /** The one player or character named, or that nobody online is. */
    public static String who(List<Player> players, String wanted) {
        String query = wanted == null ? "" : wanted.trim();
        if (query.length() == 0) {
            return "Say whom: `/who name`.";
        }
        for (Player player : players) {
            if (query.equalsIgnoreCase(player.account)
                    || (player.character.length() > 0
                            && query.equalsIgnoreCase(player.character))) {
                return describe(player);
            }
        }
        return "Nobody online is called **" + escape(query) + "**.";
    }

    private static String describe(Player player) {
        if (player.character.length() == 0) {
            return "**" + escape(player.account) + "** is online, playing as themselves.";
        }
        StringBuilder text = new StringBuilder("**").append(escape(player.character))
                .append("** — ").append(escape(player.account)).append("'s character");
        List<String> details = new ArrayList<String>();
        if (player.race.length() > 0) {
            details.add(player.race);
        }
        if (player.faction.length() > 0) {
            details.add("of " + player.faction);
        }
        if (!details.isEmpty()) {
            text.append(": ");
            for (int index = 0; index < details.size(); index++) {
                if (index > 0) {
                    text.append(", ");
                }
                text.append(escape(details.get(index)));
            }
        }
        return bound(text.append('.').toString());
    }

    /** {@code Lost Tales 0.1.3 • 3/20 players • up 2h 15m} */
    public static String serverStatus(int players, int maxPlayers, long startedMillis,
                                      long nowMillis) {
        StringBuilder text = new StringBuilder(LostTalesMetaData.MOD_NAME)
                .append(" ").append(LostTalesMetaData.MOD_VERSION)
                .append(" • ").append(players);
        if (maxPlayers > 0) {
            text.append('/').append(maxPlayers);
        }
        text.append(players == 1 && maxPlayers <= 0 ? " player" : " players");
        if (startedMillis > 0L && nowMillis >= startedMillis) {
            text.append(" • up ").append(uptime(nowMillis - startedMillis));
        }
        return bound(text.toString());
    }

    /** {@code 2d 3h}, {@code 2h 15m}, {@code 15m}, {@code 40s}. */
    static String uptime(long millis) {
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return seconds + "s";
    }

    private static List<Player> onlinePlayers(MinecraftServer server) {
        List<Player> players = new ArrayList<Player>();
        if (server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return players;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player == null) {
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
                    .getFactionDisplayName(character.getStartingFactionId());
            players.add(new Player(player.getCommandSenderName(), character.getName(),
                    raceName(character.getRaceId()), faction == null ? "" : faction));
        }
        return players;
    }

    /** {@code losttales:half_troll} reads as {@code Half troll}. */
    static String raceName(String raceId) {
        if (raceId == null || raceId.length() == 0) {
            return "";
        }
        String name = raceId.substring(raceId.indexOf(':') + 1).replace('_', ' ');
        return name.length() == 0 ? "" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Markdown and mentions rendered inert. */
    static String escape(String text) {
        return DiscordMessageSanitizer.escapeMarkdown(text == null ? "" : text);
    }

    private static String bound(String text) {
        return text.length() <= MAX_CONTENT_LENGTH ? text
                : text.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
    }
}
