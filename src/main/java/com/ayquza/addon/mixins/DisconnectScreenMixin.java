package com.ayquza.addon.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.gui.QuickJoinScreen;
import net.minecraft.client.MinecraftClient;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Clipboard;
import java.util.UUID;
import java.lang.reflect.Field;
import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(DisconnectedScreen.class)
public class DisconnectScreenMixin extends Screen {

    protected DisconnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addQuickJoinButton(CallbackInfo ci) {
        // Find the "Back to server list" button
        ButtonWidget backButton = findBackToServerListButton();

        if (backButton != null) {
            // Position the Quick Join button to the left of the Back button
            int quickJoinX = backButton.getX() - 105; // 100px width + 5px spacing
            int quickJoinY = backButton.getY();

            // Make sure the button doesn't go off screen
            if (quickJoinX < 10) {
                // If not enough space on the left, place to the right of the Back button
                quickJoinX = backButton.getX() + backButton.getWidth() + 5;

                // If also no space on the right, place above the Back button
                if (quickJoinX + 100 > this.width - 10) {
                    quickJoinX = backButton.getX();
                    quickJoinY = backButton.getY() - 25; // 20px height + 5px spacing
                }
            }

            ButtonWidget quickJoinButton = ButtonWidget.builder(
                    Text.literal("Quick Join"),
                    button -> {
                        MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                    })
                .dimensions(quickJoinX, quickJoinY, 100, 20)
                .build();

            this.addDrawableChild(quickJoinButton);

            // Add Clipboard Reconnect button under Quick Join button
            addClipBoardReconnectButton(quickJoinButton);
        }
    }

    private void addClipBoardReconnectButton(ButtonWidget quickJoinButton) {
        // Only add the button if AutoReconnect has a server connection stored
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect.lastServerConnection == null) {
            System.out.println("No server connection stored in AutoReconnect - connect to a server first");
            return;
        }

        // Position the ClipBoard Reconnect button exactly under the Quick Join button
        int clipboardButtonX = quickJoinButton.getX();
        int clipboardButtonY = quickJoinButton.getY() + 25; // 20px height + 5px spacing

        // Create the ClipBoard Reconnect button
        ButtonWidget clipboardReconnectButton = ButtonWidget.builder(
                Text.literal("Clipboard Reconnect"),
                button -> {
                    String clipboardName = getClipboardContent();
                    if (clipboardName != null && !clipboardName.trim().isEmpty()) {
                        reconnectWithCrackedAccount(clipboardName.trim());
                    } else {
                        System.out.println("Clipboard is empty or contains no valid username!");
                    }
                })
            .dimensions(clipboardButtonX, clipboardButtonY, 120, 20) // Slightly wider for the text
            .build();

        this.addDrawableChild(clipboardReconnectButton);
    }

    private ButtonWidget findBackToServerListButton() {
        // Method 1: Search by text content
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String buttonText = button.getMessage().getString();

                // Look for "Back to server list" or similar variations
                if (buttonText.equals("Back to server list") ||
                    buttonText.contains("Back to") ||
                    buttonText.contains("server list") ||
                    buttonText.contains("Server List")) {
                    return button;
                }
            }
        }

        // Method 2: Search for buttons containing "Back" or "Menu"
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String buttonText = button.getMessage().getString().toLowerCase();

                if (buttonText.contains("back") ||
                    buttonText.contains("menu")) {
                    return button;
                }
            }
        }

        // Method 3: Fallback - find the bottom-most button (usually the back button)
        ButtonWidget backButton = null;
        int lowestY = -1;
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                if (button.getY() > lowestY) {
                    lowestY = button.getY();
                    backButton = button;
                }
            }
        }

        return backButton;
    }

    private String getClipboardContent() {
        try {
            // First try Minecraft's own clipboard
            MinecraftClient client = MinecraftClient.getInstance();
            String mcClipboard = client.keyboard.getClipboard();
            if (mcClipboard != null && !mcClipboard.trim().isEmpty()) {
                System.out.println("Minecraft clipboard content: '" + mcClipboard + "'");
                return mcClipboard;
            }

            // Fallback to system clipboard
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            if (clipboard != null && clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String content = (String) clipboard.getData(DataFlavor.stringFlavor);
                System.out.println("System clipboard content: '" + content + "'");
                return content;
            }
        } catch (Exception e) {
            System.err.println("Failed to read clipboard: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return null;
    }

    private void reconnectWithCrackedAccount(String username) {
        try {
            // Change session to cracked account
            changeToCrackedSession(username);

            // Use Meteor's AutoReconnect system to reconnect
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            if (autoReconnect.lastServerConnection != null) {
                var lastServer = autoReconnect.lastServerConnection;
                System.out.println("Reconnecting to server...");

                // Use Meteor's connection method (same as their tryConnecting())
                ConnectScreen.connect(new TitleScreen(), mc, lastServer.left(), lastServer.right(), false, null);
            } else {
                System.out.println("No server connection stored in AutoReconnect!");
            }
        } catch (Exception e) {
            System.err.println("Failed to reconnect with cracked account: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void changeToCrackedSession(String username) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();

            System.out.println("Current session before change: " + (client.getSession() != null ? client.getSession().getUsername() : "null"));

            // Create offline/cracked session - try multiple approaches
            Object crackedSession = createCrackedSession(username);

            if (crackedSession != null) {
                // Use reflection to set the session
                Field sessionField = MinecraftClient.class.getDeclaredField("session");
                sessionField.setAccessible(true);
                sessionField.set(client, crackedSession);

                System.out.println("Changed session to cracked account: " + username);
                System.out.println("New session: " + (client.getSession() != null ? client.getSession().getUsername() : "null"));
            } else {
                System.err.println("Failed to create cracked session - session creation returned null");
            }
        } catch (Exception e) {
            System.err.println("Failed to change session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Object createCrackedSession(String username) {
        System.out.println("Attempting to create cracked session for: " + username);

        try {
            // Generate offline UUID
            UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            System.out.println("Generated UUID: " + offlineUUID);

            // Find the Session class
            Class<?> sessionClass = Class.forName("net.minecraft.client.session.Session");
            System.out.println("Found session class: " + sessionClass.getName());

            // Find the AccountType enum
            Class<?> accountTypeClass = Class.forName("net.minecraft.client.session.Session$AccountType");
            System.out.println("Found AccountType class: " + accountTypeClass.getName());

            // Get the LEGACY account type (for cracked accounts)
            Object legacyAccountType = null;
            try {
                // Try LEGACY first
                legacyAccountType = accountTypeClass.getField("LEGACY").get(null);
                System.out.println("Using LEGACY account type");
            } catch (Exception e) {
                // If LEGACY doesn't exist, try other types
                Object[] enumConstants = accountTypeClass.getEnumConstants();
                System.out.println("Available AccountTypes:");
                for (Object enumConstant : enumConstants) {
                    System.out.println("- " + enumConstant.toString());
                }

                // Use the first available type as fallback
                if (enumConstants.length > 0) {
                    legacyAccountType = enumConstants[0];
                    System.out.println("Using fallback account type: " + legacyAccountType);
                }
            }

            if (legacyAccountType == null) {
                System.err.println("Could not find suitable AccountType");
                return null;
            }

            // Create session with correct constructor:
            // Session(String username, UUID uuid, String accessToken, Optional<String> xuid, Optional<String> clientId, AccountType accountType)
            Object crackedSession = sessionClass.getConstructor(
                String.class,
                UUID.class,
                String.class,
                java.util.Optional.class,
                java.util.Optional.class,
                accountTypeClass
            ).newInstance(
                username,
                offlineUUID,
                "", // empty access token for cracked
                java.util.Optional.empty(), // empty xuid
                java.util.Optional.empty(), // empty clientId
                legacyAccountType
            );

            System.out.println("Successfully created cracked session!");
            return crackedSession;

        } catch (Exception e) {
            System.err.println("Failed to create cracked session: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
