package com.ninuna.losttales.quest;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
/**
 * Small server-side bridge for quest giver interactions.
 *
 * Modern versions can use richer entity/block interaction hooks and registries. In 1.7.10
 * we keep this data-driven and conservative: a quest only starts from an interaction when
 * its JSON defines an explicit entity or block target.
 */
public final class LostTalesQuestInteractionHelper {

    private LostTalesQuestInteractionHelper() {}

    public static boolean handleEntityInteraction(EntityPlayerMP player, Entity target) {
        if (!canProcess(player) || target == null || !LostTalesConfig.allowQuestInteractionStarts) {
            return false;
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
            if (matchesDimension(player, quest.getInteraction()) && matchesEntity(target, firstValue(quest.getInteraction(), "entity", "entityId", "npc", "target"))) {
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
        return false;
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
            String blockSpec = firstValue(interaction, "block", "blockId", "target");
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

    private static String getQuestIdFromEntityNbt(Entity entity) {
        if (entity == null) {
            return "";
        }
        NBTTagCompound data = entity.getEntityData();
        if (data == null) {
            return "";
        }
        String questId = data.getString("LostTalesQuestId");
        return questId == null ? "" : questId.trim();
    }

    private static boolean canProcess(EntityPlayerMP player) {
        return player != null && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static boolean matchesDimension(EntityPlayerMP player, Map<String, String> interaction) {
        String dimension = firstValue(interaction, "dimension", "dim");
        if (dimension.length() == 0) {
            return true;
        }
        int targetDimension = LostTalesDimensionHelper.parseDimensionId(dimension, player.worldObj.provider.dimensionId);
        return targetDimension == player.worldObj.provider.dimensionId;
    }

    private static boolean matchesEntity(Entity entity, String entitySpec) {
        if (entitySpec == null || entitySpec.trim().length() == 0) {
            return false;
        }

        String legacyName = normalizeLoose(EntityList.getEntityString(entity));
        String className = normalizeLoose(entity.getClass().getSimpleName().replace("Entity", ""));
        String[] entries = entitySpec.split(",");
        for (String entry : entries) {
            String normalized = normalizeLoose(entry);
            String pathOnly = normalizeLoose(stripNamespace(entry));
            if (normalized.length() == 0 || normalized.startsWith("#")) {
                continue;
            }
            if (normalized.equals(legacyName) || pathOnly.equals(legacyName) || normalized.equals(className) || pathOnly.equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBlock(Block block, String blockSpec) {
        String[] entries = blockSpec.split(",");
        Object blockNameObject = Block.blockRegistry.getNameForObject(block);
        String blockName = blockNameObject == null ? "" : normalizeResourceId(blockNameObject.toString());
        for (String entry : entries) {
            String normalized = normalizeResourceId(entry);
            if (normalized.length() == 0 || normalized.startsWith("#")) {
                continue;
            }
            Object registered = Block.blockRegistry.getObject(normalized);
            if (registered == block || normalized.equals(blockName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMetadata(int metadata, Map<String, String> interaction) {
        String metaText = firstValue(interaction, "metadata", "meta", "damage");
        if (metaText.length() == 0 || "*".equals(metaText)) {
            return true;
        }
        String[] entries = metaText.split(",");
        for (String entry : entries) {
            if (metadata == parseInt(entry.trim(), -9999)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesLocation(EntityPlayerMP player, int blockX, int blockY, int blockZ, Map<String, String> interaction) {
        String xText = interaction.get("x");
        String yText = interaction.get("y");
        String zText = interaction.get("z");
        if ((xText == null || xText.length() == 0) && (yText == null || yText.length() == 0) && (zText == null || zText.length() == 0)) {
            return true;
        }

        double targetX = parseDouble(xText, blockX) + 0.5D;
        double targetY = parseDouble(yText, blockY) + 0.5D;
        double targetZ = parseDouble(zText, blockZ) + 0.5D;
        double radius = Math.max(0.5D, parseDouble(firstValue(interaction, "radius", "range"), 1.5D));
        double dx = player.posX - targetX;
        double dy = player.posY - targetY;
        double dz = player.posZ - targetZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static String firstValue(Map<String, String> map, String... keys) {
        if (map == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = map.get(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeResourceId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0 && normalized.length() > 0 && !normalized.startsWith("#")) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private static String stripNamespace(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        return colon >= 0 && colon + 1 < trimmed.length() ? trimmed.substring(colon + 1) : trimmed;
    }

    private static String normalizeLoose(String value) {
        if (value == null) {
            return "";
        }
        String stripped = stripNamespace(value).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
