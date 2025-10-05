package com.ayquza.addon.modules;


import com.ayquza.addon.AyquzaAddon;
import com.ayquza.addon.ReflectionUtil;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;


public class FakeDisconnect extends Module {
    public static FakeDisconnect INSTANCE;
    private final MinecraftClient mc = MinecraftClient.getInstance();


    public FakeDisconnect() {
        super(AyquzaAddon.CATEGORY,"FakeDisconnect", "Lets you stay inside the world after a disconnect instead of being kicked to the menu.");
        INSTANCE = this;
    }


    @Override
    public void onActivate() {
// Wenn das Modul aktiviert wird, tun wir nichts aktiv - die Mixin-Logik verhindert den Screen
        info("FakeDisconnect activated");
    }


    @Override
    public void onDeactivate() {
        info("FakeDisconnect deactivated");
    }



    public void removeNetworkHandlerAndKeepWorld() {
        mc.execute(() -> {
            try {
// Versuche das networkHandler-Feld zu finden und auf null zu setzen
                java.lang.reflect.Field f = ReflectionUtil.findFieldByType(MinecraftClient.class, ClientPlayNetworkHandler.class);
                if (f != null) {
                    f.setAccessible(true);
                    f.set(mc, null);
                } else {
// Fallback: versuche Feldnamen, die je nach Mapping variieren können
                    String[] names = new String[]{"networkHandler", "field_71456_v", "handler"};
                    for (String n : names) {
                        try {
                            java.lang.reflect.Field ff = MinecraftClient.class.getDeclaredField(n);
                            ff.setAccessible(true);
                            ff.set(mc, null);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                }


// Schließe jeden Screen, damit der Client in die Welt zurückkehrt
                mc.setScreen((net.minecraft.client.gui.screen.Screen) null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
