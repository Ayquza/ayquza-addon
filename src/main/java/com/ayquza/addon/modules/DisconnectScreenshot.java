package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import static org.lwjgl.glfw.GLFW.*;

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

    private final Setting<Integer> screenshotDelay = sgGeneral.add(new IntSetting.Builder()
        .name("screenshot-delay-ms")
        .description("Delay in milliseconds before taking screenshot after disconnect detection.")
        .defaultValue(50)
        .min(0)
        .max(500)
        .build()
    );

    private final Setting<Integer> multipleAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("screenshot-attempts")
        .description("Number of screenshot attempts to ensure success.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build()
    );

    private boolean disconnectDetected = false;
    private boolean screenshotTaken = false;
    private int attemptCount = 0;

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
                attemptCount = 0;

                if (enableNotification.get()) {
                    info("Disconnect detected - taking screenshot in " + screenshotDelay.get() + "ms...");
                }

                // Small delay to ensure game is still rendering (but much shorter than before)
                new Thread(() -> {
                    try {
                        Thread.sleep(Math.max(10, screenshotDelay.get())); // At least 10ms, but use setting
                        takeImmediateScreenshot();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Multiple attempts over several ticks to ensure success - back to your working logic
        MinecraftClient mc = MinecraftClient.getInstance();

        if (disconnectDetected && !screenshotTaken && mc.world != null && attemptCount < multipleAttempts.get()) {
            attemptCount++;
            takeImmediateScreenshot();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        disconnectDetected = false;
        screenshotTaken = false;
        attemptCount = 0;
    }

    private void takeImmediateScreenshot() {
        if (screenshotTaken) return;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (enableNotification.get()) {
                info("Taking disconnect screenshot (attempt " + (attemptCount + 1) + ")...");
            }

            mc.execute(() -> {
                try {
                    // Keybind approach (most reliable)
                    if (mc.options != null && mc.options.screenshotKey != null) {
                        if (enableNotification.get()) {
                            info("Using keybind approach...");
                        }

                        mc.options.screenshotKey.setPressed(true);

                        // Release after very short delay
                        new Thread(() -> {
                            try {
                                Thread.sleep(10);
                                mc.execute(() -> mc.options.screenshotKey.setPressed(false));
                            } catch (Exception e) {
                                // Ignore errors here
                            }
                        }).start();

                        // Mark screenshot as "taken" after short delay
                        new Thread(() -> {
                            try {
                                Thread.sleep(200);
                                screenshotTaken = true;
                            } catch (Exception e) {
                                // Ignore errors here
                            }
                        }).start();
                    }

                    // Screenshot processing after delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
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

            // Back to longer timespan but improved logic
            long cutoffTime = System.currentTimeMillis() - 15000; // 15 seconds (compromise)

            File[] screenshots = screenshotsDir.listFiles((dir, name) -> {
                if (!name.toLowerCase().endsWith(".png") || name.startsWith("disconnect")) {
                    return false;
                }
                File file = new File(dir, name);
                return file.lastModified() > cutoffTime;
            });

            if (screenshots != null && screenshots.length > 0) {
                java.util.Arrays.sort(screenshots,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                File latestScreenshot = screenshots[0];

                if (enableNotification.get()) {
                    long ageSeconds = (System.currentTimeMillis() - latestScreenshot.lastModified()) / 1000;
                    info("Found screenshot: " + latestScreenshot.getName() + " (" + ageSeconds + "s old)");
                }

                String newName = generateFileName();
                File targetFile = new File(disconnectDir, newName);

                try {
                    if (latestScreenshot.renameTo(targetFile)) {
                        if (enableNotification.get()) {
                            info("✓ Disconnect screenshot moved: " + newName);
                        }
                    } else {
                        java.nio.file.Files.copy(latestScreenshot.toPath(), targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        latestScreenshot.delete();
                        if (enableNotification.get()) {
                            info("✓ Disconnect screenshot copied: " + newName);
                        }
                    }
                } catch (Exception e) {
                    if (enableNotification.get()) {
                        error("Failed to move/copy screenshot: " + e.getMessage());
                    }
                }

            } else {
                if (enableNotification.get()) {
                    warning("No recent screenshots found to move (checked last 15 seconds)");
                }
            }

        } catch (Exception e) {
            if (enableNotification.get()) {
                error("Failed to process screenshot movement: " + e.getMessage());
            }
        }
    }

    private String generateFileName() {
        StringBuilder fileName = new StringBuilder("disconnect");

        MinecraftClient mc = MinecraftClient.getInstance();

        try {
            if (mc.getCurrentServerEntry() != null) {
                String serverAddress = mc.getCurrentServerEntry().address;
                serverAddress = serverAddress.replaceAll("[^a-zA-Z0-9.-]", "_");
                fileName.append("_").append(serverAddress);
            } else if (mc.isInSingleplayer()) {
                fileName.append("_singleplayer");
            }
        } catch (Exception e) {
            // Ignore errors when getting server info
        }

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
        attemptCount = 0;
        if (enableNotification.get()) {

        }
    }

    @Override
    public void onDeactivate() {
        disconnectDetected = false;
        screenshotTaken = false;
        attemptCount = 0;
        if (enableNotification.get()) {
            info("DisconnectScreenshot deactivated");
        }
    }
}
