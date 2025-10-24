package com.ayquza.addon.mixins;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.gui.QuickJoinScreen;
import net.minecraft.client.MinecraftClient;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addQuickJoinButton(CallbackInfo ci) {
        ButtonWidget joinServerButton = findJoinServerButton();

        if (joinServerButton != null) {
            int buttonX = joinServerButton.getX() - 125;
            int buttonY = joinServerButton.getY();
            int buttonWidth = 120;
            int buttonHeight = 20;

            ButtonWidget quickJoinButton = ButtonWidget.builder(
                    Text.literal("Quick Join"),
                    button -> {
                        MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                    })
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

            this.addDrawableChild(quickJoinButton);
        }
    }

    private ButtonWidget findJoinServerButton() {
        for (var widget : this.children()) {
            if (widget instanceof ButtonWidget button) {
                Text message = button.getMessage();
                String messageString = message.getString();

                if (messageString.equals("Join Server") ||
                    messageString.contains("Join")) {
                    return button;
                }
            }
        }
        return null;
    }
}
