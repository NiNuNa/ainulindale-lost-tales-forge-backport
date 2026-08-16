package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.chat.LostTalesChatGui;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

/** Safe automatic opening fade/blur plus lifecycle for opt-in transforms. */
public final class LostTalesGuiAnimationHandler
        implements IResourceManagerReloadListener {
    private final LostTalesGuiBlurRenderer blurRenderer =
            new LostTalesGuiBlurRenderer();
    private GuiScreen currentScreen;
    private LostTalesGuiAnimationProfile currentProfile =
            LostTalesGuiAnimationProfile.NONE;
    private boolean contentTransformPushed;

    public LostTalesGuiAnimationHandler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen screen = event == null ? null : event.gui;
        LostTalesControlBarAnimation.onScreenOpened(screen);
        LostTalesGuiAnimationProfile nextProfile = profileFor(screen);
        boolean preserveBackdrop = screen != null
                && this.currentScreen != null
                && this.currentProfile.isEnabled()
                && nextProfile.isEnabled()
                && Minecraft.getMinecraft().theWorld != null;
        this.currentScreen = screen;
        this.currentProfile = nextProfile;
        if (screen == null || !this.currentProfile.isEnabled()) {
            LostTalesGuiAnimations.clear();
            this.blurRenderer.release();
            return;
        }
        int duration = animationDuration();
        LostTalesGuiAnimations.begin(screen, this.currentProfile,
                duration, LostTalesConfig.guiBackgroundFadeTimeMillis,
                LostTalesConfig.reducedGuiMotion, preserveBackdrop);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!isCurrent(event == null ? null : event.gui)) {
            return;
        }
        LostTalesGuiAnimationSample sample =
                LostTalesGuiAnimations.sample(event.gui);
        boolean blurEnabled = (this.currentProfile.isBlurBackground()
                || LostTalesConfig.guiAlwaysBlur)
                && LostTalesConfig.enableGuiBackgroundBlur
                && Minecraft.getMinecraft().theWorld != null;
        if (blurEnabled) {
            float strength = (float)LostTalesConfig.guiBlurStrength
                    * sample.getBackdropProgress();
            this.blurRenderer.render(Minecraft.getMinecraft(),
                    event.renderPartialTicks, strength);
        }
        if (LostTalesConfig.enableGuiBackground
                && LostTalesGuiAnimations.isManagingBackdrop(event.gui)) {
            int alpha = Math.round(
                    255.0F * (float)LostTalesConfig.guiBackgroundOpacity
                            * sample.getBackdropProgress());
            if (alpha > 0) {
                Gui.drawRect(0, 0, event.gui.width, event.gui.height,
                        (Math.min(255, alpha) << 24));
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void transformDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        this.contentTransformPushed = false;
        if (!isCurrent(event == null ? null : event.gui)) {
            return;
        }
        if (!LostTalesGuiAnimations.isContentTransformActive(event.gui)) {
            return;
        }
        LostTalesGuiAnimations.pushContentTransform(
                event.gui, event.gui.width, event.gui.height);
        this.contentTransformPushed = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (this.contentTransformPushed) {
            LostTalesGuiAnimations.popContentTransform();
            this.contentTransformPushed = false;
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.blurRenderer.resetAfterResourceReload();
    }

    @SubscribeEvent
    public void onClientDisconnect(
            ClientDisconnectionFromServerEvent event) {
        clear();
    }

    public void clear() {
        this.currentScreen = null;
        this.currentProfile = LostTalesGuiAnimationProfile.NONE;
        this.contentTransformPushed = false;
        LostTalesGuiAnimations.clear();
        this.blurRenderer.release();
    }

    private boolean isCurrent(GuiScreen screen) {
        return screen != null && screen == this.currentScreen
                && this.currentProfile.isEnabled()
                && LostTalesConfig.enableGuiAnimations;
    }

    private static LostTalesGuiAnimationProfile profileFor(
            GuiScreen screen) {
        if (!LostTalesConfig.enableGuiAnimations || screen == null
                || Minecraft.getMinecraft().theWorld == null
                || isExcluded(screen)) {
            return LostTalesGuiAnimationProfile.NONE;
        }
        if (screen instanceof LostTalesGuiAnimationOptions) {
            LostTalesGuiAnimationProfile profile =
                    ((LostTalesGuiAnimationOptions)screen)
                            .getLostTalesGuiAnimationProfile();
            return profile == null
                    ? LostTalesGuiAnimationProfile.NONE : profile;
        }
        return LostTalesGuiAnimationProfile.DEFAULT;
    }

    private static boolean isExcluded(GuiScreen screen) {
        return screen instanceof GuiChat
                || screen instanceof LostTalesChatGui
                || screen instanceof GuiDownloadTerrain
                || screen instanceof GuiGameOver
                || screen instanceof GuiSleepMP;
    }

    private static int animationDuration() {
        int duration = Math.max(10,
                LostTalesConfig.guiAnimationDurationMillis);
        if (LostTalesConfig.reducedGuiMotion) {
            duration = Math.min(duration, 90);
        }
        return Math.max(1, duration);
    }
}
