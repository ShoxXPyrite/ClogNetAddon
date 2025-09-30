package com.ShoXx.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import com.ShoXx.addon.AddonTemplate;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class WallBuilder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPattern1 = settings.createGroup("Pattern 1");
    private final SettingGroup sgPattern2 = settings.createGroup("Pattern 2");
    private final SettingGroup sgPattern3 = settings.createGroup("Pattern 3");

    private int currentPatternIndex = 1;
    private int buildStep = 0;
    private State state = State.BUILDING;

    private final Setting<Boolean> usePattern2 = sgGeneral.add(new BoolSetting.Builder()
        .name("use-pattern-2")
        .description("Whether to use pattern 2 after pattern 1.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> usePattern3 = sgGeneral.add(new BoolSetting.Builder()
        .name("use-pattern-3")
        .description("Whether to use pattern 3 after pattern 2.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between placing blocks (in milliseconds).")
        .defaultValue(150)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> placeRetries = sgGeneral.add(new IntSetting.Builder()
        .name("place-retries")
        .description("How many times to retry placing a block if it fails.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> patternDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pattern-delay")
        .description("Delay after completing a pattern (in ticks).")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> autoWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk")
        .description("Automatically walk backwards after building using Baritone pathfinding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Block> blockType = sgGeneral.add(new BlockSetting.Builder()
        .name("block-type")
        .description("The type of block to place for the wall.")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    // Pattern-specific block types
    private final Setting<Block> pattern1BlockType = sgPattern1.add(new BlockSetting.Builder()
        .name("pattern1-block-type")
        .description("The type of block to place for pattern 1.")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    private final Setting<Block> pattern2BlockType = sgPattern2.add(new BlockSetting.Builder()
        .name("pattern2-block-type")
        .description("The type of block to place for pattern 2.")
        .defaultValue(Blocks.CRYING_OBSIDIAN)
        .visible(usePattern2::get)
        .build()
    );

    private final Setting<Block> pattern3BlockType = sgPattern3.add(new BlockSetting.Builder()
        .name("pattern3-block-type")
        .description("The type of block to place for pattern 3.")
        .defaultValue(Blocks.COBWEB)
        .visible(usePattern3::get)
        .build()
    );

    private final Setting<Boolean> diagonalBuilding = sgGeneral.add(new BoolSetting.Builder()
        .name("diagonal-building")
        .description("Build walls diagonally instead of straight.")
        .defaultValue(false)
        .build()
    );

    // Render settings
    private final Setting<Boolean> renderOverlay = sgRender.add(new BoolSetting.Builder()
        .name("render-overlay")
        .description("Render block overlay for the wall pattern.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the rendered blocks.")
        .defaultValue(new SettingColor(255, 255, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the rendered blocks.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    // Pattern 1 boolean arrays for GUI
    private boolean[][] pattern1 = new boolean[5][5];
    private boolean[][] pattern2 = new boolean[5][5];
    private boolean[][] pattern3 = new boolean[5][5];

    private long lastPlaceTime = 0;
    private int walkTicks = 0;
    private int currentRetries = 0;
    private BlockPos targetPos;
    private List<BlockPos> placedBlocks = new ArrayList<>();
    private boolean baritoneAvailable = false;

    private enum State {
        BUILDING,
        WALKING,
        WAITING
    }

    public WallBuilder() {
        super(AddonTemplate.CATEGORY, "wall-builder", "Builds a 5x5 wall in front of you based on patterns using any selected block type.");

        // Initialize pattern arrays with default values
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                pattern1[i][j] = false;
                pattern2[i][j] = false;
                pattern3[i][j] = false;
            }
        }

        // Check if Baritone is available
        try {
            Class.forName("baritone.api.BaritoneAPI");
            baritoneAvailable = true;
            info("Baritone detected - auto-walk feature available");
        } catch (ClassNotFoundException e) {
            baritoneAvailable = false;
            info("Baritone not detected - auto-walk feature disabled");
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        // Pattern 1 section
        list.add(theme.label("Pattern 1")).expandX();
        WTable table1 = theme.table();
        list.add(table1);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                final int row = i;
                final int col = j;
                var checkbox = table1.add(theme.checkbox(pattern1[i][j])).widget();
                checkbox.action = () -> pattern1[row][col] = checkbox.checked;
            }
            table1.row();
        }

        // Pattern 2 section (only if usePattern2 is enabled)
        if (usePattern2.get()) {
            list.add(theme.label("Pattern 2")).expandX();
            WTable table2 = theme.table();
            list.add(table2);

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    final int row = i;
                    final int col = j;
                    var checkbox = table2.add(theme.checkbox(pattern2[i][j])).widget();
                    checkbox.action = () -> pattern2[row][col] = checkbox.checked;
                }
                table2.row();
            }
        }

        // Pattern 3 section (only if usePattern3 is enabled)
        if (usePattern3.get()) {
            list.add(theme.label("Pattern 3")).expandX();
            WTable table3 = theme.table();
            list.add(table3);

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    final int row = i;
                    final int col = j;
                    var checkbox = table3.add(theme.checkbox(pattern3[i][j])).widget();
                    checkbox.action = () -> pattern3[row][col] = checkbox.checked;
                }
                table3.row();
            }
        }

        return list;
    }

    @Override
    public void onActivate() {
        buildStep = 0;
        currentPatternIndex = 1;
        walkTicks = 0;
        state = State.BUILDING;
        currentRetries = 0;
        targetPos = null;
        placedBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        placedBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case BUILDING:
                build();
                break;
            case WALKING:
                walk();
                break;
            case WAITING:
                wait_state();
                break;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderOverlay.get() || mc.player == null) return;

        Direction facing = mc.player.getHorizontalFacing();
        BlockPos start = mc.player.getBlockPos().offset(facing, 2);

        boolean[][] currentPattern;
        switch(currentPatternIndex)
        {
            case 1 -> currentPattern = pattern1;
            case 2 -> currentPattern = pattern2;
            case 3 -> currentPattern = pattern3;
            default -> throw new IllegalStateException("Unexpected pattern index: " + currentPatternIndex);
        }

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (!currentPattern[i][j]) continue;

                BlockPos pos;
                if (diagonalBuilding.get()) {
                    // Diagonal building: render wall along the diagonal axis the player is facing
                    // Wall extends diagonally from the start position
                    // j ranges from 0-4, so blocks are placed at positions 0,1,2,3,4 along the diagonal

                    switch (facing) {
                        case NORTH: // Player facing North, render NE diagonal wall
                            // Example: at (0,-59,0) render blocks at (0,-59,-4), (1,-59,-3), (2,-59,-2), (3,-59,-1), (4,-59,0)
                            pos = start.add(j, i, -2 + j); // NE diagonal: X increases, Z increases toward 0
                            break;
                        case EAST: // Player facing East, render SE diagonal wall
                            pos = start.add(2 - j, i, j); // SE diagonal: X decreases from +2, Z increases
                            break;
                        case SOUTH: // Player facing South, render SW diagonal wall
                            pos = start.add(-j, i, 2 - j); // SW diagonal: X decreases, Z decreases from +2
                            break;
                        case WEST: // Player facing West, render NW diagonal wall
                            pos = start.add(-2 + j, i, -j); // NW diagonal: X increases toward 0, Z decreases
                            break;
                        default:
                            pos = start.add(j, i, 0);
                            break;
                    }
                } else {
                    // Normal building: render straight wall perpendicular to facing direction
                    pos = start.add(
                        facing.rotateYClockwise().getOffsetX() * (j - 2),
                        i,
                        facing.rotateYClockwise().getOffsetZ() * (j - 2)
                    );
                }

                // Different color for placed blocks
                SettingColor currentSideColor = placedBlocks.contains(pos) ?
                    new SettingColor(0, 255, 0, 50) : sideColor.get();
                SettingColor currentLineColor = placedBlocks.contains(pos) ?
                    new SettingColor(0, 255, 0, 255) : lineColor.get();

                event.renderer.box(pos, currentSideColor, currentLineColor, shapeMode.get(), 0);
            }
        }
    }

    private void build() {
        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.get()) return;

        Direction facing = mc.player.getHorizontalFacing();
        BlockPos start = mc.player.getBlockPos().offset(facing, 2);

        boolean[][] currentPattern;
        switch (currentPatternIndex) {
            case 1 -> currentPattern = pattern1;
            case 2 -> currentPattern = pattern2;
            case 3 -> currentPattern = pattern3;
            default -> throw new IllegalStateException("Unexpected pattern index: " + currentPatternIndex);
        }

        for (; buildStep < 25; buildStep++) {
            int x = buildStep % 5;
            int y = buildStep / 5;
            if (!currentPattern[y][x]) continue;

            BlockPos pos;
            if (diagonalBuilding.get()) {
                // Diagonal building: build wall along the diagonal axis the player is facing
                // Wall extends diagonally from the start position
                // x ranges from 0-4, so blocks are placed at positions 0,1,2,3,4 along the diagonal

                switch (facing) {
                    case NORTH: // Player facing North, build NE diagonal wall
                        // Example: at (0,-59,0) place blocks at (0,-59,-4), (1,-59,-3), (2,-59,-2), (3,-59,-1), (4,-59,0)
                        pos = start.add(x, y, -2 + x); // NE diagonal: X increases, Z increases toward 0
                        break;
                    case EAST: // Player facing East, build SE diagonal wall
                        pos = start.add(2 - x, y, x); // SE diagonal: X decreases from +2, Z increases
                        break;
                    case SOUTH: // Player facing South, build SW diagonal wall
                        pos = start.add(-x, y, 2 - x); // SW diagonal: X decreases, Z decreases from +2
                        break;
                    case WEST: // Player facing West, build NW diagonal wall
                        pos = start.add(-2 + x, y, -x); // NW diagonal: X increases toward 0, Z decreases
                        break;
                    default:
                        pos = start.add(x, y, 0);
                        break;
                }
            } else {
                // Normal building: build straight wall perpendicular to facing direction
                pos = start.add(
                    facing.rotateYClockwise().getOffsetX() * (x - 2),
                    y,
                    facing.rotateYClockwise().getOffsetZ() * (x - 2)
                );
            }

            // Check if the block is already placed
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                if (!placedBlocks.contains(pos)) {
                    placedBlocks.add(pos);
                }
                continue; // Block is already placed, move to the next one
            }

            if (placeBlock(pos)) {
                currentRetries = 0;
                placedBlocks.add(pos);
                return;
            }

            if (currentRetries < placeRetries.get()) {
                currentRetries++;
                return;
            }

            currentRetries = 0;
        }

        if (buildStep >= 25) {
            // Pattern completed
            if (autoWalk.get() && baritoneAvailable) {
                startBaritoneWalk();
                state = State.WALKING;
                walkTicks = patternDelay.get();
            } else {
                state = State.WAITING;
                walkTicks = patternDelay.get();
            }
        }
    }

    private void startBaritoneWalk() {
        if (!baritoneAvailable) return;

        try {
            Direction facing = mc.player.getHorizontalFacing();
            String command;

            if (diagonalBuilding.get()) {
                // Diagonal movement commands - move backward-left to continue diagonal pattern
                switch (facing) {
                    case NORTH: // Built NE diagonal, move SW to continue
                        command = "#goto ~-1 ~ ~+1"; // Southwest
                        break;
                    case EAST: // Built SE diagonal, move NW to continue
                        command = "#goto ~-1 ~ ~-1"; // Northwest
                        break;
                    case SOUTH: // Built SW diagonal, move NE to continue
                        command = "#goto ~+1 ~ ~-1"; // Northeast
                        break;
                    case WEST: // Built NW diagonal, move SE to continue
                        command = "#goto ~+1 ~ ~+1"; // Southeast
                        break;
                    default:
                        command = "#goto ~-1 ~ ~+1"; // fallback
                        break;
                }
            } else {
                // Straight movement commands
                switch (facing) {
                    case NORTH:
                        command = "#goto ~ ~ ~+1";
                        break;
                    case EAST:
                        command = "#goto ~-1 ~ ~";
                        break;
                    case SOUTH:
                        command = "#goto ~ ~ ~-1";
                        break;
                    case WEST:
                        command = "#goto ~+1 ~ ~";
                        break;
                    default:
                        command = "#goto ~ ~ ~-1"; // fallback
                        break;
                }
            }

            mc.getNetworkHandler().sendChatMessage(command);
            if (diagonalBuilding.get()) {
                info("Started Baritone auto-walk: moving diagonally from " + facing.toString().toLowerCase());
            } else {
                info("Started Baritone auto-walk: moving 1 block backwards from " + facing.toString().toLowerCase());
            }

        } catch (Exception e) {
            error("Failed to start Baritone walk: " + e.getMessage());
            // Fallback to waiting state
            state = State.WAITING;
            walkTicks = patternDelay.get();
        }
    }

    private void walk() {
        if (walkTicks > 0) {
            walkTicks--;
            return;
        }

        // Check if Baritone is still pathfinding
        if (baritoneAvailable) {
            try {
                // Simple check - if player is still moving significantly, Baritone is probably still active
                Vec3d velocity = mc.player.getVelocity();
                double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

                if (speed > 0.1) {
                    // Still moving, wait a bit more
                    walkTicks = 5;
                    return;
                }
            } catch (Exception e) {
                // If there's an error checking Baritone status, just continue
            }
        }

        // Transition to waiting state
        state = State.WAITING;
        walkTicks = patternDelay.get();
    }

    private void wait_state() {
        if (walkTicks > 0) {
            walkTicks--;
            return;
        }

        // Switch patterns and restart building continuously
        buildStep = 0;
        placedBlocks.clear();

        if (usePattern2.get() && usePattern3.get()) {
            currentPatternIndex = (currentPatternIndex % 3) + 1; // 1 -> 2 -> 3 -> 1
        } else if (usePattern2.get()) {
            currentPatternIndex = (currentPatternIndex % 2) + 1; // 1 -> 2 -> 1
        } else {
            currentPatternIndex = 1; // only pattern1 enabled
        }

        state = State.BUILDING;
    }

    private boolean placeBlock(BlockPos pos) {
        // Use pattern-specific block type if available, otherwise fall back to general block type
        Block selectedBlock;
        switch (currentPatternIndex) {
            case 1 -> selectedBlock = pattern1BlockType.get();
            case 2 -> selectedBlock = pattern2BlockType.get();
            case 3 -> selectedBlock = pattern3BlockType.get();
            default -> selectedBlock = pattern1BlockType.get();
        }

        FindItemResult blockItem = InvUtils.findInHotbar(selectedBlock.asItem());
        if (!blockItem.found()) {
            error("No " + selectedBlock.getName().getString() + " blocks in hotbar!");
            toggle();
            return false;
        }

        InvUtils.swap(blockItem.slot(), false);

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            hit,
            mc.player.currentScreenHandler.getRevision() + 2
        ));

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        lastPlaceTime = System.currentTimeMillis();
        buildStep++;

        return !mc.world.getBlockState(pos).isReplaceable();
    }
}
