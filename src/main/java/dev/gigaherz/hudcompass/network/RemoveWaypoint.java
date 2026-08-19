package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Sent client -> server, requesting an existing waypoint be removed. */
public record RemoveWaypoint(UUID id) implements CustomPacketPayload
{
    public static final Identifier ID = HudCompass.location("remove_waypoint");
    public static final Type<RemoveWaypoint> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RemoveWaypoint> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RemoveWaypoint::id,
            RemoveWaypoint::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public RemoveWaypoint(PointInfo<?> point)
    {
        this(point.getInternalId());
    }
}
