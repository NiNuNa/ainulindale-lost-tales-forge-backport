package com.ninuna.losttales.crafting;

import com.ninuna.losttales.item.ELostTalesItem;
import cpw.mods.fml.common.registry.GameRegistry;
import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.recipe.LOTRRecipes;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public enum ELostTalesCrafting {
    MORIA_GOBLINS(LOTRRecipes.gundabadRecipes, new ShapelessOreRecipe[] {
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_MACE.getItem()), LOTRMod.hammerDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_PIKE.getItem()), LOTRMod.pikeDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SPEAR.getItem()), LOTRMod.spearDwarven),
            new ShapelessOreRecipe(new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SCIMITAR.getItem()), LOTRMod.swordDwarven)
    });

    private final List recipes;
    private final ShapelessOreRecipe[] shapelessOreRecipe;

    ELostTalesCrafting(List recipes, ShapelessOreRecipe[] shapelessOreRecipe) {
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

        LOTRRecipes.createAllRecipes();
    }

    public List getRecipes() {
        return recipes;
    }

    public ShapelessOreRecipe[] getShapelessOreRecipe() {
        return shapelessOreRecipe;
    }
}