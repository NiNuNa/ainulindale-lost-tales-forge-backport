package com.ninuna.losttales.client.skin;

import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * Fetches one account skin for {@link AccountSkinTexture}: vanilla's on-disk
 * copy when it already has one, otherwise a bounded download from the
 * profile's texture URL. Runs off the render thread.
 */
final class RemoteSkinSource implements Callable<BufferedImage> {

    private static final int TIMEOUT_MILLIS = 10000;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;

    private final String url;
    private final File cachedCopy;

    RemoteSkinSource(String url, File cachedCopy) {
        this.url = url;
        this.cachedCopy = cachedCopy;
    }

    @Override
    public BufferedImage call() throws IOException {
        BufferedImage raw = null;
        if (this.cachedCopy != null && this.cachedCopy.isFile()) {
            try {
                raw = ImageIO.read(this.cachedCopy);
            } catch (IOException ignored) {
                raw = null;
            }
        }
        if (raw == null) {
            raw = download();
        }
        return SkinImageNormalizer.normalize(raw);
    }

    private BufferedImage download() throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(this.url)
                .openConnection(Minecraft.getMinecraft().getProxy());
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setInstanceFollowRedirects(false);
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            byte[] bytes = readBounded(connection.getInputStream());
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBounded(InputStream stream) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(BUFFER_SIZE);
            byte[] buffer = new byte[BUFFER_SIZE];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("skin exceeds " + MAX_BYTES + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }
}
