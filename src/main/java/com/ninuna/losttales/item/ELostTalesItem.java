package com.ninuna.losttales.item;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.creativetab.ELostTalesCreativeTabs;
import com.ninuna.losttales.item.armor.LostTalesItemArmor3D;
import com.ninuna.losttales.item.armor.LostTalesItemArmorBase;
import com.ninuna.losttales.item.consumable.LostTalesItemDrink;
import com.ninuna.losttales.item.consumable.LostTalesItemFood;
import com.ninuna.losttales.item.weapon.*;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import cpw.mods.fml.common.registry.GameRegistry;
import lotr.common.item.LOTRItemDagger;
import lotr.common.item.LOTRWeaponStats;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

public enum ELostTalesItem {
    // Armors - Arnor.
    ARNORIAN_HELMET_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 0, "arnorian_armor_light"
    ).setUnlocalizedName("arnorian_helmet_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HELMET_LIGHT_2(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 0, "arnorian_armor_light_2"
    ).setUnlocalizedName("arnorian_helmet_light_2").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_ARMOR_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 1, "arnorian_armor_light"
    ).setUnlocalizedName("arnorian_armor_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_LEGGINGS_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 2, "arnorian_armor_light"
    ).setUnlocalizedName("arnorian_leggings_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_BOOTS_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 3, "arnorian_armor_light"
    ).setUnlocalizedName("arnorian_boots_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HELMET_HEAVY(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 0, "arnorian_armor_heavy"
    ).setUnlocalizedName("arnorian_helmet_heavy").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HELMET_HEAVY_2(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 0, "arnorian_helmet_heavy_2"
    ).setUnlocalizedName("arnorian_helmet_heavy_2").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_ARMOR_HEAVY(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 1, "arnorian_armor_heavy"
    ).setUnlocalizedName("arnorian_armor_heavy").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_LEGGINGS_HEAVY(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 2, "arnorian_armor_heavy"
    ).setUnlocalizedName("arnorian_leggings_heavy").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_BOOTS_HEAVY(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 3, "arnorian_armor_heavy"
    ).setUnlocalizedName("arnorian_boots_heavy").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HELMET_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 0, "arnorian_armor_light_captain"
    ).setUnlocalizedName("arnorian_helmet_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_ARMOR_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 1, "arnorian_armor_light_captain"
    ).setUnlocalizedName("arnorian_armor_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_LEGGINGS_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 2, "arnorian_armor_light_captain"
    ).setUnlocalizedName("arnorian_leggings_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_BOOTS_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_LIGHT, 3, "arnorian_armor_light_captain"
    ).setUnlocalizedName("arnorian_boots_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HELMET_HEAVY_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 0, "arnorian_armor_heavy_captain"
    ).setUnlocalizedName("arnorian_helmet_heavy_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_ARMOR_HEAVY_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 1, "arnorian_armor_heavy_captain"
    ).setUnlocalizedName("arnorian_armor_heavy_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_LEGGINGS_HEAVY_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 2, "arnorian_armor_heavy_captain"
    ).setUnlocalizedName("arnorian_leggings_heavy_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_BOOTS_HEAVY_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.ARNOR, Type.ARMOR_HEAVY, 3, "arnorian_armor_heavy_captain"
    ).setUnlocalizedName("arnorian_boots_heavy_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Weapons - Arnor.
    ARNORIAN_DAGGER(new LostTalesItemDagger(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_DAGGER, LOTRItemDagger.DaggerEffect.NONE
    ).setUnlocalizedName("arnorian_dagger").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_DAGGER_POISONED(new LostTalesItemDagger(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_DAGGER, LOTRItemDagger.DaggerEffect.POISON
    ).setUnlocalizedName("arnorian_dagger_poisoned").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_SWORD(new LostTalesItemSword(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_SWORD
    ).setUnlocalizedName("arnorian_sword").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_LONGSWORD(new LostTalesItemLongSword(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_LONGSWORD
    ).setUnlocalizedName("arnorian_longsword").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_HALBERD(new LostTalesItemPoleArm(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_POLEARM
    ).setUnlocalizedName("arnorian_halberd").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_PIKE(new LostTalesItemPike(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_PIKE
    ).setUnlocalizedName("arnorian_pike").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    ARNORIAN_SPEAR(new LostTalesItemSpear(
            ELostTalesItemMaterial.ARNOR, Type.WEAPON_SPEAR
    ).setUnlocalizedName("arnorian_spear").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Armors - Orocarni.
    OROCARNI_HELMET_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 0, "orocarni_armor_light"
    ).setUnlocalizedName("orocarni_helmet_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_HELMET_LIGHT_2(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 0, "orocarni_armor_light_2"
    ).setUnlocalizedName("orocarni_helmet_light_2").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_HELMET_LIGHT_3(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 0, "orocarni_armor_light_3"
    ).setUnlocalizedName("orocarni_helmet_light_3").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_ARMOR_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 1, "orocarni_armor_light"
    ).setUnlocalizedName("orocarni_armor_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_LEGGINGS_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 2, "orocarni_armor_light"
    ).setUnlocalizedName("orocarni_leggings_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_BOOTS_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 3, "orocarni_armor_light"
    ).setUnlocalizedName("orocarni_boots_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_HELMET_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 0, "orocarni_armor_light_captain"
    ).setUnlocalizedName("orocarni_helmet_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_ARMOR_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 1, "orocarni_armor_light_captain"
    ).setUnlocalizedName("orocarni_armor_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_LEGGINGS_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 2, "orocarni_armor_light_captain"
    ).setUnlocalizedName("orocarni_leggings_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_BOOTS_LIGHT_CAPTAIN(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.OROCARNI, Type.ARMOR_LIGHT, 3, "orocarni_armor_light_captain"
    ).setUnlocalizedName("orocarni_boots_light_captain").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Weapons - Orocarni.
    OROCARNI_DAGGER(new LostTalesItemDagger(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_DAGGER, LOTRItemDagger.DaggerEffect.NONE
    ).setUnlocalizedName("orocarni_dagger").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_DAGGER_POISONED(new LostTalesItemDagger(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_DAGGER, LOTRItemDagger.DaggerEffect.POISON
    ).setUnlocalizedName("orocarni_dagger_poisoned").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_SWORD(new LostTalesItemSword(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_SWORD
    ).setUnlocalizedName("orocarni_sword").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_BATTLEAXE(new LostTalesItemBattleaxe(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_BATTLEAXE
    ).setUnlocalizedName("orocarni_battleaxe").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_MACE(new LostTalesItemWarhammer(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_WARHAMMER
    ).setUnlocalizedName("orocarni_mace").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_WARHAMMER(new LostTalesItemWarhammer(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_WARHAMMER
    ).setUnlocalizedName("orocarni_warhammer").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_HALBERD(new LostTalesItemPoleArm(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_POLEARM
    ).setUnlocalizedName("orocarni_halberd").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_PIKE(new LostTalesItemPike(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_PIKE
    ).setUnlocalizedName("orocarni_pike").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    OROCARNI_SPEAR(new LostTalesItemSpear(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_SPEAR
    ).setUnlocalizedName("orocarni_spear").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Armors - Galadhrim.
    GALADHRIM_HELMET_LIGHT(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.LOTHLORIEN, Type.ARMOR_LIGHT, 0, "galadhrim_armor_light"
    ).setUnlocalizedName("galadhrim_helmet_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    GALADHRIM_HELMET_LIGHT_HOOD(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.LOTHLORIEN, Type.ARMOR_LIGHT, 0, "galadhrim_armor_light_hood"
    ).setUnlocalizedName("galadhrim_helmet_light_hood").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    GALADHRIM_HELMET_HEAVY(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.LOTHLORIEN, Type.ARMOR_HEAVY, 0, "galadhrim_armor_heavy"
    ).setUnlocalizedName("galadhrim_helmet_heavy").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    GALADHRIM_HELMET_HEAVY_HOOD(new LostTalesItemArmorBase(
            ELostTalesItemMaterial.LOTHLORIEN, Type.ARMOR_HEAVY, 0, "galadhrim_armor_heavy_hood"
    ).setUnlocalizedName("galadhrim_helmet_heavy_hood").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Armors - Moon Elves.
    MOON_ELF_HELMET_LIGHT(new LostTalesItemArmor3D(
            ELostTalesItemMaterial.MOON_ELVES, Type.ARMOR_LIGHT, 0
    ).setUnlocalizedName("moon_elf_helmet_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    MOON_ELF_ARMOR_LIGHT(new LostTalesItemArmor3D(
            ELostTalesItemMaterial.MOON_ELVES, Type.ARMOR_LIGHT, 1
    ).setUnlocalizedName("moon_elf_armor_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    MOON_ELF_LEGGINGS_LIGHT(new LostTalesItemArmor3D(
            ELostTalesItemMaterial.MOON_ELVES, Type.ARMOR_LIGHT, 2
    ).setUnlocalizedName("moon_elf_leggings_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    MOON_ELF_BOOTS_LIGHT(new LostTalesItemArmor3D(
            ELostTalesItemMaterial.MOON_ELVES, Type.ARMOR_LIGHT, 3
    ).setUnlocalizedName("moon_elf_boots_light").setCreativeTab(ELostTalesCreativeTabs.COMBAT.getCreativeTab())),

    // Story - Items.
    DAINS_HAMMER(new LostTalesItemWarhammer(
            ELostTalesItemMaterial.OROCARNI, Type.WEAPON_WARHAMMER
    ).setUnlocalizedName("dains_hammer").setCreativeTab(ELostTalesCreativeTabs.STORY.getCreativeTab())),

    // Backported utility/tools.
    CREATION_TOOL_LOOT_RESPAWNER(new LostTalesItemSimple(
            ELostTalesItemMaterial.NEUTRAL, Type.TOOL
    ).setUnlocalizedName("creation_tool_loot_respawner").setCreativeTab(ELostTalesCreativeTabs.MISC.getCreativeTab())),

    HORN_TEST(new LostTalesItemHorn(
            ELostTalesItemMaterial.NEUTRAL, Type.INSTRUMENT
    ).setUnlocalizedName("horn_test").setCreativeTab(ELostTalesCreativeTabs.MISC.getCreativeTab())),

    // Consumables - Food and drinks.
    PLUM(new LostTalesItemFood(
            ELostTalesItemMaterial.NEUTRAL, Type.CONSUMABLE_FOOD, 1, 0.1F, false
    ).setUnlocalizedName("plum").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    PEAR(new LostTalesItemFood(
            ELostTalesItemMaterial.NEUTRAL, Type.CONSUMABLE_FOOD, 3, 0.3F, false
    ).setUnlocalizedName("pear").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    PEAR_BAKED(new LostTalesItemFood(
            ELostTalesItemMaterial.NEUTRAL, Type.CONSUMABLE_FOOD, 6, 0.6F, false
    ).setUnlocalizedName("pear_baked").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    PLUM_JUICE(new LostTalesItemDrink(
            ELostTalesItemMaterial.NEUTRAL, Type.CONSUMABLE_DRINK, 2, 0.1F, false
    ).setUnlocalizedName("plum_juice").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    PEAR_JUICE(new LostTalesItemDrink(
            ELostTalesItemMaterial.NEUTRAL, Type.CONSUMABLE_DRINK, 2, 0.1F, false
    ).setUnlocalizedName("pear_juice").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    // Community - Items.
    COMMUNITY_LOSSOTH_HARPOON(new LostTalesItemSpear(
            ELostTalesItemMaterial.LOSSOTH, Type.WEAPON_SPEAR, "Scosher"
    ).setUnlocalizedName("community_lossoth_harpoon").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab())),

    COMMUNITY_MORIA_GOBLIN_SCIMITAR(new LostTalesItemSword(
            ELostTalesItemMaterial.MORIA_GOBLINS, Type.WEAPON_SWORD
    ).setUnlocalizedName("community_moria_goblin_scimitar").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab())),

    COMMUNITY_MORIA_GOBLIN_MACE(new LostTalesItemWarhammer(
            ELostTalesItemMaterial.MORIA_GOBLINS, Type.WEAPON_WARHAMMER
    ).setUnlocalizedName("community_moria_goblin_mace").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab())),

    COMMUNITY_MORIA_GOBLIN_PIKE(new LostTalesItemPike(
            ELostTalesItemMaterial.MORIA_GOBLINS, Type.WEAPON_PIKE
    ).setUnlocalizedName("community_moria_goblin_pike").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab())),

    TEST(new LostTalesItemSpear(
            ELostTalesItemMaterial.MORIA_GOBLINS, Type.WEAPON_SPEAR
    ).setUnlocalizedName("test").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab())),

    COMMUNITY_MORIA_GOBLIN_SPEAR(new LostTalesItemSpear(
            ELostTalesItemMaterial.MORIA_GOBLINS, Type.WEAPON_SPEAR
    ).setUnlocalizedName("community_moria_goblin_spear").setCreativeTab(ELostTalesCreativeTabs.COMMUNITY.getCreativeTab()));

    private final Item item;
    private IIcon largeIcon;

    ELostTalesItem(Item item) {
        this.item = item;
    }

    public static void initAndRegisterItems() {
        LOTRWeaponStats.registerMeleeReach(LostTalesItemLongSword.class, 1.25F);
        LOTRWeaponStats.registerMeleeReach(LostTalesItemPike.class, 2.0F);
        LOTRWeaponStats.registerMeleeReach(LostTalesItemPoleArm.class, 1.5F);
        LOTRWeaponStats.registerMeleeSpeed(LostTalesItemLongSword.class, 0.834F);
        LOTRWeaponStats.registerMeleeSpeed(LostTalesItemPike.class, 0.5F);
        LOTRWeaponStats.registerMeleeSpeed(LostTalesItemPoleArm.class, 0.667F);
        LOTRWeaponStats.registerMeleeSpeed(LostTalesItemWarhammer.class, 0.667F);
        LOTRWeaponStats.registerMeleeExtraKnockback(LostTalesItemWarhammer.class, 1);

        for (ELostTalesItem i : ELostTalesItem.values()) {
            String folder;
            if (i.getItem().getUnlocalizedName().startsWith("item.community")) {
                folder = "community/";
            } else {
                folder = "";
            }
            
            i.getItem().setTextureName(LostTalesMetaData.MOD_ID + ":"+ folder + i.getItem().getUnlocalizedName().substring(5));
            GameRegistry.registerItem(i.getItem(), i.getItem().getUnlocalizedName().substring(5));
        }
    }

    public Item getItem() {
        return this.item;
    }

    public IIcon getLargeIcon() {
        return this.largeIcon;
    }

    public void setLargeIcon(IIcon icon) {
        this.largeIcon = icon;
    }

    public enum Type {
        WEAPON_SWORD("Sword"),
        WEAPON_LONGSWORD("Longsword"),
        WEAPON_POLEARM("Polearm"),
        WEAPON_PIKE("Pike"),
        WEAPON_DAGGER("Dagger"),
        WEAPON_SPEAR("Spear"),
        WEAPON_BATTLEAXE("Battleaxe"),
        WEAPON_WARHAMMER("Warhammer"),
        ARMOR_HEAVY("Heavy Armor"),
        ARMOR_LIGHT("Light Armor"),
        BLOCK_BUILDING("Decoration Block"),
        BLOCK_DECORATION("Building Block"),
        BLOCK_PLUSHIE("Plushie"),
        CONSUMABLE_POTION("Potion"),
        CONSUMABLE_FOOD("Food"),
        CONSUMABLE_DRINK("Drink"),
        TOOL("Tool"),
        INSTRUMENT("Instrument");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}