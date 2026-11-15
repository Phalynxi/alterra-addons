package dev.phi.alterra.weather.net;

import dev.phi.alterra.weather.AlterraWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: precise water amounts that changed nearby (block positions, and per position the amount in 120ths of a block, or -1 when it has settled). */
public record WaterSync(long[] pos, byte[] amount) implements CustomPacketPayload {
    public static final Type<WaterSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "water_sync"));
    public static final StreamCodec<ByteBuf, WaterSync> CODEC = StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY.map(WaterSync::longs, WaterSync::bytes), WaterSync::pos, ByteBufCodecs.BYTE_ARRAY, WaterSync::amount, WaterSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static long[] longs(byte[] b) { java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b); long[] l = new long[b.length / 8]; for (int i = 0; i < l.length; i++) l[i] = bb.getLong(); return l; }
    private static byte[] bytes(long[] l) { java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(l.length * 8); for (long v : l) bb.putLong(v); return bb.array(); }
}
