package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.Util;
import java.util.List;

public class AutoLavaCast extends Module {
    private final SettingGroup sgFourWay = settings.createGroup("AutoLavaCast+");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBuild = settings.createGroup("Build Options");
    private final SettingGroup sgTimings = settings.createGroup("Timings");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private boolean isUpdatingSettings = false;
    private static final int ESTIMATION_MSG_ID = "AutoLavaCast_estimation".hashCode();



    private final Setting<Boolean> fourWayEnabled = sgFourWay.add(new BoolSetting.Builder()
        .name("minimal-mode")
        .description("Builds in 4 cardinal directions.")
        .defaultValue(false)
        .onChanged(val -> {
            if (!val && !isUpdatingSettings) {
                isUpdatingSettings = true;
                // Nur auf false setzen, wenn sie nicht schon false sind
                Setting<Boolean> exp = (Setting<Boolean>) settings.get("expanded-mode");
                Setting<Boolean> full = (Setting<Boolean>) settings.get("full-connected-mode");
                if (exp.get()) exp.set(false);
                if (full.get()) full.set(false);
                isUpdatingSettings = false;
            }
            printEstimation();
        })
        .build()
    );

    private final Setting<Boolean> eightWay = sgFourWay.add(new BoolSetting.Builder()
        .name("expanded-mode")
        .description("Adds side wings to the paths.")
        .defaultValue(false)
        .onChanged(val -> {
            if (!isUpdatingSettings) {
                isUpdatingSettings = true;
                if (val) {
                    Setting<Boolean> min = (Setting<Boolean>) settings.get("minimal-mode");
                    if (!min.get()) min.set(true);
                } else {
                    Setting<Boolean> full = (Setting<Boolean>) settings.get("full-connected-mode");
                    if (full.get()) full.set(false);
                }
                isUpdatingSettings = false;
            }
            printEstimation();
        })
        .build()
    );

    private final Setting<Boolean> twelveWay = sgFourWay.add(new BoolSetting.Builder()
        .name("full-connected-mode")
        .description("Dynamically connects wings to close all gaps.")
        .defaultValue(false)
        .onChanged(val -> {
            if (val && !isUpdatingSettings) {
                isUpdatingSettings = true;
                Setting<Boolean> min = (Setting<Boolean>) settings.get("minimal-mode");
                Setting<Boolean> exp = (Setting<Boolean>) settings.get("expanded-mode");
                if (!min.get()) min.set(true);
                if (!exp.get()) exp.set(true);
                isUpdatingSettings = false;
            }
            printEstimation();
        })
        .build()
    );

    private final Setting<Integer> fourWayLength = sgFourWay.add(new IntSetting.Builder()
        .name("length")
        .description("Distance per direction.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .onChanged(val -> printEstimation())
        .build()
    );

    private final Setting<Integer> eightWayStages = sgFourWay.add(new IntSetting.Builder()
        .name("lavacast-stages")
        .description("Number of layers to build.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .onChanged(val -> printEstimation())
        .build()
    );

    private final Setting<Double> returnSpeed = sgFourWay.add(new DoubleSetting.Builder()
        .name("return-speed")
        .description("Flight speed during return.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 5.0)
        .build()
    );

    private final Setting<Boolean> previewButton = sgFourWay.add(new BoolSetting.Builder()
        .name("preview-guide-imgur")
        .description("Opens a visual guide in your browser.")
        .defaultValue(false)
        .onChanged(val -> {
            if (val) {
                Util.getOperatingSystem().open("https://imgur.com/a/nMBVwn9");
                mc.execute(() -> {
                    Setting<Boolean> s = (Setting<Boolean>) settings.get("preview-guide-imgur");
                    if (s != null) s.set(false);
                });
            }
        })
        .build()
    );

    private final Setting<List<Block>> skippableBlox = sgGeneral.add(new BlockListSetting.Builder()
        .name("Blocks to not use")
        .description("Do not use these blocks for mountains.")
        .build()
    );

    public final Setting<Boolean> mouseT = sgGeneral.add(new BoolSetting.Builder()
        .name("MouseTurn")
        .description("Changes building direction based on your looking direction")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> startPaused = sgGeneral.add(new BoolSetting.Builder()
        .name("Start Paused")
        .description("AutoMountain is Paused when module activated, for more control.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> spcoffset = sgBuild.add(new IntSetting.Builder()
        .name("OnDemandSpacing")
        .description("Amount of space in blocks between stairs when pressing jumpKey.")
        .defaultValue(1)
        .min(1)
        .max(2)
        .visible(() -> false)
        .build()
    );

    public final Setting<Double> StairTimer = sgTimings.add(new DoubleSetting.Builder()
        .name("TimerMultiplier")
        .description("The multiplier value for Timer.")
        .defaultValue(1)
        .sliderRange(0.1, 10)
        .build()
    );

    private final Setting<Integer> spd = sgTimings.add(new IntSetting.Builder()
        .name("PlacementTickDelay")
        .description("Delay block placement to slow down the builder.")
        .min(1)
        .sliderRange(1, 10)
        .defaultValue(1)
        .build()
    );

    private final Setting<Integer> munscher = sgTimings.add(new IntSetting.Builder()
        .name("DiagonalSwitchDelay")
        .description("Delays switching direction by this many ticks when building diagonally.")
        .min(1)
        .sliderRange(1, 10)
        .defaultValue(1)
        .visible(() -> false)
        .build()
    );

    public final Setting<Boolean> delayakick = sgTimings.add(new BoolSetting.Builder()
        .name("PauseBasedAntiKick")
        .description("Helps if you're flying, or sending too many packets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgTimings.add(new IntSetting.Builder()
        .name("PauseForThisAmountOfTicks")
        .description("The amount of delay in ticks, when pausing.")
        .min(1)
        .defaultValue(5)
        .sliderRange(0, 100)
        .visible(delayakick::get)
        .build()
    );

    private final Setting<Integer> offTime = sgTimings.add(new IntSetting.Builder()
        .name("TicksBetweenPause")
        .description("The amount of delay, in ticks, between pauses.")
        .min(1)
        .defaultValue(20)
        .sliderRange(1, 200)
        .visible(delayakick::get)
        .build()
    );

    private final Setting<Integer> limit = sgBuild.add(new IntSetting.Builder()
        .name("UpwardBuildLimit")
        .description("Sets the Y level at which the stairs stop going up")
        .sliderRange(-64, 318)
        .defaultValue(318)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> downlimit = sgBuild.add(new IntSetting.Builder()
        .name("DownwardBuildLimit")
        .description("Sets the Y level at which the stairs stop going down")
        .sliderRange(-64, 318)
        .visible(() -> false)
        .defaultValue(-64)
        .build()
    );

    public final Setting<Boolean> InvertUpDir = sgBuild.add(new BoolSetting.Builder()
        .name("InvertDir@UpwardLimitOrCeiling")
        .description("Inverts Direction from up to down.")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    public final Setting<Boolean> InvertDownDir = sgBuild.add(new BoolSetting.Builder()
        .name("InvertDir@DownwardLimitOrFloor")
        .description("Inverts Direction from down to up.")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> useHotbarRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-hotbarStackRefill-integration")
        .description("Automatically enables your HotbarStackRefill module.")
        .defaultValue(true)
        .onChanged(val -> {
            if (val && !Modules.get().get(HotbarStackRefill.class).isActive()) {
                Modules.get().get(HotbarStackRefill.class).toggle();
            }
        })
        .build()
    );

    public final Setting<Boolean> disabledisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("Disable On Disconnect")
        .description("Toggles the Module off when you disconnect.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> lagpause = sgTimings.add(new BoolSetting.Builder()
        .name("Pause if Server Lagging")
        .description("Pause Builder if server is lagging")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lag = sgTimings.add(new DoubleSetting.Builder()
        .name("How many seconds until pause")
        .description("Pause Builder if server is lagging for this many seconds.")
        .min(0)
        .sliderRange(0, 10)
        .defaultValue(1)
        .visible(lagpause::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the next stair will be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(255, 0, 255, 15))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> rendertopbottomblock = sgRender.add(new BoolSetting.Builder()
        .name("render highest/lowest block")
        .description("Renders a block overlay where the highest and lowest blocks are.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> topbottomsideColor = sgRender.add(new ColorSetting.Builder()
        .name("high/low-block-side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(255, 0, 255, 15, true))
        .visible(() -> render.get() && rendertopbottomblock.get())
        .build()
    );

    private final Setting<SettingColor> topbottomlineColor = sgRender.add(new ColorSetting.Builder()
        .name("high/low-block-line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(255, 0, 255, 255, true))
        .visible(() -> render.get() && rendertopbottomblock.get())
        .build()
    );

    public final Setting<Boolean> lowYrst = sgGeneral.add(new BoolSetting.Builder()
        .name("ResetLowestBlockOnACTIVATE")
        .description("UNCHECK for proper timings for AutoLavaCaster's UseLastLowestBlockfromAutoMountain.")
        .defaultValue(true)
        .build()
    );

    private boolean pause = false;
    private boolean resetTimer;
    private float timeSinceLastTick;
    private int delayLeft = delay.get();
    private int offLeft = offTime.get();
    private BlockPos playerPos;
    private BlockPos renderplayerPos;
    private int cookie = 0;
    private int speed = 0;
    private boolean go = true;
    private float cookieyaw;
    private boolean search = true;
    private boolean search2 = true;
    public static BlockPos lowestblock = new BlockPos(666, 666, 666);
    public static BlockPos highestblock = new BlockPos(666, 666, 666);
    public static int groundY;
    public static int groundY2;
    private int lowblockY = -1;
    private int highblockY = -1;
    public static boolean isthisfirstblock;
    private Direction wasfacing;
    private int prevPitch;
    private boolean justSwapped = false;
    private int graceTicks = 0;
    private int lastHotbarSlot = -1;
    private int eightWaySubStage = 0;
    private int twelveWaySubStage = 0;
    private BlockPos axisPoint = null;
    private int stuckTicks = 0;
    private BlockPos lastTickPos = null;
    private int currentEightWayDepth = 0;
    private BlockPos segmentStartPos = null;
    private BlockPos lastLeftWingEnd = null;
    private BlockPos lastRightWingEnd = null;
    private boolean isConnecting = false;
    private BlockPos currentWingEnd = null;
    private BlockPos fourWayStartPos = null;
    private int fourWayStage = 0;
    private boolean isReturning = false;
    private boolean fourWayBuildDown = true;
    private final Direction[] buildDirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};


    private long startTime;
    private int blocksPlaced;
    private final java.util.Set<BlockPos> placedPositions = new java.util.HashSet<>();

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton rstlowblock = table.add(theme.button("Reset Lowest/Highest Block")).expandX().minWidth(100).widget();
        rstlowblock.action = () -> {
            lowestblock = new BlockPos(666, 666, 666);
            highestblock = new BlockPos(666, 666, 666);
            isthisfirstblock = true;
        };
        table.row();
        return table;
    }

    public AutoLavaCast() {
        super(AyquzaAddon.CATEGORY, "AutoLavaCast+", "Make Mountains!");
    }

    private int calculateEstimatedBlocks() {
        int len = fourWayLength.get();
        int stages = eightWayStages.get();
        if (twelveWay.get()) {
            return 4 * len + 8 * len * (stages * (stages + 1)) / 2;
        } else if (eightWay.get()) {
            return 4 * len + 8 * stages * len;
        } else if (fourWayEnabled.get()) {
            return 4 * len;
        }
        return 0;
    }

    private void printEstimation() {
        if (mc.player == null || isUpdatingSettings) return;

        int required = calculateEstimatedBlocks();

        int invCount = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                invCount += stack.getCount();
            }
        }

        int missing = Math.max(0, required - invCount);
        double missingStacks = Math.ceil((missing / 64.0) * 10) / 10.0;

        ChatUtils.sendMsg(ESTIMATION_MSG_ID, "AutoLavaCast+", Formatting.GRAY,
            Text.literal(String.format("§fReq: §b%d §7| §fInv: §e%d §7| §fMissing: §c%d §7(§6~%.1f Stacks§7)",
                required, invCount, missing, missingStacks)));
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen && disabledisconnect.get()) toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disabledisconnect.get()) toggle();
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        placedPositions.clear();
        printEstimation();
        blocksPlaced = 0;


        lastHotbarSlot = mc.player.getInventory().getSelectedSlot();
        if (lowYrst.get()) isthisfirstblock = true;
        if (useHotbarRefill.get() && !Modules.get().get(HotbarStackRefill.class).isActive()) {
            Modules.get().get(HotbarStackRefill.class).toggle();
        }
        groundY = 0;
        groundY2 = 0;
        lowblockY = -1;
        highblockY = -1;
        fourWayStartPos = null;
        fourWayStage = 0;
        isReturning = false;
        resetTMode();

        if (startPaused.get()) {
            pause = false;
            ChatUtils.sendMsg(Text.of("Press UseKey (RightClick) to Build Stairs!"));
        } else if (!startPaused.get()) {
            mc.player.setPos(mc.player.getX(), Math.ceil(mc.player.getY()), mc.player.getZ());
            wasfacing = mc.player.getHorizontalFacing();
            prevPitch = Math.round(mc.player.getPitch());

            mc.player.setVelocity(0, 0, 0);
            PlayerUtils.centerPlayer();
            pause = true;
        }
        resetTimer = false;
        playerPos = mc.player.getBlockPos();
        renderplayerPos = mc.player.getBlockPos();
        if (startPaused.get() || isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack())) return;
        BlockPos pos = playerPos.add(new Vec3i(0, -1, 0));
        if (mc.world.getBlockState(pos).isReplaceable()) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            if (placedPositions.add(pos)) blocksPlaced++;
        }
    }

    @Override
    public void onDeactivate() {
        resetTMode();
        if (mc.player == null) return;
        mc.player.setNoGravity(false);

        if (isthisfirstblock) {
            highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
            lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
            isthisfirstblock = false;
        }
        if (!startPaused.get() && pause) {
            if (!isthisfirstblock && mc.player.getY() < lowestblock.getY()) lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
            if (!isthisfirstblock && mc.player.getY() > highestblock.getY() + 1) highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
        }
        search = true;
        seekground();
        search2 = true;
        seekground2();
        speed = 0;
        resetTimer = true;
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        if (isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack())) return;
        if (!startPaused.get() && !pause) {
            BlockPos pos = playerPos.add(new Vec3i(0, -1, 0));
            if (mc.world.getBlockState(pos).isReplaceable()) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                if (placedPositions.add(pos)) blocksPlaced++;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get() && mc.player != null) {
            if (mc.options.jumpKey.isPressed()) {
                if ((mouseT.get() && mc.player.getPitch() <= 40) || (!mouseT.get() && prevPitch <= 40)) {
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.NORTH) || (!mouseT.get() && wasfacing == Direction.NORTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, +spcoffset.get(), -1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.SOUTH) || (!mouseT.get() && wasfacing == Direction.SOUTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, +spcoffset.get(), 1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.EAST) || (!mouseT.get() && wasfacing == Direction.EAST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(1, +spcoffset.get(), 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.WEST) || (!mouseT.get() && wasfacing == Direction.WEST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(-1, +spcoffset.get(), 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                } else if ((mouseT.get() && mc.player.getPitch() > 40) || (!mouseT.get() && prevPitch > 40)) {
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.NORTH) || (!mouseT.get() && wasfacing == Direction.NORTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, -spcoffset.get() - 2, -1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.SOUTH) || (!mouseT.get() && wasfacing == Direction.SOUTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, -spcoffset.get() - 2, 1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.EAST) || (!mouseT.get() && wasfacing == Direction.EAST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(1, -spcoffset.get() - 2, 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.WEST) || (!mouseT.get() && wasfacing == Direction.WEST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(-1, -spcoffset.get() - 2, 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                }
            } else if (!mc.options.jumpKey.isPressed()) {
                if ((mouseT.get() && mc.player.getPitch() <= 40) || (!mouseT.get() && prevPitch <= 40)) {
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.NORTH) || (!mouseT.get() && wasfacing == Direction.NORTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, 0, -1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.SOUTH) || (!mouseT.get() && wasfacing == Direction.SOUTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, 0, 1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.EAST) || (!mouseT.get() && wasfacing == Direction.EAST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(1, 0, 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);

                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.WEST) || (!mouseT.get() && wasfacing == Direction.WEST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(-1, 0, -0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);

                    }
                } else if ((mouseT.get() && mc.player.getPitch() > 40) || (!mouseT.get() && prevPitch > 40)) {
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.NORTH) || (!mouseT.get() && wasfacing == Direction.NORTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, -2, -1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.SOUTH) || (!mouseT.get() && wasfacing == Direction.SOUTH)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(0, -2, 1));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.EAST) || (!mouseT.get() && wasfacing == Direction.EAST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(1, -2, 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                    if ((mouseT.get() && mc.player.getMovementDirection() == Direction.WEST) || (!mouseT.get() && wasfacing == Direction.WEST)) {
                        BlockPos pos1 = renderplayerPos.add(new Vec3i(-1, -2, 0));
                        event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                }
            }
            if (rendertopbottomblock.get()) {
                if (highestblock != new BlockPos(666, 666, 666)) {
                    event.renderer.box(highestblock, topbottomsideColor.get(), topbottomlineColor.get(), shapeMode.get(), 0);
                }
                if (lowestblock != new BlockPos(666, 666, 666)) {
                    event.renderer.box(lowestblock, topbottomsideColor.get(), topbottomlineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (mc.options.useKey.isPressed()) {
            if (pause) {
                BlockPos pos = playerPos.add(new Vec3i(0, -1, 0));
                if (mc.world.getBlockState(pos).isReplaceable()) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                    mc.player.swingHand(Hand.MAIN_HAND);
                    if (placedPositions.add(pos)) blocksPlaced++;
                }
            }
            if (!pause) mc.player.setPos(mc.player.getX(), Math.ceil(mc.player.getY()), mc.player.getZ());

            pause = !pause;

            if (pause && fourWayEnabled.get() && fourWayStartPos == null) {
                fourWayStartPos = mc.player.getBlockPos();
                fourWayBuildDown = mc.player.getPitch() > 40;
                fourWayStage = 0;
                isReturning = false;
            }

            mc.player.setVelocity(0, 0, 0);
            cookie = 0;
            speed = 0;
            resetTimer = true;
            Modules.get().get(Timer.class).setOverride(Timer.OFF);
            if (isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack())) return;
            if (isthisfirstblock) {
                highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                isthisfirstblock = false;
            }
            if (!isthisfirstblock && mc.player.getY() < lowestblock.getY()) {
                lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                seekground();
            }
            if (!isthisfirstblock && mc.player.getY() > highestblock.getY() + 1) {
                highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                seekground2();
            }
        }
    }

    @EventHandler
    private void onKeyEvent(KeyEvent event) {
        if (!pause) return;
        if (mc.options.forwardKey.isPressed()) {
            if (mouseT.get()) mc.player.setPitch(35);
            if (!mouseT.get()) prevPitch = 35;
        }
        if (mc.options.backKey.isPressed()) {
            if (mouseT.get()) mc.player.setPitch(75);
            if (!mouseT.get()) prevPitch = 75;
        }
        if ((lagpause.get() && timeSinceLastTick >= lag.get()) || isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack()) || !pause) return;
        if (mc.options.leftKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() - 90);
            if (!mouseT.get()) {
                if (wasfacing == Direction.NORTH) {
                    wasfacing = Direction.WEST;
                    return;
                }
                if (wasfacing == Direction.SOUTH) {
                    wasfacing = Direction.EAST;
                    return;
                }
                if (wasfacing == Direction.WEST) {
                    wasfacing = Direction.SOUTH;
                    return;
                }
                if (wasfacing == Direction.EAST) {
                    wasfacing = Direction.NORTH;
                    return;
                }
            }
        }
        if (mc.options.rightKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() + 90);
            if (!mouseT.get()) {
                if (wasfacing == Direction.NORTH) {
                    wasfacing = Direction.EAST;
                    return;
                }
                if (wasfacing == Direction.SOUTH) {
                    wasfacing = Direction.WEST;
                    return;
                }
                if (wasfacing == Direction.WEST) {
                    wasfacing = Direction.NORTH;
                    return;
                }
                if (wasfacing == Direction.EAST) {
                    wasfacing = Direction.SOUTH;
                }
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket)
            ((PlayerMoveC2SPacketAccessor) event.packet).meteor$setOnGround(true);
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        playerPos = mc.player.getBlockPos();
        if (mc.player.getY() % 1 != 0 && !pause) {
            renderplayerPos = new BlockPos(mc.player.getBlockX(), mc.player.getBlockY() + 1, mc.player.getBlockZ());
        } else renderplayerPos = mc.player.getBlockPos();
        timeSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();

        if (speed < spd.get()) {
            go = false;
            speed++;
        } else {
            speed = 0;
        }

        if (justSwapped) {
            graceTicks--;
            if (graceTicks > 0) {
                go = false;
                speed = 0;
                mc.player.setVelocity(0, 0, 0);
                PlayerUtils.centerPlayer();
                mc.player.setPos(mc.player.getX(), Math.round(mc.player.getY()) + 0.25, mc.player.getZ());
                return;
            } else {
                justSwapped = false;
            }
        }

        if (speed >= spd.get()) {
            go = true;
        }

        if (!pause) {
            wasfacing = mc.player.getHorizontalFacing();
            prevPitch = Math.round(mc.player.getPitch());
            mc.player.setNoGravity(false);
            search = true;
            search2 = true;
        }
        if (!pause) return;
        if (!delayakick.get()) {
            offLeft = 666666666;
            delayLeft = 0;
        } else if (delayakick.get() && offLeft > offTime.get()) {
            offLeft = offTime.get();
        }

        if (!isReturning) {
            mc.player.setVelocity(0, 0, 0);
            PlayerUtils.centerPlayer();
            mc.player.setPos(mc.player.getX(), Math.round(mc.player.getY()) + 0.25, mc.player.getZ());
        }

        if (Modules.get().get(Flight.class).isActive()) {
            Modules.get().get(Flight.class).toggle();
        }

        if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.AIR) {
            resetTimer = false;
            Modules.get().get(Timer.class).setOverride(StairTimer.get());
        } else if (!resetTimer) {
            resetTimer = true;
            Modules.get().get(Timer.class).setOverride(Timer.OFF);
        }
        if ((lagpause.get() && timeSinceLastTick >= lag.get()) || isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack()) || !pause || !go) return;
        if (mc.options.sneakKey.isPressed() && mc.options.rightKey.isPressed() && delayLeft <= 0 && offLeft > 0) {
            cookie++;
            if (cookie == munscher.get()) {
                cookieyaw = mc.player.getYaw();
                if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() + 90);
                if (!mouseT.get()) {
                    if (wasfacing == Direction.NORTH) {
                        wasfacing = Direction.EAST;
                    } else if (wasfacing == Direction.SOUTH) {
                        wasfacing = Direction.WEST;
                    } else if (wasfacing == Direction.WEST) {
                        wasfacing = Direction.NORTH;
                    } else if (wasfacing == Direction.EAST) {
                        wasfacing = Direction.SOUTH;
                    }
                }
            } else if (cookie >= munscher.get() + munscher.get()) {
                if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() - 90);
                if (!mouseT.get()) {
                    if (wasfacing == Direction.NORTH) {
                        wasfacing = Direction.WEST;
                    } else if (wasfacing == Direction.SOUTH) {
                        wasfacing = Direction.EAST;
                    } else if (wasfacing == Direction.WEST) {
                        wasfacing = Direction.SOUTH;
                    } else if (wasfacing == Direction.EAST) {
                        wasfacing = Direction.NORTH;
                    }
                }
                cookie = 0;
            }
        }
        if (mc.options.sneakKey.isPressed() && mc.options.leftKey.isPressed() && delayLeft <= 0 && offLeft > 0) {
            cookie++;
            if (cookie == munscher.get()) {
                cookieyaw = mc.player.getYaw();
                if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() - 90);
                if (!mouseT.get()) {
                    if (wasfacing == Direction.NORTH) {
                        wasfacing = Direction.WEST;
                    } else if (wasfacing == Direction.SOUTH) {
                        wasfacing = Direction.EAST;
                    } else if (wasfacing == Direction.WEST) {
                        wasfacing = Direction.SOUTH;
                    } else if (wasfacing == Direction.EAST) {
                        wasfacing = Direction.NORTH;
                    }
                }
            } else if (cookie >= munscher.get() + munscher.get()) {
                if (mouseT.get()) mc.player.setYaw(mc.player.getYaw() + 90);
                if (!mouseT.get()) {
                    if (wasfacing == Direction.NORTH) {
                        wasfacing = Direction.EAST;
                    } else if (wasfacing == Direction.SOUTH) {
                        wasfacing = Direction.WEST;
                    } else if (wasfacing == Direction.WEST) {
                        wasfacing = Direction.NORTH;
                    } else if (wasfacing == Direction.EAST) {
                        wasfacing = Direction.SOUTH;
                    }
                }
                cookie = 0;
            }
        } else if (!mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed() && cookie >= 1) {
            mc.player.setYaw(cookieyaw);
            cookieyaw = mc.player.getYaw();
            cookie = 0;
        }
        if (pause) {
            if (isthisfirstblock) {
                highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                isthisfirstblock = false;
            }
            if (!isthisfirstblock && mc.player.getY() < lowestblock.getY()) {
                lowestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                seekground();
            }
            if (!isthisfirstblock && mc.player.getY() > highestblock.getY() + 1) {
                highestblock = mc.player.getBlockPos().add(new Vec3i(0, -1, 0));
                seekground2();
            }
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (!pause) return;

        boolean useMouseT = mouseT.get();
        int currentPitch = Math.round(mc.player.getPitch());
        Direction currentMoveDir = mc.player.getMovementDirection();

        if (fourWayEnabled.get() && pause) {
            if (fourWayStartPos == null) {
                fourWayStartPos = mc.player.getBlockPos();
                segmentStartPos = fourWayStartPos;
                fourWayStage = 0;
                eightWaySubStage = 0;
                currentEightWayDepth = 0;
                isReturning = false;
            }

            BlockPos currentPos = mc.player.getBlockPos();
            if (!isReturning && lastTickPos != null && currentPos.equals(lastTickPos)) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastTickPos = currentPos;

            if (fourWayStage > 3) {
                long duration = System.currentTimeMillis() - startTime;
                double seconds = duration / 1000.0;
                int estimated = calculateEstimatedBlocks();
                ChatUtils.info(Formatting.GREEN + "Build Finished! " + Formatting.GRAY + "Time: " + String.format("%.1f", seconds) + "s | Placed: " + blocksPlaced + " / " + estimated + " blocks.");
                toggle();
                return;
            }

            if (isReturning) {
                if (!mc.player.getAbilities().flying) mc.player.getAbilities().flying = true;

                boolean hasNextDepth = (currentEightWayDepth + 1 < eightWayStages.get());
                Vec3d target;

                if (eightWay.get() || twelveWay.get()) {
                    if (eightWaySubStage == 2) {
                        target = Vec3d.ofBottomCenter(axisPoint).add(0, 0.25, 0);
                    } else {
                        if (hasNextDepth) target = Vec3d.ofBottomCenter(axisPoint).add(0, 0.25, 0);
                        else target = Vec3d.ofBottomCenter(fourWayStartPos).add(0, 0.25, 0);
                    }
                } else {
                    target = Vec3d.ofBottomCenter(fourWayStartPos).add(0, 0.25, 0);
                }

                double pX = mc.player.getX();
                double pY = mc.player.getY();
                double pZ = mc.player.getZ();
                Vec3d playerPosVec = new Vec3d(pX, pY, pZ);

                double dist = playerPosVec.distanceTo(target);
                if (dist < (returnSpeed.get() + 0.1)) {
                    isReturning = false;
                    mc.player.setVelocity(0, 0, 0);
                    mc.player.setPosition(target.x, target.y, target.z);

                    if (!mc.player.getAbilities().creativeMode) mc.player.getAbilities().flying = false;

                    if (eightWay.get() || twelveWay.get()) {
                        if (eightWaySubStage == 2) {
                            eightWaySubStage = 3;
                        } else if (eightWaySubStage == 4) {
                            if (hasNextDepth) {
                                currentEightWayDepth++;
                                eightWaySubStage = 0;
                                segmentStartPos = axisPoint;
                            } else {
                                currentEightWayDepth = 0;
                                eightWaySubStage = 0;
                                fourWayStage++;
                                segmentStartPos = fourWayStartPos;
                            }
                        }
                    } else {
                        fourWayStage++;
                    }
                } else {
                    Vec3d dir = target.subtract(playerPosVec).normalize();
                    mc.player.setVelocity(dir.x * returnSpeed.get(), dir.y * returnSpeed.get(), dir.z * returnSpeed.get());
                }
                return;
            }

            Direction mainDir = buildDirs[fourWayStage];
            Direction moveDir = mainDir;
            BlockPos currentOrigin = (segmentStartPos != null) ? segmentStartPos : fourWayStartPos;

            if (eightWay.get() || twelveWay.get()) {
                if (eightWaySubStage == 1 || eightWaySubStage == 2) {
                    moveDir = mainDir.rotateYCounterclockwise();
                    currentOrigin = axisPoint;
                } else if (eightWaySubStage == 3 || eightWaySubStage == 4) {
                    moveDir = mainDir.rotateYClockwise();
                    currentOrigin = axisPoint;
                }
            }

            int distMoved = Math.max(
                Math.abs(mc.player.getBlockX() - currentOrigin.getX()),
                Math.abs(mc.player.getBlockZ() - currentOrigin.getZ())
            );

            int targetLen = fourWayLength.get();

            if (twelveWay.get() && (eightWaySubStage == 1 || eightWaySubStage == 3)) {
                if (currentEightWayDepth > 0) {
                    targetLen = (currentEightWayDepth + 1) * fourWayLength.get();
                }
            }

            if (distMoved >= targetLen || (stuckTicks > 10)) {
                stuckTicks = 0;
                if (eightWay.get() || twelveWay.get()) {
                    if (eightWaySubStage == 0) {
                        axisPoint = mc.player.getBlockPos();
                        eightWaySubStage = 1;
                    } else if (eightWaySubStage == 1) {
                        eightWaySubStage = 2;
                        isReturning = true;
                    } else if (eightWaySubStage == 3) {
                        eightWaySubStage = 4;
                        isReturning = true;
                    }
                } else {
                    isReturning = true;
                }
                return;
            }

            useMouseT = false;
            currentMoveDir = moveDir;
            wasfacing = moveDir;
            prevPitch = fourWayBuildDown ? 75 : 35;
        }

        if ((useMouseT && currentPitch <= 40) || (!useMouseT && prevPitch <= 40)) {
            if (delayLeft > 0) delayLeft--;
            else if ((!lagpause.get() || timeSinceLastTick < lag.get()) && delayLeft <= 0 && offLeft > 0 && mc.player.getY() <= limit.get() && mc.player.getY() >= downlimit.get()) {
                offLeft--;
                if (mc.player == null || mc.world == null) { toggle(); return; }
                if ((lagpause.get() && timeSinceLastTick >= lag.get()) || isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack()) || !pause || !go) return;

                if ((useMouseT && currentMoveDir == Direction.NORTH) || (!useMouseT && wasfacing == Direction.NORTH)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos un1 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, 0));
                        BlockPos un2 = playerPos.add(new Vec3i(0, spcoffset.get() + 1, -1));
                        BlockPos un3 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, -1));
                        BlockPos un4 = playerPos.add(new Vec3i(0, spcoffset.get() + 3, -1));
                        BlockPos pos = playerPos.add(new Vec3i(0, spcoffset.get(), -1));
                        if (mc.world.getBlockState(un1).isReplaceable() && mc.world.getBlockState(un2).isReplaceable() && mc.world.getBlockState(un3).isReplaceable() && mc.world.getBlockState(un4).isReplaceable() && mc.world.getFluidState(un1).isEmpty() && mc.world.getFluidState(un2).isEmpty() && mc.world.getFluidState(un3).isEmpty() && mc.world.getFluidState(un4).isEmpty() && !mc.world.getBlockState(un1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(un2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1 + spcoffset.get(), mc.player.getZ() - 1);
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    } else {
                        BlockPos un1 = playerPos.add(new Vec3i(0, 2, 0));
                        BlockPos un2 = playerPos.add(new Vec3i(0, 1, -1));
                        BlockPos un3 = playerPos.add(new Vec3i(0, 2, -1));
                        BlockPos un4 = playerPos.add(new Vec3i(0, 3, -1));
                        BlockPos pos = playerPos.add(new Vec3i(0, 0, -1));
                        if (mc.world.getBlockState(un1).isReplaceable() && mc.world.getBlockState(un2).isReplaceable() && mc.world.getBlockState(un3).isReplaceable() && mc.world.getBlockState(un4).isReplaceable() && mc.world.getFluidState(un1).isEmpty() && mc.world.getFluidState(un2).isEmpty() && mc.world.getFluidState(un3).isEmpty() && mc.world.getFluidState(un4).isEmpty() && !mc.world.getBlockState(un1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(un4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(un2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ() - 1);
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.EAST) || (!useMouseT && wasfacing == Direction.EAST)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos ue1 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, 0));
                        BlockPos ue2 = playerPos.add(new Vec3i(+1, spcoffset.get() + 1, 0));
                        BlockPos ue3 = playerPos.add(new Vec3i(+1, spcoffset.get() + 2, 0));
                        BlockPos ue4 = playerPos.add(new Vec3i(+1, spcoffset.get() + 3, 0));
                        BlockPos pos = playerPos.add(new Vec3i(1, spcoffset.get(), 0));
                        if (mc.world.getBlockState(ue1).isReplaceable() && mc.world.getBlockState(ue2).isReplaceable() && mc.world.getBlockState(ue3).isReplaceable() && mc.world.getBlockState(ue4).isReplaceable() && mc.world.getFluidState(ue1).isEmpty() && mc.world.getFluidState(ue2).isEmpty() && mc.world.getFluidState(ue3).isEmpty() && mc.world.getFluidState(ue4).isEmpty() && !mc.world.getBlockState(ue1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(ue2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() + 1, mc.player.getY() + 1 + spcoffset.get(), mc.player.getZ());
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    } else {
                        BlockPos ue1 = playerPos.add(new Vec3i(0, 2, 0));
                        BlockPos ue2 = playerPos.add(new Vec3i(+1, 1, 0));
                        BlockPos ue3 = playerPos.add(new Vec3i(+1, 2, 0));
                        BlockPos ue4 = playerPos.add(new Vec3i(+1, 3, 0));
                        BlockPos pos = playerPos.add(new Vec3i(1, 0, 0));
                        if (mc.world.getBlockState(ue1).isReplaceable() && mc.world.getBlockState(ue2).isReplaceable() && mc.world.getBlockState(ue3).isReplaceable() && mc.world.getBlockState(ue4).isReplaceable() && mc.world.getFluidState(ue1).isEmpty() && mc.world.getFluidState(ue2).isEmpty() && mc.world.getFluidState(ue3).isEmpty() && mc.world.getFluidState(ue4).isEmpty() && !mc.world.getBlockState(ue1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ue4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(ue2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() + 1, mc.player.getY() + 1, mc.player.getZ());
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.SOUTH) || (!useMouseT && wasfacing == Direction.SOUTH)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos us1 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, 0));
                        BlockPos us2 = playerPos.add(new Vec3i(0, spcoffset.get() + 1, +1));
                        BlockPos us3 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, +1));
                        BlockPos us4 = playerPos.add(new Vec3i(0, spcoffset.get() + 3, +1));
                        BlockPos pos = playerPos.add(new Vec3i(0, spcoffset.get(), 1));
                        if (mc.world.getBlockState(us1).isReplaceable() && mc.world.getBlockState(us2).isReplaceable() && mc.world.getBlockState(us3).isReplaceable() && mc.world.getBlockState(us4).isReplaceable() && mc.world.getFluidState(us1).isEmpty() && mc.world.getFluidState(us2).isEmpty() && mc.world.getFluidState(us3).isEmpty() && mc.world.getFluidState(us4).isEmpty() && !mc.world.getBlockState(us1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(us2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1 + spcoffset.get(), mc.player.getZ() + 1);
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    } else {
                        BlockPos us1 = playerPos.add(new Vec3i(0, 2, 0));
                        BlockPos us2 = playerPos.add(new Vec3i(0, 1, +1));
                        BlockPos us3 = playerPos.add(new Vec3i(0, 2, +1));
                        BlockPos us4 = playerPos.add(new Vec3i(0, 3, +1));
                        BlockPos pos = playerPos.add(new Vec3i(0, 0, 1));
                        if (mc.world.getBlockState(us1).isReplaceable() && mc.world.getBlockState(us2).isReplaceable() && mc.world.getBlockState(us3).isReplaceable() && mc.world.getBlockState(us4).isReplaceable() && mc.world.getFluidState(us1).isEmpty() && mc.world.getFluidState(us2).isEmpty() && mc.world.getFluidState(us3).isEmpty() && mc.world.getFluidState(us4).isEmpty() && !mc.world.getBlockState(us1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(us4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(us2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ() + 1);
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.WEST) || (!useMouseT && wasfacing == Direction.WEST)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos uw1 = playerPos.add(new Vec3i(0, spcoffset.get() + 2, 0));
                        BlockPos uw2 = playerPos.add(new Vec3i(-1, spcoffset.get() + 1, 0));
                        BlockPos uw3 = playerPos.add(new Vec3i(-1, spcoffset.get() + 2, 0));
                        BlockPos uw4 = playerPos.add(new Vec3i(-1, spcoffset.get() + 3, 0));
                        BlockPos pos = playerPos.add(new Vec3i(-1, spcoffset.get(), 0));
                        if (mc.world.getBlockState(uw1).isReplaceable() && mc.world.getBlockState(uw2).isReplaceable() && mc.world.getBlockState(uw3).isReplaceable() && mc.world.getBlockState(uw4).isReplaceable() && mc.world.getFluidState(uw1).isEmpty() && mc.world.getFluidState(uw2).isEmpty() && mc.world.getFluidState(uw3).isEmpty() && mc.world.getFluidState(uw4).isEmpty() && !mc.world.getBlockState(uw1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(uw2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() - 1, mc.player.getY() + 1 + spcoffset.get(), mc.player.getZ());
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    } else {
                        BlockPos uw1 = playerPos.add(new Vec3i(0, 2, 0));
                        BlockPos uw2 = playerPos.add(new Vec3i(-1, 1, 0));
                        BlockPos uw3 = playerPos.add(new Vec3i(-1, 2, 0));
                        BlockPos uw4 = playerPos.add(new Vec3i(-1, 3, 0));
                        BlockPos pos = playerPos.add(new Vec3i(-1, 0, 0));
                        if (mc.world.getBlockState(uw1).isReplaceable() && mc.world.getBlockState(uw2).isReplaceable() && mc.world.getBlockState(uw3).isReplaceable() && mc.world.getBlockState(uw4).isReplaceable() && mc.world.getFluidState(uw1).isEmpty() && mc.world.getFluidState(uw2).isEmpty() && mc.world.getFluidState(uw3).isEmpty() && mc.world.getFluidState(uw4).isEmpty() && !mc.world.getBlockState(uw1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw3).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(uw4).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(uw2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() - 1, mc.player.getY() + 1, mc.player.getZ());
                        } else {
                            if (InvertUpDir.get()) {
                                if (useMouseT) mc.player.setPitch(75);
                                if (!useMouseT) prevPitch = 75;
                            }
                        }
                    }
                }
                if (mc.player.getY() >= limit.get() - 1 && InvertUpDir.get()) {
                    if (useMouseT) mc.player.setPitch(75);
                    if (!useMouseT) prevPitch = 75;
                }
            } else if (mc.player.getY() <= downlimit.get() && !InvertDownDir.get() || mc.player.getY() >= limit.get() && !InvertUpDir.get() || delayLeft <= 0 && offLeft <= 0) {
                delayLeft = delay.get();
                offLeft = offTime.get();
            }
        } else if ((useMouseT && currentPitch > 40) || (!useMouseT && prevPitch > 40)) {
            if (delayLeft > 0) delayLeft--;
            else if ((!lagpause.get() || timeSinceLastTick < lag.get()) && delayLeft <= 0 && offLeft > 0 && mc.player.getY() <= limit.get() && mc.player.getY() >= downlimit.get()) {
                offLeft--;
                if (mc.player == null || mc.world == null) { toggle(); return; }
                if ((lagpause.get() && timeSinceLastTick >= lag.get()) || isInvalidBlock(mc.player.getMainHandStack().getItem().getDefaultStack()) || !pause || !go) return;

                if ((useMouseT && currentMoveDir == Direction.NORTH) || (!useMouseT && wasfacing == Direction.NORTH)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos dn1 = playerPos.add(new Vec3i(0, -spcoffset.get() - 1, -1));
                        BlockPos dn2 = playerPos.add(new Vec3i(0, -spcoffset.get(), -1));
                        BlockPos dn3 = playerPos.add(new Vec3i(0, -spcoffset.get() + 1, -1));
                        BlockPos pos = playerPos.add(new Vec3i(0, -spcoffset.get() - 2, -1));
                        if (mc.world.getBlockState(dn1).isReplaceable() && mc.world.getBlockState(dn2).isReplaceable() && mc.world.getBlockState(dn3).isReplaceable() && mc.world.getFluidState(dn1).isEmpty() && mc.world.getFluidState(dn2).isEmpty() && mc.world.getFluidState(dn3).isEmpty() && !mc.world.getBlockState(dn1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dn2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dn3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(dn2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() - 1 - spcoffset.get(), mc.player.getZ() - 1);
                        } else { if (InvertDownDir.get()) mc.player.setPitch(35); }
                    } else {
                        BlockPos dn1 = playerPos.add(new Vec3i(0, -1, -1));
                        BlockPos dn2 = playerPos.add(new Vec3i(0, 0, -1));
                        BlockPos dn3 = playerPos.add(new Vec3i(0, 1, -1));
                        BlockPos pos = playerPos.add(new Vec3i(0, -2, -1));
                        if (mc.world.getBlockState(dn1).isReplaceable() && mc.world.getBlockState(dn2).isReplaceable() && mc.world.getBlockState(dn3).isReplaceable() && mc.world.getFluidState(dn1).isEmpty() && mc.world.getFluidState(dn2).isEmpty() && mc.world.getFluidState(dn3).isEmpty() && !mc.world.getBlockState(dn1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dn2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dn3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(dn2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ() - 1);
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.EAST) || (!useMouseT && wasfacing == Direction.EAST)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos de1 = playerPos.add(new Vec3i(1, -spcoffset.get() - 1, 0));
                        BlockPos de2 = playerPos.add(new Vec3i(1, -spcoffset.get(), 0));
                        BlockPos de3 = playerPos.add(new Vec3i(1, -spcoffset.get() + 1, 0));
                        BlockPos pos = playerPos.add(new Vec3i(1, -spcoffset.get() - 2, 0));
                        if (mc.world.getBlockState(de1).isReplaceable() && mc.world.getBlockState(de2).isReplaceable() && mc.world.getBlockState(de3).isReplaceable() && mc.world.getFluidState(de1).isEmpty() && mc.world.getFluidState(de2).isEmpty() && mc.world.getFluidState(de3).isEmpty() && !mc.world.getBlockState(de1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(de2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(de3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(de2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() + 1, mc.player.getY() - 1 - spcoffset.get(), mc.player.getZ());
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    } else {
                        BlockPos de1 = playerPos.add(new Vec3i(1, -1, 0));
                        BlockPos de2 = playerPos.add(new Vec3i(1, 0, 0));
                        BlockPos de3 = playerPos.add(new Vec3i(1, 1, 0));
                        BlockPos pos = playerPos.add(new Vec3i(1, -2, 0));
                        if (mc.world.getBlockState(de1).isReplaceable() && mc.world.getBlockState(de2).isReplaceable() && mc.world.getBlockState(de3).isReplaceable() && mc.world.getFluidState(de1).isEmpty() && mc.world.getFluidState(de2).isEmpty() && mc.world.getFluidState(de3).isEmpty() && !mc.world.getBlockState(de1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(de2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(de3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(de2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() + 1, mc.player.getY() - 1, mc.player.getZ());
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.SOUTH) || (!useMouseT && wasfacing == Direction.SOUTH)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos ds1 = playerPos.add(new Vec3i(0, -spcoffset.get() - 1, 1));
                        BlockPos ds2 = playerPos.add(new Vec3i(0, -spcoffset.get(), 1));
                        BlockPos ds3 = playerPos.add(new Vec3i(0, -spcoffset.get() + 1, 1));
                        BlockPos pos = playerPos.add(new Vec3i(0, -spcoffset.get() - 2, 1));
                        if (mc.world.getBlockState(ds1).isReplaceable() && mc.world.getBlockState(ds2).isReplaceable() && mc.world.getBlockState(ds3).isReplaceable() && mc.world.getFluidState(ds1).isEmpty() && mc.world.getFluidState(ds2).isEmpty() && mc.world.getFluidState(ds3).isEmpty() && !mc.world.getBlockState(ds1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ds2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ds3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(ds2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() - 1 - spcoffset.get(), mc.player.getZ() + 1);
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    } else {
                        BlockPos ds1 = playerPos.add(new Vec3i(0, -1, 1));
                        BlockPos ds2 = playerPos.add(new Vec3i(0, 0, 1));
                        BlockPos ds3 = playerPos.add(new Vec3i(0, 1, 1));
                        BlockPos pos = playerPos.add(new Vec3i(0, -2, 1));
                        if (mc.world.getBlockState(ds1).isReplaceable() && mc.world.getBlockState(ds2).isReplaceable() && mc.world.getBlockState(ds3).isReplaceable() && mc.world.getFluidState(ds1).isEmpty() && mc.world.getFluidState(ds2).isEmpty() && mc.world.getFluidState(ds3).isEmpty() && !mc.world.getBlockState(ds1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ds2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(ds3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(ds2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ() + 1);
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    }
                }
                if ((useMouseT && currentMoveDir == Direction.WEST) || (!useMouseT && wasfacing == Direction.WEST)) {
                    if (mc.options.jumpKey.isPressed()) {
                        BlockPos dw1 = playerPos.add(new Vec3i(-1, -spcoffset.get() - 1, 0));
                        BlockPos dw2 = playerPos.add(new Vec3i(-1, -spcoffset.get(), 0));
                        BlockPos dw3 = playerPos.add(new Vec3i(-1, -spcoffset.get() + 1, 0));
                        BlockPos pos = playerPos.add(new Vec3i(-1, -spcoffset.get() - 2, 0));
                        if (mc.world.getBlockState(dw1).isReplaceable() && mc.world.getBlockState(dw2).isReplaceable() && mc.world.getBlockState(dw3).isReplaceable() && mc.world.getFluidState(dw1).isEmpty() && mc.world.getFluidState(dw2).isEmpty() && mc.world.getFluidState(dw3).isEmpty() && !mc.world.getBlockState(dw1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dw2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dw3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(dw2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() - 1, mc.player.getY() - 1 - spcoffset.get(), mc.player.getZ());
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    } else {
                        BlockPos dw1 = playerPos.add(new Vec3i(-1, -1, 0));
                        BlockPos dw2 = playerPos.add(new Vec3i(-1, 0, 0));
                        BlockPos dw3 = playerPos.add(new Vec3i(-1, 1, 0));
                        BlockPos pos = playerPos.add(new Vec3i(-1, -2, 0));
                        if (mc.world.getBlockState(dw1).isReplaceable() && mc.world.getBlockState(dw2).isReplaceable() && mc.world.getBlockState(dw3).isReplaceable() && mc.world.getFluidState(dw1).isEmpty() && mc.world.getFluidState(dw2).isEmpty() && mc.world.getFluidState(dw3).isEmpty() && !mc.world.getBlockState(dw1).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dw2).isOf(Blocks.POWDER_SNOW) && !mc.world.getBlockState(dw3).isOf(Blocks.POWDER_SNOW) && mc.world.getWorldBorder().contains(dw2)) {
                            if (mc.world.getBlockState(pos).isReplaceable()) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                mc.player.swingHand(Hand.MAIN_HAND);
                                if (placedPositions.add(pos)) blocksPlaced++;
                            }
                            mc.player.setPosition(mc.player.getX() - 1, mc.player.getY() - 1, mc.player.getZ());
                        } else {
                            if (InvertDownDir.get()) {
                                if (useMouseT) mc.player.setPitch(35);
                                if (!useMouseT) prevPitch = 35;
                            }
                        }
                    }
                }
                if (mc.player.getY() <= downlimit.get() + 1 && InvertDownDir.get()) {
                    if (useMouseT) mc.player.setPitch(35);
                    if (!useMouseT) prevPitch = 35;
                }
            } else if (mc.player.getY() <= downlimit.get() || mc.player.getY() >= limit.get() || delayLeft <= 0 && offLeft <= 0) {
                delayLeft = delay.get();
                offLeft = offTime.get();
            }
        }

        if (!isReturning) {
            PlayerUtils.centerPlayer();
        }
    }

    private void resetTMode() {
        fourWayStage = 0;
        eightWaySubStage = 0;
        currentEightWayDepth = 0;
        isReturning = false;
        fourWayStartPos = null;
        axisPoint = null;
        segmentStartPos = null;
        stuckTicks = 0;
        lastTickPos = null;
        lastLeftWingEnd = null;
        lastRightWingEnd = null;
        isConnecting = false;
    }

    private void seekground() {
        if (!(mc.world.getBlockState(lowestblock.add(new Vec3i(0, -1, 0))).getBlock() == Blocks.AIR)) groundY = lowestblock.getY();
        else {
            for (lowblockY = -2; lowblockY > -319;) {
                BlockPos lowpos1 = lowestblock.add(new Vec3i(0, lowblockY, 0));
                if (mc.world.getBlockState(lowpos1).getBlock() == Blocks.AIR && search) {
                    groundY = lowpos1.getY();
                }
                if (!(mc.world.getBlockState(lowpos1).getBlock() == Blocks.AIR)) search = false;
                lowblockY--;
            }
        }
    }

    private void seekground2() {
        if (!(mc.world.getBlockState(highestblock.add(new Vec3i(0, -1, 0))).getBlock() == Blocks.AIR)) groundY2 = highestblock.getY();
        else {
            for (highblockY = -2; highblockY > -319;) {
                BlockPos lowpos1 = highestblock.add(new Vec3i(0, highblockY, 0));
                if (mc.world.getBlockState(lowpos1).getBlock() == Blocks.AIR && search2) {
                    groundY2 = lowpos1.getY();
                }
                if (!(mc.world.getBlockState(lowpos1).getBlock() == Blocks.AIR)) search2 = false;
                highblockY--;
            }
        }
    }

    private void cascadingpileof() {
        FindItemResult findResult = InvUtils.findInHotbar(block -> !isInvalidBlock(block));
        if (!findResult.found() || findResult.slot() < 0 || findResult.slot() > 8) return;
        mc.player.getInventory().setSelectedSlot(findResult.slot());
    }

    private boolean isInvalidBlock(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem)
            || stack.getItem() instanceof BedItem
            || stack.getItem() instanceof PowderSnowBucketItem
            || stack.getItem() instanceof ScaffoldingItem
            || stack.getItem() instanceof TallBlockItem
            || stack.getItem() instanceof VerticallyAttachableBlockItem
            || stack.getItem() instanceof PlaceableOnWaterItem
            || ((BlockItem) stack.getItem()).getBlock() instanceof PlantBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof TorchBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof AbstractRedstoneGateBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof RedstoneWireBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof FenceBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof WallBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof FenceGateBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof FallingBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof AbstractRailBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof AbstractSignBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof BellBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CarpetBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof ConduitBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CoralFanBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CoralWallFanBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof DeadCoralFanBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof DeadCoralWallFanBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof TripwireHookBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof PointedDripstoneBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof TripwireBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof SnowBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof PressurePlateBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof WallMountedBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof AmethystClusterBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof BuddingAmethystBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof ChorusFlowerBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof ChorusPlantBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof LanternBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CandleBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof TntBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CakeBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CobwebBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof SugarCaneBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof SporeBlossomBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof KelpBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof GlowLichenBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof CactusBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof BambooBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof FlowerPotBlock
            || ((BlockItem) stack.getItem()).getBlock() instanceof LadderBlock
            || skippableBlox.get().contains(((BlockItem) stack.getItem()).getBlock());
    }
}
