package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: this player's game is (or no longer is) drawing wet surfaces and puddles, so the server should (or should stop) working out and sending how wet it is around them. */
public record WetSubscribe(boolean on) implements CustomPacketPayload {
    public static final Type<WetSubscribe> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "wet_subscribe"));
    public static final StreamCodec<ByteBuf, WetSubscribe> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, WetSubscribe::on, WetSubscribe::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
