package com.ninuna.losttales.util;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import javax.imageio.ImageIO;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRank;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRRoads;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.spawning.LOTRSpawnEntry;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;

public abstract class LostTalesUtil {

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

    public static void addRoad(String name, Object... routePoints) {
        findAndInvokeMethod(new Object[]{name, routePoints}, LOTRRoads.class, null, "registerRoad", String.class, Object[].class);
    }

    public static void addDisplayOnlyRoad(String name, Object... routePoints) {
        findAndInvokeMethod(new Object[]{name, routePoints}, LOTRRoads.class, null, "registerDisplayOnlyRoad", String.class, Object[].class);
    }

    public static void setWorldGenMapImage (ResourceLocation res) {
        BufferedImage img = getImage(getInputStream(res));
        setWorldGenMapImage(img, null, null);
    }

    public static void setWorldGenMapImageWithOverlay(ResourceLocation baseMap, ResourceLocation overlayMap, LOTRBiome overlayBiome) {
        BufferedImage baseImage = getImage(getInputStream(baseMap));
        BufferedImage overlayImage = getImage(getInputStream(overlayMap));
        setWorldGenMapImage(baseImage, overlayImage, null, null, overlayBiome);
    }

    public static void setWorldGenMapImageWithOverlayBiomes(ResourceLocation baseMap, ResourceLocation overlayMap, int[] overlayRgbColors, LOTRBiome[] overlayBiomes, LOTRBiome fallbackOverlayBiome) {
        BufferedImage baseImage = getImage(getInputStream(baseMap));
        BufferedImage overlayImage = getImage(getInputStream(overlayMap));
        setWorldGenMapImage(baseImage, overlayImage, overlayRgbColors, overlayBiomes, fallbackOverlayBiome);
    }

    private static void setWorldGenMapImage(BufferedImage baseImage, BufferedImage overlayImage, LOTRBiome overlayBiome) {
        setWorldGenMapImage(baseImage, overlayImage, null, null, overlayBiome);
    }

    private static void setWorldGenMapImage(BufferedImage baseImage, BufferedImage overlayImage, int[] overlayRgbColors, LOTRBiome[] overlayBiomes, LOTRBiome fallbackOverlayBiome) {
        if (baseImage == null) {
            FMLLog.severe("Failed to load LOTR world generation map image", new Object[0]);
            return;
        }

        LOTRGenLayerWorld.imageWidth = baseImage.getWidth();
        LOTRGenLayerWorld.imageHeight = baseImage.getHeight();

        int[] baseColors = baseImage.getRGB(0, 0, LOTRGenLayerWorld.imageWidth, LOTRGenLayerWorld.imageHeight, null, 0, LOTRGenLayerWorld.imageWidth);
        int[] overlayColors = getOverlayColors(overlayImage, LOTRGenLayerWorld.imageWidth, LOTRGenLayerWorld.imageHeight);
        byte[] biomeImageData = new byte[LOTRGenLayerWorld.imageWidth * LOTRGenLayerWorld.imageHeight];
        int unknownPixelCount = 0;
        int firstUnknownColor = 0;
        int firstUnknownX = -1;
        int firstUnknownY = -1;

        for (int i = 0; i < baseColors.length; ++i) {
            if (overlayColors != null && isVisibleOverlayPixel(overlayColors[i])) {
                LOTRBiome overlayBiome = getOverlayBiomeForPixel(overlayColors[i], overlayRgbColors, overlayBiomes, fallbackOverlayBiome);
                if (overlayBiome != null) {
                    biomeImageData[i] = (byte) overlayBiome.biomeID;
                    continue;
                }
            }

            int color = baseColors[i];
            Integer biomeID = LOTRDimension.MIDDLE_EARTH.colorsToBiomeIDs.get(color);
            if (biomeID != null) {
                biomeImageData[i] = (byte) biomeID.intValue();
                continue;
            }
            if (unknownPixelCount == 0) {
                firstUnknownColor = color;
                firstUnknownX = i % LOTRGenLayerWorld.imageWidth;
                firstUnknownY = i / LOTRGenLayerWorld.imageWidth;
            }
            unknownPixelCount++;
            biomeImageData[i] = (byte) LOTRBiome.ocean.biomeID;
        }
        if (unknownPixelCount > 0) {
            FMLLog.warning(
                    "Lost Tales world map contains %d unknown biome pixels; "
                            + "using ocean as a fallback. First: %08x at %d, %d.",
                    unknownPixelCount, firstUnknownColor,
                    firstUnknownX, firstUnknownY);
        }
        ReflectionHelper.setPrivateValue(LOTRGenLayerWorld.class, null, biomeImageData, "biomeImageData");
    }

    private static int[] getOverlayColors(BufferedImage overlayImage, int width, int height) {
        if (overlayImage == null) {
            return null;
        }
        if (overlayImage.getWidth() != width || overlayImage.getHeight() != height) {
            FMLLog.warning("Lost Tales map overlay size does not match the LOTR map size. Overlay will be ignored.", new Object[0]);
            return null;
        }
        return overlayImage.getRGB(0, 0, width, height, null, 0, width);
    }

    private static boolean isVisibleOverlayPixel(int overlayColor) {
        return ((overlayColor >> 24) & 255) > 0;
    }

    private static LOTRBiome getOverlayBiomeForPixel(int overlayColor, int[] overlayRgbColors, LOTRBiome[] overlayBiomes, LOTRBiome fallbackOverlayBiome) {
        if (overlayRgbColors == null || overlayBiomes == null || overlayRgbColors.length != overlayBiomes.length) {
            return fallbackOverlayBiome;
        }

        int rgb = overlayColor & 0x00FFFFFF;
        for (int i = 0; i < overlayRgbColors.length; i++) {
            if ((overlayRgbColors[i] & 0x00FFFFFF) == rgb) {
                return overlayBiomes[i];
            }
        }

        return fallbackOverlayBiome;
    }

    public static BufferedImage getImage(InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            return ImageIO.read(in);
        }
        catch(IOException e) {
            FMLLog.warning(
                    "Lost Tales could not decode an image resource: %s",
                    e.toString());
        }
        finally {
            try {
                in.close();
            }
            catch(IOException e) {
                FMLLog.warning(
                        "Lost Tales could not close an image resource: %s",
                        e.toString());
            }
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

    private static <E> Constructor<E> findConstructor(Class<E> clazz,
                                                        Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException exception) {
            FMLLog.severe("Lost Tales compatibility constructor not found: %s%s",
                    clazz.getName(), java.util.Arrays.toString(parameterTypes));
        } catch (SecurityException exception) {
            FMLLog.severe("Lost Tales cannot access compatibility constructor %s%s: %s",
                    clazz.getName(), java.util.Arrays.toString(parameterTypes),
                    exception.toString());
        }
        return null;
    }

    private static <T, E> T findAndInvokeMethod (Object arg, Class<? super E> clazz, E instance, String methodName, Class<?>... methodTypes) {
        return findAndInvokeMethod (new Object[]{arg}, clazz, instance, new String[]{methodName}, methodTypes);
    }

    private static <T, E> T findAndInvokeMethod (Object[] arg, Class<? super E> clazz, E instance, String methodName, Class<?>... methodTypes) {
        return findAndInvokeMethod (arg, clazz, instance, new String[]{methodName}, methodTypes);
    }

    @SuppressWarnings("unchecked")
    private static <T, E> T findAndInvokeMethod(Object[] args,
                                                 Class<? super E> clazz,
                                                 E instance,
                                                 String[] methodNames,
                                                 Class<?>... methodTypes) {
        try {
            Method method = ReflectionHelper.findMethod(clazz, instance,
                    methodNames, methodTypes);
            return (T)method.invoke(instance, args);
        } catch (RuntimeException exception) {
            FMLLog.severe("Lost Tales compatibility method not found: %s.%s%s (%s)",
                    clazz.getName(), methodNames[0],
                    java.util.Arrays.toString(methodTypes), exception.toString());
        } catch (IllegalAccessException exception) {
            FMLLog.severe("Lost Tales cannot access compatibility method %s.%s: %s",
                    clazz.getName(), methodNames[0], exception.toString());
        } catch (InvocationTargetException exception) {
            FMLLog.severe("Lost Tales compatibility method %s.%s failed: %s",
                    clazz.getName(), methodNames[0], exception.getCause());
        }
        return null;
    }

    private static <E> E findAndInvokeConstructor(Object[] args, Class<E> clazz,
                                                   Class<?>... parameterTypes) {
        Constructor<E> constructor = findConstructor(clazz, parameterTypes);
        if (constructor == null) {
            return null;
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InstantiationException exception) {
            FMLLog.severe("Lost Tales cannot instantiate compatibility class %s: %s",
                    clazz.getName(), exception.toString());
        } catch (IllegalAccessException exception) {
            FMLLog.severe("Lost Tales cannot access compatibility constructor %s: %s",
                    clazz.getName(), exception.toString());
        } catch (IllegalArgumentException exception) {
            FMLLog.severe("Lost Tales supplied invalid constructor arguments for %s: %s",
                    clazz.getName(), exception.toString());
        } catch (InvocationTargetException exception) {
            FMLLog.severe("Lost Tales compatibility constructor %s failed: %s",
                    clazz.getName(), exception.getCause());
        } catch (SecurityException exception) {
            FMLLog.severe("Lost Tales cannot make compatibility constructor accessible for %s: %s",
                    clazz.getName(), exception.toString());
        }
        return null;
    }
}
