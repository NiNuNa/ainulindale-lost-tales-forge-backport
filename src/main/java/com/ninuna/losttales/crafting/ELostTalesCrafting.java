package com.ninuna.losttales.crafting;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.item.ELostTalesItem;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.recipe.LOTRRecipes;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public enum ELostTalesCrafting {
    MORIA_GOBLINS(LOTRRecipes.gundabadRecipes, new ShapelessOreRecipe[] {
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_MACE.getItem()), LOTRMod.hammerDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_PIKE.getItem()), LOTRMod.pikeDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SPEAR.getItem()), LOTRMod.spearDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SCIMITAR.getItem()), LOTRMod.swordDwarven)
    });

    private final List<IRecipe> recipes;
    private final ShapelessOreRecipe[] shapelessOreRecipe;

    ELostTalesCrafting(List<IRecipe> recipes,
                       ShapelessOreRecipe[] shapelessOreRecipe) {
        this.recipes = recipes;
        this.shapelessOreRecipe = shapelessOreRecipe;
    }

    public static void initAndRegisterCrafting() {
        GameRegistry.addSmelting(ELostTalesItem.PEAR.getItem(), new ItemStack(ELostTalesItem.PEAR_BAKED.getItem()), 0.35F);

        for (ELostTalesCrafting c : ELostTalesCrafting.values()) {
            for (ShapelessOreRecipe s : c.getShapelessOreRecipe()) {
                c.getRecipes().add(s);
            }
        }
        registerWaystoneRecipe();

        // LOTR has already populated these lists before required-after mods run.
        // Repeating its global initializer duplicates the base mod's recipes.
    }

    private static void registerWaystoneRecipe() {
        if (!LostTalesConfig.enableWaystoneRecipe) {
            return;
        }
        Object corner = resolveIngredient(
                LostTalesConfig.waystoneRecipeCornerIngredient);
        Object edge = resolveIngredient(
                LostTalesConfig.waystoneRecipeEdgeIngredient);
        Object center = resolveIngredient(
                LostTalesConfig.waystoneRecipeCenterIngredient);
        if (corner == null || edge == null || center == null) {
            FMLLog.warning(
                    "[%s] Waystone recipe was not registered because a configured ingredient is invalid",
                    LostTalesMetaData.MOD_ID);
            return;
        }
        GameRegistry.addRecipe(new ShapedOreRecipe(
                new ItemStack(ELostTalesBlock.WAYSTONE.getBlock()),
                "CEC", "EKE", "CEC",
                Character.valueOf('C'), corner,
                Character.valueOf('E'), edge,
                Character.valueOf('K'), center));
    }

    private static Object resolveIngredient(String configured) {
        String value = configured == null ? "" : configured.trim();
        if (value.startsWith("ore:") && value.length() > 4) {
            return value.substring(4);
        }
        int metadata = 0;
        int metadataSeparator = value.lastIndexOf('@');
        if (metadataSeparator > value.indexOf(':')) {
            try {
                metadata = Integer.parseInt(
                        value.substring(metadataSeparator + 1));
                value = value.substring(0, metadataSeparator);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        int namespaceSeparator = value.indexOf(':');
        if (namespaceSeparator <= 0
                || namespaceSeparator >= value.length() - 1) {
            return null;
        }
        String namespace = value.substring(0, namespaceSeparator);
        String path = value.substring(namespaceSeparator + 1);
        Item item = GameRegistry.findItem(namespace, path);
        if (item != null) {
            return new ItemStack(item, 1, metadata);
        }
        Block block = GameRegistry.findBlock(namespace, path);
        return block == null ? null : new ItemStack(block, 1, metadata);
    }

    public List<IRecipe> getRecipes() {
        return recipes;
    }

    public ShapelessOreRecipe[] getShapelessOreRecipe() {
        return shapelessOreRecipe;
    }
}
