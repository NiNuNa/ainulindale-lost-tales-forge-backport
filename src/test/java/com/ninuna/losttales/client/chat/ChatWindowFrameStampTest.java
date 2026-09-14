package com.ninuna.losttales.client.chat;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The stamps a window draws answer the pointer where they were drawn, so
 * the tip that reads out a stamp's whole date names the stamp under the
 * pointer and no other.
 */
public final class ChatWindowFrameStampTest {

    @After
    public void cleanUp() {
        ChatWindowFrame.clear();
    }

    @Test
    public void aStampAnswersOnItsOwnBoxWhileItsWindowIsDrawn() {
        ChatWindowFrame frame = ChatWindowFrame.of(new ChatWindow("w9"));
        frame.drawn = true;
        frame.clearStamps();
        frame.recordStamp(10.0F, 20.0F, 30.0F, 26.0F, 7);
        frame.recordStamp(10.0F, 40.0F, 30.0F, 46.0F, 8);
        assertEquals(7, frame.stampLineAt(10.0D, 20.0D));
        assertEquals(8, frame.stampLineAt(29.5D, 45.9D));
        // The far edges belong to what is beyond them.
        assertEquals(0, frame.stampLineAt(30.0D, 22.0D));
        assertEquals(0, frame.stampLineAt(20.0D, 26.0D));
        assertEquals(0, frame.stampLineAt(20.0D, 30.0D));
        // A window not drawn this frame has no stamps to rest on.
        frame.drawn = false;
        assertEquals(0, frame.stampLineAt(20.0D, 22.0D));
        // Each draw records its own stamps afresh.
        frame.drawn = true;
        frame.clearStamps();
        assertEquals(0, frame.stampLineAt(20.0D, 22.0D));
    }

    @Test
    public void everyStampAWindowDrawsIsKept() {
        ChatWindowFrame frame = ChatWindowFrame.of(new ChatWindow("w9"));
        frame.drawn = true;
        frame.clearStamps();
        for (int index = 0; index < 40; index++) {
            frame.recordStamp(0.0F, index * 10.0F, 20.0F,
                    index * 10.0F + 6.0F, index + 1);
        }
        assertEquals(1, frame.stampLineAt(5.0D, 3.0D));
        assertEquals(40, frame.stampLineAt(5.0D, 393.0D));
        // An empty box is no stamp.
        frame.recordStamp(50.0F, 0.0F, 50.0F, 6.0F, 99);
        assertEquals(0, frame.stampLineAt(50.0D, 3.0D));
    }
}
