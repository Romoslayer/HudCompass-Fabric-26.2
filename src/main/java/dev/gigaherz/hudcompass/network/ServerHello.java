package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server -> client on join, announcing that the server has the mod. The client replies with
 * {@link ClientHello} to kick off an initial full sync.
 */
public record ServerHello() implements CustomPacketPayload
{
    public static final ServerHello INSTANCE = new ServerHello();

    public static final Identifier ID = HudCompass.location("server_hello");
    public static final Type<ServerHello> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, ServerHello> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
