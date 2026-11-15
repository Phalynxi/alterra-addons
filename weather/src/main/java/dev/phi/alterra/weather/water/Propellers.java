package dev.phi.alterra.weather.water;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create Aeronautics' propellers blow on the water like rotor blades: where the air they push meets the water, it ripples and throws spray, more the faster they turn and the lower they are.
 * Aeronautics is not a dependency, so its propeller contraption entities are found by their class name and read by reflection (the contraption's anchor is the bearing, whose speed and facing say how it turns);
 * if anything about them looks different than expected this quietly does nothing.
 */
public final class Propellers {
    private Propellers() {}
    private static boolean failed;

    public static void tick(ClientLevel level, Vec3 cam) {
        if (failed || level.getGameTime() % 3 != 0) return;
        try {
            for (Entity en : level.getEntitiesOfClass(Entity.class, AABB.ofSize(cam, 80, 60, 80), x -> x.getClass().getName().contains("PropellerBearingContraptionEntity"))) {
                Object contraption = en.getClass().getMethod("getContraption").invoke(en);
                BlockPos anchor = (BlockPos) contraption.getClass().getField("anchor").get(contraption);
                var be = level.getBlockEntity(anchor);
                if (be == null) continue;
                float rpm = Math.abs((float) be.getClass().getMethod("getSpeed").invoke(be));
                if (rpm < 8f) continue;
                var state = be.getBlockState();
                Direction axis = state.getOptionalValue(BlockStateProperties.FACING).orElse(state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.UP));
                // the air is pushed along the axis of the bearing: straight down for a rotor, sideways for a propeller on its side (then its wash lands a few blocks to the side of it)
                Vec3 hub = en.position(), target = axis.getAxis() == Direction.Axis.Y ? hub : hub.add(axis.getStepX() * 4.0, 0, axis.getStepZ() * 4.0);
                int surface = waterBelow(level, target);
                if (surface == Integer.MIN_VALUE) continue;
                double h = hub.y - surface;
                if (h > 26) continue;
                float power = Math.min(1f, rpm / 128f) / (1f + (float) Math.max(0, h) / 4f);
                if (power < 0.04f) continue;
                var r = level.random;
                if (r.nextFloat() < 0.8f) { double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * (1.0 + h * 0.25); Ripples.add(target.x + Math.cos(a) * d, target.z + Math.sin(a) * d, Math.min(1f, power * 1.3f)); }
                if (r.nextFloat() < power * 0.6f) { double aa = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * (0.8 + h * 0.2); Splashes.add(target.x + Math.cos(aa) * d, surface + 0.95, target.z + Math.sin(aa) * d, 0.2 + power * 0.7); }   // sheets of water thrown up where the wash lands
            }
        } catch (Throwable t) { failed = true; }
    }

    /** The y of the water's top block under a point (within 30 blocks), or MIN_VALUE if there is none. */
    private static int waterBelow(ClientLevel level, Vec3 p) {
        var pos = new BlockPos.MutableBlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
        for (int i = 0; i < 30; i++) {
            var fs = level.getFluidState(pos);
            if (fs.is(net.minecraft.tags.FluidTags.WATER) && level.getFluidState(pos.above()).isEmpty()) return pos.getY();
            if (!level.getBlockState(pos).isAir() && fs.isEmpty()) return Integer.MIN_VALUE;   // land first
            pos.move(0, -1, 0);
        }
        return Integer.MIN_VALUE;
    }
}
