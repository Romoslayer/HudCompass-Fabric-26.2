package dev.gigaherz.hudcompass.icons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A lightweight, loader-agnostic replacement for upstream's NeoForge-registry-backed
 * {@code IconDataSerializer}. Dispatches by name rather than a NeoForge registry ID, both for
 * NBT (disk/attachment-equivalent storage) and now for the network stream codec used by
 * multiplayer waypoint sync.
 */
public record IconDataSerializer<T extends IIconData<T>>(String name, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
{
    private static final Map<String, IconDataSerializer<?>> BY_NAME = new LinkedHashMap<>();
    private static final Map<IconDataSerializer<?>, String> NAMES = new IdentityHashMap<>();

    public static <T extends IIconData<T>> IconDataSerializer<T> register(String name, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
    {
        IconDataSerializer<T> serializer = new IconDataSerializer<>(name, codec, streamCodec);
        BY_NAME.put(name, serializer);
        NAMES.put(serializer, name);
        return serializer;
    }

    @Nullable
    public static IconDataSerializer<?> byName(String name)
    {
        return BY_NAME.get(name);
    }

    public static final Codec<IconDataSerializer<?>> BY_ID_CODEC = Codec.STRING.flatXmap(
            name -> {
                IconDataSerializer<?> serializer = BY_NAME.get(name);
                return serializer != null
                        ? DataResult.success(serializer)
                        : DataResult.error(() -> "Unknown icon data serializer: " + name);
            },
            serializer -> {
                String name = NAMES.get(serializer);
                return name != null
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unregistered icon data serializer");
            }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, IconDataSerializer<?>> BY_ID_STREAM_CODEC = StreamCodec.of(
            (buf, serializer) -> {
                String name = NAMES.get(serializer);
                if (name == null)
                    throw new IllegalArgumentException("Unregistered icon data serializer");
                ByteBufCodecs.stringUtf8(256).encode(buf, name);
            },
            buf -> {
                String name = ByteBufCodecs.stringUtf8(256).decode(buf);
                IconDataSerializer<?> serializer = BY_NAME.get(name);
                if (serializer == null)
                    throw new IllegalArgumentException("Unknown icon data serializer: " + name);
                return serializer;
            }
    );
}
