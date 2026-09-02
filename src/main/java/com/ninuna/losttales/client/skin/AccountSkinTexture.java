package com.ninuna.losttales.client.skin;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A 64x64 skin texture that is valid the moment it is registered and swaps
 * to its real image when a background load finishes.
 *
 * The placeholder is uploaded synchronously on registration. The optional
 * source runs on its own daemon thread and hands a normalized image back;
 * the next {@link #getGlTextureId()} on the render thread uploads it. A
 * failed or unusable load keeps the placeholder and is logged once.
 */
public final class AccountSkinTexture extends AbstractTexture {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final BufferedImage placeholder;
    private final Callable<BufferedImage> source;
    private final String description;
    private volatile BufferedImage pending;
    private boolean started;

    /**
     * @param placeholder the 64x64 image shown until the source delivers; used
     *                    for good when {@code source} is null
     * @param source      background loader returning a normalized 64x64 image or
     *                    null; may be null for a static texture
     * @param description what the texture is, for the failure log line
     */
    public AccountSkinTexture(BufferedImage placeholder, Callable<BufferedImage> source,
                              String description) {
        if (placeholder == null) {
            throw new IllegalArgumentException("placeholder must not be null");
        }
        this.placeholder = placeholder;
        this.source = source;
        this.description = description == null ? "skin" : description;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) {
        this.deleteGlTexture();
        TextureUtil.uploadTextureImage(super.getGlTextureId(), this.placeholder);
        if (this.source != null && !this.started) {
            this.started = true;
            startLoading();
        }
    }

    @Override
    public int getGlTextureId() {
        BufferedImage image = this.pending;
        if (image != null) {
            this.pending = null;
            TextureUtil.uploadTextureImage(super.getGlTextureId(), image);
        }
        return super.getGlTextureId();
    }

    private void startLoading() {
        Thread thread = new Thread("Lost Tales skin loader #"
                + THREAD_COUNTER.incrementAndGet()) {
            @Override
            public void run() {
                try {
                    BufferedImage image = AccountSkinTexture.this.source.call();
                    if (image != null) {
                        AccountSkinTexture.this.pending = image;
                    } else {
                        FMLLog.info("[%s] %s is not a usable skin; keeping the default",
                                LostTalesMetaData.MOD_ID,
                                AccountSkinTexture.this.description);
                    }
                } catch (Exception failure) {
                    FMLLog.warning("[%s] Could not load %s: %s",
                            LostTalesMetaData.MOD_ID,
                            AccountSkinTexture.this.description,
                            failure.toString());
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
}
