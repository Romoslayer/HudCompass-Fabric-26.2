package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import dev.gigaherz.hudcompass.network.codec.ByteBufCodecs;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/** Sent server -> client: a full snapshot of that player's server-authoritative waypoint state. */
public record SyncWaypointData(byte[] bytes) implements FabricPacket
{
    public static final StreamCodec<FriendlyByteBuf, SyncWaypointData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(1 << 20), SyncWaypointData::bytes,
            SyncWaypointData::new
    );

    public static final ResourceLocation ID = HudCompass.location("sync_waypoint_data");
    public static final PacketType<SyncWaypointData> TYPE = PacketType.create(ID, buf -> SyncWaypointData.STREAM_CODEC.decode(buf));

    @Override
    public PacketType<SyncWaypointData> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }

    public static SyncWaypointData of(PointsOfInterest points, RegistryAccess registryAccess)
    {
        var temp = new FriendlyByteBuf(Unpooled.buffer());
        points.write(temp);
        var bytes = new byte[temp.readableBytes()];
        temp.readBytes(bytes, 0, bytes.length);
        return new SyncWaypointData(bytes);
    }
}
