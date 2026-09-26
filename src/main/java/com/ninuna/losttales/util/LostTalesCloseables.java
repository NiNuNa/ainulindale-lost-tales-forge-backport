package com.ninuna.losttales.util;

import java.io.Closeable;
import java.io.IOException;

/** Closing a stream once its work is done, or has failed. */
public final class LostTalesCloseables {

    private LostTalesCloseables() {}

    /**
     * Closes the stream, if any. A failure to close is ignored: whatever
     * was read or written has already succeeded or failed on its own.
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing is left to do with it.
        }
    }
}
