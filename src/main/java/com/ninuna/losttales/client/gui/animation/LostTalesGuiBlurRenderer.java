package com.ninuna.losttales.client.gui.animation;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Defensive wrapper around a centered two-pass Gaussian blur shader. */
final class LostTalesGuiBlurRenderer {
    private static final ResourceLocation BLUR =
            new ResourceLocation(
                    "losttales", "shaders/post/gui_gaussian_blur.json");
    private static final Field SHADERS = findShadersField();

    private ShaderGroup shaderGroup;
    private int width = -1;
    private int height = -1;
    private boolean unavailable;
    private boolean failureLogged;

    boolean render(Minecraft minecraft, float partialTicks,
                   float strength) {
        if (this.unavailable || minecraft == null
                || minecraft.theWorld == null
                || minecraft.getFramebuffer() == null
                || !OpenGlHelper.isFramebufferEnabled()
                || strength <= 0.01F || SHADERS == null) {
            return false;
        }
        boolean attributesPushed = false;
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attributesPushed = true;
            ensureShader(minecraft);
            setRadius(Math.max(0.0F, Math.min(12.0F, strength)));
            this.shaderGroup.loadShaderGroup(partialTicks);
            minecraft.getFramebuffer().bindFramebuffer(true);
            GL11.glPopAttrib();
            attributesPushed = false;
            minecraft.entityRenderer.setupOverlayRendering();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            return true;
        } catch (Throwable failure) {
            if (attributesPushed) {
                try {
                    GL11.glPopAttrib();
                } catch (Throwable ignored) {
                    // Continue into the normal GUI fallback.
                }
            }
            disable(failure);
            try {
                minecraft.getFramebuffer().bindFramebuffer(true);
                minecraft.entityRenderer.setupOverlayRendering();
            } catch (Throwable ignored) {
                // The normal GUI pass remains the final fallback.
            }
            return false;
        }
    }

    void release() {
        if (this.shaderGroup != null) {
            try {
                this.shaderGroup.deleteShaderGroup();
            } catch (RuntimeException ignored) {
                // Resource reload and display teardown may already own it.
            }
        }
        this.shaderGroup = null;
        this.width = -1;
        this.height = -1;
    }

    void resetAfterResourceReload() {
        release();
        this.unavailable = false;
    }

    private void ensureShader(Minecraft minecraft) throws Exception {
        if (this.shaderGroup == null) {
            this.shaderGroup = new ShaderGroup(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    minecraft.getFramebuffer(), BLUR);
        }
        if (this.width != minecraft.displayWidth
                || this.height != minecraft.displayHeight) {
            this.width = minecraft.displayWidth;
            this.height = minecraft.displayHeight;
            this.shaderGroup.createBindFramebuffers(
                    this.width, this.height);
        }
    }

    private void setRadius(float radius) throws IllegalAccessException {
        Object value = SHADERS.get(this.shaderGroup);
        if (!(value instanceof List)) {
            throw new IllegalStateException("blur shader pass list missing");
        }
        for (Object entry : (List<?>)value) {
            if (!(entry instanceof Shader)) {
                continue;
            }
            ShaderUniform uniform = ((Shader)entry).getShaderManager()
                    .func_147991_a("Radius");
            if (uniform != null) {
                uniform.func_148090_a(radius);
            }
        }
    }

    private void disable(Throwable failure) {
        release();
        this.unavailable = true;
        if (!this.failureLogged) {
            this.failureLogged = true;
            FMLLog.warning("[losttales] GUI background blur disabled for "
                    + "this resource session: %s",
                    failure.getClass().getSimpleName());
        }
    }

    private static Field findShadersField() {
        String[] names = new String[] {"listShaders", "field_148031_d"};
        for (String name : names) {
            try {
                Field field = ShaderGroup.class.getDeclaredField(name);
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
                // Try the other MCP/SRG runtime name.
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
