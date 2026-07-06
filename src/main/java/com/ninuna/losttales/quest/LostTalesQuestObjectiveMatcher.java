package com.ninuna.losttales.quest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Locale;
import java.util.Map;

/**
 * Small compatibility matcher for data-driven quest objectives.
 *
 * The modern NeoForge branch can use vanilla item/entity tags. Minecraft 1.7.10 does
 * not have those tag registries, so this helper maps item "tag" parameters to the
 * Forge OreDictionary and maps entity "tag" parameters to simple legacy groups such
 * as hostile, passive, player, and living. Explicit registry names still remain the
 * preferred, most predictable format.
 */
public final class LostTalesQuestObjectiveMatcher {
    private LostTalesQuestObjectiveMatcher() {}

    public static boolean matchesItem(ItemStack stack, LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return false;
        }
        Map<String, String> params = objective.getParams();
        return matchesItemOrTag(stack,
                firstNonEmpty(params.get("item"), params.get("itemId"), params.get("items")),
                firstNonEmpty(params.get("tag"), params.get("ore"), params.get("oreDict"), params.get("oredict")));
    }

    public static boolean matchesItemOrTag(ItemStack stack, String itemSpec, String tagSpec) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        if (itemSpec != null && itemSpec.trim().length() > 0) {
            String[] entries = itemSpec.split(",");
            for (String entry : entries) {
                String spec = entry == null ? "" : entry.trim();
                if (spec.length() == 0 || isComment(spec)) {
                    continue;
                }
                if (isTagSelector(spec)) {
                    if (matchesOreDictionary(stack, stripTagPrefix(spec))) {
                        return true;
                    }
                } else if (matchesItemId(stack, spec)) {
                    return true;
                }
            }
        }

        if (tagSpec != null && tagSpec.trim().length() > 0) {
            String[] entries = tagSpec.split(",");
            for (String entry : entries) {
                String spec = entry == null ? "" : entry.trim();
                if (spec.length() == 0 || isComment(spec)) {
                    continue;
                }
                if (matchesOreDictionary(stack, stripTagPrefix(spec))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean matchesEntity(Entity entity, LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return false;
        }
        Map<String, String> params = objective.getParams();
        return matchesEntity(entity,
                firstNonEmpty(params.get("entity"), params.get("entityId"), params.get("type")),
                firstNonEmpty(params.get("tag"), params.get("group")));
    }

    public static boolean matchesEntity(Entity entity, String entitySpec, String tagSpec) {
        if (entity == null) {
            return false;
        }

        boolean hasSelector = false;
        if (entitySpec != null && entitySpec.trim().length() > 0) {
            String[] entries = entitySpec.split(",");
            for (String entry : entries) {
                String spec = entry == null ? "" : entry.trim();
                if (spec.length() == 0 || isComment(spec)) {
                    continue;
                }
                hasSelector = true;
                if (isTagSelector(spec)) {
                    if (matchesEntityGroup(entity, stripTagPrefix(spec))) {
                        return true;
                    }
                } else if (matchesEntityId(entity, spec)) {
                    return true;
                }
            }
        }

        if (tagSpec != null && tagSpec.trim().length() > 0) {
            String[] entries = tagSpec.split(",");
            for (String entry : entries) {
                String spec = entry == null ? "" : entry.trim();
                if (spec.length() == 0 || isComment(spec)) {
                    continue;
                }
                hasSelector = true;
                if (matchesEntityGroup(entity, stripTagPrefix(spec))) {
                    return true;
                }
            }
        }

        // Preserve the previous 1.7.10 behavior: a kill objective without an entity
        // selector means any killed entity can count.
        return !hasSelector;
    }

    private static boolean matchesItemId(ItemStack stack, String spec) {
        ParsedItemSelector selector = parseItemSelector(spec);
        if (selector.itemId.length() == 0) {
            return false;
        }

        Object registered = Item.itemRegistry.getObject(selector.itemId);
        if (registered instanceof Item && stack.getItem() == registered) {
            return selector.matchesMetadata(stack.getItemDamage());
        }

        Object stackName = Item.itemRegistry.getNameForObject(stack.getItem());
        return stackName != null
                && normalizeResourceId(stackName.toString()).equals(selector.itemId)
                && selector.matchesMetadata(stack.getItemDamage());
    }

    private static boolean matchesOreDictionary(ItemStack stack, String oreSpec) {
        String normalizedSpec = normalizeOreName(oreSpec);
        if (normalizedSpec.length() == 0) {
            return false;
        }

        int[] oreIds = OreDictionary.getOreIDs(stack);
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (matchesOreName(oreName, normalizedSpec)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesOreName(String oreName, String spec) {
        if (oreName == null || spec == null) {
            return false;
        }
        String normalizedOre = normalizeOreName(oreName);
        String pathOnly = normalizeOreName(stripNamespace(spec));
        return normalizedOre.equals(spec) || normalizedOre.equals(pathOnly) || normalizeLoose(oreName).equals(normalizeLoose(spec));
    }

    private static boolean matchesEntityId(Entity entity, String spec) {
        String legacyName = normalizeLoose(EntityList.getEntityString(entity));
        String className = normalizeLoose(entity.getClass().getSimpleName().replace("Entity", ""));
        String normalized = normalizeLoose(spec);
        String pathOnly = normalizeLoose(stripNamespace(spec));
        return normalized.length() > 0
                && (normalized.equals(legacyName) || pathOnly.equals(legacyName) || normalized.equals(className) || pathOnly.equals(className));
    }

    private static boolean matchesEntityGroup(Entity entity, String group) {
        String normalized = normalizeLoose(group);
        if (normalized.length() == 0) {
            return false;
        }
        if ("living".equals(normalized) || "mob".equals(normalized)) {
            return entity instanceof EntityLivingBase;
        }
        if ("player".equals(normalized) || "players".equals(normalized)) {
            return entity instanceof EntityPlayer;
        }
        if ("hostile".equals(normalized) || "hostiles".equals(normalized) || "monster".equals(normalized) || "monsters".equals(normalized)) {
            return entity instanceof IMob;
        }
        if ("passive".equals(normalized) || "passives".equals(normalized) || "animal".equals(normalized) || "animals".equals(normalized) || "creature".equals(normalized) || "creatures".equals(normalized)) {
            return entity instanceof IAnimals && !(entity instanceof IMob);
        }
        if ("npc".equals(normalized) || "npcs".equals(normalized)) {
            String legacyName = normalizeLoose(EntityList.getEntityString(entity));
            String className = normalizeLoose(entity.getClass().getSimpleName());
            return legacyName.indexOf("npc") >= 0 || className.indexOf("npc") >= 0;
        }
        return matchesEntityId(entity, group);
    }

    private static ParsedItemSelector parseItemSelector(String spec) {
        String itemId = spec == null ? "" : spec.trim();
        int meta = OreDictionary.WILDCARD_VALUE;

        int at = itemId.lastIndexOf('@');
        if (at >= 0 && at + 1 < itemId.length()) {
            meta = parseMetadata(itemId.substring(at + 1), OreDictionary.WILDCARD_VALUE);
            itemId = itemId.substring(0, at);
        } else {
            int firstColon = itemId.indexOf(':');
            int lastColon = itemId.lastIndexOf(':');
            if (firstColon >= 0 && lastColon > firstColon && lastColon + 1 < itemId.length()) {
                int parsedMeta = parseMetadata(itemId.substring(lastColon + 1), Integer.MIN_VALUE);
                if (parsedMeta != Integer.MIN_VALUE) {
                    meta = parsedMeta;
                    itemId = itemId.substring(0, lastColon);
                }
            }
        }

        return new ParsedItemSelector(normalizeResourceId(itemId), meta);
    }

    private static int parseMetadata(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 0 || "*".equals(trimmed)) {
            return OreDictionary.WILDCARD_VALUE;
        }
        try {
            return Math.max(0, Integer.parseInt(trimmed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isTagSelector(String spec) {
        if (spec == null) {
            return false;
        }
        String trimmed = spec.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return trimmed.startsWith("#") || lower.startsWith("ore:") || lower.startsWith("oredict:") || lower.startsWith("oredictionary:") || lower.startsWith("tag:");
    }

    private static boolean isComment(String spec) {
        return spec != null && spec.trim().startsWith("//");
    }

    private static String stripTagPrefix(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.trim();
        while (stripped.startsWith("#")) {
            stripped = stripped.substring(1).trim();
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ore:")) {
            return stripped.substring("ore:".length()).trim();
        }
        if (lower.startsWith("oredict:")) {
            return stripped.substring("oredict:".length()).trim();
        }
        if (lower.startsWith("oredictionary:")) {
            return stripped.substring("oredictionary:".length()).trim();
        }
        if (lower.startsWith("tag:")) {
            return stripped.substring("tag:".length()).trim();
        }
        return stripped;
    }

    private static String normalizeResourceId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0 && normalized.length() > 0) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private static String normalizeOreName(String value) {
        if (value == null) {
            return "";
        }
        return stripTagPrefix(value).trim().toLowerCase(Locale.ROOT);
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

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class ParsedItemSelector {
        private final String itemId;
        private final int metadata;

        private ParsedItemSelector(String itemId, int metadata) {
            this.itemId = itemId == null ? "" : itemId;
            this.metadata = metadata;
        }

        private boolean matchesMetadata(int stackMetadata) {
            return this.metadata == OreDictionary.WILDCARD_VALUE || this.metadata == stackMetadata;
        }
    }
}
