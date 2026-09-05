package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;

/**
 * The sub-commands of {@code /losttales}, each with the names it answers
 * to and the usage line the root prints for it. Adding a sub-command is
 * one constant here: the root dispatches, completes and documents from
 * this list, and the sub-command's own permission level is checked
 * before it runs.
 */
public enum ELostTalesSubCommand {
    QUEST(new LostTalesCommandQuest("quest", LostTalesMetaData.MOD_ID + " quest"),
            "quest <defs|list|start|complete|reset|abandon|pin|unpin|starter|scan>",
            "quest", "quests", "q"),
    MAP_MARKER(new LostTalesCommandMapMarker("mapmarker",
            LostTalesMetaData.MOD_ID + " mapmarker"),
            "mapmarker <known|list|discover|forget|track|untrack|retry|reseed>",
            "mapmarker", "mapmarkers", "marker", "markers"),
    HUD(new LostTalesCommandHud("hud", LostTalesMetaData.MOD_ID + " hud"),
            "hud <status|preset|set|move|toggle>",
            "hud", "overlay"),
    SUMMON(new LostTalesCommandSummon("summon"),
            "summon <entity> [x] [y] [z] [dataTag]",
            "summon", "entity"),
    PARTY(new LostTalesCommandPartyAdmin(),
            "party <status|validate|repair|clearcombat>",
            "party", "parties"),
    CHARACTER(new LostTalesCommandCharacterAdmin(),
            "character <status|recover|cooldown|freeze|unfreeze|deleted|restore|rollback|purge> ...",
            "character", "characters", "char"),
    CHAT(new LostTalesCommandChatModeration(),
            "chat <mute|unmute|mutes>",
            "chat"),
    CONFIG(new LostTalesCommandConfig(),
            "config <list|get|set|reload> ...",
            "config", "cfg"),
    DISCORD(new LostTalesCommandDiscord(),
            "discord <list|bind|unbind|reload> ...",
            "discord"),
    ROLE(new LostTalesCommandRole(),
            "role <list|assign|unassign|create|edit|delete> ...",
            "role", "roles");

    private final CommandBase command;
    private final String usage;
    private final String[] names;

    ELostTalesSubCommand(CommandBase command, String usage, String... names) {
        this.command = command;
        this.usage = usage;
        this.names = names;
    }

    public CommandBase getCommand() {
        return this.command;
    }

    /** The arguments the root's usage lists after the sub-command's name. */
    public String getUsage() {
        return this.usage;
    }

    /** The name the sub-command is listed and completed under. */
    public String getPrimaryName() {
        return this.names[0];
    }

    /** The sub-command one of these names asks for, or null. */
    public static ELostTalesSubCommand byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        for (ELostTalesSubCommand candidate : values()) {
            for (String alias : candidate.names) {
                if (alias.equals(wanted)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Every primary name, in declaration order, for tab completion. */
    public static String[] primaryNames() {
        List<String> names = new ArrayList<String>(values().length);
        for (ELostTalesSubCommand candidate : values()) {
            names.add(candidate.getPrimaryName());
        }
        return names.toArray(new String[names.size()]);
    }
}
