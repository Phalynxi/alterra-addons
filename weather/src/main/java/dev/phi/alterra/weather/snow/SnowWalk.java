package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.AlterraWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Snow is soft, not a wall: standing on or in it, a player steps up a whole block without jumping (the game's snow layers are collision blocks, and its step height of 0.6 made
 * every deep drift a climb). The bigger step height is a transient attribute modifier, on only while there is snow at the feet, so everywhere else walking is the game's own.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class SnowWalk {
    private SnowWalk() {}
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "snow_walk");
    private static final AttributeModifier STEP = new AttributeModifier(ID, 0.6, AttributeModifier.Operation.ADD_VALUE);

    private static boolean snowy(net.minecraft.world.level.Level l, BlockPos p) {
        var s = l.getBlockState(p);
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 4 != 0) return;
        var a = p.getAttribute(Attributes.STEP_HEIGHT); if (a == null) return;
        BlockPos at = p.blockPosition();
        boolean on = snowy(p.level(), at) || snowy(p.level(), at.below()) || snowy(p.level(), at.relative(p.getDirection())) || snowy(p.level(), at.relative(p.getDirection()).below());
        if (on) { if (!a.hasModifier(ID)) a.addTransientModifier(STEP); }
        else if (a.hasModifier(ID)) a.removeModifier(ID);
    }
}
