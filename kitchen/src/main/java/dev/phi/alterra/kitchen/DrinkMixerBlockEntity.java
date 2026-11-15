package dev.phi.alterra.kitchen;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The Drink Mixer: a Create basin with four drink tanks instead of two. With three or four different drinks in it and a glass bottle, a mechanical mixer above it
 * mixes them a bottle at a time (250 mB in all, shared evenly) into a mixed drink that is the same every time for the same drinks (see MixedDrink).
 * With two drinks it is an ordinary basin and the usual mixes apply.
 */
public class DrinkMixerBlockEntity extends BasinBlockEntity {
    public DrinkMixerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.remove(inputTank);
        inputTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 4, 1000, true).whenFluidUpdates(this::notifyChangeOfContents);
        behaviours.add(inputTank);
        fluidCapability = new CombinedTankWrapper(outputTank.getCapability(), inputTank.getCapability());
    }

    public net.neoforged.neoforge.items.IItemHandler items() { return itemCapability; }
    public IFluidHandler fluids() { return fluidCapability; }

    /** The recipe the mixer runs while it mixes drinks here: a stand-in, the work is done by {@link #mixOnce()}. */
    public static final Recipe<RecipeInput> MARKER = new Recipe<>() {
        @Override public boolean matches(RecipeInput in, net.minecraft.world.level.Level l) { return false; }
        @Override public ItemStack assemble(RecipeInput in, net.minecraft.core.HolderLookup.Provider p) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int w, int h) { return true; }
        @Override public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider p) { return new ItemStack(AlterraKitchen.MIXED_DRINK.get()); }
        @Override public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() { return net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE; }
        @Override public net.minecraft.world.item.crafting.RecipeType<?> getType() { return net.minecraft.world.item.crafting.RecipeType.CRAFTING; }
    };

    private record Plan(List<String> drinks, List<FluidStack> take, int bottleSlot, List<Integer> herbSlots, List<String> herbs) {}

    /** What one bottle would take from a basin (any basin): every drink in it, at least {@code min} different ones, an even share of 250 mB each, and a glass bottle. */
    private static Plan plan(BasinBlockEntity basin, int min) {
        List<String> ids = new ArrayList<>(); List<FluidStack> held = new ArrayList<>();
        for (var seg : basin.inputTank.getTanks()) {
            FluidStack fs = seg.getRenderedFluid();
            if (fs.isEmpty()) continue;
            var key = BuiltInRegistries.FLUID.getKey(fs.getFluid());
            if (!key.getNamespace().equals(AlterraKitchen.ID) || AlterraKitchen.JUICES.stream().noneMatch(j -> j.id().equals(key.getPath()))) return null;   // only drinks
            if (ids.contains(key.getPath())) continue;
            ids.add(key.getPath()); held.add(fs);
        }
        if (ids.size() < min) return null;
        int share = (250 + ids.size() - 1) / ids.size();
        List<FluidStack> take = new ArrayList<>();
        for (FluidStack fs : held) { if (fs.getAmount() < share) return null; take.add(fs.copyWithAmount(share)); }
        var inv = basin.getInputInventory(); int slot = -1;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(Items.GLASS_BOTTLE)) { slot = i; break; }
        if (slot < 0) return null;
        List<Integer> hs = new ArrayList<>(); List<String> herbs = new ArrayList<>();
        for (int i = 0; i < inv.getSlots() && hs.size() < 3; i++) if (MixedDrink.isHerb(inv.getStackInSlot(i))) { hs.add(i); herbs.add(MixedDrink.herbId(inv.getStackInSlot(i))); }   // herbs in the basin go in too
        return new Plan(ids, take, slot, hs, herbs);
    }

    /** Can this basin mix a drink: the Drink Mixer always with three or four drinks; any basin with two when no written recipe takes them. */
    public static boolean canMix(BasinBlockEntity basin, int min) { return plan(basin, min) != null; }
    public boolean canMix() { return canMix(this, 3); }

    /** Mixes one bottle in a basin; false if it could not (nothing to mix, or no room for the drink). */
    public static boolean mixOnce(BasinBlockEntity basin) {
        Plan p = plan(basin, 2);
        if (p == null) return false;
        ItemStack out = new ItemStack(AlterraKitchen.MIXED_DRINK.get());
        out.set(AlterraKitchen.MIXED.get(), MixedDrink.of(p.drinks(), p.herbs()));
        if (!basin.acceptOutputs(List.of(out.copy()), List.of(), true)) return false;
        IFluidHandler tanks = basin.inputTank.getCapability();
        for (FluidStack fs : p.take()) tanks.drain(fs, IFluidHandler.FluidAction.EXECUTE);
        basin.getInputInventory().extractItem(p.bottleSlot(), 1, false);
        for (int hs : p.herbSlots()) basin.getInputInventory().extractItem(hs, 1, false);
        basin.acceptOutputs(List.of(out), List.of(), false);
        basin.notifyChangeOfContents();
        return true;
    }
}
