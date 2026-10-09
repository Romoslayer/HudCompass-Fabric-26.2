package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import net.minecraft.network.FriendlyByteBuf;
import dev.gigaherz.hudcompass.network.codec.ByteBufCodecs;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Sent client -> server, requesting a new waypoint be added at the given position. */
public record AddWaypoint(
        String label,
        double x,
        double y,
        double z,
        ResourceLocation spriteName
) implements FabricPacket
{
    public static final ResourceLocation ID = HudCompass.location("add_waypoint");

    public static final PacketType<AddWaypoint> TYPE = PacketType.create(ID, buf -> AddWaypoint.STREAM_CODEC.decode(buf));

    public static final StreamCodec<FriendlyByteBuf, AddWaypoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), AddWaypoint::label,
            ByteBufCodecs.DOUBLE, AddWaypoint::x,
            ByteBufCodecs.DOUBLE, AddWaypoint::y,
            ByteBufCodecs.DOUBLE, AddWaypoint::z,
            ByteBufCodecs.RESOURCE_LOCATION, AddWaypoint::spriteName,
            AddWaypoint::new
    );

    @Override
    public PacketType<AddWaypoint> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }

    public static AddWaypoint of(BasicWaypoint point)
    {
        var labelComponent = point.getLabel();
        var labelText = labelComponent != null ? labelComponent.getString() : "";
        Vec3 position = point.getPosition();
        BasicIconData data = (BasicIconData) point.getIconData();
        return new AddWaypoint(
                labelText,
                position.x,
                position.y,
                position.z,
                data.spriteName
        );
    }
}
