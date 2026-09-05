package com.ninuna.losttales.client;

import com.ninuna.losttales.client.character.CharacterClientTaskQueue;
import net.minecraft.client.Minecraft;

/**
 * Runs work on the client's main thread, where the OpenGL context and
 * the GUI live. FML fires its connection events on the network thread,
 * so a handler that deletes a texture or touches a screen from one of
 * them hops here first — through the same queue the mod's packet
 * handlers use, so a connect's clearing and the server's first packets
 * run in the order they arrived. Work asked for from the main thread
 * runs at once.
 */
public final class LostTalesClientThread {

    private LostTalesClientThread() {}

    public static void run(Runnable task) {
        if (task == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.func_152345_ab()) {
            task.run();
        } else {
            CharacterClientTaskQueue.enqueue(task);
        }
    }
}
