package dev.phi.alterra.weather.net;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to a client that draws the water effects: an explosion went off here (position and radius), so the water nearby can throw up a splash and rings. */
public record BlastSync(double x, double y, double z, float radius) implements CustomPacketPayload {
    public static final Type<BlastSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "blast_sync"));
    public static final StreamCodec<ByteBuf, BlastSync> CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, BlastSync::x, ByteBufCodecs.DOUBLE, BlastSync::y, ByteBufCodecs.DOUBLE, BlastSync::z, ByteBufCodecs.FLOAT, BlastSync::radius, BlastSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
