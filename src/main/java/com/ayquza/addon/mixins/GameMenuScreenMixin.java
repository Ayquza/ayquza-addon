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

            ButtonWidget quickJoinButton = ButtonWidget.builder(
                    Text.literal("Quick Join"),
                    button -> {
                        MinecraftClient.getInstance().setScreen(new QuickJoinScreen(this));
                    })
                .dimensions(290, this.height - 300, 80, 20) // x, y, width, height (bottom left)
                .build();

            this.addDrawableChild(quickJoinButton);
        }
    }
}

