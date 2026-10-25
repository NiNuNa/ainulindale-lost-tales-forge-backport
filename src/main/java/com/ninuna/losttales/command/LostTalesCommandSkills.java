package com.ninuna.losttales.command;

import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.ProgressionRules;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.ProgressionSettings;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillStanding;
import com.ninuna.losttales.progression.SkillWords;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

/**
 * The progression's operator tools (PROG-01, PROG-11): {@code show
 * <player>} says where a played character stands; {@code give <player>
 * <skill> <experience>} gives a character experience, as a quest would,
 * for an event or a test. The skill and deed files are read again by
 * {@code /losttales reload skills}.
 */
public final class LostTalesCommandSkills extends LostTalesCommandBase {

    static final String SAY = "chat.losttales.command.skills.";
    private static final int MAX_GIFT = 1000000;

    public LostTalesCommandSkills() {
        super("skills");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales skills <show|give> ...";
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
        if ("show".equals(action) && args.length == 2) {
            show(sender, args[1]);
        } else if ("give".equals(action) && args.length == 4) {
            give(sender, args[1], args[2], args[3]);
        } else {
            sendUsage(sender);
        }
    }

    private void show(ICommandSender sender, String typed) {
        EntityPlayerMP player = LostTalesCommandSubject.inPerson(sender, typed);
        if (player == null) {
            return;
        }
        String name = player.getCommandSenderName();
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

    private void give(ICommandSender sender, String typed, String skillId, String written) {
        EntityPlayerMP player = LostTalesCommandSubject.inPerson(sender, typed);
        if (player == null) {
            return;
        }
        String name = player.getCommandSenderName();
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
        double gained = ProgressionService.give(player, skill.getId(), amount);
        say(sender, EnumChatFormatting.GREEN, SAY + "give.done", name,
                Long.valueOf(Math.round(gained)), SkillWords.name(skill));
    }

    private void sendUsage(ICommandSender sender) {
        usage(sender, "/losttales skills show <player>");
        usage(sender, "/losttales skills give <player> <skill> <experience>");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "show", "give");
        }
        if (args.length == 2 && ("show".equalsIgnoreCase(args[0])
                || "give".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args,
                    LostTalesCommandSubject.names(sender));
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
