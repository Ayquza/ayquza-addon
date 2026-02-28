package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.lwjgl.glfw.GLFW;
import meteordevelopment.meteorclient.utils.misc.Keybind;

public class ClearCurrentServer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> removeKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("remove-key")
        .description("Taste um den aktuellen Server aus der Liste zu entfernen.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_UNKNOWN))
        .build()
    );

    public ClearCurrentServer() {
        super(AyquzaAddon.CATEGORY, "clear-current-server", "Entfernt den aktuellen Server aus der Multipayer-Liste.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!removeKey.get().isPressed()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() == null) return; // Nicht auf einem Server

        ServerInfo current = mc.getCurrentServerEntry();
        ServerList serverList = new ServerList(mc);
        serverList.loadFile(); // Liste laden


        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo info = serverList.get(i);
            if (info.address.equalsIgnoreCase(current.address)) {
                serverList.remove(info);
                serverList.saveFile(); // Speichern
                info("Server §c" + info.name + "§r wurde aus der Liste entfernt.");
                break;
            }
        }
    }
}
