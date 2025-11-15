package com.ayquza.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import com.ayquza.addon.AyquzaAddon;

public class HotbarStackRefill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("Refill when stack count is below this number.")
        .defaultValue(32)
        .min(1)
        .max(64)
        .sliderMin(1)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between refills.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> allSlots = sgGeneral.add(new BoolSetting.Builder()
        .name("all-hotbar-slots")
        .description("Monitor and refill all hotbar slots. If disabled, only refills the selected slot.")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;

    public HotbarStackRefill() {
        super(AyquzaAddon.CATEGORY, "hotbar-stack-refill", "Automatically refills hotbar stacks from your inventory.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        int startSlot = allSlots.get() ? 0 : mc.player.getInventory().getSelectedSlot();
        int endSlot = allSlots.get() ? 9 : mc.player.getInventory().getSelectedSlot() + 1;

        for (int hotbarSlot = startSlot; hotbarSlot < endSlot; hotbarSlot++) {
            ItemStack hotbarStack = mc.player.getInventory().getStack(hotbarSlot);

            if (hotbarStack.isEmpty()) continue;
            if (hotbarStack.getCount() >= threshold.get()) continue;

            int foundSlot = -1;
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack invStack = mc.player.getInventory().getStack(invSlot);
                if (!invStack.isEmpty() && ItemStack.areItemsEqual(invStack, hotbarStack)) {
                    foundSlot = invSlot;
                    break;
                }
            }

            if (foundSlot == -1) continue;

            InvUtils.move().from(foundSlot).to(hotbarSlot);
            return;
        }
    }
}
