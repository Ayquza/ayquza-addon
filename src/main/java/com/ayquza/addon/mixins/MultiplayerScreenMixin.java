
package com.ayquza.addon.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.gui.QuickJoinScreen;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Shadow
    private MultiplayerServerListWidget serverListWidget;

    private TextFieldWidget nameSearchField;
    private TextFieldWidget motdSearchField;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addQuickJoinButton(CallbackInfo ci) {

        ButtonWidget joinServerButton = findJoinServerButton();
        if (joinServerButton != null) {
            int buttonX = joinServerButton.getX() - 125;
            int buttonY = joinServerButton.getY();

            ButtonWidget quickJoinButton = ButtonWidget.builder(
                    Text.literal("Quick Join"),
                    button -> MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this)))
                .dimensions(buttonX, buttonY, 120, 20)
                .build();
            this.addDrawableChild(quickJoinButton);
        }


        int fieldWidth = 150;
        int fieldHeight = 12;
        int bottomY = this.height - 30;

        int leftEdge = 170;


        motdSearchField = new TextFieldWidget(
            this.textRenderer,
            leftEdge - fieldWidth,
            bottomY,
            fieldWidth,
            fieldHeight,
            Text.literal("Search MOTD...")
        );
        motdSearchField.setMaxLength(64);
        motdSearchField.setPlaceholder(Text.literal("Search MOTD..."));
        motdSearchField.setChangedListener(text -> applyFilter());
        this.addDrawableChild(motdSearchField);


        nameSearchField = new TextFieldWidget(
            this.textRenderer,
            leftEdge - fieldWidth,
            bottomY - fieldHeight - 5,
            fieldWidth,
            fieldHeight,
            Text.literal("Search Server...")
        );
        nameSearchField.setMaxLength(64);
        nameSearchField.setPlaceholder(Text.literal("Search Server..."));
        nameSearchField.setChangedListener(text -> applyFilter());
        this.addDrawableChild(nameSearchField);
    }

    private void applyFilter() {
        if (serverListWidget == null) return;

        String nameQuery = nameSearchField != null
            ? nameSearchField.getText().toLowerCase().trim() : "";
        String motdQuery = motdSearchField != null
            ? motdSearchField.getText().toLowerCase().trim() : "";

        ServerList serverList = ((MultiplayerScreenAccessor) (Object) this).getServerList();
        if (serverList == null) return;

        serverListWidget.setServers(serverList);

        if (nameQuery.isEmpty() && motdQuery.isEmpty()) return;


        serverListWidget.children().removeIf(entry -> {
            if (!(entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry)) {
                return false;
            }

            ServerInfo info = ((ServerEntryAccessor) serverEntry).getServer();

            if (!nameQuery.isEmpty()) {
                if (!info.name.toLowerCase().contains(nameQuery)) return true;
            }

            if (!motdQuery.isEmpty()) {
                String motd = info.label != null ? info.label.getString().toLowerCase() : "";
                if (!motd.contains(motdQuery)) return true;
            }

            return false;
        });
    }

    private ButtonWidget findJoinServerButton() {
        for (var widget : this.children()) {
            if (widget instanceof ButtonWidget button) {
                String msg = button.getMessage().getString();
                if (msg.equals("Join Server") || msg.contains("Join")) {
                    return button;
                }
            }
        }
        return null;
    }
}
