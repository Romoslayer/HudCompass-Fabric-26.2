package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server -> client on join, announcing that the server has the mod. The client replies with
 * {@link ClientHello} to kick off an initial full sync.
 */
public record ServerHello() implements FabricPacket
{
    public static final ServerHello INSTANCE = new ServerHello();

    public static final ResourceLocation ID = HudCompass.location("server_hello");
    public static final PacketType<ServerHello> TYPE = PacketType.create(ID, buf -> ServerHello.STREAM_CODEC.decode(buf));

    public static final StreamCodec<FriendlyByteBuf, ServerHello> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PacketType<ServerHello> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }
}
