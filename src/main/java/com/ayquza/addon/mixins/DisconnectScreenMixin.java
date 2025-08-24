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


        int buttonY = this.height / 2 - 7;
        int buttonX = this.width / 2 - 205;

        ButtonWidget quickJoinButton = ButtonWidget.builder(
                Text.literal("Quick Join"),
                button -> {
                    MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                })
            .dimensions(buttonX, buttonY, 100, 20)
            .build();

        this.addDrawableChild(quickJoinButton);
    }
}
