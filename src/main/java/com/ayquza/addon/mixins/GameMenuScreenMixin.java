package com.ayquza.addon.mixins;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
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
    private void addCustomButtons(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.getCurrentServerEntry() == null) return;

        ButtonWidget disconnectButton = findDisconnectButton();
        if (disconnectButton == null) return;

        int buttonWidth = disconnectButton.getWidth();
        int buttonHeight = disconnectButton.getHeight();
        int spacing = 5;

        // Quick Join button - half the width of disconnect button, same height
        int quickJoinWidth = buttonWidth / 2;
        int quickJoinHeight = buttonHeight;

        int quickJoinX = disconnectButton.getX() - (quickJoinWidth + spacing);
        int quickJoinY = disconnectButton.getY();

        int reconnectX = disconnectButton.getX();
        int reconnectY = disconnectButton.getY() + buttonHeight + spacing;

        ButtonWidget quickJoinButton = ButtonWidget.builder(
            Text.literal("Quick Join"),
            button -> MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this))
        ).dimensions(quickJoinX, quickJoinY, quickJoinWidth, quickJoinHeight).build();

        ButtonWidget reconnectButton = ButtonWidget.builder(
            Text.literal("Reconnect"),
            button -> {
                ServerInfo info = mc.getCurrentServerEntry();
                if (info != null) {
                    mc.world.disconnect(Text.of(""));
                    ConnectScreen.connect(
                        new MultiplayerScreen(new TitleScreen()),
                        mc,
                        ServerAddress.parse(info.address),
                        info,
                        false,
                        null
                    );
                }
            }
        ).dimensions(reconnectX, reconnectY, buttonWidth, buttonHeight).build();

        this.addDrawableChild(quickJoinButton);
        this.addDrawableChild(reconnectButton);
    }

    private ButtonWidget findDisconnectButton() {
        for (var element : this.children()) {
            if (element instanceof ButtonWidget button) {
                String text = button.getMessage().getString().toLowerCase();
                if (text.contains("disconnect") || text.contains("leave") || text.contains("quit")) {
                    return button;
                }
            }
        }
        return null;
    }
}
