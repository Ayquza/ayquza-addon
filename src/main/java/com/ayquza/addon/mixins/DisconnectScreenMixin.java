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
import java.util.Optional;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
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

            // Try to create cracked session with multiple approaches
            Object crackedSession = createCrackedSessionModern(username);

            if (crackedSession == null) {
                crackedSession = createCrackedSessionLegacy(username);
            }

            if (crackedSession != null) {
                // Use reflection to set the session
                Field sessionField = findSessionField(client);
                if (sessionField != null) {
                    sessionField.setAccessible(true);
                    sessionField.set(client, crackedSession);

                    System.out.println("Changed session to cracked account: " + username);
                    System.out.println("New session: " + (client.getSession() != null ? client.getSession().getUsername() : "null"));
                } else {
                    System.err.println("Could not find session field in MinecraftClient");
                }
            } else {
                System.err.println("Failed to create cracked session - session creation returned null");
            }
        } catch (Exception e) {
            System.err.println("Failed to change session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Field findSessionField(MinecraftClient client) {
        try {
            // Try common field names
            String[] fieldNames = {"session", "f_91076_", "field_1726"};

            for (String fieldName : fieldNames) {
                try {
                    Field field = MinecraftClient.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {}
            }

            // If specific names don't work, search through all fields
            Field[] fields = MinecraftClient.class.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType().getSimpleName().contains("Session") ||
                    field.getType().getName().contains("session")) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching for session field: " + e.getMessage());
        }
        return null;
    }

    private Object createCrackedSessionModern(String username) {
        System.out.println("Attempting to create modern cracked session for: " + username);

        try {
            // Try modern class names first
            String[] sessionClassNames = {
                "net.minecraft.client.session.Session",
                "net.minecraft.class_320", // Potential obfuscated name
                "net.minecraft.client.MinecraftClient$Session"
            };

            Class<?> sessionClass = null;
            for (String className : sessionClassNames) {
                try {
                    sessionClass = Class.forName(className);
                    System.out.println("Found session class: " + sessionClass.getName());
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (sessionClass == null) {
                System.out.println("Could not find Session class with modern approach");
                return null;
            }

            // Generate offline UUID
            UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            System.out.println("Generated UUID: " + offlineUUID);

            // Try to find AccountType
            Class<?> accountTypeClass = null;
            String[] accountTypeClassNames = {
                "net.minecraft.client.session.Session$AccountType",
                "net.minecraft.class_320$class_321"
            };

            for (String className : accountTypeClassNames) {
                try {
                    accountTypeClass = Class.forName(className);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (accountTypeClass == null) {
                System.out.println("Could not find AccountType class");
                return null;
            }

            // Get account type
            Object accountType = getAccountType(accountTypeClass);
            if (accountType == null) {
                System.out.println("Could not get account type");
                return null;
            }

            // Try different constructor signatures
            Constructor<?>[] constructors = sessionClass.getConstructors();
            System.out.println("Available constructors: " + constructors.length);

            for (Constructor<?> constructor : constructors) {
                try {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    System.out.println("Constructor params: " + java.util.Arrays.toString(paramTypes));

                    // Try the most common constructor signature
                    if (paramTypes.length >= 4) {
                        Object[] args = new Object[paramTypes.length];
                        args[0] = username; // username
                        args[1] = offlineUUID; // uuid
                        args[2] = ""; // access token

                        // Fill remaining parameters based on type
                        for (int i = 3; i < paramTypes.length; i++) {
                            if (paramTypes[i] == Optional.class) {
                                args[i] = Optional.empty();
                            } else if (paramTypes[i] == accountTypeClass) {
                                args[i] = accountType;
                            } else if (paramTypes[i] == String.class) {
                                args[i] = "";
                            }
                        }

                        Object session = constructor.newInstance(args);
                        System.out.println("Successfully created modern cracked session!");
                        return session;
                    }
                } catch (Exception e) {
                    System.out.println("Constructor failed: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Modern session creation failed: " + e.getMessage());
        }
        return null;
    }

    private Object createCrackedSessionLegacy(String username) {
        System.out.println("Attempting legacy session creation approach");

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Object currentSession = client.getSession();

            if (currentSession != null) {
                Class<?> sessionClass = currentSession.getClass();
                System.out.println("Using current session class: " + sessionClass.getName());

                // Generate offline UUID
                UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());

                // Try to create new session using same class as current session
                Constructor<?>[] constructors = sessionClass.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    try {
                        Class<?>[] paramTypes = constructor.getParameterTypes();
                        Object[] args = new Object[paramTypes.length];

                        // Try to fill parameters intelligently
                        for (int i = 0; i < paramTypes.length; i++) {
                            if (paramTypes[i] == String.class) {
                                if (i == 0) args[i] = username;
                                else args[i] = "";
                            } else if (paramTypes[i] == UUID.class) {
                                args[i] = offlineUUID;
                            } else if (paramTypes[i] == Optional.class) {
                                args[i] = Optional.empty();
                            } else if (paramTypes[i].isEnum()) {
                                // Try to get the first enum value
                                Object[] enumValues = paramTypes[i].getEnumConstants();
                                if (enumValues.length > 0) {
                                    args[i] = enumValues[0];
                                }
                            }
                        }

                        Object session = constructor.newInstance(args);
                        System.out.println("Successfully created legacy cracked session!");
                        return session;

                    } catch (Exception e) {
                        System.out.println("Legacy constructor failed: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Legacy session creation failed: " + e.getMessage());
        }

        return null;
    }

    private Object getAccountType(Class<?> accountTypeClass) {
        try {
            // Try different account type names
            String[] typeNames = {"LEGACY", "MOJANG", "MSA", "OFFLINE"};

            for (String typeName : typeNames) {
                try {
                    Field field = accountTypeClass.getField(typeName);
                    Object type = field.get(null);
                    System.out.println("Using account type: " + typeName);
                    return type;
                } catch (Exception ignored) {}
            }

            // Fallback to first enum constant
            Object[] enumConstants = accountTypeClass.getEnumConstants();
            if (enumConstants.length > 0) {
                System.out.println("Using fallback account type: " + enumConstants[0]);
                return enumConstants[0];
            }

        } catch (Exception e) {
            System.err.println("Failed to get account type: " + e.getMessage());
        }
        return null;
    }
}
