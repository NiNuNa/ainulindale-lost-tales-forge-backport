package com.ninuna.losttales.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** A server step that throws is logged and the next step still runs. */
public final class ServerStepTest {

    @Test
    public void aFaultyStepNeverSkipsTheNext() {
        final List<String> ran = new ArrayList<String>();
        assertTrue(ServerStep.run("first", new Runnable() {
            @Override
            public void run() {
                ran.add("first");
            }
        }));
        assertFalse(ServerStep.run("faulty", new Runnable() {
            @Override
            public void run() {
                ran.add("faulty");
                throw new IllegalStateException("broken store");
            }
        }));
        assertTrue(ServerStep.run("last", new Runnable() {
            @Override
            public void run() {
                ran.add("last");
            }
        }));
        List<String> expected = new ArrayList<String>();
        expected.add("first");
        expected.add("faulty");
        expected.add("last");
        assertEquals(expected, ran);
    }

    @Test
    public void anErrorIsNotCaught() {
        try {
            ServerStep.run("out of memory", new Runnable() {
                @Override
                public void run() {
                    throw new AssertionError("not a step's fault");
                }
            });
            fail("an Error passes through");
        } catch (AssertionError expected) {
            assertEquals("not a step's fault", expected.getMessage());
        }
    }
}
