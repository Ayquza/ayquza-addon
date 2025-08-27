package com.ayquza.addon.mixins;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.accounts.AccountsScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Shadow @Final private MinecraftClient client;
    private static boolean keyPressed = false;
    private static int cachedHotkey = -1;
    private static long lastHotkeyUpdate = 0;
    private static final long HOTKEY_CACHE_TIME = 5000; // 5 Sekunden Cache

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyPressed(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // Hole den konfigurierten Hotkey (mit Cache)
        int hotkeyCode = getConfiguredHotkey();
        if (hotkeyCode == -1 || key != hotkeyCode) return;

        try {
            // Only on key press (not release)
            if (action == GLFW.GLFW_PRESS && !keyPressed) {
                keyPressed = true;
                handleHotkeyPress();
            } else if (action == GLFW.GLFW_RELEASE) {
                keyPressed = false;
            }
        } catch (Exception e) {
            System.out.println("[KeyboardMixin] Error handling hotkey: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getConfiguredHotkey() {
        // Cache-System: Nur alle 5 Sekunden neu laden
        long currentTime = System.currentTimeMillis();
        if (cachedHotkey != -1 && (currentTime - lastHotkeyUpdate) < HOTKEY_CACHE_TIME) {
            return cachedHotkey;
        }

        try {
            // Try to find the module
            meteordevelopment.meteorclient.systems.modules.Modules modules =
                meteordevelopment.meteorclient.systems.modules.Modules.get();

            if (modules == null) {
                cachedHotkey = GLFW.GLFW_KEY_F6;
                lastHotkeyUpdate = currentTime;
                return cachedHotkey;
            }

            // Search for our module
            for (meteordevelopment.meteorclient.systems.modules.Module module : modules.getAll()) {
                if (module.name.equals("accounts-hotkey") && module instanceof com.ayquza.addon.modules.AutoAccountsOnWhitelist) {
                    com.ayquza.addon.modules.AutoAccountsOnWhitelist hotkeyModule =
                        (com.ayquza.addon.modules.AutoAccountsOnWhitelist) module;

                    // Get the configured hotkey
                    meteordevelopment.meteorclient.utils.misc.Keybind keybind = hotkeyModule.getHotkey();
                    if (keybind != null && keybind.isSet()) {
                        // Nur einmal loggen wenn sich der Hotkey ändert
                        if (cachedHotkey != keybind.getValue()) {
                            System.out.println("[KeyboardMixin] Hotkey updated to: " + keybind.toString());
                        }
                        cachedHotkey = keybind.getValue();
                        lastHotkeyUpdate = currentTime;
                        return cachedHotkey;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[KeyboardMixin] Error getting hotkey: " + e.getMessage());
        }

        // Fallback to F6
        cachedHotkey = GLFW.GLFW_KEY_F6;
        lastHotkeyUpdate = currentTime;
        return cachedHotkey;
    }

    private void handleHotkeyPress() {
        if (client == null) return;

        Screen currentScreen = client.currentScreen;
        String screenName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";

        System.out.println("[KeyboardMixin] Hotkey pressed! Current screen: " + screenName);

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
            System.out.println("[KeyboardMixin] In relevant menu - opening Account Manager!");

            // Execute in next client tick
            client.execute(() -> {
                try {
                    System.out.println("[KeyboardMixin] Executing Account Manager open...");

                    if (GuiThemes.get() == null) {
                        System.out.println("[KeyboardMixin] ERROR: GuiThemes is null!");
                        return;
                    }

                    AccountsScreen accountsScreen = new AccountsScreen(GuiThemes.get());
                    client.setScreen(accountsScreen);

                    System.out.println("[KeyboardMixin] SUCCESS! Account Manager opened via Mixin!");

                } catch (Exception e) {
                    System.out.println("[KeyboardMixin] Error opening: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("[KeyboardMixin] Not in relevant menu - ignoring hotkey");
        }
    }
}
