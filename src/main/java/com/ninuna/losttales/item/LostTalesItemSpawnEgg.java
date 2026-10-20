package com.ninuna.losttales.item;

import com.ninuna.losttales.compat.lotr.LotrSpawnEggs;
import com.ninuna.losttales.creativetab.ELostTalesCreativeTabs;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.util.LostTalesWords;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * The spawn eggs of the Lost Tales creatures (CONTENT-04): one item whose
 * damage is the creature's entity id, drawn as the vanilla egg in the
 * creature's two colours and standing in LOTR's spawn-egg tab. Used on a
 * block it hatches the creature against the face it touched, and a
 * dispenser hatches it in front of itself, as with a vanilla egg; outside
 * creative mode the egg is used up.
 */
public class LostTalesItemSpawnEgg extends Item {
    /** The lang key of an egg's name, {@code %s} the creature's name. */
    public static final String NAME_KEY = "item.losttales.spawn_egg.creature";

    /** The colour of an egg whose damage names no creature. */
    private static final int NO_CREATURE_COLOR = 0xFFFFFF;

    /** The faces a block has; any other side a client sends is refused. */
    private static final int SIDES = 6;

    public LostTalesItemSpawnEgg() {
        this.setHasSubtypes(true);
        this.setCreativeTab(LotrSpawnEggs.tabOr(ELostTalesCreativeTabs.MISC.getCreativeTab()));
    }

    /** An egg's name in the reader's words: Spawn and the creature's name. */
    public static String nameOf(ELostTalesEntity creature, LostTalesWords words) {
        return words.format(NAME_KEY, words.format(creature.getNameKey()));
    }

    /** The creature an egg hatches, or null when its damage names none. */
    public static ELostTalesEntity creatureOf(ItemStack stack) {
        return stack == null ? null : ELostTalesEntity.byId(stack.getItemDamage());
    }

    /**
     * The egg of the creature an entity is, which a creative player's pick
     * on it gives; null for an entity that is none of the mod's creatures.
     */
    public static ItemStack eggOf(Entity entity) {
        ELostTalesEntity creature = entity == null ? null : ELostTalesEntity.byClass(entity.getClass());
        if (creature == null) {
            return null;
        }
        return new ItemStack(ELostTalesItem.SPAWN_EGG.getItem(), 1, creature.getId());
    }

    /** Lets a dispenser hatch the eggs, as it hatches vanilla ones. */
    public static void registerDispenseBehavior(Item egg) {
        BlockDispenser.dispenseBehaviorRegistry.putObject(egg, new BehaviorDefaultDispenseItem() {
            @Override
            protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
                EnumFacing facing = BlockDispenser.func_149937_b(source.getBlockMetadata());
                double x = source.getX() + facing.getFrontOffsetX();
                double y = source.getYInt() + 0.2D;
                double z = source.getZ() + facing.getFrontOffsetZ();
                hatch(stack, source.getWorld(), x, y, z);
                stack.splitStack(1);
                return stack;
            }
        });
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ELostTalesEntity creature = creatureOf(stack);
        if (creature == null) {
            return super.getItemStackDisplayName(stack);
        }
        return nameOf(creature, LostTalesWords.LANG);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
            int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (side < 0 || side >= SIDES) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        int spawnX = x + Facing.offsetsXForSide[side];
        int spawnY = y + Facing.offsetsYForSide[side];
        int spawnZ = z + Facing.offsetsZForSide[side];
        // A creature hatched on top of a fence stands half a block up, as vanilla's do.
        double lift = side == 1 && block != null && block.getRenderType() == 11 ? 0.5D : 0.0D;
        Entity entity = hatch(stack, world, spawnX + 0.5D, spawnY + lift, spawnZ + 0.5D);
        if (entity != null && !player.capabilities.isCreativeMode) {
            --stack.stackSize;
        }
        return true;
    }

    /**
     * Hatches the egg's creature at the point: named as the egg is when it
     * was renamed, and marked as LOTR marks its egg-spawned NPCs. Null when
     * the egg names no creature or the creature cannot be made.
     */
    private static Entity hatch(ItemStack stack, World world, double x, double y, double z) {
        ELostTalesEntity creature = creatureOf(stack);
        if (creature == null) {
            return null;
        }
        EntityLiving entity = spawnCreature(world, creature, x, y, z);
        if (entity == null) {
            return null;
        }
        if (stack.hasDisplayName()) {
            entity.setCustomNameTag(stack.getDisplayName());
        }
        LotrSpawnEggs.markEggSpawned(entity);
        return entity;
    }

    /**
     * Makes the creature by its Forge registration, places it facing a
     * random way and lets it equip itself as an egg-spawned creature does.
     * Null when the creature is not registered in this game.
     */
    private static EntityLiving spawnCreature(World world, ELostTalesEntity creature,
            double x, double y, double z) {
        Object registeredName = EntityList.classToStringMapping.get(creature.getEntityClass());
        if (!(registeredName instanceof String)) {
            return null;
        }
        Entity entity = EntityList.createEntityByName((String)registeredName, world);
        if (!(entity instanceof EntityLiving)) {
            return null;
        }
        EntityLiving living = (EntityLiving)entity;
        living.setLocationAndAngles(x, y, z,
                MathHelper.wrapAngleTo180_float(world.rand.nextFloat() * 360.0F), 0.0F);
        living.rotationYawHead = living.rotationYaw;
        living.renderYawOffset = living.rotationYaw;
        living.onSpawnWithEgg((IEntityLivingData)null);
        world.spawnEntityInWorld(living);
        living.playLivingSound();
        return living;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (ELostTalesEntity creature : ELostTalesEntity.values()) {
            list.add(new ItemStack(item, 1, creature.getId()));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        ELostTalesEntity creature = creatureOf(stack);
        if (creature == null) {
            return NO_CREATURE_COLOR;
        }
        return pass == 0 ? creature.getEggColor() : creature.getEggSpotColor();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        return Items.spawn_egg.getIconFromDamageForRenderPass(damage, pass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return Items.spawn_egg.getIconFromDamage(damage);
    }

    /** The egg is drawn with the vanilla egg's pictures and has none of its own. */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
    }
}
