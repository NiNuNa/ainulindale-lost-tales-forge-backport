package com.ninuna.losttales.progression;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.progression.perk.Perks;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The server's hand on a character's growth: experience a skill gains by
 * what its player does, a quest pays or an operator gives (PROG-01 to
 * PROG-04, PROG-08), the LOTR waypoints it reached, and the perks a
 * player takes (PROG-06). Only a played character grows; a ghost, a fake
 * player and a save that cannot be read change nothing. What a skill
 * gains is never shared (PROG-09) and a death takes none of it (PROG-10).
 */
public final class ProgressionService {
    private static final Map<UUID, RepeatMemory> MEMORIES = new HashMap<UUID, RepeatMemory>();

    private ProgressionService() {}

    /** Forgets what every character repeated; as the server starts and stops. */
    public static synchronized void clear() {
        MEMORIES.clear();
    }

    /** The played character, or null for a ghost, a fake player or an unreadable store. */
    public static RoleplayCharacter playedCharacter(EntityPlayerMP player) {
        if (player == null || player instanceof FakePlayer || player.worldObj == null
                || player.worldObj.isRemote || player.playerNetServerHandler == null) {
            return null;
        }
        return CharacterActiveResolver.get(player);
    }

    /**
     * Counts an act: {@code worth} experience toward {@code skillId}, less
     * for a repeat of the same act on the same thing lately (PROG-08), and
     * within the skill's hourly cap ({@link GrantSource#ACT}).
     * {@code repeatKey} names what repeats are counted under, none when
     * empty; where {@code repeats} is false the act shrinks as those
     * repeats do and is none itself (a hit shrinks as kills of its kind
     * do). A named set worn whose bonus feeds the skill adds its share
     * (CONTENT-07). Answers the experience gained.
     */
    public static double count(EntityPlayerMP player, String skillId, String repeatKey,
                               boolean repeats, double worth) {
        if (!(worth > 0.0D)) {
            return 0.0D;
        }
        RoleplayCharacter character = playedCharacter(player);
        if (character == null) {
            return 0.0D;
        }
        double share = 1.0D;
        if (repeatKey != null && repeatKey.length() > 0) {
            RepeatMemory memory = memory(character.getCharacterId());
            String key = skillId + "|" + repeatKey;
            long now = System.currentTimeMillis();
            share = repeats ? memory.countRepeat(key, ProgressionSettings.repeatRule(), now)
                    : memory.peekShare(key, ProgressionSettings.repeatRule(), now);
        }
        double sets = 1.0D + Armour.worn(player).experience(skillId);
        return grant(player, character, skillId, worth * share * sets, GrantSource.ACT);
    }

    /**
     * An operator's {@code give} ({@link GrantSource#GIVE}): no repeat and
     * no hourly cap holds it back.
     */
    public static double give(EntityPlayerMP player, String skillId, double amount) {
        RoleplayCharacter character = playedCharacter(player);
        return character == null ? 0.0D
                : grant(player, character, skillId, amount, GrantSource.GIVE);
    }

    /**
     * A quest's reward in skill experience (PROG-04, QUEST-19), more under
     * Storyteller ({@link GrantSource#QUEST}): no repeat and no hourly cap
     * holds it back.
     */
    public static double questReward(EntityPlayerMP player, String skillId, double amount) {
        RoleplayCharacter character = playedCharacter(player);
        return character == null ? 0.0D
                : grant(player, character, skillId, amount * storyteller(player),
                        GrantSource.QUEST);
    }

    /** What Storyteller makes a quest's skill experience worth, times; 1 without it. */
    public static double storyteller(EntityPlayerMP player) {
        return 1.0D + Perks.of(player).total(PerkEffect.QUEST_SKILL_BONUS);
    }

    private static double grant(EntityPlayerMP player, RoleplayCharacter character,
                                String skillId, double amount, GrantSource source) {
        SkillDefinition skill = ProgressionCatalog.skill(skillId);
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (skill == null || data == null || data.isReadOnly() || !(amount > 0.0D)) {
            return 0.0D;
        }
        double allowed = source.allow(memory(character.getCharacterId()), skillId, amount,
                LostTalesConfig.skillHourlyCap, System.currentTimeMillis());
        if (!(allowed > 0.0D)) {
            return 0.0D;
        }
        ProgressionState state = data.state();
        ProgressionRules.Gain gain = ProgressionRules.gain(state, skillId, allowed,
                ProgressionSettings.curve(), ProgressionSettings.characterRule());
        if (gain == ProgressionRules.Gain.NONE) {
            return 0.0D;
        }
        data.replace(state);
        if (gain.changedLevels()) {
            int reached = gain.levelsReached.get(gain.levelsReached.size() - 1).intValue();
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.skill.note.level", SkillWords.name(skill),
                    Integer.valueOf(reached)));
            if (gain.characterLevels > 0) {
                player.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.skill.note.character",
                        Integer.valueOf(state.getCharacterLevel())));
            }
            ProgressionEvents.levelsReached(player, state, gain);
        }
        return allowed;
    }

    /**
     * Keeps a LOTR waypoint the played character stands near as reached
     * (PROG-01); answers whether it was the first time, which is when Lore
     * counts it. Nothing is kept for a ghost, a read-only save or a
     * character that reached as many waypoints as it may.
     */
    public static boolean waypointReached(EntityPlayerMP player, String waypointId) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || data.isReadOnly() || !ProgressionState.fitsId(waypointId)
                || data.view().hasWaypoint(waypointId) || playedCharacter(player) == null) {
            return false;
        }
        ProgressionState state = data.state();
        try {
            if (!state.addWaypoint(waypointId)) {
                return false;
            }
        } catch (IllegalStateException full) {
            return false;
        }
        return data.replace(state);
    }

    /** Takes the next rank of a perk the player asked for, if its needs are met. */
    public static void takePerk(EntityPlayerMP player, String skillId, String perkId) {
        RoleplayCharacter character = playedCharacter(player);
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (character == null || data == null || data.isReadOnly()) {
            refuse(player, "unavailable");
            return;
        }
        SkillDefinition skill = ProgressionCatalog.skill(skillId);
        PerkDefinition perk = skill == null ? null : skill.perk(perkId);
        ProgressionState state = data.state();
        int points = ProgressionRules.availablePoints(state,
                ProgressionSettings.pointsPerLevel(), ProgressionCatalog.skillsById());
        ProgressionRules.Refusal refusal = ProgressionRules.canTake(state, skill, perk, points);
        if (refusal != ProgressionRules.Refusal.NONE) {
            refuse(player, refusal.name().toLowerCase(Locale.ROOT));
            data.markDirty();
            return;
        }
        int rank = state.perkRank(skillId, perkId) + 1;
        state.setPerkRank(skillId, perkId, rank);
        data.replace(state);
        player.addChatMessage(new ChatComponentTranslation("chat.losttales.skill.perk.taken",
                SkillWords.perkName(skill, perk), Integer.valueOf(rank)));
    }

    /** Sends the client its character's growth when it changed since it was last sent. */
    public static void tick(EntityPlayerMP player) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data != null && data.takeDirty()) {
            ProgressionSync.sendState(player);
        }
    }

    private static void refuse(EntityPlayerMP player, String why) {
        player.addChatMessage(new ChatComponentTranslation(
                "chat.losttales.skill.perk.refused." + why));
    }

    private static synchronized RepeatMemory memory(UUID characterId) {
        RepeatMemory memory = MEMORIES.get(characterId);
        if (memory == null) {
            memory = new RepeatMemory();
            MEMORIES.put(characterId, memory);
        }
        return memory;
    }
}
