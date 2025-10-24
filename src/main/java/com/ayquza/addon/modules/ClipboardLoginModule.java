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
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc == null) return;

        if (hotkey.get().isPressed()) {
            if (!keyPressed) {
                keyPressed = true;
                executeClipboardLogin();
            }
        } else {
            keyPressed = false;
        }
    }

    private void executeClipboardLogin() {
        String clipboardText = getClipboardText();
        if (clipboardText == null || clipboardText.trim().isEmpty()) return;

        String username = clipboardText.trim();
        if (!validateUsername(username)) return;

        ServerInfo currentServer = mc.getCurrentServerEntry();

        MeteorExecutor.execute(() -> {
            try {
                CrackedAccount crackedAccount = new CrackedAccount(username);

                if (addIfNotExists.get()) {
                    addAccountIfNotExists(crackedAccount);
                } else {
                    CrackedAccount existing = findExistingAccount(username);
                    if (existing != null) crackedAccount = existing;
                    else return;
                }

                if (crackedAccount.login()) {
                    Accounts.get().save();
                    if (autoReconnect.get() && currentServer != null) {
                        reconnectToServer(currentServer);
                    }
                }

            } catch (Exception ignored) {}
        });
    }

    private String getClipboardText() {
        try {
            return mc.keyboard.getClipboard();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateUsername(String username) {
        if (username.isEmpty() || username.length() > 16) return false;
        return username.matches("^[a-zA-Z0-9_]+$");
    }

    private CrackedAccount findExistingAccount(String username) {
        for (meteordevelopment.meteorclient.systems.accounts.Account<?> acc : Accounts.get()) {
            if (acc instanceof CrackedAccount) {
                CrackedAccount ca = (CrackedAccount) acc;
                if (ca.getUsername().equalsIgnoreCase(username)) return ca;
            }
        }
        return null;
    }

    private void addAccountIfNotExists(CrackedAccount crackedAccount) {
        if (crackedAccount.fetchInfo()) {
            crackedAccount.getCache().loadHead();
            if (!Accounts.get().exists(crackedAccount)) Accounts.get().add(crackedAccount);
        } else throw new RuntimeException("Failed to fetch account info for: " + crackedAccount.getUsername());
    }

    private void reconnectToServer(ServerInfo serverInfo) {
        mc.execute(() -> {
            try {
                if (mc.world != null) mc.world.disconnect(Text.of("Reconnecting"));
                if (mc.getCurrentServerEntry() == null) mc.disconnectWithSavingScreen();
                else mc.disconnectWithProgressScreen();
                mc.setScreen(new MultiplayerScreen(new TitleScreen()));
                ConnectScreen.connect(
                    new MultiplayerScreen(new TitleScreen()),
                    mc,
                    ServerAddress.parse(serverInfo.address),
                    serverInfo,
                    false,
                    null
                );
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onActivate() { keyPressed = false; }
    @Override
    public void onDeactivate() {}
}
