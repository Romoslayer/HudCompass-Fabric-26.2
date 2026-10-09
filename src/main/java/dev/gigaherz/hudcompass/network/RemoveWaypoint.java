package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.network.codec.ByteBufCodecs;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Sent client -> server, requesting an existing waypoint be removed. */
public record RemoveWaypoint(UUID id) implements FabricPacket
{
    public static final ResourceLocation ID = HudCompass.location("remove_waypoint");
    public static final PacketType<RemoveWaypoint> TYPE = PacketType.create(ID, buf -> RemoveWaypoint.STREAM_CODEC.decode(buf));

    public static final StreamCodec<FriendlyByteBuf, RemoveWaypoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.UUID, RemoveWaypoint::id,
            RemoveWaypoint::new
    );

    @Override
    public PacketType<RemoveWaypoint> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }

    public RemoveWaypoint(PointInfo<?> point)
    {
        this(point.getInternalId());
    }
}
