package dev.phi.alterra.kitchen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.phi.alterra.api.AlterraApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
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

/** Alterra Kitchen: juices (items and fluids), the fruit press, crates and the trellis. The lists of juices and crates are in the json files next to the art. */
@Mod(AlterraKitchen.ID)
public final class AlterraKitchen {
    public static final String ID = "alterra_kitchen";
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, ID);

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(ID);
    /** What the Explorer's Journal knows about food: kept on the book itself, so what you discover is lost with it and shared with anyone who holds it. */
    public static final Supplier<net.minecraft.core.component.DataComponentType<TastedSet>> JOURNAL_NOTES = COMPONENTS.registerComponentType("journal_notes", b -> b.persistent(TastedSet.CODEC).networkSynchronized(TastedSet.STREAM_CODEC));
    public static final Supplier<net.minecraft.core.component.DataComponentType<Dish>> DISH = COMPONENTS.registerComponentType("dish", b -> b.persistent(Dish.CODEC).networkSynchronized(Dish.STREAM_CODEC));
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, ID);
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<DishRecipe>> DISH_RECIPE = RECIPE_SERIALIZERS.register("dish", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(DishRecipe::new));
    public static final Supplier<net.minecraft.core.component.DataComponentType<MixedDrink>> MIXED = COMPONENTS.registerComponentType("mixed_drink", b -> b.persistent(MixedDrink.CODEC).networkSynchronized(MixedDrink.STREAM_CODEC));
    public static final Supplier<net.minecraft.core.component.DataComponentType<Scoops>> SCOOPS = COMPONENTS.registerComponentType("scoops", b -> b.persistent(Scoops.CODEC).networkSynchronized(Scoops.STREAM_CODEC));
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<IceCreamStackRecipe>> ICE_CREAM_STACK_RECIPE = RECIPE_SERIALIZERS.register("ice_cream_stack", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(IceCreamStackRecipe::new));
    public static final DeferredItem<Item> ICE_CREAM_STACK = ITEMS.register("ice_cream_stack", IceCreamItem::new);
    /** The made-up dishes by their base (noodles, spaghetti, macaroni, flatbread, sandwich, stuffed_potato). */
    public static final Map<String, DeferredItem<Item>> DISH_ITEMS = new java.util.LinkedHashMap<>();
    /** How many tinted zones a dish's picture has (see KitchenClient). */
    public static final Map<String, Integer> DISH_ZONES = new java.util.HashMap<>();
    /** The entry of each dish in alterra_kitchen_dishes.json (zone names and the colours they have by default), by item id. */
    public static final Map<String, Map<String, Object>> DISH_META = new java.util.HashMap<>();
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, ID);
    public static final Supplier<net.minecraft.world.inventory.MenuType<AssemblyMenu>> KITCHEN_MENU = MENUS.register("assembly", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AssemblyMenu::client));
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, ID);
    public static final Supplier<net.minecraft.world.item.crafting.RecipeType<FreezingRecipe>> FREEZING = RECIPE_TYPES.register("freezing", () -> net.minecraft.world.item.crafting.RecipeType.simple(id("freezing")));
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<DrinkMixRecipe>> DRINK_MIX = RECIPE_SERIALIZERS.register("drink_mix", DrinkMixRecipe::serializer);
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<FreezingRecipe>> FREEZING_SERIALIZER = RECIPE_SERIALIZERS.register("freezing", () -> new net.minecraft.world.item.crafting.SimpleCookingSerializer<>(FreezingRecipe::new, 100));
    public static final DeferredBlock<Block> ICE_MACHINE = BLOCKS.register("ice_machine", () -> new KitchenFurnaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .lightLevel(st -> st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 4 : 0), () -> AlterraKitchen.ICE_MACHINE_BE.get(), FREEZING, "Ice Cream Machine", false));
    public static final Supplier<BlockEntityType<KitchenFurnaceEntity>> ICE_MACHINE_BE = BLOCK_ENTITIES.register("ice_machine", () -> BlockEntityType.Builder.of((pos, st) -> new KitchenFurnaceEntity(AlterraKitchen.ICE_MACHINE_BE.get(), pos, st, FREEZING.get(), "Ice Cream Machine", false), ICE_MACHINE.get()).build(null));
    public static final DeferredBlock<Block> PREP_TABLE = BLOCKS.register("prep_table", () -> new KitchenCounterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).ignitedByLava(), AssemblyMenu.Layout.PREP));
    public static final DeferredBlock<Block> ICE_CREAM_STAND = BLOCKS.register("ice_cream_stand", () -> new KitchenCounterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(2.0F).sound(SoundType.WOOD).noOcclusion(), AssemblyMenu.Layout.STAND));
    public static final Supplier<BlockEntityType<AssemblyBlockEntity>> ASSEMBLY_BE = BLOCK_ENTITIES.register("assembly", () -> BlockEntityType.Builder.of(AssemblyBlockEntity::new, AlterraKitchen.PREP_TABLE.get(), AlterraKitchen.KITCHEN_COUNTER.get(), AlterraKitchen.ICE_CREAM_STAND.get()).build(null));
    public static final DeferredBlock<Block> STOVE = BLOCKS.register("stove", () -> new KitchenFurnaceBlock(stationProps(), () -> AlterraKitchen.STOVE_BE.get(), () -> net.minecraft.world.item.crafting.RecipeType.SMOKING, "Stove", true));
    public static final DeferredBlock<Block> OVEN = BLOCKS.register("oven", () -> new KitchenFurnaceBlock(stationProps(), () -> AlterraKitchen.OVEN_BE.get(), () -> net.minecraft.world.item.crafting.RecipeType.SMELTING, "Oven", false));
    public static final DeferredBlock<Block> KITCHEN_COUNTER = BLOCKS.register("kitchen_counter", () -> new KitchenCounterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD), AssemblyMenu.Layout.COUNTER));
    public static final Supplier<BlockEntityType<KitchenFurnaceEntity>> STOVE_BE = BLOCK_ENTITIES.register("stove", () -> BlockEntityType.Builder.of((pos, st) -> new KitchenFurnaceEntity(AlterraKitchen.STOVE_BE.get(), pos, st, net.minecraft.world.item.crafting.RecipeType.SMOKING, "Stove", true), STOVE.get()).build(null));
    public static final Supplier<BlockEntityType<KitchenFurnaceEntity>> OVEN_BE = BLOCK_ENTITIES.register("oven", () -> BlockEntityType.Builder.of((pos, st) -> new KitchenFurnaceEntity(AlterraKitchen.OVEN_BE.get(), pos, st, net.minecraft.world.item.crafting.RecipeType.SMELTING, "Oven", false), OVEN.get()).build(null));
    private static BlockBehaviour.Properties stationProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .lightLevel(st -> st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 13 : 0);
    }
    public static final DeferredBlock<Block> ROCK_SALT = BLOCKS.register("rock_salt", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 3.0F).sound(SoundType.CALCITE).requiresCorrectToolForDrops()) {
        @Override public net.minecraft.network.chat.MutableComponent getName() { return net.minecraft.network.chat.Component.literal("Rock Salt"); }
    });

    /** Wild plants: block name, name, the part they drop, its name, taste (a plant with taste 0 is harmless and a normal food). Their art, loot and world generation come from tools/food_art.py. */
    private static final String[][] PLANTS = {
        {"poison_fern", "Fiddlehead Fern", "poison_fern_fronds", "Fiddleheads", "6"}, {"nightshade", "Duskberry Bush", "nightshade_berries", "Duskberries", "9"},
        {"hemlock", "Wild Parsley", "hemlock_leaves", "Wild Parsley Sprigs", "8"}, {"deathcap", "Meadow Cap", "deathcap", "Meadow Cap", "4"}, {"oleander", "Rosebay", "oleander_leaves", "Rosebay Leaves", "7"}};
    public static final List<DeferredItem<Item>> PLANT_ITEMS = new ArrayList<>();
    private static void registerPlants() {
        for (String[] pl : PLANTS) {
            DeferredBlock<Block> block = BLOCKS.register(pl[0], () -> new WildPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY), pl[1]));
            if (!pl[0].equals(pl[2])) DECOR.add(ITEMS.registerSimpleBlockItem(pl[0], block));   // the deathcap's drop is the item of that name already
            int taste = Integer.parseInt(pl[4]);
            if (taste > 0) PLANT_ITEMS.add(ITEMS.register(pl[2], () -> { Item it = new PoisonItem(pl[2], pl[3], taste); TASTE.put(it, taste); return it; }));
        }
    }

    /** The taste (0 to 100) of every food and drink that has a fixed one; made-up dishes work theirs out from what is in them. */
    public static final Map<Item, Integer> TASTE = new java.util.HashMap<>();
    public static int tasteOf(ItemStack st) {
        if (st.getItem() instanceof DishItem && st.get(DISH.get()) != null) return st.get(DISH.get()).taste();
        if (st.getItem() instanceof IceCreamItem && st.get(SCOOPS.get()) != null) return st.get(SCOOPS.get()).taste();
        return TASTE.getOrDefault(st.getItem(), -1);
    }
    /** Drinks have no recipe to judge, so their taste comes from what kind of drink they are (a steady spread from their name): secret recipes the best, then wine, mixes, and single juices. */
    private static int drinkTaste(String name, boolean secret, boolean hidden, boolean ferment, boolean golden) {
        int h = Math.floorMod(name.hashCode(), 100);
        if (secret) return 88 + h % 11;
        if (golden) return 80 + h % 12;
        if (ferment) return 70 + h % 20;
        if (hidden) return 58 + h % 24;
        return 52 + h % 22;
    }

    public static final List<DeferredItem<Item>> DRINKS = new ArrayList<>();
    /** The drinks the creative tab shows: not the secret ones. */
    public static final List<DeferredItem<Item>> LISTED = new ArrayList<>();
    /** Jams, pies, muffins, dried fruit and the rest: listed in alterra_kitchen_foods.json (made by tools/food_art.py). */
    public static final List<DeferredItem<Item>> FOODS = new ArrayList<>();
    /** The foods by creative tab section (the "group" of each in alterra_kitchen_foods.json). */
    public static final Map<String, List<DeferredItem<Item>>> GROUPS = new java.util.LinkedHashMap<>();
    /** Dishes made from another mod's drops: only listed in the creative tab when that mod is installed. */
    private static final Map<DeferredItem<Item>, String> NEEDS = new java.util.HashMap<>();
    private static final java.util.Set<String> STATIONS = java.util.Set.of("fruit_press", "keg", "stove", "oven", "kitchen_counter", "prep_table", "ice_cream_stand", "ice_machine", "powered_ice_machine");
    private static final java.util.Set<String> WILD = java.util.Set.of("poison_fern", "nightshade", "hemlock", "oleander", "rock_salt");
    private static String path(Supplier<? extends Item> i) { return BuiltInRegistries.ITEM.getKey(i.get()).getPath(); }
    private static boolean haveNeeded(DeferredItem<Item> i) { String need = NEEDS.get(i); return need == null || net.neoforged.fml.ModList.get().isLoaded(need); }
    public static final List<DeferredItem<BlockItem>> DECOR = new ArrayList<>();
    /** A juice as a fluid: pressed or mixed in Create, piped, bottled by a spout. No placeable block or bucket. */
    public record Juice(String id, int color, DeferredHolder<FluidType, FluidType> type) {}
    public static final List<Juice> JUICES = new ArrayList<>();
    /** What each drink gives besides food (golden drinks): id -> effects, for mixed drinks to carry over. */
    static final Map<String, List<Map<String, Object>>> JUICE_EFFECTS = new java.util.HashMap<>();
    /** A drink from the Drink Mixer: what is in it is on the stack (MixedDrink). Drunk like the others, the glass comes back; it carries over what its golden drinks give, at half the time. */
    public static final DeferredItem<Item> MIXED_DRINK = ITEMS.register("mixed_drink", () -> new Item(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)
            .food(new FoodProperties(4, 2.4f, true, 2.0f, java.util.Optional.of(new ItemStack(Items.GLASS_BOTTLE)), List.of()))) {
        @Override public net.minecraft.network.chat.Component getName(ItemStack st) { MixedDrink m = st.get(MIXED.get()); return m == null || m.drinks().isEmpty() ? net.minecraft.network.chat.Component.literal("Mixed Drink") : m.name(); }
        @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.DRINK; }
        @Override public SoundEvent getDrinkingSound() { return SoundEvents.GENERIC_DRINK; }
        @Override public SoundEvent getEatingSound() { return SoundEvents.GENERIC_DRINK; }
        @Override public ItemStack finishUsingItem(ItemStack st, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity who) {
            MixedDrink m = st.get(MIXED.get());
            if (m != null && !level.isClientSide) for (String d : m.drinks()) for (Map<String, Object> e : JUICE_EFFECTS.getOrDefault(d, List.of()))
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse((String) e.get("id"))).ifPresent(h -> who.addEffect(new MobEffectInstance(h, ((Number) e.get("ticks")).intValue() / 2, ((Number) e.get("amp")).intValue())));
            if (m != null && !level.isClientSide) m.herbEffects(who);
            TasteKnowledge.learn(who, st);
            return super.finishUsingItem(st, level, who);
        }
        @Override public void appendHoverText(ItemStack st, TooltipContext ctx, List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            MixedDrink m = st.get(MIXED.get());
            if (m != null) tip.add(net.minecraft.network.chat.Component.literal(m.drinks().size() + " drinks mixed" + (m.herbs().isEmpty() ? "" : ", with herbs")).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });

    public static final DeferredBlock<Block> FRUIT_PRESS = BLOCKS.register("fruit_press", () -> new FruitPressBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final Supplier<BlockEntityType<FruitPressBlockEntity>> FRUIT_PRESS_BE = BLOCK_ENTITIES.register("fruit_press",
            () -> BlockEntityType.Builder.of(FruitPressBlockEntity::new, FRUIT_PRESS.get()).build(null));

    public static final DeferredBlock<Block> KEG = BLOCKS.register("keg", () -> new KegBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final Supplier<BlockEntityType<KegBlockEntity>> KEG_BE = BLOCK_ENTITIES.register("keg",
            () -> BlockEntityType.Builder.of(KegBlockEntity::new, KEG.get()).build(null));

    /** In the Prism Lib hub (if the library is there) with its logo: found by reflection, Kitchen does not depend on the library. */
    private static void hub() {
        if (!net.neoforged.fml.ModList.get().isLoaded("prismlib")) return;
        try {
            Class.forName("dev.phi.prismlib.hub.PrismHub").getMethod("registerWithTexture", String.class, String.class, String.class, ResourceLocation.class, java.util.function.Function.class)
                    .invoke(null, ID, "Kitchen", "Dishes, tastes and stations", id("textures/gui/hub_logo.png"), null);
        } catch (ReflectiveOperationException | LinkageError e) { org.slf4j.LoggerFactory.getLogger("Alterra Kitchen").warn("Kitchen could not register in the Prism Lib hub: {}", e.toString()); }
    }

    public AlterraKitchen(IEventBus bus) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) hub();
        registerDecor();
        registerJuices();
        registerFoods();
        registerDishes();
        registerPlants();
        DECOR.add(ITEMS.registerSimpleBlockItem("rock_salt", ROCK_SALT));
        DECOR.add(ITEMS.registerSimpleBlockItem("stove", STOVE)); DECOR.add(ITEMS.registerSimpleBlockItem("oven", OVEN)); DECOR.add(ITEMS.registerSimpleBlockItem("kitchen_counter", KITCHEN_COUNTER));
        DECOR.add(ITEMS.registerSimpleBlockItem("prep_table", PREP_TABLE)); DECOR.add(ITEMS.registerSimpleBlockItem("ice_cream_stand", ICE_CREAM_STAND)); DECOR.add(ITEMS.registerSimpleBlockItem("ice_machine", ICE_MACHINE));
        if (net.neoforged.fml.ModList.get().isLoaded("create")) CreateCompat.init(bus);
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> e.registrar("1").optional()
                .playToServer(NameDish.TYPE, NameDish.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player().containerMenu instanceof AssemblyMenu m) m.name(p.name()); })));   // the powered ice cream machine
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {   // funnels, hoppers and belts can feed and empty the stove, oven and ice cream machine
            for (Supplier<BlockEntityType<KitchenFurnaceEntity>> t : List.of(STOVE_BE, OVEN_BE, ICE_MACHINE_BE))
                e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, t.get(), (be, side) -> new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        });
        COMPONENTS.register(bus); RECIPE_SERIALIZERS.register(bus); RECIPE_TYPES.register(bus); MENUS.register(bus);
        KitchenEffects.EFFECTS.register(bus);
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); FLUID_TYPES.register(bus); FLUIDS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) { LISTED.forEach(i -> e.accept(i.get())); PLANT_ITEMS.forEach(i -> e.accept(i.get())); FOODS.forEach(i -> { if (haveNeeded(i)) e.accept(i.get()); }); DISH_ITEMS.values().forEach(i -> e.accept(i.get())); e.accept(ICE_CREAM_STACK.get()); }
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) DECOR.forEach(i -> e.accept(i.get()));
        });
        AlterraApi.addTabSection(id("drinks"), 0xFFFFF4E8, () -> LISTED.stream().map(d -> new ItemStack(d.get())).toList());
        String[][] sections = {{"preserves", "0xFFF6D6D0"}, {"baking", "0xFFFBE3C0"}, {"frozen", "0xFFD8ECF4"}, {"meals", "0xFFF2D8B8"}, {"pantry", "0xFFE8EDD0"}, {"seasonings", "0xFFEEE6D0"}};
        for (String[] sec : sections) AlterraApi.addTabSection(id(sec[0]), (int) Long.decode(sec[1]).longValue(), () -> GROUPS.getOrDefault(sec[0], List.of()).stream().filter(AlterraKitchen::haveNeeded).map(d -> new ItemStack(d.get())).toList());
        AlterraApi.addTabSection(id("wild"), 0xFFDCE8C8, () -> java.util.stream.Stream.concat(PLANT_ITEMS.stream(), DECOR.stream().filter(d -> WILD.contains(path(d)))).map(d -> new ItemStack(d.get())).toList());
        AlterraApi.addTabSection(id("dishes"), 0xFFF6D6D0, () -> java.util.stream.Stream.concat(DISH_ITEMS.values().stream(), java.util.stream.Stream.of(ICE_CREAM_STACK)).map(d -> new ItemStack(d.get())).toList());   // the plain base of each: made-up dishes only come from a station
        AlterraApi.addTabSection(id("stations"), 0xFFE4E4E8, () -> DECOR.stream().filter(d -> STATIONS.contains(path(d))).map(d -> new ItemStack(d.get())).toList());
        AlterraApi.addTabSection(id("decor"), 0xFFF2E6C8, () -> DECOR.stream().filter(d -> !STATIONS.contains(path(d)) && !WILD.contains(path(d))).map(d -> new ItemStack(d.get())).toList());
    }

    static <T> T read(String file, TypeToken<T> type) {
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
        DECOR.add(ITEMS.registerSimpleBlockItem("keg", KEG));
        block("trellis", () -> new TrellisBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
        for (String crate : read("alterra_kitchen_crates.json", new TypeToken<List<String>>() {}))
            block(crate, () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    private static void registerDishes() {
        for (Map<String, Object> d : read("alterra_kitchen_dishes.json", new TypeToken<List<Map<String, Object>>>() {})) {
            String id = (String) d.get("id"), title = (String) d.get("name"); int nutrition = ((Number) d.get("nutrition")).intValue();
            DISH_ITEMS.put(id.substring("dish_".length()), ITEMS.register(id, () -> new DishItem(title, nutrition)));
            DISH_ZONES.put(id, d.get("zones") instanceof Number z ? z.intValue() : 0);
            DISH_META.put(id, d);
        }
    }

    /** Foods from the json list: each has a name, nutrition, saturation, eating time and optionally what is left in your hands (a bottle or bowl). No potion effects. */
    private static void registerFoods() {
        List<Map<String, Object>> list = read("alterra_kitchen_foods.json", new TypeToken<List<Map<String, Object>>>() {});
        for (Map<String, Object> f : list) {
            String name = (String) f.get("id"), title = (String) f.get("name");
            if (Boolean.TRUE.equals(f.get("ingredient"))) {   // raw pasta and the like: not edible
                int st = f.get("stack") instanceof Number n ? n.intValue() : 64;
                DeferredItem<Item> ing = ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(st)) {
                    @Override public net.minecraft.network.chat.Component getName(ItemStack s) { return net.minecraft.network.chat.Component.literal(title); }
                });
                FOODS.add(ing); GROUPS.computeIfAbsent((String) f.getOrDefault("group", "pantry"), k -> new ArrayList<>()).add(ing);
                continue;
            }
            int nutrition = ((Number) f.get("nutrition")).intValue(), stack = f.get("stack") instanceof Number n ? n.intValue() : 64;
            float saturation = ((Number) f.get("saturation")).floatValue(), eat = f.get("eat") instanceof Number n ? n.floatValue() : 1.6f;
            int taste = f.get("taste") instanceof Number tn ? tn.intValue() : -1;
            Item left = f.get("returns") instanceof String r ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(r)) : null;
            DeferredItem<Item> dish = ITEMS.register(name, () -> {
                FoodProperties.Builder fb = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation);
                if (left != null) fb.usingConvertsTo(left);
                FoodProperties base = fb.build();
                FoodProperties food = new FoodProperties(base.nutrition(), base.saturation(), false, eat, base.usingConvertsTo(), base.effects());
                Item.Properties p = new Item.Properties().food(food).stacksTo(stack);
                if (left != null) p.craftRemainder(left);
                Item it = new Item(p) {
                    @Override public net.minecraft.network.chat.Component getName(ItemStack st) { return net.minecraft.network.chat.Component.literal(title); }
                    @Override public ItemStack finishUsingItem(ItemStack st, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity who) { TasteKnowledge.learn(who, st); return super.finishUsingItem(st, level, who); }
                    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack st) {
                        return taste >= 0 ? java.util.Optional.of(new DishCard(taste, nutrition, saturation, 0, 0, null, ClientKnowledge.known(st), false)) : java.util.Optional.empty();
                    }
                };
                if (taste >= 0) TASTE.put(it, taste);
                return it;
            });
            FOODS.add(dish); GROUPS.computeIfAbsent((String) f.getOrDefault("group", "meals"), k -> new ArrayList<>()).add(dish);
            if (f.get("needs") instanceof String mod) NEEDS.put(dish, mod);
        }
    }

    /** Bottled juices: drunk like honey (always drinkable, the glass bottle comes back), plus the same juice as a fluid. */
    @SuppressWarnings("unchecked")
    private static void registerJuices() {
        List<Map<String, Object>> list = read("alterra_kitchen_juices.json", new TypeToken<List<Map<String, Object>>>() {});
        for (Map<String, Object> j : list) {
            String name = (String) j.get("id");
            int color = ((Number) j.get("color")).intValue();
            int nutrition = j.get("nutrition") instanceof Number n ? n.intValue() : 3;
            float saturation = j.get("saturation") instanceof Number n ? n.floatValue() : 0.4f;
            boolean foil = Boolean.TRUE.equals(j.get("foil"));
            float thirst = j.get("thirst") instanceof Number n ? n.floatValue() : 0f;   // wine and ale: a chance of Legendary Survival Overhaul's thirst
            List<Map<String, Object>> effects = j.get("effects") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            JUICE_EFFECTS.put(name, effects);
            boolean secret = Boolean.TRUE.equals(j.get("secret"));
            DeferredItem<Item> drink = ITEMS.register(name, () -> {
                FoodProperties.Builder fb = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE);
                for (Map<String, Object> e : effects) {
                    var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse((String) e.get("id")));
                    if (effect.isPresent()) fb.effect(() -> new MobEffectInstance(effect.get(), ((Number) e.get("ticks")).intValue(), ((Number) e.get("amp")).intValue()), 1.0f);
                }
                FoodProperties base = fb.build();
                FoodProperties food = new FoodProperties(base.nutrition(), base.saturation(), base.canAlwaysEat(), 2.0f, base.usingConvertsTo(), base.effects());   // drunk in 2 s
                int taste = drinkTaste(name, secret, Boolean.TRUE.equals(j.get("hidden")), thirst > 0, name.startsWith("golden_") || name.startsWith("enchanted_golden"));
                Item drinkItem = new Item(new Item.Properties().food(food).stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)) {
                    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack st) {
                        return java.util.Optional.of(new DishCard(taste, nutrition, saturation, LsoStats.hydration(name), LsoStats.temperature(name), null, ClientKnowledge.known(st), false));   // thirst and temperature from Legendary Survival Overhaul's data
                    }
                    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.DRINK; }
                    @Override public SoundEvent getDrinkingSound() { return SoundEvents.GENERIC_DRINK; }
                    @Override public SoundEvent getEatingSound() { return SoundEvents.GENERIC_DRINK; }
                    @Override public boolean isFoil(ItemStack st) { return foil || super.isFoil(st); }
                    @Override public ItemStack finishUsingItem(ItemStack st, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity who) {
                        TasteKnowledge.learn(who, st);
                        if (!level.isClientSide && thirst > 0 && AlterraApi.compat("lsoWineThirst") && level.random.nextFloat() < thirst * AlterraApi.number("lsoWineChance", 100) / 100f)
                            BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("legendarysurvivaloverhaul:thirst")).ifPresent(h -> who.addEffect(new MobEffectInstance(h, 500, 0)));
                        return super.finishUsingItem(st, level, who);
                    }
                };
                TASTE.put(drinkItem, taste);
                return drinkItem;
            });
            DRINKS.add(drink);
            if (!secret) LISTED.add(drink);
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
