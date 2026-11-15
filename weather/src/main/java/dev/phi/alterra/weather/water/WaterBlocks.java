package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** The blocks of finite water: the drain (water that reaches it is gone) and slush (water half frozen, holding its parts until it thaws). */
public final class WaterBlocks {
    private WaterBlocks() {}
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlterraWeather.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlterraWeather.ID);

    /** A grate: water standing on it or running against it drains away, two parts a step from above and one from each side. */
    public static final DeferredBlock<Block> DRAIN = BLOCKS.register("drain", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    /** Water half frozen: 1 to 8 parts (its height), soft to walk through. It thaws back into the same water when it is warm again. */
    public static final class Slush extends Block {
        public static final IntegerProperty PARTS = IntegerProperty.create("parts", 1, 8);
        Slush(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(PARTS, 4)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(PARTS); }
        @Override protected VoxelShape getShape(BlockState st, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(0, 0, 0, 16, st.getValue(PARTS) * 2, 16); }
        @Override protected VoxelShape getCollisionShape(BlockState st, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override protected void entityInside(BlockState st, Level l, BlockPos p, Entity e) { e.makeStuckInBlock(st, new Vec3(0.75, 0.9, 0.75)); }
        @Override protected boolean isRandomlyTicking(BlockState st) { return true; }
        @Override protected void randomTick(BlockState st, ServerLevel l, BlockPos p, RandomSource r) {
            if (!FiniteWater.freezing(l, p)) FiniteWater.set(l, p, st.getValue(PARTS));   // thaws into the water it was
        }
        @Override protected boolean propagatesSkylightDown(BlockState st, BlockGetter l, BlockPos p) { return true; }
    }
    public static final DeferredBlock<Slush> SLUSH = BLOCKS.register("slush", () -> new Slush(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.2f).sound(SoundType.SNOW).noOcclusion().pushReaction(PushReaction.DESTROY).replaceable()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus);
        ITEMS.registerSimpleBlockItem("drain", DRAIN);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(DRAIN.get());
        });
    }
}
