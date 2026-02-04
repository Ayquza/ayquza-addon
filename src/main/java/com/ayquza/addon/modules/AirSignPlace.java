package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AirSignPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRange = settings.createGroup("Range");
    private final SettingGroup sgSign = settings.createGroup("Sign");

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the block will be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> customRange = sgRange.add(new BoolSetting.Builder()
        .name("custom-range")
        .description("Use custom range for air place.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgRange.add(new DoubleSetting.Builder()
        .name("range")
        .description("Custom range to place at.")
        .visible(customRange::get)
        .defaultValue(5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> placeSign = sgSign.add(new BoolSetting.Builder()
        .name("place-sign")
        .description("Automatically places a sign on top of the placed block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> signDelay = sgSign.add(new IntSetting.Builder()
        .name("sign-delay")
        .description("Delay in ticks before placing the sign.")
        .visible(placeSign::get)
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoSwitchSign = sgSign.add(new BoolSetting.Builder()
        .name("auto-switch-sign")
        .description("Automatically switches to a sign in your hotbar.")
        .visible(placeSign::get)
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> switchBack = sgSign.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switches back to the original slot after placing the sign.")
        .visible(placeSign::get)
        .defaultValue(true)
        .build()
    );

    private HitResult hitResult;
    private BlockPos lastPlacedBlock = null;
    private int signPlaceTimer = 0;
    private int previousSlot = -1;
    private boolean needsSwitchBack = false;
    private boolean shouldCloseSignGUI = false;

    public AirSignPlace() {
        super(AyquzaAddon.CATEGORY, "air-sign-place", "Places a block where your crosshair is pointing at and optionally a sign on top.");
    }

    @Override
    public void onDeactivate() {
        lastPlacedBlock = null;
        signPlaceTimer = 0;
        previousSlot = -1;
        needsSwitchBack = false;
        shouldCloseSignGUI = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!InvUtils.testInHands(this::placeable)) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS) return;

        double r = customRange.get() ? range.get() : mc.player.getBlockInteractionRange();
        hitResult = mc.getCameraEntity().raycast(r, 0, false);

        if (needsSwitchBack && previousSlot != -1) {
            InvUtils.swap(previousSlot, false);
            previousSlot = -1;
            needsSwitchBack = false;
        }

        if (placeSign.get() && lastPlacedBlock != null && signPlaceTimer > 0) {
            signPlaceTimer--;
            if (signPlaceTimer == 0) {
                placeSignOnBlock(lastPlacedBlock);
                lastPlacedBlock = null;
            }
        }
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (!(hitResult instanceof BlockHitResult bhr) || !placeable(mc.player.getStackInHand(event.hand))) return;

        Block toPlace = Blocks.OBSIDIAN;
        Item i = mc.player.getStackInHand(event.hand).getItem();
        if (i instanceof BlockItem blockItem) toPlace = blockItem.getBlock();
        if (!BlockUtils.canPlaceBlock(bhr.getBlockPos(), (i instanceof ArmorStandItem || i instanceof BlockItem), toPlace)) return;

        Vec3d hitPos = Vec3d.ofCenter(bhr.getBlockPos());

        BlockHitResult b = new BlockHitResult(hitPos, mc.player.getMovementDirection().getOpposite(), bhr.getBlockPos(), false);
        BlockUtils.interact(b, event.hand, true);

        event.toReturn = ActionResult.SUCCESS;

        if (placeSign.get() && i instanceof BlockItem) {
            lastPlacedBlock = bhr.getBlockPos();
            signPlaceTimer = signDelay.get();
            if (switchBack.get()) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (shouldCloseSignGUI && event.screen instanceof SignEditScreen) {
            event.cancel();
            shouldCloseSignGUI = false;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!(hitResult instanceof BlockHitResult bhr)
            || (mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS)
            || !mc.world.getBlockState(bhr.getBlockPos()).isReplaceable()
            || !InvUtils.testInHands(this::placeable)
            || !render.get()) return;

        event.renderer.box(bhr.getBlockPos(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void placeSignOnBlock(BlockPos blockPos) {
        if (mc.world.getBlockState(blockPos).isAir()) return;

        BlockPos signPos = blockPos.up();

        if (!mc.world.getBlockState(signPos).isReplaceable()) return;

        FindItemResult signSlot;

        if (autoSwitchSign.get()) {
            signSlot = InvUtils.findInHotbar(
                Items.OAK_SIGN,
                Items.BIRCH_SIGN,
                Items.SPRUCE_SIGN,
                Items.JUNGLE_SIGN,
                Items.ACACIA_SIGN,
                Items.DARK_OAK_SIGN,
                Items.CRIMSON_SIGN,
                Items.WARPED_SIGN,
                Items.MANGROVE_SIGN,
                Items.CHERRY_SIGN,
                Items.BAMBOO_SIGN
            );
        } else {
            Item currentItem = mc.player.getMainHandStack().getItem();
            if (isSign(currentItem)) {
                signSlot = new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandStack().getCount());
            } else {
                return;
            }
        }

        if (!signSlot.found()) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        if (autoSwitchSign.get() && signSlot.slot() != currentSlot) {
            InvUtils.swap(signSlot.slot(), false);
        }

        Vec3d hitVec = Vec3d.ofCenter(blockPos).add(0, 0.5, 0);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, blockPos, false);

        shouldCloseSignGUI = true;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        if (switchBack.get() && previousSlot != -1 && autoSwitchSign.get()) {
            needsSwitchBack = true;
        }
    }

    private boolean isSign(Item item) {
        return item == Items.OAK_SIGN ||
            item == Items.BIRCH_SIGN ||
            item == Items.SPRUCE_SIGN ||
            item == Items.JUNGLE_SIGN ||
            item == Items.ACACIA_SIGN ||
            item == Items.DARK_OAK_SIGN ||
            item == Items.CRIMSON_SIGN ||
            item == Items.WARPED_SIGN ||
            item == Items.MANGROVE_SIGN ||
            item == Items.CHERRY_SIGN ||
            item == Items.BAMBOO_SIGN;
    }

    private boolean placeable(ItemStack stack) {
        Item i = stack.getItem();
        return i instanceof BlockItem || i instanceof SpawnEggItem || i instanceof FireworkRocketItem || i instanceof ArmorStandItem;
    }
}
