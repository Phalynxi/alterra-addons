package dev.phi.alterra.kitchen;

import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import java.util.function.Supplier;

/** Everything that needs Create's classes: only touched when Create is installed (see the AlterraKitchen constructor). */
final class CreateCompat {
    /** Client: the Drink Mixer is drawn like a basin (its contents, fluids and items). */
    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) { e.registerBlockEntityRenderer(MIXER_BE.get(), com.simibubi.create.content.processing.basin.BasinRenderer::new); }

    private CreateCompat() {}
    static final DeferredBlock<Block> BLOCK = AlterraKitchen.BLOCKS.register("powered_ice_machine", () -> new PoweredIceMachineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .lightLevel(st -> st.getValue(PoweredIceMachineBlock.LIT) ? 4 : 0)));
    static final Supplier<BlockEntityType<PoweredIceMachineEntity>> BE = AlterraKitchen.BLOCK_ENTITIES.register("powered_ice_machine", () -> BlockEntityType.Builder.of((pos, st) -> new PoweredIceMachineEntity(CreateCompat.BE.get(), pos, st), BLOCK.get()).build(null));

    static final DeferredBlock<Block> MIXER = AlterraKitchen.BLOCKS.register("drink_mixer", () -> new DrinkMixerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.5F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()));
    static final Supplier<BlockEntityType<DrinkMixerBlockEntity>> MIXER_BE = AlterraKitchen.BLOCK_ENTITIES.register("drink_mixer", () -> BlockEntityType.Builder.of((pos, st) -> new DrinkMixerBlockEntity(CreateCompat.MIXER_BE.get(), pos, st), MIXER.get()).build(null));

    static void init(IEventBus bus) {
        AlterraKitchen.DECOR.add(AlterraKitchen.ITEMS.registerSimpleBlockItem("drink_mixer", MIXER));
        bus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MIXER_BE.get(), (be, side) -> be.items());
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MIXER_BE.get(), (be, side) -> be.fluids());
        });
        AlterraKitchen.DECOR.add(AlterraKitchen.ITEMS.registerSimpleBlockItem("powered_ice_machine", BLOCK));
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> BlockStressValues.IMPACTS.register(BLOCK.get(), () -> 4.0)));   // 4 su at 1 rpm: a mechanical mixer needs 4 too
        bus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BE.get(), (be, side) -> new SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side)));
    }
}
