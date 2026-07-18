package com.ninuna.losttales.client.accessory;

import com.ninuna.losttales.accessory.AccessoryBootstrap;
import com.ninuna.losttales.accessory.AccessoryDefinition;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Client-only, server-gated perception effect for the One Ring wearer. */
public final class WraithWorldVisualEffect {

    private static final ResourceLocation VERTEX_SHADER =
            new ResourceLocation("losttales", "shaders/wraith_world.vert");
    private static final ResourceLocation FRAGMENT_SHADER =
            new ResourceLocation("losttales", "shaders/wraith_world.frag");
    private static final float ENTER_STEP = 0.065F;
    private static final float EXIT_STEP = 0.10F;

    private static float previousIntensity;
    private static float intensity;
    private static long visualTicks;
    private static int shaderProgram;
    private static int sceneUniform = -1;
    private static int previousSceneUniform = -1;
    private static int intensityUniform = -1;
    private static int timeUniform = -1;
    private static int aspectUniform = -1;
    private static int texelSizeUniform = -1;
    private static int copiedFrameTexture;
    private static int historyFrameTexture;
    private static int copiedWidth;
    private static int copiedHeight;
    private static boolean historyReady;
    private static boolean shaderFailed;

    private WraithWorldVisualEffect() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        previousIntensity = intensity;
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean active = hasOwnerVisualEffect(minecraft);
        float step = active ? ENTER_STEP : EXIT_STEP;
        intensity = approach(intensity, active ? 1.0F : 0.0F, step);
        if (intensity > 0.0F) {
            visualTicks++;
        } else {
            visualTicks = 0L;
            historyReady = false;
        }
        WraithWorldVeilField.update(minecraft, active, intensity);
    }

    public static void applyFogColors(EntityViewRenderEvent.FogColors event) {
        if (event == null) {
            return;
        }
        float amount = interpolatedIntensity((float)event.renderPartialTicks);
        if (amount <= 0.001F) {
            return;
        }
        float luminance = event.red * 0.299F
                + event.green * 0.587F + event.blue * 0.114F;
        float pulse = 0.5F + 0.5F * (float)Math.sin(
                (visualTicks + event.renderPartialTicks) * 0.055D);
        float targetRed = luminance * 0.44F + 0.035F * pulse;
        float targetGreen = luminance * 0.66F + 0.085F * pulse;
        float targetBlue = luminance * 0.78F + 0.13F * pulse;
        float blend = amount * 0.82F;
        event.red = mix(event.red, targetRed, blend);
        event.green = mix(event.green, targetGreen, blend);
        event.blue = mix(event.blue, targetBlue, blend);
    }

    /** Renders after the world and hand, but before the normal HUD. */
    public static void render(RenderGameOverlayEvent.Pre event) {
        if (event == null
                || event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        float amount = interpolatedIntensity(event.partialTicks);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (amount <= 0.001F || minecraft == null
                || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }
        if (!renderShader(minecraft, event.resolution, amount,
                event.partialTicks)) {
            renderFallback(event.resolution, amount, event.partialTicks);
        }
    }

    public static void reset() {
        previousIntensity = 0.0F;
        intensity = 0.0F;
        visualTicks = 0L;
        historyReady = false;
        WraithWorldVeilField.clear();
    }

    /** Rebuilds shader objects lazily after a resource-pack reload. */
    public static void onResourceManagerReload() {
        deleteGlResources();
        shaderFailed = false;
    }

    static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(target, value + Math.max(0.0F, step));
        }
        return Math.max(target, value - Math.max(0.0F, step));
    }

    private static boolean hasOwnerVisualEffect(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return false;
        }
        AccessoryDefinition definition = ClientAccessoryEffectCache
                .getDefinition(minecraft.thePlayer);
        return definition != null
                && AccessoryBootstrap.WRAITH_WORLD_VISUAL_EFFECT_ID.equals(
                definition.getOwnerVisualEffectId());
    }

    private static float interpolatedIntensity(float partialTicks) {
        float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
        return previousIntensity
                + (intensity - previousIntensity) * partial;
    }

    private static boolean renderShader(
            Minecraft minecraft, ScaledResolution resolution,
            float amount, float partialTicks) {
        if (shaderFailed || !OpenGlHelper.shadersSupported
                || !OpenGlHelper.isFramebufferEnabled()
                || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0) {
            return false;
        }
        Framebuffer framebuffer = minecraft.getFramebuffer();
        if (framebuffer == null || framebuffer.framebufferObject <= 0) {
            return false;
        }
        try {
            ensureShader(minecraft);
            ensureCopyTexture(
                    minecraft.displayWidth, minecraft.displayHeight);
            copyCurrentFrame(copiedFrameTexture,
                    minecraft.displayWidth, minecraft.displayHeight);
            if (!historyReady) {
                copyCurrentFrame(historyFrameTexture,
                        minecraft.displayWidth, minecraft.displayHeight);
                historyReady = true;
            }
            drawShaderFrame(
                    resolution, minecraft.displayWidth,
                    minecraft.displayHeight, amount, partialTicks);
            copyCurrentFrame(historyFrameTexture,
                    minecraft.displayWidth, minecraft.displayHeight);
            return true;
        } catch (Throwable throwable) {
            shaderFailed = true;
            deleteGlResources();
            FMLLog.warning("[losttales] Wraith-world shader disabled; "
                    + "using compatibility overlay: %s",
                    throwable.toString());
            return false;
        }
    }

    private static void ensureShader(Minecraft minecraft)
            throws IOException {
        if (shaderProgram != 0) {
            return;
        }
        int vertex = compileShader(
                GL20.GL_VERTEX_SHADER, readShader(minecraft, VERTEX_SHADER));
        int fragment = 0;
        try {
            fragment = compileShader(GL20.GL_FRAGMENT_SHADER,
                    readShader(minecraft, FRAGMENT_SHADER));
            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertex);
            GL20.glAttachShader(shaderProgram, fragment);
            GL20.glLinkProgram(shaderProgram);
            if (GL20.glGetProgrami(
                    shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("link failed: "
                        + GL20.glGetProgramInfoLog(shaderProgram, 4096));
            }
            sceneUniform = GL20.glGetUniformLocation(
                    shaderProgram, "scene");
            previousSceneUniform = GL20.glGetUniformLocation(
                    shaderProgram, "previousScene");
            intensityUniform = GL20.glGetUniformLocation(
                    shaderProgram, "effectIntensity");
            timeUniform = GL20.glGetUniformLocation(shaderProgram, "time");
            aspectUniform = GL20.glGetUniformLocation(
                    shaderProgram, "aspectRatio");
            texelSizeUniform = GL20.glGetUniformLocation(
                    shaderProgram, "texelSize");
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(
                shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("compile failed: " + log);
        }
        return shader;
    }

    private static String readShader(
            Minecraft minecraft, ResourceLocation location)
            throws IOException {
        IResource resource = minecraft.getResourceManager()
                .getResource(location);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8));
        try {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            return source.toString();
        } finally {
            reader.close();
        }
    }

    private static void ensureCopyTexture(int width, int height) {
        if (copiedFrameTexture == 0) {
            copiedFrameTexture = GL11.glGenTextures();
        }
        if (historyFrameTexture == 0) {
            historyFrameTexture = GL11.glGenTextures();
        }
        if (copiedWidth == width && copiedHeight == height) {
            return;
        }
        copiedWidth = width;
        copiedHeight = height;
        allocateFrameTexture(copiedFrameTexture, width, height);
        allocateFrameTexture(historyFrameTexture, width, height);
        historyReady = false;
    }

    private static void allocateFrameTexture(
            int texture, int width, int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB,
                width, height, 0, GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
    }

    private static void copyCurrentFrame(
            int texture, int width, int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                0, 0, 0, 0, width, height);
    }

    private static void drawShaderFrame(
            ScaledResolution resolution, int displayWidth,
            int displayHeight, float amount, float partialTicks) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, resolution.getScaledWidth(),
                resolution.getScaledHeight(), 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glViewport(0, 0, displayWidth, displayHeight);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL20.glUseProgram(shaderProgram);
            GL20.glUniform1i(sceneUniform, 0);
            GL20.glUniform1i(previousSceneUniform, 1);
            GL20.glUniform1f(intensityUniform, amount);
            GL20.glUniform1f(timeUniform,
                    (visualTicks + partialTicks) / 20.0F);
            GL20.glUniform1f(aspectUniform,
                    displayWidth / (float)Math.max(1, displayHeight));
            GL20.glUniform2f(texelSizeUniform,
                    1.0F / Math.max(1, displayWidth),
                    1.0F / Math.max(1, displayHeight));
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyFrameTexture);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, copiedFrameTexture);
            drawTexturedQuad(resolution.getScaledWidth(),
                    resolution.getScaledHeight());
        } finally {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL20.glUseProgram(previousProgram);
            GL11.glDepthMask(true);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
    }

    private static void drawTexturedQuad(int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        tessellator.addVertexWithUV(0.0D, height, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV(width, height, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(width, 0.0D, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 1.0D);
        tessellator.draw();
    }

    private static void renderFallback(
            ScaledResolution resolution, float amount,
            float partialTicks) {
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        float pulse = 0.5F + 0.5F * (float)Math.sin(
                (visualTicks + partialTicks) * 0.055D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            drawSolidRect(0, 0, width, height,
                    0.16F, 0.38F, 0.48F,
                    amount * (0.12F + pulse * 0.035F));
            int edgeX = Math.max(24, width / 4);
            int edgeY = Math.max(18, height / 4);
            drawHorizontalGradient(0, 0, edgeX, height,
                    amount * 0.64F, 0.0F);
            drawHorizontalGradient(width - edgeX, 0, width, height,
                    0.0F, amount * 0.64F);
            drawVerticalGradient(0, 0, width, edgeY,
                    amount * 0.52F, 0.0F);
            drawVerticalGradient(0, height - edgeY, width, height,
                    0.0F, amount * 0.52F);
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
        }
    }

    private static void drawSolidRect(
            double left, double top, double right, double bottom,
            float red, float green, float blue, float alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
    }

    private static void drawHorizontalGradient(
            double left, double top, double right, double bottom,
            float leftAlpha, float rightAlpha) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glShadeModel(GL11.GL_SMOOTH);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.01F, 0.025F, 0.04F, leftAlpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.setColorRGBA_F(0.01F, 0.025F, 0.04F, rightAlpha);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private static void drawVerticalGradient(
            double left, double top, double right, double bottom,
            float topAlpha, float bottomAlpha) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glShadeModel(GL11.GL_SMOOTH);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.01F, 0.025F, 0.04F, bottomAlpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.setColorRGBA_F(0.01F, 0.025F, 0.04F, topAlpha);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private static void deleteGlResources() {
        try {
            if (shaderProgram != 0) {
                GL20.glDeleteProgram(shaderProgram);
            }
            if (copiedFrameTexture != 0) {
                GL11.glDeleteTextures(copiedFrameTexture);
            }
            if (historyFrameTexture != 0) {
                GL11.glDeleteTextures(historyFrameTexture);
            }
        } catch (Throwable ignored) {
            // The OpenGL context may already be gone during client shutdown.
        }
        shaderProgram = 0;
        sceneUniform = -1;
        previousSceneUniform = -1;
        intensityUniform = -1;
        timeUniform = -1;
        aspectUniform = -1;
        texelSizeUniform = -1;
        copiedFrameTexture = 0;
        historyFrameTexture = 0;
        copiedWidth = 0;
        copiedHeight = 0;
        historyReady = false;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, amount));
    }
}
