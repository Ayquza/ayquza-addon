package com.ayquza.addon.mixins;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.gui.QuickJoinScreen;
import net.minecraft.client.MinecraftClient;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void addQuickJoinButton(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.getCurrentServerEntry() != null) {

            // Find the Disconnect button
            ButtonWidget disconnectButton = findDisconnectButton();

            if (disconnectButton != null) {
                // Position the Quick Join button to the left of the Disconnect button
                int quickJoinX = disconnectButton.getX() - 85; // 80px width + 5px spacing
                int quickJoinY = disconnectButton.getY();

                // Make sure the button doesn't go off screen
                if (quickJoinX < 10) {
                    // If not enough space on the left, place to the right of the Disconnect button
                    quickJoinX = disconnectButton.getX() + disconnectButton.getWidth() + 5;

                    // If also no space on the right, place above the Disconnect button
                    if (quickJoinX + 80 > this.width - 10) {
                        quickJoinX = disconnectButton.getX();
                        quickJoinY = disconnectButton.getY() - 25; // 20px height + 5px spacing
                    }
                }

                ButtonWidget quickJoinButton = ButtonWidget.builder(
                        Text.literal("Quick Join"),
                        button -> {
                            MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                        })
                    .dimensions(quickJoinX, quickJoinY, 80, 20)
                    .build();

                this.addDrawableChild(quickJoinButton);
            }
        }
    }

    private ButtonWidget findDisconnectButton() {
        // Method 1: Search by text content
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String buttonText = button.getMessage().getString().toLowerCase();

                // Various possible texts for the Disconnect button
                if (buttonText.contains("disconnect") ||
                    buttonText.contains("leave server") ||
                    buttonText.contains("quit server")) {
                    return button;
                }
            }
        }

        // Method 2: Search by typical position of Disconnect button
        // In GameMenu, the Disconnect button is usually in the lower half
        ButtonWidget disconnectButton = null;
        int centerY = this.height / 2;

        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                // Disconnect button is often in the lower half of the screen
                if (button.getY() > centerY) {
                    // Check if it could be a typical Disconnect button
                    // (often 200px wide and centered or positioned left/right)
                    if (button.getWidth() >= 200 ||
                        (button.getX() > this.width / 4 && button.getX() < 3 * this.width / 4)) {
                        if (disconnectButton == null || button.getY() > disconnectButton.getY()) {
                            disconnectButton = button;
                        }
                    }
                }
            }
        }

        // Method 3: If still not found, take the bottom-most button
        if (disconnectButton == null) {
            int lowestY = -1;
            for (var element : this.children()) {
                if (element instanceof ButtonWidget button) {
                    if (button.getY() > lowestY) {
                        lowestY = button.getY();
                        disconnectButton = button;
                    }
                }
            }
        }

        return disconnectButton;
    }
}
