package com.ayquza.addon.mixins;

import com.ayquza.addon.modules.ClipboardConnectModule;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class ClipboardConnectKeyboardMixin {

    @Shadow @Final private MinecraftClient client;
    private static boolean clipboardKeyPressed = false;
    private static int cachedClipboardHotkey = -1;
    private static long lastClipboardHotkeyUpdate = 0;
    private static final long CLIPBOARD_HOTKEY_CACHE_TIME = 5000; // 5 Sekunden Cache

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onClipboardKeyPressed(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // Hole den konfigurierten Clipboard-Hotkey
        int hotkeyCode = getConfiguredClipboardHotkey();
        if (hotkeyCode == -1 || key != hotkeyCode) return;

        try {
            // Only on key press (not release)
            if (action == GLFW.GLFW_PRESS && !clipboardKeyPressed) {
                clipboardKeyPressed = true;
                handleClipboardHotkeyPress();
            } else if (action == GLFW.GLFW_RELEASE) {
                clipboardKeyPressed = false;
            }
        } catch (Exception e) {
            System.out.println("[ClipboardConnectMixin] Error handling hotkey: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getConfiguredClipboardHotkey() {
        // Cache-System: Nur alle 5 Sekunden neu laden
        long currentTime = System.currentTimeMillis();
        if (cachedClipboardHotkey != -1 && (currentTime - lastClipboardHotkeyUpdate) < CLIPBOARD_HOTKEY_CACHE_TIME) {
            return cachedClipboardHotkey;
        }

        try {
            // Try to find the clipboard-connect module
            meteordevelopment.meteorclient.systems.modules.Modules modules =
                meteordevelopment.meteorclient.systems.modules.Modules.get();

            if (modules == null) {
                cachedClipboardHotkey = GLFW.GLFW_KEY_F8;
                lastClipboardHotkeyUpdate = currentTime;
                return cachedClipboardHotkey;
            }

            // Search for clipboard-connect module
            for (meteordevelopment.meteorclient.systems.modules.Module module : modules.getAll()) {
                if (module.name.equals("clipboard-connect") && module instanceof ClipboardConnectModule) {
                    ClipboardConnectModule clipboardModule = (ClipboardConnectModule) module;

                    // Only respond if module is active
                    if (!clipboardModule.isActive()) {
                        cachedClipboardHotkey = -1;
                        lastClipboardHotkeyUpdate = currentTime;
                        return cachedClipboardHotkey;
                    }

                    // Get the configured hotkey
                    meteordevelopment.meteorclient.utils.misc.Keybind keybind = clipboardModule.getHotkey();
                    if (keybind != null && keybind.isSet()) {
                        int keyValue = keybind.getValue();

                        // Nur Tastatur-Tasten unterstützen (keine Maustasten)
                        if (keyValue >= GLFW.GLFW_MOUSE_BUTTON_1 && keyValue <= GLFW.GLFW_MOUSE_BUTTON_8) {
                            System.out.println("[ClipboardConnectMixin] Mouse buttons not supported, ignoring: " + keybind.toString());
                            cachedClipboardHotkey = -1;
                            lastClipboardHotkeyUpdate = currentTime;
                            return cachedClipboardHotkey;
                        }

                        // Nur einmal loggen wenn sich der Hotkey ändert
                        if (cachedClipboardHotkey != keyValue) {
                            System.out.println("[ClipboardConnectMixin] Clipboard hotkey updated to: " + keybind.toString());
                        }
                        cachedClipboardHotkey = keyValue;
                        lastClipboardHotkeyUpdate = currentTime;
                        return cachedClipboardHotkey;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ClipboardConnectMixin] Error getting hotkey: " + e.getMessage());
        }

        // No active module found
        cachedClipboardHotkey = -1;
        lastClipboardHotkeyUpdate = currentTime;
        return cachedClipboardHotkey;
    }

    private void handleClipboardHotkeyPress() {
        if (client == null) return;

        Screen currentScreen = client.currentScreen;
        String screenName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";

        System.out.println("[ClipboardConnectMixin] Clipboard hotkey pressed! Current screen: " + screenName);

        // Check if we're in a relevant menu
        boolean inRelevantMenu = currentScreen instanceof MultiplayerScreen ||
            currentScreen instanceof TitleScreen ||
            currentScreen instanceof DisconnectedScreen ||
            (currentScreen != null && (
                screenName.toLowerCase().contains("disconnect") ||
                    screenName.toLowerCase().contains("multiplayer") ||
                    screenName.toLowerCase().contains("server")
            ));

        if (inRelevantMenu) {
            System.out.println("[ClipboardConnectMixin] In relevant menu - executing clipboard connect!");

            // Execute clipboard connect in next client tick
            client.execute(() -> {
                try {
                    System.out.println("[ClipboardConnectMixin] Executing clipboard connect...");

                    // Get clipboard text using Minecraft's keyboard
                    String clipboardText = null;
                    try {
                        clipboardText = client.keyboard.getClipboard();
                    } catch (Exception e) {
                        System.out.println("[ClipboardConnectMixin] Failed to get clipboard: " + e.getMessage());
                        return;
                    }

                    if (clipboardText == null || clipboardText.trim().isEmpty()) {
                        System.out.println("[ClipboardConnectMixin] Clipboard is empty!");
                        return;
                    }

                    String serverAddress = clipboardText.trim();
                    System.out.println("[ClipboardConnectMixin] Connecting to: " + serverAddress);

                    // Parse server address
                    ServerAddress parsedAddress = ServerAddress.parse(serverAddress);

                    // Create server info
                    ServerInfo serverInfo = new ServerInfo("ClipboardConnect", serverAddress, ServerInfo.ServerType.OTHER);

                    // Set screen to multiplayer screen first
                    MultiplayerScreen multiplayerScreen = new MultiplayerScreen(new TitleScreen());
                    client.setScreen(multiplayerScreen);

                    // Connect to server
                    ConnectScreen.connect(
                        multiplayerScreen,
                        client,
                        parsedAddress,
                        serverInfo,
                        false, // quickPlay
                        null   // transferState
                    );

                    System.out.println("[ClipboardConnectMixin] SUCCESS! Connecting to: " + serverAddress);

                } catch (Exception e) {
                    System.out.println("[ClipboardConnectMixin] Error connecting: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("[ClipboardConnectMixin] Not in relevant menu - ignoring hotkey");
        }
    }
}
