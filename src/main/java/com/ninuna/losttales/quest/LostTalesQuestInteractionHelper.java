package com.ninuna.losttales.quest;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Quests that start by touching somebody or something, on the server. A
 * quest starts this way only when its file's {@code interaction} names its
 * giver: an {@code entity} ({@link LostTalesQuestObjectiveMatcher} reads
 * it), or a {@code block} with an optional {@code meta}, {@code dimension},
 * {@code x}, {@code y}, {@code z} and {@code radius}.
 */
public final class LostTalesQuestInteractionHelper {

    private LostTalesQuestInteractionHelper() {}

    public static boolean handleEntityInteraction(EntityPlayerMP player, Entity target) {
        if (!canProcess(player) || target == null) {
            return false;
        }
        // Somebody a quest sent the player to is answered first: a
        // delivery or a word owed to this person is what the player came
        // for, whether or not this server lets quests start from an
        // interaction at all.
        boolean answered = LostTalesQuestManager.handleTalkedTo(player, target);
        if (!LostTalesConfig.allowQuestInteractionStarts) {
            return answered;
        }

        String nbtQuestId = getQuestIdFromEntityNbt(target);
        if (nbtQuestId.length() > 0) {
            LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(nbtQuestId);
            boolean markerChanged = LostTalesQuestManager.revealQuestGiverMarker(player, quest, target, false);
            LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, nbtQuestId, LostTalesQuestStartSource.INTERACTION);
            if (result == LostTalesQuestManager.StartResult.STARTED || result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE || result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
                if (markerChanged && result != LostTalesQuestManager.StartResult.STARTED) {
                    LostTalesQuestManager.syncToClient(player);
                }
                return true;
            }
            if (markerChanged) {
                LostTalesQuestManager.syncToClient(player);
            }
        }

        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
            if (!quest.canStartFromInteraction() || quest.getInteraction().isEmpty()) {
                continue;
            }
            // A quest that is offered in conversation waits to be taken;
            // touching its giver opens the talk rather than starting it.
            if (LostTalesQuestDialogue.of(quest).isOffered()) {
                continue;
            }
            if (matchesDimension(player, quest.getInteraction())
                    && LostTalesQuestParams.value(quest.getInteraction(), "entity").length() > 0
                    && LostTalesQuestObjectiveMatcher.matchesEntity(target, quest.getInteraction())) {
                boolean markerChanged = LostTalesQuestManager.revealQuestGiverMarker(player, quest, target, false);
                LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, quest.getId(), LostTalesQuestStartSource.INTERACTION);
                if (result == LostTalesQuestManager.StartResult.STARTED || result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE || result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
                    if (markerChanged && result != LostTalesQuestManager.StartResult.STARTED) {
                        LostTalesQuestManager.syncToClient(player);
                    }
                    return true;
                }
                if (markerChanged) {
                    LostTalesQuestManager.syncToClient(player);
                }
            }
        }
        return answered;
    }

    public static boolean handleBlockInteraction(EntityPlayerMP player, Block block, int metadata, int x, int y, int z) {
        if (!canProcess(player) || block == null || !LostTalesConfig.allowQuestInteractionStarts) {
            return false;
        }

        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
            if (!quest.canStartFromInteraction() || quest.getInteraction().isEmpty()) {
                continue;
            }
            Map<String, String> interaction = quest.getInteraction();
            String blockSpec = LostTalesQuestParams.value(interaction, "block");
            if (blockSpec.length() == 0) {
                continue;
            }
            if (!matchesDimension(player, interaction) || !matchesBlock(block, blockSpec) || !matchesMetadata(metadata, interaction) || !matchesLocation(player, x, y, z, interaction)) {
                continue;
            }

            boolean markerChanged = LostTalesQuestManager.revealQuestGiverMarker(player, quest, block, x, y, z, false);
            LostTalesQuestManager.StartResult result = LostTalesQuestManager.startQuest(player, quest.getId(), LostTalesQuestStartSource.INTERACTION);
            if (result == LostTalesQuestManager.StartResult.STARTED || result == LostTalesQuestManager.StartResult.ALREADY_ACTIVE || result == LostTalesQuestManager.StartResult.ALREADY_COMPLETED) {
                if (markerChanged && result != LostTalesQuestManager.StartResult.STARTED) {
                    LostTalesQuestManager.syncToClient(player);
                }
                return true;
            }
            if (markerChanged) {
                LostTalesQuestManager.syncToClient(player);
            }
        }
        return false;
    }

    /** The quest a creature's own data names under {@code LostTalesQuestId}; empty for none or one past the id bound. */
    private static String getQuestIdFromEntityNbt(Entity entity) {
        if (entity == null) {
            return "";
        }
        NBTTagCompound data = entity.getEntityData();
        if (data == null) {
            return "";
        }
        String questId = data.getString("LostTalesQuestId").trim();
        return LostTalesQuestIds.fits(questId) ? questId : "";
    }

    private static boolean canProcess(EntityPlayerMP player) {
        return player != null && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static boolean matchesDimension(EntityPlayerMP player, Map<String, String> interaction) {
        String dimension = LostTalesQuestParams.value(interaction, "dimension");
        if (dimension.length() == 0) {
            return true;
        }
        int targetDimension = LostTalesDimensionHelper.parseDimensionId(dimension, player.worldObj.provider.dimensionId);
        return targetDimension == player.worldObj.provider.dimensionId;
    }

    private static boolean matchesBlock(Block block, String blockSpec) {
        String[] entries = blockSpec.split(",");
        Object blockNameObject = Block.blockRegistry.getNameForObject(block);
        String blockName = blockNameObject == null ? ""
                : LostTalesQuestObjectiveMatcher.normalizeResourceId(blockNameObject.toString());
        for (String entry : entries) {
            String normalized = LostTalesQuestObjectiveMatcher.normalizeResourceId(entry);
            if (normalized.length() == 0) {
                continue;
            }
            Object registered = Block.blockRegistry.getObject(normalized);
            if (registered == block || normalized.equals(blockName)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the block's metadata is one {@code meta} lists; any, where it lists none. */
    private static boolean matchesMetadata(int metadata, Map<String, String> interaction) {
        String metaText = LostTalesQuestParams.value(interaction, "meta");
        if (metaText.length() == 0) {
            return true;
        }
        for (String entry : metaText.split(",")) {
            if (metadata == LostTalesQuestParams.parseInt(entry, -1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesLocation(EntityPlayerMP player, int blockX, int blockY, int blockZ, Map<String, String> interaction) {
        String xText = LostTalesQuestParams.value(interaction, "x");
        String yText = LostTalesQuestParams.value(interaction, "y");
        String zText = LostTalesQuestParams.value(interaction, "z");
        if (xText.length() == 0 && yText.length() == 0 && zText.length() == 0) {
            return true;
        }

        double targetX = LostTalesQuestParams.parseDouble(xText, blockX) + 0.5D;
        double targetY = LostTalesQuestParams.parseDouble(yText, blockY) + 0.5D;
        double targetZ = LostTalesQuestParams.parseDouble(zText, blockZ) + 0.5D;
        double radius = Math.max(0.5D, LostTalesQuestParams.parseDouble(
                LostTalesQuestParams.value(interaction, "radius"), 1.5D));
        double dx = player.posX - targetX;
        double dy = player.posY - targetY;
        double dz = player.posZ - targetZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
