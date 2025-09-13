/*
 * Custom Discord Rich Presence Module for AyquzaAddon
 */

package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class CustomRPC extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Discord App Settings
    private final Setting<String> applicationId = sgGeneral.add(new StringSetting.Builder()
        .name("application-id")
        .description("Your Discord Application ID")
        .defaultValue("1415721711705395312")
        .build()
    );

    // Image Settings
    private final Setting<String> largeImageKey = sgGeneral.add(new StringSetting.Builder()
        .name("large-image-key")
        .description("Large image key")
        .defaultValue("test1")
        .build()
    );

    private final Setting<String> largeImageText = sgGeneral.add(new StringSetting.Builder()
        .name("large-image-text")
        .description("Text shown when hovering over the large image")
        .defaultValue("Ayquza Client")
        .build()
    );

    private final Setting<String> smallImageKey = sgGeneral.add(new StringSetting.Builder()
        .name("small-image-key")
        .description("Small image key")
        .defaultValue("togif")
        .build()
    );

    private final Setting<String> smallImageText = sgGeneral.add(new StringSetting.Builder()
        .name("small-image-text")
        .description("Text shown when hovering over the small image")
        .defaultValue("Use Ayquza addon")
        .build()
    );

    // Text Settings
    private final Setting<String> details = sgGeneral.add(new StringSetting.Builder()
        .name("details")
        .description("First line of the RPC")
        .defaultValue("discord.gg/mlpi")
        .build()
    );

    private final Setting<String> state = sgGeneral.add(new StringSetting.Builder()
        .name("state")
        .description("Second line of the RPC")
        .defaultValue("Use Ayquza Addon!")
        .build()
    );

    // Optional: Timestamp
    private final Setting<Boolean> showTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-timestamp")
        .description("Shows elapsed time")
        .defaultValue(false)
        .build()
    );

    // Debug Mode
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows debug messages in the console")
        .defaultValue(false)
        .build()
    );

    private RichPresence rpc;
    private boolean needsUpdate = true;
    private long startTime;

    public CustomRPC() {
        super(AyquzaAddon.CATEGORY, "custom-rpc", "Custom Discord Rich Presence for AyquzaAddon.");
        runInMainMenu = true;
        System.out.println("[CustomRPC] Module constructed!");
    }

    @Override
    public void onActivate() {
        try {
            if (debugMode.get()) {
                System.out.println("[CustomRPC] Activating Custom RPC...");
            }

            // Convert Application ID to long
            long appId = Long.parseLong(applicationId.get());

            // Start Discord
            DiscordIPC.start(appId, null);

            rpc = new RichPresence();
            startTime = System.currentTimeMillis() / 1000L;

            updateRPC();

            info("Custom RPC enabled!");
            System.out.println("[CustomRPC] Module activated successfully!");

        } catch (NumberFormatException e) {
            error("Invalid Application ID: " + applicationId.get());
            System.err.println("[CustomRPC] Invalid Application ID: " + applicationId.get());
        } catch (Exception e) {
            error("Error while activating: " + e.getMessage());
            System.err.println("[CustomRPC] Activation error: " + e.getMessage());
            if (debugMode.get()) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDeactivate() {
        try {
            DiscordIPC.stop();
            info("Custom RPC disabled!");
            System.out.println("[CustomRPC] Module deactivated!");
        } catch (Exception e) {
            if (debugMode.get()) {
                System.err.println("[CustomRPC] Deactivation error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateRPC() {
        if (rpc == null) {
            if (debugMode.get()) {
                System.out.println("[CustomRPC] RPC is null, cannot update");
            }
            return;
        }

        try {
            // Set timestamp
            if (showTimestamp.get()) {
                rpc.setStart(startTime);
            }

            // Set images
            rpc.setLargeImage(largeImageKey.get(), largeImageText.get());
            rpc.setSmallImage(smallImageKey.get(), smallImageText.get());

            // Set text
            rpc.setDetails(details.get());
            rpc.setState(state.get());

            // Apply RPC
            DiscordIPC.setActivity(rpc);
            needsUpdate = false;

            if (debugMode.get()) {
                System.out.println("[CustomRPC] RPC updated successfully");
                System.out.println("  Details: " + details.get());
                System.out.println("  State: " + state.get());
                System.out.println("  Large Image: " + largeImageKey.get());
                System.out.println("  Small Image: " + smallImageKey.get());
            }

        } catch (Exception e) {
            error("Error while updating RPC: " + e.getMessage());
            if (debugMode.get()) {
                System.err.println("[CustomRPC] Update error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            // Update only when settings have changed
            if (needsUpdate) {
                updateRPC();
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                System.err.println("[CustomRPC] Tick error: " + e.getMessage());
            }
        }
    }

    // Trigger update when settings change
    public void triggerUpdate() {
        needsUpdate = true;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        // Header
        table.add(theme.label("AyquzaAddon Discord RPC")).expandX();
        table.row();
        table.add(theme.label("")).expandX(); // Empty line
        table.row();

        // Setup instructions
        table.add(theme.label("Setup Instructions:")).expandX();
        table.row();
        table.add(theme.label("1. Go to discord.com/developers/applications")).expandX();
        table.row();
        table.add(theme.label("2. Create a new application")).expandX();
        table.row();
        table.add(theme.label("3. Copy the Application ID and paste it above")).expandX();
        table.row();
        table.add(theme.label("4. Under Rich Presence > Art Assets, upload images")).expandX();
        table.row();
        table.add(theme.label("5. Use the image names as keys")).expandX();
        table.row();

        // Empty line
        table.add(theme.label("")).expandX();
        table.row();

        // Status
        String status = DiscordIPC.isConnected() ? "Connected" : "Disconnected";
        table.add(theme.label("Discord Status: " + status)).expandX();
        table.row();

        // Empty line
        table.add(theme.label("")).expandX();
        table.row();

        // Buttons
        WButton updateButton = theme.button("Update RPC Now");
        updateButton.action = this::updateRPC;
        table.add(updateButton).expandX();
        table.row();

        WButton restartButton = theme.button("Restart RPC Completely");
        restartButton.action = () -> {
            try {
                onDeactivate();
                Thread.sleep(1000); // Short wait
                onActivate();
                info("RPC has been restarted!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                error("Error while restarting: " + e.getMessage());
            }
        };
        table.add(restartButton).expandX();
        table.row();

        // Test Button
        WButton testButton = theme.button("Test Connection");
        testButton.action = () -> {
            if (DiscordIPC.isConnected()) {
                info("Discord RPC is connected!");
                updateRPC();
            } else {
                error("Discord RPC is not connected!");
            }
        };
        table.add(testButton).expandX();

        return table;
    }
}
