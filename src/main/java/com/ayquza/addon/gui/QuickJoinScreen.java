package com.ayquza.addon.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public class QuickJoinScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget serverAddressField;
    private ButtonWidget joinButton;
    private ButtonWidget cancelButton;

    public QuickJoinScreen(Screen parent) {
        super(Text.literal("Quick Join"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.serverAddressField = new TextFieldWidget(this.textRenderer,
            centerX - 100, centerY - 30, 200, 20, Text.literal("Server Address"));
        this.serverAddressField.setMaxLength(128);
        this.addSelectableChild(this.serverAddressField);

        this.joinButton = ButtonWidget.builder(Text.literal("Join Server"), button -> joinServer())
            .dimensions(centerX - 100, centerY + 5, 98, 20)
            .build();
        this.addDrawableChild(this.joinButton);

        this.cancelButton = ButtonWidget.builder(Text.literal("Cancel"), button ->
                MinecraftClient.getInstance().setScreen(this.parent))
            .dimensions(centerX + 2, centerY + 5, 98, 20)
            .build();
        this.addDrawableChild(this.cancelButton);

        this.setInitialFocus(this.serverAddressField);


        connectFromClipboard();
    }

    private void connectFromClipboard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String clipboard = mc.keyboard.getClipboard().trim();
        if (clipboard.isEmpty()) return;

        this.serverAddressField.setText(clipboard);

        mc.setScreen(new MultiplayerScreen(new TitleScreen()));
        mc.execute(() -> ConnectScreen.connect(
            new MultiplayerScreen(new TitleScreen()),
            mc,
            ServerAddress.parse(clipboard),
            new ServerInfo("QuickJoin", clipboard, ServerInfo.ServerType.OTHER),
            false,
            null
        ));
    }

    private void joinServer() {
        connectFromClipboard();
    }
}
