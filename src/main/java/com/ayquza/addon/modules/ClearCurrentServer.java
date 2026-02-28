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

    private final SettingGroup sgIngame = settings.createGroup("In-Game");
    private final SettingGroup sgServerList = settings.createGroup("Server List");

    private final Setting<Keybind> removeKey = sgIngame.add(new KeybindSetting.Builder()
        .name("remove-ingame-server-key")
        .description("Key to remove the server you are currently connected to from the Multiplayer Server list.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_UNKNOWN))
        .build()
    );

    private final Setting<Keybind> removeSelectedKey = sgServerList.add(new KeybindSetting.Builder()
        .name("remove-selected-server-key")
        .description("Key to remove the selected server in the multiplayer menu.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_UNKNOWN))
        .build()
    );

    public ClearCurrentServer() {
        super(AyquzaAddon.CATEGORY, "clear-current-server", "Removes servers from the multiplayer list via keybinds.");
    }

    public Keybind getRemoveSelectedKey() {
        return removeSelectedKey.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!removeKey.get().isPressed()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() == null) return;

        ServerInfo current = mc.getCurrentServerEntry();
        ServerList serverList = new ServerList(mc);
        serverList.loadFile();

        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo info = serverList.get(i);
            if (info.address.equalsIgnoreCase(current.address)) {
                serverList.remove(info);
                serverList.saveFile();
                info("Server §c" + info.name + "§r removed from list.");
                break;
            }
        }
    }
}
