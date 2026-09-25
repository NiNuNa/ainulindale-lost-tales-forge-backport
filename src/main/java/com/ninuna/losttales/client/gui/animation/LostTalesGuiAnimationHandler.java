package com.ninuna.losttales.client.gui.animation;

import com.ninuna.losttales.client.LostTalesClientThread;
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

/**
 * Safe automatic opening fade/blur plus lifecycle for opt-in transforms.
 * The veil and the blur are the screen's look and answer to the Screen
 * Backgrounds options alone; the motion settings only time how they and
 * the content arrive, so with motion off they are simply there at once.
 */
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
        LostTalesGuiAnimations.begin(screen, this.currentProfile,
                preserveBackdrop);
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
        LostTalesGuiRegionBlur.getInstance().resetAfterResourceReload();
    }

    /** Fired on the network thread; the textures released here are the client thread's. */
    @SubscribeEvent
    public void onClientDisconnect(
            ClientDisconnectionFromServerEvent event) {
        LostTalesClientThread.run(new Runnable() {
            @Override
            public void run() {
                clear();
            }
        });
    }

    public void clear() {
        this.currentScreen = null;
        this.currentProfile = LostTalesGuiAnimationProfile.NONE;
        this.contentTransformPushed = false;
        LostTalesGuiAnimations.clear();
        this.blurRenderer.release();
        LostTalesGuiRegionBlur.getInstance().release();
    }

    private boolean isCurrent(GuiScreen screen) {
        return screen != null && screen == this.currentScreen
                && this.currentProfile.isEnabled();
    }

    private static LostTalesGuiAnimationProfile profileFor(
            GuiScreen screen) {
        if (screen == null
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

    /**
     * Screens the automatic fade never touches. The chat is one of them,
     * and the window screen, which is the game's chat screen underneath:
     * it is opened and closed constantly during play and is meant to read
     * over the world rather than in front of it, so the full-screen fade
     * and blur leave it alone; its windows carry their own opening motion
     * and blur only their own boxes ({@link LostTalesGuiRegionBlur}).
     */
    private static boolean isExcluded(GuiScreen screen) {
        return screen instanceof GuiChat
                || screen instanceof GuiDownloadTerrain
                || screen instanceof GuiGameOver
                || screen instanceof GuiSleepMP;
    }
}
