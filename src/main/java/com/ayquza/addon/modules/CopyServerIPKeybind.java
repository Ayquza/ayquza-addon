package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;

public class CopyServerIPKeybind extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public CopyServerIPKeybind() {
        super(AyquzaAddon.CATEGORY, "copy-server-ip", "Copies the current server IP to your clipboard with a keybind.");
    }

    @Override
    public void onActivate() {
        if (mc.getCurrentServerEntry() != null) {
            String ip = mc.getCurrentServerEntry().address;

            // Use GLFW clipboard instead of AWT
            GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), ip);

            info("Copied server IP: " + ip);
        } else {
            info("You are not connected to a server.");
        }

        toggle(); // Auto-disable after use
    }
}
