package dev.phi.alterra.weather.net;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the storm surge around a player, as a square of water-grid cells (cell x, z of its corner; one signed byte per cell, in hundredths of a block). */
public record SeaSync(int cx, int cz, byte[] h) implements CustomPacketPayload {
    public static final int N = 48;
    public static final Type<SeaSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "sea_sync"));
    public static final StreamCodec<ByteBuf, SeaSync> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SeaSync::cx, ByteBufCodecs.VAR_INT, SeaSync::cz, ByteBufCodecs.BYTE_ARRAY, SeaSync::h, SeaSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
