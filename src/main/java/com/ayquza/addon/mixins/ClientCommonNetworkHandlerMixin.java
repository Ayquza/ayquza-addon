package com.ayquza.addon.mixins;

import com.ayquza.addon.modules.FakeDisconnect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ayquza.addon.ReflectionUtil;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {

    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void onDisconnectedPrevent(DisconnectionInfo disconnectionInfo, CallbackInfo ci) {
        if (FakeDisconnect.INSTANCE != null && FakeDisconnect.INSTANCE.isActive()) {
            ci.cancel();

            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                try {
                    mc.inGameHud.getChatHud().addMessage(
                        Text.literal("§c[FakeDisconnect] Server kicked you for: ")
                            .append(disconnectionInfo.reason())
                    );

                    java.lang.reflect.Field f = ReflectionUtil.findFieldByType(MinecraftClient.class, ClientPlayNetworkHandler.class);
                    if (f != null) {
                        f.setAccessible(true);
                        f.set(mc, null);
                    }

                    mc.setScreen((Screen) null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
