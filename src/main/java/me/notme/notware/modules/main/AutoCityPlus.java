package me.notme.notware.modules.main;

import me.notme.notware.NotWare;
import me.notme.notware.utils.combat.NotwareUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;

public class AutoCityPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPause = settings.createGroup("Pause");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The radius players can be in to be targeted.")
        .defaultValue(5)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mine")
        .description("Will mine the blocks using packets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> extraSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("extra-swing")
        .description("Will send a extra hand swing packet.")
        .defaultValue(false)
        .visible(packetMine::get)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Changes slots only clientside.")
        .defaultValue(false)
        .build()
    );

    // Pause

    private final Setting<Boolean> eatPause = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Pauses cev breaker when eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> drinkPause = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-drink")
        .description("Pauses cev breaker when drinking.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> renderSwing = sgRender.add(new BoolSetting.Builder()
        .name("render-swing")
        .description("Renders a hand swing animation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the obsidian will be placed.")
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
        .defaultValue(new SettingColor(140, 170, 245, 25))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(140, 170, 245, 255))
        .visible(render::get)
        .build()
    );

    public static ArrayList<BlockPos> surround = new ArrayList<>() {{
        add(new BlockPos(1, 0, 0));
        add(new BlockPos(-1, 0, 0));
        add(new BlockPos(0, 0, 1));
        add(new BlockPos(0, 0, -1));
    }};

    private final ArrayList<BlockPos> plus = new ArrayList<>() {{
        add(new BlockPos(1, 0, 0));
        add(new BlockPos(1, 0, 1));
        add(new BlockPos(-1, 0, 0));
        add(new BlockPos(-1, 0, -1));
        add(new BlockPos(0, 0, 1));
        add(new BlockPos(-1, 0, 1));
        add(new BlockPos(0, 0, -1));
        add(new BlockPos(1, 0, -1));
    }};

    private ArrayList<BlockPos> positions = new ArrayList<>();
    private PlayerEntity target;
    private BlockPos breakingPos;

    public AutoCityPlus() {
        super(NotWare.nwcombat, "auto-city-plus", "yes");
    }

    @Override
    public void onActivate() {
        target = null;
        breakingPos = null;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        target = TargetUtils.getPlayerTarget(range.get(), priority.get());

        FindItemResult obby = InvUtils.findInHotbar(Items.OBSIDIAN);
        FindItemResult pick = InvUtils.findInHotbar(stack -> stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE);
        FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);

        if (!obby.found()) {
            error("can't find obsidian in your hotbar, disabling...");
            toggle();
            return;
        }

        if (!pick.found()) {
            error("can't find a pickaxe in your hotbar, disabling...");
            toggle();
            return;
        }

        if (!crystal.found()) {
            error("can't find crystals in your hotbar, disabling...");
            toggle();
            return;
        }

        if (PlayerUtils.shouldPause(false, eatPause.get(), drinkPause.get())) return;

        if (target != null && shouldCity(target.getBlockPos())) {
            BlockPos pos = target.getBlockPos();

            if (breakingPos != null && !getSurround(pos).isEmpty()) {
                for (BlockPos p : getSurround(pos)) {
                    if (!getCrystalPositions(breakingPos.add(p)).isEmpty()) {
                        interact(getCrystalPositions(breakingPos.add(p).down()).get(0), crystal, Direction.UP);
                    }
                }
            }

            if (breakingPos != null && !getSurround(pos).isEmpty()) {
                for (BlockPos p : getSurround(pos)) {
                    if (!getCrystals(pos.add(p)).isEmpty()) {
                        attack(getCrystals(pos.add(p)).get(0));
                    }
                }
            }
        }
    }

    private ArrayList<BlockPos> getAir(BlockPos pos) {
        ArrayList<BlockPos> air = new ArrayList<>();

        for (BlockPos position : surround) {
            if (NotwareUtils.getBlockState(pos.add(position)).isAir()) air.add(pos.add(position));
        }

        air.sort(Comparator.comparingDouble(pos2 -> NotwareUtils.distance(mc.player.getPos(), Utils.vec3d(pos2))));
        return air;
    }

    private ArrayList<BlockPos> getCity(BlockPos pos) {
        ArrayList<BlockPos> city = new ArrayList<>();

        for (BlockPos position : surround) {
            if (!NotwareUtils.getBlockState(pos.add(position)).isAir() && BlockUtils.canBreak(pos.add(position))) city.add(pos.add(position));
        }

        city.sort(Comparator.comparingDouble(pos2 -> NotwareUtils.distance(mc.player.getPos(), Utils.vec3d(pos2))));
        return city;
    }

    private ArrayList<BlockPos> getSurround(BlockPos pos) {
        ArrayList<BlockPos> surr = new ArrayList<>();

        for (BlockPos position : surround) {
            if (!NotwareUtils.getBlockState(pos.add(position)).isAir() && BlockUtils.canBreak(pos.add(position))) surr.add(pos.add(position));
        }

        surr.sort(Comparator.comparingDouble(position -> NotwareUtils.distance(mc.player.getPos(), Utils.vec3d(position))));
        return surr;
    }

    private ArrayList<Entity> getCrystals(BlockPos pos) {
        ArrayList<BlockPos> crystals = new ArrayList<>();
        ArrayList<Entity> entities = new ArrayList<>();

        if (canInteract(pos.down())) crystals.add(pos.down());

        for (int i = 0; i <= 1; i++) {
            for (BlockPos position : plus) {
                if (canInteract(pos.add(position).down(i))) crystals.add(pos.add(position).down(i));
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && crystals.contains(entity.getBlockPos())) entities.add(entity);
        }

        entities.sort(Comparator.comparingDouble(entity -> NotwareUtils.distance(mc.player.getPos(), Vec3d.ofCenter(entity.getBlockPos()))));

        return entities;
    }

    private ArrayList<BlockPos> getCrystalPositions(BlockPos pos) {
        ArrayList<BlockPos> crystals = new ArrayList<>();

        if (canInteract(pos.down())) crystals.add(pos.down());

        for (int i = 0; i <= 1; i++) {
            for (BlockPos position : plus) {
                if (canInteract(pos.add(position).down(i))) crystals.add(pos.add(position).down(i));
            }
        }

        crystals.sort(Comparator.comparingDouble(position -> NotwareUtils.distance(mc.player.getPos(), Vec3d.ofCenter(position))));

        return crystals;
    }

    private boolean shouldCity(BlockPos pos) {
        int i = 0;

        for (BlockPos position : surround) {
            if (NotwareUtils.getBlockState(pos.add(position)).isAir() || !BlockUtils.canBreak(pos.add(position))) i++;
        }

        return i == 0;
    }

    private boolean canInteract(BlockPos pos) {
        if (!NotwareUtils.getBlockState(pos).isAir()) return false;

        double x = pos.getX();
        double y = pos.getY() + 1;
        double z = pos.getZ();

        Box box = new Box(x - 0.5, y, z - 0.5, x + 1.5, y + 1.5, z + 1.5);

        return !EntityUtils.intersectsWithEntity(box, entity -> !entity.isSpectator());
    }

    private void mine(BlockPos pos, FindItemResult pick) {
        if (!packetMine.get()) {
            BlockUtils.breakBlock(pos, renderSwing.get());
        } else {
            if (mc.player.getInventory().selectedSlot != pick.getSlot()) InvUtils.swap(pick.getSlot(), false);

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            if (extraSwing.get()) swingHand(Hand.MAIN_HAND);
        }
    }

    private void attack(Entity entity) {
        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));

        swingHand(Hand.MAIN_HAND);
    }

    private void interact(BlockPos pos, FindItemResult item, Direction direction) {
        if (item == null) return;
        if (item.getHand() == null || !silentSwitch.get()) InvUtils.swap(item.getSlot(), false);

        if (silentSwitch.get() && item.getHand() != null) {
            mc.interactionManager.interactBlock(mc.player, mc.world, item.getHand(), new BlockHitResult(mc.player.getPos(), direction, pos, true));
            swingHand(item.getHand());
        } else {
            mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, new BlockHitResult(mc.player.getPos(), direction, pos, true));
            swingHand(Hand.MAIN_HAND);
        }
    }

    private void swingHand(Hand hand) {
        if (renderSwing.get()) mc.player.swingHand(hand);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }

    @Override
    public String getInfoString() {
        return target != null ? target.getEntityName() : null;
    }

    // Render

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (positions.isEmpty() || !render.get()) return;

        for (BlockPos pos : positions) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            positions.remove(pos);
        }
    }
}
