package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.systems.accounts.types.CrackedAccount;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class ClipboardLoginModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> hotkey = sgGeneral.add(new KeybindSetting.Builder()
        .name("hotkey")
        .description("Key to login with clipboard username.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F7))
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows debug messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Automatically reconnects to current server after login.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> addIfNotExists = sgGeneral.add(new BoolSetting.Builder()
        .name("add-if-not-exists")
        .description("Adds the account if it doesn't exist in your account list.")
        .defaultValue(true)
        .build()
    );

    private boolean keyPressed = false;

    public ClipboardLoginModule() {
        super(AyquzaAddon.CATEGORY, "clipboard-login", "Login with username from clipboard via hotkey.");
        System.out.println("[ClipboardLogin] Module constructed!");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc == null) return;

        // Check hotkey
        try {
            if (hotkey.get().isPressed()) {
                if (!keyPressed) {
                    keyPressed = true;

                    if (debugMode.get()) {
                        info("Clipboard login hotkey pressed!");
                    }

                    executeClipboardLogin();
                }
            } else {
                keyPressed = false;
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                System.out.println("[ClipboardLogin] Error during hotkey check: " + e.getMessage());
            }
        }
    }

    private void executeClipboardLogin() {
        try {
            String clipboardText = getClipboardText();

            if (clipboardText == null || clipboardText.trim().isEmpty()) {
                error("Clipboard is empty or invalid!");
                return;
            }

            String username = clipboardText.trim();

            if (debugMode.get()) {
                System.out.println("[ClipboardLogin] Clipboard content: '" + username + "'");
            }

            // Validate username
            if (!validateUsername(username)) {
                return;
            }

            info("Logging in as: " + username);

            // Get current server for reconnect
            ServerInfo currentServer = mc.getCurrentServerEntry();

            // Execute login in background thread
            MeteorExecutor.execute(() -> {
                try {
                    CrackedAccount crackedAccount = new CrackedAccount(username);

                    if (addIfNotExists.get()) {
                        addAccountIfNotExists(crackedAccount);
                    } else {
                        // Try to find existing account
                        CrackedAccount existing = findExistingAccount(username);
                        if (existing != null) {
                            crackedAccount = existing;
                        } else {
                            error("Account not found: " + username + " (add-if-not-exists is disabled)");
                            return;
                        }
                    }

                    // Login
                    if (crackedAccount.login()) {
                        Accounts.get().save();
                        info("Successfully logged in as: " + crackedAccount.getUsername());

                        // Reconnect if enabled and we're on a server
                        if (autoReconnect.get() && currentServer != null) {
                            reconnectToServer(currentServer);
                        }
                    } else {
                        error("Failed to login as: " + crackedAccount.getUsername());
                    }

                } catch (Exception e) {
                    error("Login failed: " + e.getMessage());
                    if (debugMode.get()) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            error("Error executing clipboard login: " + e.getMessage());
            if (debugMode.get()) {
                e.printStackTrace();
            }
        }
    }

    private String getClipboardText() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }

            return (String) clipboard.getData(DataFlavor.stringFlavor);

        } catch (UnsupportedFlavorException | IOException e) {
            if (debugMode.get()) {
                System.out.println("[ClipboardLogin] Clipboard error: " + e.getMessage());
            }
            return null;
        }
    }

    private boolean validateUsername(String username) {
        if (username.isEmpty()) {
            error("Username cannot be empty.");
            return false;
        }

        if (username.length() > 16) {
            error("Username cannot be longer than 16 characters.");
            return false;
        }

        // Check for valid minecraft username characters
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            error("Username contains invalid characters. Only letters, numbers and underscore allowed.");
            return false;
        }

        return true;
    }

    private CrackedAccount findExistingAccount(String username) {
        for (meteordevelopment.meteorclient.systems.accounts.Account<?> acc : Accounts.get()) {
            if (acc instanceof CrackedAccount) {
                CrackedAccount ca = (CrackedAccount) acc;
                if (ca.getUsername().equalsIgnoreCase(username)) {
                    return ca;
                }
            }
        }
        return null;
    }

    private void addAccountIfNotExists(CrackedAccount crackedAccount) {
        if (crackedAccount.fetchInfo()) {
            crackedAccount.getCache().loadHead();

            if (!Accounts.get().exists(crackedAccount)) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardLogin] Adding new cracked account: " + crackedAccount.getUsername());
                }
                info("Added new cracked account: " + crackedAccount.getUsername());
                Accounts.get().add(crackedAccount);
            } else {
                if (debugMode.get()) {
                    System.out.println("[ClipboardLogin] Account already exists: " + crackedAccount.getUsername());
                }
            }
        } else {
            throw new RuntimeException("Failed to fetch account info for: " + crackedAccount.getUsername());
        }
    }

    private void reconnectToServer(ServerInfo serverInfo) {
        mc.execute(() -> {
            try {
                if (debugMode.get()) {
                    System.out.println("[ClipboardLogin] Reconnecting to: " + serverInfo.address);
                }

                info("Disconnecting...");

                if (mc.world != null) {
                    mc.world.disconnect(Text.of("Reconnecting"));
                }

                if (mc.getCurrentServerEntry() == null) {
                    mc.disconnectWithSavingScreen();
                } else {
                    mc.disconnectWithProgressScreen();
                }

                mc.setScreen(new MultiplayerScreen(new TitleScreen()));

                info("Reconnecting to server: " + serverInfo.address);

                ConnectScreen.connect(
                    new MultiplayerScreen(new TitleScreen()),
                    mc,
                    ServerAddress.parse(serverInfo.address),
                    serverInfo,
                    false,
                    null
                );

            } catch (Exception e) {
                error("Reconnect failed: " + e.getMessage());
                if (debugMode.get()) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onActivate() {
        keyPressed = false;
        System.out.println("[ClipboardLogin] Module activated!");
        info("Clipboard Login activated - Hotkey: " + hotkey.get().toString());
        info("Copy a username to clipboard and press the hotkey!");

        if (debugMode.get()) {
            info("Debug mode enabled");
        }
    }

    @Override
    public void onDeactivate() {
        System.out.println("[ClipboardLogin] Module deactivated!");
        info("Clipboard Login deactivated!");
    }
}
