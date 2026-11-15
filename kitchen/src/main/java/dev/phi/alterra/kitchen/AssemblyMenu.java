package dev.phi.alterra.kitchen;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

/**
 * How kitchen food is made: you put a base on the table and drop the fillings, seasoning and sauce onto it (or scoops onto a cone), and the dish builds up on the plate as a live picture.
 * What is on the table stays in the block when you close the screen. The prep table makes simple dishes, the kitchen counter all of them, the ice cream stand stacks and twists.
 * Clicks on the plate arrive as menu buttons: 10 drop one carried item, 11 drop it as a twist partner, 1 serve, 2 take everything back, 100 + n take item n back.
 */
public final class AssemblyMenu extends AbstractContainerMenu {
    public enum Role {
        BASE, FILLING, SEASONING, SAUCE, SCOOP, TWIST;
        boolean accepts(ItemStack st) {
            return switch (this) {
                case BASE -> DishRecipe.isBase(st);
                case FILLING -> DishRecipe.isFilling(st);
                case SEASONING -> st.is(DishRecipe.SEASONINGS);
                case SAUCE -> st.is(DishRecipe.SAUCES);
                case SCOOP -> IceCreamStackRecipe.scoopsOf(st) != null;
                case TWIST -> IceCreamStackRecipe.scoopsOf(st) != null && !(st.getItem() instanceof IceCreamItem) && IceCreamStackRecipe.scoopsOf(st).get(0).swirl();
            };
        }
    }

    public enum Layout {
        PREP("Prep Table", 1), COUNTER("Kitchen Counter", 2), STAND("Ice Cream Stand", 2);
        public final String title; final int tier;
        Layout(String title, int tier) { this.title = title; this.tier = tier; }
        /** What each place on the table is for, in the order the dish is read. */
        public List<Role> roles() {
            List<Role> l = new ArrayList<>();
            if (this == STAND) { for (int i = 0; i < Scoops.MAX; i++) l.add(Role.SCOOP); for (int i = 0; i < Scoops.MAX; i++) l.add(Role.TWIST); return l; }
            l.add(Role.BASE);
            for (int i = 0; i < (this == PREP ? 3 : 6); i++) l.add(Role.FILLING);
            l.add(Role.SEASONING);
            if (this == COUNTER) l.add(Role.SAUCE);
            return l;
        }
    }

    public static final int WIDTH = 176, HEIGHT = 206;
    private static final DishRecipe DISH = new DishRecipe(CraftingBookCategory.MISC);
    private final Layout layout; private final ContainerLevelAccess access; private final Block block; private final Player player;
    private final List<Role> roles; private final SimpleContainer in; private final ResultContainer out = new ResultContainer();
    private final net.minecraft.world.ContainerListener listener = c -> slotsChanged(c);

    public AssemblyMenu(int id, Inventory inv, Layout layout, ContainerLevelAccess access, Block block, SimpleContainer table) {
        super(AlterraKitchen.KITCHEN_MENU.get(), id);
        this.layout = layout; this.access = access; this.block = block; this.player = inv.player; this.roles = layout.roles(); this.in = table;
        addSlot(new HiddenSlot(out, 0, false));
        for (int i = 0; i < roles.size(); i++) addSlot(new HiddenSlot(in, i, true));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + 9 * r + c, 8 + 18 * c, 124 + 18 * r));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + 18 * c, 182));
        in.addListener(listener);
        slotsChanged(in);
    }
    public static AssemblyMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        Layout l = Layout.values()[buf.readInt()];
        return new AssemblyMenu(id, inv, l, ContainerLevelAccess.NULL, null, new SimpleContainer(l.roles().size()));
    }
    public Layout layout() { return layout; }
    /** The finished food as it stands (empty until the table holds something that makes one). */
    public ItemStack preview() { return out.getItem(0); }
    public ItemStack table(int i) { return in.getItem(i); }
    public int places() { return roles.size(); }

    /** A slot that holds something but is not drawn or clickable: the plate takes its place on the screen. */
    public static final class HiddenSlot extends Slot {
        private final boolean input;
        HiddenSlot(net.minecraft.world.Container c, int i, boolean input) { super(c, i, -10000, -10000); this.input = input; }
        @Override public boolean isActive() { return false; }
        @Override public boolean mayPlace(ItemStack st) { return false; }
        @Override public boolean mayPickup(Player p) { return false; }
    }

    // ------------------------------------------------------------ the table
    /** Puts one of the item on the table (as a twist partner if asked and it fits); returns whether it went on. */
    private boolean addOne(ItemStack src, boolean twist) {
        int target = -1;
        if (twist) {
            for (int i = Scoops.MAX - 1; i >= 0 && target < 0; i--)
                if (layout == Layout.STAND && !in.getItem(i).isEmpty() && in.getItem(Scoops.MAX + i).isEmpty() && IceCreamStackRecipe.twistOf(in.getItem(i), src) != null) target = Scoops.MAX + i;
        } else {
            for (int i = 0; i < roles.size() && target < 0; i++)
                if (roles.get(i) != Role.TWIST && in.getItem(i).isEmpty() && roles.get(i).accepts(src)) target = i;
        }
        if (target < 0) return false;
        in.setItem(target, src.split(1));
        return true;
    }
    private void giveBack(ItemStack st) { if (!st.isEmpty()) player.getInventory().placeItemBackInInventory(st); }

    @Override public boolean clickMenuButton(Player p, int id) {
        if (id == 10 || id == 11) {
            ItemStack carried = getCarried();
            return !carried.isEmpty() && addOne(carried, id == 11);
        }
        if (id == 1) {
            ItemStack made = out.getItem(0).copy();
            if (made.isEmpty()) return false;
            int used = 0;
            for (int i = 0; i < in.getContainerSize(); i++) if (!in.getItem(i).isEmpty()) used++;
            in.clearContent();
            giveBack(made);
            if (layout == Layout.STAND && used > 1) giveBack(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "wafer_cone")), used - 1));   // the cones that are not needed
            return true;
        }
        if (id == 2) { for (int i = 0; i < in.getContainerSize(); i++) giveBack(in.removeItemNoUpdate(i)); in.setChanged(); return true; }
        int n = id - 100;
        if (n >= 0 && n < in.getContainerSize() && !in.getItem(n).isEmpty()) {
            giveBack(in.removeItemNoUpdate(n)); in.setChanged();
            return true;
        }
        return false;
    }

    @Override public void slotsChanged(net.minecraft.world.Container c) {
        if (player.level().isClientSide) return;
        out.setItem(0, DishNames.named(player, Stations.at(layout.tier, this::build)));   // with the name the player gave this dish before, if any
        broadcastChanges();
    }

    private ItemStack build() { return layout == Layout.STAND ? buildStand() : buildDish(); }
    private ItemStack buildDish() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < in.getContainerSize(); i++) if (!in.getItem(i).isEmpty()) all.add(in.getItem(i));
        if (all.isEmpty()) return ItemStack.EMPTY;
        CraftingInput input = CraftingInput.of(all.size(), 1, all);
        return DISH.matches(input, player.level()) ? DISH.assemble(input, player.level().registryAccess()) : ItemStack.EMPTY;
    }
    private ItemStack buildStand() {
        List<Scoops.Scoop> all = new ArrayList<>(); boolean twist = false;
        for (int i = 0; i < Scoops.MAX; i++) {
            ItemStack main = in.getItem(i), partner = in.getItem(Scoops.MAX + i);
            if (main.isEmpty()) { if (!partner.isEmpty()) return ItemStack.EMPTY; continue; }
            if (!partner.isEmpty()) {
                Scoops.Scoop t = IceCreamStackRecipe.twistOf(main, partner);
                if (t == null) return ItemStack.EMPTY;
                all.add(t); twist = true;
            } else all.addAll(IceCreamStackRecipe.scoopsOf(main));
        }
        return all.size() > Scoops.MAX || (all.size() < 2 && !twist) ? ItemStack.EMPTY : IceCreamStackRecipe.make(all);
    }

    /** The name typed on the screen for what is on the plate: saved for this dish (empty forgets it) and put on it. */
    public void name(String name) {
        ItemStack made = out.getItem(0);
        if (made.isEmpty()) return;
        DishNames.save(player, made, name);
        ItemStack fresh = Stations.at(layout.tier, this::build);
        out.setItem(0, DishNames.named(player, fresh));
        broadcastChanges();
    }

    @Override public void removed(Player p) { super.removed(p); in.removeListener(listener); }   // the table keeps what is on it
    @Override public boolean stillValid(Player p) { return stillValid(access, p, block); }

    /** Shift-clicking an item in your bag puts it on the table. */
    @Override public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || index <= roles.size()) return ItemStack.EMPTY;
        if (addOne(slot.getItem(), false)) slot.setChanged();
        return ItemStack.EMPTY;
    }
}
