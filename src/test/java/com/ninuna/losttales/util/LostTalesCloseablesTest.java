package com.ninuna.losttales.util;

import java.io.Closeable;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesCloseablesTest {

    @Test
    public void aStreamIsClosedOnce() {
        final int[] closed = new int[1];
        LostTalesCloseables.closeQuietly(new Closeable() {
            @Override
            public void close() {
                closed[0]++;
            }
        });
        assertEquals(1, closed[0]);
    }

    @Test
    public void aFailedCloseAndNoStreamAreBothQuiet() {
        LostTalesCloseables.closeQuietly(null);
        LostTalesCloseables.closeQuietly(new Closeable() {
            @Override
            public void close() throws IOException {
                throw new IOException("disk gone");
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void onlyAFailureToCloseIsIgnored() {
        LostTalesCloseables.closeQuietly(new Closeable() {
            @Override
            public void close() {
                throw new IllegalStateException("a bug, not a closed stream");
            }
        });
    }
}
