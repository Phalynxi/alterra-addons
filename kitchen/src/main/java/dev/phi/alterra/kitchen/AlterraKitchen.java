package dev.phi.alterra.kitchen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.phi.alterra.api.AlterraApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Alterra Kitchen: bottled fruit juices (each with its own glass), the juices as fluids for pipes and tanks, the fruit press, fruit crates
 * and the grape trellis. The list of juices, crates and the press table are data files written with the art (alterra_kitchen_*.json).
 */
@Mod(AlterraKitchen.ID)
public final class AlterraKitchen {
    public static final String ID = "alterra_kitchen";
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, ID);

    public static final List<DeferredItem<Item>> DRINKS = new ArrayList<>();
    public static final List<DeferredItem<BlockItem>> DECOR = new ArrayList<>();
    /** A juice as a fluid: pressed or mixed in Create, piped, bottled by a spout. No placeable block or bucket. */
    public record Juice(String id, int color, DeferredHolder<FluidType, FluidType> type) {}
    public static final List<Juice> JUICES = new ArrayList<>();

    public static final DeferredBlock<Block> FRUIT_PRESS = BLOCKS.register("fruit_press", () -> new FruitPressBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final Supplier<BlockEntityType<FruitPressBlockEntity>> FRUIT_PRESS_BE = BLOCK_ENTITIES.register("fruit_press",
            () -> BlockEntityType.Builder.of(FruitPressBlockEntity::new, FRUIT_PRESS.get()).build(null));

    public AlterraKitchen(IEventBus bus) {
        registerDecor();
        registerJuices();
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); FLUID_TYPES.register(bus); FLUIDS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) DRINKS.forEach(i -> e.accept(i.get()));
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) DECOR.forEach(i -> e.accept(i.get()));
        });
        AlterraApi.addTabSection(id("drinks"), 0xFFFFF4E8, () -> DRINKS.stream().map(d -> new ItemStack(d.get())).toList());
        AlterraApi.addTabSection(id("decor"), 0xFFF2E6C8, () -> DECOR.stream().map(d -> new ItemStack(d.get())).toList());
    }

    private static <T> T read(String file, TypeToken<T> type) {
        try (var in = AlterraKitchen.class.getResourceAsStream("/" + file)) {
            return new Gson().fromJson(new InputStreamReader(in), type.getType());
        } catch (Exception e) { throw new IllegalStateException("Alterra Kitchen: cannot read " + file, e); }
    }

    private static DeferredBlock<Block> block(String name, Supplier<Block> factory) {
        DeferredBlock<Block> b = BLOCKS.register(name, factory);
        DECOR.add(ITEMS.registerSimpleBlockItem(name, b));
        return b;
    }

    private static void registerDecor() {
        DECOR.add(ITEMS.registerSimpleBlockItem("fruit_press", FRUIT_PRESS));
        block("trellis", () -> new TrellisBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
        for (String crate : read("alterra_kitchen_crates.json", new TypeToken<List<String>>() {}))
            block(crate, () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    /** Bottled juices: drunk like honey (always drinkable, the glass bottle comes back), plus the same juice as a fluid. */
    private static void registerJuices() {
        List<Map<String, Object>> list = read("alterra_kitchen_juices.json", new TypeToken<List<Map<String, Object>>>() {});
        for (Map<String, Object> j : list) {
            String name = (String) j.get("id");
            int color = ((Number) j.get("color")).intValue();
            DRINKS.add(ITEMS.register(name, () -> {
                FoodProperties base = new FoodProperties.Builder().nutrition(3).saturationModifier(0.4f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE).build();
                FoodProperties food = new FoodProperties(base.nutrition(), base.saturation(), base.canAlwaysEat(), 2.0f, base.usingConvertsTo(), base.effects());   // drunk in 2 s
                return new Item(new Item.Properties().food(food).stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)) {
                    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.DRINK; }
                    @Override public SoundEvent getDrinkingSound() { return SoundEvents.GENERIC_DRINK; }
                    @Override public SoundEvent getEatingSound() { return SoundEvents.GENERIC_DRINK; }
                };
            }));
            DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create().descriptionId("item." + ID + "." + name)
                    .density(1100).viscosity(1300).sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
            Juice[] self = new Juice[1];
            DeferredHolder<Fluid, FlowingFluid> source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props(self[0])));
            DeferredHolder<Fluid, FlowingFluid> flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(props(self[0])));
            self[0] = new Juice(name, color, type);
            JUICES.add(self[0]);
            SOURCES.put(name, source); FLOWINGS.put(name, flowing);
        }
    }
    private static final Map<String, DeferredHolder<Fluid, FlowingFluid>> SOURCES = new java.util.HashMap<>(), FLOWINGS = new java.util.HashMap<>();
    private static BaseFlowingFluid.Properties props(Juice j) { return new BaseFlowingFluid.Properties(j.type(), SOURCES.get(j.id()), FLOWINGS.get(j.id())); }
}
