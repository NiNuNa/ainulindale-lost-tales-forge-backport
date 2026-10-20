package com.ninuna.losttales.command;

import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.ProgressionRules;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.ProgressionSettings;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.ProgressionSync;
import com.ninuna.losttales.progression.SkillStanding;
import com.ninuna.losttales.progression.SkillWords;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

/**
 * The progression's operator tools (PROG-01, PROG-11): {@code reload}
 * reads the skill and deed files again and sends every client the new
 * catalog (the race files, starts included, are read as the server
 * starts, CHAR-38); {@code show <player>} says where a played character
 * stands; {@code give <player> <skill> <experience>} gives a character
 * experience, as a quest would, for an event or a test.
 */
public final class LostTalesCommandSkills extends LostTalesCommandBase {

    static final String SAY = "chat.losttales.command.skills.";
    private static final int MAX_PROBLEMS_SAID = 64;
    private static final int MAX_GIFT = 1000000;

    public LostTalesCommandSkills() {
        super("skills");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales skills <reload|show|give> ...";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.PROGRESSION_ADMIN;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0 || sender.getEntityWorld() == null
                || sender.getEntityWorld().isRemote) {
            sendUsage(sender);
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(action)) {
            reload(sender);
        } else if ("show".equals(action) && args.length == 2) {
            show(sender, args[1]);
        } else if ("give".equals(action) && args.length == 4) {
            give(sender, args[1], args[2], args[3]);
        } else {
            sendUsage(sender);
        }
    }

    private void reload(ICommandSender sender) {
        List<String> problems = ProgressionCatalog.load();
        int said = 0;
        for (String problem : problems) {
            if (said++ >= MAX_PROBLEMS_SAID) {
                break;
            }
            say(sender, EnumChatFormatting.YELLOW, SAY + "reload.left_out", problem);
        }
        say(sender, EnumChatFormatting.GREEN, SAY + "reload.done",
                Integer.valueOf(ProgressionCatalog.skills().size()),
                Integer.valueOf(ProgressionCatalog.deeds().size()));
        ProgressionSync.sendCatalogToAll(MinecraftServer.getServer());
    }

    private void show(ICommandSender sender, String name) {
        EntityPlayerMP player = online(sender, name);
        if (player == null) {
            return;
        }
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || ProgressionService.playedCharacter(player) == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_character", name);
            return;
        }
        ProgressionState state = data.state();
        say(sender, EnumChatFormatting.GOLD, SAY + "show.head", name,
                Integer.valueOf(state.getCharacterLevel()),
                Integer.valueOf(ProgressionRules.availablePoints(state,
                        ProgressionSettings.pointsPerLevel(), ProgressionCatalog.skillsById())),
                Integer.valueOf(state.deeds().size()));
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            SkillStanding standing = state.skill(skill.getId());
            say(sender, EnumChatFormatting.GRAY, SAY + "show.skill", SkillWords.name(skill),
                    Integer.valueOf(standing.getLevel()),
                    Long.valueOf(Math.round(standing.getInto())),
                    Long.valueOf(Math.round(ProgressionSettings.curve().needed(standing.getLevel()))));
        }
    }

    private void give(ICommandSender sender, String name, String skillId, String written) {
        EntityPlayerMP player = online(sender, name);
        if (player == null) {
            return;
        }
        SkillDefinition skill = ProgressionCatalog.skill(skillId);
        if (skill == null) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown_skill", skillId);
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(written);
        } catch (NumberFormatException notANumber) {
            amount = -1;
        }
        if (amount < 1 || amount > MAX_GIFT) {
            say(sender, EnumChatFormatting.RED, SAY + "give.amount", written,
                    Integer.valueOf(MAX_GIFT));
            return;
        }
        if (ProgressionService.playedCharacter(player) == null) {
            say(sender, EnumChatFormatting.RED, SAY + "no_character", name);
            return;
        }
        double gained = ProgressionService.reward(player, skill.getId(), amount);
        say(sender, EnumChatFormatting.GREEN, SAY + "give.done", name,
                Long.valueOf(Math.round(gained)), SkillWords.name(skill));
    }

    private EntityPlayerMP online(ICommandSender sender, String name) {
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP player = server == null ? null
                : server.getConfigurationManager().func_152612_a(name);
        if (player == null) {
            say(sender, EnumChatFormatting.RED, PLAYER_UNKNOWN, name);
        }
        return player;
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, "/losttales skills reload");
        usage(sender, "/losttales skills show <player>");
        usage(sender, "/losttales skills give <player> <skill> <experience>");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "show", "give");
        }
        if (args.length == 2 && ("show".equalsIgnoreCase(args[0])
                || "give".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args,
                    MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (SkillDefinition skill : ProgressionCatalog.skills()) {
                ids.add(skill.getId());
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        return null;
    }
}
