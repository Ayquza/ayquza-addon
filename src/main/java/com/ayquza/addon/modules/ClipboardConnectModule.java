package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.regex.Pattern;

public class ClipboardConnectModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> hotkey = sgGeneral.add(new KeybindSetting.Builder()
        .name("hotkey")
        .description("Key to connect to server from clipboard IP.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F8))
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows debug messages.")
        .defaultValue(true) // Standardmäßig aktiviert für Debugging
        .build()
    );

    private final Setting<Boolean> autoDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disconnect")
        .description("Automatically disconnects from current server before connecting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> validateIP = sgGeneral.add(new BoolSetting.Builder()
        .name("validate-ip")
        .description("Validates the IP format before connecting.")
        .defaultValue(false) // Deaktiviert für erste Tests
        .build()
    );

    private final Setting<Boolean> useMinecraftClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("use-minecraft-clipboard")
        .description("Try to use Minecraft's internal clipboard instead of system clipboard.")
        .defaultValue(true)
        .build()
    );

    // Regex patterns for IP validation
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::[0-9]{1,5})?$"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*(?::[0-9]{1,5})?$"
    );

    private boolean keyPressed = false;

    public ClipboardConnectModule() {
        super(AyquzaAddon.CATEGORY, "clipboard-connect", "Connect to server with IP from clipboard via hotkey.");
        System.out.println("[ClipboardConnect] Module constructed!");
    }

    // Static service instance that runs globally
    private static GlobalClipboardService globalService;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc == null) return;

        try {
            if (hotkey.get().isPressed()) {
                if (!keyPressed) {
                    keyPressed = true;

                    if (debugMode.get()) {
                        info("Clipboard connect hotkey pressed!");
                        System.out.println("[ClipboardConnect] Hotkey pressed - starting execution");
                    }

                    executeClipboardConnect();
                }
            } else {
                keyPressed = false;
            }
        } catch (Exception e) {
            error("Error during hotkey check: " + e.getMessage());
            if (debugMode.get()) {
                e.printStackTrace();
            }
        }
    }

    private void executeClipboardConnect() {
        try {
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Starting clipboard connect execution...");
            }

            String clipboardText = getClipboardText();

            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Raw clipboard result: " +
                    (clipboardText != null ? "'" + clipboardText + "' (length: " + clipboardText.length() + ")" : "null"));
            }

            if (clipboardText == null || clipboardText.trim().isEmpty()) {
                error("Clipboard is empty or unreadable!");
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Clipboard text is null or empty");
                    // Versuche alternative Methoden
                    tryAlternativeClipboardMethods();
                }
                return;
            }

            String serverAddress = clipboardText.trim();

            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Processing server address: '" + serverAddress + "'");
            }

            // Validate server address
            if (validateIP.get()) {
                if (!validateServerAddress(serverAddress)) {
                    return;
                }
            } else if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Skipping validation (disabled)");
            }

            info("Connecting to server: " + serverAddress);

            // Execute connection on main thread
            mc.execute(() -> {
                try {
                    if (debugMode.get()) {
                        System.out.println("[ClipboardConnect] Executing connection on main thread...");
                    }

                    // Disconnect from current server if enabled
                    if (autoDisconnect.get() && mc.world != null) {
                        if (debugMode.get()) {
                            System.out.println("[ClipboardConnect] Disconnecting from current server...");
                        }

                        mc.world.disconnect(Text.literal(""));
                        mc.setScreen(new MultiplayerScreen(new TitleScreen()));

                        // Small delay to ensure proper disconnect
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);
                                mc.execute(() -> connectToServer(serverAddress));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (Exception e) {
                                error("Delayed connection failed: " + e.getMessage());
                                if (debugMode.get()) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    } else {
                        connectToServer(serverAddress);
                    }

                } catch (Exception e) {
                    error("Connection execution failed: " + e.getMessage());
                    if (debugMode.get()) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            error("Error executing clipboard connect: " + e.getMessage());
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Full exception details:");
                e.printStackTrace();
            }
        }
    }

    private String getClipboardText() {
        String result = null;

        // Methode 1: Minecraft's eigenes Clipboard (priorität)
        if (useMinecraftClipboard.get()) {
            try {
                // Versuche Minecraft's Clipboard zu verwenden (sicherer in 1.21+)
                result = mc.keyboard.getClipboard();
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Minecraft clipboard result: " +
                        (result != null ? "'" + result + "' (length: " + result.length() + ")" : "null"));
                }
                if (result != null && !result.trim().isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Minecraft clipboard failed: " + e.getMessage());
                }
            }
        }

        // Methode 2: System Clipboard mit erweiterten Sicherheitschecks
        try {
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Trying system clipboard...");
            }

            // Prüfe erst ob Toolkit verfügbar ist (manchmal null in headless environments)
            Toolkit toolkit = null;
            try {
                toolkit = Toolkit.getDefaultToolkit();
            } catch (Exception e) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Toolkit not available: " + e.getMessage());
                }
                return null;
            }

            if (toolkit == null) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Toolkit is null");
                }
                return null;
            }

            Clipboard clipboard = toolkit.getSystemClipboard();
            if (clipboard == null) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] System clipboard is null!");
                }
                return null;
            }

            // Sicherheitscheck: Ist überhaupt String-Data verfügbar?
            try {
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    if (debugMode.get()) {
                        // Zeige verfügbare Formate
                        DataFlavor[] flavors = clipboard.getAvailableDataFlavors();
                        System.out.println("[ClipboardConnect] String flavor not available. Available flavors: " + flavors.length);
                        for (DataFlavor flavor : flavors) {
                            System.out.println("  - " + flavor.getMimeType());
                        }
                    }
                    return null;
                }
            } catch (IllegalStateException e) {
                // Clipboard ist gesperrt/nicht verfügbar
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Clipboard locked: " + e.getMessage());
                }
                return null;
            }

            // Versuche Daten zu lesen mit Timeout-Protection
            Object data = null;
            try {
                data = clipboard.getData(DataFlavor.stringFlavor);
            } catch (IOException e) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] IO Exception reading clipboard: " + e.getMessage());
                }
                return null;
            } catch (UnsupportedFlavorException e) {
                if (debugMode.get()) {
                    System.out.println("[ClipboardConnect] Unsupported flavor: " + e.getMessage());
                }
                return null;
            }

            result = data != null ? data.toString() : null;

            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] System clipboard result: " +
                    (result != null ? "'" + result + "' (length: " + result.length() + ")" : "null"));
            }

            return result;

        } catch (SecurityException e) {
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] SecurityException: " + e.getMessage());
            }
            error("Clipboard access denied by security policy");
            return null;
        } catch (Exception e) {
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Unexpected clipboard error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            return null;
        }
    }

    private void tryAlternativeClipboardMethods() {
        // Versuche verschiedene alternative Methoden
        if (debugMode.get()) {
            System.out.println("[ClipboardConnect] Trying alternative clipboard methods...");

            try {
                // Prüfe ob überhaupt ein Clipboard verfügbar ist
                if (!Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    System.out.println("[ClipboardConnect] No string data in clipboard");
                } else {
                    System.out.println("[ClipboardConnect] String data should be available, but reading failed");
                }
            } catch (Exception e) {
                System.out.println("[ClipboardConnect] Alternative check failed: " + e.getMessage());
            }
        }
    }

    private void connectToServer(String serverAddress) {
        try {
            if (debugMode.get()) {
                System.out.println("[ClipboardConnect] Connecting to: " + serverAddress);
            }

            // Parse server address
            ServerAddress parsedAddress = ServerAddress.parse(serverAddress);

            // Create server info
            ServerInfo serverInfo = new ServerInfo("ClipboardConnect", serverAddress, ServerInfo.ServerType.OTHER);

            // Set screen to multiplayer screen first
            MultiplayerScreen multiplayerScreen = new MultiplayerScreen(new TitleScreen());
            mc.setScreen(multiplayerScreen);

            // Connect to server
            ConnectScreen.connect(
                multiplayerScreen,
                mc,
                parsedAddress,
                serverInfo,
                false, // quickPlay
                null   // transferState
            );

            info("Connecting to: " + serverAddress);

        } catch (Exception e) {
            error("Failed to connect to server: " + e.getMessage());
            if (debugMode.get()) {
                e.printStackTrace();
            }
        }
    }

    private boolean validateServerAddress(String address) {
        if (address.isEmpty()) {
            error("Server address cannot be empty.");
            return false;
        }

        // Remove protocol if present
        String cleanAddress = address.replaceFirst("^(minecraft://|mc://)", "");

        // Check if it's a valid IP or domain
        boolean isValidIP = IP_PATTERN.matcher(cleanAddress).matches();
        boolean isValidDomain = DOMAIN_PATTERN.matcher(cleanAddress).matches();

        if (!isValidIP && !isValidDomain) {
            error("Invalid server address format: " + address);
            if (debugMode.get()) {
                info("Address should be in format: IP:Port or domain.com:port");
            }
            return false;
        }

        // Check port range if specified
        if (cleanAddress.contains(":")) {
            String[] parts = cleanAddress.split(":");
            if (parts.length == 2) {
                try {
                    int port = Integer.parseInt(parts[1]);
                    if (port < 1 || port > 65535) {
                        error("Invalid port number: " + port + " (must be 1-65535)");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    error("Invalid port format in: " + address);
                    return false;
                }
            }
        }

        if (debugMode.get()) {
            System.out.println("[ClipboardConnect] Server address validated: " + cleanAddress);
        }

        return true;
    }

    @Override
    public void onActivate() {
        keyPressed = false;
        System.out.println("[ClipboardConnect] Module activated!");
        info("Clipboard Connect activated!");

        if (debugMode.get()) {
            info("Debug mode enabled - check console for detailed logs");
        }
    }

    @Override
    public void onDeactivate() {
        System.out.println("[ClipboardConnect] Module deactivated!");
        info("Clipboard Connect deactivated!");
    }

    // Public getter methods for the global service
    public Keybind getHotkey() {
        return hotkey.get();
    }

    public boolean isDebugMode() {
        return debugMode.get();
    }

    public boolean isUseMinecraftClipboard() {
        return useMinecraftClipboard.get();
    }

    public boolean isValidateIP() {
        return validateIP.get();
    }

    public boolean isAutoDisconnect() {
        return autoDisconnect.get();
    }

    // Public methods for the global service to use
    public void executeClipboardConnectFromGlobal() {
        executeClipboardConnect();
    }

    public String getClipboardTextFromGlobal() {
        return getClipboardText();
    }

    public void connectToServerFromGlobal(String serverAddress) {
        connectToServer(serverAddress);
    }

    public boolean validateServerAddressFromGlobal(String address) {
        return validateServerAddress(address);
    }

    // Global service for menu hotkeys
    public static class GlobalClipboardService {
        private Thread hotkeyThread;
        private boolean running = false;
        private final ClipboardConnectModule module;
        private boolean keyPressed = false;

        public GlobalClipboardService(ClipboardConnectModule module) {
            this.module = module;
        }

        public void start() {
            if (running) return;

            running = true;
            hotkeyThread = new Thread(() -> {
                System.out.println("[GlobalClipboardService] Service started!");

                while (running) {
                    try {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null) {
                            Thread.sleep(100);
                            continue;
                        }

                        net.minecraft.client.gui.screen.Screen currentScreen = mc.currentScreen;

                        // Check if we're in a menu where we want the hotkey to work
                        boolean inMenu = currentScreen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen ||
                            currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen ||
                            currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen ||
                            (currentScreen != null &&
                                (currentScreen.getClass().getSimpleName().toLowerCase().contains("disconnect") ||
                                    currentScreen.getClass().getSimpleName().toLowerCase().contains("multiplayer") ||
                                    currentScreen.getClass().getSimpleName().toLowerCase().contains("server")));

                        if (inMenu) {
                            // Check configured hotkey directly via GLFW
                            try {
                                long window = mc.getWindow().getHandle();
                                Keybind hotkey = module.getHotkey();

                                if (hotkey != null && hotkey.isSet()) {
                                    int keyState = GLFW.glfwGetKey(window, hotkey.getValue());

                                    if (keyState == GLFW.GLFW_PRESS) {
                                        if (!keyPressed) {
                                            keyPressed = true;

                                            if (module.isDebugMode()) {
                                                System.out.println("[GlobalClipboardService] Hotkey detected in menu! Screen: " +
                                                    currentScreen.getClass().getSimpleName());
                                            }

                                            // Execute clipboard connect in main thread
                                            mc.execute(() -> {
                                                try {
                                                    if (module.isDebugMode()) {
                                                        System.out.println("[GlobalClipboardService] Executing clipboard connect from menu...");
                                                    }

                                                    String clipboardText = module.getClipboardTextFromGlobal();

                                                    if (clipboardText == null || clipboardText.trim().isEmpty()) {
                                                        System.out.println("[GlobalClipboardService] Clipboard is empty!");
                                                        return;
                                                    }

                                                    String serverAddress = clipboardText.trim();

                                                    // Validate if enabled
                                                    if (module.isValidateIP()) {
                                                        if (!module.validateServerAddressFromGlobal(serverAddress)) {
                                                            return;
                                                        }
                                                    }

                                                    System.out.println("[GlobalClipboardService] Connecting to: " + serverAddress);

                                                    // Connect to server
                                                    module.connectToServerFromGlobal(serverAddress);

                                                } catch (Exception e) {
                                                    System.out.println("[GlobalClipboardService] Error executing clipboard connect: " + e.getMessage());
                                                    if (module.isDebugMode()) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            });
                                        }
                                    } else {
                                        keyPressed = false;
                                    }
                                }
                            } catch (Exception e) {
                                if (module.isDebugMode()) {
                                    System.out.println("[GlobalClipboardService] GLFW error: " + e.getMessage());
                                }
                            }
                        }

                        Thread.sleep(50); // 20 FPS checking

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        if (module.isDebugMode()) {
                            System.out.println("[GlobalClipboardService] Error: " + e.getMessage());
                        }
                    }
                }

                System.out.println("[GlobalClipboardService] Service terminated!");
            });

            hotkeyThread.setDaemon(true);
            hotkeyThread.setName("ClipboardConnect-GlobalService");
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
