package dev.gigaherz.hudcompass.network.codec;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Minimal stand-in for vanilla's {@code net.minecraft.network.codec.StreamCodec}, which only
 * arrived in 1.20.5. Same method names and generic shape ({@code encode}/{@code decode},
 * {@code of}, {@code unit}, {@code composite}, {@code dispatch}) so the waypoint/icon/payload code
 * on this 1.20.1 branch stays line-for-line parallel with the 1.21.1 and 26.x builds -- only the
 * imports differ. Implements just what this mod uses, not vanilla's whole API.
 */
public interface StreamCodec<B, V>
{
    V decode(B buf);

    void encode(B buf, V value);

    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder)
    {
        return new StreamCodec<>()
        {
            @Override
            public V decode(B buf)
            {
                return decoder.apply(buf);
            }

            @Override
            public void encode(B buf, V value)
            {
                encoder.accept(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V instance)
    {
        return of((buf, value) -> {
            if (!value.equals(instance))
                throw new IllegalStateException("Can't encode '" + value + "', expected '" + instance + "'");
        }, buf -> instance);
    }

    /**
     * Writes the key derived from each value with this codec, then the value itself with the codec
     * that key selects -- the same framing as vanilla's {@code StreamCodec#dispatch}.
     */
    @SuppressWarnings("unchecked")
    default <O> StreamCodec<B, O> dispatch(Function<? super O, ? extends V> typeGetter,
                                           Function<? super V, ? extends StreamCodec<? super B, ? extends O>> codecGetter)
    {
        StreamCodec<B, V> keyCodec = this;
        return new StreamCodec<>()
        {
            @Override
            public O decode(B buf)
            {
                V key = keyCodec.decode(buf);
                return codecGetter.apply(key).decode(buf);
            }

            @Override
            public void encode(B buf, O value)
            {
                V key = typeGetter.apply(value);
                keyCodec.encode(buf, key);
                ((StreamCodec<? super B, O>) codecGetter.apply(key)).encode(buf, value);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            Function<T1, C> factory)
    {
        return of((buf, value) -> c1.encode(buf, g1.apply(value)),
                buf -> factory.apply(c1.decode(buf)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            BiFunction<T1, T2, C> factory)
    {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            return factory.apply(v1, v2);
        });
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            Function3<T1, T2, T3, C> factory)
    {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            return factory.apply(v1, v2, v3);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            Function4<T1, T2, T3, T4, C> factory)
    {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            return factory.apply(v1, v2, v3, v4);
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            Function5<T1, T2, T3, T4, T5, C> factory)
    {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
            c5.encode(buf, g5.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            T5 v5 = c5.decode(buf);
            return factory.apply(v1, v2, v3, v4, v5);
        });
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            Function6<T1, T2, T3, T4, T5, T6, C> factory)
    {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
            c5.encode(buf, g5.apply(value));
            c6.encode(buf, g6.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            T5 v5 = c5.decode(buf);
            T6 v6 = c6.decode(buf);
            return factory.apply(v1, v2, v3, v4, v5, v6);
        });
    }
}
