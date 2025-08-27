package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DisconnectScreenshot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-notification")
        .description("Show a chat message when a disconnect screenshot is taken.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> folderName = sgGeneral.add(new StringSetting.Builder()
        .name("folder-name")
        .description("Custom folder name for disconnect screenshots (inside screenshots folder).")
        .defaultValue("disconnect-screenshots")
        .build()
    );

    private final Setting<Boolean> includeTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("include-timestamp")
        .description("Include timestamp in the screenshot filename.")
        .defaultValue(true)
        .build()
    );

    private boolean disconnectDetected = false;
    private boolean screenshotTaken = false;

    public DisconnectScreenshot() {
        super(AyquzaAddon.CATEGORY, "disconnect-screenshot", "Takes a screenshot before disconnecting from a server.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof DisconnectS2CPacket && !disconnectDetected) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null && mc.player != null) {
                disconnectDetected = true;
                screenshotTaken = false;

                if (enableNotification.get()) {
                    info("Disconnect detected - taking screenshot...");
                }

                // Sofort Screenshot machen (noch bevor der Screen wechselt)
                takeImmediateScreenshot();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Fallback: Falls das Packet-Event nicht funktioniert hat
        MinecraftClient mc = MinecraftClient.getInstance();

        if (disconnectDetected && !screenshotTaken && mc.world != null) {
            takeImmediateScreenshot();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        disconnectDetected = false;
        screenshotTaken = false;
    }

    private void takeImmediateScreenshot() {
        if (screenshotTaken) return;
        screenshotTaken = true;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (enableNotification.get()) {
                info("Taking disconnect screenshot...");
            }

            // Mehrere Ansätze versuchen
            mc.execute(() -> {
                try {
                    // Ansatz 1: Direkte Screenshot-Keybind Ausführung
                    if (mc.options != null && mc.options.screenshotKey != null) {
                        if (enableNotification.get()) {
                            info("Attempting keybind approach...");
                        }

                        // Simuliere Tastendruck über Keybind-System
                        mc.options.screenshotKey.setPressed(true);

                        // Warte kurz und release
                        new Thread(() -> {
                            try {
                                Thread.sleep(50);
                                mc.options.screenshotKey.setPressed(false);
                            } catch (Exception e) {}
                        }).start();
                    }

                    // Ansatz 2: Direkter Keyboard-Event (Fallback)
                    try {
                        if (enableNotification.get()) {
                            info("Attempting keyboard event approach...");
                        }

                        long window = mc.getWindow().getHandle();

                        // F2 Press
                        mc.keyboard.onKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_F2, 0,
                            org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);

                        // Kurze Verzögerung dann Release
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                                mc.keyboard.onKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_F2, 0,
                                    org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
                            } catch (Exception e) {}
                        }).start();

                    } catch (Exception e2) {
                        if (enableNotification.get()) {
                            warning("Keyboard event failed: " + e2.getMessage());
                        }
                    }

                    // Ansatz 3: Client Screenshot Command (falls verfügbar)
                    try {
                        if (enableNotification.get()) {
                            info("Attempting client screenshot command...");
                        }

                        // Versuche Screenshot über Client-Befehle
                        if (mc.player != null) {
                            mc.player.networkHandler.sendChatCommand("screenshot");
                        }
                    } catch (Exception e3) {
                        // Ignore - nicht alle Clients haben diesen Befehl
                    }

                    if (enableNotification.get()) {
                        info("Screenshot attempts completed - waiting for file...");
                    }

                    // Nach etwas längerer Verzögerung den Screenshot suchen und verschieben
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000); // 1 Sekunde warten
                            moveLatestScreenshot();
                        } catch (Exception e) {
                            if (enableNotification.get()) {
                                error("Failed to process screenshot: " + e.getMessage());
                            }
                        }
                    }).start();

                } catch (Exception e) {
                    if (enableNotification.get()) {
                        error("Failed to simulate screenshot: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            if (enableNotification.get()) {
                error("Failed to schedule disconnect screenshot: " + e.getMessage());
            }
        }
    }

    private void moveLatestScreenshot() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            File screenshotsDir = new File(mc.runDirectory, "screenshots");
            File disconnectDir = new File(screenshotsDir, folderName.get());

            if (!disconnectDir.exists()) {
                disconnectDir.mkdirs();
            }

            // Erweiterte Zeitspanne: 30 Sekunden (sicherer)
            long cutoffTime = System.currentTimeMillis() - 30000;

            // Finde den neuesten Screenshot
            File[] screenshots = screenshotsDir.listFiles((dir, name) -> {
                if (!name.toLowerCase().endsWith(".png") || name.contains("disconnect")) {
                    return false;
                }
                File file = new File(dir, name);
                return file.lastModified() > cutoffTime;
            });

            if (screenshots != null && screenshots.length > 0) {
                // Sortiere nach Änderungsdatum (neueste zuerst)
                java.util.Arrays.sort(screenshots,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                File latestScreenshot = screenshots[0];

                // Debug: Zeige gefundenen Screenshot
                if (enableNotification.get()) {
                    long ageSeconds = (System.currentTimeMillis() - latestScreenshot.lastModified()) / 1000;
                    info("Found screenshot: " + latestScreenshot.getName() + " (" + ageSeconds + "s old)");
                }

                String newName = generateFileName();
                File targetFile = new File(disconnectDir, newName);

                // Verschiebe/kopiere den Screenshot
                if (latestScreenshot.renameTo(targetFile)) {
                    if (enableNotification.get()) {
                        info("Disconnect screenshot moved: " + newName);
                    }
                } else {
                    // Falls rename nicht funktioniert, kopiere die Datei
                    java.nio.file.Files.copy(latestScreenshot.toPath(), targetFile.toPath());
                    latestScreenshot.delete();
                    if (enableNotification.get()) {
                        info("Disconnect screenshot copied: " + newName);
                    }
                }
            } else {
                // Debug: Keine Screenshots gefunden
                if (enableNotification.get()) {
                    warning("No recent screenshots found to move");
                }
            }

        } catch (Exception e) {
            if (enableNotification.get()) {
                error("Failed to move screenshot: " + e.getMessage());
            }
        }
    }

    private String generateFileName() {
        StringBuilder fileName = new StringBuilder("disconnect");

        MinecraftClient mc = MinecraftClient.getInstance();

        // Server-Info hinzufügen
        try {
            if (mc.getCurrentServerEntry() != null) {
                String serverAddress = mc.getCurrentServerEntry().address;
                serverAddress = serverAddress.replaceAll("[^a-zA-Z0-9.-]", "_");
                fileName.append("_").append(serverAddress);
            } else if (mc.isInSingleplayer()) {
                fileName.append("_singleplayer");
            }
        } catch (Exception e) {
            // Ignoriere Fehler bei Server-Info
        }

        // Zeitstempel
        if (includeTimestamp.get()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            fileName.append("_").append(sdf.format(new Date()));
        }

        fileName.append(".png");
        return fileName.toString();
    }

    @Override
    public void onActivate() {
        disconnectDetected = false;
        screenshotTaken = false;
    }

    @Override
    public void onDeactivate() {
        disconnectDetected = false;
        screenshotTaken = false;
    }
}
