package com.ninuna.losttales.progression.deed;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrDeedAchievements;
import com.ninuna.losttales.compat.lotr.LotrDeedPlaces;
import com.ninuna.losttales.compat.lotr.LotrQuestRewards;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.progression.FellowshipCompanions;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillStanding;
import com.ninuna.losttales.progression.count.CountEvents;
import com.ninuna.losttales.progression.count.Things;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestRewards;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * How a character earns its deeds (PROG-11): by the foes it slays, the
 * quests it finishes and the world quests it helps win, the places it
 * reaches and the levels its skills and itself reach. A kill counts only a
 * creature that fights back, judged as the combat skills judge it; every
 * completion of a quest counts. A deed done together counts for every
 * companion in a fellowship who took part. An earned deed gives its
 * alignment and, bundled, its LOTR achievement, which LOTR announces; a
 * server's own deed the server announces itself.
 */
public final class DeedService {
    /** What any creature is, for a kill deed. */
    public static final String ANY_CREATURE = "*";

    /** What one finished quest does to a deed. */
    enum QuestStep {
        /** Nothing: the deed asks for no such quest. */
        NONE,
        /** One more counted: every completion, a repeatable quest's each time. */
        COUNT,
        /** The deed's listed quests are looked up in the History again. */
        LISTED
    }

    private DeedService() {}

    /**
     * A creature slain: the killer counts it, and so do the companions
     * who struck it too, each only where it fought back against them
     * ({@link CountEvents#isFoe}), so a named chicken is no named foe.
     */
    public static void creatureSlain(EntityPlayerMP killer, EntityLivingBase target,
                                     Collection<EntityPlayerMP> strikers) {
        if (killer == null || target == null || !CountEvents.isFoe(killer, target)) {
            return;
        }
        String name = Things.nameOf(target);
        boolean named = target instanceof EntityLiving && ((EntityLiving) target).hasCustomNameTag();
        for (DeedDefinition deed : ProgressionCatalog.deeds()) {
            if (!countsKill(deed, name, named, true)) {
                continue;
            }
            count(killer, deed, 1L);
            if (deed.isTogether() && strikers != null) {
                for (EntityPlayerMP striker : strikers) {
                    if (striker != killer && FellowshipCompanions.areCompanions(killer, striker)
                            && countsKill(deed, name, named, CountEvents.isFoe(striker, target))) {
                        count(striker, deed, 1L);
                    }
                }
            }
        }
    }

    /**
     * Whether a kill deed counts a slain creature: a foe that fought back,
     * of the creatures it lists or any, bearing a name where it asks for one.
     */
    static boolean countsKill(DeedDefinition deed, String creature, boolean named,
                              boolean foe) {
        return foe && deed.getKind() == DeedDefinition.Kind.KILL
                && (!deed.isNamed() || named)
                && (deed.getCreatures().isEmpty() || deed.getCreatures().contains(creature)
                        || deed.getCreatures().contains(ANY_CREATURE));
    }

    /** A quest finished, and for a world quest, one the player helped win. */
    public static void questFinished(EntityPlayerMP player, LostTalesQuestDefinition quest) {
        for (DeedDefinition deed : ProgressionCatalog.deeds()) {
            switch (questStep(deed, quest.getId(), quest.isWorldQuest())) {
                case COUNT:
                    count(player, deed, 1L);
                    break;
                case LISTED:
                    evaluateQuests(player, deed);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * What a finished quest does to a deed: a deed of any quests counts
     * every completion, the same quest's again each time it is done; a deed
     * of listed quests looks them up in the History; a world quest deed
     * counts each world quest the player helped win, of those it lists or any.
     */
    static QuestStep questStep(DeedDefinition deed, String questId, boolean worldQuest) {
        if (deed.getKind() == DeedDefinition.Kind.QUESTS) {
            if (deed.getQuests().isEmpty()) {
                return QuestStep.COUNT;
            }
            return deed.getQuests().contains(questId) ? QuestStep.LISTED : QuestStep.NONE;
        }
        if (deed.getKind() == DeedDefinition.Kind.WORLD_QUEST && worldQuest
                && (deed.getQuests().isEmpty() || deed.getQuests().contains(questId))) {
            return QuestStep.COUNT;
        }
        return QuestStep.NONE;
    }

    /**
     * Earns every deed the character's state already meets: the levels
     * of its skills and of itself, the quests its History holds. Asked as
     * levels are reached, as a player joins or switches, and after a reload.
     */
    public static void evaluate(EntityPlayerMP player) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || ProgressionService.playedCharacter(player) == null) {
            return;
        }
        for (DeedDefinition deed : ProgressionCatalog.deeds()) {
            ProgressionState state = data.state();
            if (state.hasDeed(deed.getId())) {
                continue;
            }
            switch (deed.getKind()) {
                case CHARACTER_LEVEL:
                    if (state.getCharacterLevel() >= deed.getLevel()) {
                        earn(player, deed);
                    }
                    break;
                case SKILL_LEVEL:
                    if (bestLevel(state, deed.getSkill()) >= deed.getLevel()) {
                        earn(player, deed);
                    }
                    break;
                case QUESTS:
                    if (!deed.getQuests().isEmpty()) {
                        evaluateQuests(player, deed);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static int bestLevel(ProgressionState state, String skill) {
        if (!DeedDefinition.ANY_SKILL.equals(skill)) {
            return state.skill(skill).getLevel();
        }
        int best = 1;
        for (Map.Entry<String, SkillStanding> standing : state.skills().entrySet()) {
            best = Math.max(best, standing.getValue().getLevel());
        }
        return best;
    }

    /** A deed of listed quests: done once the History holds each of them finished. */
    private static void evaluateQuests(EntityPlayerMP player, DeedDefinition deed) {
        LostTalesQuestPlayerData quests = LostTalesQuestPlayerData.get(player);
        if (quests == null) {
            return;
        }
        long finished = 0L;
        for (String questId : deed.getQuests()) {
            if (quests.isQuestCompleted(questId)) {
                finished++;
            }
        }
        setCounter(player, deed, finished);
    }

    /** The places of every unfinished place deed the player stands at now. */
    public static void lookAround(EntityPlayerMP player) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || player.worldObj == null
                || ProgressionService.playedCharacter(player) == null) {
            return;
        }
        for (DeedDefinition deed : ProgressionCatalog.deeds()) {
            if (deed.getKind() != DeedDefinition.Kind.PLACES || data.state().hasDeed(deed.getId())) {
                continue;
            }
            long reached = data.state().deedCounter(deed.getId());
            long before = reached;
            for (int index = 0; index < deed.getPlaces().size() && index < 63; index++) {
                if ((reached & (1L << index)) != 0L) {
                    continue;
                }
                double[] place = positionOf(player, deed.getPlaces().get(index));
                if (place != null && (int) place[0] == player.dimension) {
                    double dx = player.posX - place[1];
                    double dz = player.posZ - place[2];
                    if (dx * dx + dz * dz <= deed.getRadius() * deed.getRadius()) {
                        reached |= 1L << index;
                    }
                }
            }
            if (reached != before) {
                if (Long.bitCount(reached) >= deed.getPlaces().size()) {
                    earn(player, deed);
                } else {
                    setCounter(player, deed, reached);
                    player.addChatMessage(new ChatComponentTranslation(
                            "chat.losttales.deed.note.place", words(deed),
                            Integer.valueOf(Long.bitCount(reached)),
                            Integer.valueOf(deed.getPlaces().size())));
                }
            }
        }
    }

    private static double[] positionOf(EntityPlayerMP player, String place) {
        if (LotrDeedPlaces.isWaypoint(place)) {
            return LotrDeedPlaces.position(place);
        }
        LostTalesMapMarkerRecord marker =
                LostTalesMapMarkerStorage.get(player.worldObj).getRecord(place);
        return marker == null ? null
                : new double[] { marker.getDimensionId(), marker.getX(), marker.getZ() };
    }

    private static void count(EntityPlayerMP player, DeedDefinition deed, long amount) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || data.isReadOnly()
                || ProgressionService.playedCharacter(player) == null
                || data.state().hasDeed(deed.getId())) {
            return;
        }
        long counted = data.state().deedCounter(deed.getId()) + amount;
        if (counted >= deed.target()) {
            earn(player, deed);
        } else {
            setCounter(player, deed, counted);
        }
    }

    private static void setCounter(EntityPlayerMP player, DeedDefinition deed, long count) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || data.isReadOnly() || data.state().hasDeed(deed.getId())) {
            return;
        }
        if (count >= deed.target() && deed.getKind() != DeedDefinition.Kind.PLACES) {
            earn(player, deed);
            return;
        }
        ProgressionState state = data.state();
        if (state.deedCounter(deed.getId()) == count) {
            return;
        }
        try {
            state.setDeedCounter(deed.getId(), count);
        } catch (IllegalStateException full) {
            return;
        }
        data.replace(state);
    }

    /** Marks the deed done, gives what it gives and has it announced. */
    static void earn(EntityPlayerMP player, DeedDefinition deed) {
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        RoleplayCharacter character = ProgressionService.playedCharacter(player);
        if (data == null || data.isReadOnly() || character == null) {
            return;
        }
        ProgressionState state = data.state();
        try {
            if (!state.addDeed(deed.getId())) {
                return;
            }
        } catch (IllegalStateException full) {
            return;
        }
        data.replace(state);
        if (deed.getAlignment().length() > 0) {
            for (LostTalesQuestRewards.Alignment alignment : LostTalesQuestRewards.alignments(
                    Collections.singletonMap(LostTalesQuestRewards.ALIGNMENT, deed.getAlignment()))) {
                LotrQuestRewards.addAlignment(player, alignment.getFactionId(),
                        alignment.getAmount());
            }
        }
        if (LotrDeedAchievements.isAchievement(deed.getId())) {
            LotrDeedAchievements.award(player, deed.getId());
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            server.getConfigurationManager().sendChatMsg(new ChatComponentTranslation(
                    "chat.losttales.deed.done", character.getName(), words(deed)));
        }
    }

    /** A deed's name as a line carries it: the file's own words, else its achievement's. */
    static IChatComponent words(DeedDefinition deed) {
        if (deed.getName().length() > 0) {
            return new ChatComponentText(deed.getName());
        }
        return new ChatComponentTranslation(DeedWords.nameKey(deed.getId()));
    }
}
