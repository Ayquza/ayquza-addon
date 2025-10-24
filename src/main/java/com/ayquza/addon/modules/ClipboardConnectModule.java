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
        .defaultValue(true)
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
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useMinecraftClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("use-minecraft-clipboard")
        .description("Try to use Minecraft's internal clipboard instead of system clipboard.")
        .defaultValue(true)
        .build()
    );

    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::[0-9]{1,5})?$"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*(?::[0-9]{1,5})?$"
    );

    private boolean keyPressed = false;

    public ClipboardConnectModule() {
        super(AyquzaAddon.CATEGORY, "clipboard-connect", "Connect to server with IP from clipboard via hotkey.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc == null) return;

        if (hotkey.get().isPressed()) {
            if (!keyPressed) {
                keyPressed = true;
                executeClipboardConnect();
            }
        } else {
            keyPressed = false;
        }
    }

    private void executeClipboardConnect() {
        String clipboardText = getClipboardText();
        if (clipboardText == null || clipboardText.trim().isEmpty()) return;

        String serverAddress = clipboardText.trim();

        if (validateIP.get() && !validateServerAddress(serverAddress)) return;

        mc.execute(() -> {
            try {
                if (autoDisconnect.get() && mc.world != null) {
                    mc.world.disconnect(Text.literal(""));
                    mc.setScreen(new MultiplayerScreen(new TitleScreen()));
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            mc.execute(() -> connectToServer(serverAddress));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                } else {
                    connectToServer(serverAddress);
                }
            } catch (Exception ignored) {}
        });
    }

    private String getClipboardText() {
        String result = null;
        if (useMinecraftClipboard.get()) {
            try {
                result = mc.keyboard.getClipboard();
                if (result != null && !result.trim().isEmpty()) return result;
            } catch (Exception ignored) {}
        }

        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            if (toolkit == null) return null;

            Clipboard clipboard = toolkit.getSystemClipboard();
            if (clipboard == null || !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null;

            Object data = clipboard.getData(DataFlavor.stringFlavor);
            result = data != null ? data.toString() : null;

            return result;
        } catch (IOException | UnsupportedFlavorException | SecurityException ignored) {
            return null;
        }
    }

    private void connectToServer(String serverAddress) {
        try {
            ServerAddress parsedAddress = ServerAddress.parse(serverAddress);
            ServerInfo serverInfo = new ServerInfo("ClipboardConnect", serverAddress, ServerInfo.ServerType.OTHER);
            MultiplayerScreen multiplayerScreen = new MultiplayerScreen(new TitleScreen());
            mc.setScreen(multiplayerScreen);
            ConnectScreen.connect(multiplayerScreen, mc, parsedAddress, serverInfo, false, null);
        } catch (Exception ignored) {}
    }

    private boolean validateServerAddress(String address) {
        if (address.isEmpty()) return false;
        String cleanAddress = address.replaceFirst("^(minecraft://|mc://)", "");
        boolean isValidIP = IP_PATTERN.matcher(cleanAddress).matches();
        boolean isValidDomain = DOMAIN_PATTERN.matcher(cleanAddress).matches();
        if (!isValidIP && !isValidDomain) return false;

        if (cleanAddress.contains(":")) {
            String[] parts = cleanAddress.split(":");
            if (parts.length == 2) {
                try {
                    int port = Integer.parseInt(parts[1]);
                    if (port < 1 || port > 65535) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onActivate() {
        keyPressed = false;
    }

    @Override
    public void onDeactivate() {}

    public Keybind getHotkey() { return hotkey.get(); }
    public boolean isDebugMode() { return debugMode.get(); }
    public boolean isUseMinecraftClipboard() { return useMinecraftClipboard.get(); }
    public boolean isValidateIP() { return validateIP.get(); }
    public boolean isAutoDisconnect() { return autoDisconnect.get(); }

    public void executeClipboardConnectFromGlobal() { executeClipboardConnect(); }
    public String getClipboardTextFromGlobal() { return getClipboardText(); }
    public void connectToServerFromGlobal(String serverAddress) { connectToServer(serverAddress); }
    public boolean validateServerAddressFromGlobal(String address) { return validateServerAddress(address); }

    public static class GlobalClipboardService {
        private Thread hotkeyThread;
        private boolean running = false;
        private final ClipboardConnectModule module;
        private boolean keyPressed = false;

        public GlobalClipboardService(ClipboardConnectModule module) { this.module = module; }

        public void start() {
            if (running) return;
            running = true;

            hotkeyThread = new Thread(() -> {
                while (running) {
                    try {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null) { Thread.sleep(100); continue; }

                        net.minecraft.client.gui.screen.Screen currentScreen = mc.currentScreen;
                        boolean inMenu = currentScreen instanceof MultiplayerScreen ||
                            currentScreen instanceof TitleScreen ||
                            currentScreen != null && (currentScreen.getClass().getSimpleName().toLowerCase().contains("disconnect") ||
                                currentScreen.getClass().getSimpleName().toLowerCase().contains("multiplayer") ||
                                currentScreen.getClass().getSimpleName().toLowerCase().contains("server"));

                        if (inMenu) {
                            long window = mc.getWindow().getHandle();
                            Keybind hotkey = module.getHotkey();
                            if (hotkey != null && hotkey.isSet() && GLFW.glfwGetKey(window, hotkey.getValue()) == GLFW.GLFW_PRESS) {
                                if (!keyPressed) {
                                    keyPressed = true;
                                    mc.execute(() -> {
                                        String clipboardText = module.getClipboardTextFromGlobal();
                                        if (clipboardText == null || clipboardText.trim().isEmpty()) return;
                                        String serverAddress = clipboardText.trim();
                                        if (module.isValidateIP() && !module.validateServerAddressFromGlobal(serverAddress)) return;
                                        module.connectToServerFromGlobal(serverAddress);
                                    });
                                }
                            } else keyPressed = false;
                        }
                        Thread.sleep(50);
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    catch (Exception ignored) {}
                }
            });

            hotkeyThread.setDaemon(true);
            hotkeyThread.setName("ClipboardConnect-GlobalService");
            hotkeyThread.start();
        }

        public void stop() { running = false; if (hotkeyThread != null) hotkeyThread.interrupt(); }
        public boolean isRunning() { return running && hotkeyThread != null && hotkeyThread.isAlive(); }
    }
}
