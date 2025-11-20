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
        .description("Monitor and refill all hotbar slots. If disabled, only refills the selected slot. (Only Inventory refill source)")
        .defaultValue(true)
        .build()
    );

    private final Setting<RefillSource> refillSource = sgGeneral.add(new EnumSetting.Builder<RefillSource>()
        .name("refill-source")
        .description("Where to take items from when refilling.")
        .defaultValue(RefillSource.InventoryOnly)
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
            if (!isStackableItem(hotbarStack)) continue;
            if (hotbarStack.getCount() >= threshold.get()) continue;

            int foundSlot = findRefillSlot(hotbarStack, hotbarSlot);

            if (foundSlot == -1) continue;

            InvUtils.move().from(foundSlot).to(hotbarSlot);
            return;
        }
    }

    private boolean isStackableItem(ItemStack stack) {
        int maxCount = stack.getMaxCount();
        return maxCount == 16 || maxCount == 64;
    }

    private int findRefillSlot(ItemStack targetStack, int excludeSlot) {
        RefillSource source = refillSource.get();

        if (allSlots.get()) {
            return searchRange(targetStack, excludeSlot, 9, 36);
        }

        if (source == RefillSource.Both) {
            int invSlot = searchRange(targetStack, excludeSlot, 9, 36);
            if (invSlot != -1) return invSlot;

            return searchRange(targetStack, excludeSlot, 0, 9);
        }

        if (source == RefillSource.InventoryOnly) {
            return searchRange(targetStack, excludeSlot, 9, 36);
        }

        return searchRange(targetStack, excludeSlot, 0, 9);
    }

    private int searchRange(ItemStack targetStack, int excludeSlot, int start, int end) {
        for (int slot = start; slot < end; slot++) {
            if (slot == excludeSlot) continue;

            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, targetStack)) {
                return slot;
            }
        }
        return -1;
    }

    public enum RefillSource {
        InventoryOnly,
        HotbarOnly,
        Both
    }
}
