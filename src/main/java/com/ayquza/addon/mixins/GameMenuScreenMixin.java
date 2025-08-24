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

            // Finde den Disconnect-Button durch verschiedene Methoden
            ButtonWidget disconnectButton = findDisconnectButton();

            if (disconnectButton != null) {
                int quickJoinX = disconnectButton.getX() - 85; // 80px Breite + 5px Abstand
                int quickJoinY = disconnectButton.getY();

                // Stelle sicher, dass der Button nicht außerhalb des Bildschirms ist
                if (quickJoinX < 10) {
                    // Falls nicht genug Platz links, platziere rechts vom Disconnect-Button
                    quickJoinX = disconnectButton.getX() + disconnectButton.getWidth() + 5;

                    // Falls auch rechts kein Platz, platziere über dem Disconnect-Button
                    if (quickJoinX + 80 > this.width - 10) {
                        quickJoinX = disconnectButton.getX();
                        quickJoinY = disconnectButton.getY() - 25; // 20px Höhe + 5px Abstand
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
            } else {
                // Fallback: Falls Disconnect-Button nicht gefunden, verwende ursprüngliche Position
                ButtonWidget quickJoinButton = ButtonWidget.builder(
                        Text.literal("Quick Join"),
                        button -> {
                            MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                        })
                    .dimensions(290, this.height - 300, 80, 20)
                    .build();

                this.addDrawableChild(quickJoinButton);
            }
        }
    }

    private ButtonWidget findDisconnectButton() {
        ButtonWidget disconnectButton = null;

        // Methode 1: Suche nach Text (verschiedene Sprachen)
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String buttonText = button.getMessage().getString().toLowerCase();

                // Verschiedene mögliche Texte für den Disconnect-Button
                if (buttonText.contains("disconnect") ||
                    buttonText.contains("leave server") ||
                    buttonText.contains("quit server") ||
                    buttonText.contains("trennen") || // Deutsch
                    buttonText.contains("server verlassen") || // Deutsch
                    buttonText.contains("verbindung trennen") || // Deutsch
                    buttonText.contains("beenden")) { // Deutsch
                    return button;
                }
            }
        }

        // Methode 2: Suche nach typischer Position des Disconnect-Buttons
        // Im GameMenu ist der Disconnect-Button normalerweise in der unteren Hälfte
        int centerY = this.height / 2;

        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                // Disconnect-Button ist oft in der unteren Bildschirmhälfte und hat eine bestimmte Breite
                if (button.getY() > centerY) {
                    // Prüfe ob es ein typischer Disconnect-Button sein könnte
                    // (oft 200px breit und zentriert oder links/rechts positioniert)
                    if (button.getWidth() >= 200 ||
                        (button.getX() > this.width / 4 && button.getX() < 3 * this.width / 4)) {
                        if (disconnectButton == null || button.getY() > disconnectButton.getY()) {
                            disconnectButton = button;
                        }
                    }
                }
            }
        }

        // Methode 3: Falls immer noch nicht gefunden, nimm den untersten Button
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
