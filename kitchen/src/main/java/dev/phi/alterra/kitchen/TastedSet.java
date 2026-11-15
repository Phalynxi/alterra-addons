package dev.phi.alterra.kitchen;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.List;

/** What a player has tasted: a key for every food, drink and dish they have eaten (see TasteKnowledge). Kept with the player, copied on death and sent to the client. */
public record TastedSet(List<Integer> keys) {
    public static final Codec<TastedSet> CODEC = Codec.INT.listOf().xmap(TastedSet::new, TastedSet::keys);
    public static final StreamCodec<RegistryFriendlyByteBuf, TastedSet> STREAM_CODEC = TastedSet.<RegistryFriendlyByteBuf>codec().cast();
    private static <B extends io.netty.buffer.ByteBuf> StreamCodec<B, TastedSet> codec() { return ByteBufCodecs.VAR_INT.<B>cast().apply(ByteBufCodecs.list()).map(TastedSet::new, TastedSet::keys); }
}
