package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server in reply to {@link ServerHello}, confirming the client also has the mod
 * and would like the server's authoritative waypoint state.
 */
public record ClientHello() implements FabricPacket
{
    public static final ClientHello INSTANCE = new ClientHello();

    public static final ResourceLocation ID = HudCompass.location("client_hello");
    public static final PacketType<ClientHello> TYPE = PacketType.create(ID, buf -> ClientHello.STREAM_CODEC.decode(buf));

    public static final StreamCodec<FriendlyByteBuf, ClientHello> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PacketType<ClientHello> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }
}
