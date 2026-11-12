package dev.phi.alterra.farmersdelight;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredRegister;
import vectorwing.farmersdelight.common.item.KnifeItem;

/**
 * The Alterra knives (Crystal, Ember, Frost) for Farmer's Delight. Recipes, cutting-board recipes and tags are data files.
 */
public final class KnifeItems {
    private KnifeItems() {}

    public static void register(DeferredRegister.Items items) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(KnifeItems::onDrops);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(KnifeItems::onItemSpawn);
        items.register("crystal_knife", () -> new KnifeItem(Tiers.IRON, knife(Tiers.IRON, 0.5f, -1.8f)));   // a little quicker than iron
        items.register("ember_knife", () -> new EffectKnife(Tiers.IRON, knife(Tiers.IRON, 0.5f, -2.0f), true));
        items.register("frost_knife", () -> new EffectKnife(Tiers.IRON, knife(Tiers.IRON, 0.5f, -2.0f), false));
    }

    private static Item.Properties knife(Tiers tier, float dmg, float speed) {
        return new Item.Properties().attributes(net.minecraft.world.item.DiggerItem.createAttributes(tier, dmg, speed));
    }

    private static boolean emberKnife(ItemStack s) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("alterra_farmersdelight:ember_knife");
    }

    /** The cooked version of a stack from any mod: its furnace recipe, else its smoker recipe, else its campfire recipe; the stack itself
     *  if none of them gives food (so ores, sand or logs are never "cooked"). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack cooked(net.minecraft.world.level.Level level, ItemStack s) {
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(s);
        net.minecraft.world.item.crafting.RecipeType[] types = {net.minecraft.world.item.crafting.RecipeType.SMELTING,
                net.minecraft.world.item.crafting.RecipeType.SMOKING, net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING};
        for (var type : types) {
            java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.AbstractCookingRecipe>> r =
                    level.getRecipeManager().getRecipeFor(type, input, level);
            if (r.isEmpty()) continue;
            ItemStack out = r.get().value().assemble(input, level.registryAccess()).copy();
            if (out.isEmpty() || !out.has(net.minecraft.core.component.DataComponents.FOOD)) continue;
            out.setCount(s.getCount());
            return out;
        }
        return s;
    }

    /** Mobs killed with the Ember Knife drop their meat cooked. */
    private static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) {
        if (!FdConfig.get(FdConfig.EMBER_KNIFE_COOKS)) return;
        if (!(e.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player p) || !emberKnife(p.getMainHandItem())) return;
        for (var drop : e.getDrops()) drop.setItem(cooked(drop.level(), drop.getItem()));
    }

    /** Cutting on a Farmer's Delight cutting board with the Ember Knife: the pieces come off the board cooked (raw beef -> beef patties,
     *  porkchop -> cooked bacon, cod -> cooked cod slices ...). The board drops its results as items at the board, so they are caught here. */
    private static void onItemSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity it) || e.loadedFromDisk()) return;
        if (!FdConfig.get(FdConfig.EMBER_KNIFE_COOKS)) return;
        var pos = it.blockPosition();
        boolean board = false;
        for (var q : new net.minecraft.core.BlockPos[] {pos, pos.below()}) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getLevel().getBlockState(q).getBlock()).toString();
            if (id.equals("farmersdelight:cutting_board")) board = true;
        }
        if (!board) return;
        var p = e.getLevel().getNearestPlayer(it, 6);
        if (p == null || !emberKnife(p.getMainHandItem())) return;
        ItemStack c = cooked(e.getLevel(), it.getItem());
        if (c != it.getItem()) it.setItem(c);
    }

    /** Ember Knife sets what it hits on fire (3 s); Frost Knife freezes it (about 3 s, like powder snow). */
    static class EffectKnife extends KnifeItem {
        private final boolean ember;
        EffectKnife(Tiers tier, Item.Properties p, boolean ember) { super(tier, p); this.ember = ember; }
        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (ember) target.igniteForSeconds(3);
            else target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 60));   // freezes it, like powder snow
            return super.hurtEnemy(stack, target, attacker);
        }
    }
}
