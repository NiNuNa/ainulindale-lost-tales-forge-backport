package com.ninuna.losttales.util;

import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.armor.LostTalesItemArmorBase;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRMapLabels;
import lotr.common.world.biome.LOTRBiome;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import org.lwjgl.input.Keyboard;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Physical-client-only helpers formerly mixed into LostTalesUtil.
 * Keeping these references in a separate class prevents common LOTR setup from
 * loading Minecraft or LOTR client classes on a dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientUtil {

    private LostTalesClientUtil() {}

    public static void addItemInformation(List list, ItemStack itemStack,
                                          ELostTalesItemMaterial material, String credits,
                                          EntityPlayer player, ELostTalesItem.Type itemType) {
        addItemLore(list, getItemLore(itemStack));
        addItemDetails(list, itemStack, credits, material, itemType);
        if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
            addArmorSetInformation(itemStack, player, list, "Test Bonus!");
        }
    }

    public static void addItemBlockInformation(List list, ItemStack itemStack,
                                               Block block, ELostTalesUser credits) {
        addItemLore(list, getItemLore(itemStack));
        addItemBlockDetails(list, itemStack, block, credits);
    }

    public static String[] getItemLore(ItemStack itemStack) {
        return new String[]{I18n.format(itemStack.getUnlocalizedName() + ".lore.line1"),
                I18n.format(itemStack.getUnlocalizedName() + ".lore.line2")};
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

    public static void addItemDetails(List list, ItemStack itemStack, String credits,
                                      ELostTalesItemMaterial material,
                                      ELostTalesItem.Type itemType) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            list.add("");
            list.add("Faction: §f§o" + I18n.format("lotr.faction."
                    + material.getFaction().getFaction().codeName() + ".name"));
            if (material.getMaterial() != null) {
                list.add("Repair Item: §f§o"
                        + material.getMaterial().getRepairItem().getDisplayName());
            }
            list.add("Type: §f§o" + itemType.getName());
            if (credits != null) {
                list.add("§8Created by: §o" + credits);
            }
        } else {
            list.add("");
            list.add("Hold §f[CTRL] §r§7to view item details.");
        }
    }

    public static void addItemBlockDetails(List list, ItemStack itemStack,
                                           Block block, ELostTalesUser credits) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            list.add("");
            if (block instanceof LostTalesBlockPlushie) {
                LostTalesBlockPlushie plushie = (LostTalesBlockPlushie)block;
                list.add("Rarity: " + plushie.getRarity().rarityColor
                        + "§o" + plushie.getRarity().rarityName);
            }
            list.add("Type: §f§o" + ELostTalesItem.Type.BLOCK_BUILDING.getName());
            if (credits != ELostTalesUser.NULL) {
                list.add("§8Created by: §o" + credits.getName());
            }
        } else {
            list.add("");
            list.add("Hold §f[CTRL] §r§7to view item details.");
        }
    }

    public static void addArmorSetInformation(ItemStack itemStack, EntityPlayer player,
                                              List list, String setBonusDescription) {
        ItemStack helmet = player.getCurrentArmor(3);
        ItemStack armor = player.getCurrentArmor(2);
        ItemStack leggings = player.getCurrentArmor(1);
        ItemStack boots = player.getCurrentArmor(0);
        String infoTextBase = "§8>§o ";
        String[] names = new String[]{infoTextBase, infoTextBase, infoTextBase, infoTextBase};
        int setCounter = 0;

        if (itemStack == helmet || itemStack == armor || itemStack == leggings || itemStack == boots) {
            list.add("");
            if (LostTalesKeyBindings.isModifierKeyDown()) {
                if (itemStack.getItem() instanceof LostTalesItemArmorBase) {
                    boolean heavy = ((LostTalesItemArmorBase)itemStack.getItem()).getItemType()
                            == ELostTalesItem.Type.ARMOR_HEAVY;
                    setCounter = displayArmorSetDetails(heavy, names, helmet, armor, leggings, boots);
                    list.add("§e[" + setCounter + "/4] §r§7"
                            + (heavy ? "Heavy" : "Light") + " Armor Set:");
                }
                Collections.addAll(list, names);
                if (setCounter == 4) {
                    list.add("");
                    boolean heavy = ((LostTalesItemArmorBase)itemStack.getItem()).getItemType()
                            == ELostTalesItem.Type.ARMOR_HEAVY;
                    list.add("§e[4/4] §r§7" + (heavy ? "Heavy" : "Light")
                            + " Armor Set Bonus:");
                    list.add(setBonusDescription.isEmpty()
                            ? "§e§oNo set bonus!" : "§e§o" + setBonusDescription);
                }
            } else {
                list.add("Hold §e[" + LostTalesKeyBindings.getModifierKeyDisplayName()
                        + "] §r§7to view armor set information.");
            }
        }
    }

    private static int displayArmorSetDetails(boolean heavy, String[] names,
                                              ItemStack helmet, ItemStack armor,
                                              ItemStack leggings, ItemStack boots) {
        int count = 0;
        count = getArmorSetDetails(helmet, heavy, names, count, 0);
        count = getArmorSetDetails(armor, heavy, names, count, 1);
        count = getArmorSetDetails(leggings, heavy, names, count, 2);
        return getArmorSetDetails(boots, heavy, names, count, 3);
    }

    private static int getArmorSetDetails(ItemStack stack, boolean heavy,
                                          String[] names, int count, int slot) {
        if (stack != null) {
            if (stack.getItem() instanceof LostTalesItemArmorBase
                    && heavy == (((LostTalesItemArmorBase)stack.getItem()).getItemType()
                    == ELostTalesItem.Type.ARMOR_HEAVY)) {
                names[slot] = "§e>§o ";
                count++;
            }
            names[slot] += stack.getDisplayName();
        } else {
            names[slot] += "Empty";
        }
        return count;
    }

    public static void setClientMapImage(ResourceLocation mapTexture) {
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null, mapTexture, "mapTexture");
        ResourceLocation sepiaMapTexture;
        try {
            BufferedImage mapImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(mapTexture).getInputStream());
            sepiaMapTexture = invokeStatic(new Object[]{mapImage,
                            new ResourceLocation("lotr:map_sepia")},
                    LOTRTextures.class, "convertToSepia",
                    BufferedImage.class, ResourceLocation.class);
        } catch (IOException exception) {
            FMLLog.severe("Failed to generate LOTR sepia map: %s", exception.toString());
            sepiaMapTexture = mapTexture;
        }
        if (sepiaMapTexture == null) {
            sepiaMapTexture = mapTexture;
        }
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                sepiaMapTexture, "sepiaMapTexture");
    }

    public static void setClientMapImageWithOverlay(ResourceLocation baseMap,
                                                    ResourceLocation overlayMap) {
        BufferedImage mergedMapImage = getMergedClientMapImage(baseMap, overlayMap);
        if (mergedMapImage == null) {
            setClientMapImage(baseMap);
            return;
        }
        ResourceLocation mergedMapTexture = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("losttales_lotr_map", new DynamicTexture(mergedMapImage));
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                mergedMapTexture, "mapTexture");
        ResourceLocation sepiaMapTexture = invokeStatic(new Object[]{mergedMapImage,
                        new ResourceLocation("losttales:map_sepia")},
                LOTRTextures.class, "convertToSepia",
                BufferedImage.class, ResourceLocation.class);
        if (sepiaMapTexture == null) {
            sepiaMapTexture = mergedMapTexture;
        }
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                sepiaMapTexture, "sepiaMapTexture");
    }

    private static BufferedImage getMergedClientMapImage(ResourceLocation baseMap,
                                                          ResourceLocation overlayMap) {
        try {
            BufferedImage baseImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(baseMap).getInputStream());
            BufferedImage overlayImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(overlayMap).getInputStream());
            return mergeMapImages(baseImage, overlayImage);
        } catch (IOException exception) {
            FMLLog.severe("Failed to load Lost Tales map overlay: %s", exception.toString());
            return null;
        }
    }

    private static BufferedImage mergeMapImages(BufferedImage baseImage,
                                                 BufferedImage overlayImage) {
        if (baseImage == null) {
            return null;
        }
        if (overlayImage == null) {
            return baseImage;
        }
        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        if (overlayImage.getWidth() != width || overlayImage.getHeight() != height) {
            FMLLog.warning("Lost Tales map overlay size does not match the LOTR map size. Overlay will be ignored.");
            return baseImage;
        }
        int[] baseColors = baseImage.getRGB(0, 0, width, height, null, 0, width);
        int[] overlayColors = overlayImage.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < baseColors.length; i++) {
            baseColors[i] = blendOver(baseColors[i], overlayColors[i]);
        }
        BufferedImage merged = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        merged.setRGB(0, 0, width, height, baseColors, 0, width);
        return merged;
    }

    private static int blendOver(int baseColor, int overlayColor) {
        int alpha = overlayColor >>> 24;
        if (alpha <= 0) {
            return baseColor;
        }
        if (alpha >= 255) {
            return 0xFF000000 | overlayColor & 0x00FFFFFF;
        }
        int inverse = 255 - alpha;
        int red = (((overlayColor >> 16) & 255) * alpha
                + ((baseColor >> 16) & 255) * inverse) / 255;
        int green = (((overlayColor >> 8) & 255) * alpha
                + ((baseColor >> 8) & 255) * inverse) / 255;
        int blue = ((overlayColor & 255) * alpha
                + (baseColor & 255) * inverse) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public static ResourceLocation getTextureResourceLocation(InputStream input,
                                                               String textureName) {
        BufferedImage image = LostTalesUtil.getImage(input);
        return image == null ? null : Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation(textureName, new DynamicTexture(image));
    }

    public static LOTRMapLabels addMapLabel(String enumName, LOTRBiome biomeLabel,
                                            int x, int y, float scale, int angle,
                                            float zoomMin, float zoomMax) {
        return addMapLabel(enumName, (Object)biomeLabel, x, y, scale, angle, zoomMin, zoomMax);
    }

    public static LOTRMapLabels addMapLabel(String enumName, String stringLabel,
                                            int x, int y, float scale, int angle,
                                            float zoomMin, float zoomMax) {
        return addMapLabel(enumName, (Object)stringLabel, x, y, scale, angle, zoomMin, zoomMax);
    }

    private static LOTRMapLabels addMapLabel(String enumName, Object label,
                                             int x, int y, float scale, int angle,
                                             float zoomMin, float zoomMax) {
        Class<?>[] types = {Object.class, int.class, int.class, float.class,
                int.class, float.class, float.class};
        Object[] arguments = {label, x, y, scale, angle, zoomMin, zoomMax};
        return EnumHelper.addEnum(LOTRMapLabels.class, enumName, types, arguments);
    }

    @SuppressWarnings("unchecked")
    private static <T, E> T invokeStatic(Object[] arguments, Class<E> owner,
                                         String methodName, Class<?>... parameterTypes) {
        try {
            Method method = ReflectionHelper.findMethod(owner, (E)null,
                    new String[]{methodName}, parameterTypes);
            return (T)method.invoke(null, arguments);
        } catch (RuntimeException exception) {
            FMLLog.severe("Failed to resolve client compatibility method %s.%s: %s",
                    owner.getName(), methodName, exception.toString());
        } catch (IllegalAccessException exception) {
            FMLLog.severe("Cannot access client compatibility method %s.%s: %s",
                    owner.getName(), methodName, exception.toString());
        } catch (InvocationTargetException exception) {
            FMLLog.severe("Client compatibility method %s.%s failed: %s",
                    owner.getName(), methodName, exception.getCause());
        }
        return null;
    }
}
