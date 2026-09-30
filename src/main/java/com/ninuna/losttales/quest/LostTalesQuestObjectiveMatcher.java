package com.ninuna.losttales.quest;

import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Whether an item or a creature is the one a quest names. The one reading
 * of the selectors, used by every objective, the conversation and the
 * quest giver alike.
 *
 * <p>Items: {@code item} lists registry names, each {@code id} or
 * {@code id@meta} (an id without a namespace is Minecraft's), and
 * {@code ore} lists ore dictionary names; a stack matches any one of
 * them.</p>
 *
 * <p>Whom: {@code entity} lists creature kinds by the name the game
 * registers them under ({@code Zombie}, {@code losttales.Nia}), and
 * {@code group} lists groups: {@code living}, {@code player},
 * {@code hostile}, {@code animal} and {@code npc}. A creature matches
 * when it is one of the kinds or in one of the groups; a selector that
 * names nobody matches nobody.</p>
 */
public final class LostTalesQuestObjectiveMatcher {
    public static final String GROUP_LIVING = "living";
    public static final String GROUP_PLAYER = "player";
    public static final String GROUP_HOSTILE = "hostile";
    public static final String GROUP_ANIMAL = "animal";
    public static final String GROUP_NPC = "npc";

    private LostTalesQuestObjectiveMatcher() {}

    /** Whether the word is one of the five groups. */
    public static boolean isGroup(String word) {
        return GROUP_LIVING.equals(word) || GROUP_PLAYER.equals(word)
                || GROUP_HOSTILE.equals(word) || GROUP_ANIMAL.equals(word)
                || GROUP_NPC.equals(word);
    }

    /** Whether the params name anybody at all: an {@code entity} or a {@code group}. */
    public static boolean namesWhom(Map<String, String> params) {
        return LostTalesQuestParams.value(params, "entity").length() > 0
                || LostTalesQuestParams.value(params, "group").length() > 0;
    }

    public static boolean matchesItem(ItemStack stack, LostTalesQuestObjectiveDefinition objective) {
        return objective != null && matchesItem(stack, objective.getParams());
    }

    /** Whether the stack is one the params' {@code item} or {@code ore} names. */
    public static boolean matchesItem(ItemStack stack, Map<String, String> params) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        for (String spec : LostTalesQuestParams.value(params, "item").split(",")) {
            String trimmed = spec.trim();
            if (trimmed.length() > 0 && matchesItemId(stack, trimmed)) {
                return true;
            }
        }
        for (String spec : LostTalesQuestParams.value(params, "ore").split(",")) {
            String trimmed = spec.trim();
            if (trimmed.length() > 0 && matchesOre(stack, trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesEntity(Entity entity, LostTalesQuestObjectiveDefinition objective) {
        return objective != null && matchesEntity(entity, objective.getParams());
    }

    /** Whether the creature is one the params' {@code entity} or {@code group} names. */
    public static boolean matchesEntity(Entity entity, Map<String, String> params) {
        if (entity == null) {
            return false;
        }
        String kinds = LostTalesQuestParams.value(params, "entity");
        if (kinds.length() > 0) {
            String name = EntityList.getEntityString(entity);
            for (String kind : kinds.split(",")) {
                if (name != null && name.equals(kind.trim())) {
                    return true;
                }
            }
        }
        for (String group : LostTalesQuestParams.value(params, "group").split(",")) {
            if (inGroup(entity, group.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inGroup(Entity entity, String group) {
        if (GROUP_LIVING.equals(group)) {
            return entity instanceof EntityLivingBase;
        }
        if (GROUP_PLAYER.equals(group)) {
            return entity instanceof EntityPlayer;
        }
        if (GROUP_HOSTILE.equals(group)) {
            return entity instanceof IMob;
        }
        if (GROUP_ANIMAL.equals(group)) {
            return entity instanceof IAnimals && !(entity instanceof IMob);
        }
        if (GROUP_NPC.equals(group)) {
            return isNpc(entity);
        }
        return false;
    }

    /** Whether the creature is some mod's NPC: a class it is made from is named for one. */
    private static boolean isNpc(Entity entity) {
        for (Class<?> type = entity.getClass(); type != null
                && type != Entity.class; type = type.getSuperclass()) {
            if (type.getSimpleName().toLowerCase(Locale.ROOT).indexOf("npc") >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesItemId(ItemStack stack, String spec) {
        int meta = OreDictionary.WILDCARD_VALUE;
        String id = spec;
        int at = spec.lastIndexOf('@');
        if (at >= 0) {
            meta = LostTalesQuestParams.parseInt(spec.substring(at + 1), -1);
            id = spec.substring(0, at).trim();
            if (meta < 0) {
                return false;
            }
        }
        String itemId = normalizeResourceId(id);
        if (itemId.length() == 0 || meta != OreDictionary.WILDCARD_VALUE
                && meta != stack.getItemDamage()) {
            return false;
        }
        Object registered = Item.itemRegistry.getObject(itemId);
        if (registered instanceof Item) {
            return stack.getItem() == registered;
        }
        Object stackName = Item.itemRegistry.getNameForObject(stack.getItem());
        return stackName != null
                && normalizeResourceId(stackName.toString()).equals(itemId);
    }

    private static boolean matchesOre(ItemStack stack, String ore) {
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            if (ore.equals(OreDictionary.getOreName(oreId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A registry name as selectors compare it: trimmed, lower-cased, and
     * in the {@code minecraft} namespace when it names none.
     */
    static String normalizeResourceId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0 && normalized.length() > 0) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }
}
