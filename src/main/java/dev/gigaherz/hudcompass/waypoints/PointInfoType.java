package dev.gigaherz.hudcompass.waypoints;

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

public record PointInfoType<T extends PointInfo<T>>(String name, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
{
    private static final Map<String, PointInfoType<?>> BY_NAME = new LinkedHashMap<>();
    private static final Map<PointInfoType<?>, String> NAMES = new IdentityHashMap<>();

    public static <T extends PointInfo<T>> PointInfoType<T> register(String name, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
    {
        PointInfoType<T> type = new PointInfoType<>(name, codec, streamCodec);
        BY_NAME.put(name, type);
        NAMES.put(type, name);
        return type;
    }

    @Nullable
    public static PointInfoType<?> byName(String name)
    {
        return BY_NAME.get(name);
    }

    public static final Codec<PointInfoType<?>> BY_ID_CODEC = Codec.STRING.flatXmap(
            name -> {
                PointInfoType<?> type = BY_NAME.get(name);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown point info type: " + name);
            },
            type -> {
                String name = NAMES.get(type);
                return name != null
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unregistered point info type");
            }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PointInfoType<?>> BY_ID_STREAM_CODEC = StreamCodec.of(
            (buf, type) -> {
                String name = NAMES.get(type);
                if (name == null)
                    throw new IllegalArgumentException("Unregistered point info type");
                ByteBufCodecs.stringUtf8(256).encode(buf, name);
            },
            buf -> {
                String name = ByteBufCodecs.stringUtf8(256).decode(buf);
                PointInfoType<?> type = BY_NAME.get(name);
                if (type == null)
                    throw new IllegalArgumentException("Unknown point info type: " + name);
                return type;
            }
    );
}
