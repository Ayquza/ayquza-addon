package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.systems.accounts.types.CrackedAccount;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerNameCyclerModule extends Module {

    private static boolean persistentIsCycling = false;
    private static List<String> persistentPlayerNames = new ArrayList<>();
    private static int persistentCurrentIndex = 0;
    private static ServerInfo persistentTargetServer = null;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");

    private final Setting<Keybind> hotkey = sgGeneral.add(new KeybindSetting.Builder()
        .name("hotkey")
        .description("Key to start cycling through player names.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F8))
        .build()
    );

    private final Setting<Integer> reconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reconnect-delay")
        .description("Delay in seconds between reconnects.")
        .defaultValue(3)
        .min(1)
        .max(30)
        .sliderMin(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> stopOnOp = sgBehavior.add(new BoolSetting.Builder()
        .name("stop-on-op")
        .description("Stops cycling when account has operator permissions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> addIfNotExists = sgBehavior.add(new BoolSetting.Builder()
        .name("add-if-not-exists")
        .description("Adds accounts if they don't exist in your account list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugMode = sgBehavior.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows debug messages.")
        .defaultValue(false)
        .build()
    );

    private boolean keyPressed = false;
    private boolean isCycling = false;
    private List<String> playerNames = new ArrayList<>();
    private int currentIndex = 0;
    private ServerInfo targetServer = null;
    private long lastReconnectTime = 0;
    private boolean waitingForReconnect = false;
    private AtomicBoolean checkingOp = new AtomicBoolean(false);
    private boolean hasCheckedOp = false;

    public PlayerNameCyclerModule() {
        super(AyquzaAddon.CATEGORY, "player-name-cycler", "Cycles through all online player names with automatic reconnect.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc == null) return;

        try {
            if (hotkey.get().isPressed()) {
                if (!keyPressed) {
                    keyPressed = true;
                    if (!isCycling) {
                        startCycling();
                    } else {
                        stopCycling();
                    }
                }
            } else {
                keyPressed = false;
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Hotkey check error: " + e.getMessage());
            }
        }

        if (isCycling && !waitingForReconnect) {
            if (mc.getCurrentServerEntry() != null && mc.world != null) {
                if (stopOnOp.get() && !hasCheckedOp && !checkingOp.get()) {
                    checkOpStatus();
                }
            }
        }

        if (waitingForReconnect && System.currentTimeMillis() - lastReconnectTime >= reconnectDelay.get() * 1000L) {
            waitingForReconnect = false;
            proceedToNextAccount();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isCycling && targetServer != null) {
            lastReconnectTime = System.currentTimeMillis();
            waitingForReconnect = true;
            hasCheckedOp = false;

            if (debugMode.get()) {
                info("Left server, waiting " + reconnectDelay.get() + " seconds before reconnect...");
            }
        }
    }

    private void startCycling() {
        if (mc.world == null || mc.getNetworkHandler() == null) {
            error("You must be connected to a server to use this module!");
            return;
        }

        playerNames.clear();
        for (PlayerListEntry player : mc.getNetworkHandler().getPlayerList()) {
            String name = player.getProfile().getName();
            if (name != null && !name.isEmpty()) {
                playerNames.add(name);
            }
        }

        if (playerNames.isEmpty()) {
            error("No players found on the server!");
            return;
        }

        targetServer = mc.getCurrentServerEntry();
        if (targetServer == null) {
            error("Could not get current server information!");
            return;
        }

        String currentPlayerName = mc.player.getGameProfile().getName();
        playerNames.removeIf(name -> name.equalsIgnoreCase(currentPlayerName));

        if (playerNames.isEmpty()) {
            error("No other players found on the server!");
            return;
        }

        currentIndex = 0;
        isCycling = true;
        waitingForReconnect = false;
        hasCheckedOp = false;

        info("Starting cycle through " + playerNames.size() + " players: " + String.join(", ", playerNames));

        loginAndReconnect(playerNames.get(currentIndex));

        persistentIsCycling = true;
        persistentPlayerNames = new ArrayList<>(playerNames);
        persistentCurrentIndex = currentIndex;
        persistentTargetServer = targetServer;
    }

    private void stopCycling() {
        isCycling = false;
        waitingForReconnect = false;
        hasCheckedOp = false;
        info("Stopped cycling through player names.");

        if (debugMode.get()) {
            info("Cycled through " + currentIndex + " out of " + playerNames.size() + " players.");
        }
        persistentIsCycling = false;
        persistentPlayerNames.clear();
        persistentCurrentIndex = 0;
        persistentTargetServer = null;
    }

    private void proceedToNextAccount() {
        currentIndex++;

        if (currentIndex >= playerNames.size()) {
            info("Finished cycling through all " + playerNames.size() + " player names!");
            stopCycling();
            return;
        }

        String nextName = playerNames.get(currentIndex);
        info("Moving to next account (" + (currentIndex + 1) + "/" + playerNames.size() + "): " + nextName);
        loginAndReconnect(nextName);

        persistentCurrentIndex = currentIndex;
    }

    private void loginAndReconnect(String username) {
        if (debugMode.get()) {
            info("Attempting to login as: " + username);
        }

        MeteorExecutor.execute(() -> {
            try {
                CrackedAccount crackedAccount = new CrackedAccount(username);

                if (addIfNotExists.get()) {
                    addAccountIfNotExists(crackedAccount);
                } else {
                    CrackedAccount existing = findExistingAccount(username);
                    if (existing != null) {
                        crackedAccount = existing;
                    } else {
                        error("Account not found: " + username);
                        mc.execute(() -> {
                            lastReconnectTime = System.currentTimeMillis();
                            waitingForReconnect = true;
                        });
                        return;
                    }
                }

                if (crackedAccount.login()) {
                    Accounts.get().save();
                    info("Logged in as: " + crackedAccount.getUsername());

                    reconnectToServer(targetServer);
                } else {
                    error("Failed to login as: " + crackedAccount.getUsername());
                    mc.execute(() -> {
                        lastReconnectTime = System.currentTimeMillis();
                        waitingForReconnect = true;
                    });
                }

            } catch (Exception e) {
                error("Login error: " + e.getMessage());
                if (debugMode.get()) {
                    e.printStackTrace();
                }
                mc.execute(() -> {
                    lastReconnectTime = System.currentTimeMillis();
                    waitingForReconnect = true;
                });
            }
        });
    }

    private void checkOpStatus() {
        if (checkingOp.get()) return;

        checkingOp.set(true);
        hasCheckedOp = true;

        if (debugMode.get()) {
            info("Checking OP status by attempting gamemode change...");
        }

        boolean wasCreative = mc.player.getAbilities().creativeMode;

        mc.getNetworkHandler().sendChatCommand("gamemode creative");

        MeteorExecutor.execute(() -> {
            try {
                Thread.sleep(1000);

                mc.execute(() -> {
                    boolean isCreativeNow = mc.player != null && mc.player.getAbilities().creativeMode;

                    if (!wasCreative && isCreativeNow) {
                        info("§aAccount has OP! Stopping cycle.");
                        info("§aSuccessfully found OP account: §e" + mc.player.getGameProfile().getName());
                        stopCycling();
                    }  else {
                        if (debugMode.get()) {
                            info("No OP permissions, continuing cycle...");
                        }
                        lastReconnectTime = System.currentTimeMillis();
                        waitingForReconnect = true;
                        persistentCurrentIndex = currentIndex;
                    }

                    checkingOp.set(false);
                });
            } catch (InterruptedException e) {
                checkingOp.set(false);
            }
        });
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
                    info("Adding new account: " + crackedAccount.getUsername());
                }
                Accounts.get().add(crackedAccount);
            }
        } else {
            throw new RuntimeException("Failed to fetch account info for: " + crackedAccount.getUsername());
        }
    }

    private void reconnectToServer(ServerInfo serverInfo) {
        mc.execute(() -> {
            try {
                if (debugMode.get()) {
                    info("Reconnecting to: " + serverInfo.address);
                }

                ConnectScreen.connect(
                    mc.currentScreen,
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
        if (persistentIsCycling) {
            isCycling = true;
            playerNames = new ArrayList<>(persistentPlayerNames);
            currentIndex = persistentCurrentIndex;
            targetServer = persistentTargetServer;
            waitingForReconnect = false;
            hasCheckedOp = false;
        } else {
            keyPressed = false;
            isCycling = false;
            waitingForReconnect = false;
            hasCheckedOp = false;
            playerNames.clear();
            currentIndex = 0;
            targetServer = null;

            info("Player Name Cycler activated! Press " + hotkey.get() + " to start cycling.");
        }
    }

    @Override
    public void onDeactivate() {
        if (isCycling && !persistentIsCycling) {
            stopCycling();
        }

        if (!persistentIsCycling) {
            info("Player Name Cycler deactivated!");
        }
    }
}
