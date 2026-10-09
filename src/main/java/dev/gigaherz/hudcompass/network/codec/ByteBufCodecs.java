package dev.gigaherz.hudcompass.network.codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * The handful of primitive stream codecs this mod uses, standing in for 1.20.5+'s vanilla
 * {@code ByteBufCodecs} (plus {@code UUIDUtil.STREAM_CODEC}, {@code ComponentSerialization.STREAM_CODEC}
 * and {@code ResourceLocation.STREAM_CODEC}, gathered here as {@link #UUID}, {@link #COMPONENT} and
 * {@link #RESOURCE_LOCATION}). All built on {@link FriendlyByteBuf}'s own read/write methods. See
 * {@link StreamCodec}.
 */
public final class ByteBufCodecs
{
    private ByteBufCodecs()
    {
    }

    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Float> FLOAT = StreamCodec.of(FriendlyByteBuf::writeFloat, FriendlyByteBuf::readFloat);
    public static final StreamCodec<FriendlyByteBuf, Double> DOUBLE = StreamCodec.of(FriendlyByteBuf::writeDouble, FriendlyByteBuf::readDouble);
    public static final StreamCodec<FriendlyByteBuf, UUID> UUID = StreamCodec.of(FriendlyByteBuf::writeUUID, FriendlyByteBuf::readUUID);
    public static final StreamCodec<FriendlyByteBuf, Component> COMPONENT = StreamCodec.of(FriendlyByteBuf::writeComponent, FriendlyByteBuf::readComponent);
    public static final StreamCodec<FriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION = StreamCodec.of(FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::readResourceLocation);

    public static StreamCodec<FriendlyByteBuf, String> stringUtf8(int maxLength)
    {
        return StreamCodec.of((buf, value) -> buf.writeUtf(value, maxLength), buf -> buf.readUtf(maxLength));
    }

    public static StreamCodec<FriendlyByteBuf, byte[]> byteArray(int maxLength)
    {
        return StreamCodec.of(FriendlyByteBuf::writeByteArray, buf -> buf.readByteArray(maxLength));
    }

    public static <V> StreamCodec<FriendlyByteBuf, Optional<V>> optional(StreamCodec<FriendlyByteBuf, V> codec)
    {
        return StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.isPresent());
                    value.ifPresent(v -> codec.encode(buf, v));
                },
                buf -> buf.readBoolean() ? Optional.of(codec.decode(buf)) : Optional.empty());
    }
}
