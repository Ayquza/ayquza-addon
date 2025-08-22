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
        // Add Quick Join button in bottom left corner
        ButtonWidget quickJoinButton = ButtonWidget.builder(
                Text.literal("Quick Join"),
                button -> {
                    MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                })
            .dimensions(193, this.height - 55, 120, 40) // x, y, width, height (bottom left)
            .build();

        this.addDrawableChild(quickJoinButton);
    }
}
