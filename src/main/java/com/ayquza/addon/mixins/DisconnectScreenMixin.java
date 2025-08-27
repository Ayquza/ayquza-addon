package com.ayquza.addon.mixins;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.gui.QuickJoinScreen;
import net.minecraft.client.MinecraftClient;

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
        }
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
}
