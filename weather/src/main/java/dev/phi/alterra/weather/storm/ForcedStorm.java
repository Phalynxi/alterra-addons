package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;

import dev.phi.alterra.weather.storm.Storms.Kind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to clients: a storm that /weather switched on (kind, game time it lasts until; until 0 means it was switched off). */
public record ForcedStorm(int kind, long until) implements CustomPacketPayload {
    public static final Type<ForcedStorm> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "forced_storm"));
    public static final StreamCodec<ByteBuf, ForcedStorm> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ForcedStorm::kind, ByteBufCodecs.VAR_LONG, ForcedStorm::until, ForcedStorm::new);
    /** The kind number that stands for the heavy thunderstorm. */
    public static final int HEAVY = 100;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void apply(ForcedStorm p) {
        if (p.kind == HEAVY) { HeavyStorm.forcedUntil = Math.max(0, p.until); return; }
        Kind k = Kind.values()[Math.floorMod(p.kind, Kind.values().length)];
        if (p.until <= 0) Storms.FORCED_UNTIL.remove(k); else Storms.FORCED_UNTIL.put(k, p.until);
    }
}
