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
        // Finde den Back-Button durch verschiedene Methoden
        ButtonWidget backButton = findBackButton();

        if (backButton != null) {
            int quickJoinX = backButton.getX() - 105; // 100px Breite + 5px Abstand
            int quickJoinY = backButton.getY();

            // Stelle sicher, dass der Button nicht außerhalb des Bildschirms ist
            if (quickJoinX < 10) {
                // Falls nicht genug Platz links, platziere rechts vom Back-Button
                quickJoinX = backButton.getX() + backButton.getWidth() + 5;

                // Falls auch rechts kein Platz, platziere über dem Back-Button
                if (quickJoinX + 100 > this.width - 10) {
                    quickJoinX = backButton.getX();
                    quickJoinY = backButton.getY() - 25; // 20px Höhe + 5px Abstand
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

    private ButtonWidget findBackButton() {
        ButtonWidget backButton = null;

        // Methode 1: Suche nach Text
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String buttonText = button.getMessage().getString().toLowerCase();

                if (buttonText.contains("back") || buttonText.contains("server list") ||
                    buttonText.contains("menu") || buttonText.contains("zurück") ||
                    buttonText.contains("serverliste")) {
                    return button;
                }
            }
        }

        // Methode 2: Suche nach Position (unterster Button)
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
