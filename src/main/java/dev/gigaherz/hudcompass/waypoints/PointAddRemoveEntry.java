package dev.gigaherz.hudcompass.waypoints;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** One added/updated waypoint from the waypoint editor GUI, paired with the world it belongs to. */
public record PointAddRemoveEntry(ResourceLocation worldKey, PointInfo<?> point)
{
    public static void write(FriendlyByteBuf buffer, PointAddRemoveEntry entry)
    {
        buffer.writeResourceLocation(entry.worldKey);
        PointInfo.DISPATCH_STREAM_CODEC.encode(buffer, entry.point);
    }

    public static PointAddRemoveEntry read(FriendlyByteBuf buffer)
    {
        ResourceLocation worldKey = buffer.readResourceLocation();
        PointInfo<?> point = PointInfo.DISPATCH_STREAM_CODEC.decode(buffer);
        return new PointAddRemoveEntry(worldKey, point);
    }
}
