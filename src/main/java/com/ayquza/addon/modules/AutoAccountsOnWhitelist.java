package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.accounts.AccountsScreen;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.lwjgl.glfw.GLFW;

public class AutoAccountsOnWhitelist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> hotkey = sgGeneral.add(new KeybindSetting.Builder()
        .name("hotkey")
        .description("Key to open the Account Manager.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F6))
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows debug messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in milliseconds before opening the Account Manager.")
        .defaultValue(0)
        .min(0)
        .max(2000)
        .sliderMin(0)
        .sliderMax(1000)
        .build()
    );

    // Static service instance that runs globally
    private static GlobalHotkeyService globalService;
    private boolean keyPressed = false;

    public AutoAccountsOnWhitelist() {
        super(AyquzaAddon.CATEGORY, "accounts-hotkey", "Opens the Account Manager via hotkey everywhere.");
        System.out.println("[AccountsHotkey] Module constructed!");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Nur im Spiel - für Ingame Hotkey
        if (!isActive() || mc == null) return;

        try {
            if (hotkey.get().isPressed()) {
                if (!keyPressed) {
                    keyPressed = true;
                    System.out.println("[AccountsHotkey] Hotkey detected in game!");
                    openAccountManager();
                }
            } else {
                keyPressed = false;
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                System.out.println("[AccountsHotkey] Error during hotkey check: " + e.getMessage());
            }
        }
    }

    private void openAccountManager() {
        System.out.println("[AccountsHotkey] openAccountManager() called");

        if (delay.get() > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(delay.get());
                    MinecraftClient.getInstance().execute(this::showAccountsScreen);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else {
            showAccountsScreen();
        }
    }

    private void showAccountsScreen() {
        System.out.println("[AccountsHotkey] showAccountsScreen() started");

        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (!mc.isOnThread()) {
                mc.execute(this::showAccountsScreen);
                return;
            }

            System.out.println("[AccountsHotkey] Current screen: " +
                (mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "null"));

            GuiTheme theme = GuiThemes.get();
            if (theme == null) {
                System.out.println("[AccountsHotkey] ERROR: GuiTheme is null!");
                return;
            }

            AccountsScreen accountsScreen = new AccountsScreen(theme);
            mc.setScreen(accountsScreen);

            System.out.println("[AccountsHotkey] SUCCESS! Account Manager opened!");

            try {
                info("Account Manager opened!");
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.out.println("[AccountsHotkey] EXCEPTION: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onActivate() {
        keyPressed = false;
        System.out.println("[AccountsHotkey] MODULE ACTIVATED!");

        // Start the global service
        if (globalService == null || !globalService.isRunning()) {
            globalService = new GlobalHotkeyService(this);
            globalService.start();
            System.out.println("[AccountsHotkey] Global service started!");
        }


    }

    @Override
    public void onDeactivate() {
        System.out.println("[AccountsHotkey] MODULE DEACTIVATED!");

        // Don't stop the global service - let it run for other modules

        try {
            info("Accounts Hotkey deactivated!");
        } catch (Exception ignored) {}
    }

    public boolean isDebugMode() {
        return debugMode.get();
    }

    public int getDelay() {
        return delay.get();
    }

    public meteordevelopment.meteorclient.utils.misc.Keybind getHotkey() {
        return hotkey.get();
    }

    // Globaler Service der unabhängig läuft
    public static class GlobalHotkeyService {
        private Thread hotkeyThread;
        private boolean running = false;
        private final AutoAccountsOnWhitelist module;
        private boolean keyPressed = false;

        public GlobalHotkeyService(AutoAccountsOnWhitelist module) {
            this.module = module;
        }

        public void start() {
            if (running) return;

            running = true;
            hotkeyThread = new Thread(() -> {
                System.out.println("[GlobalHotkeyService] Service started!");

                while (running) {
                    try {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null) {
                            Thread.sleep(100);
                            continue;
                        }

                        Screen currentScreen = mc.currentScreen;

                        // Check if we're in a menu
                        boolean inMenu = currentScreen instanceof MultiplayerScreen ||
                            currentScreen instanceof TitleScreen ||
                            currentScreen instanceof DisconnectedScreen ||
                            (currentScreen != null &&
                                (currentScreen.getClass().getSimpleName().toLowerCase().contains("disconnect") ||
                                    currentScreen.getClass().getSimpleName().toLowerCase().contains("multiplayer")));

                        if (inMenu) {
                            // Check F6 directly via GLFW
                            try {
                                long window = mc.getWindow().getHandle();
                                int keyState = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F6);

                                if (keyState == GLFW.GLFW_PRESS) {
                                    if (!keyPressed) {
                                        keyPressed = true;
                                        System.out.println("[GlobalHotkeyService] F6 detected in menu! Screen: " +
                                            currentScreen.getClass().getSimpleName());

                                        // Execute in main thread
                                        mc.execute(() -> {
                                            if (module.getDelay() > 0) {
                                                new Thread(() -> {
                                                    try {
                                                        Thread.sleep(module.getDelay());
                                                        mc.execute(module::showAccountsScreen);
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }).start();
                                            } else {
                                                module.showAccountsScreen();
                                            }
                                        });
                                    }
                                } else {
                                    keyPressed = false;
                                }
                            } catch (Exception e) {
                                if (module.isDebugMode()) {
                                    System.out.println("[GlobalHotkeyService] GLFW error: " + e.getMessage());
                                }
                            }
                        }

                        Thread.sleep(50); // 20 FPS checking

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        if (module.isDebugMode()) {
                            System.out.println("[GlobalHotkeyService] Error: " + e.getMessage());
                        }
                    }
                }

                System.out.println("[GlobalHotkeyService] Service terminated!");
            });

            hotkeyThread.setDaemon(true);
            hotkeyThread.setName("AccountsHotkey-GlobalService");
            hotkeyThread.start();
        }

        public void stop() {
            running = false;
            if (hotkeyThread != null) {
                hotkeyThread.interrupt();
            }
        }

        public boolean isRunning() {
            return running && hotkeyThread != null && hotkeyThread.isAlive();
        }
    }
}
