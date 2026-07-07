package com.ninuna.losttales.util;

import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.armor.LostTalesItemArmorBase;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRMapLabels;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRank;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.spawning.LOTRSpawnEntry;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import org.lwjgl.input.Keyboard;

public abstract class LostTalesUtil {

    public static void addItemInformation(List list, ItemStack itemStack, ELostTalesItemMaterial material, String credits, EntityPlayer player, ELostTalesItem.Type itemType) {
        addItemLore(list, getItemLore(itemStack));
        addItemDetails(list, itemStack, credits, material, itemType);
        if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
            addArmorSetInformation(itemStack, player, list, "Test Bonus!");
        }
        addUniqueItemDetails();
    }

    public static void addItemBlockInformation(List list, ItemStack itemStack, Block block, ELostTalesUser credits) {
        addItemLore(list, getItemLore(itemStack));
        addItemBlockDetails(list, itemStack, block, credits);

    }

    public static String[] getItemLore(ItemStack itemStack) {
        return new String[]{I18n.format(itemStack.getUnlocalizedName() + ".lore.line1"), I18n.format(itemStack.getUnlocalizedName() + ".lore.line2")};
    }

    public static void addItemLore(List list, String[] itemLore) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            for (String lore : itemLore) {
                list.add("§7§o" + lore);
            }
        } else {
            list.add("Hold §f[SHIFT] §r§7to view item lore.");
        }
    }

    public static void addItemDetails(List list, ItemStack itemStack, String credits, ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            list.add("");
            list.add("Faction: §f§o" +  I18n.format("lotr.faction." + material.getFaction().getFaction().codeName() + ".name"));

            if (material.getMaterial() != null) {
                list.add("Repair Item: §f§o" + material.getMaterial().getRepairItem().getDisplayName());
            }

            list.add("Type: §f§o" + itemType.getName());

            if (credits != null){
                list.add("§8Created by: §o" + credits);
            }
        } else {
            list.add("");
            list.add("Hold §f[CTRL] §r§7to view item details.");
        }
    }

    public static void addItemBlockDetails(List list, ItemStack itemStack, Block block, ELostTalesUser credits) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            list.add("");

            if (block instanceof LostTalesBlockPlushie) {
                list.add("Rarity: " + ((LostTalesBlockPlushie)block).getRarity().rarityColor + "§o" + ((LostTalesBlockPlushie)block).getRarity().rarityName);
            }

            list.add("Type: §f§o" + ELostTalesItem.Type.BLOCK_BUILDING.getName());

            if (credits != ELostTalesUser.NULL){
                list.add("§8Created by: §o" + credits.getName());
            }
        } else {
            list.add("");
            list.add("Hold §f[CTRL] §r§7to view item details.");
        }
    }

    public static void addArmorSetInformation(ItemStack itemStack, EntityPlayer player, List list, String setBonusDescription) {
        ItemStack helmet = player.getCurrentArmor(3);
        ItemStack armor = player.getCurrentArmor(2);
        ItemStack leggings = player.getCurrentArmor(1);
        ItemStack boots = player.getCurrentArmor(0);

        String infoTextBase = "§8>§o ";
        String[] names = new String[]{infoTextBase, infoTextBase, infoTextBase, infoTextBase};

        int setCounter = 0;

        //Display armor set progress when armor is equipped.
        if (itemStack == helmet || itemStack == armor || itemStack == leggings || itemStack == boots) {
            list.add("");
            if (LostTalesKeyBindings.isModifierKeyDown()) {
                if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
                    if (((LostTalesItemArmorBase)itemStack.getItem()).getItemType() == ELostTalesItem.Type.ARMOR_HEAVY) {
                        setCounter = displayArmorSetDetails(true, names, helmet, armor, leggings, boots);
                        list.add("§e[" + setCounter + "/4] §r§7Heavy Armor Set:");
                    } else {
                        setCounter = displayArmorSetDetails(false, names, helmet, armor, leggings, boots);
                        list.add("§e[" + setCounter + "/4] §r§7Light Armor Set:");
                    }
                }
                Collections.addAll(list, names);

                //Display the armor set bonus.
                if (setCounter == 4) {
                    list.add("");
                    if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
                        if (((LostTalesItemArmorBase)itemStack.getItem()).getItemType() == ELostTalesItem.Type.ARMOR_HEAVY) {
                            list.add("§e[4/4] §r§7Heavy Armor Set Bonus:");
                        } else {
                            list.add("§e[4/4] §r§7Light Armor Set Bonus:");
                        }
                    }
                    if (!setBonusDescription.isEmpty()) {
                        list.add("§e§o" + setBonusDescription);
                    } else {
                        list.add("§e§oNo set bonus!");
                    }
                }
            } else {
                list.add("Hold §e[" + LostTalesKeyBindings.getModifierKeyDisplayName() + "] §r§7to view armor set information.");
            }
        }
    }

    private static int displayArmorSetDetails(Boolean isHeavyArmor, String[] names, ItemStack helmet, ItemStack armor, ItemStack leggings, ItemStack boots) {
        int setCounter = 0;

        setCounter = getArmorSetDetails(helmet, isHeavyArmor, names, setCounter, 0);
        setCounter = getArmorSetDetails(armor, isHeavyArmor, names, setCounter, 1);
        setCounter = getArmorSetDetails(leggings, isHeavyArmor, names, setCounter, 2);
        setCounter = getArmorSetDetails(boots, isHeavyArmor, names, setCounter, 3);

        return setCounter;
    }

    private static int getArmorSetDetails(ItemStack itemStack, Boolean isHeavyArmor, String[] names, int setCounter, int armorSlot) {
        String partOfSetBase = "§e>§o ";
        String emptySlot = "Empty";

        if (itemStack != null) {
            if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
                if (isHeavyArmor == (((LostTalesItemArmorBase)itemStack.getItem()).getItemType() == ELostTalesItem.Type.ARMOR_HEAVY)) {
                    names[armorSlot] = partOfSetBase;
                    setCounter++;
                    
                }
            }
            names[armorSlot] += itemStack.getDisplayName();
        } else {
            names[armorSlot] += emptySlot;
        }
        return setCounter;
    }

    private static void addUniqueItemDetails() {}

    //  LOTR Reflections
    public static LOTRFaction addFaction(String enumName, int color, LOTRDimension.DimensionRegion region, EnumSet<LOTRFaction.FactionType> types) {
        return addFaction(enumName, color, LOTRDimension.MIDDLE_EARTH, region, true, true, Integer.MIN_VALUE, null, types);
    }

    public static LOTRFaction addFaction(String enumName, int color, LOTRDimension dim, LOTRDimension.DimensionRegion region, boolean player, boolean registry, int alignment, LOTRMapRegion mapInfo, EnumSet<LOTRFaction.FactionType> types) {
        Class<?>[] classArr = {int.class, LOTRDimension.class, LOTRDimension.DimensionRegion.class, boolean.class, boolean.class, int.class, LOTRMapRegion.class, EnumSet.class};
        Object[] args = {color, dim, region, player, registry, alignment, mapInfo, types};

        return EnumHelper.addEnum(LOTRFaction.class, enumName, classArr, args);
    }

    public static LOTRSpawnList newLOTRSpawnList (LOTRSpawnEntry... entries) {
        return findAndInvokeConstructor (new Object[]{entries}, LOTRSpawnList.class, LOTRSpawnEntry[].class);
    }

    public static LOTRDimension.DimensionRegion addDimensionRegion(String enumName, String regionName) {
        Class<?>[] classArr = {String.class};
        Object[] args = {regionName};

        return EnumHelper.addEnum(LOTRDimension.DimensionRegion.class, enumName, classArr, args);
    }

    public static LOTRAchievement.Category addAchievementCategory(String enumName, LOTRFaction faction) {
        Class<?>[] classArr = {LOTRFaction.class};
        Object[] args = {faction};

        return EnumHelper.addEnum(LOTRAchievement.Category.class, enumName, classArr, args);
    }

    public static LOTRAchievement.Category addAchievementCategory(String enumName, LOTRBiome biome) {
        Class<?>[] classArr = {LOTRBiome.class};
        Object[] args = {biome};

        return EnumHelper.addEnum(LOTRAchievement.Category.class, enumName, classArr, args);
    }

    public static void setFactionAchievementCategory (LOTRFaction faction, LOTRAchievement.Category category) {
        ReflectionHelper.setPrivateValue (LOTRFaction.class, faction, category, "achieveCategory");
    }

    public static LOTRFactionRank addFactionRank (LOTRFaction faction, float alignment, String name, boolean gendered) {
        return findAndInvokeMethod (new Object[]{alignment, name, gendered}, LOTRFaction.class, faction, "addRank", float.class, String.class, boolean.class);
    }

    public static LOTRWaypoint addWaypoint(String name, LOTRWaypoint.Region region, LOTRFaction faction, double x, double z, boolean hidden) {
        Class<?>[] classArr = {LOTRWaypoint.Region.class, LOTRFaction.class, double.class, double.class, boolean.class};
        Object[] args = {region, faction, x, z, hidden};

        return EnumHelper.addEnum(LOTRWaypoint.class, name, classArr, args);
    }

    public static void setWorldGenMapImage (ResourceLocation res) {
        BufferedImage img = getImage(getInputStream(res));
        LOTRGenLayerWorld.imageWidth = img.getWidth();
        LOTRGenLayerWorld.imageHeight = img.getHeight();

        int[] colors = img.getRGB(0, 0, LOTRGenLayerWorld.imageWidth, LOTRGenLayerWorld.imageHeight, null, 0, LOTRGenLayerWorld.imageWidth);
        byte[] biomeImageData = new byte[LOTRGenLayerWorld.imageWidth * LOTRGenLayerWorld.imageHeight];

        for (int i = 0; i < colors.length; ++i) {
            int color = colors[i];
            Integer biomeID = LOTRDimension.MIDDLE_EARTH.colorsToBiomeIDs.get (color);
            if (biomeID != null) {
                biomeImageData[i] = (byte) biomeID.intValue ();
                continue;
            }
            System.out.println("Found unknown biome on map: " + Integer.toHexString (color) + " at location: " + (i % LOTRGenLayerWorld.imageWidth) + ", " + (i / LOTRGenLayerWorld.imageWidth));
            biomeImageData[i] = (byte) LOTRBiome.ocean.biomeID;
        }
        ReflectionHelper.setPrivateValue(LOTRGenLayerWorld.class, null, biomeImageData, "biomeImageData");
    }

    @SideOnly(Side.CLIENT)
    public static void setClientMapImage (ResourceLocation mapTexture) {
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null, mapTexture, "mapTexture");

        ResourceLocation sepiaMapTexture;
        try {
            BufferedImage mapImage = getImage(Minecraft.getMinecraft ().getResourceManager ().getResource (mapTexture).getInputStream ());
            sepiaMapTexture = findAndInvokeMethod (new Object[]{mapImage, new ResourceLocation ("lotr:map_sepia")}, LOTRTextures.class, null, "convertToSepia", BufferedImage.class, ResourceLocation.class);
        }
        catch (IOException e) {
            FMLLog.severe("Failed to generate LOTR sepia map", new Object[0]);
            e.printStackTrace();
            sepiaMapTexture = mapTexture;
        }
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null, sepiaMapTexture, "sepiaMapTexture");
    }


    public static BufferedImage getImage(InputStream in) {
        try {
            return ImageIO.read(in);
        }
        catch(IOException e) {
            System.out.println("Failed to convert a input stream into a buffered image.");
        }
        finally {
            try {
                in.close();
            }
            catch(IOException e) {}
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    public static ResourceLocation getTextureResourceLocation(InputStream in, String textureName) {
        BufferedImage img = getImage(in);
        if(img != null) {
            return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(textureName, new DynamicTexture(img));
        }
        return null;
    }

    public static InputStream getInputStream(ResourceLocation res) {
        return getInputStream(getContainer(res), getPath(res));
    }

    private static InputStream getInputStream(ModContainer container, String path) {
        return container.getClass().getResourceAsStream(path);
    }

    private static String getPath(ResourceLocation res) {
        return "/assets/" + res.getResourceDomain() + "/" + res.getResourcePath();
    }

    private static ModContainer getContainer(ResourceLocation res) {
        ModContainer modContainer = Loader.instance().getIndexedModList().get(res.getResourceDomain());
        if(modContainer == null) throw new IllegalArgumentException("Can't find the mod container for the domain " + res.getResourceDomain());
        return modContainer;
    }

    public static void clearControlZones (LOTRFaction faction) {
        ReflectionHelper.setPrivateValue (LOTRFaction.class, faction, new ArrayList<LOTRControlZone>(), "controlZones");
    }

    public static LOTRWaypoint.Region addWaypointRegion(String name) {
        Class<?>[] classArr = {};
        Object[] args = {};

        return EnumHelper.addEnum(LOTRWaypoint.Region.class, name, classArr, args);
    }

    public static void addControlZone (LOTRFaction faction, LOTRControlZone zone) {
        findAndInvokeMethod (zone, LOTRFaction.class, faction, "addControlZone", LOTRControlZone.class);
    }

    public static LOTRMapLabels addMapLabel(String enumName, LOTRBiome biomeLabel, int x, int y, float scale, int angle, float zoomMin, float zoomMan) {
        return addMapLabel(enumName, (Object) biomeLabel, x, y, scale, angle, zoomMin, zoomMan);
    }

    public static LOTRMapLabels addMapLabel(String enumName, String stringLabel, int x, int y, float scale, int angle, float zoomMin, float zoomMan) {
        return addMapLabel(enumName, (Object) stringLabel, x, y, scale, angle, zoomMin, zoomMan);
    }

    private static LOTRMapLabels addMapLabel(String enumName, Object label, int x, int y, float scale, int angle, float zoomMin, float zoomMan) {
        Class<?>[] classArr = {Object.class, int.class, int.class, float.class, int.class, float.class, float.class};
        Object[] args = {label, x, y, scale, angle, zoomMin, zoomMan};

        return EnumHelper.addEnum(LOTRMapLabels.class, enumName, classArr, args);
    }

    private static <E> Constructor<E> findConstructor (Class<E> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredConstructor (parameterTypes);
        }
        catch (NoSuchMethodException | SecurityException e) {
            System.out.println("Error when getting constructor from class " + clazz.getSimpleName () + " with parameters " + parameterTypes);
            e.printStackTrace ();
        }
        return null;
    }

    private static <T, E> T findAndInvokeMethod (Class<? super E> clazz, E instance, String methodName) {
        return findAndInvokeMethod (new Object[] {}, clazz, instance, methodName);
    }

    private static <T, E> T findAndInvokeMethod (Object arg, Class<? super E> clazz, E instance, String methodName, Class<?>... methodTypes) {
        return findAndInvokeMethod (new Object[]{arg}, clazz, instance, new String[]{methodName}, methodTypes);
    }

    private static <T, E> T findAndInvokeMethod (Object[] arg, Class<? super E> clazz, E instance, String methodName, Class<?>... methodTypes) {
        return findAndInvokeMethod (arg, clazz, instance, new String[]{methodName}, methodTypes);
    }

    @SuppressWarnings("unchecked")
    private static <T, E> T findAndInvokeMethod (Object[] args, Class<? super E> clazz, E instance, String[] methodNames, Class<?>... methodTypes) {
        Method addControlZoneMethod = ReflectionHelper.findMethod (clazz, instance, methodNames, methodTypes);
        try {
            return (T) addControlZoneMethod.invoke (instance, args);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            System.out.println("Error when getting method " + methodNames[0] + " from class " + clazz.getSimpleName ());e.printStackTrace ();
        }
        return null;
    }

    private static <E> E findAndInvokeConstructor (String className, Class<?>... parameterTypes) {
        return findAndInvokeConstructor (new Object[] {}, className, parameterTypes);
    }

    private static <E> E findAndInvokeConstructor (Object[] args, String className, Class<?>... parameterTypes) {
        try {
            return findAndInvokeConstructor (args, (Class<? extends E>) Class.forName (className), parameterTypes);
        }
        catch (ClassNotFoundException e) {
            System.out.println("Error when finding class " + className + " for a constructor.");
            e.printStackTrace ();
        }
        return null;
    }

    private static <E> E findAndInvokeConstructor (Class<E> clazz, Object... args) {
        Class<?>[] paramaterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramaterTypes[i] = args[i].getClass ();
        }
        return findAndInvokeConstructor (args, clazz, paramaterTypes);
    }

    private static <E> E findAndInvokeConstructor (Object[] args, Class<E> clazz, Class<?>... parameterTypes) {
        Constructor<E> constructor = findConstructor(clazz, parameterTypes);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance (args);
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            System.out.println("Error when initializing constructor from class " + clazz.getSimpleName () + " with parameters " + args);
        }
        return null;
    }
}