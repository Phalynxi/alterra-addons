package dev.phi.alterra.weather.net;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to a subscribed client: how wet the world is around that player (0 to 1). */
public record WetSync(float wet) implements CustomPacketPayload {
    public static final Type<WetSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "wet_sync"));
    public static final StreamCodec<ByteBuf, WetSync> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, WetSync::wet, WetSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
