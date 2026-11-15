package dev.phi.alterra.weather.net;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to the clients near a disaster: for each one eight numbers (id, type, x, y, z, radius, height, strength). Sent every other tick while any is active. */
public record DisasterSync(List<Float> v) implements CustomPacketPayload {
    public static final Type<DisasterSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "disaster_sync"));
    public static final StreamCodec<ByteBuf, DisasterSync> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(64)), DisasterSync::v, DisasterSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
