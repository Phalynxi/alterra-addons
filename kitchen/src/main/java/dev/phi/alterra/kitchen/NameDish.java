package dev.phi.alterra.kitchen;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the name typed on a kitchen station's screen for the dish on the plate (empty: use the made-up name). */
public record NameDish(String name) implements CustomPacketPayload {
    public static final Type<NameDish> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "name_dish"));
    public static final StreamCodec<ByteBuf, NameDish> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(48), NameDish::name, NameDish::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
