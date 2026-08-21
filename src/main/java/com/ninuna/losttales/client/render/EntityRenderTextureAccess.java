package com.ninuna.losttales.client.render;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Asks an entity's renderer which texture it draws with. Reflection is
 * required because {@code Render.getEntityTexture} is protected and no
 * Forge or LOTR API exposes the per-entity skin choice (LOTR NPC skins are
 * picked randomly per entity on the client). The member is resolved once
 * and verified by shape rather than trusting an obfuscated name.
 */
public final class EntityRenderTextureAccess {
    private static final Method GET_ENTITY_TEXTURE = resolveMethod();

    private EntityRenderTextureAccess() {}

    public static boolean isAvailable() {
        return GET_ENTITY_TEXTURE != null;
    }

    /** The texture the entity currently renders with, or null. */
    public static ResourceLocation resolveEntityTexture(Entity entity) {
        if (GET_ENTITY_TEXTURE == null || entity == null) {
            return null;
        }
        try {
            Render render = RenderManager.instance
                    .getEntityRenderObject(entity);
            return render == null ? null
                    : (ResourceLocation)GET_ENTITY_TEXTURE.invoke(
                            render, entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveMethod() {
        String[] names = new String[] {
                "getEntityTexture", "func_110775_a"};
        for (int index = 0; index < names.length; index++) {
            try {
                Method method = Render.class.getDeclaredMethod(
                        names[index], Entity.class);
                if (method.getReturnType() == ResourceLocation.class) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }
}
